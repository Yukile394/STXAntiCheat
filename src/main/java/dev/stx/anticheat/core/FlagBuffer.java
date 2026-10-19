package dev.stx.anticheat.core;

/**
 * Tek bir şüpheli olayın kanıt üretmesini engelleyen tampon.
 * fail() değeri artırır, pass() azaltır; yalnızca limit aşılırsa true döner.
 */
public final class FlagBuffer {
    private final double limit;
    private final double decay;
    private double value;

    public FlagBuffer(double limit, double decay) {
        this.limit = limit;
        this.decay = decay;
    }

    /** @return true -> limit aşıldı (kanıt üretilmeli). Tetiklendikten sonra yarıya düşer. */
    public boolean fail(double amount) {
        value += amount;
        if (value >= limit) {
            value = limit * 0.5;
            return true;
        }
        return false;
    }

    public void pass() { value = Math.max(0, value - decay); }
    public void reset() { value = 0; }
    public double value() { return value; }
}
