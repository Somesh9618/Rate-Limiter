
package com.example.rate_limiter.limiter;

import com.example.rate_limiter.config.RateLimitProperties;

import java.util.List;
import java.util.Objects;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

public class TokenBucketLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final RedisScript<String> script;

    public TokenBucketLimiter(
            StringRedisTemplate redis,
            RateLimitProperties properties) {

        this.redis = Objects.requireNonNull(redis);
        this.properties = Objects.requireNonNull(properties);

        this.script = RedisScript.of(
            new ClassPathResource(
                "scripts/token-bucket.lua"
            ),
            String.class
        );
    }

    @Override
    public RateLimitDecision check(String clientId) {

        // Match the validation used by earlier algorithms.
        if (clientId == null ||
                !clientId.matches("[A-Za-z0-9_-]{1,64}")) {

            throw new IllegalArgumentException(
                "Invalid client ID"
            );
        }

        int capacity = properties.capacity();

        double refillRate =
            properties.refillTokensPerSecond();

        // Expire only after enough inactivity for
        // a fully empty bucket to replenish.
        double fullRefillMs =
            Math.ceil(1000.0 * capacity / refillRate);

        if (!Double.isFinite(fullRefillMs)
                || fullRefillMs > 1_000_000_000_000L) {

            throw new IllegalArgumentException(
                "Configured refill time is too large"
            );
        }

        // Additional 1-second cleanup buffer.
        long ttlMs = (long) fullRefillMs + 1000L;

        String key = "rate:bucket:" + clientId;

        // Execute the complete decision atomically.
        String response = redis.execute(
            script,
            List.of(key),
            String.valueOf(capacity),
            String.valueOf(refillRate),
            String.valueOf(ttlMs)
        );

        if (response == null) {
            throw new IllegalStateException(
                "Redis returned no Token Bucket decision"
            );
        }

        String[] parts = response.split(":", -1);

        if (parts.length != 3) {
            throw new IllegalStateException(
                "Invalid Redis response: " + response
            );
        }

        boolean allowed = switch (parts[0]) {
            case "1" -> true;
            case "0" -> false;
            default -> throw new IllegalStateException(
                "Invalid allowance: " + parts[0]
            );
        };

        long remaining = Long.parseLong(parts[1]);
        long retryAfter = Long.parseLong(parts[2]);

        return new RateLimitDecision(
            allowed,
            remaining,
            retryAfter
        );
    }
}
