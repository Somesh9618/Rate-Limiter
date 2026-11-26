
package com.example.rate_limiter.limiter;

import com.example.rate_limiter.config.RateLimitProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FixedWindowLimiterTest {

    @Autowired
    private StringRedisTemplate redis;

    // Creates an independent limiter with test settings.
    private FixedWindowLimiter createLimiter(
            int capacity,
            long windowSeconds,
            Instant testTime) {

        RateLimitProperties properties =
            new RateLimitProperties(
                RateLimitAlgorithm.FIXED_WINDOW,
                capacity,
                windowSeconds,
                1.0
            );

        Clock clock = Clock.fixed(
            testTime,
            ZoneOffset.UTC
        );

        return new FixedWindowLimiter(
            redis,
            properties,
            clock
        );
    }

    // Unique client ensures tests don't share counters.
    private String newClient() {
        return "test" +
            UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void shouldAllowFirstFiveRequests() {

        String client = newClient();

        FixedWindowLimiter limiter = createLimiter(
            5,
            60,
            Instant.parse("2026-11-26T12:00:30Z")
        );

        for (int i = 0; i < 5; i++) {

            RateLimitDecision decision =
                limiter.check(client);

            assertTrue(decision.allowed());
            assertEquals(4 - i, decision.remaining());
            assertEquals(0, decision.retryAfterSeconds());
        }
    }

    @Test
    void shouldRejectSixthRequest() {

        String client = newClient();

        FixedWindowLimiter limiter = createLimiter(
            5,
            60,
            Instant.parse("2026-11-26T12:00:30Z")
        );

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.check(client).allowed());
        }

        RateLimitDecision sixth = limiter.check(client);

        assertFalse(sixth.allowed());
        assertEquals(0, sixth.remaining());
        assertEquals(30, sixth.retryAfterSeconds());
    }

    @Test
    void differentClientsHaveDifferentCounters() {

        String clientA = newClient();
        String clientB = newClient();

        FixedWindowLimiter limiter = createLimiter(
            5,
            60,
            Instant.parse("2026-11-26T12:00:30Z")
        );

        for (int i = 0; i < 5; i++) {
            limiter.check(clientA);
        }

        assertFalse(limiter.check(clientA).allowed());

        RateLimitDecision decisionB =
            limiter.check(clientB);

        assertTrue(decisionB.allowed());
        assertEquals(4, decisionB.remaining());
    }

    @Test
    void shouldResetAtNewWindow() {

        String client = newClient();

        FixedWindowLimiter beforeBoundary = createLimiter(
            5,
            60,
            Instant.parse("2026-11-26T12:00:59Z")
        );

        FixedWindowLimiter afterBoundary = createLimiter(
            5,
            60,
            Instant.parse("2026-11-26T12:01:00Z")
        );

        for (int i = 0; i < 5; i++) {
            assertTrue(
                beforeBoundary.check(client).allowed()
            );
        }

        assertFalse(
            beforeBoundary.check(client).allowed()
        );

        // One second later, a new window begins.
        RateLimitDecision newWindow =
            afterBoundary.check(client);

        assertTrue(newWindow.allowed());
        assertEquals(4, newWindow.remaining());
    }

    @Test
    void shouldHandleExactlyOneAllowedRequest() {

        String client = newClient();

        FixedWindowLimiter limiter = createLimiter(
            1,
            60,
            Instant.parse("2026-11-26T12:00:30Z")
        );

        assertTrue(limiter.check(client).allowed());
        assertFalse(limiter.check(client).allowed());
    }

    @Test
    void shouldRejectInvalidClientId() {

        FixedWindowLimiter limiter = createLimiter(
            5,
            60,
            Instant.parse("2026-11-26T12:00:30Z")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> limiter.check("invalid client !")
        );
    }

    @Test
    void shouldEnforceLimitUnderConcurrency()
            throws Exception {

        int capacity = 10;
        int totalRequests = 100;

        String client = newClient();

        FixedWindowLimiter limiter = createLimiter(
            capacity,
            60,
            Instant.parse("2026-11-26T12:00:30Z")
        );

        ExecutorService executor =
            Executors.newFixedThreadPool(20);

        CountDownLatch startSignal =
            new CountDownLatch(1);

        List<Future<Boolean>> futures =
            new ArrayList<>();

        try {
            for (int i = 0; i < totalRequests; i++) {

                futures.add(executor.submit(() -> {

                    startSignal.await();

                    return limiter.check(client).allowed();
                }));
            }

            // Release all tasks together.
            startSignal.countDown();

            int allowed = 0;
            int rejected = 0;

            for (Future<Boolean> future : futures) {

                boolean result =
                    future.get(30, TimeUnit.SECONDS);

                if (result) {
                    allowed++;
                } else {
                    rejected++;
                }
            }

            assertEquals(10, allowed);
            assertEquals(90, rejected);

            System.out.println(
                "Concurrent allowed: " + allowed
            );

            System.out.println(
                "Concurrent rejected: " + rejected
            );

        } finally {
            executor.shutdownNow();
        }
    }
}
