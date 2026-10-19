package dev.stx.anticheat.core;

/** Eşya kullanırken (yemek/yay/kalkan) yavaşlama atlatma. Vanilla'da giriş ×0.2 olur. */
public final class NoSlowCheck {
    private final double limit;
    private int usingTicks;
    private final FlagBuffer buf = new FlagBuffer(5, 0.5);

    public NoSlowCheck(double limit) { this.limit = limit; }

    public Result onMove(MoveSample s) {
        if (!s.usingItem() || s.exempt() || s.velocity() || s.special()) { usingTicks = 0; buf.reset(); return null; }
        usingTicks++;
        if (usingTicks < 6) return null;               // momentumun sönmesi için süre tanı
        double lim = limit * s.speedMul() * (s.ice() ? 2.0 : 1.0);
        if (s.hSpeed() > lim) {
            if (buf.fail(1)) return Result.of(1.0, String.format(java.util.Locale.ROOT,
                    "NoSlow hız=%.3f limit=%.3f (eşya kullanırken)", s.hSpeed(), lim));
        } else buf.pass();
        return null;
    }
}
