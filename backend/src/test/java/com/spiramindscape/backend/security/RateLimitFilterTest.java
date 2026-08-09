package com.spiramindscape.backend.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
        // Tiny limits so the test is fast and deterministic.
        filter.configure(2, 2, 2, 2, 2);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest aiChat(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/ai/chat");
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    @DisplayName("requests within the limit pass; the one over the limit gets 429 + Retry-After")
    void blocksOverLimit() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(aiChat("1.2.3.4"), res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(aiChat("1.2.3.4"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("different client IPs have independent buckets")
    void perIpIsolation() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 2; i++) filter.doFilter(aiChat("1.1.1.1"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse other = new MockHttpServletResponse();
        filter.doFilter(aiChat("2.2.2.2"), other, chain);
        assertThat(other.getStatus()).isEqualTo(200); // fresh bucket for a new IP
    }

    @Test
    @DisplayName("authenticated users are keyed by principal, not IP")
    void perUserKeying() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-42", "x",
                        AuthorityUtils.createAuthorityList("ROLE_USER")));
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 2; i++) filter.doFilter(aiChat("9.9.9.9"), new MockHttpServletResponse(), chain);
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(aiChat("9.9.9.9"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("when disabled (e2e/test profile), nothing is throttled")
    void disabledPassesEverything() throws Exception {
        RateLimitFilter disabled = new RateLimitFilter(); // enabled defaults to false
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 50; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            disabled.doFilter(aiChat("1.2.3.4"), res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("the unauthenticated client-error endpoint is throttled")
    void clientErrorsEndpointIsThrottled() throws Exception {
        // POST /api/client-errors is permitAll and CSRF-exempt, so this throttle is the
        // control that stops anyone using it to flood the logs. If the limit is ever
        // dropped, that endpoint becomes an open write channel into Cloud Logging.
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(clientError("4.4.4.4"), res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(clientError("4.4.4.4"), blocked, chain);
        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    private MockHttpServletRequest clientError(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/client-errors");
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    @DisplayName("unthrottled paths (e.g. GET /health) always pass")
    void unthrottledPaths() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockHttpServletRequest req = new MockHttpServletRequest("GET", "/health");
            req.setRemoteAddr("3.3.3.3");
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }
}
