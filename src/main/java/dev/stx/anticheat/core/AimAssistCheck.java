package dev.stx.anticheat.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Aim assist / aimbot: insan nişanı vuruş anında hedef merkezinden her zaman biraz sapar ve bu sapma değişkendir.
 * Yazılım nişanı ise merkeze çok yakın ve çok tutarlıdır. Geniş örneklem ister; düşük ağırlıkla kullanılmalıdır.
 */
public final class AimAssistCheck {
    private final List<Double> errors = new ArrayList<>();
    private final int samples;
    private final double maxMean, maxStdDev;

    public AimAssistCheck(int samples, double maxMean, double maxStdDev) {
        this.samples = samples; this.maxMean = maxMean; this.maxStdDev = maxStdDev;
    }

    /** @param centerErrorDeg vuruş anında bakış ile hedef merkezi arasındaki açı (derece) */
    public Result onHit(double centerErrorDeg) {
        errors.add(centerErrorDeg);
        if (errors.size() < samples) return null;
        double mean = Stats.mean(errors), sd = Stats.stdDev(errors);
        errors.clear();
        if (mean < maxMean && sd < maxStdDev)
            return Result.of(1.0, String.format(java.util.Locale.ROOT,
                    "AimAssist: %d vuruşta merkez sapması ort=%.2f° sapma=%.2f° (insan nişanı bundan çok daha dağınıktır)", samples, mean, sd));
        return null;
    }
}
