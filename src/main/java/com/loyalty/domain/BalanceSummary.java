package com.loyalty.domain;

import java.math.BigDecimal;

/**
 * A customer's standing at a point in time.
 *
 * @param availablePoints     unexpired points that can be redeemed right now
 * @param pointsOwed          points clawed back by refunds that will be deducted from future earnings
 * @param tier                tier derived from {@code rollingSpend}
 * @param rollingSpend        non-refunded spend in the rolling tier window, in dollars
 */
public record BalanceSummary(
        String customerId,
        long availablePoints,
        long pointsOwed,
        Tier tier,
        BigDecimal rollingSpend) {
}
