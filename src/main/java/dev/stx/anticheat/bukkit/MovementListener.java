package dev.stx.anticheat.bukkit;

import dev.stx.anticheat.core.Geometry;
import dev.stx.anticheat.core.MoveSample;
import dev.stx.anticheat.core.Result;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

final class MovementListener implements Listener {
    private final STXAntiCheat plugin;

    MovementListener(STXAntiCheat plugin) { this.plugin = plugin; }

    private boolean flag(Player p, PlayerData d, String name, Result r) {
        if (r == null) return false;
        plugin.flagger().flag(p, d, name, r);
        return true;
    }

    /**
     * Hareket kısıtı aktifken (log sonrası anında dondurma veya ihlal kısıtı): yerinden oynayamaz, yukarı çıkamaz.
     * Yalnızca düşüşe ve bakış yönünü çevirmeye izin verilir. Sıvı/lav içindekiler tuzağa düşmesin diye serbesttir.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFreeze(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.peek(p.getUniqueId());
        if (d == null || !d.mitActive("MOVE", System.currentTimeMillis())) return;
        if (p.isInLava() || p.isInWater() || p.isInsideVehicle() || p.isGliding()) return;
        Location f = e.getFrom(), t = e.getTo();
        if (t == null) return;
        double dx = t.getX() - f.getX(), dy = t.getY() - f.getY(), dz = t.getZ() - f.getZ();
        if (dx * dx + dz * dz > 1.0E-6 || dy > 0.001) {
            Location n = f.clone();
            n.setYaw(t.getYaw());
            n.setPitch(t.getPitch());
            e.setTo(n);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        try {
            handle(e, p);
        } catch (Throwable t) {
            // Bir hata tüm AntiCheat'i veya diğer kontrolleri durdurmasın
            plugin.getLogger().warning("Hareket kontrolü hatası (" + p.getName() + "): " + t);
        }
    }

    private void handle(PlayerMoveEvent e, Player p) {
        PlayerData d = plugin.data(p);
        Location from = e.getFrom(), to = e.getTo();
        if (to == null || to.getWorld() == null) return;
        STXConfig cfg = plugin.cfg();
        long tick = plugin.tick();
        long nowMs = STXAntiCheat.nowMs();
        long wall = System.currentTimeMillis();

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
        d.pushYawDelta(dYaw);
        d.lastYaw = yaw; d.lastPitch = pitch;
        long gapMs = d.lastMoveMs == 0 ? 0 : nowMs - d.lastMoveMs;
        d.lastMoveMs = nowMs;

        if (dx * dx + dy * dy + dz * dz > 100.0) d.grace(tick + cfg.i("safety.teleport-grace-ticks", 30));

        String reason = plugin.exemptReason(p, d);
        boolean gliding = "elytra".equals(reason);            // yalnızca elytra nedeniyle muaf -> elytra kontrolleri çalışır
        boolean exempt = reason != null;
        boolean lag = plugin.lagging(p, d);
        boolean bad = false;

        d.setHot(wall < d.moveHotUntilMs);                      // yakalanan oyuncu: hassas mod
        d.pushPos(nowMs, to.getX(), to.getY(), to.getZ());

        bad |= flag(p, d, "Timer", d.timer.onMove(nowMs, (exempt && !gliding) || lag));

        boolean combatOk = plugin.exemptReasonCombat(p, d) == null && !lag;
        boolean rotChanged = dYaw != 0 || dPitch != 0;
        if (combatOk && d.inCombat(tick) && !(d.bedrock && cfg.b("general.bedrock-relaxed", true))) {
            bad |= flag(p, d, "Rotation", d.rotation.onRotation(dPitch));
            if (rotChanged) bad |= flag(p, d, "Aura", d.snapShape.onRotation(tick, dYaw, dPitch));   // izole snap şekli
            tracking(p, d, to, yaw, pitch, tick);                                                  // açık alan: kros-hair takibi
        }

        if (!gliding) d.elytra.reset();

        if (moved) {
            World w = to.getWorld();
            boolean standing;
            boolean special = false;
            if (gliding && !lag) {
                // Elytra: vanilla fizik tahmini. Hız verilmişse (knockback/patlama) veya sıvıdaysa değerlendirilmez.
                boolean skipEval = tick < d.velocityUntilTick || p.isInWater() || p.isInLava() || p.isSwimming()
                        || Probe.special(p, d, to.getX(), to.getY(), to.getZ(), tick);
                d.motion.reset();
                bad |= flag(p, d, "ElytraSpeed", d.elytra.onMove(dx, dy, dz, yaw, pitch, tick < d.boostUntilTick, skipEval, gapMs));
                standing = false;
            } else if (exempt || lag) {
                MoveSample s = new MoveSample(tick, dx, dy, dz, yaw, pitch, p.isOnGround(), p.isOnGround(), true, false, false,
                        false, false, 1.0, 0, false, p.getFallDistance());
                standing = p.isOnGround();
                d.air.onMove(s); d.speed.onMove(s); d.motion.onMove(s, gapMs, null); d.noFall.onMove(s, () -> true); d.noSlow.onMove(s);
                d.jesus.onMove(false, 0, 0, true);
            } else {
                final double tx = to.getX(), ty = to.getY(), tz = to.getZ();
                standing = Probe.standing(p, tx, ty, tz);
                special = Probe.special(p, d, tx, ty, tz, tick);
                boolean ice = Probe.ice(d, w, tx, ty, tz, tick);
                boolean velGrace = tick < d.velocityUntilTick;
                MoveSample s = new MoveSample(tick, dx, dy, dz, yaw, pitch, p.isOnGround(), standing, false, velGrace, special,
                        Probe.gravityAltered(p), ice, Probe.speedMul(p), Probe.jumpLevel(p),
                        Probe.usingItemSlows(p, cfg.noSlowIgnore()), p.getFallDistance(),
                        Probe.slip(w, tx, ty, tz, standing), p.isSprinting(), Probe.moveAttr(p));

                Result r = d.air.onMove(s);
                if (r != null) {
                    String name = airName(r);
                    if (name.equals("Fly") && dy > 0 && r.detail().startsWith("Fly") && Probe.nearWall(w, tx, ty, tz)) name = "Spider";
                    bad |= flag(p, d, name, r);
                }
                bad |= flag(p, d, "Speed", d.motion.onMove(s, gapMs, () -> Probe.entityNear(p)));   // vanilla ivme modeli
                bad |= flag(p, d, "Speed", d.speed.onMove(s));                                      // dış güvenlik sınırı
                bad |= flag(p, d, "NoFall", d.noFall.onMove(s, () -> Probe.supportedLoose(p, tx, ty, tz)));
                bad |= flag(p, d, "NoSlow", d.noSlow.onMove(s));
                bad |= flag(p, d, "Velocity", d.velocity.onMove(s, nowMs));
                d.criticals.onMove(tick, s, d.lastStanding);
                bad |= flag(p, d, "GuiMove", d.window.onMoveWhileOpen(tick, s.hSpeed(), standing, special || velGrace));

                boolean surface = !standing && !p.isInWater() && !p.isSwimming() && Probe.liquidBelow(w, tx, ty, tz);
                bad |= flag(p, d, "Jesus", d.jesus.onMove(surface, dy, s.hSpeed(), false));

                if (cfg.enabled("Phase") && !special && !velGrace) bad |= phase(p, d, from, to, w);

                if (!bad && standing && !special) d.setGood(w, tx, ty, tz, yaw, pitch);
            }
            d.lastStanding = standing;
            d.lastHSpeed = Math.hypot(dx, dz);
            d.lastX = to.getX(); d.lastY = to.getY(); d.lastZ = to.getZ();
        }
    }

    /** Savaştaki hedefe kros-hair kalıcılığı (yalnızca oyuncu hedeflerde, yanal hareket ederken anlamlıdır). */
    private void tracking(Player p, PlayerData d, Location to, float yaw, float pitch, long tick) {
        if (d.combatTarget == null || tick - d.combatTargetTick > 60 || !plugin.cfg().enabled("Aura")) { d.hasBearing = false; return; }
        Entity t = Bukkit.getEntity(d.combatTarget);
        if (!(t instanceof Player tp) || t.isDead() || !t.getWorld().equals(to.getWorld())) { d.hasBearing = false; return; }
        Location tl = t.getLocation();
        double ddx = tl.getX() - to.getX(), ddz = tl.getZ() - to.getZ();
        boolean inRange = Math.hypot(ddx, ddz) < 6.0;
        double bearing = Math.toDegrees(Math.atan2(ddz, ddx));
        double bd = d.hasBearing ? Math.abs(Geometry.wrapDegrees(bearing - d.lastBearing)) : 0;
        d.lastBearing = bearing; d.hasBearing = true;
        double ex = to.getX(), ey = to.getY() + p.getEyeHeight(), ez = to.getZ();
        double[] look = Geometry.look(yaw, pitch);
        boolean on = rayHits(ex, ey, ez, look, Geometry.Box.ofEntity(tl.getX(), tl.getY(), tl.getZ(), t.getWidth(), t.getHeight()).expand(0.12));
        if (!on) {                                                     // ping geçmişindeki konumlar da sayılır
            PlayerData td = plugin.peek(tp.getUniqueId());
            if (td != null) {
                java.util.List<Geometry.Box> hist = new java.util.ArrayList<>(6);
                td.collectBoxes(STXAntiCheat.nowMs() - Math.max(100, p.getPing() + 100L), t.getWidth(), t.getHeight(), hist);
                for (Geometry.Box b : hist) if (rayHits(ex, ey, ez, look, b.expand(0.12))) { on = true; break; }
            }
        }
        plugin.flagger().flag(p, d, "Aura", d.tracking.onTick(inRange, on, bd));
    }

    private static boolean rayHits(double ex, double ey, double ez, double[] look, Geometry.Box b) {
        return !Double.isNaN(Geometry.rayBox(ex, ey, ez, look[0], look[1], look[2], b, 7.0));
    }

    private static String airName(Result r) {
        String t = r.detail();
        if (t.startsWith("Step")) return "Step";
        if (t.startsWith("HighJump")) return "HighJump";
        return "Fly";           // Fly, Hover, FastFall
    }

    private boolean phase(Player p, PlayerData d, Location from, Location to, World w) {
        double h = p.getHeight();
        boolean inside = Probe.insideSolid(w, to.getX(), to.getY(), to.getZ(), h);
        if (!inside) { d.phaseBuf.pass(); return false; }
        if (Probe.insideSolid(w, from.getX(), from.getY(), from.getZ(), h)) return false;
        if (Probe.movingBlocksNear(w, to.getX(), to.getY(), to.getZ())) return false;
        if (d.phaseBuf.fail(1)) {
            plugin.flagger().flag(p, d, "Phase", Result.of(1.0, String.format(java.util.Locale.ROOT,
                    "Phase: katı bloğa girdi (%.2f,%.2f,%.2f)", to.getX(), to.getY(), to.getZ())));
            return true;
        }
        return false;
    }
}
