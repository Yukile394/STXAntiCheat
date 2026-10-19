package dev.stx.anticheat.core;

/**
 * Anti-Knockback tespiti. Sunucu bir hız verdiğinde, oyuncunun o yöndeki gerçek yer değiştirmesi
 * (ping'e göre genişletilmiş pencerede) beklenenle kıyaslanır. Duvar/tavan/sıvı gibi engel varsa
 * örnek hiç alınmaz.
 */
public final class VelocityCheck {
    private final double minRatio;
    private final FlagBuffer buf;

    private boolean active;
    private long deadline;
    private double ux, uz, hMag, vy;
    private double proj, peakProj, dyAcc, peakDy;
    private int moves;

    public VelocityCheck(double minRatio, double bufferLimit) {
        this.minRatio = minRatio;
        this.buf = new FlagBuffer(bufferLimit, 0.6);
    }

    /** @param skip true ise (engel/sıvı/muafiyet) bu knockback ölçülmez */
    public void onVelocity(double vx, double vyIn, double vz, boolean skip, long nowMs, int pingMs) {
        active = false;
        if (skip) return;
        double h = Math.hypot(vx, vz);
        if (h < 0.2 && vyIn < 0.3) return;
        if (h >= 0.2) { ux = vx / h; uz = vz / h; } else { ux = 0; uz = 0; }
        hMag = h >= 0.2 ? h : 0;
        vy = vyIn;
        proj = peakProj = dyAcc = peakDy = 0;
        moves = 0;
        deadline = nowMs + Math.min(pingMs, 400) + 500;
        active = true;
    }

    public void cancel() { active = false; }

    public Result onMove(MoveSample s, long nowMs) {
        if (!active) return null;
        if (s.exempt() || s.special()) { active = false; return null; }
        if (nowMs > deadline) return evaluate();
        moves++;
        proj += s.dx() * ux + s.dz() * uz;
        peakProj = Math.max(peakProj, proj);
        dyAcc += s.dy();
        peakDy = Math.max(peakDy, dyAcc);
        return null;
    }

    /** Hareket olmasa bile (cheat'te oyuncu hiç kıpırdamaz) süre dolunca değerlendirir. */
    public Result poll(long nowMs) {
        if (active && nowMs > deadline) return evaluate();
        return null;
    }

    private Result evaluate() {
        active = false;
        double ratio;
        String kind;
        if (hMag > 0) { ratio = peakProj / hMag; kind = "yatay"; }
        else { ratio = peakDy / vy; kind = "dikey"; }
        if (ratio < minRatio) {
            if (buf.fail(1)) return Result.of(1.0, String.format(java.util.Locale.ROOT,
                    "Velocity(%s) oran=%.2f (beklenen>=%.2f) knockback=%.2f", kind, ratio, minRatio, hMag > 0 ? hMag : vy));
        } else buf.pass();
        return null;
    }
}
