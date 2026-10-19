package dev.stx.anticheat.core;

import dev.stx.anticheat.core.Geometry.Box;
import java.util.List;

/**
 * Vuruş ışını: oyuncunun GERÇEK bakış yönü hedefin (genişletilmiş) kutusuna hiç isabet etmiyorsa
 * vuruş "bakmadan" yapılmıştır (Aura rotasyonsuz mod, HitBox genişletme, sessiz rotasyon).
 */
public final class AimCheck {
    private final double expand;
    private final FlagBuffer buf;

    public AimCheck(double expand, double bufferLimit) {
        this.expand = expand;
        this.buf = new FlagBuffer(bufferLimit, 0.75);
    }

    public Result onHit(double ex, double ey, double ez, float yaw, float pitch, List<Box> history, double maxDist) {
        if (history.isEmpty()) return null;
        double[] d = Geometry.look(yaw, pitch);
        double bestAngle = 180;
        for (Box b : history) {
            Box e = b.expand(expand);
            double t = Geometry.rayBox(ex, ey, ez, d[0], d[1], d[2], e, maxDist + 1);
            if (!Double.isNaN(t)) { buf.pass(); return null; }
            // En yakın açı sapması (log için)
            double cx = (b.minX() + b.maxX()) / 2 - ex, cy = (b.minY() + b.maxY()) / 2 - ey, cz = (b.minZ() + b.maxZ()) / 2 - ez;
            double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (len > 1e-6) {
                double dot = (cx * d[0] + cy * d[1] + cz * d[2]) / len;
                bestAngle = Math.min(bestAngle, Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot)))));
            }
        }
        if (buf.fail(1))
            return Result.of(1.0, String.format(java.util.Locale.ROOT,
                    "Aim: bakış hedef kutusunu (+%.2f) ıskalıyor, merkeze açı=%.1f°", expand, bestAngle));
        return null;
    }
}
