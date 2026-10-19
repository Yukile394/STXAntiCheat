package dev.stx.anticheat.bukkit;

import dev.stx.anticheat.core.Geometry.Box;
import dev.stx.anticheat.core.ReachCheck;
import dev.stx.anticheat.core.Result;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class CombatListener implements Listener {
    private final STXAntiCheat plugin;

    CombatListener(STXAntiCheat plugin) { this.plugin = plugin; }

    private boolean relaxed(PlayerData d) {
        return d.bedrock && plugin.cfg().b("general.bedrock-relaxed", true);
    }

    private boolean skip(Player p, PlayerData d) {
        String r = plugin.exemptReason(p, d);
        return r != null || plugin.lagging(p, d);
    }

    /** Tıklama sayacı (hava): blok kırarken tetiklenmez. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClickAir(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_AIR) return;
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);
        if (d.bypass || relaxed(d) || skip(p, d)) return;
        Result r = d.click.onSwing(STXAntiCheat.nowMs());
        if (r != null) plugin.flagger().flag(p, d, "AutoClicker", r);
    }

    /** Varlığa saldırı girişimi (hasarsız olanlar dahil): CPS + vuruş aralığı + Criticals deseni. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttackAttempt(PrePlayerAttackEntityEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);
        if (d.mitActive("ATTACK", System.currentTimeMillis())) { e.setCancelled(true); return; }   // geçici saldırı engeli
        long tick = plugin.tick();
        d.lastAttackTick = tick;
        if (d.bypass || skip(p, d)) return;
        if (!relaxed(d)) {
            Result r = d.click.onSwing(STXAntiCheat.nowMs());
            if (r != null) plugin.flagger().flag(p, d, "AutoClicker", r);
            if (e.willAttack()) {
                r = d.click.onAttack(tick);
                if (r != null) plugin.flagger().flag(p, d, "AutoClicker", r);
            }
        }
        if (e.willAttack()) {
            Result r = d.criticals.onAttack(tick);
            if (r != null) plugin.flagger().flag(p, d, "Criticals", r);
        }
    }

    /** Doğrulanmış vuruş: Reach, Aim (bakış ışını), Aura (çoklu hedef/snap/duvar). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        PlayerData d = plugin.data(p);
        if (d.mitActive("ATTACK", System.currentTimeMillis())) { e.setCancelled(true); return; }  // geçici saldırı engeli
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;     // süpürme/projektil hariç
        if (d.bypass || skip(p, d)) return;
        STXConfig cfg = plugin.cfg();
        Entity target = e.getEntity();
        if (target == p) return;

        long tick = plugin.tick(), nowMs = STXAntiCheat.nowMs();
        Location eye = p.getEyeLocation();
        List<Box> boxes = boxesFor(target, p, nowMs);
        double reach = Probe.entityReach(p);
        boolean isPlayer = target instanceof Player;
        double allowed = reach + (isPlayer ? 0 : cfg.cd("Reach", "mob-extra", 0.30));

        Result r = d.reach.onHit(eye.getX(), eye.getY(), eye.getZ(), boxes, allowed);
        if (r != null) plugin.flagger().flag(p, d, "Reach", r);
        // Anlık engel: ping penceresindeki en yakın konuma bile açıkça ulaşılamıyorsa bu vuruşu iptal et (ihlal sayısından bağımsız)
        if (cfg.enabled("Reach") && cfg.b("sanctions.mitigation-enabled", true)
                && ReachCheck.minDistance(eye.getX(), eye.getY(), eye.getZ(), boxes)
                > allowed + cfg.cd("Reach", "tolerance", 0.30) + cfg.cd("Reach", "cancel-margin", 0.50)) {
            e.setCancelled(true);
            return;
        }

        if (!relaxed(d)) {
            r = d.aim.onHit(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(), boxes, allowed);
            if (r != null) plugin.flagger().flag(p, d, "Aim", r);
        }

        r = d.aura.onHit(tick, target.getEntityId(), d.recentYawDelta());
        if (r != null) plugin.flagger().flag(p, d, "Aura", r);

        if (cfg.cb("Aura", "wall-check", true)) wallCheck(p, d, eye, target);
    }

    private List<Box> boxesFor(Entity t, Player attacker, long nowMs) {
        double w = t.getWidth(), h = t.getHeight();
        List<Box> list = new ArrayList<>(8);
        Location l = t.getLocation();
        list.add(Box.ofEntity(l.getX(), l.getY(), l.getZ(), w, h));
        long window = Math.max(100, attacker.getPing() + 150L);
        if (t instanceof Player tp) {
            PlayerData td = plugin.peek(tp.getUniqueId());
            if (td != null) td.collectBoxes(nowMs - window, w, h, list);
        } else {
            Vector v = t.getVelocity();
            int back = (int) Math.min(10, window / 50);
            list.add(Box.ofEntity(l.getX() - v.getX() * back, l.getY(), l.getZ() - v.getZ() * back, w, h));
        }
        return list;
    }

    /** Hedefin 3 noktasının HEPSİ blokla kapalıysa duvar arkasından vuruş. */
    private void wallCheck(Player p, PlayerData d, Location eye, Entity target) {
        Location tl = target.getLocation();
        double h = target.getHeight();
        double[] ys = {h * 0.5, h * 0.9, 0.2};
        boolean allBlocked = true;
        for (double yo : ys) {
            Vector dir = new Vector(tl.getX() - eye.getX(), tl.getY() + yo - eye.getY(), tl.getZ() - eye.getZ());
            double dist = dir.length();
            if (dist < 0.8) { allBlocked = false; break; }
            dir.normalize();
            RayTraceResult rr = p.getWorld().rayTraceBlocks(eye, dir, dist - 0.1, FluidCollisionMode.NEVER, true);
            if (rr == null) { allBlocked = false; break; }
        }
        if (allBlocked) {
            if (d.wallBuf.fail(1))
                plugin.flagger().flag(p, d, "Aura", Result.of(1.0, "Aura duvar: hedefin 3 noktası da blokla kapalı"));
        } else d.wallBuf.pass();
    }

    /** Geçici blok yerleştirme engeli (Scaffold müdahalesi). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceBlock(BlockPlaceEvent e) {
        PlayerData d = plugin.data(e.getPlayer());
        if (d.mitActive("PLACE", System.currentTimeMillis())) e.setCancelled(true);
    }

    /** Scaffold: yanlış yüz, ışın uyuşmazlığı, yerleştirme hızı. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);
        if (d.bypass || p.getGameMode() == GameMode.CREATIVE || skip(p, d)) return;
        Block placed = e.getBlockPlaced(), against = e.getBlockAgainst();
        int nx = placed.getX() - against.getX(), ny = placed.getY() - against.getY(), nz = placed.getZ() - against.getZ();
        boolean unit = Math.abs(nx) + Math.abs(ny) + Math.abs(nz) == 1;
        boolean wrongFace = false, rayMiss = false;
        if (unit) {
            Location eye = p.getEyeLocation();
            double fx = against.getX() + 0.5 + 0.5 * nx, fy = against.getY() + 0.5 + 0.5 * ny, fz = against.getZ() + 0.5 + 0.5 * nz;
            double dot = (eye.getX() - fx) * nx + (eye.getY() - fy) * ny + (eye.getZ() - fz) * nz;
            wrongFace = dot < -0.15;
            if (!relaxed(d)) {
                RayTraceResult rr = p.rayTraceBlocks(6.0);
                rayMiss = rr == null || rr.getHitBlock() == null || !rr.getHitBlock().equals(against);
            }
        }
        Result r = d.scaffold.onPlace(plugin.tick(), wrongFace, rayMiss, d.lastHSpeed);
        if (r != null) plugin.flagger().flag(p, d, "Scaffold", r);
    }
}
