package dev.stx.anticheat.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Fare hassasiyeti kuantizasyonu (GCD). Gerçek fare dönüşleri sabit bir adımın tam katlarıdır;
 * yazılımla üretilen (Aura/AimBot) dönüşler değildir. Bedrock/Geyser, sinematik kamera ve
 * sunucu kaynaklı rotasyonlarda yanlış pozitif verebildiği için düşük ağırlıkla ve ardışık pencerelerle kullanılır.
 */
public final class RotationCheck {
    private final List<Double> pitchDeltas = new ArrayList<>();
    private final int window;
    private final double minStep;
    private int badWindows;

    public RotationCheck(int window, double minStep) { this.window = window; this.minStep = minStep; }

    public void reset() { pitchDeltas.clear(); badWindows = 0; }

    /** Yalnızca savaş sırasındaki, sunucu kaynaklı olmayan, anlamlı pitch farklarını besleyin. */
    public Result onRotation(double dPitch) {
        double a = Math.abs(dPitch);
        if (a < 0.002 || a > 20) return null;
        pitchDeltas.add(a);
        if (pitchDeltas.size() < window) return null;

        double g = pitchDeltas.get(0);
        for (int i = 1; i < pitchDeltas.size(); i++) g = Stats.gcd(g, pitchDeltas.get(i), 3e-4);
        pitchDeltas.clear();

        if (g < minStep) {
            badWindows++;
            if (badWindows >= 3) {
                badWindows = 1;
                return Result.of(1.0, String.format(java.util.Locale.ROOT,
                        "Rotasyon GCD=%.5f (<%.4f): fare kuantizasyonu yok, %d ardışık pencere", g, minStep, 3));
            }
        } else {
            badWindows = 0;
        }
        return null;
    }
}
