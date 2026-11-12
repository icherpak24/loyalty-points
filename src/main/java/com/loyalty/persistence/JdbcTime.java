package com.loyalty.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/** Timestamps are persisted as UTC epoch milliseconds. */
final class JdbcTime {

    private JdbcTime() {
    }

    static long toMillis(Instant instant) {
        return instant.toEpochMilli();
    }

    static Instant fromMillis(ResultSet rs, String column) throws SQLException {
        long millis = rs.getLong(column);
        return rs.wasNull() ? null : Instant.ofEpochMilli(millis);
    }
}
