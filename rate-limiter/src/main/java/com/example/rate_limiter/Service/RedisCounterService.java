
package com.example.rate_limiter.Service;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisCounterService {

    private final StringRedisTemplate redis;

    public RedisCounterService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    // Increase the request counter for a client.
    public long increment(String clientId) {

        String key = "demo:counter:" + clientId;

        Long count = redis.opsForValue().increment(key);

        if (count == null) {
            throw new IllegalStateException(
                "Redis did not return a counter"
            );
        }

        // Set expiration only when the counter is new.
        if (count == 1) {
            redis.expire(key, Duration.ofSeconds(120));
        }

        return count;
    }

    // Retrieve the current counter value.
    public String getCount(String clientId) {

        String key = "demo:counter:" + clientId;

        return redis.opsForValue().get(key);
    }

    // Retrieve the key's remaining lifetime.
    public long getTimeToLive(String clientId) {

        String key = "demo:counter:" + clientId;

        Long ttl = redis.getExpire(key);

        return ttl == null ? -2 : ttl;
    }

    // Reset only this demo client's counter.
    public void reset(String clientId) {

        String key = "demo:counter:" + clientId;

        redis.delete(key);
    }
}
