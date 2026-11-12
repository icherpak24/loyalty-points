package com.loyalty.error;

public class InsufficientPointsException extends LoyaltyException {
    private static final long serialVersionUID = 1L;

    private final long required;
    private final long available;

    public InsufficientPointsException(long required, long available) {
        super("INSUFFICIENT_POINTS",
                "Reward costs " + required + " points but only " + available + " are available");
        this.required = required;
        this.available = available;
    }

    public long required() {
        return required;
    }

    public long available() {
        return available;
    }
}
