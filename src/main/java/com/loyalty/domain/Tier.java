package com.loyalty.domain;

/**
 * Customer tier, derived from non-refunded spend over the rolling tier window.
 * Thresholds are inclusive lower bounds, in cents.
 */
public enum Tier {
    SILVER(0),
    GOLD(500_00),
    PLATINUM(2_000_00);

    private final long minimumSpendCents;

    Tier(long minimumSpendCents) {
        this.minimumSpendCents = minimumSpendCents;
    }

    public long minimumSpendCents() {
        return minimumSpendCents;
    }

    public static Tier forSpend(long spendCents) {
        Tier result = SILVER;
        for (Tier tier : values()) {
            if (spendCents >= tier.minimumSpendCents) {
                result = tier;
            }
        }
        return result;
    }
}
