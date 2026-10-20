package dev.stx.anticheat.bukkit;

import dev.stx.anticheat.core.SanctionPolicy;
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

    // ---- kademeli müdahale kuralları: checks.<Ad>.<anahtar>, yoksa sanctions.default.<anahtar> ----
    private double sd(String check, String key, double def) {
        String p = "checks." + check + "." + key;
        return c.isSet(p) ? c.getDouble(p) : c.getDouble("sanctions.default." + key, def);
    }

    public SanctionPolicy.Rule rule(String check) {
        return new SanctionPolicy.Rule(c.getBoolean("checks." + check + ".ban-eligible", false),
                (int) sd(check, "violations-to-restrict", 2), (int) sd(check, "violations-to-ban", 3),
                sd(check, "confirm-points", 6), sd(check, "strong-points", 14),
                (long) (sd(check, "expire-hours", 72) * 3_600_000L),
                (long) (sd(check, "violation-cooldown-seconds", 60) * 1000L),
                (int) sd(check, "mitigation-seconds", 15), (int) sd(check, "restrict-seconds", 30));
    }

    /** ATTACK, MOVE, PLACE, CONTAINER, ELYTRA veya NONE; birden fazla için "ELYTRA+ATTACK" */
    public List<String> mitigationTypes(String check) {
        String raw = c.getString("checks." + check + ".mitigation", "NONE").toUpperCase(java.util.Locale.ROOT);
        List<String> out = new java.util.ArrayList<>();
        for (String t : raw.split("[+,]")) if (!t.isBlank() && !t.trim().equals("NONE")) out.add(t.trim());
        return out;
    }

    /** İlk (ana) müdahale türü; yoksa NONE. */
    public String mitigationType(String check) {
        List<String> l = mitigationTypes(check);
        return l.isEmpty() ? "NONE" : l.get(0);
    }

    public boolean banActive() { return b("sanctions.ban-enabled", true); }
}
