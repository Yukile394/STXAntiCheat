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
    public AimAssistCheck aimAssist;
    public ElytraCheck elytra;
    public ElytraHitCheck elytraHit;
    public RocketCadenceCheck rocket;
    public TrackingCheck tracking;
    public SnapShapeCheck snapShape;
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
    private double yaw1, yaw2;          // işaretli son iki yaw farkı

    public final Map<String, Long> lastAlertMs = new HashMap<>();

    /** Aktif geçici müdahaleler: tür (ATTACK/MOVE/PLACE/CONTAINER) -> bitiş (duvar saati ms). */
    public final Map<String, Long> mitUntil = new HashMap<>();
    public boolean selfSetback, setbackQueued, hasGood;
    /** Hareket hilesinden yakalandıktan sonra bu zamana kadar "hot": aynı hileye çok daha hızlı tepki, hepsi anında geri alınır. */
    public long moveHotUntilMs, moveGraceUntilTick, boostUntilTick, combatTargetTick = -100000, lastMoveMs;
    public java.util.UUID combatTarget;
    public double lastBearing;
    public boolean hasBearing, hotApplied;
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
        aimAssist = new AimAssistCheck(c.ci("AimAssist", "samples", 25), c.cd("AimAssist", "max-mean", 1.2), c.cd("AimAssist", "max-stddev", 0.7));
        click = new ClickCheck(c.ci("AutoClicker", "max-cps", 22), c.ci("AutoClicker", "sustain-seconds", 3),
                c.ci("AutoClicker", "consistency-samples", 40), c.cd("AutoClicker", "consistency-min-stddev", 0.35));
        rotation = new RotationCheck(c.ci("Rotation", "window", 40), c.cd("Rotation", "min-step", 0.005));
        scaffold = new ScaffoldCheck(c.ci("Scaffold", "rate-per-20-ticks", 14));
        criticals = new CriticalsCheck();
        elytra = new ElytraCheck();
        elytraHit = new ElytraHitCheck(c.cd("ElytraTarget", "locked-degrees", 1.8), c.ci("ElytraTarget", "window", 6), c.ci("ElytraTarget", "need", 4));
        rocket = new RocketCadenceCheck(c.ci("ElytraTarget", "rocket-samples", 6), c.cd("ElytraTarget", "rocket-max-stddev-ticks", 2.5));
        tracking = new TrackingCheck(c.ci("Aura", "tracking-window", 60), c.cd("Aura", "tracking-min-on-target", 0.985), c.cd("Aura", "tracking-min-bearing", 2.0));
        snapShape = new SnapShapeCheck(c.cd("Aura", "snap-degrees", 35), c.ci("Aura", "snap-count", 5), c.ci("Aura", "snap-window-ticks", 80));
        jesus = new JesusCheck(c.ci("Jesus", "ticks", 12));
        window = new WindowCheck(c.ci("ChestStealer", "min-open-ticks", 3), c.ci("ChestStealer", "samples", 12),
                c.cd("ChestStealer", "min-stddev", 0.3));
    }

    /** Hot mod: tüm hareket kontrollerini hassaslaştırır / geri alır. Yalnızca durum değişince çalışır. */
    public void setHot(boolean h) {
        if (h == hotApplied) return;
        hotApplied = h;
        air.setHot(h); speed.setHot(h); noFall.setHot(h); noSlow.setHot(h); elytra.setHot(h);
    }

    public void grace(long untilTick) { graceUntilTick = Math.max(graceUntilTick, untilTick); }

    public boolean inCombat(long tick) { return tick - lastAttackTick < 100; }

    public void pushYawDelta(double signedYaw) { yaw2 = yaw1; yaw1 = signedYaw; }
    public double recentYawDelta() { return Math.max(Math.abs(yaw1), Math.abs(yaw2)); }
    /** Sessiz rotasyon: hedefe hızla dön, vur, geri dön (iki ardışık büyük zıt yönlü dönüş). */
    public boolean snapBack() { return yaw1 * yaw2 < 0 && Math.min(Math.abs(yaw1), Math.abs(yaw2)) > 35; }

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
        elytra.reset(); tracking.reset(); snapShape.reset(); hasBearing = false; hotApplied = false;
        timer.reset();
        velocity.cancel();
        rotation.reset();
        hCount = 0;
    }
}
