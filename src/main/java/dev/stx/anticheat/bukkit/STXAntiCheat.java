package dev.stx.anticheat.bukkit;

import dev.stx.anticheat.core.PunishPolicy;
import dev.stx.anticheat.core.Result;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class STXAntiCheat extends JavaPlugin {
    private STXConfig cfg;
    private PunishPolicy policy;
    private Flagger flagger;
    private ViolationLog vlog;
    private volatile long tick;
    private volatile double tps = 20.0;
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private final Set<UUID> alertsOff = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        vlog = new ViolationLog(getDataFolder().toPath().resolve("logs"), cfg.i("logging.queue-size", 20000),
                cfg.i("logging.keep-days", 14), getLogger());
        vlog.start();
        flagger = new Flagger(this);

        Bukkit.getPluginManager().registerEvents(new StateListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MovementListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new InventoryListener(this), this);

        PluginCommand cmd = getCommand("stx");
        if (cmd != null) {
            StxCommand ex = new StxCommand(this);
            cmd.setExecutor(ex);
            cmd.setTabCompleter(ex);
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> tick++, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, this::heartbeat, 20L, 20L);

        for (Player p : Bukkit.getOnlinePlayers()) data(p);      // /reload veya geç yükleme
        getLogger().info("STXAntiCheat etkin. Ceza: " + (cfg.punishmentActive() ? "AÇIK" : "KAPALI (yalnızca kayıt/uyarı)"));
        if (cfg.b("punishment.enabled", false) && !cfg.b("punishment.i-have-reviewed-the-logs", false))
            getLogger().warning("punishment.enabled=true ama i-have-reviewed-the-logs=false: ceza VERİLMEYECEK.");
    }

    @Override
    public void onDisable() {
        if (vlog != null) {
            if (vlog.dropped() > 0) getLogger().warning(vlog.dropped() + " log kaydı kuyruk dolduğu için düşürüldü.");
            vlog.stop();
        }
        players.clear();
    }

    void loadSettings() {
        reloadConfig();
        cfg = new STXConfig(getConfig());
        policy = new PunishPolicy(cfg.thresholds());
        for (PlayerData d : players.values()) d.build(cfg);
    }

    public void reloadAll() { loadSettings(); }

    // ---- erişimciler ----
    STXConfig cfg() { return cfg; }
    PunishPolicy policy() { return policy; }
    Flagger flagger() { return flagger; }
    ViolationLog violationLog() { return vlog; }
    long tick() { return tick; }
    double tps() { return tps; }
    static long nowMs() { return System.nanoTime() / 1_000_000L; }

    PlayerData data(Player p) {
        return players.computeIfAbsent(p.getUniqueId(), id -> {
            PlayerData d = new PlayerData(id, p.getName(), cfg);
            d.grace(tick + cfg.i("safety.join-grace-ticks", 100));
            d.bypass = p.hasPermission(cfg.s("general.bypass-permission", "stx.bypass"));
            String pre = cfg.s("general.bedrock-prefix", ".");
            d.bedrock = pre != null && !pre.isEmpty() && p.getName().startsWith(pre);
            return d;
        });
    }

    PlayerData peek(UUID id) { return players.get(id); }
    void remove(UUID id) { players.remove(id); alertsOff.remove(id); }
    Iterable<PlayerData> all() { return players.values(); }

    boolean alertsEnabled(UUID id) { return !alertsOff.contains(id); }
    boolean toggleAlerts(UUID id) { if (alertsOff.remove(id)) return true; alertsOff.add(id); return false; }

    /** Lag/bağlantı koşulları nedeniyle bu oyuncudan KANIT toplanmamalı mı? */
    boolean lagging(Player p, PlayerData d) {
        if (tps < cfg.d("safety.min-tps", 18.0)) return true;
        if (p.getPing() > cfg.i("safety.max-ping-ms", 400)) return true;
        return tick < d.pingGraceUntilTick;
    }

    /** Sert muafiyet nedeni; null = muaf değil. */
    String exemptReason(Player p, PlayerData d) {
        if (d.bypass) return "bypass";
        if (nowMs() < d.exemptUntilMs) return "exempt";
        if (tick < d.graceUntilTick) return "grace";
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return "gamemode";
        if (p.getAllowFlight() || p.isFlying()) return "flight";
        if (p.isGliding()) return "elytra";
        if (p.isInsideVehicle()) return "vehicle";
        if (p.isRiptiding()) return "riptide";
        if (p.isDead() || p.isSleeping()) return "state";
        if (cfg.list("general.disabled-worlds").contains(p.getWorld().getName())) return "world";
        return null;
    }

    private void heartbeat() {
        try { tps = Math.min(20.0, Bukkit.getTPS()[0]); } catch (Throwable ignored) { }
        long now = nowMs();
        String perm = cfg.s("general.bypass-permission", "stx.bypass");
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = peek(p.getUniqueId());
            if (d == null) continue;
            d.bypass = p.hasPermission(perm);
            int ping = p.getPing();
            if (d.lastPing >= 0 && Math.abs(ping - d.lastPing) > cfg.i("safety.ping-jump-ms", 120))
                d.pingGraceUntilTick = tick + cfg.i("safety.ping-jump-grace-ticks", 60);
            d.lastPing = ping;
            if (lagging(p, d) || exemptReason(p, d) != null) { d.velocity.cancel(); continue; }
            Result r = d.velocity.poll(now);        // hiç hareket etmeyen (knockback'i yutan) oyuncu için
            if (r != null) flagger.flag(p, d, "Velocity", r);
        }
    }

    List<String> checkNames() {
        return List.of("Reach", "Aim", "Aura", "AutoClicker", "Velocity", "Fly", "Step", "HighJump", "Speed", "Timer",
                "NoFall", "NoSlow", "Scaffold", "Criticals", "ChestStealer", "GuiMove", "Rotation", "Phase");
    }
}
