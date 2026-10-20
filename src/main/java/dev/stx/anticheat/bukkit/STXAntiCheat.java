package dev.stx.anticheat.bukkit;

import dev.stx.anticheat.core.Result;
import dev.stx.anticheat.core.ViolationLedger;
import java.io.IOException;
import java.nio.file.Path;
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
    private Flagger flagger;
    private Sanctions sanctions;
    private final ViolationLedger ledger = new ViolationLedger();
    private volatile boolean dirty;
    private Path ledgerFile;
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
        sanctions = new Sanctions(this);

        // Kalıcı ihlal defteri (UUID bazlı) – sunucu yeniden başlatılsa da korunur
        ledgerFile = getDataFolder().toPath().resolve("data").resolve("violations.dat");
        try {
            int bad = ledger.load(ViolationLedger.read(ledgerFile));
            if (bad > 0) getLogger().warning("violations.dat içinde " + bad + " bozuk satır atlandı.");
        } catch (IOException e) {
            getLogger().warning("İhlal defteri okunamadı: " + e.getMessage());
        }

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
        long every = Math.max(30, cfg.i("sanctions.save-interval-seconds", 300)) * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> { if (dirty) saveLedger(false); }, every, every);

        for (Player p : Bukkit.getOnlinePlayers()) reinit(p);    // /reload veya geç yükleme
        getLogger().info("STXAntiCheat etkin. Geçici müdahale: " + (cfg.b("sanctions.mitigation-enabled", true) ? "AÇIK" : "KAPALI")
                + " | Ban: " + (cfg.banActive() ? "AÇIK (" + cfg.d("sanctions.ban-duration-days", 7) + " gün)" : "KAPALI"));
    }

    @Override
    public void onDisable() {
        saveLedger(true);
        if (vlog != null) {
            if (vlog.dropped() > 0) getLogger().warning(vlog.dropped() + " log kaydı kuyruk dolduğu için düşürüldü.");
            vlog.stop();
        }
        players.clear();
    }

    void loadSettings() {
        reloadConfig();
        cfg = new STXConfig(getConfig());
        for (PlayerData d : players.values()) d.build(cfg);
    }

    public void reloadAll() { loadSettings(); }

    // ---- erişimciler ----
    STXConfig cfg() { return cfg; }
    Sanctions sanctions() { return sanctions; }
    ViolationLedger ledger() { return ledger; }
    void markDirty() { dirty = true; }
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
            // Çıkıp girerek kısıtlamadan kaçılamaz: bitmemiş müdahaleleri geri yükle
            ledger.mitigations(id, System.currentTimeMillis()).forEach(d.mitUntil::put);
            return d;
        });
    }

    /** Girişte tüm geçici durumu sıfırdan kurar (eski kontrol/zamanlayıcı kalıntısı kalmaz); ihlaller defterden gelir. */
    PlayerData reinit(Player p) {
        players.remove(p.getUniqueId());
        return data(p);
    }

    void saveLedger(boolean sync) {
        dirty = false;
        final String text = ledger.serialize(System.currentTimeMillis(), 90L * 86_400_000L);
        Runnable w = () -> {
            try { ViolationLedger.writeAtomic(ledgerFile, text); }
            catch (IOException e) { getLogger().warning("İhlal defteri yazılamadı: " + e.getMessage()); dirty = true; }
        };
        if (sync || !isEnabled()) w.run(); else Bukkit.getScheduler().runTaskAsynchronously(this, w);
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

    /**
     * Savaş kontrolleri için muafiyet: elytra ve hareket-toleransları (uçuş açma/glide bitişi) muaf TUTMAZ;
     * böylece elytra ile Aura/ElytraTarget yapanlar kontrol dışında kalmaz.
     */
    String exemptReasonCombat(Player p, PlayerData d) {
        if (d.bypass) return "bypass";
        if (nowMs() < d.exemptUntilMs) return "exempt";
        if (tick < d.graceUntilTick) return "grace";
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return "gamemode";
        if (p.getAllowFlight() || p.isFlying()) return "flight";
        if (p.isInsideVehicle() || p.isRiptiding() || p.isDead() || p.isSleeping()) return "state";
        if (cfg.list("general.disabled-worlds").contains(p.getWorld().getName())) return "world";
        return null;
    }

    /** Hareket kontrolleri için sert muafiyet nedeni; null = muaf değil. */
    String exemptReason(Player p, PlayerData d) {
        if (tick < d.moveGraceUntilTick) return "move-grace";
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
          try {
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
          } catch (Throwable t) { getLogger().warning("heartbeat hatası (" + p.getName() + "): " + t); }
        }
    }

    List<String> checkNames() {
        return List.of("Reach", "Aim", "Aura", "AutoClicker", "Velocity", "Fly", "Step", "HighJump", "Speed", "Timer",
                "NoFall", "NoSlow", "Scaffold", "Criticals", "ChestStealer", "GuiMove", "Rotation", "Phase", "Jesus", "Spider", "AimAssist", "ElytraSpeed", "ElytraTarget");
    }
}
