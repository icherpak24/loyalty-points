package com.loyalty.persistence;

import com.loyalty.domain.ConsumptionReason;
import com.loyalty.domain.PointLot;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class PointLotRepository {

    private static final String COLUMNS =
            "id, customer_id, purchase_id, points_earned, points_remaining, earned_at, expires_at";

    private static final RowMapper<PointLot> ROW_MAPPER = (rs, rowNum) -> new PointLot(
            rs.getLong("id"),
            rs.getString("customer_id"),
            rs.getString("purchase_id"),
            rs.getLong("points_earned"),
            rs.getLong("points_remaining"),
            JdbcTime.fromMillis(rs, "earned_at"),
            JdbcTime.fromMillis(rs, "expires_at"));

    private final JdbcClient jdbc;

    public PointLotRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts a lot holding all of its earned points and returns its generated id. */
    public long insert(String customerId, String purchaseId, long pointsEarned, Instant earnedAt, Instant expiresAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO point_lots (customer_id, purchase_id, points_earned, points_remaining, earned_at, expires_at)
                        VALUES (:customerId, :purchaseId, :points, :points, :earnedAt, :expiresAt)
                        """)
                .param("customerId", customerId)
                .param("purchaseId", purchaseId)
                .param("points", pointsEarned) // a new lot starts with everything it earned still remaining
                .param("earnedAt", JdbcTime.toMillis(earnedAt))
                .param("expiresAt", JdbcTime.toMillis(expiresAt))
                .update(keys);
        return Objects.requireNonNull(keys.getKey(), "No id generated for point lot").longValue();
    }

    public Optional<PointLot> findByPurchase(String purchaseId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM point_lots WHERE purchase_id = :purchaseId")
                .param("purchaseId", purchaseId)
                .query(ROW_MAPPER)
                .optional();
    }

    /** Unexpired lots that still hold points, ordered so the soonest-expiring are consumed first. */
    public List<PointLot> findActivePointsByEarliestExpiration(String customerId, Instant now) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM point_lots"
                        + " WHERE customer_id = :customerId AND expires_at > :now AND points_remaining > 0"
                        + " ORDER BY expires_at, id")
                .param("customerId", customerId)
                .param("now", JdbcTime.toMillis(now))
                .query(ROW_MAPPER)
                .list();
    }

    public long availablePoints(String customerId, Instant now) {
        return jdbc.sql("""
                        SELECT COALESCE(SUM(points_remaining), 0)
                        FROM point_lots
                        WHERE customer_id = :customerId AND expires_at > :now
                        """)
                .param("customerId", customerId)
                .param("now", JdbcTime.toMillis(now))
                .query(Long.class)
                .single();
    }

    /** Deducts points from a lot and records why. Callers run this inside their transaction. */
    public void consumePoints(long lotId, long points, ConsumptionReason reason, String referenceId, Instant now) {
        // Check and subtract in one statement: the WHERE clause refuses to take a lot below zero.
        int updated = jdbc.sql("""
                        UPDATE point_lots SET points_remaining = points_remaining - :points
                        WHERE id = :lotId AND points_remaining >= :points
                        """)
                .param("points", points)
                .param("lotId", lotId)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Lot " + lotId + " does not hold " + points + " points");
        }
        jdbc.sql("""
                        INSERT INTO lot_consumptions (lot_id, reason, reference_id, points, consumed_at)
                        VALUES (:lotId, :reason, :referenceId, :points, :consumedAt)
                        """)
                .param("lotId", lotId)
                .param("reason", reason.name())
                .param("referenceId", referenceId)
                .param("points", points)
                .param("consumedAt", JdbcTime.toMillis(now))
                .update();
    }
}
