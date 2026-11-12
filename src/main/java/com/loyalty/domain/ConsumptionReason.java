package com.loyalty.domain;

/** Why points were removed from a lot. Persisted as text in lot_consumptions.reason. */
public enum ConsumptionReason {
    REDEMPTION,
    REFUND_CLAWBACK,
    DEBT_REPAYMENT
}
