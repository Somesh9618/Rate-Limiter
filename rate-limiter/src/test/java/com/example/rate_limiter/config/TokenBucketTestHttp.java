
package com.example.rate_limiter.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "rate-limiter.algorithm=TOKEN_BUCKET",
        "rate-limiter.capacity=5",
        "rate-limiter.refill-tokens-per-second=0.001",
        "rate-limiter.demo-api-keys=day8-client-1,day8-client-2"
    }
)
class TokenBucketHttpTest {

    @Autowired
    private StringRedisTemplate redis;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client =
        HttpClient.newHttpClient();

    @BeforeEach
    void resetBuckets() {

        redis.delete("rate:bucket:day8-client-1");
        redis.delete("rate:bucket:day8-client-2");
    }

    private HttpResponse<String> request(
            String apiKey) throws Exception {

        HttpRequest.Builder builder =
            HttpRequest.newBuilder()
                .uri(URI.create(
                    "http://localhost:" + port
                    + "/api/products"
                ))
                .GET();

        if (apiKey != null) {
            builder.header("X-API-Key", apiKey);
        }

        return client.send(
            builder.build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    @Test
    void shouldReturn429AfterFiveRequests()
            throws Exception {

        for (int i = 0; i < 5; i++) {

            HttpResponse<String> response =
                request("day8-client-1");

            assertEquals(200, response.statusCode());

            assertEquals(
                String.valueOf(4 - i),
                response.headers()
                    .firstValue("RateLimit-Remaining")
                    .orElse("")
            );
        }

        HttpResponse<String> sixth =
            request("day8-client-1");

        assertEquals(429, sixth.statusCode());

        assertEquals(
            "0",
            sixth.headers()
                .firstValue("RateLimit-Remaining")
                .orElse("")
        );

        String retryHeader =
            sixth.headers()
                .firstValue("Retry-After")
                .orElseThrow();

        assertTrue(
            Long.parseLong(retryHeader) >= 1
        );
    }

    @Test
    void shouldRejectMissingApiKey()
            throws Exception {

        HttpResponse<String> response =
            request(null);

        assertEquals(401, response.statusCode());
    }

    @Test
    void clientsShouldHaveIndependentBuckets()
            throws Exception {

        for (int i = 0; i < 5; i++) {
            assertEquals(
                200,
                request("day8-client-1").statusCode()
            );
        }

        assertEquals(
            429,
            request("day8-client-1").statusCode()
        );

        HttpResponse<String> secondClient =
            request("day8-client-2");

        assertEquals(
            200,
            secondClient.statusCode()
        );

        assertEquals(
            "4",
            secondClient.headers()
                .firstValue("RateLimit-Remaining")
                .orElse("")
        );
    }
}
