package com.loyalty.domain;

import java.time.Instant;

/** The points earned from a single purchase, which expire together. */
public record PointLot(
        long id,
        String customerId,
        String purchaseId,
        long pointsEarned,
        long pointsRemaining,
        Instant earnedAt,
        Instant expiresAt) {

    /** A lot is usable up to, but not including, its expiry instant. */
    public boolean isActiveAt(Instant now) {
        return now.isBefore(expiresAt);
    }
}
