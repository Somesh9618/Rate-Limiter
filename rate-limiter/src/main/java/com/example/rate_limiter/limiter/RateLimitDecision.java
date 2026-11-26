package com.example.rate_limiter.limiter;

public record RateLimitDecision(boolean allowed, long remaining, long retryAfterSeconds) {
   public RateLimitDecision{
    if(remaining < 0){
        throw new IllegalArgumentException("Remaining requests cannot be negative");
    }
    if(retryAfterSeconds < 0){
        throw new IllegalArgumentException("Retry after seconds cannot be negative");
    }
   }
}
