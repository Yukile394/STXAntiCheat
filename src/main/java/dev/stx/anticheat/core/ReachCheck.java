package dev.stx.anticheat.core;

import dev.stx.anticheat.core.Geometry.Box;
import java.util.List;

/**
 * Reach: göz → hedef kutusu mesafesi. Hedefin ping penceresindeki TÜM geçmiş konumlarından
 * en yakın olan alınır; yani yalnızca hiçbir geçmiş konumda ulaşılamayacak vuruşlar şüphelidir.
 */
public final class ReachCheck {
    private final FlagBuffer buf;
    private final double tolerance;

    public ReachCheck(double tolerance, double bufferLimit) {
        this.tolerance = tolerance;
        this.buf = new FlagBuffer(bufferLimit, 0.35);
    }

    public Result onHit(double ex, double ey, double ez, List<Box> history, double allowedReach) {
        if (history.isEmpty()) return null;
        double min = Double.MAX_VALUE;
        for (Box b : history) min = Math.min(min, Geometry.distance(b, ex, ey, ez));
        double limit = allowedReach + tolerance;
        if (min > limit) {
            double over = min - limit;
            if (buf.fail(1 + Math.min(2, over * 2)))
                return Result.of(1.0, String.format(java.util.Locale.ROOT,
                        "Reach mesafe=%.3f izin=%.3f (tolerans dahil %.3f)", min, allowedReach, limit));
        } else buf.pass();
        return null;
    }
}
