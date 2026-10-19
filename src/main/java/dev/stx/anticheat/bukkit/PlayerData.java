package dev.stx.anticheat.bukkit;

import dev.stx.anticheat.core.*;
import dev.stx.anticheat.core.Geometry.Box;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.World;

/** Oyuncu başına durum. Yalnızca ana iş parçacığında kullanılır (Bukkit olayları). Bellek sabit boyutludur. */
public final class PlayerData {
    private static final int HIST = 40;

    public final UUID uuid;
    public final String name;
    public final Suspicion suspicion;

    public AirCheck air;
    public SpeedCheck speed;
    public TimerCheck timer;
    public NoFallCheck noFall;
    public NoSlowCheck noSlow;
    public VelocityCheck velocity;
    public ReachCheck reach;
    public AimCheck aim;
    public AuraCheck aura;
    public ClickCheck click;
    public RotationCheck rotation;
    public ScaffoldCheck scaffold;
    public CriticalsCheck criticals;
    public WindowCheck window;
    public JesusCheck jesus;
    public final FlagBuffer wallBuf = new FlagBuffer(4, 0.5);
    public final FlagBuffer phaseBuf = new FlagBuffer(3, 0.5);

    public volatile boolean bypass;
    public boolean bedrock;
    public long graceUntilTick, velocityUntilTick, liquidUntilTick, bounceUntilTick, pistonUntilTick,
            climbUntilTick, iceUntilTick, pingGraceUntilTick;
    public long lastAttackTick = -100000;
    public long exemptUntilMs, punishCooldownUntilMs, wouldCooldownUntilMs;
    public int lastPing = -1;

    public boolean hasLast;
    public double lastX, lastY, lastZ, lastHSpeed;
    public float lastYaw, lastPitch;
    public boolean lastStanding = true;
    private double yawAbs1, yawAbs2;

    public final Map<String, Long> lastAlertMs = new HashMap<>();

    /** Aktif geçici müdahaleler: tür (ATTACK/MOVE/PLACE/CONTAINER) -> bitiş (duvar saati ms). */
    public final Map<String, Long> mitUntil = new HashMap<>();
    public boolean selfSetback, setbackQueued, hasGood;
    public long lastSetbackMs;
    public World goodWorld;
    public double goodX, goodY, goodZ;
    public float goodYaw, goodPitch;

    public boolean mitActive(String type, long nowWall) {
        Long u = mitUntil.get(type);
        return u != null && u > nowWall;
    }

    public void setGood(World w, double x, double y, double z, float yaw, float pitch) {
        goodWorld = w; goodX = x; goodY = y; goodZ = z; goodYaw = yaw; goodPitch = pitch; hasGood = true;
    }

    private final long[] hT = new long[HIST];
    private final double[] hX = new double[HIST], hY = new double[HIST], hZ = new double[HIST];
    private int hHead, hCount;

    public PlayerData(UUID uuid, String name, STXConfig cfg) {
        this.uuid = uuid;
        this.name = name;
        this.suspicion = new Suspicion(cfg.d("suspicion.decay-per-second", 0.15));
        build(cfg);
    }

    /** Kontrolleri (yeniden) oluşturur; /stx reload sonrası da çağrılır. */
    public void build(STXConfig c) {
        air = new AirCheck(c.ci("Fly", "hover-ticks", 8), c.cd("Fly", "max-rise", 1.32));
        speed = new SpeedCheck(c.cd("Speed", "margin", 0.10), c.cd("Speed", "ice-multiplier", 2.2));
        timer = new TimerCheck(c.cd("Timer", "max-credit-ms", 800), c.cd("Timer", "tolerance-ms", 100));
        noFall = new NoFallCheck();
        noSlow = new NoSlowCheck(c.cd("NoSlow", "limit", 0.12));
        velocity = new VelocityCheck(c.cd("Velocity", "min-ratio", 1.2), c.cd("Velocity", "buffer", 3));
        reach = new ReachCheck(c.cd("Reach", "tolerance", 0.30), c.cd("Reach", "buffer", 6));
        aim = new AimCheck(c.cd("Aim", "expand", 0.15), c.cd("Aim", "buffer", 7));
        aura = new AuraCheck(c.ci("Aura", "multi-targets", 3), c.ci("Aura", "multi-ticks", 5), c.cd("Aura", "flick-degrees", 60));
        click = new ClickCheck(c.ci("AutoClicker", "max-cps", 22), c.ci("AutoClicker", "sustain-seconds", 3),
                c.ci("AutoClicker", "consistency-samples", 40), c.cd("AutoClicker", "consistency-min-stddev", 0.35));
        rotation = new RotationCheck(c.ci("Rotation", "window", 40), c.cd("Rotation", "min-step", 0.005));
        scaffold = new ScaffoldCheck(c.ci("Scaffold", "rate-per-20-ticks", 14));
        criticals = new CriticalsCheck();
        jesus = new JesusCheck(c.ci("Jesus", "ticks", 12));
        window = new WindowCheck(c.ci("ChestStealer", "min-open-ticks", 3), c.ci("ChestStealer", "samples", 12),
                c.cd("ChestStealer", "min-stddev", 0.3));
    }

    public void grace(long untilTick) { graceUntilTick = Math.max(graceUntilTick, untilTick); }

    public boolean inCombat(long tick) { return tick - lastAttackTick < 100; }

    public void pushYawDelta(double absYaw) { yawAbs2 = yawAbs1; yawAbs1 = absYaw; }
    public double recentYawDelta() { return Math.max(yawAbs1, yawAbs2); }

    public void pushPos(long ms, double x, double y, double z) {
        hT[hHead] = ms; hX[hHead] = x; hY[hHead] = y; hZ[hHead] = z;
        hHead = (hHead + 1) % HIST;
        if (hCount < HIST) hCount++;
    }

    /** sinceMs'den bu yana kaydedilen konumları kutu olarak ekler. */
    public void collectBoxes(long sinceMs, double w, double h, List<Box> out) {
        for (int k = 0; k < hCount; k++) {
            int idx = (hHead - 1 - k + HIST) % HIST;
            if (hT[idx] < sinceMs) break;
            out.add(Box.ofEntity(hX[idx], hY[idx], hZ[idx], w, h));
        }
    }

    /** Teleport/dünya değişimi sonrası referansları sıfırla. */
    public void resetMotionState() {
        hasLast = false;
        timer.reset();
        velocity.cancel();
        rotation.reset();
        hCount = 0;
    }
}
