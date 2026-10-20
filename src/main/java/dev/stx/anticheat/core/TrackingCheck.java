package dev.stx.anticheat.core;

/**
 * Açık alan Aura: hedef yanal hareket ederken (bakış açısı sürekli değişirken) kros-hair'in hitbox üzerinde
 * KALMA oranı. Aura Track modu her tik hedefe kilitlenir (~%100); insan strafe eden hedefi sürekli ıskalar.
 * Düz kaçan/kovalanan hedefte açısal hız ~0 olduğu için sayılmaz.
 */
public final class TrackingCheck {
    private final int window;
    private final double minOnTarget, minBearingDegPerTick;
    private int ticks, on;
    private double bearingSum;
    private int badWindows;

    public TrackingCheck(int window, double minOnTarget, double minBearingDegPerTick) {
        this.window = window; this.minOnTarget = minOnTarget; this.minBearingDegPerTick = minBearingDegPerTick;
    }

    public void reset() { ticks = 0; on = 0; bearingSum = 0; badWindows = 0; }

    /**
     * @param inRange       hedef 6 blok içinde ve canlı
     * @param onTarget      bakış ışını hedefin (genişletilmiş) kutusuna isabet ediyor
     * @param bearingDelta  hedefin bu tikte saldırgana göre açısal yer değiştirmesi (derece, mutlak)
     */
    public Result onTick(boolean inRange, boolean onTarget, double bearingDelta) {
        if (!inRange) { ticks = 0; on = 0; bearingSum = 0; return null; }
        ticks++;
        if (onTarget) on++;
        bearingSum += bearingDelta;
        if (ticks < window) return null;
        double frac = on / (double) ticks, bearing = bearingSum / ticks;
        ticks = 0; on = 0; bearingSum = 0;
        if (bearing >= minBearingDegPerTick && frac >= minOnTarget) {
            if (++badWindows >= 2) {
                badWindows = 1;
                return Result.of(1.0, String.format(java.util.Locale.ROOT,
                        "Aura takip: yanal hareket eden hedefte kros-hair %%%.1f hitbox üzerinde (%d tik, açısal hız %.1f°/tik)",
                        frac * 100, window, bearing));
            }
        } else badWindows = 0;
        return null;
    }
}
