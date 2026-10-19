package dev.stx.anticheat.core;

import java.util.ArrayList;
import java.util.List;

/** ChestStealer / AutoTotem tıklama hızı ve GuiMove (konteyner açıkken yürüme). */
public final class WindowCheck {
    private final int minOpenTicks;
    private final int samples;
    private final double minStdDev;
    private long openTick = Long.MIN_VALUE, lastClick = Long.MIN_VALUE;
    private final List<Integer> gaps = new ArrayList<>();
    private final FlagBuffer moveBuf = new FlagBuffer(8, 0.5);

    public WindowCheck(int minOpenTicks, int samples, double minStdDev) {
        this.minOpenTicks = minOpenTicks; this.samples = samples; this.minStdDev = minStdDev;
    }

    public void onOpen(long tick) { openTick = tick; lastClick = Long.MIN_VALUE; gaps.clear(); moveBuf.reset(); }
    public void onClose() { openTick = Long.MIN_VALUE; gaps.clear(); }

    /** Dış konteynerde (sandık vb.) shift-tıklama. */
    public Result onContainerClick(long tick) {
        if (openTick == Long.MIN_VALUE) return null;
        Result r = null;
        if (lastClick == Long.MIN_VALUE && tick - openTick < minOpenTicks)
            r = Result.of(1.0, "ChestStealer: konteyner açıldıktan " + (tick - openTick) + " tik sonra ilk tıklama (<" + minOpenTicks + ")");
        if (lastClick != Long.MIN_VALUE) {
            long g = tick - lastClick;
            if (g <= 10) gaps.add((int) g);
            if (gaps.size() >= samples) {
                double sd = Stats.stdDev(gaps), mean = Stats.mean(gaps);
                gaps.clear();
                if (sd < minStdDev && mean <= 3 && r == null)
                    r = Result.of(0.75, String.format(java.util.Locale.ROOT,
                            "ChestStealer ritim: %d tıklama, ort=%.2f tik, sapma=%.3f", samples, mean, sd));
            }
        }
        lastClick = tick;
        return r;
    }

    /** Konteyner açıkken hareket (GuiMove). */
    public Result onMoveWhileOpen(long tick, double hSpeed, boolean ground, boolean exempt) {
        if (openTick == Long.MIN_VALUE || exempt) { moveBuf.reset(); return null; }
        if (tick - openTick < 12) return null;        // momentum sönsün
        if (ground && hSpeed > 0.12) {
            if (moveBuf.fail(1)) return Result.of(1.0, String.format(java.util.Locale.ROOT,
                    "GuiMove: konteyner açıkken hız=%.3f (%d tik açık)", hSpeed, tick - openTick));
        } else moveBuf.pass();
        return null;
    }
}
