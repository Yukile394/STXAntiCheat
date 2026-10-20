package dev.stx.anticheat.bukkit;

import dev.stx.anticheat.core.AimCheck;
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

    /** İstatistiksel kontroller (CPS, Reach tamponu...) için: lag/TPS düşüşünde atla. */
    private boolean skip(Player p, PlayerData d) {
        return plugin.exemptReasonCombat(p, d) != null || plugin.lagging(p, d);
    }

    /** Geometrik kontroller (duvar, bakış açısı) için: yalnızca muafiyet ve aşırı ping. TPS düşüşü bunları kapatmaz. */
    private boolean skipGeometry(Player p, PlayerData d) {
        return plugin.exemptReasonCombat(p, d) != null || p.getPing() > 700;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClickAir(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_AIR) return;
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);
        if (d.bypass || relaxed(d) || skip(p, d)) return;
        Result r = d.click.onSwing(STXAntiCheat.nowMs());
        if (r != null) plugin.flagger().flag(p, d, "AutoClicker", r);
    }

    /** Havai fişek: boost penceresi (ElytraSpeed için) + otomatik roket ritmi (ElytraTarget). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRocket(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        org.bukkit.inventory.ItemStack it = e.getItem();
        Player p = e.getPlayer();
        if (it == null || it.getType() != org.bukkit.Material.FIREWORK_ROCKET || !p.isGliding()) return;
        PlayerData d = plugin.data(p);
        int power = 3;
        try { if (it.getItemMeta() instanceof org.bukkit.inventory.meta.FireworkMeta fm) power = Math.max(0, fm.getPower()); } catch (Throwable ignored) { }
        long tick = plugin.tick();
        d.boostUntilTick = Math.max(d.boostUntilTick, tick + 10L * (power + 1) + 25);      // roket ömrü + pay
        if (d.bypass || tick - d.lastAttackTick > 400 || !plugin.cfg().enabled("ElytraTarget")) return;   // yalnızca savaş bağlamında
        Result r = d.rocket.onUse(tick);
        if (r != null) plugin.flagger().flag(p, d, "ElytraTarget", r);
    }

    /** Saldırı girişimi: geçici saldırı engeli, CPS, vuruş aralığı, Criticals deseni. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttackAttempt(PrePlayerAttackEntityEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);
        if (d.mitActive("ATTACK", System.currentTimeMillis())) { e.setCancelled(true); return; }
        long tick = plugin.tick();
        d.lastAttackTick = tick;
        d.combatTarget = e.getAttacked().getUniqueId();
        d.combatTargetTick = tick;
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

    /**
     * Doğrulanmış her vuruş: duvar arkası, bakmadan vuruş, Reach, Aim, Aura, AimAssist.
     * Açıkça geçersiz vuruşlar (duvar/arkaya bakma/ulaşılamayan) ANINDA iptal edilir; ihlal sayısından bağımsızdır.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        PlayerData d = plugin.data(p);
        if (d.mitActive("ATTACK", System.currentTimeMillis())) { e.setCancelled(true); return; }
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (d.bypass || plugin.exemptReasonCombat(p, d) != null) return;
        Entity target = e.getEntity();
        if (target == p) return;
        d.combatTarget = target.getUniqueId();
        d.combatTargetTick = plugin.tick();

        STXConfig cfg = plugin.cfg();
        boolean cancelOk = cfg.b("sanctions.mitigation-enabled", true);
        boolean geo = !skipGeometry(p, d);
        boolean stat = !plugin.lagging(p, d);
        long tick = plugin.tick(), nowMs = STXAntiCheat.nowMs();
        Location eye = p.getEyeLocation();
        List<Box> boxes = boxesFor(target, p, nowMs);

        // 1) DUVAR ARKASI: her vuruşta, hedefin TÜM ping-geçmişi konumları için ışın atılır. Hepsi kapalıysa iptal.
        if (geo && cfg.enabled("Aura") && cfg.cb("Aura", "wall-check", true) && behindWall(p, eye, boxes)) {
            plugin.flagger().flag(p, d, "Aura", Result.of(2.0, "Aura duvar: hedef bloğun arkasında, vuruş iptal edildi"));
            if (cancelOk) { e.setCancelled(true); return; }
        }

        // 2) BAKMADAN VURUŞ: bakış, hedefin hiçbir geçmiş konumuna makul açıda değil -> anında iptal
        double angle = AimCheck.minAngle(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(), boxes);
        double cancelAngle = cfg.cd("Aura", "angle-cancel-degrees", 70);
        if (geo && !relaxed(d) && cfg.enabled("Aura") && angle > cancelAngle) {
            plugin.flagger().flag(p, d, "Aura", Result.of(2.0, String.format(java.util.Locale.ROOT,
                    "Aura bakmadan vuruş: bakış hedeften %.0f° sapmış, vuruş iptal edildi", angle)));
            if (cancelOk) { e.setCancelled(true); return; }
        }

        // 3) REACH
        double allowed = Probe.entityReach(p) + (target instanceof Player ? 0 : cfg.cd("Reach", "mob-extra", 0.30));
        if (stat) {
            Result r = d.reach.onHit(eye.getX(), eye.getY(), eye.getZ(), boxes, allowed);
            if (r != null) plugin.flagger().flag(p, d, "Reach", r);
        }
        if (stat && cfg.enabled("Reach") && cancelOk
                && ReachCheck.minDistance(eye.getX(), eye.getY(), eye.getZ(), boxes)
                > allowed + cfg.cd("Reach", "tolerance", 0.30) + cfg.cd("Reach", "cancel-margin", 0.50)) {
            e.setCancelled(true);
            return;
        }

        // 4) AIM (ışın hitbox'ı ıskalıyor) ve AimAssist (merkeze fazla kilitli)
        if (stat && !relaxed(d)) {
            Result r = d.aim.onHit(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(), boxes, allowed);
            if (r != null) plugin.flagger().flag(p, d, "Aim", r);
            Box cur = boxes.get(0);
            double cdx = (cur.minX() + cur.maxX()) / 2 - eye.getX(), cdz = (cur.minZ() + cur.maxZ()) / 2 - eye.getZ();
            if (Math.hypot(cdx, cdz) >= 2.0) {                       // çok yakın mesafede açı ölçümü anlamsız
                r = d.aimAssist.onHit(angle);
                if (r != null) plugin.flagger().flag(p, d, "AimAssist", r);
            }
        }

        // 4b) ELYTRATARGET: elytra ile hedefe kilitli vuruş / dön-vur-geri dön
        if (geo && p.isGliding() && cfg.enabled("ElytraTarget")) {
            double eyeErr = angle;
            if (target instanceof org.bukkit.entity.LivingEntity le) {
                Location te = le.getEyeLocation();
                Box eb = Box.ofEntity(te.getX(), te.getY() - 0.05, te.getZ(), 0.1, 0.1);
                eyeErr = Math.min(eyeErr, AimCheck.minAngle(eye.getX(), eye.getY(), eye.getZ(), eye.getYaw(), eye.getPitch(), List.of(eb)));
            }
            Result r = d.elytraHit.onHit(true, d.snapBack(), eyeErr);
            if (r != null) plugin.flagger().flag(p, d, "ElytraTarget", r);
        }

        // 5) AURA davranışı: çoklu hedef + keskin dönüş + sessiz rotasyon (dön-vur-geri dön)
        if (geo) {
            double yawD = d.snapBack() ? Math.max(d.recentYawDelta(), 90) : d.recentYawDelta();
            Result r = d.aura.onHit(tick, target.getEntityId(), yawD);
            if (r != null) plugin.flagger().flag(p, d, "Aura", r);
        }
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

    /** Göz -> hedef arasında blok var mı? Konumların HEPSİNDE ve 5 noktanın HEPSİNDE kapalıysa duvar arkasıdır. */
    private boolean behindWall(Player p, Location eye, List<Box> boxes) {
        int n = boxes.size();
        int[] idx = n <= 4 ? null : new int[]{0, n / 3, (2 * n) / 3, n - 1};
        int count = idx == null ? n : idx.length;
        for (int k = 0; k < count; k++) {
            Box b = boxes.get(idx == null ? k : idx[k]);
            if (visible(p, eye, b)) return false;
        }
        return true;
    }

    private boolean visible(Player p, Location eye, Box b) {
        double cx = (b.minX() + b.maxX()) / 2, cz = (b.minZ() + b.maxZ()) / 2, h = b.maxY() - b.minY();
        double dxs = cx - eye.getX(), dzs = cz - eye.getZ(), len = Math.hypot(dxs, dzs);
        double px = len > 1e-6 ? -dzs / len * 0.3 : 0.3, pz = len > 1e-6 ? dxs / len * 0.3 : 0;   // yan noktalar
        double[][] pts = {
                {cx, b.minY() + h * 0.5, cz}, {cx, b.minY() + h * 0.9, cz}, {cx, b.minY() + h * 0.15, cz},
                {cx + px, b.minY() + h * 0.5, cz + pz}, {cx - px, b.minY() + h * 0.5, cz - pz}};
        for (double[] pt : pts) {
            Vector dir = new Vector(pt[0] - eye.getX(), pt[1] - eye.getY(), pt[2] - eye.getZ());
            double dist = dir.length();
            if (dist < 0.8) return true;
            dir.normalize();
            RayTraceResult rr = p.getWorld().rayTraceBlocks(eye, dir, dist - 0.1, FluidCollisionMode.NEVER, true);
            if (rr == null) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlaceBlock(BlockPlaceEvent e) {
        PlayerData d = plugin.data(e.getPlayer());
        if (d.mitActive("PLACE", System.currentTimeMillis())) e.setCancelled(true);
    }

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
