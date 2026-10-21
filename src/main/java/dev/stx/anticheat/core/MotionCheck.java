package dev.stx.anticheat.core;

import java.util.function.BooleanSupplier;

/**
 * Speed için vanilla yatay hareket modeli (ivme-artığı). Vanilla'da her tik:
 *   D[k+1] = f * D[k] + A      (f = sürtünme, A = oyuncunun girdisinden gelen ivme, |A| <= aMax)
 *   zıplama tikinde sprint ise ek +0.2 (bakış yönünde).
 * Gerçek D[k+1] ile f*D[k] arasındaki fark (artık) aMax'ı aşıyorsa oyuncu vanilla'nın izin vermediği ivmeyi kullanıyordur.
 * Sabit hız limitlerinin aksine buz, slime, hız efekti, zıplama ve önceki hıza duyarlıdır; bu yüzden yanlış pozitif çok düşüktür.
 * İki tikin arasında boşluk (lag/durma) varsa değerlendirme yapılmaz.
 */
public final class MotionCheck {
    private double pdx, pdz;
    private boolean have, s1, s2;
    private double slip1 = 0.6, slip2 = 0.6;
    private int skip;
    private double tolMul;
    private final double baseTolMul, tolAbs;
    private final FlagBuffer buf = new FlagBuffer(5, 0.4);

    public MotionCheck(double tolMul, double tolAbs) { this.baseTolMul = tolMul; this.tolMul = tolMul; this.tolAbs = tolAbs; }

    public void setHot(boolean h) { tolMul = h ? 1.0 + (baseTolMul - 1.0) * 0.4 : baseTolMul; buf.setScale(h ? 0.34 : 1.0); }

    public void reset() { have = false; skip = 3; buf.reset(); }

    private void sync(MoveSample s) {
        pdx = s.dx(); pdz = s.dz();
        s2 = s1; slip2 = slip1;
        s1 = s.standing(); slip1 = s.slip();
        have = true;
    }

    private static double residual(double dx, double dz, double pdx, double pdz, double f, double jx, double jz, boolean takeoff) {
        double ex = dx - f * pdx, ez = dz - f * pdz;
        double r = Math.hypot(ex, ez);
        if (takeoff) r = Math.min(r, Math.hypot(ex - jx, ez - jz));
        return r;
    }

    /** @param gapMs bir önceki pakete göre geçen süre; @param entityNear yalnızca ihlal şüphesinde çağrılır (itilme payı) */
    public Result onMove(MoveSample s, long gapMs, BooleanSupplier entityNear) {
        if (s.exempt() || s.velocity() || s.special() || gapMs > 85) { reset(); sync(s); return null; }
        if (!have || skip > 0) { if (skip > 0) skip--; sync(s); return null; }

        // Önceki tikteki sürtünme: tik başında yerdeydiyse blok sürtünmesi * 0.91, değilse 0.91.
        double fGround = slip2 * 0.91, fAir = 0.91;
        // Zıplama: tik başında yerde + şimdi havada + sprint -> +0.2 bakış yönünde
        boolean takeoff = s1 && !s.standing() && s.sprinting();
        double yr = Math.toRadians(s.yaw());
        double jx = -Math.sin(yr) * 0.2, jz = Math.cos(yr) * 0.2;

        double rg = residual(s.dx(), s.dz(), pdx, pdz, fGround, jx, jz, takeoff);
        double ra = residual(s.dx(), s.dz(), pdx, pdz, fAir, jx, jz, takeoff);
        // Tik başı durumu belirsizse (zemin<->hava geçişi) iki sürtünmeden lehe olanı alınır
        double r = (s2 && s1) ? rg : (!s2 && !s1) ? ra : Math.min(rg, ra);

        // İzin verilen azami ivme
        boolean groundAccel = s1 || s.standing();
        double sl = s1 ? slip1 : s.slip();
        double aGround = s.attr() * (0.216 / (sl * sl * sl));
        double aAir = s.sprinting() ? 0.026 : 0.02;
        double aMax = groundAccel ? Math.max(aGround, aAir) : aAir;
        double allowed = aMax * tolMul + tolAbs;

        Result out = null;
        if (r > allowed && entityNear != null && r <= allowed + 0.12 && entityNear.getAsBoolean()) {
            buf.pass();                                     // yakında varlık: itilme payı
        } else if (r > allowed) {
            double ratio = r / Math.max(1e-6, aMax);
            if (buf.fail(1 + Math.min(2.5, (ratio - tolMul) * 1.2)))
                out = Result.of(1.0, String.format(java.util.Locale.ROOT,
                        "Speed: ivme artığı=%.3f izin=%.3f (aMax=%.3f x%.1f) zemin=%b zıplama=%b hız=%.3f",
                        r, allowed, aMax, ratio, groundAccel, takeoff, s.hSpeed()));
        } else buf.pass();
        sync(s);
        return out;
    }
}
