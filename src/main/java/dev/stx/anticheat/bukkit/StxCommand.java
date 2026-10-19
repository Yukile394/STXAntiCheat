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
                msg(s, String.format(Locale.ROOT, "&f%s &7toplam=&e%.1f &7bağımsız=&e%d &7ping=&e%d &7tps=&e%.1f",
                        t.getName(), d.suspicion.total(now),
                        d.suspicion.distinct(plugin.cfg().d("punishment.distinct-min-points", 6), now), t.getPing(), plugin.tps()));
                Map<String, Double> snap = d.suspicion.snapshot(now);
                msg(s, snap.isEmpty() ? "Şüphe puanı yok." : snap.toString());
                msg(s, "Karar: &e" + plugin.policy().decide(d.suspicion, now)
                        + " &7| Ceza aktif: " + (plugin.cfg().punishmentActive() ? "&cEVET" : "&aHAYIR"));
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
                d.build(plugin.cfg());
                msg(s, t.getName() + " şüphe puanları sıfırlandı.");
            }
            case "reload" -> { plugin.reloadAll(); msg(s, "Yapılandırma yeniden yüklendi."); }
            default -> {
                msg(s, "&f/stx alerts &7- uyarıları aç/kapat");
                msg(s, "&f/stx status [oyuncu] &7- şüphe puanları");
                msg(s, "&f/stx exempt <oyuncu> [dk] &7- geçici muafiyet");
                msg(s, "&f/stx reset <oyuncu> &7- puanları sıfırla");
                msg(s, "&f/stx reload &7- config yenile   &f/stx history &7- log konumu");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String x : List.of("alerts", "status", "history", "exempt", "reset", "reload"))
                if (x.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(x);
        } else if (a.length == 2 && List.of("status", "exempt", "reset").contains(a[0].toLowerCase(Locale.ROOT))) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
        }
        return out;
    }
}
