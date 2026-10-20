package dev.stx.anticheat.core;

import java.util.function.BooleanSupplier;

/** İstemcinin onGround iddiası ile sunucunun çarpışma kontrolünü karşılaştırır. */
public final class NoFallCheck {
    private double fall, lastDy;
    private int skip;

    public void setHot(boolean h) { groundBuf.setScale(h ? 0.5 : 1.0); desyncBuf.setScale(h ? 0.5 : 1.0); }
    private final FlagBuffer groundBuf = new FlagBuffer(3, 0.5);
    private final FlagBuffer desyncBuf = new FlagBuffer(3, 0.5);

    private void reset(MoveSample s) { fall = 0; lastDy = s.dy(); skip = 3; groundBuf.reset(); desyncBuf.reset(); }

    /** @param solidBelow lazy: ayaklar altında gerçekten çarpışma var mı (pahalı olduğu için yalnızca gerekirse çağrılır) */
    public Result onMove(MoveSample s, BooleanSupplier solidBelow) {
        if (s.exempt() || s.special() || s.velocity() || s.gravityOff()) { reset(s); return null; }
        if (skip > 0) { skip--; lastDy = s.dy(); return null; }

        Result r = null;
        if (s.clientGround()) {
            boolean suspicious = lastDy < -0.15 || fall > 2.0 || s.dy() < -0.3;
            if (suspicious) {
                if (!solidBelow.getAsBoolean()) {
                    if (groundBuf.fail(1)) r = Result.of(1.0, String.format(java.util.Locale.ROOT,
                            "NoFall: onGround=true ama altta blok yok (düşüş=%.2f, lastDy=%.2f)", fall, lastDy));
                } else {
                    groundBuf.pass();
                    fall = 0;
                }
            } else {
                fall = 0;
            }
        } else {
            fall = Math.max(0, fall - s.dy());
            if (fall > 4.0 && s.serverFall() < fall * 0.4) {
                if (desyncBuf.fail(1)) r = Result.of(1.0, String.format(java.util.Locale.ROOT,
                        "NoFall: fallDistance sıfırlanmış (gerçek=%.2f sunucu=%.2f)", fall, s.serverFall()));
            } else {
                desyncBuf.pass();
            }
        }
        lastDy = s.dy();
        return r;
    }
}
