package com.loyalty.domain;

/**
 * @param pointsOriginallyEarned points the purchase earned
 * @param pointsClawedBack       points removed from the customer's active balance
 * @param pointsAddedToOwed      points already spent that could not be recovered, now owed from future earnings
 */
public record RefundResult(
        String purchaseId,
        long pointsOriginallyEarned,
        long pointsClawedBack,
        long pointsAddedToOwed,
        BalanceSummary balance) {
}
