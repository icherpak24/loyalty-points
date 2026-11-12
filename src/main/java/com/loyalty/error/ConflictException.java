package com.loyalty.error;

/** The request is valid but conflicts with the current state (e.g. a duplicate or repeated operation). */
public class ConflictException extends LoyaltyException {
    private static final long serialVersionUID = 1L;

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
