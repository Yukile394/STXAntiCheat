package dev.stx.anticheat.bukkit;

import dev.stx.anticheat.core.PunishPolicy;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

/** config.yml üzerinde tipli erişim. Eksik anahtarlarda güvenli varsayılan döner. */
public final class STXConfig {
    private final FileConfiguration c;

    public STXConfig(FileConfiguration c) { this.c = c; }

    public boolean b(String path, boolean def) { return c.getBoolean(path, def); }
    public double d(String path, double def) { return c.getDouble(path, def); }
    public int i(String path, int def) { return c.getInt(path, def); }
    public String s(String path, String def) { return c.getString(path, def); }
    public List<String> list(String path) { return c.getStringList(path); }

    public boolean enabled(String check) { return c.getBoolean("checks." + check + ".enabled", true); }
    public double weight(String check) { return c.getDouble("checks." + check + ".weight", 1.0); }
    public double cd(String check, String key, double def) { return c.getDouble("checks." + check + "." + key, def); }
    public int ci(String check, String key, int def) { return c.getInt("checks." + check + "." + key, def); }
    public boolean cb(String check, String key, boolean def) { return c.getBoolean("checks." + check + "." + key, def); }

    public PunishPolicy.Thresholds thresholds() {
        return new PunishPolicy.Thresholds(
                d("punishment.alert-total", 10), d("punishment.kick-total", 40), d("punishment.ban-total", 90),
                i("punishment.min-distinct-checks", 2), d("punishment.distinct-min-points", 6),
                d("punishment.single-check-kick", 60), d("punishment.single-check-ban", 120));
    }

    /** Ceza ancak iki ayrı emniyet de açıksa verilir. */
    public boolean punishmentActive() {
        return b("punishment.enabled", false) && b("punishment.i-have-reviewed-the-logs", false);
    }
}
