package com.loyalty.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

@Repository
public class CustomerRepository {

    private final JdbcClient jdbc;

    public CustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Customers are created implicitly on their first purchase. */
    public void createIfAbsent(String customerId, Instant now) {
        jdbc.sql("INSERT OR IGNORE INTO customers (id, points_owed, created_at) VALUES (:id, 0, :createdAt)")
                .param("id", customerId)
                .param("createdAt", JdbcTime.toMillis(now))
                .update();
    }

    public boolean exists(String customerId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM customers WHERE id = :id)")
                .param("id", customerId)
                .query(Boolean.class)
                .single();
    }

    /** Returns the customer's points owed, or empty if the customer doesn't exist. */
    public OptionalLong findPointsOwed(String customerId) {
        Optional<Long> owed = jdbc.sql("SELECT points_owed FROM customers WHERE id = :id")
                .param("id", customerId)
                .query(Long.class)
                .optional();
        return owed.map(OptionalLong::of).orElse(OptionalLong.empty());
    }

    public void updatePointsOwed(String customerId, long pointsOwed) {
        jdbc.sql("UPDATE customers SET points_owed = :pointsOwed WHERE id = :id")
                .param("pointsOwed", pointsOwed)
                .param("id", customerId)
                .update();
    }
}
