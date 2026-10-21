package dev.stx.anticheat.core;

import java.util.Random;

/** Vanilla 1.21 yatay+dikey oyuncu fiziği: rastgele strafe/çapraz/zıplama/sprint/buz/hız efekti. */
final class Sim2D {
    final Random r;
    double vx, vz, vy, y;
    boolean og = true, sprint;
    double yaw, slip = 0.6;
    final int speedLevel;
    long tick;
    int iceLeft;
    double cheatMul = 1.0, cheatAdd = 0.0;      // hile sapması: 1.25x veya +0.12 ekleme

    Sim2D(long seed, int speedLevel) { r = new Random(seed); this.speedLevel = speedLevel; }

    MoveSample step() {
        // --- girdi ---
        double fwd = r.nextDouble() < 0.85 ? 1 : (r.nextDouble() < 0.5 ? 0 : -1);
        double str = r.nextDouble() < 0.3 ? (r.nextBoolean() ? 1 : -1) : 0;
        boolean jump = r.nextDouble() < 0.3;
        if (fwd > 0 && !sprint && r.nextDouble() < 0.5) sprint = true;
        if (fwd <= 0) sprint = false;
        if (r.nextDouble() < 0.02) yaw += r.nextInt(180) - 90; else yaw += r.nextGaussian() * 4;
        if (iceLeft > 0) iceLeft--; else if (r.nextDouble() < 0.01) { iceLeft = 60; }
        double surf = iceLeft > 0 ? (r.nextBoolean() ? 0.98 : 0.989) : 0.6;
        // sürtünme tik BAŞINDAKİ zemine göre; zemin yoksa 0.91
        boolean ogStart = og;
        double slipStart = slip;

        double attr = (sprint ? 0.13 : 0.1) * (1 + 0.2 * speedLevel);
        if (jump && og) {
            vy = 0.42;
            if (sprint) { vx += -Math.sin(Math.toRadians(yaw)) * 0.2; vz += Math.cos(Math.toRadians(yaw)) * 0.2; }
        }
        double ix = str * 0.98, iz = fwd * 0.98, len = Math.hypot(ix, iz);
        if (len > 1) { ix /= len; iz /= len; }
        double a = og ? attr * (0.216 / (slipStart * slipStart * slipStart)) : (sprint ? 0.026 : 0.02);
        double ax = 0, az = 0;
        if (len > 1e-7) {
            double sn = Math.sin(Math.toRadians(yaw)), cs = Math.cos(Math.toRadians(yaw));
            ax = a * (ix * cs - iz * sn); az = a * (iz * cs + ix * sn);
        }
        double dx = vx + ax, dz = vz + az;
        dx = dx * cheatMul + (cheatAdd != 0 ? -Math.sin(Math.toRadians(yaw)) * cheatAdd : 0);
        dz = dz * cheatMul + (cheatAdd != 0 ? Math.cos(Math.toRadians(yaw)) * cheatAdd : 0);
        double dy = vy;
        boolean landed = false;
        if (y + dy <= 0) { dy = -y; y = 0; landed = true; vy = 0; } else y += dy;
        og = landed || (y == 0 && vy <= 0);
        slip = og ? surf : slip;
        double f = ogStart ? slipStart * 0.91 : 0.91;
        vx = dx * f; vz = dz * f;
        vy = (vy - 0.08) * 0.98;
        tick++;
        return new MoveSample(tick, dx, dy, dz, (float) yaw, 0f, og, og, false, false, false, false, surf > 0.6, 1.0, 0, false, 0,
                og ? surf : 0.6, sprint, attr);
    }
}
