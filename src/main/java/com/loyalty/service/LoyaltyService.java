package com.loyalty.service;

import com.loyalty.domain.BalanceSummary;
import com.loyalty.domain.ConsumptionReason;
import com.loyalty.domain.EarnResult;
import com.loyalty.domain.LoyaltyPolicy;
import com.loyalty.domain.PointLot;
import com.loyalty.domain.Purchase;
import com.loyalty.domain.RedemptionResult;
import com.loyalty.domain.RefundResult;
import com.loyalty.domain.Reward;
import com.loyalty.domain.Tier;
import com.loyalty.error.ConflictException;
import com.loyalty.error.InsufficientPointsException;
import com.loyalty.error.InvalidRequestException;
import com.loyalty.error.NotFoundException;
import com.loyalty.persistence.CustomerRepository;
import com.loyalty.persistence.PointLotRepository;
import com.loyalty.persistence.PurchaseRepository;
import com.loyalty.persistence.RedemptionRepository;
import com.loyalty.persistence.RewardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * All loyalty business operations. Each public method is one transaction; any exception rolls it back.
 *
 * <p>Points are held in <em>lots</em>, one per earning purchase. Expiry is evaluated at read
 * time ({@code expires_at > now}) rather than by a background job, so an expired point can never
 * be spent no matter when it is checked.
 */
@Service
@Transactional
public class LoyaltyService {

    private final CustomerRepository customers;
    private final PurchaseRepository purchases;
    private final PointLotRepository lots;
    private final RewardRepository rewards;
    private final RedemptionRepository redemptions;
    private final Clock clock;
    private final LoyaltyPolicy policy;

    public LoyaltyService(CustomerRepository customers, PurchaseRepository purchases, PointLotRepository lots,
                          RewardRepository rewards, RedemptionRepository redemptions,
                          Clock clock, LoyaltyPolicy policy) {
        this.customers = customers;
        this.purchases = purchases;
        this.lots = lots;
        this.rewards = rewards;
        this.redemptions = redemptions;
        this.clock = clock;
        this.policy = policy;
    }

    /** Records a purchase and awards points for it. A purchase id can only be recorded once. */
    public EarnResult recordPurchase(String customerId, String purchaseId, BigDecimal amount) {
        requireId(customerId, "customer id");
        requireId(purchaseId, "purchase id");
        long amountCents = toCents(amount);
        Instant now = now();

        if (purchases.find(purchaseId).isPresent()) {
            throw new ConflictException("DUPLICATE_PURCHASE", "Purchase '" + purchaseId + "' has already been recorded");
        }
        customers.createIfAbsent(customerId, now);
        purchases.insert(new Purchase(purchaseId, customerId, amountCents, now, null));

        long pointsEarned = policy.pointsFor(amountCents);
        if (pointsEarned == 0) {
            return new EarnResult(purchaseId, 0, 0, null, summarize(customerId, now));
        }

        Instant expiresAt = policy.expiryFor(now);
        long lotId = lots.insert(customerId, purchaseId, pointsEarned, now, expiresAt);

        // Points owed from earlier refunds are settled before new points become spendable.
        long pointsOwed = customers.findPointsOwed(customerId).orElseThrow();
        long repayment = Math.min(pointsOwed, pointsEarned);
        if (repayment > 0) {
            lots.consumePoints(lotId, repayment, ConsumptionReason.DEBT_REPAYMENT, purchaseId, now);
            customers.updatePointsOwed(customerId, pointsOwed - repayment);
        }
        return new EarnResult(purchaseId, pointsEarned, repayment, expiresAt, summarize(customerId, now));
    }

    public BalanceSummary balance(String customerId) {
        requireId(customerId, "customer id");
        requireCustomer(customerId);
        return summarize(customerId, now());
    }

    public List<Reward> listRewards() {
        return rewards.findAll();
    }

    /** Spends points on a reward, consuming the soonest-expiring points first. */
    public RedemptionResult redeem(String customerId, String rewardId) {
        requireId(customerId, "customer id");
        requireId(rewardId, "reward id");
        Instant now = now();

        requireCustomer(customerId);
        Reward reward = rewards.find(rewardId).orElseThrow(() -> NotFoundException.reward(rewardId));

        long available = lots.availablePoints(customerId, now);
        if (available < reward.costPoints()) {
            throw new InsufficientPointsException(reward.costPoints(), available);
        }

        long redemptionId = redemptions.insert(customerId, reward.id(), reward.costPoints(), now);
        long consumed = consumePointsByEarliestExpiration(customerId, reward.costPoints(),
                ConsumptionReason.REDEMPTION, String.valueOf(redemptionId), now);
        if (consumed != reward.costPoints()) {
            // Can't happen given the balance check inside the same locked transaction.
            throw new IllegalStateException("Consumed " + consumed + " of " + reward.costPoints() + " points");
        }
        return new RedemptionResult(redemptionId, reward, now, summarize(customerId, now));
    }

    /**
     * Refunds a purchase and claws back the points it earned:
     * <ol>
     *   <li>Unspent points still in the purchase's own lot are removed.</li>
     *   <li>Points from that lot that already expired unused are ignored — the customer never benefited.</li>
     *   <li>Points from that lot the customer already spent are taken from their other active lots,
     *       soonest-expiring first.</li>
     *   <li>Anything still outstanding is recorded as points owed and repaid from future earnings.</li>
     * </ol>
     */
    public RefundResult refundPurchase(String customerId, String purchaseId) {
        requireId(customerId, "customer id");
        requireId(purchaseId, "purchase id");
        Instant now = now();

        //make sure purcaseId and customerId agree
        Purchase purchase = purchases.find(purchaseId)
                .filter(p -> p.customerId().equals(customerId))
                .orElseThrow(() -> NotFoundException.purchase(purchaseId));
        if (purchase.isRefunded()) {
            throw new ConflictException("ALREADY_REFUNDED", "Purchase '" + purchaseId + "' was already refunded");
        }
        purchases.markRefunded(purchaseId, now);

        //lot maybe missing since we do not create a lot for anything under $1 because pointsFor rounds down.
        Optional<PointLot> maybeLot = lots.findByPurchase(purchaseId);
        if (maybeLot.isEmpty()) {
            return new RefundResult(purchaseId, 0, 0, 0, summarize(customerId, now));
        }
        PointLot lot = maybeLot.get();

        long fromOwnLot = 0;
        long expiredUnused = 0;
        if (lot.isActiveAt(now)) {
            fromOwnLot = lot.pointsRemaining();
            if (fromOwnLot > 0) {
                lots.consumePoints(lot.id(), fromOwnLot, ConsumptionReason.REFUND_CLAWBACK, purchaseId, now);
            }
        } else {
            expiredUnused = lot.pointsRemaining();
        }

        long alreadySpent = lot.pointsEarned() - fromOwnLot - expiredUnused;
        long fromOtherLots = consumePointsByEarliestExpiration(customerId, alreadySpent,
                ConsumptionReason.REFUND_CLAWBACK, purchaseId, now);
        long addedToOwed = alreadySpent - fromOtherLots;
        if (addedToOwed > 0) {
            long currentlyOwed = customers.findPointsOwed(customerId).orElseThrow();
            customers.updatePointsOwed(customerId, currentlyOwed + addedToOwed);
        }

        return new RefundResult(purchaseId, lot.pointsEarned(), fromOwnLot + fromOtherLots, addedToOwed,
                summarize(customerId, now));
    }

    /** Takes up to {@code points} from active lots, soonest-expiring first. Returns how many were taken. */
    private long consumePointsByEarliestExpiration(String customerId, long points, ConsumptionReason reason,
                                             String referenceId, Instant now) {
        long outstandingPoints = points;
        for (PointLot lot : lots.findActivePointsByEarliestExpiration(customerId, now)) {
            if (outstandingPoints == 0) {
                break;
            }
            long take = Math.min(lot.pointsRemaining(), outstandingPoints);
            lots.consumePoints(lot.id(), take, reason, referenceId, now);
            outstandingPoints -= take;
        }
        return points - outstandingPoints;
    }

    private BalanceSummary summarize(String customerId, Instant now) {
        long available = lots.availablePoints(customerId, now);
        long owed = customers.findPointsOwed(customerId).orElse(0);
        long spendCents = purchases.nonRefundedSpendCentsSince(customerId, policy.tierWindowStart(now));
        return new BalanceSummary(customerId, available, owed, Tier.forSpend(spendCents),
                BigDecimal.valueOf(spendCents, 2));
    }

    private void requireCustomer(String customerId) {
        if (!customers.exists(customerId)) {
            throw NotFoundException.customer(customerId);
        }
    }

    private Instant now() {
        // Persisted at millisecond precision, so compute with the same precision.
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static void requireId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("A " + name + " is required");
        }
    }

    private static long toCents(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidRequestException("A purchase amount is required");
        }
        if (amount.signum() <= 0) {
            throw new InvalidRequestException("Purchase amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidRequestException("Purchase amount cannot have fractional cents");
        }
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException e) {
            throw new InvalidRequestException("Purchase amount is too large");
        }
    }
}
