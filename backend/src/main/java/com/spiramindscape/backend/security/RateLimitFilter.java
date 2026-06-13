package com.spiramindscape.backend.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process, per-caller rate limiting (OWASP A06/A07 — abuse, cost, and DoS).
 *
 * <p>Token buckets keyed by authenticated user id, or by client IP when
 * anonymous. The expensive/abusable endpoints get tighter limits than ordinary
 * reads. Over-limit requests get {@code 429} + {@code Retry-After}.
 *
 * <p>Single-instance design: a {@link ConcurrentHashMap} of buckets is enough
 * for this app's scale (Cloud Run rarely runs more than one instance). If it
 * ever scales out, swap to a shared store — the keying stays the same.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** Off in the e2e/test profiles, where a black-box suite fires hundreds of
     *  requests from one IP and would otherwise be throttled. On in prod/dev. */
    @Value("${spira.ratelimit.enabled:true}")
    private boolean enabled;

    @Value("${spira.ratelimit.ai-chat-per-minute:20}")
    private int aiChatPerMinute;
    @Value("${spira.ratelimit.graphql-per-minute:120}")
    private int graphqlPerMinute;
    @Value("${spira.ratelimit.login-per-minute:10}")
    private int loginPerMinute;
    @Value("${spira.ratelimit.keys-per-minute:10}")
    private int keysPerMinute;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }
        Limit limit = limitFor(request);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }
        String key = limit.name() + ":" + callerKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(limit.perMinute()));
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429); // Too Many Requests
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too many requests. Please slow down and try again shortly.\"}");
        }
    }

    /** Which limit applies to this request, or null if unthrottled. */
    private Limit limitFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equals(method) && path.equals("/api/ai/chat")) {
            return new Limit("ai-chat", aiChatPerMinute);
        }
        if ("POST".equals(method) && path.equals("/api/ai/keys")) {
            return new Limit("keys", keysPerMinute);
        }
        if ("POST".equals(method) && path.equals("/graphql")) {
            return new Limit("graphql", graphqlPerMinute);
        }
        if (path.startsWith("/oauth2/authorization")) {
            return new Limit("login", loginPerMinute);
        }
        return null;
    }

    /** Authenticated → user principal name; otherwise the client IP. */
    private String callerKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getName())) {
            return "u:" + auth.getName();
        }
        // ForwardedHeaderFilter (server.forward-headers-strategy=framework) makes
        // getRemoteAddr reflect the real client behind Cloud Run's proxy.
        return "ip:" + request.getRemoteAddr();
    }

    private Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(perMinute)
                        .refillGreedy(perMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private record Limit(String name, int perMinute) {}

    // Visible for tests: lets a test inject limits without Spring.
    void configure(int aiChat, int graphql, int login, int keys) {
        this.enabled = true;
        this.aiChatPerMinute = aiChat;
        this.graphqlPerMinute = graphql;
        this.loginPerMinute = login;
        this.keysPerMinute = keys;
    }
}
