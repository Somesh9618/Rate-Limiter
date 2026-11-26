
package com.example.rate_limiter.filter;

import com.example.rate_limiter.config.RateLimitProperties;
import com.example.rate_limiter.limiter.RateLimitDecision;
import com.example.rate_limiter.limiter.RateLimiter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Component
public class RateLimitFilter implements Filter {

    private final RateLimiter limiter;
    private final RateLimitProperties properties;
    private final Set<String> validKeys;

    public RateLimitFilter(
            RateLimiter limiter,
            RateLimitProperties properties,
            @Value("${rate-limiter.demo-api-keys}")
            String configuredKeys) {

        this.limiter = limiter;
        this.properties = properties;

        this.validKeys = Arrays.stream(
                configuredKeys.split(",")
            )
            .map(String::trim)
            .filter(key -> !key.isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req =
            (HttpServletRequest) request;

        HttpServletResponse res =
            (HttpServletResponse) response;

        // Only protect the product API.
        if (!req.getRequestURI()
                .equals("/api/products")) {

            chain.doFilter(request, response);
            return;
        }

        // Read the local demo client's API key.
        String clientId = req.getHeader("X-API-Key");

        if (clientId == null ||
                !validKeys.contains(clientId)) {

            writeError(
                res,
                401,
                "Valid API key required"
            );

            return;
        }

        RateLimitDecision decision;

        try {
            decision = limiter.check(clientId);

        } catch (DataAccessException e) {

            // Redis connectivity, timeout, or data-access failure.
            writeError(
                res,
                503,
                "Rate limiting service unavailable"
            );

            return;

        } catch (IllegalStateException e) {

            // Unexpected or invalid response from limiter.
            writeError(
                res,
                503,
                "Rate limiting service unavailable"
            );

            return;
        }

        // Inform the client of the configured quota.
        res.setHeader(
            "RateLimit-Limit",
            String.valueOf(properties.capacity())
        );

        res.setHeader(
            "RateLimit-Remaining",
            String.valueOf(decision.remaining())
        );

        if (!decision.allowed()) {

            res.setHeader(
                "Retry-After",
                String.valueOf(decision.retryAfterSeconds())
            );

            writeError(
                res,
                429,
                "Too Many Requests"
            );

            return;
        }

        // Proceed to the product controller.
        chain.doFilter(request, response);
    }

    private void writeError(
            HttpServletResponse response,
            int status,
            String message) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        response.getWriter().write(
            "{\"error\":\"" + message + "\"}"
        );
    }
}
