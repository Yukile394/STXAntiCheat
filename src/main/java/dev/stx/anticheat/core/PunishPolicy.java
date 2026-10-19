package dev.stx.anticheat.core;

/**
 * Ceza kararı: (a) yeterli toplam puan, (b) yeterince farklı bağımsız kanıt birlikte aranır.
 * Tek bir kontrol ancak çok yüksek puana ulaşırsa tek başına yeterli sayılır.
 */
public final class PunishPolicy {
    public enum Action { NONE, ALERT, KICK, BAN }

    public record Thresholds(double alertTotal, double kickTotal, double banTotal,
                             int minDistinctChecks, double distinctMinPoints,
                             double singleCheckKick, double singleCheckBan) {}

    private final Thresholds t;

    public PunishPolicy(Thresholds t) { this.t = t; }

    public Action decide(Suspicion s, long now) {
        double total = s.total(now);
        int distinct = s.distinct(t.distinctMinPoints(), now);
        double max = s.max(now);

        boolean multi = distinct >= t.minDistinctChecks();
        if (multi && total >= t.banTotal()) return Action.BAN;
        if (max >= t.singleCheckBan() && total >= t.banTotal()) return Action.BAN;
        if (multi && total >= t.kickTotal()) return Action.KICK;
        if (max >= t.singleCheckKick() && total >= t.kickTotal()) return Action.KICK;
        if (total >= t.alertTotal()) return Action.ALERT;
        return Action.NONE;
    }
}
