package com.example.rate_limiter.config;

import com.example.rate_limiter.limiter.RateLimitAlgorithm;
import org.springframework.boot.context.properties.ConfigurationProperties;


//It will be used to store the settings for all three algorithms.
@ConfigurationProperties(prefix = "rate-limiter")
public record RateLimitProperties(
  RateLimitAlgorithm algorithm,
  int capacity, long windowSeconds, double refillTokensPerSecond
) {
  public RateLimitProperties {
    if(algorithm == null){
      throw new IllegalArgumentException("Algorithm cannot be null");
    }
    if(capacity <= 0){
      throw new IllegalArgumentException("Capacity must be greater than 0");
    }
    if(windowSeconds <= 0){
      throw new IllegalArgumentException("Window seconds must be greater than 0");
    }
    if(!Double.isFinite(refillTokensPerSecond) || refillTokensPerSecond <= 0){
      throw new IllegalArgumentException("Refill tokens per second must be a positive finite number");
    }
  }
  
}

