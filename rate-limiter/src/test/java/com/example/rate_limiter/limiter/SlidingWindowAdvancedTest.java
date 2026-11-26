
package com.example.rate_limiter.limiter;

import com.example.rate_limiter.config.RateLimitProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "rate-limiter.algorithm=SLIDING_WINDOW",
    "rate-limiter.capacity=5",
    "rate-limiter.window-seconds=60"
})
class SlidingWindowAdvancedTest {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private RateLimiter selectedLimiter;

    private String clientId() {
        return "advanced" +
            UUID.randomUUID().toString().replace("-", "");
    }

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

    @Test
    void springShouldSelectSlidingWindow() {

        assertInstanceOf(
            SlidingWindowLimiter.class,
            selectedLimiter
        );
    }

    @Test
    void shouldAllowOnlyTenConcurrentRequests()
            throws Exception {

        int capacity = 10;
        int attempts = 100;

        String client = clientId();

        SlidingWindowLimiter limiter =
            createLimiter(capacity, 60);

        ExecutorService executor =
            Executors.newFixedThreadPool(20);

        CountDownLatch startSignal =
            new CountDownLatch(1);

        List<Future<Boolean>> futures =
            new ArrayList<>();

        try {

            for (int i = 0; i < attempts; i++) {

                futures.add(
                    executor.submit(() -> {

                        startSignal.await();

                        return limiter.check(client)
                            .allowed();
                    })
                );
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
                "Sliding Window allowed: " + allowed
            );

            System.out.println(
                "Sliding Window rejected: " + rejected
            );

        } finally {
            startSignal.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void exactRollingBoundaryShouldReleaseOneSlot() {

        String key =
            "rate:sliding:" + clientId();

        RedisScript<String> script = RedisScript.of(
            new ClassPathResource(
                "scripts/sliding-window.lua"
            ),
            String.class
        );

        // 60-second rolling window.
        String windowMs = "60000";

        // Event A was accepted at 940001 ms.
        String first = redis.execute(
            script,
            List.of(key),
            "1",
            windowMs,
            "request-a",
            "940001"
        );

        assertEquals("1:0:0", first);

        // At 1000000 ms, event A is only
        // 59999 ms old: it still counts.
        String second = redis.execute(
            script,
            List.of(key),
            "1",
            windowMs,
            "request-b",
            "1000000"
        );

        assertEquals("0:0:1", second);

        // At 1000001 ms, event A is
        // exactly 60000 ms old and expires.
        String third = redis.execute(
            script,
            List.of(key),
            "1",
            windowMs,
            "request-c",
            "1000001"
        );

        assertEquals("1:0:0", third);

        assertEquals(
            1L,
            redis.opsForZSet().zCard(key)
        );

        assertNull(
            redis.opsForZSet().score(
                key,
                "request-a"
            )
        );
    }

    @Test
    void shouldCalculateRetryTimeFromOldestEvent() {

        String key =
            "rate:sliding:" + clientId();

        RedisScript<String> script = RedisScript.of(
            new ClassPathResource(
                "scripts/sliding-window.lua"
            ),
            String.class
        );

        // Capacity = 1, window = 60 seconds.
        // First request accepted at 1,000,000 ms.
        String first = redis.execute(
            script,
            List.of(key),
            "1",
            "60000",
            "request-1",
            "1000000"
        );

        assertEquals("1:0:0", first);

        // A second request comes after 15 seconds.
        String second = redis.execute(
            script,
            List.of(key),
            "1",
            "60000",
            "request-2",
            "1015000"
        );

        // Oldest request expires in 45 seconds.
        assertEquals("0:0:45", second);
    }
}
