package com.example.rate_limiter.config;


import com.example.rate_limiter.limiter.SlidingWindowLimiter;
import com.example.rate_limiter.limiter.RateLimiter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
"rate-limiter.algorithm=SLIDING_WINDOW"
})
class SlidingWindowSelectionTest {

@Autowired
private RateLimiter limiter;

@Autowired
private ApplicationContext context;

@Test
void shouldSelectSlidingWindow() {

assertInstanceOf(
SlidingWindowLimiter.class,
limiter
);
}

@Test
void shouldRegisterExactlyOneLimiter() {

assertEquals(
1,
context.getBeansOfType(RateLimiter.class).size()
);
}
}