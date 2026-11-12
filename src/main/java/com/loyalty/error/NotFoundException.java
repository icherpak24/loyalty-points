package com.loyalty.error;

public class NotFoundException extends LoyaltyException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(String code, String message) {
        super(code, message);
    }

    public static NotFoundException customer(String customerId) {
        return new NotFoundException("CUSTOMER_NOT_FOUND", "No customer with id '" + customerId + "'");
    }

    public static NotFoundException reward(String rewardId) {
        return new NotFoundException("REWARD_NOT_FOUND", "No reward with id '" + rewardId + "'");
    }

    public static NotFoundException purchase(String purchaseId) {
        return new NotFoundException("PURCHASE_NOT_FOUND", "No purchase with id '" + purchaseId + "' for this customer");
    }
}
