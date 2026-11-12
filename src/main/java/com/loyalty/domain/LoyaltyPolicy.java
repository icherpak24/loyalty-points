package com.loyalty.domain;

import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;

/**
 * The business rules of the programme in one place: how many points a purchase earns,
 * how long they last, and the window used to compute tier.
 */
public record LoyaltyPolicy(Period pointsValidity, Period tierWindow) {

    private static final long CENTS_PER_POINT = 100; // 1 point per whole dollar spent

    public static LoyaltyPolicy standard() {
        return new LoyaltyPolicy(Period.ofMonths(12), Period.ofMonths(12));
    }

    /** Points are awarded per whole dollar; fractional dollars are rounded down. */
    public long pointsFor(long amountCents) {
        return amountCents / CENTS_PER_POINT;
    }

    /** Calendar-aware: points earned on 15 March expire on 15 March the following year (UTC). */
    public Instant expiryFor(Instant earnedAt) {
        return earnedAt.atZone(ZoneOffset.UTC).plus(pointsValidity).toInstant();
    }

    /** Purchases strictly after this instant count toward the customer's tier. */
    public Instant tierWindowStart(Instant now) {
        return now.atZone(ZoneOffset.UTC).minus(tierWindow).toInstant();
    }
}
