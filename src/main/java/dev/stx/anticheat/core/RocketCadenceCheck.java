package dev.stx.anticheat.core;

import java.util.ArrayList;
import java.util.List;

/** Otomatik havai fişek (ElytraTarget "Firework sending"): ritmik, makine gibi sabit aralıkla roket kullanımı. */
public final class RocketCadenceCheck {
    private final List<Integer> gaps = new ArrayList<>();
    private long last = Long.MIN_VALUE;
    private final int samples;
    private final double maxStdTicks;

    public RocketCadenceCheck(int samples, double maxStdTicks) { this.samples = samples; this.maxStdTicks = maxStdTicks; }

    public Result onUse(long tick) {
        if (last != Long.MIN_VALUE) {
            long g = tick - last;
            if (g > 160 || g < 4) gaps.clear(); else gaps.add((int) g);
        }
        last = tick;
        if (gaps.size() >= samples) {
            double mean = Stats.mean(gaps), sd = Stats.stdDev(gaps);
            gaps.clear();
            if (sd < maxStdTicks && mean >= 18 && mean <= 100)
                return Result.of(1.5, String.format(java.util.Locale.ROOT,
                        "ElytraTarget: otomatik roket ritmi %d kullanım, ort=%.1f tik, sapma=%.2f tik", samples, mean, sd));
        }
        return null;
    }
}
