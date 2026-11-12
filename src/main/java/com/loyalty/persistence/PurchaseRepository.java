package com.loyalty.persistence;

import com.loyalty.domain.Purchase;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class PurchaseRepository {

    private static final RowMapper<Purchase> ROW_MAPPER = (rs, rowNum) -> new Purchase(
            rs.getString("id"),
            rs.getString("customer_id"),
            rs.getLong("amount_cents"),
            JdbcTime.fromMillis(rs, "purchased_at"),
            JdbcTime.fromMillis(rs, "refunded_at"));

    private final JdbcClient jdbc;

    public PurchaseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Purchase> find(String purchaseId) {
        return jdbc.sql("SELECT id, customer_id, amount_cents, purchased_at, refunded_at FROM purchases WHERE id = :id")
                .param("id", purchaseId)
                .query(ROW_MAPPER)
                .optional();
    }

    public void insert(Purchase purchase) {
        jdbc.sql("""
                        INSERT INTO purchases (id, customer_id, amount_cents, purchased_at)
                        VALUES (:id, :customerId, :amountCents, :purchasedAt)
                        """)
                .param("id", purchase.id())
                .param("customerId", purchase.customerId())
                .param("amountCents", purchase.amountCents())
                .param("purchasedAt", JdbcTime.toMillis(purchase.purchasedAt()))
                .update();
    }

    public void markRefunded(String purchaseId, Instant refundedAt) {
        int updated = jdbc.sql("UPDATE purchases SET refunded_at = :refundedAt WHERE id = :id AND refunded_at IS NULL")
                .param("refundedAt", JdbcTime.toMillis(refundedAt))
                .param("id", purchaseId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Purchase " + purchaseId + " was not refundable");
        }
    }

    /** Sum of non-refunded purchase amounts made strictly after {@code since}. */
    public long nonRefundedSpendCentsSince(String customerId, Instant since) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(amount_cents), 0)
                        FROM purchases
                        WHERE customer_id = :customerId AND purchased_at > :since AND refunded_at IS NULL
                        """)
                .param("customerId", customerId)
                .param("since", JdbcTime.toMillis(since))
                .query(Long.class)
                .single();
    }
}
