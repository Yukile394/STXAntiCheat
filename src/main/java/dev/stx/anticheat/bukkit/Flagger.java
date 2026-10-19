package dev.stx.anticheat.bukkit;

import dev.stx.anticheat.core.PunishPolicy;
import dev.stx.anticheat.core.Result;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Kanıt -> şüphe puanı -> log -> uyarı -> (isteğe bağlı) ceza. Ceza varsayılan olarak kapalıdır. */
final class Flagger {
    private final STXAntiCheat plugin;
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    Flagger(STXAntiCheat plugin) { this.plugin = plugin; }

    void flag(Player p, PlayerData d, String check, Result r) {
        if (r == null) return;
        STXConfig c = plugin.cfg();
        if (!c.enabled(check)) return;
        double w = r.weight() * c.weight(check);
        if (w <= 0) return;

        long now = System.currentTimeMillis();
        double pts = d.suspicion.add(check, w, now);
        double total = d.suspicion.total(now);
        PunishPolicy.Action action = plugin.policy().decide(d.suspicion, now);

        record("EVIDENCE", p, d, check, r.detail(), w, pts, total, now);
        alert(p, d, check, r.detail(), pts, now);

        if (action == PunishPolicy.Action.KICK || action == PunishPolicy.Action.BAN) {
            if (c.punishmentActive()) {
                if (now >= d.punishCooldownUntilMs) {
                    d.punishCooldownUntilMs = now + c.i("punishment.cooldown-seconds", 120) * 1000L;
                    record("PUNISH_" + action, p, d, check, r.detail(), w, pts, total, now);
                    String key = action == PunishPolicy.Action.BAN ? "punishment.ban-command" : "punishment.kick-command";
                    runCommand(c.s(key, "kick %player%"), p, check, total);
                    d.suspicion.clear();
                }
            } else if (c.b("punishment.dry-run-logging", true) && now >= d.wouldCooldownUntilMs) {
                d.wouldCooldownUntilMs = now + 60_000L;
                record("WOULD_PUNISH_" + action, p, d, check, r.detail(), w, pts, total, now);
                notifyNotPunished(p, d, action, check, r.detail(), total, now);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void runCommand(String template, Player p, String check, double total) {
        String cmd = template.replace("%player%", p.getName()).replace("%check%", check)
                .replace("%total%", String.format(Locale.ROOT, "%.1f", total));
        cmd = org.bukkit.ChatColor.translateAlternateColorCodes('&', cmd);
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    private void alert(Player p, PlayerData d, String check, String detail, double pts, long now) {
        STXConfig c = plugin.cfg();
        boolean opsAll = c.b("alerts.notify-ops", true) && c.b("alerts.ops-see-all-evidence", true);
        double threshold = c.d("suspicion.alert-points-per-check", 4);
        if (pts < threshold && !opsAll) return;
        Long last = d.lastAlertMs.get(check);
        if (last != null && now - last < c.i("suspicion.alert-cooldown-seconds", 3) * 1000L) return;
        d.lastAlertMs.put(check, now);
        Component msg = AMP.deserialize(String.format(Locale.ROOT,
                "&8[&bSTX&8] &f%s &7%s &8(&c%.1f&8) &7%s", p.getName(), check, pts, detail));
        for (Player s : Bukkit.getOnlinePlayers()) {
            if (!plugin.alertsEnabled(s.getUniqueId())) continue;
            boolean op = c.b("alerts.notify-ops", true) && s.isOp();
            boolean staff = s.hasPermission("stx.alerts");
            if (op && opsAll) s.sendMessage(msg);                    // OP: puan eşiğinden bağımsız hepsi
            else if ((staff || op) && pts >= threshold) s.sendMessage(msg);
        }
        if (c.b("logging.console", false) && d.suspicion.total(now) >= c.d("logging.console-min-total", 8))
            plugin.getLogger().info(p.getName() + " " + check + " (" + String.format(Locale.ROOT, "%.1f", pts) + ") " + detail);
    }

    /** Ceza eşiği aşıldı ama oyuncu ATILMADI (ceza kapalı) -> OP'lere ve yetkililere bildir. */
    private void notifyNotPunished(Player p, PlayerData d, PunishPolicy.Action action, String check, String detail,
                                   double total, long now) {
        STXConfig c = plugin.cfg();
        if (!c.b("alerts.notify-when-not-punished", true)) return;
        Component msg = AMP.deserialize(String.format(Locale.ROOT,
                "&8[&bSTX&8] &e⚠ &f%s &7için &c%s &7eşiği aşıldı (toplam &c%.1f&7, bağımsız kanıt &c%d&7) ama ceza KAPALI olduğu için "
                        + "&aatılmadı&7. Son kanıt: &f%s &8- &7%s &8| &7/stx status %s",
                p.getName(), action, total,
                d.suspicion.distinct(c.d("punishment.distinct-min-points", 6), now), check, detail, p.getName()));
        for (Player s : Bukkit.getOnlinePlayers()) {
            if (!plugin.alertsEnabled(s.getUniqueId())) continue;
            if ((c.b("alerts.notify-ops", true) && s.isOp()) || s.hasPermission("stx.alerts")) s.sendMessage(msg);
        }
    }

    private void record(String type, Player p, PlayerData d, String check, String detail,
                        double weight, double pts, double total, long now) {
        if (!plugin.cfg().b("logging.file", true)) return;
        Location l = p.getLocation();
        Map<String, Double> snap = d.suspicion.snapshot(now);
        StringBuilder h = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> e : snap.entrySet()) {
            if (!first) h.append(',');
            first = false;
            h.append('"').append(ViolationLog.esc(e.getKey())).append("\":").append(e.getValue());
        }
        h.append('}');
        String json = String.format(Locale.ROOT,
                "{\"ts\":\"%s\",\"type\":\"%s\",\"player\":\"%s\",\"uuid\":\"%s\",\"check\":\"%s\",\"detail\":\"%s\","
                        + "\"weight\":%.2f,\"points\":%.2f,\"total\":%.2f,\"distinct\":%d,\"ping\":%d,\"tps\":%.2f,"
                        + "\"gamemode\":\"%s\",\"world\":\"%s\",\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"history\":%s}",
                java.time.Instant.ofEpochMilli(now), type, ViolationLog.esc(p.getName()), d.uuid, ViolationLog.esc(check),
                ViolationLog.esc(detail), weight, pts, total,
                d.suspicion.distinct(plugin.cfg().d("punishment.distinct-min-points", 6), now),
                p.getPing(), plugin.tps(), p.getGameMode(), ViolationLog.esc(l.getWorld().getName()),
                l.getX(), l.getY(), l.getZ(), h);
        plugin.violationLog().write(json);
    }
}
