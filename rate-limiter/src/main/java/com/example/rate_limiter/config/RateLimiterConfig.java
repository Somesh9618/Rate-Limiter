
package com.example.rate_limiter.config;

import com.example.rate_limiter.limiter.FixedWindowLimiter;
import com.example.rate_limiter.limiter.SlidingWindowLimiter;
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

    // Shared clock used by Fixed Window.
    @Bean
    public Clock rateLimitClock() {
        return Clock.systemUTC();
    }

    // Selected when FIXED_WINDOW is configured.
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

    // Selected when SLIDING_WINDOW is configured.
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
}
