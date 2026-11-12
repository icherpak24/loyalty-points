package com.loyalty.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Objects;

@Repository
public class RedemptionRepository {

    private final JdbcClient jdbc;

    public RedemptionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts a redemption and returns its generated id. */
    public long insert(String customerId, String rewardId, long pointsSpent, Instant redeemedAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO redemptions (customer_id, reward_id, points_spent, redeemed_at)
                        VALUES (:customerId, :rewardId, :pointsSpent, :redeemedAt)
                        """)
                .param("customerId", customerId)
                .param("rewardId", rewardId)
                .param("pointsSpent", pointsSpent)
                .param("redeemedAt", JdbcTime.toMillis(redeemedAt))
                .update(keys);
        return Objects.requireNonNull(keys.getKey(), "No id generated for redemption").longValue();
    }
}
