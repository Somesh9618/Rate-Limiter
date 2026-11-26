
package com.example.rate_limiter.config;

import com.example.rate_limiter.limiter.FixedWindowLimiter;
import com.example.rate_limiter.limiter.SlidingWindowLimiter;
import com.example.rate_limiter.limiter.TokenBucketLimiter;
import com.example.rate_limiter.limiter.RateLimiter;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimiterConfig {

    // Fixed Window uses the Java application's clock.
    @Bean
    public Clock rateLimitClock() {
        return Clock.systemUTC();
    }

    // Algorithm 1: Fixed Window.
    @Bean
    @ConditionalOnProperty(
        name = "rate-limiter.algorithm",
        havingValue = "FIXED_WINDOW"
    )
    public RateLimiter fixedWindowLimiter(
            StringRedisTemplate redis,
            RateLimitProperties properties,
            Clock rateLimitClock) {

        return new FixedWindowLimiter(
            redis,
            properties,
            rateLimitClock
        );
    }

    // Algorithm 2: Sliding Window Log.
    @Bean
    @ConditionalOnProperty(
        name = "rate-limiter.algorithm",
        havingValue = "SLIDING_WINDOW"
    )
    public RateLimiter slidingWindowLimiter(
            StringRedisTemplate redis,
            RateLimitProperties properties) {

        return new SlidingWindowLimiter(
            redis,
            properties
        );
    }

    // Algorithm 3: Token Bucket.
    @Bean
    @ConditionalOnProperty(
        name = "rate-limiter.algorithm",
        havingValue = "TOKEN_BUCKET"
    )
    public RateLimiter tokenBucketLimiter(
            StringRedisTemplate redis,
            RateLimitProperties properties) {

        return new TokenBucketLimiter(
            redis,
            properties
        );
    }
}
