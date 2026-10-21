package dev.stx.anticheat.core;

/**
 * Yatay hız DIŞ GÜVENLİK sınırı (geniş tolerans). Asıl tespit MotionCheck (vanilla ivme modeli) tarafından yapılır;
 * bu sınır yalnızca modelin kaçırabileceği absürt hızlar içindir.
 * Yatay hız: anlık sınır + 20 tiklik ortalama.
 * Taban değerler VanillaSim ile ölçülmüştür (sprint+bunny-hop dahil, en kötü meşru durum):
 *  - zemin  : 0.3129 * mul        (bhop iniş tiki)
 *  - hava   : 0.6122 + 0.158*(mul-1)   (hava ivmesi efektle az ölçeklenir)
 *  - 20tik ort.: 0.3641 + 0.140*(mul-1)
 * Üzerine güvenlik payı (margin) eklenir.
 */
public final class SpeedCheck {
    public static final double GROUND_BASE = 0.3129, AIR_BASE = 0.6122, AIR_SLOPE = 0.158, AVG_BASE = 0.3641, AVG_SLOPE = 0.140;

    private final double margin, iceMul;
    private double hotMul = 1.0;
    private final double[] ring = new double[20];
    private int idx, n, skip;
    private final FlagBuffer instBuf = new FlagBuffer(3, 0.5);
    private final FlagBuffer avgBuf = new FlagBuffer(3, 0.25);

    /** @param margin ör. 0.10 → limitlerin %10 üstü tolerans */
    public SpeedCheck(double margin, double iceMul) { this.margin = margin; this.iceMul = iceMul; }

    public void setHot(boolean h) {
        hotMul = h ? 0.35 : 1.0;
        instBuf.setScale(h ? 0.34 : 1.0);
        avgBuf.setScale(h ? 0.5 : 1.0);
    }

    private void reset() { n = 0; idx = 0; skip = 5; instBuf.reset(); avgBuf.reset(); }

    public static double groundLimit(double mul, double margin) { return GROUND_BASE * mul * (1 + margin); }
    public static double airLimit(double mul, double margin) { return (AIR_BASE + AIR_SLOPE * (mul - 1)) * (1 + margin); }
    public static double avgLimit(double mul, double margin) { return (AVG_BASE + AVG_SLOPE * (mul - 1)) * (1 + margin); }

    public Result onMove(MoveSample s) {
        if (s.exempt() || s.velocity() || s.special()) { reset(); return null; }
        if (skip > 0) { skip--; return null; }

        double hs = s.hSpeed();
        ring[idx] = hs; idx = (idx + 1) % ring.length; if (n < ring.length) n++;

        double ice = s.ice() ? iceMul : 1.0;
        double mul = Math.max(0.5, s.speedMul());
        double limit = (s.standing() ? groundLimit(mul, margin * hotMul) : airLimit(mul, margin * hotMul)) * ice;
        Result r = null;
        if (hs > limit) {
            double over = (hs - limit) / Math.max(0.05, limit);
            if (instBuf.fail(1 + Math.min(2, over * 4)))
                r = Result.of(1.0, String.format(java.util.Locale.ROOT, "Speed anlık=%.3f limit=%.3f ground=%b", hs, limit, s.standing()));
        } else {
            instBuf.pass();
        }
        if (n == ring.length) {
            double sum = 0; for (double v : ring) sum += v;
            double avg = sum / ring.length;
            double aLim = avgLimit(mul, margin * hotMul) * ice;
            if (avg > aLim) {
                if (avgBuf.fail(1) && r == null)
                    r = Result.of(1.0, String.format(java.util.Locale.ROOT, "Speed ortalama=%.3f limit=%.3f", avg, aLim));
            } else {
                avgBuf.pass();
            }
        }
        return r;
    }
}
