package dev.stx.anticheat.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class StxCommand implements CommandExecutor, TabCompleter {
    private final STXAntiCheat plugin;
    private static final LegacyComponentSerializer AMP = LegacyComponentSerializer.legacyAmpersand();

    StxCommand(STXAntiCheat plugin) { this.plugin = plugin; }

    private void msg(CommandSender s, String m) { s.sendMessage(AMP.deserialize("&8[&bSTX&8] &7" + m)); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "alerts" -> {
                if (!(s instanceof Player p)) { msg(s, "Yalnızca oyuncular."); return true; }
                msg(s, plugin.toggleAlerts(p.getUniqueId()) ? "Uyarılar &aAÇIK" : "Uyarılar &cKAPALI");
            }
            case "status" -> {
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player p ? p : null);
                PlayerData d = t == null ? null : plugin.peek(t.getUniqueId());
                if (d == null) { msg(s, "Oyuncu bulunamadı."); return true; }
                long now = System.currentTimeMillis();
                msg(s, String.format(Locale.ROOT, "&f%s &7toplam puan=&e%.1f &7ping=&e%d &7tps=&e%.1f",
                        t.getName(), d.suspicion.total(now), t.getPing(), plugin.tps()));
                Map<String, Double> snap = d.suspicion.snapshot(now);
                msg(s, snap.isEmpty() ? "Şüphe puanı yok." : "Puanlar: " + snap);
                StringBuilder vs = new StringBuilder();
                for (String ck : plugin.checkNames()) {
                    var rule = plugin.cfg().rule(ck);
                    int n = plugin.ledger().count(t.getUniqueId(), ck, now, rule.expireMs());
                    if (n > 0) vs.append(ck).append('=').append(n).append(rule.banEligible() ? "/" + rule.violationsToBan() : "").append(' ');
                }
                msg(s, vs.length() == 0 ? "Kalıcı ihlal yok." : "İhlaller: &e" + vs);
                msg(s, "Aktif müdahale: &e" + (d.mitUntil.entrySet().stream().filter(x -> x.getValue() > now).map(x -> x.getKey() + " " + (x.getValue() - now) / 1000 + "sn").toList())
                        + " &7| Ban sistemi: " + (plugin.cfg().banActive() ? "&cAÇIK" : "&aKAPALI"));
            }
            case "history" -> msg(s, "Kayıtlar: plugins/STXAntiCheat/logs/stx-<tarih>.jsonl (alanlar: check, detail, ping, tps, history)");
            case "exempt" -> {
                if (a.length < 2) { msg(s, "/stx exempt <oyuncu> [dakika]"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                PlayerData d = t == null ? null : plugin.peek(t.getUniqueId());
                if (d == null) { msg(s, "Oyuncu bulunamadı."); return true; }
                int min = 10;
                if (a.length > 2) { try { min = Math.max(1, Math.min(1440, Integer.parseInt(a[2]))); } catch (NumberFormatException ignored) { } }
                d.exemptUntilMs = STXAntiCheat.nowMs() + min * 60_000L;
                msg(s, t.getName() + " " + min + " dk muaf.");
            }
            case "reset" -> {
                if (a.length < 2) { msg(s, "/stx reset <oyuncu>"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                PlayerData d = t == null ? null : plugin.peek(t.getUniqueId());
                if (d == null) { msg(s, "Oyuncu bulunamadı."); return true; }
                d.suspicion.clear();
                d.mitUntil.clear();
                plugin.ledger().clear(t.getUniqueId());
                plugin.markDirty();
                d.build(plugin.cfg());
                msg(s, t.getName() + " için puanlar, kalıcı ihlaller ve kısıtlamalar sıfırlandı (yanlış pozitif düzeltme).");
            }
            case "unban" -> {
                if (a.length < 2) { msg(s, "/stx unban <oyuncu>"); return true; }
                java.util.UUID id = plugin.ledger().uuidByName(a[1]);
                if (id == null) { msg(s, "Bu isimde kayıtlı oyuncu yok."); return true; }
                plugin.ledger().unban(id);
                plugin.ledger().clear(id);
                plugin.markDirty();
                msg(s, a[1] + " banı kaldırıldı ve ihlalleri sıfırlandı.");
            }
            case "kapat", "disable" -> {
                if (a.length < 2) { msg(s, "&e/stx kapat <kontrol...> [dakika] &7(örn. &f/stx kapat aura speed&7, &f/stx kapat fly 10&7, &f/stx kapat hepsi&7)"); return true; }
                long minutes = 0;
                int end = a.length;
                try { minutes = Long.parseLong(a[a.length - 1]); end = a.length - 1; } catch (NumberFormatException ignored) { }
                java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
                for (int i = 1; i < end; i++) {
                    List<String> r = plugin.resolveChecks(a[i]);
                    if (r == null) { msg(s, "&cBilinmeyen kontrol: &f" + a[i] + " &7(liste: /stx kontroller)"); return true; }
                    names.addAll(r);
                }
                if (names.isEmpty()) { msg(s, "&cKontrol adı girmedin."); return true; }
                plugin.disableChecks(new ArrayList<>(names), minutes > 0 ? System.currentTimeMillis() + minutes * 60_000L : 0L);
                msg(s, "&cKapatıldı: &f" + String.join(", ", names) + (minutes > 0 ? " &7(" + minutes + " dk sonra otomatik açılır)" : " &7(&f/stx geriac&7 ile açılana kadar kapalı kalır, yeniden başlatmada da)"));
                plugin.getLogger().warning(s.getName() + " kontrolleri kapattı: " + names + (minutes > 0 ? " (" + minutes + " dk)" : ""));
            }
            case "geriac", "geriaç", "ac", "aç", "enable" -> {
                if (a.length < 2) { msg(s, "&e/stx geriac <kontrol...> &7(örn. &f/stx geriac aura&7, &f/stx geriac hepsi&7)"); return true; }
                java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
                for (int i = 1; i < a.length; i++) {
                    List<String> r = plugin.resolveChecks(a[i]);
                    if (r == null) { msg(s, "&cBilinmeyen kontrol: &f" + a[i] + " &7(liste: /stx kontroller)"); return true; }
                    names.addAll(r);
                }
                plugin.enableChecks(new ArrayList<>(names));
                msg(s, "&aGeri açıldı: &f" + String.join(", ", names));
                plugin.getLogger().info(s.getName() + " kontrolleri geri açtı: " + names);
            }
            case "kontroller", "list", "checks" -> {
                StringBuilder on = new StringBuilder(), off = new StringBuilder(), cfgOff = new StringBuilder();
                for (String n : plugin.checkNames()) {
                    if (plugin.isRuntimeDisabled(n)) off.append(n).append(' ');
                    else if (!plugin.cfg().enabled(n)) cfgOff.append(n).append(' ');
                    else on.append(n).append(' ');
                }
                msg(s, "&aAçık: &f" + on);
                msg(s, off.length() == 0 ? "&7Oyun içinden kapatılan: yok" : "&cOyun içinden kapatılan: &f" + off + "&7(/stx geriac ile aç)");
                if (cfgOff.length() > 0) msg(s, "&8config.yml'de kapalı: " + cfgOff);
            }
            case "reload" -> { plugin.reloadAll(); msg(s, "Yapılandırma yeniden yüklendi."); }
            default -> {
                msg(s, "&f/stx alerts &7- uyarıları aç/kapat");
                msg(s, "&f/stx status [oyuncu] &7- şüphe puanları");
                msg(s, "&f/stx exempt <oyuncu> [dk] &7- geçici muafiyet");
                msg(s, "&f/stx reset <oyuncu> &7- puanları sıfırla");
                msg(s, "&f/stx unban <oyuncu> &7- banı kaldır");
                msg(s, "&f/stx kapat <kontrol...> [dk] &7- koruma kapat (örn. aura speed)");
                msg(s, "&f/stx geriac <kontrol...|hepsi> &7- korumayı geri aç   &f/stx kontroller &7- liste");
                msg(s, "&f/stx reload &7- config yenile   &f/stx history &7- log konumu");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : List.of("alerts", "status", "history", "exempt", "reset", "unban", "kapat", "geriac", "kontroller", "reload"))
                if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length >= 2 && List.of("kapat", "geriac", "geriaç", "ac", "aç").contains(a[0].toLowerCase(Locale.ROOT))) {
            String cur = a[a.length - 1].toLowerCase(Locale.ROOT);
            List<String> opts = new ArrayList<>(plugin.checkNames());
            opts.add("hepsi");
            for (String n : opts) if (n.toLowerCase(Locale.ROOT).startsWith(cur)) out.add(n);
        } else if (a.length == 2 && List.of("status", "exempt", "reset").contains(a[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
        }
        return out;
    }
}
