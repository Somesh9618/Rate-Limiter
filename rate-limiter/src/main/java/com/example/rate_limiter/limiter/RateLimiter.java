package com.example.rate_limiter.limiter;

public interface RateLimiter {
  RateLimitDecision check(String clientId);

}
