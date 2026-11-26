
package com.example.rate_limiter.limiter;

import com.example.rate_limiter.config.RateLimitProperties;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

public class SlidingWindowLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final RedisScript<String> script;

    public SlidingWindowLimiter(
            StringRedisTemplate redis,
            RateLimitProperties properties) {

        this.redis = Objects.requireNonNull(redis);
        this.properties = Objects.requireNonNull(properties);

        this.script = RedisScript.of(
            new ClassPathResource(
                "scripts/sliding-window.lua"
            ),
            String.class
        );
    }

    @Override
    public RateLimitDecision check(String clientId) {

        // Accept simple, safe identifiers only.
        if (clientId == null ||
                !clientId.matches("[A-Za-z0-9_-]{1,64}")) {

            throw new IllegalArgumentException(
                "Invalid client ID"
            );
        }

        int limit = properties.capacity();

        // Convert seconds into milliseconds.
        long windowMs = Math.multiplyExact(
            properties.windowSeconds(),
            1000L
        );

        // One sorted set for each client.
        String key = "rate:sliding:" + clientId;

        // Unique member for this particular request.
        // Two requests can share the same timestamp
        // while retaining separate entries.
        String requestId = UUID.randomUUID().toString();

        String result = redis.execute(
            script,
            List.of(key),
            String.valueOf(limit),
            String.valueOf(windowMs),
            requestId
        );

        if (result == null) {
            throw new IllegalStateException(
                "Redis script returned no decision"
            );
        }

        // Expected format: allowed:remaining:retryAfter
        String[] parts = result.split(":", -1);

        if (parts.length != 3) {
            throw new IllegalStateException(
                "Invalid Redis response: " + result
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
