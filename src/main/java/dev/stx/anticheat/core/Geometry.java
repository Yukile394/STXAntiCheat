package dev.stx.anticheat.core;

/** Bukkit'ten bağımsız basit geometri yardımcıları. */
public final class Geometry {
    private Geometry() {}

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public static Box ofEntity(double x, double y, double z, double width, double height) {
            double h = width / 2.0;
            return new Box(x - h, y, z - h, x + h, y + height, z + h);
        }
        public Box expand(double v) {
            return new Box(minX - v, minY - v, minZ - v, maxX + v, maxY + v, maxZ + v);
        }
    }

    /** Noktadan kutuya en kısa mesafe (içerideyse 0). */
    public static double distance(Box b, double x, double y, double z) {
        double dx = Math.max(Math.max(b.minX() - x, 0), x - b.maxX());
        double dy = Math.max(Math.max(b.minY() - y, 0), y - b.maxY());
        double dz = Math.max(Math.max(b.minZ() - z, 0), z - b.maxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Minecraft bakış vektörü. */
    public static double[] look(float yaw, float pitch) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double cp = Math.cos(pr);
        return new double[]{-Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp};
    }

    /** Işın-kutu kesişimi (slab). Isabet yoksa NaN, varsa mesafe. */
    public static double rayBox(double ox, double oy, double oz, double dx, double dy, double dz, Box b, double maxDist) {
        double tmin = 0, tmax = maxDist;
        double[] o = {ox, oy, oz}, d = {dx, dy, dz};
        double[] lo = {b.minX(), b.minY(), b.minZ()}, hi = {b.maxX(), b.maxY(), b.maxZ()};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-12) {
                if (o[i] < lo[i] || o[i] > hi[i]) return Double.NaN;
            } else {
                double t1 = (lo[i] - o[i]) / d[i], t2 = (hi[i] - o[i]) / d[i];
                if (t1 > t2) { double t = t1; t1 = t2; t2 = t; }
                tmin = Math.max(tmin, t1);
                tmax = Math.min(tmax, t2);
                if (tmin > tmax) return Double.NaN;
            }
        }
        return tmin;
    }

    public static double wrapDegrees(double a) {
        a %= 360.0;
        if (a >= 180.0) a -= 360.0;
        if (a < -180.0) a += 360.0;
        return a;
    }
}
