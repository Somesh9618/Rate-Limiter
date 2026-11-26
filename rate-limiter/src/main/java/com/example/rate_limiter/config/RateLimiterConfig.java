
package com.example.rate_limiter.config;

import com.example.rate_limiter.limiter.FixedWindowLimiter;
import com.example.rate_limiter.limiter.RateLimiter;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimiterConfig {

    @Bean
    public Clock rateLimitClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RateLimiter rateLimiter(
            org.springframework.data.redis.core.StringRedisTemplate redis,
            RateLimitProperties properties,
            Clock rateLimitClock) {

        if (properties.algorithm()
                != com.example.rate_limiter.limiter
                    .RateLimitAlgorithm.FIXED_WINDOW) {

            throw new IllegalStateException(
                "Only FIXED_WINDOW is implemented so far"
            );
        }

        return new FixedWindowLimiter(
            redis,
            properties,
            rateLimitClock
        );
    }
}
