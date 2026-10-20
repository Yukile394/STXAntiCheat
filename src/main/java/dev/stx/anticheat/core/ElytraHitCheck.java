package dev.stx.anticheat.core;

import java.util.ArrayDeque;

/**
 * ElytraTarget: elytra ile uçarken hedefe kilitli vuruş. Vuruşların çoğunda nişan hedefe milimetrik
 * kilitliyse (hedef gözü/merkezi hatası < eşik) veya "ileri bak, vuruş tikinde snap, geri dön" deseni varsa.
 * İnsan, saniyede birkaç blok hızla uçarken art arda milimetrik nişan tutamaz.
 */
public final class ElytraHitCheck {
    private final ArrayDeque<Boolean> win = new ArrayDeque<>();
    private final double lockedDeg;
    private final int window, need;

    public ElytraHitCheck(double lockedDeg, int window, int need) { this.lockedDeg = lockedDeg; this.window = window; this.need = need; }

    public void reset() { win.clear(); }

    public Result onHit(boolean gliding, boolean snapBack, double aimErrorDeg) {
        if (!gliding) return null;
        win.addLast(snapBack || aimErrorDeg < lockedDeg);
        if (win.size() > window) win.pollFirst();
        if (win.size() < window) return null;
        int c = 0; for (boolean b : win) if (b) c++;
        if (c >= need) {
            win.clear();
            return Result.of(2.0, "ElytraTarget: elytra ile son " + window + " vuruşun " + c + " tanesi kilitli nişan/snap (insan dışı)");
        }
        return null;
    }
}
