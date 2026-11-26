package com.example.rate_limiter.Service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("redis-demo")
public class RedisConnectionDemo implements CommandLineRunner {

    private final RedisCounterService counterService;

    public RedisConnectionDemo(
            RedisCounterService counterService) {

        this.counterService = counterService;
    }

    @Override
    public void run(String... args) {

        String clientId = "user102";

        System.out.println("===== REDIS DEMO START =====");

        // Start the demonstration with a fresh key.
        counterService.reset(clientId);

        for (int i = 1; i <= 3; i++) {

            long count = counterService.increment(clientId);

            System.out.println(
                "Request " + i + " -> Counter: " + count
            );
        }

        String value = counterService.getCount(clientId);
        long ttl = counterService.getTimeToLive(clientId);

        System.out.println("Final Redis value: " + value);
        System.out.println("Remaining TTL: " + ttl + " seconds");

        System.out.println("===== REDIS DEMO END =====");
    }
}

