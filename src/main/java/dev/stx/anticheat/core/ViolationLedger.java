package dev.stx.anticheat.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kalıcı ihlal defteri. UUID bazlıdır (isim değişse de izlenir). İhlaller zaman damgalıdır ve
 * hile türüne göre süre dolunca sayılmaz. Geçici müdahale bitiş zamanları da burada tutulur ki
 * oyuncu çıkıp girerek kısıtlamadan kaçamasın.
 * Biçim: satır tabanlı metin. V|uuid|isim|kontrol|ts1,ts2  ve  M|uuid|anahtar|bitişMs
 */
public final class ViolationLedger {
    private static final int MAX_PER_CHECK = 20;

    private static final class Rec {
        String name = "?";
        final Map<String, List<Long>> violations = new HashMap<>();
        final Map<String, Long> mitigations = new HashMap<>();
        long banUntil;
        String banReason = "";
    }

    private final Map<UUID, Rec> map = new HashMap<>();

    private Rec rec(UUID id) { return map.computeIfAbsent(id, k -> new Rec()); }

    private static void prune(List<Long> l, long now, long expireMs) { l.removeIf(t -> now - t > expireMs); }

    /** İhlal kaydeder; süresi dolmamış ihlal sayısını (bu dahil) döndürür. */
    public synchronized int record(UUID id, String name, String check, long nowMs, long expireMs) {
        Rec r = rec(id);
        r.name = name;
        List<Long> l = r.violations.computeIfAbsent(check, k -> new ArrayList<>());
        prune(l, nowMs, expireMs);
        l.add(nowMs);
        while (l.size() > MAX_PER_CHECK) l.remove(0);
        return l.size();
    }

    public synchronized int count(UUID id, String check, long nowMs, long expireMs) {
        Rec r = map.get(id);
        if (r == null) return 0;
        List<Long> l = r.violations.get(check);
        if (l == null) return 0;
        prune(l, nowMs, expireMs);
        return l.size();
    }

    /** Son ihlal zamanı; yoksa Long.MIN_VALUE/2. */
    public synchronized long lastViolation(UUID id, String check) {
        Rec r = map.get(id);
        if (r == null) return Long.MIN_VALUE / 2;
        List<Long> l = r.violations.get(check);
        return (l == null || l.isEmpty()) ? Long.MIN_VALUE / 2 : l.get(l.size() - 1);
    }

    /** Süresi dolmamış ihlali olan farklı hile türü sayısı. */
    public synchronized int distinctChecks(UUID id, long nowMs, java.util.function.ToLongFunction<String> expireMsOf) {
        Rec r = map.get(id);
        if (r == null) return 0;
        int c = 0;
        for (Map.Entry<String, List<Long>> e : r.violations.entrySet()) {
            prune(e.getValue(), nowMs, expireMsOf.applyAsLong(e.getKey()));
            if (!e.getValue().isEmpty()) c++;
        }
        return c;
    }

    public synchronized void setMitigation(UUID id, String key, long untilMs) { rec(id).mitigations.put(key, untilMs); }

    public synchronized Map<String, Long> mitigations(UUID id, long nowMs) {
        Map<String, Long> out = new HashMap<>();
        Rec r = map.get(id);
        if (r == null) return out;
        r.mitigations.entrySet().removeIf(e -> e.getValue() <= nowMs);
        out.putAll(r.mitigations);
        return out;
    }

    public synchronized void clear(UUID id) { map.remove(id); }

    // ---------------- geçici (süreli) ban: sunucu ban listesinden bağımsız, sürüm-bağımsız ----------------
    public synchronized void setBan(UUID id, String name, long untilMs, String reason) {
        Rec r = rec(id);
        r.name = name;
        r.banUntil = untilMs;
        r.banReason = reason == null ? "" : reason;
    }

    /** Aktif ban bitiş zamanı; ban yoksa/bittiyse 0. */
    public synchronized long banUntil(UUID id, long nowMs) {
        Rec r = map.get(id);
        return (r == null || r.banUntil <= nowMs) ? 0 : r.banUntil;
    }

    public synchronized String banReason(UUID id) {
        Rec r = map.get(id);
        return r == null ? "" : r.banReason;
    }

    public synchronized void unban(UUID id) {
        Rec r = map.get(id);
        if (r != null) { r.banUntil = 0; r.banReason = ""; }
    }

    /** İsimden UUID (büyük/küçük harf duyarsız); bulunamazsa null. */
    public synchronized UUID uuidByName(String name) {
        for (Map.Entry<UUID, Rec> e : map.entrySet())
            if (e.getValue().name.equalsIgnoreCase(name)) return e.getKey();
        return null;
    }

    // ---------------- kalıcılık ----------------
    private static String clean(String s) { return s.replace('|', '_').replace('\n', ' ').replace('\r', ' '); }

    public synchronized String serialize(long nowMs, long maxAgeMs) {
        StringBuilder sb = new StringBuilder("# STXAntiCheat ihlal defteri v1\n");
        for (Map.Entry<UUID, Rec> e : map.entrySet()) {
            Rec r = e.getValue();
            for (Map.Entry<String, List<Long>> v : r.violations.entrySet()) {
                prune(v.getValue(), nowMs, maxAgeMs);
                if (v.getValue().isEmpty()) continue;
                sb.append("V|").append(e.getKey()).append('|').append(clean(r.name)).append('|').append(clean(v.getKey())).append('|');
                for (int i = 0; i < v.getValue().size(); i++) { if (i > 0) sb.append(','); sb.append(v.getValue().get(i)); }
                sb.append('\n');
            }
            if (r.banUntil > nowMs)
                sb.append("B|").append(e.getKey()).append('|').append(clean(r.name)).append('|').append(r.banUntil)
                        .append('|').append(clean(r.banReason)).append('\n');
            for (Map.Entry<String, Long> m : r.mitigations.entrySet()) {
                if (m.getValue() > nowMs)
                    sb.append("M|").append(e.getKey()).append('|').append(clean(m.getKey())).append('|').append(m.getValue()).append('\n');
            }
        }
        return sb.toString();
    }

    /** Bozuk satırları atlar; kalan her şeyi yükler. @return atlanan satır sayısı */
    public synchronized int load(String text) {
        int bad = 0;
        for (String line : text.split("\n")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            try {
                String[] p = line.split("\\|", -1);
                UUID id = UUID.fromString(p[1]);
                Rec r = rec(id);
                if (p[0].equals("V")) {
                    r.name = p[2];
                    List<Long> l = r.violations.computeIfAbsent(p[3], k -> new ArrayList<>());
                    for (String t : p[4].split(",")) if (!t.isBlank()) l.add(Long.parseLong(t.trim()));
                } else if (p[0].equals("B")) {
                    r.name = p[2];
                    r.banUntil = Long.parseLong(p[3].trim());
                    r.banReason = p.length > 4 ? p[4] : "";
                } else if (p[0].equals("M")) {
                    r.mitigations.put(p[2], Long.parseLong(p[3].trim()));
                } else bad++;
            } catch (RuntimeException ex) {
                bad++;
            }
        }
        return bad;
    }

    /** Atomik yazım: geçici dosyaya yaz, sonra taşı (yarım kalmış dosya riskini önler). */
    public static void writeAtomic(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String read(Path file) throws IOException {
        return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }
}
