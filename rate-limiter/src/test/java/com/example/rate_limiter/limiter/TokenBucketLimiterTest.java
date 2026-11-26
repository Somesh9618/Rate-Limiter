
package com.example.rate_limiter.limiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import com.example.rate_limiter.config.RateLimitProperties;

@SpringBootTest(properties = {
    "rate-limiter.algorithm=SLIDING_WINDOW"
})
class TokenBucketLimiterTest {

    @Autowired
    private StringRedisTemplate redis;

    private String newClient() {
        return "bucket" +
            UUID.randomUUID().toString().replace("-", "");
    }

    private String key(String client) {
        return "rate:bucket:" + client;
    }

    private TokenBucketLimiter createLimiter(
            int capacity,
            double refillRate) {

        RateLimitProperties properties =
            new RateLimitProperties(
                RateLimitAlgorithm.TOKEN_BUCKET,
                capacity,
                60,
                refillRate
            );

        return new TokenBucketLimiter(redis, properties);
    }

    // Executes the real Lua script with controlled time.
    // This test-only helper does not change production Java.
    private String executeAt(
            String client,
            int capacity,
            double refillRate,
            long timeMs) {

        RedisScript<String> script = RedisScript.of(
            new ClassPathResource(
                "scripts/token-bucket.lua"
            ),
            String.class
        );

        long ttlMs =
            (long) Math.ceil(
                1000.0 * capacity / refillRate
            ) + 1000L;

        return redis.execute(
            script,
            List.of(key(client)),
            String.valueOf(capacity),
            String.valueOf(refillRate),
            String.valueOf(ttlMs),
            String.valueOf(timeMs)
        );
    }

    @Test
    void shouldAllowFiveRequestsFromFullBucket() {

        String client = newClient();

        for (int i = 0; i < 5; i++) {

            String result = executeAt(
                client, 5, 1.0, 1000000L
            );

            assertEquals(
                "1:" + (4 - i) + ":0",
                result
            );
        }
    }

    @Test
    void shouldRejectSixthImmediateRequest() {

        String client = newClient();

        for (int i = 0; i < 5; i++) {
            executeAt(client, 5, 1.0, 1000000L);
        }

        String sixth = executeAt(
            client, 5, 1.0, 1000000L
        );

        assertEquals("0:0:1", sixth);
    }

    @Test
    void shouldRefillOneTokenAfterOneSecond() {

        String client = newClient();

        // Consume all five tokens at one timestamp.
        for (int i = 0; i < 5; i++) {
            executeAt(client, 5, 1.0, 1000000L);
        }

        // Only half a token has refilled.
        String halfSecond = executeAt(
            client, 5, 1.0, 1000500L
        );

        assertEquals("0:0:1", halfSecond);

        // One full second after depletion:
        // one token is available and consumed.
        String afterOneSecond = executeAt(
            client, 5, 1.0, 1001000L
        );

        assertEquals("1:0:0", afterOneSecond);
    }

    @Test
    void bucketShouldNeverExceedCapacity() {

        String client = newClient();

        // First request: 5 -> 4 tokens.
        String first = executeAt(
            client, 5, 1.0, 1000000L
        );

        assertEquals("1:4:0", first);

        // Twenty seconds pass. The bucket refills,
        // but must stop at capacity 5.
        // This request then consumes one token.
        String later = executeAt(
            client, 5, 1.0, 1020000L
        );

        assertEquals("1:4:0", later);

        String stored =
            (String) redis.opsForHash().get(
                key(client),
                "tokens"
            );

        assertNotNull(stored);

        assertEquals(
            4.0,
            Double.parseDouble(stored),
            0.000001
        );
    }

    @Test
    void differentClientsShouldHaveSeparateBuckets() {

        String clientA = newClient();
        String clientB = newClient();

        for (int i = 0; i < 5; i++) {
            executeAt(clientA, 5, 1.0, 1000000L);
        }

        assertEquals(
            "0:0:1",
            executeAt(clientA, 5, 1.0, 1000000L)
        );

        assertEquals(
            "1:4:0",
            executeAt(clientB, 5, 1.0, 1000000L)
        );
    }

    @Test
    void shouldAllowOnlyTenConcurrentRequests()
            throws Exception {

        int capacity = 10;
        int attempts = 100;

        String client = newClient();

        // Very slow refill prevents a whole new
        // token appearing during the concurrency test.
        TokenBucketLimiter limiter =
            createLimiter(capacity, 0.001);

        ExecutorService executor =
            Executors.newFixedThreadPool(20);

        CountDownLatch startSignal =
            new CountDownLatch(1);

        List<Future<Boolean>> futures =
            new ArrayList<>();

        try {
            for (int i = 0; i < attempts; i++) {

                futures.add(executor.submit(() -> {

                    startSignal.await();

                    return limiter.check(client).allowed();
                }));
            }

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
                "Token Bucket allowed: " + allowed
            );

            System.out.println(
                "Token Bucket rejected: " + rejected
            );

        } finally {
            startSignal.countDown();
            executor.shutdownNow();

            // Remove this long-TTL test bucket.
            redis.delete(key(client));
        }
    }

    @Test
    void shouldRejectInvalidClientId() {

        TokenBucketLimiter limiter =
            createLimiter(5, 1.0);

        assertThrows(
            IllegalArgumentException.class,
            () -> limiter.check("bad client !")
        );
    }
}
