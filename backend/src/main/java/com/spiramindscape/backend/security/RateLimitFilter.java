package com.spiramindscape.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-caller rate limiting (OWASP A06/A07 — abuse, cost, and DoS).
 *
 * <p>Token buckets keyed by authenticated user id, or by client IP when
 * anonymous. The expensive/abusable endpoints get tighter limits than ordinary
 * reads. Over-limit requests get {@code 429} + {@code Retry-After}.
 *
 * <p><b>The counters are shared, not per-instance</b> ({@link SharedRateLimitStore}, BUG-057).
 * They used to be a {@code ConcurrentHashMap} in each instance, which made the real limit
 * "N per minute times however many instances Cloud Run is running" — a number that rises with
 * load, so the guard weakened exactly as it started to matter — and reset every counter each
 * time an instance was recycled. This class now decides only <i>which</i> limit applies and
 * <i>who</i> the caller is; the counting lives behind {@link RateLimitStore}.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("security.ratelimit");

    private final RateLimitStore store;

    public RateLimitFilter(RateLimitStore store) {
        this.store = store;
    }

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
    /** Deliberately low: a crash loop should report once, not hundreds of times. */
    @Value("${spira.ratelimit.client-errors-per-minute:10}")
    private int clientErrorsPerMinute;

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
        if (store.tryConsume(key, limit.perMinute())) {
            chain.doFilter(request, response);
        } else {
            // Throttling used to be completely invisible: a user hitting a limit saw a 429
            // and we saw nothing, so "is the limit too tight or is someone hammering us?"
            // had no answer. The caller key is a user id or an IP — never a token.
            log.warn("rate_limit_block limit={} caller={} path={}",
                    limit.name(), callerKey(request), request.getRequestURI());
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
        // Browser error reports are permitAll (see SecurityConfig), so this throttle is
        // the compensating control that keeps an unauthenticated endpoint from being used
        // to flood the logs. Anonymous callers key by IP.
        if ("POST".equals(method) && path.equals("/api/client-errors")) {
            return new Limit("client-errors", clientErrorsPerMinute);
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

    private record Limit(String name, int perMinute) {}

    // Visible for tests: lets a test inject limits without Spring.
    void configure(int aiChat, int graphql, int login, int keys, int clientErrors) {
        this.enabled = true;
        this.aiChatPerMinute = aiChat;
        this.graphqlPerMinute = graphql;
        this.loginPerMinute = login;
        this.keysPerMinute = keys;
        this.clientErrorsPerMinute = clientErrors;
    }
}
