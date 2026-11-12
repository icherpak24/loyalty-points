package com.loyalty.domain;

import java.time.Instant;

public record Purchase(String id, String customerId, long amountCents, Instant purchasedAt, Instant refundedAt) {

    public boolean isRefunded() {
        return refundedAt != null;
    }
}
