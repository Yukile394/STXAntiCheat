package dev.stx.anticheat.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Oyuncu başına şüphe puanı. Her kontrol ayrı puan tutar; puanlar zamanla azalır.
 * Ceza kararı toplam puana VE birbirinden bağımsız kontrol sayısına bağlıdır.
 */
public final class Suspicion {
    private static final class Entry { double points; long last; int hits; }

    private final Map<String, Entry> map = new HashMap<>();
    private final double decayPerSecond;

    public Suspicion(double decayPerSecond) { this.decayPerSecond = decayPerSecond; }

    private void decay(Entry e, long now) {
        if (now > e.last) {
            e.points = Math.max(0, e.points - decayPerSecond * (now - e.last) / 1000.0);
            e.last = now;
        }
    }

    public synchronized double add(String check, double weight, long nowMs) {
        Entry e = map.computeIfAbsent(check, k -> { Entry n = new Entry(); n.last = nowMs; return n; });
        decay(e, nowMs);
        e.points += weight;
        e.hits++;
        return e.points;
    }

    public synchronized double points(String check, long nowMs) {
        Entry e = map.get(check);
        if (e == null) return 0;
        decay(e, nowMs);
        return e.points;
    }

    public synchronized double total(long nowMs) {
        double t = 0;
        for (Entry e : map.values()) { decay(e, nowMs); t += e.points; }
        return t;
    }

    /** minPoints üstünde puanı olan farklı kontrol sayısı. */
    public synchronized int distinct(double minPoints, long nowMs) {
        int c = 0;
        for (Entry e : map.values()) { decay(e, nowMs); if (e.points >= minPoints) c++; }
        return c;
    }

    public synchronized double max(long nowMs) {
        double m = 0;
        for (Entry e : map.values()) { decay(e, nowMs); m = Math.max(m, e.points); }
        return m;
    }

    public synchronized Map<String, Double> snapshot(long nowMs) {
        Map<String, Double> out = new LinkedHashMap<>();
        map.forEach((k, e) -> { decay(e, nowMs); if (e.points > 0.01) out.put(k, Math.round(e.points * 100) / 100.0); });
        return out;
    }

    public synchronized void clear() { map.clear(); }
}
