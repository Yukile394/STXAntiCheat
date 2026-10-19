package dev.stx.anticheat.bukkit;

import dev.stx.anticheat.core.Geometry;
import dev.stx.anticheat.core.MoveSample;
import dev.stx.anticheat.core.Result;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

final class MovementListener implements Listener {
    private final STXAntiCheat plugin;

    MovementListener(STXAntiCheat plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);
        Location from = e.getFrom(), to = e.getTo();
        if (to == null || to.getWorld() == null) return;
        STXConfig cfg = plugin.cfg();
        Flagger fl = plugin.flagger();
        long tick = plugin.tick();
        long nowMs = STXAntiCheat.nowMs();

        if (!d.hasLast) {
            d.lastX = from.getX(); d.lastY = from.getY(); d.lastZ = from.getZ();
            d.lastYaw = from.getYaw(); d.lastPitch = from.getPitch();
            d.hasLast = true;
        }

        double dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        boolean moved = dx != 0 || dy != 0 || dz != 0;
        float yaw = to.getYaw(), pitch = to.getPitch();
        double dYaw = Geometry.wrapDegrees(yaw - d.lastYaw);
        double dPitch = pitch - d.lastPitch;
        d.pushYawDelta(Math.abs(dYaw));
        d.lastYaw = yaw; d.lastPitch = pitch;

        // Açıklanamayan büyük tek adım (başka plugin'in setTo'su vb.) -> tolerans, ceza değil
        if (dx * dx + dy * dy + dz * dz > 100.0) d.grace(tick + cfg.i("safety.teleport-grace-ticks", 30));

        String reason = plugin.exemptReason(p, d);
        boolean lag = plugin.lagging(p, d);
        boolean exempt = reason != null;

        // Konum geçmişi (Reach/Aim için hedef konumları) her zaman tutulur
        d.pushPos(nowMs, to.getX(), to.getY(), to.getZ());

        // ---- Timer: her hareket/rotasyon paketi 50ms istemci zamanı harcar ----
        Result tr = d.timer.onMove(nowMs, exempt || lag);
        if (tr != null) fl.flag(p, d, "Timer", tr);

        // ---- Rotasyon GCD: yalnızca savaşta, bedrock/sunucu rotasyonu dışında ----
        if (!exempt && !lag && d.inCombat(tick) && !(d.bedrock && cfg.b("general.bedrock-relaxed", true))) {
            Result rr = d.rotation.onRotation(dPitch);
            if (rr != null) fl.flag(p, d, "Rotation", rr);
        }

        if (moved) {
            MoveSample s;
            boolean standing = false;
            boolean special = false;
            World w = to.getWorld();
            if (exempt || lag) {
                s = new MoveSample(tick, dx, dy, dz, yaw, pitch, p.isOnGround(), p.isOnGround(), true, false, false, false,
                        false, 1.0, 0, false, p.getFallDistance());
                standing = p.isOnGround();
                // durumları sıfırlamak için örnek ver; sonuç yok sayılır
                d.air.onMove(s); d.speed.onMove(s); d.noFall.onMove(s, () -> true); d.noSlow.onMove(s);
            } else {
                standing = Probe.standing(p, to.getX(), to.getY(), to.getZ());
                special = Probe.special(p, d, to.getX(), to.getY(), to.getZ(), tick);
                boolean ice = Probe.ice(d, w, to.getX(), to.getY(), to.getZ(), tick);
                boolean velGrace = tick < d.velocityUntilTick;
                s = new MoveSample(tick, dx, dy, dz, yaw, pitch, p.isOnGround(), standing, false, velGrace, special,
                        Probe.gravityAltered(p), ice, Probe.speedMul(p), Probe.jumpLevel(p), p.isHandRaised(),
                        p.getFallDistance());
                final double tx = to.getX(), ty = to.getY(), tz = to.getZ();

                Result r = d.air.onMove(s);
                if (r != null) fl.flag(p, d, airName(r), r);
                r = d.speed.onMove(s);
                if (r != null) fl.flag(p, d, "Speed", r);
                r = d.noFall.onMove(s, () -> Probe.supportedLoose(p, tx, ty, tz));
                if (r != null) fl.flag(p, d, "NoFall", r);
                r = d.noSlow.onMove(s);
                if (r != null) fl.flag(p, d, "NoSlow", r);
                r = d.velocity.onMove(s, nowMs);
                if (r != null) fl.flag(p, d, "Velocity", r);
                d.criticals.onMove(tick, s, d.lastStanding);

                r = d.window.onMoveWhileOpen(tick, s.hSpeed(), standing, special || velGrace);
                if (r != null) fl.flag(p, d, "GuiMove", r);

                if (cfg.enabled("Phase") && !special && !velGrace) phase(p, d, from, to, w);
            }
            d.lastStanding = standing;
            d.lastHSpeed = Math.hypot(dx, dz);
            d.lastX = to.getX(); d.lastY = to.getY(); d.lastZ = to.getZ();
        }
    }

    private static String airName(Result r) {
        String t = r.detail();
        if (t.startsWith("Step")) return "Step";
        if (t.startsWith("HighJump")) return "HighJump";
        return "Fly";           // Fly, Hover, FastFall
    }

    /** Phase/NoClip: önceki konum serbestken yeni konum katı blok içinde. */
    private void phase(Player p, PlayerData d, Location from, Location to, World w) {
        double h = p.getHeight();
        boolean inside = Probe.insideSolid(w, to.getX(), to.getY(), to.getZ(), h);
        if (!inside) { d.phaseBuf.pass(); return; }
        if (Probe.insideSolid(w, from.getX(), from.getY(), from.getZ(), h)) return;     // zaten içindeydi (sıkışma vb.)
        if (Probe.movingBlocksNear(w, to.getX(), to.getY(), to.getZ())) return;
        if (d.phaseBuf.fail(1))
            plugin.flagger().flag(p, d, "Phase", Result.of(1.0, String.format(java.util.Locale.ROOT,
                    "Phase: katı bloğa girdi (%.2f,%.2f,%.2f)", to.getX(), to.getY(), to.getZ())));
    }
}
