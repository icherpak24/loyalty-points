package com.loyalty.config;

import com.loyalty.domain.LoyaltyPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.time.Clock;

@Configuration
public class LoyaltyConfig {

    private static final int BUSY_TIMEOUT_MILLIS = 5_000;

    /**
     * SQLite data source. Defining it here replaces Spring Boot's default pooled data source, because
     * the SQLite settings below must apply to every connection:
     *
     * <ul>
     *   <li><b>IMMEDIATE transactions</b> take SQLite's write lock when the transaction begins rather
     *       than on its first write. Two concurrent redemptions therefore can't both read the same
     *       balance and both spend it: the second waits, then sees the first one's result.</li>
     *   <li><b>Busy timeout</b>: how long a transaction waits for that lock before failing.</li>
     *   <li><b>WAL journal</b> lets readers proceed while a write is in progress.</li>
     * </ul>
     */
    @Bean
    public DataSource dataSource(@Value("${loyalty.db.path}") String dbPath) {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(BUSY_TIMEOUT_MILLIS);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        return dataSource;
    }

    /** Injected rather than calling {@code Instant.now()} directly, so tests can control time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public LoyaltyPolicy loyaltyPolicy() {
        return LoyaltyPolicy.standard();
    }
}
