package dev.stx.anticheat.core;

/**
 * Hile türüne özel kademeli müdahale kararı. Her şüphe ceza değildir:
 *  - LOW  (kanıt puanı < confirm)  -> yalnızca kayıt
 *  - MEDIUM/HIGH ve ihlal sayısı:  1 -> geçici müdahale, 2+ -> kısıtlama, ban yalnızca
 *    (ban-uygun hile) + (yeterli ihlal) + (HIGH kanıt) + (yeterli bağımsız hile türü) birlikteyse.
 */
public final class SanctionPolicy {
    public enum Level { LOW, MEDIUM, HIGH }
    public enum Stage { LOG, WARN_MITIGATE, RESTRICT, BAN }

    /** Hile türü başına kural (config'ten gelir). */
    public record Rule(boolean banEligible, int violationsToRestrict, int violationsToBan,
                       double confirmPoints, double strongPoints, long expireMs, long violationCooldownMs,
                       int mitigationSeconds, int restrictSeconds) {}

    private SanctionPolicy() {}

    public static Level level(double checkPoints, Rule r) {
        if (checkPoints >= r.strongPoints()) return Level.HIGH;
        if (checkPoints >= r.confirmPoints()) return Level.MEDIUM;
        return Level.LOW;
    }

    /**
     * @param violations   bu ihlal dahil, süresi dolmamış ihlal sayısı (bu hile türü için)
     * @param distinct     süresi dolmamış ihlali olan farklı hile türü sayısı
     */
    public static Stage stage(Level level, int violations, Rule r, int distinct, int minDistinctForBan) {
        if (level == Level.LOW) return Stage.LOG;
        if (r.banEligible() && level == Level.HIGH && violations >= r.violationsToBan() && distinct >= minDistinctForBan)
            return Stage.BAN;
        if (violations >= r.violationsToRestrict()) return Stage.RESTRICT;
        return Stage.WARN_MITIGATE;
    }
}
