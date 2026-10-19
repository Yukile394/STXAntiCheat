package dev.stx.anticheat.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * AutoClicker: (1) sürdürülen aşırı CPS, (2) vuruşlar arası tik aralığının makine gibi sabit olması.
 * Paper'da paketler ana iş parçacığında işlendiği için alt-tik zamanlama güvenilmezdir; bu yüzden
 * aralıklar TİK cinsinden ve geniş örneklemle değerlendirilir.
 */
public final class ClickCheck {
    private final ArrayDeque<Long> swings = new ArrayDeque<>();
    private final List<Long> attackTicks = new ArrayList<>();
    private final int maxCps, sustainSeconds, samples;
    private final double minStdDev;
    private long lastAttackTick = Long.MIN_VALUE;
    private final List<Integer> intervals = new ArrayList<>();
    private long windowStart = -1;
    private int overSeconds;

    public ClickCheck(int maxCps, int sustainSeconds, int samples, double minStdDev) {
        this.maxCps = maxCps; this.sustainSeconds = sustainSeconds;
        this.samples = samples; this.minStdDev = minStdDev;
    }

    /** Kol sallama (dig/animasyon). Blok kırarken çağırmayın. */
    public Result onSwing(long nowMs) {
        swings.addLast(nowMs);
        while (!swings.isEmpty() && nowMs - swings.peekFirst() > 1000) swings.pollFirst();
        if (windowStart < 0) windowStart = nowMs;
        if (nowMs - windowStart >= 1000) {
            int cps = swings.size();
            windowStart = nowMs;
            if (cps > maxCps) {
                overSeconds++;
                if (overSeconds >= sustainSeconds)
                    return Result.of(1.0, "AutoClicker CPS=" + cps + " (limit " + maxCps + ", " + overSeconds + " sn üst üste)");
            } else overSeconds = 0;
        }
        return null;
    }

    public Result onAttack(long tick) {
        if (lastAttackTick != Long.MIN_VALUE) {
            long dt = tick - lastAttackTick;
            if (dt > 40) intervals.clear();       // uzun ara: yeni seri
            else intervals.add((int) dt);
        }
        lastAttackTick = tick;
        if (intervals.size() >= samples) {
            double mean = Stats.mean(intervals), sd = Stats.stdDev(intervals);
            intervals.clear();
            if (mean <= 14 && sd < minStdDev)
                return Result.of(0.5, String.format(java.util.Locale.ROOT,
                        "AutoClicker tutarlılık: %d vuruş, ort=%.2f tik, sapma=%.3f (<%.2f)", samples, mean, sd, minStdDev));
        }
        return null;
    }
}
