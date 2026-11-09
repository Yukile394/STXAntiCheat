package dev.stx.anticheat.bukkit;

import dev.stx.anticheat.core.SanctionPolicy;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Doğrulanmış ihlallerde hile türüne özel geçici müdahale ve ban (ceza gösterisi: PunishShow). */
final class Sanctions {
    private static final LegacyComponentSerializer SEC = LegacyComponentSerializer.legacySection();
    private final STXAntiCheat plugin;

    Sanctions(STXAntiCheat plugin) { this.plugin = plugin; }

    private final java.util.Map<java.util.UUID, PunishShow> shows = new java.util.HashMap<>();

    boolean showing(java.util.UUID id) { return shows.containsKey(id); }
    void showEnded(java.util.UUID id) { shows.remove(id); }

    /** Oyuncu çıktı: gösteri sürüyorsa final (creeper/TNT) yine oynar; ban zaten defterdedir. */
    void onQuit(Player p) {
        PunishShow s = shows.get(p.getUniqueId());
        if (s != null) s.onQuit();
    }

    /** Eklenti kapanırken: açık gösterileri temizle (varlık kalmasın). */
    void shutdown() {
        for (PunishShow s : new java.util.ArrayList<>(shows.values())) s.finish();
        shows.clear();
        PunishShow.cleanupTeams();
    }

    /**
     * Kademeli müdahale kararı uygulanır. ESKİ "şüpheli hareketlerin algılandı / 15 saniye engellendi / kısıtlandı" mesajları
     * KALDIRILDI. O anın yerine, ban-uygun hilelerde doğrudan ceza gösterisi + ban gelir (config: sanctions.show).
     * Ban-uygun olmayan hilelerde (AutoClicker, Criticals...) yalnızca mesajsız geçici engel uygulanır.
     */
    void execute(Player p, PlayerData d, String check, SanctionPolicy.Stage st, int n,
                 SanctionPolicy.Rule rule, long nowWall) {
        if (st == SanctionPolicy.Stage.LOG) return;
        if (shows.containsKey(p.getUniqueId())) return;                 // zaten cezalandırılıyor
        STXConfig c = plugin.cfg();
        java.util.List<String> types = c.mitigationTypes(check);
        String type = types.isEmpty() ? "NONE" : types.get(0);
        boolean canMitigate = c.b("sanctions.mitigation-enabled", true) && !type.equals("NONE");
        boolean banOn = c.banActive();

        if (rule.banEligible() && banOn) {
            if (c.b("sanctions.show.enabled", true)) { startShow(p, d, check, n, nowWall); return; }
            if (st == SanctionPolicy.Stage.BAN) { banNow(p, d, check, n, nowWall); return; }
        }

        // Ban yok / ban-uygun değil: mesajsız geçici engel
        if (st == SanctionPolicy.Stage.BAN) {
            plugin.flagger().recordEvent("WOULD_BAN", p, d, check, "n=" + n, nowWall);
            notifyWouldBan(p, d, check, n, nowWall);
        }
        if (canMitigate) startAll(p, d, types, st == SanctionPolicy.Stage.WARN_MITIGATE ? rule.mitigationSeconds() : rule.restrictSeconds(), nowWall);
    }

    private long banUntil(long nowWall) {
        return nowWall + (long) (plugin.cfg().d("sanctions.ban-duration-days", 15) * 86_400_000L);
    }

    /** Ban defterde (UUID bazlı) kaydedilir; oyuncu hâlâ çevrimiçi olsa da çıkıp girerek kaçamaz. */
    private long recordBan(Player p, PlayerData d, String check, int n, long nowWall, String how) {
        long until = banUntil(nowWall);
        plugin.ledger().setBan(p.getUniqueId(), p.getName(), until, check);
        plugin.saveLedger(false);
        plugin.flagger().recordEvent("BAN", p, d, check, "n=" + n + " until=" + until + " " + how, nowWall);
        d.suspicion.clear();
        return until;
    }

    /** Gösteri: ban hemen yazılır, oyuncu havaya kalkıp lamalarla döner, sonra atılır. */
    private void startShow(Player p, PlayerData d, String check, int n, long nowWall) {
        long until = recordBan(p, d, check, n, nowWall, "show");
        PunishShow s = new PunishShow(plugin, this, p, d, plugin.cfg().s("sanctions.ban-reason", "Hile Kullanımı"), until);
        shows.put(p.getUniqueId(), s);
        // Olay işlenirken değil, bir sonraki tikte başlat (hareket/vuruş olayının ortasında bindirme yapma)
        Bukkit.getScheduler().runTask(plugin, s::start);
    }

    /** Gösterisiz ban (sanctions.show.enabled: false). */
    private void banNow(Player p, PlayerData d, String check, int n, long nowWall) {
        long until = recordBan(p, d, check, n, nowWall, "direct");
        p.kick(SEC.deserialize(banScreen(plugin.cfg().s("sanctions.ban-reason", "Hile Kullanımı"), until, nowWall)));
    }

    /** Giriş engeli ve kick mesajı için kalan süre. */
    static String remaining(long untilMs, long nowMs) {
        long m = Math.max(0, (untilMs - nowMs) / 60_000L);
        return "§7Kalan süre: §f" + (m / 1440) + " gün " + ((m % 1440) / 60) + " saat " + (m % 60) + " dk";
    }

    static String banScreen(String reason, long untilMs, long nowMs) {
        return "§c§lAntiCheat §8» §cSunucudan yasaklandın.\n§7Sebep: §f" + reason + "\n" + remaining(untilMs, nowMs);
    }

    private void notifyWouldBan(Player p, PlayerData d, String check, int n, long now) {
        STXConfig c = plugin.cfg();
        if (!c.b("alerts.notify-when-not-punished", true)) return;
        Component msg = SEC.deserialize(String.format(Locale.ROOT,
                "§8[§bSTX§8] §e⚠ §f%s §7için ban koşulları oluştu (§c%s§7, %d. ihlal) ama ban KAPALI olduğu için §aatılmadı§7. §8/stx status %s",
                p.getName(), check, n, p.getName()));
        for (Player s : Bukkit.getOnlinePlayers()) {
            if (!plugin.alertsEnabled(s.getUniqueId())) continue;
            if ((c.b("alerts.notify-ops", true) && s.isOp()) || s.hasPermission("stx.alerts")) s.sendMessage(msg);
        }
    }

    void startAll(Player p, PlayerData d, java.util.List<String> types, int seconds, long nowWall) {
        for (String t : types) start(p, d, t, seconds, nowWall);
    }

    /** Elytra'yı kapatır: havadaysa uçuşu bitirir (düşüş serbest). */
    void stopGliding(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline() && p.isGliding()) p.setGliding(false); });
    }

    void start(Player p, PlayerData d, String type, int seconds, long nowWall) {
        long until = nowWall + seconds * 1000L;
        d.mitUntil.merge(type, until, Math::max);
        plugin.ledger().setMitigation(p.getUniqueId(), type, d.mitUntil.get(type));
        plugin.markDirty();
        if (type.equals("MOVE")) { d.moveHotUntilMs = Math.max(d.moveHotUntilMs, nowWall + 30_000L); setback(p, d); }
        if (type.equals("ELYTRA")) stopGliding(p);
    }

    /** Oyuncuyu son temiz (yerde, kanıtsız) konuma geri alır. Bir sonraki tikte, tek görevle. */
    void setback(Player p, PlayerData d) {
        long now = System.currentTimeMillis();
        if (d.setbackQueued || !d.hasGood || now - d.lastSetbackMs < 250) return;
        d.setbackQueued = true;
        d.lastSetbackMs = now;
        Bukkit.getScheduler().runTask(plugin, () -> {
            d.setbackQueued = false;
            if (!p.isOnline() || d.goodWorld == null || !d.goodWorld.equals(p.getWorld())) return;
            Location to = new Location(d.goodWorld, d.goodX, d.goodY, d.goodZ, d.goodYaw, d.goodPitch);
            d.selfSetback = true;
            p.setVelocity(new Vector(0, 0, 0));
            p.setFallDistance(0f);
            p.teleport(to);
        });
    }
}
