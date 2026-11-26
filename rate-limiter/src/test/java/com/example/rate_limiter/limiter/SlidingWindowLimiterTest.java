
package com.example.rate_limiter.limiter;

import com.example.rate_limiter.config.RateLimitProperties;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SlidingWindowLimiterTest {

    @Autowired
    private StringRedisTemplate redis;

    // Create Sliding Window directly.
    // Spring's selected bean stays Fixed Window today.
    private SlidingWindowLimiter createLimiter(
            int capacity,
            long windowSeconds) {

        RateLimitProperties properties =
            new RateLimitProperties(
                RateLimitAlgorithm.SLIDING_WINDOW,
                capacity,
                windowSeconds,
                1.0
            );

        return new SlidingWindowLimiter(
            redis,
            properties
        );
    }

    // New ID prevents interference between tests.
    private String newClient() {

        return "slide" +
            UUID.randomUUID().toString().replace("-", "");
    }

    @Test
    void shouldAllowFirstFiveRequests() {

        String client = newClient();

        SlidingWindowLimiter limiter =
            createLimiter(5, 60);

        for (int i = 0; i < 5; i++) {

            RateLimitDecision decision =
                limiter.check(client);

            assertTrue(decision.allowed());

            assertEquals(
                4 - i,
                decision.remaining()
            );
        }
    }

    @Test
    void shouldRejectSixthRequest() {

        String client = newClient();

        SlidingWindowLimiter limiter =
            createLimiter(5, 60);

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.check(client).allowed());
        }

        RateLimitDecision sixth =
            limiter.check(client);

        assertFalse(sixth.allowed());
        assertEquals(0, sixth.remaining());

        assertTrue(
            sixth.retryAfterSeconds() >= 1
        );

        assertTrue(
            sixth.retryAfterSeconds() <= 60
        );
    }

    @Test
    void shouldKeepClientsIndependent() {

        String clientA = newClient();
        String clientB = newClient();

        SlidingWindowLimiter limiter =
            createLimiter(5, 60);

        for (int i = 0; i < 5; i++) {
            limiter.check(clientA);
        }

        assertFalse(limiter.check(clientA).allowed());

        RateLimitDecision firstB =
            limiter.check(clientB);

        assertTrue(firstB.allowed());
        assertEquals(4, firstB.remaining());
    }

    @Test
    void shouldRemoveExpiredRequestEvents() {

        String client = newClient();

        String key = "rate:sliding:" + client;

        // Use a capacity of one to make the
        // expiry behavior easy to verify.
        SlidingWindowLimiter limiter =
            createLimiter(1, 60);

        // Insert a request from two minutes ago.
        long oldTimestamp =
            Instant.now().minusSeconds(120)
                .toEpochMilli();

        redis.opsForZSet().add(
            key,
            "old-request",
            oldTimestamp
        );

        assertEquals(
            1L,
            redis.opsForZSet().zCard(key)
        );

        // The Lua script should remove the old event
        // and admit one new request.
        RateLimitDecision decision =
            limiter.check(client);

        assertTrue(decision.allowed());
        assertEquals(0, decision.remaining());

        // Only the new request should remain.
        assertEquals(
            1L,
            redis.opsForZSet().zCard(key)
        );

        assertNull(
            redis.opsForZSet().score(
                key,
                "old-request"
            )
        );
    }
}
