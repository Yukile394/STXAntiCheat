package dev.stx.anticheat.core;

/**
 * Elytra hız/yön doğrulaması: vanilla LivingEntity.updateFallFlyingMovement formülü bir önceki hız
 * ve o tikteki bakış yönüyle bir sonraki hızı TAHMİN eder. Gerçek yer değiştirme tahminden fazlaysa
 * (ElytraPlus sabit hız, boost, ySpeed...) şüphe doğar. Çarpışmalar hızı yalnızca azalttığı için tek yönlüdür.
 * Havai fişek aktifken: üst hız sınırı + (bakış sabitse) hız yönünün bakışa yakınsaması aranır.
 */
public final class ElytraCheck {
    private double vx, vy, vz;
    private boolean have;
    private int skip, boostTicks, lookStable;
    private float lastYaw, lastPitch;
    private final FlagBuffer speedBuf = new FlagBuffer(4, 0.5);
    private final FlagBuffer boostBuf = new FlagBuffer(4, 0.5);
    private final FlagBuffer driftBuf = new FlagBuffer(8, 0.25);   // her tik küçük ama SÜREKLİ fazlalık (sabit hız hileleri)

    public void setHot(boolean h) { speedBuf.setScale(h ? 0.5 : 1.0); boostBuf.setScale(h ? 0.5 : 1.0); driftBuf.setScale(h ? 0.5 : 1.0); }

    public void reset() { have = false; skip = 3; boostTicks = 0; lookStable = 0; speedBuf.reset(); boostBuf.reset(); driftBuf.reset(); }

    /** Tahmin edilen bir sonraki hız (vanilla). out = {x,y,z} */
    static void predict(double vx, double vy, double vz, float pitch, double[] look, double[] out) {
        double lx = look[0], ly = look[1], lz = look[2];
        double f = Math.toRadians(pitch);
        double d = Math.sqrt(lx * lx + lz * lz);
        double d1 = Math.hypot(vx, vz);
        double d2 = Math.pow(Math.cos(f), 2);
        double px = vx, py = vy + 0.08 * (-1.0 + d2 * 0.75), pz = vz;
        if (py < 0 && d > 0) { double d3 = py * -0.1 * d2; px += lx * d3 / d; py += d3; pz += lz * d3 / d; }
        if (f < 0 && d > 0) { double d4 = d1 * (-Math.sin(f)) * 0.04; px += -lx * d4 / d; py += d4 * 3.2; pz += -lz * d4 / d; }
        if (d > 0) { px += (lx / d * d1 - px) * 0.1; pz += (lz / d * d1 - pz) * 0.1; }
        out[0] = px * 0.99; out[1] = py * 0.98; out[2] = pz * 0.99;
    }

    /**
     * @param gapMs     bir önceki pakete göre geçen süre (büyük boşluk = lag/blink: değerlendirme yapma)
     * @param boost     havai fişek aktif
     * @param skipEval  hız verildi / sıvı / özel ortam
     */
    public Result onMove(double dx, double dy, double dz, float yaw, float pitch, boolean boost, boolean skipEval, long gapMs) {
        if (skipEval || gapMs > 250) { reset(); remember(dx, dy, dz, yaw, pitch); return null; }
        double[] look = Geometry.look(yaw, pitch);
        double dLook = Math.abs(Geometry.wrapDegrees(yaw - lastYaw)) + Math.abs(pitch - lastPitch);
        lookStable = dLook < 8 ? lookStable + 1 : 0;
        Result r = null;
        if (have && skip <= 0) {
            double aSpeed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!boost) {
                boostTicks = 0;
                double[] p = new double[3];
                predict(vx, vy, vz, pitch, look, p);
                double pSpeed = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
                double excess = aSpeed - pSpeed, upExcess = dy - p[1];
                if (excess > 0.05 + 0.03 * pSpeed || upExcess > 0.08 + 0.02 * Math.abs(p[1])) {
                    if (speedBuf.fail(1 + Math.min(2, Math.max(excess, upExcess) * 4)))
                        r = Result.of(1.0, String.format(java.util.Locale.ROOT,
                                "ElytraSpeed gerçek=%.3f vanilla tahmin=%.3f (fazla=%.3f, dy fazlası=%.3f)", aSpeed, pSpeed, excess, upExcess));
                } else speedBuf.pass();
                // Vanilla'da fazlalık ~0'dır (çarpışma yalnızca azaltır). Küçük ama sürekli fazlalık = hile.
                if (excess > 0.015 + 0.003 * pSpeed || upExcess > 0.015) {
                    if (driftBuf.fail(1) && r == null)
                        r = Result.of(1.0, String.format(java.util.Locale.ROOT,
                                "ElytraSpeed sürekli fazlalık: gerçek=%.3f tahmin=%.3f (fazla=%.3f, dy fazlası=%.3f)", aSpeed, pSpeed, excess, upExcess));
                } else driftBuf.pass();
            } else {
                boostTicks++;
                boolean bad = aSpeed > 2.8;
                if (!bad && boostTicks >= 8 && lookStable >= 6 && aSpeed > 0.3) {
                    double cos = (dx * look[0] + dy * look[1] + dz * look[2]) / aSpeed;
                    bad = cos < 0.88;      // roket hızı bakış yönüne yakınsar
                }
                if (bad) {
                    if (boostBuf.fail(1))
                        r = Result.of(1.0, String.format(java.util.Locale.ROOT,
                                "ElytraSpeed (roket) hız=%.3f yön bakışla uyuşmuyor", aSpeed));
                } else boostBuf.pass();
            }
        } else if (skip > 0) skip--;
        remember(dx, dy, dz, yaw, pitch);
        have = true;
        return r;
    }

    private void remember(double dx, double dy, double dz, float yaw, float pitch) {
        vx = dx; vy = dy; vz = dz; lastYaw = yaw; lastPitch = pitch;
    }
}
