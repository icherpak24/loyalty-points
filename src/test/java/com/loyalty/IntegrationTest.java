package com.loyalty;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Base for tests that need the full application. All subclasses share one Spring context (and so
 * one temporary database file); each test starts from empty tables and a clock fixed at {@link #START}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(IntegrationTest.TestClockConfig.class)
public abstract class IntegrationTest {

    protected static final Instant START = Instant.parse("2026-01-15T10:00:00Z");

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @DynamicPropertySource
    static void useTemporaryDatabase(DynamicPropertyRegistry registry) throws IOException {
        Path dir = Files.createTempDirectory("loyalty-test");
        registry.add("loyalty.db.path", () -> dir.resolve("test.db").toString());
    }

    @Autowired
    protected MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetState() {
        clock.set(START);
        // Children before parents, to satisfy foreign keys. The seeded reward catalog is kept.
        for (String table : new String[] {"lot_consumptions", "redemptions", "point_lots", "purchases", "customers"}) {
            jdbc.update("DELETE FROM " + table);
        }
    }
}
