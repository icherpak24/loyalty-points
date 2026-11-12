package com.loyalty;

import com.loyalty.domain.BalanceSummary;
import com.loyalty.domain.EarnResult;
import com.loyalty.domain.RefundResult;
import com.loyalty.domain.Tier;
import com.loyalty.error.ConflictException;
import com.loyalty.error.InsufficientPointsException;
import com.loyalty.error.InvalidRequestException;
import com.loyalty.error.NotFoundException;
import com.loyalty.service.LoyaltyService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Period;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoyaltyServiceTest extends IntegrationTest {

    @Autowired
    private LoyaltyService service;

    private EarnResult buy(String purchaseId, String amount) {
        return service.recordPurchase("alice", purchaseId, new BigDecimal(amount));
    }

    private long available() {
        return service.balance("alice").availablePoints();
    }

    @Nested
    class Earning {

        @Test
        void earnsOnePointPerWholeDollar() {
            EarnResult result = buy("order-1", "42.99");

            assertEquals(42, result.pointsEarned());
            assertEquals(42, available());
            assertEquals(START.atZone(java.time.ZoneOffset.UTC).plusYears(1).toInstant(), result.expiresAt());
        }

        @Test
        void purchaseUnderOneDollarEarnsNothingButIsRecorded() {
            EarnResult result = buy("order-1", "0.99");

            assertEquals(0, result.pointsEarned());
            assertEquals(new BigDecimal("0.99"), result.balance().rollingSpend());
        }

        @Test
        void samePurchaseCannotEarnTwice() {
            buy("order-1", "50");

            ConflictException e = assertThrows(ConflictException.class, () -> buy("order-1", "50"));
            assertEquals("DUPLICATE_PURCHASE", e.code());
            assertEquals(50, available());
        }

        @Test
        void rejectsInvalidAmounts() {
            assertThrows(InvalidRequestException.class, () -> buy("a", "0"));
            assertThrows(InvalidRequestException.class, () -> buy("b", "-5"));
            assertThrows(InvalidRequestException.class, () -> buy("c", "1.005"));
            assertThrows(InvalidRequestException.class,
                    () -> service.recordPurchase("alice", "d", null));
        }

        @Test
        void unknownCustomerHasNoBalance() {
            assertThrows(NotFoundException.class, () -> service.balance("nobody"));
        }
    }

    @Nested
    class Redeeming {

        @Test
        void redeemingDeductsTheRewardCost() {
            buy("order-1", "250");

            service.redeem("alice", "free-coffee");

            assertEquals(150, available());
        }

        @Test
        void insufficientBalanceIsRejectedAndNothingIsDeducted() {
            buy("order-1", "99");

            InsufficientPointsException e = assertThrows(InsufficientPointsException.class,
                    () -> service.redeem("alice", "free-coffee"));
            assertEquals(100, e.required());
            assertEquals(99, e.available());
            assertEquals(99, available());
        }

        @Test
        void unknownRewardIsRejected() {
            buy("order-1", "100");

            assertThrows(NotFoundException.class, () -> service.redeem("alice", "free-car"));
        }

        @Test
        void consumesSoonestExpiringPointsFirst() {
            buy("old", "100");                     // expires START + 12 months
            clock.advance(Period.ofMonths(6));
            buy("new", "100");                     // expires START + 18 months

            service.redeem("alice", "free-coffee"); // 100 points: should drain "old" entirely

            clock.advance(Period.ofMonths(6));      // "old" lot has now expired
            assertEquals(100, available(), "the newer lot should be untouched");
        }
    }

    @Nested
    class Expiry {

        @Test
        void pointsExpireTwelveMonthsAfterEarning() {
            buy("order-1", "100");

            clock.advance(Period.ofMonths(12).minusDays(0));
            clock.advance(Duration.ofMillis(-1));
            assertEquals(100, available(), "still valid one millisecond before expiry");

            clock.advance(Duration.ofMillis(1));
            assertEquals(0, available(), "expired exactly at the 12-month mark");
        }

        @Test
        void expiredPointsCannotBeRedeemed() {
            buy("order-1", "100");
            clock.advance(Period.ofMonths(13));

            assertThrows(InsufficientPointsException.class, () -> service.redeem("alice", "free-coffee"));
        }
    }

    @Nested
    class Tiers {

        @Test
        void tierFollowsRollingTwelveMonthSpend() {
            buy("order-1", "499.99");
            assertEquals(Tier.SILVER, service.balance("alice").tier());

            buy("order-2", "0.01");
            assertEquals(Tier.GOLD, service.balance("alice").tier());

            buy("order-3", "1500");
            assertEquals(Tier.PLATINUM, service.balance("alice").tier());

            clock.advance(Period.ofMonths(12));
            BalanceSummary later = service.balance("alice");
            assertEquals(Tier.SILVER, later.tier());
            assertEquals(new BigDecimal("0.00"), later.rollingSpend());
        }

        @Test
        void refundedPurchasesDoNotCountTowardTier() {
            buy("order-1", "600");
            service.refundPurchase("alice", "order-1");

            assertEquals(Tier.SILVER, service.balance("alice").tier());
        }
    }

    @Nested
    class Refunds {

        @Test
        void refundClawsBackUnspentPoints() {
            buy("order-1", "100");
            buy("order-2", "50");

            RefundResult result = service.refundPurchase("alice", "order-1");

            assertEquals(100, result.pointsClawedBack());
            assertEquals(0, result.pointsAddedToOwed());
            assertEquals(50, available());
        }

        @Test
        void alreadySpentPointsAreRecoveredFromOtherLots() {
            buy("order-1", "100");
            buy("order-2", "200");
            service.redeem("alice", "free-coffee");  // spends all of order-1's lot (soonest expiring)

            RefundResult result = service.refundPurchase("alice", "order-1");

            assertEquals(100, result.pointsClawedBack());
            assertEquals(100, available());
        }

        @Test
        void unrecoverablePointsBecomeOwedAndAreRepaidFromFutureEarnings() {
            buy("order-1", "100");
            service.redeem("alice", "free-coffee");

            RefundResult refund = service.refundPurchase("alice", "order-1");
            assertEquals(0, refund.pointsClawedBack());
            assertEquals(100, refund.pointsAddedToOwed());
            assertEquals(100, service.balance("alice").pointsOwed());

            EarnResult earn = buy("order-2", "130");
            assertEquals(100, earn.pointsToOwed());
            assertEquals(30, available());
            assertEquals(0, service.balance("alice").pointsOwed());
        }

        @Test
        void pointsThatExpiredUnusedAreNotClawedBackAgain() {
            buy("order-1", "100");
            clock.advance(Period.ofMonths(13));
            buy("order-2", "100");

            RefundResult result = service.refundPurchase("alice", "order-1");

            assertEquals(0, result.pointsClawedBack());
            assertEquals(0, result.pointsAddedToOwed());
            assertEquals(100, available());
        }

        @Test
        void purchaseCannotBeRefundedTwice() {
            buy("order-1", "100");
            service.refundPurchase("alice", "order-1");

            ConflictException e = assertThrows(ConflictException.class,
                    () -> service.refundPurchase("alice", "order-1"));
            assertEquals("ALREADY_REFUNDED", e.code());
        }

        @Test
        void cannotRefundAnotherCustomersPurchase() {
            buy("order-1", "100");

            assertThrows(NotFoundException.class, () -> service.refundPurchase("bob", "order-1"));
            assertEquals(100, available());
        }
    }
}
