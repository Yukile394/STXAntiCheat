package dev.stx.anticheat.core;

/**
 * Eşya kullanırken (yemek/yay/kalkan) yavaşlama atlatma. Vanilla'da giriş x0.2 olur.
 * Havadaki momentum (bunny-hop sırasında yemek vb.) meşru olduğundan YALNIZCA yerde, art arda
 * en az 4 tik durduktan sonra değerlendirilir.
 */
public final class NoSlowCheck {
    private final double limit;
    private int usingTicks, groundTicks;
    private final FlagBuffer buf = new FlagBuffer(3, 0.5);
    private final FlagBuffer sprintBuf = new FlagBuffer(6, 0.5);

    public NoSlowCheck(double limit) { this.limit = limit; }

    public void setHot(boolean h) { buf.setScale(h ? 0.34 : 1.0); sprintBuf.setScale(h ? 0.5 : 1.0); }

    public Result onMove(MoveSample s) {
        if (!s.usingItem() || s.exempt() || s.velocity() || s.special() || !s.standing()) {
            usingTicks = 0; groundTicks = s.standing() ? groundTicks + 1 : 0;
            if (!s.usingItem()) { buf.reset(); sprintBuf.reset(); }
            return null;
        }
        groundTicks++;
        usingTicks++;
        if (usingTicks < 5 || groundTicks < 4) return null;       // momentumun sönmesi için süre tanı
        // Vanilla'da eşya kullanırken sprint EDİLEMEZ; sunucu hâlâ sprint görüyorsa ve hız da yüksekse NoSlow kanıtıdır.
        if (s.sprinting() && usingTicks >= 7 && s.hSpeed() > limit * 0.8 * s.speedMul()) {
            if (sprintBuf.fail(1.5))
                return Result.of(1.0, String.format(java.util.Locale.ROOT,
                        "NoSlow: eşya kullanırken sprint (%d tik) hız=%.3f", usingTicks, s.hSpeed()));
        } else sprintBuf.pass();
        double lim = limit * s.speedMul() * (s.ice() ? 2.0 : 1.0);
        if (s.hSpeed() > lim) {
            if (buf.fail(1 + Math.min(2, (s.hSpeed() - lim) * 6)))
                return Result.of(1.0, String.format(java.util.Locale.ROOT,
                        "NoSlow hız=%.3f limit=%.3f (eşya kullanırken)", s.hSpeed(), lim));
        } else buf.pass();
        return null;
    }
}
