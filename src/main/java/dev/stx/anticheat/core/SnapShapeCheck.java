package dev.stx.anticheat.core;

/**
 * Rotasyon ŞEKLİ: insan dönüşü rampalıdır (küçük-büyük-küçük birkaç tik). Yazılım (Aura Track/vuruş snap'i)
 * tek tikte büyük sıçrama yapıp öncesi ve sonrası neredeyse sabit kalır ("izole snap").
 * GCD düzeltmesi bu şekli gizleyemez.
 */
public final class SnapShapeCheck {
    private final double bigDeg;
    private final int need, windowTicks;
    private long t0 = Long.MIN_VALUE, t1 = Long.MIN_VALUE;
    private double m0, m1;
    private final long[] snaps;
    private int n, head;

    public SnapShapeCheck(double bigDeg, int need, int windowTicks) {
        this.bigDeg = bigDeg; this.need = need; this.windowTicks = windowTicks;
        this.snaps = new long[Math.max(need, 1)];
    }

    public void reset() { t0 = t1 = Long.MIN_VALUE; n = 0; head = 0; }

    /** Savaş sırasında her rotasyon paketinde çağır (yalnızca anlamlı değişimlerde). */
    public Result onRotation(long tick, double dYaw, double dPitch) {
        double m2 = Math.hypot(dYaw, dPitch);
        long t2 = tick;
        Result r = null;
        if (t0 != Long.MIN_VALUE && t1 != Long.MIN_VALUE && m1 >= bigDeg
                && (m0 <= 4 || t1 - t0 >= 3) && (m2 <= 4 || t2 - t1 >= 3)) {
            snaps[head] = t1; head = (head + 1) % snaps.length; if (n < snaps.length) n++;
            if (n == snaps.length) {
                long oldest = Long.MAX_VALUE;
                for (long s : snaps) oldest = Math.min(oldest, s);
                if (t1 - oldest <= windowTicks) {
                    n = 0; head = 0;
                    r = Result.of(0.8, "Rotasyon şekli: " + need + " izole snap (öncesi/sonrası sabit) / " + windowTicks + " tik");
                }
            }
        }
        t0 = t1; m0 = m1; t1 = t2; m1 = m2;
        return r;
    }
}
