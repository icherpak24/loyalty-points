package com.loyalty.error;

public class InvalidRequestException extends LoyaltyException {
    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
        super("INVALID_REQUEST", message);
    }
}
