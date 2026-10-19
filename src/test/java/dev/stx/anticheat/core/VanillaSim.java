package dev.stx.anticheat.core;

import java.util.ArrayList;
import java.util.List;

/** Vanilla (1.21) oyuncu fiziğinin sadeleştirilmiş simülasyonu: düz zemin, ileri hareket. */
final class VanillaSim {
    boolean onGround = true;
    double y = 0, vy = 0, vz = 0, vx = 0;
    long tick = 0;
    double fall = 0;
    double jumpBoost = 0;   // 0.1 * level

    /** @param sprint sprint, @param jump zıplama tuşu, @param forward ileri tuşu */
    MoveSample step(boolean sprint, boolean jump, double input, double speedMul) {
        if (jump && onGround) {
            vy = 0.42 + jumpBoost;
            if (sprint) vz += 0.2;
        }
        boolean og = onGround;
        double attr = (sprint ? 0.13 : 0.1) * speedMul;
        double a = input * (og ? attr * 0.98 : 0.02 * (sprint ? 1.3 : 1.0) * 0.98);
        vz += a;
        double dz = vz, dy = vy;
        if (y + dy <= 0) { dy = -y; y = 0; onGround = true; vy = 0; }
        else { y += dy; onGround = false; }
        if (onGround) fall = 0; else if (dy < 0) fall -= dy;
        double f = og ? 0.6 * 0.91 : 0.91;
        vz *= f; vx *= f;
        vy = (vy - 0.08) * 0.98;
        tick++;
        return new MoveSample(tick, 0, dy, dz, 0, 0, onGround, onGround, false, false, false, false, false,
                speedMul, jumpBoost > 0 ? (int) Math.round(jumpBoost * 10) : 0, false, fall);
    }

    static List<MoveSample> run(int ticks, boolean sprint, boolean bhop, double speedMul) {
        VanillaSim s = new VanillaSim();
        List<MoveSample> out = new ArrayList<>();
        for (int i = 0; i < ticks; i++) out.add(s.step(sprint, bhop, 1.0, speedMul));
        return out;
    }
}
