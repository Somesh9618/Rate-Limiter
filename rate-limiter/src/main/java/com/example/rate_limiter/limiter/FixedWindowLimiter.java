
package com.example.rate_limiter.limiter;

import com.example.rate_limiter.config.RateLimitProperties;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

public class FixedWindowLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final Clock clock;
    private final RedisScript<String> script;

    public FixedWindowLimiter(
            StringRedisTemplate redis,
            RateLimitProperties properties,
            Clock clock) {

        this.redis = Objects.requireNonNull(redis);
        this.properties = Objects.requireNonNull(properties);
        this.clock = Objects.requireNonNull(clock);

        this.script = RedisScript.of(
            new ClassPathResource(
                "scripts/fixed-window.lua"
            ),
            String.class
        );
    }

    @Override
    public RateLimitDecision check(String clientId) {

        // Allow safe client identifiers only.
        if (clientId == null ||
            !clientId.matches("[A-Za-z0-9_-]{1,64}")) {

            throw new IllegalArgumentException(
                "Invalid client ID"
            );
        }

        int limit = properties.capacity();
        long window = properties.windowSeconds();

        // Get the current timestamp.
        long now = clock.instant().getEpochSecond();

        // Calculate the current calendar window.
        long windowId = Math.floorDiv(now, window);

        // Seconds until the next calendar window.
        long resetSeconds =
            window - Math.floorMod(now, window);

        // Unique Redis key for this client and window.
        String key =
            "rate:fixed:" + clientId + ":" + windowId;

        // Retain old counters briefly.
        long expirationSeconds =
            Math.multiplyExact(window, 2);

        String response = redis.execute(
            script,
            List.of(key),
            String.valueOf(limit),
            String.valueOf(expirationSeconds),
            String.valueOf(resetSeconds)
        );

        if (response == null) {
            throw new IllegalStateException(
                "Redis returned no rate-limit decision"
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
                "Invalid allowance value: " + parts[0]
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
