package dev.stx.anticheat.core;

/** Jesus/WaterWalk: sıvı yüzeyinde (içinde değil, üstünde) düz ve yatay ilerleme. Vanilla'da imkansızdır. */
public final class JesusCheck {
    private final int limit;
    private int ticks;

    public JesusCheck(int limit) { this.limit = limit; }

    /**
     * @param onLiquidSurface ayak altı sıvı, ayak bloğu sıvı değil, sunucu zemin bulmuyor, yüzmüyor
     */
    public Result onMove(boolean onLiquidSurface, double dy, double hSpeed, boolean exempt) {
        if (exempt || !onLiquidSurface || Math.abs(dy) > 0.03 || hSpeed < 0.04) { ticks = 0; return null; }
        if (++ticks >= limit) {
            ticks = 0;
            return Result.of(1.0, String.format(java.util.Locale.ROOT, "Jesus: sıvı yüzeyinde %d tik düz ilerleme (hız=%.3f)", limit, hSpeed));
        }
        return null;
    }
}
