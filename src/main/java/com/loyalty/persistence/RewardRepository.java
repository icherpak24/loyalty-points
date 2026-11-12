package com.loyalty.persistence;

import com.loyalty.domain.Reward;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RewardRepository {

    private final JdbcClient jdbc;

    public RewardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // Reward's fields match the columns (cost_points -> costPoints), so JdbcClient maps rows to the
    // record automatically and no hand-written RowMapper is needed.

    public Optional<Reward> find(String rewardId) {
        return jdbc.sql("SELECT id, name, cost_points FROM rewards WHERE id = :id")
                .param("id", rewardId)
                .query(Reward.class)
                .optional();
    }

    public List<Reward> findAll() {
        return jdbc.sql("SELECT id, name, cost_points FROM rewards ORDER BY cost_points, id")
                .query(Reward.class)
                .list();
    }
}
