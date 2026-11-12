package com.loyalty;

import com.loyalty.error.InsufficientPointsException;
import com.loyalty.service.LoyaltyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcurrencyTest extends IntegrationTest {

    @Autowired
    private LoyaltyService service;

    @Test
    void concurrentRedemptionsCannotDoubleSpend() throws Exception {
        service.recordPurchase("alice", "order-1", new BigDecimal("100")); // exactly one coffee's worth

        int attempts = 10;
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        service.redeem("alice", "free-coffee");
                        succeeded.incrementAndGet();
                    } catch (InsufficientPointsException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, succeeded.get());
        assertEquals(attempts - 1, rejected.get());
        assertEquals(0, service.balance("alice").availablePoints());
    }
}
