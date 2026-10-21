package dev.stx.anticheat.bukkit;

import java.util.Collection;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Sunucu tarafı çarpışma/ortam sorguları. Yüklü olmayan chunk'a ASLA dokunmaz (chunk yüklemez). */
final class Probe {
    static final double HALF = 0.3, EPS = 1.0E-4;

    private Probe() {}

    private static int floor(double v) { return (int) Math.floor(v); }

    /** Ayakların altında (tolBelow içinde) taşıyıcı bir çarpışma yüzeyi var mı? Yüklenmemiş chunk -> true (muhafazakâr). */
    static boolean supported(World w, double x, double y, double z, double tolBelow) {
        int x0 = floor(x - HALF + EPS), x1 = floor(x + HALF - EPS);
        int z0 = floor(z - HALF + EPS), z1 = floor(z + HALF - EPS);
        int yTop = floor(y + 0.003), yBot = floor(y - tolBelow) - 1;   // çit/duvar 1.5 yüksek olabilir
        for (int bx = x0; bx <= x1; bx++) {
            for (int bz = z0; bz <= z1; bz++) {
                if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return true;
                for (int by = yTop; by >= yBot; by--) {
                    Block b = w.getBlockAt(bx, by, bz);
                    Material m = b.getType();
                    if (m.isAir() || b.isPassable()) continue;
                    // DİKKAT: her zaman gerçek çarpışma şekli. Çamur (0.875), ruh kumu, tarla, patika tam küp DEĞİLDİR.
                    double top = Double.NEGATIVE_INFINITY;
                    for (BoundingBox bb : b.getCollisionShape().getBoundingBoxes()) {
                        if (bx + bb.getMaxX() <= x - HALF + EPS || bx + bb.getMinX() >= x + HALF - EPS
                                || bz + bb.getMaxZ() <= z - HALF + EPS || bz + bb.getMinZ() >= z + HALF - EPS) continue;
                        top = Math.max(top, by + bb.getMaxY());
                    }
                    if (top >= y - tolBelow && top <= y + 0.003) return true;
                }
            }
        }
        return false;
    }

    /** Bot/shulker/vagon üstünde durma (blok yok ama gerçek zemin). Yalnızca gerekirse çağrılır. */
    static boolean entitySupport(Player p, double x, double y, double z) {
        BoundingBox box = new BoundingBox(x - 0.4, y - 0.35, z - 0.4, x + 0.4, y + 0.05, z + 0.4);
        Collection<Entity> list = p.getWorld().getNearbyEntities(box,
                e -> e != p && (e instanceof Boat || e instanceof Shulker || e instanceof Minecart));
        return !list.isEmpty();
    }

    /** Hareket kontrollerinin "zeminde duruyor" bilgisi (sunucu hesabı). */
    static boolean standing(Player p, double x, double y, double z) {
        World w = p.getWorld();
        if (supported(w, x, y, z, 0.02)) return true;
        return p.isOnGround() && entitySupport(p, x, y, z);
    }

    /** NoFall için geniş (lag toleranslı) taşıyıcı sorgusu. */
    static boolean supportedLoose(Player p, double x, double y, double z) {
        return supported(p.getWorld(), x, y, z, 0.35) || entitySupport(p, x, y, z);
    }

    /** Oyuncu kutusu katı blokla kesişiyor mu (Phase). */
    static boolean insideSolid(World w, double x, double y, double z, double height) {
        double minX = x - HALF + 0.06, maxX = x + HALF - 0.06, minZ = z - HALF + 0.06, maxZ = z + HALF - 0.06;
        double minY = y + 0.1, maxY = y + height - 0.1;
        for (int bx = floor(minX); bx <= floor(maxX - EPS); bx++) {
            for (int bz = floor(minZ); bz <= floor(maxZ - EPS); bz++) {
                if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return false;
                for (int by = floor(minY); by <= floor(maxY - EPS); by++) {
                    Block b = w.getBlockAt(bx, by, bz);
                    Material m = b.getType();
                    if (m.isAir() || b.isPassable()) continue;
                    for (BoundingBox bb : b.getCollisionShape().getBoundingBoxes()) {
                        if (bx + bb.getMinX() < maxX && bx + bb.getMaxX() > minX
                                && by + bb.getMinY() < maxY && by + bb.getMaxY() > minY
                                && bz + bb.getMinZ() < maxZ && bz + bb.getMaxZ() > minZ) return true;
                    }
                }
            }
        }
        return false;
    }

    /** Çevredeki piston / düşen blok benzeri geçici katılar var mı (Phase yanlış pozitifini önler). */
    static boolean movingBlocksNear(World w, double x, double y, double z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dy = -1; dy <= 2; dy++)
                for (int dz = -1; dz <= 1; dz++) {
                    int bx = floor(x) + dx, bz = floor(z) + dz;
                    if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return true;
                    Material m = w.getBlockAt(bx, floor(y) + dy, bz).getType();
                    if (m == Material.MOVING_PISTON || m == Material.PISTON_HEAD || m == Material.PISTON
                            || m == Material.STICKY_PISTON) return true;
                }
        return false;
    }

    /**
     * Özel ortam (sıvı, tırmanma, ağ, bal/slime/yatak sıçraması, piston...). Çıkıştan sonra kısa süre "özel" kalır.
     * Durumu PlayerData üzerindeki süre alanlarında tutar.
     */
    static boolean special(Player p, PlayerData d, double x, double y, double z, long tick) {
        World w = p.getWorld();
        if (p.isInWater() || p.isInLava() || p.isSwimming()) d.liquidUntilTick = tick + 12;
        Block feet = w.getBlockAt(floor(x), floor(y), floor(z));
        Block head = w.getBlockAt(floor(x), floor(y + 1.0), floor(z));
        Block below = w.getBlockAt(floor(x), floor(y - 0.2), floor(z));
        Material f = feet.getType(), h = head.getType(), b = below.getType();

        if (Tag.CLIMBABLE.isTagged(f) || Tag.CLIMBABLE.isTagged(h) || Tag.CLIMBABLE.isTagged(b)) d.climbUntilTick = tick + 8;
        if (f == Material.COBWEB || h == Material.COBWEB || f == Material.SWEET_BERRY_BUSH || h == Material.SWEET_BERRY_BUSH
                || f == Material.POWDER_SNOW || h == Material.POWDER_SNOW || f == Material.BUBBLE_COLUMN || b == Material.BUBBLE_COLUMN
                || f == Material.HONEY_BLOCK || b == Material.HONEY_BLOCK) d.climbUntilTick = Math.max(d.climbUntilTick, tick + 12);
        if (b == Material.SLIME_BLOCK || b == Material.HONEY_BLOCK || Tag.BEDS.isTagged(b)) d.bounceUntilTick = tick + 30;
        if (f == Material.MOVING_PISTON || f == Material.PISTON_HEAD || b == Material.MOVING_PISTON || b == Material.PISTON_HEAD
                || h == Material.MOVING_PISTON || h == Material.PISTON_HEAD) d.pistonUntilTick = tick + 15;
        return tick < d.liquidUntilTick || tick < d.climbUntilTick || tick < d.bounceUntilTick || tick < d.pistonUntilTick;
    }

    static boolean ice(PlayerData d, World w, double x, double y, double z, long tick) {
        Material m = w.getBlockAt(floor(x), floor(y - 0.5), floor(z)).getType();
        if (m == Material.ICE || m == Material.PACKED_ICE || m == Material.BLUE_ICE || m == Material.FROSTED_ICE)
            d.iceUntilTick = tick + 25;
        return tick < d.iceUntilTick;
    }

    /** Ayak altı sıvı, ayak bloğu sıvı değil (sıvının ÜSTÜNDE). */
    static boolean liquidBelow(World w, double x, double y, double z) {
        Block below = w.getBlockAt(floor(x), floor(y - 0.1), floor(z));
        Block feet = w.getBlockAt(floor(x), floor(y), floor(z));
        return below.isLiquid() && !feet.isLiquid();
    }

    /** Yanda katı blok var mı (Spider: duvara tırmanma). */
    static boolean nearWall(World w, double x, double y, double z) {
        double[][] o = {{0.45, 0}, {-0.45, 0}, {0, 0.45}, {0, -0.45}};
        for (double[] d : o) {
            int bx = floor(x + d[0]), bz = floor(z + d[1]);
            if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return false;
            for (double yo : new double[]{0.5, 1.4}) {
                Block b = w.getBlockAt(bx, floor(y + yo), bz);
                if (!b.getType().isAir() && !b.isPassable()) return true;
            }
        }
        return false;
    }

    /** Knockback yönünde 1.2 blokta engel veya tavan var mı? Varsa knockback ölçülmez. */
    static boolean knockbackBlocked(Player p, Vector v) {
        World w = p.getWorld();
        Vector h = new Vector(v.getX(), 0, v.getZ());
        if (h.lengthSquared() > 0.04) {
            h.normalize();
            for (double yo : new double[]{0.1, 0.9, 1.6}) {
                var rr = w.rayTraceBlocks(p.getLocation().add(0, yo, 0), h, 1.2, org.bukkit.FluidCollisionMode.NEVER, true);
                if (rr != null) return true;
            }
        }
        if (v.getY() > 0.1) {
            var rr = w.rayTraceBlocks(p.getLocation().add(0, 1.8, 0), new Vector(0, 1, 0), 0.8, org.bukkit.FluidCollisionMode.NEVER, true);
            if (rr != null) return true;
        }
        return false;
    }

    // -------- öznitelik / efekt yardımcıları (sürümler arası anahtar adı farkı için kayıt defteriyle) --------

    private static Attribute attr(String... keys) {
        for (String k : keys) {
            Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(k));
            if (a != null) return a;
        }
        return null;
    }

    private static final Attribute MOVE = attr("movement_speed", "generic.movement_speed");
    private static final Attribute REACH = attr("entity_interaction_range", "player.entity_interaction_range");

    /** Saldırı menzili (varsayılan 3.0). */
    static double entityReach(Player p) {
        if (REACH == null) return 3.0;
        AttributeInstance in = p.getAttribute(REACH);
        return in == null ? 3.0 : in.getValue();
    }

    /** Hız çarpanı: öznitelik değeri (efekt + sprint + özel modifier'lar dahil) / beklenen taban. */
    static double speedMul(Player p) {
        if (MOVE != null) {
            AttributeInstance in = p.getAttribute(MOVE);
            if (in != null) {
                double v = in.getValue();
                double base = p.isSprinting() ? 0.13 : 0.1;
                return Math.max(0.5, v / base);
            }
        }
        double mul = p.getWalkSpeed() / 0.2;
        PotionEffect sp = p.getPotionEffect(PotionEffectType.SPEED);
        if (sp != null) mul *= 1 + 0.2 * (sp.getAmplifier() + 1);
        PotionEffect sl = p.getPotionEffect(PotionEffectType.SLOWNESS);
        if (sl != null) mul *= Math.max(0.1, 1 - 0.15 * (sl.getAmplifier() + 1));
        return Math.max(0.5, mul);
    }

    private static final Attribute JUMP = attr("jump_strength", "generic.jump_strength");

    /** Etkin zıplama seviyesi: Zıplama Güçlendirme + özel jump_strength özniteliği (taban 0.42) farkı. */
    static int jumpLevel(Player p) {
        PotionEffect e = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
        int lvl = e == null ? 0 : e.getAmplifier() + 1;
        if (JUMP != null) {
            AttributeInstance in = p.getAttribute(JUMP);
            if (in != null) lvl += Math.max(0, (int) Math.round((in.getValue() - 0.42) / 0.1));
        }
        return lvl;
    }

    /** Ayak altındaki bloğun sürtünmesi (yalnızca yerdeyken anlamlı). */
    static double slip(World w, double x, double y, double z, boolean standing) {
        if (!standing) return 0.6;
        Material m = w.getBlockAt(floor(x), floor(y - 0.5000001), floor(z)).getType();
        if (m == Material.BLUE_ICE) return 0.989;
        if (m == Material.ICE || m == Material.PACKED_ICE || m == Material.FROSTED_ICE) return 0.98;
        if (m == Material.SLIME_BLOCK) return 0.8;
        return 0.6;
    }

    /** Hareket hızı özniteliği (sprint, efekt ve özel modifier'lar dahil). Yoksa yaklaşık değer. */
    static double moveAttr(Player p) {
        if (MOVE != null) {
            AttributeInstance in = p.getAttribute(MOVE);
            if (in != null) return Math.max(0.02, in.getValue());
        }
        return (p.isSprinting() ? 0.13 : 0.1) * speedMul(p);
    }

    /** Eşya kullanımı yavaşlatıyor mu? Yavaşlatmayan eşyalar (spyglass, boynuz, fırça) config listesiyle yok sayılır. */
    static boolean usingItemSlows(Player p, java.util.Set<Material> ignore) {
        if (!p.isHandRaised()) return false;
        org.bukkit.inventory.ItemStack it = p.getActiveItem();
        return it == null || !ignore.contains(it.getType());
    }

    /** Yakında itebilecek varlık var mı (yalnızca ihlal şüphesinde çağrılır). */
    static boolean entityNear(Player p) {
        return !p.getNearbyEntities(1.15, 1.0, 1.15).isEmpty();
    }

    static boolean gravityAltered(Player p) {
        return p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING);
    }
}
