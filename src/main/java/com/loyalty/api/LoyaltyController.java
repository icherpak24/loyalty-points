package com.loyalty.api;

import com.loyalty.domain.BalanceSummary;
import com.loyalty.domain.EarnResult;
import com.loyalty.domain.RedemptionResult;
import com.loyalty.domain.RefundResult;
import com.loyalty.domain.Reward;
import com.loyalty.service.LoyaltyService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/** REST adapter: maps HTTP onto {@link LoyaltyService}. No business logic lives here. */
@RestController
@RequestMapping("/points") // prefix for every route below, e.g. /points/customers/{id}/balance
public class LoyaltyController {

    private final LoyaltyService loyaltyService;
    
	public record PurchaseRequest(String purchaseId, BigDecimal amount) {}

    public record RedemptionRequest(String rewardId) {}

    public LoyaltyController(LoyaltyService service) {
        this.loyaltyService = service;
    }

    @GetMapping("/rewards")
    public List<Reward> rewards() {
        return loyaltyService.listRewards();
    }

    @GetMapping("/customers/{customerId}/balance")
    public BalanceSummary balance(@PathVariable String customerId) {
        return loyaltyService.balance(customerId);
    }

    @PostMapping("/customers/{customerId}/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public EarnResult recordPurchase(@PathVariable String customerId, @RequestBody PurchaseRequest purchaseRequest) {
        return loyaltyService.recordPurchase(customerId, purchaseRequest.purchaseId(), purchaseRequest.amount());
    }

    @PostMapping("/customers/{customerId}/purchases/{purchaseId}/refund")
    public RefundResult refund(@PathVariable String customerId, @PathVariable String purchaseId) {
        return loyaltyService.refundPurchase(customerId, purchaseId);
    }

    @PostMapping("/customers/{customerId}/redemptions")
    @ResponseStatus(HttpStatus.CREATED)
    public RedemptionResult redeem(@PathVariable String customerId, @RequestBody RedemptionRequest request) {
        return loyaltyService.redeem(customerId, request.rewardId());
    }
}
