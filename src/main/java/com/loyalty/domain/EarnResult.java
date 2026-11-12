package com.loyalty.domain;

import java.time.Instant;

/**
 * @param pointsEarned      points awarded for the purchase
 * @param pointsToOwed      portion of {@code pointsEarned} used to pay down points owed from earlier refunds
 * @param expiresAt         when the points from this purchase expire; null if the purchase earned no points
 */
public record EarnResult(
        String purchaseId,
        long pointsEarned,
        long pointsToOwed,
        Instant expiresAt,
        BalanceSummary balance) {
}
