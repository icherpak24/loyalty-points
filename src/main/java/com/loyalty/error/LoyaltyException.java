package com.loyalty.error;

/**
 * Base class for expected business-rule failures. Each carries a stable, machine-readable code
 * so API clients can branch on it without parsing messages.
 */
public abstract class LoyaltyException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;

    protected LoyaltyException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
