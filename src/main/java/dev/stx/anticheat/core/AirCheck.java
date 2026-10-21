package dev.stx.anticheat.core;

/**
 * Dikey hareket modeli: Fly, HighJump, Step, Spider (yukarı sapma), FastFall (Aura pullDown), Hover.
 * Vanilla yerçekimi: dy' = (dy - 0.08) * 0.98.
 */
public final class AirCheck {
    public static final double STEP_LIMIT = 0.62;
    private static final double UP_TOLERANCE = 0.03;
    private static final double DOWN_TOLERANCE = 0.12;

    private boolean prevGround = true;
    private double lastDy;
    private int airTicks, skip, hover;
    private double rise, height;

    private final FlagBuffer flyBuf = new FlagBuffer(3, 0.5);
    private final FlagBuffer fallBuf = new FlagBuffer(3, 0.5);
    private final FlagBuffer stepBuf = new FlagBuffer(2, 0.5);
    private final int hoverTicks;
    private boolean hot;
    private final double maxRise;

    public AirCheck(int hoverTicks, double maxRise) {
        this.hoverTicks = hoverTicks;
        this.maxRise = maxRise;
    }

    public AirCheck() { this(8, 1.32); }

    /** Hot mod: oyuncu az önce hareket hilesinden yakalandı; aynı hileye çok daha hızlı tepki ver. */
    public void setHot(boolean h) {
        hot = h;
        flyBuf.setScale(h ? 0.34 : 1.0);
        fallBuf.setScale(h ? 0.34 : 1.0);
        stepBuf.setScale(h ? 0.5 : 1.0);
    }

    private void resetState(MoveSample s) {
        prevGround = s.standing();
        lastDy = s.dy();
        airTicks = 0; hover = 0; rise = 0; height = 0;
        skip = 3;
        flyBuf.reset(); fallBuf.reset();
    }

    /** @return kanıt veya null. check adı Result.detail içinde "[Tür]" öneki ile gelir. */
    public Result onMove(MoveSample s) {
        if (s.exempt() || s.special() || s.velocity() || s.gravityOff()) {
            resetState(s);
            return null;
        }
        if (skip > 0) {
            skip--;
            prevGround = s.standing();
            lastDy = s.dy();
            return null;
        }

        Result r = null;
        if (s.standing()) {
            if (prevGround && s.dy() > STEP_LIMIT) {
                if (stepBuf.fail(1)) r = Result.of(1.0, "Step dy=" + fmt(s.dy()) + " limit=" + STEP_LIMIT);
            } else {
                stepBuf.pass();
            }
            airTicks = 0; rise = 0; hover = 0; height = 0;
        } else if (prevGround) {
            // Kalkış tiki
            // Kalkış tikinde basamak/slab çıkışı (0.5-0.6) meşrudur: sınır = max(zıplama, adım yüksekliği 0.6) + pay
            double max = Math.max(0.6, 0.42 + 0.1 * s.jumpLevel()) + 0.05;
            if (s.dy() > max) r = Result.of(1.0, "HighJump takeoff dy=" + fmt(s.dy()) + " max=" + fmt(max));
            airTicks = 0;
            height = s.dy();
            rise = Math.max(0, height);
            hover = 0;
        } else {
            airTicks++;
            double predicted = (lastDy - 0.08) * 0.98;
            double diff = s.dy() - predicted;
            if (diff > UP_TOLERANCE) {
                if (flyBuf.fail(1)) r = Result.of(1.0, "Fly dy=" + fmt(s.dy()) + " beklenen=" + fmt(predicted) + " fark=" + fmt(diff));
            } else if (predicted < -0.1 && diff < -DOWN_TOLERANCE) {
                if (fallBuf.fail(1)) r = Result.of(1.0, "FastFall dy=" + fmt(s.dy()) + " beklenen=" + fmt(predicted));
            } else {
                flyBuf.pass(); fallBuf.pass();
            }
            height += s.dy();                           // NET yükseklik (aşağı inişler düşülür)
            rise = Math.max(rise, height);
            hover = Math.abs(s.dy()) < 0.02 ? hover + 1 : 0;
            if (r == null && hover >= (hot ? Math.max(3, hoverTicks / 2) : hoverTicks)) {
                r = Result.of(1.0, "Hover " + hover + " tik havada sabit");
                hover = 0;
            }
            if (r == null && s.jumpLevel() == 0 && rise > maxRise) {
                r = Result.of(1.0, "HighJump yükselme=" + fmt(rise) + " max=" + fmt(maxRise));
                rise = 0;
            }
        }
        prevGround = s.standing();
        lastDy = s.dy();
        return r;
    }

    private static String fmt(double d) { return String.format(java.util.Locale.ROOT, "%.3f", d); }
}
