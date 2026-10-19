package dev.stx.anticheat.core;

/**
 * Timer / TickShift / burst tespiti. Hareket paketleri arası süreden bir "kredi" hesaplanır:
 * her paket 50 ms'lik istemci zamanı harcar; boşluklar (lag) sınırlı miktarda kredi biriktirir.
 * Kredi toleransın altına inerse istemci olması gerekenden hızlı paket yolluyordur.
 */
public final class TimerCheck {
    private final double maxCreditMs, toleranceMs;
    private long lastMs = -1;
    private double credit;

    public TimerCheck(double maxCreditMs, double toleranceMs) {
        this.maxCreditMs = maxCreditMs; this.toleranceMs = toleranceMs;
    }

    public void reset() { lastMs = -1; credit = 0; }

    public Result onMove(long nowMs, boolean exemptOrLag) {
        if (exemptOrLag) { reset(); return null; }
        if (lastMs < 0) { lastMs = nowMs; return null; }
        long dt = nowMs - lastMs;
        lastMs = nowMs;
        credit = Math.min(maxCreditMs, credit + dt - 50.0);
        if (credit < -toleranceMs) {
            double c = credit;
            credit = 0;
            return Result.of(1.0, String.format(java.util.Locale.ROOT,
                    "Timer kredi=%.0fms (paket hızı ≈ %.2fx)", c, 1.0 + (-c) / 1000.0));
        }
        return null;
    }

    public double credit() { return credit; }
}
