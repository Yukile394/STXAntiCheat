package dev.stx.anticheat.bukkit;

import dev.stx.anticheat.core.Result;
import dev.stx.anticheat.core.SanctionPolicy;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Kanıt -> kontrol başına puan -> (doğrulanmışsa) kalıcı ihlal -> kademeli müdahale. */
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
        SanctionPolicy.Rule rule = c.rule(check);
        SanctionPolicy.Level lvl = SanctionPolicy.level(pts, rule);

        record("EVIDENCE", p, d, check, "[" + lvl + "] " + r.detail(), w, pts, total, now);
        alert(p, d, check, r.detail(), pts, now);
        instant(p, d, check, pts, now);                        // log düştüğü anda hileye özel anında engel
        if (lvl == SanctionPolicy.Level.LOW) return;          // düşük güven: ihlal sayılmaz (yalnızca kayıt + kısa engel)

        // Aktif hareket kısıtı varken doğrulanmış yeni kanıt -> anında geri alma (ihlal sayısı artmaz)
        if (c.b("sanctions.mitigation-enabled", true) && c.mitigationType(check).equals("MOVE") && d.mitActive("MOVE", now))
            plugin.sanctions().setback(p, d);

        // Yeni "ihlal" yalnızca cooldown sonrası sayılır: her şüpheli hareket ban sebebi olmaz
        UUID id = p.getUniqueId();
        if (now - plugin.ledger().lastViolation(id, check) < rule.violationCooldownMs()) return;
        int n = plugin.ledger().record(id, p.getName(), check, now, rule.expireMs());
        int distinct = plugin.ledger().distinctChecks(id, now, k -> plugin.cfg().rule(k).expireMs());
        SanctionPolicy.Stage st = SanctionPolicy.stage(lvl, n, rule, distinct, c.i("sanctions.min-distinct-checks-for-ban", 2));
        plugin.markDirty();
        record("VIOLATION_" + st, p, d, check, "n=" + n + " bağımsız=" + distinct + " seviye=" + lvl + " | " + r.detail(), w, pts, total, now);
        plugin.sanctions().execute(p, d, check, st, n, rule, now);
    }

    /**
     * Kanıt kaydedildiği anda kısa süreli, kendiliğinden biten engel: saldırı/blok/konteyner iptali veya
     * hareket dondurma + son temiz konuma geri alma. İhlal sayısını ARTIRMAZ (3/3 sayacından bağımsız).
     */
    private void instant(Player p, PlayerData d, String check, double pts, long now) {
        STXConfig c = plugin.cfg();
        if (!c.b("sanctions.mitigation-enabled", true) || !c.b("sanctions.instant.enabled", true)) return;
        if (!c.cb(check, "instant", false)) return;
        if (pts < c.d("sanctions.instant.min-points", 2)) return;
        String type = c.mitigationType(check);
        switch (type) {
            case "ATTACK", "PLACE", "CONTAINER" ->
                    d.mitUntil.merge(type, now + (long) (c.d("sanctions.instant.block-seconds", 3) * 1000), Math::max);
            case "MOVE" -> {
                d.mitUntil.merge("MOVE", now + (long) (c.d("sanctions.instant.move-freeze-seconds", 2) * 1000), Math::max);
                plugin.sanctions().setback(p, d);
            }
            default -> { }
        }
    }

    void recordEvent(String type, Player p, PlayerData d, String check, String detail, long now) {
        record(type, p, d, check, detail, 0, d.suspicion.points(check, now), d.suspicion.total(now), now);
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
            if (op && opsAll) s.sendMessage(msg);
            else if ((staff || op) && pts >= threshold) s.sendMessage(msg);
        }
        if (c.b("logging.console", false) && d.suspicion.total(now) >= c.d("logging.console-min-total", 8))
            plugin.getLogger().info(p.getName() + " " + check + " (" + String.format(Locale.ROOT, "%.1f", pts) + ") " + detail);
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
                        + "\"weight\":%.2f,\"points\":%.2f,\"total\":%.2f,\"ping\":%d,\"tps\":%.2f,"
                        + "\"gamemode\":\"%s\",\"world\":\"%s\",\"x\":%.2f,\"y\":%.2f,\"z\":%.2f,\"history\":%s}",
                java.time.Instant.ofEpochMilli(now), type, ViolationLog.esc(p.getName()), d.uuid, ViolationLog.esc(check),
                ViolationLog.esc(detail), weight, pts, total, p.getPing(), plugin.tps(), p.getGameMode(),
                ViolationLog.esc(l.getWorld().getName()), l.getX(), l.getY(), l.getZ(), h);
        plugin.violationLog().write(json);
    }
}
