package dev.stx.anticheat.core;

import java.util.ArrayDeque;

/** Scaffold/Tower: yanlış yüz, bakış-ışın uyuşmazlığı, aşırı yerleştirme hızı. */
public final class ScaffoldCheck {
    private final ArrayDeque<Long> placeTicks = new ArrayDeque<>();
    private final int rateLimit;
    private final FlagBuffer faceBuf = new FlagBuffer(2, 0.5);
    private final FlagBuffer rayBuf = new FlagBuffer(6, 0.5);
    private final FlagBuffer rateBuf = new FlagBuffer(2, 0.5);

    public ScaffoldCheck(int ratePer20Ticks) { this.rateLimit = ratePer20Ticks; }

    /**
     * @param wrongFace  göz, tıklanan yüzün ARKA tarafında (vanilla istemci bunu tıklayamaz)
     * @param rayMiss    gerçek bakış ışını yerleştirmenin dayandığı bloğa isabet etmiyor
     * @param hSpeed     yerleştirme anındaki yatay hız
     */
    public Result onPlace(long tick, boolean wrongFace, boolean rayMiss, double hSpeed) {
        placeTicks.addLast(tick);
        while (!placeTicks.isEmpty() && tick - placeTicks.peekFirst() > 20) placeTicks.pollFirst();

        Result r = null;
        if (wrongFace) {
            if (faceBuf.fail(1)) r = Result.of(1.0, "Scaffold: blok yüzü gözün arkasında (imkansız tıklama)");
        } else faceBuf.pass();

        if (rayMiss) {
            if (rayBuf.fail(1) && r == null) r = Result.of(1.0, "Scaffold: bakış ışını yerleştirilen bloğa isabet etmiyor");
        } else rayBuf.pass();

        if (placeTicks.size() >= rateLimit && hSpeed > 0.1) {
            if (rateBuf.fail(1) && r == null)
                r = Result.of(1.0, "Scaffold hız: " + placeTicks.size() + " blok / 20 tik, hız=" + String.format(java.util.Locale.ROOT, "%.2f", hSpeed));
        } else rateBuf.pass();
        return r;
    }
}
