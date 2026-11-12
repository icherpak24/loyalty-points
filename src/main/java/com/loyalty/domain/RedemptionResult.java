package com.loyalty.domain;

import java.time.Instant;

public record RedemptionResult(
        long redemptionId,
        Reward reward,
        Instant redeemedAt,
        BalanceSummary balance) {
}
