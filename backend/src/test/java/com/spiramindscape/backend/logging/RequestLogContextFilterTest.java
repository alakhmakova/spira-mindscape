package com.spiramindscape.backend.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLogContextFilterTest {

    private final RequestLogContextFilter filter = new RequestLogContextFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** Captures whatever the MDC held *inside* the chain, which is the only place it matters. */
    private static final class MdcCapturingChain implements FilterChain {
        String traceId;
        String userId;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response) {
            this.traceId = MDC.get(RequestLogContextFilter.TRACE_ID);
            this.userId = MDC.get(RequestLogContextFilter.USER_ID);
        }
    }

    private static MockHttpServletRequest request(String cloudTraceHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/graphql");
        if (cloudTraceHeader != null) {
            request.addHeader(RequestLogContextFilter.CLOUD_TRACE_HEADER, cloudTraceHeader);
        }
        return request;
    }

    @Test
    @DisplayName("with no inbound header a trace id is generated and visible inside the chain")
    void generatesTraceIdWhenNoHeader() throws Exception {
        MdcCapturingChain chain = new MdcCapturingChain();
        filter.doFilter(request(null), new MockHttpServletResponse(), chain);
        assertThat(chain.traceId).isNotBlank();
    }

    @Test
    @DisplayName("Cloud Run's X-Cloud-Trace-Context supplies the id, so our logs join Cloud Trace")
    void reusesCloudRunTraceId() throws Exception {
        MdcCapturingChain chain = new MdcCapturingChain();
        filter.doFilter(request("105445aa7843bc8bf206b12000100000/1;o=1"),
                new MockHttpServletResponse(), chain);
        assertThat(chain.traceId).isEqualTo("105445aa7843bc8bf206b12000100000");
    }

    @Test
    @DisplayName("a malformed inbound header is ignored rather than propagated")
    void ignoresMalformedHeader() throws Exception {
        // A junk value must not end up in the Cloud Trace resource name, where it would
        // produce a dead link and could carry whatever a caller chose to send.
        MdcCapturingChain chain = new MdcCapturingChain();
        filter.doFilter(request("not-a-trace-id/1;o=1"), new MockHttpServletResponse(), chain);
        assertThat(chain.traceId).isNotBlank().isNotEqualTo("not-a-trace-id");
        assertThat(chain.traceId).hasSize(32);
    }

    @Test
    @DisplayName("the trace id is echoed back so a caller can quote it without hitting an error")
    void setsResponseHeader() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MdcCapturingChain chain = new MdcCapturingChain();
        filter.doFilter(request(null), response, chain);
        assertThat(response.getHeader(RequestLogContextFilter.RESPONSE_HEADER))
                .isEqualTo(chain.traceId);
    }

    @Test
    @DisplayName("the MDC is empty again after the request — Tomcat reuses threads")
    void clearsMdcAfterRequest() throws Exception {
        filter.doFilter(request(null), new MockHttpServletResponse(), new MdcCapturingChain());
        assertThat(MDC.get(RequestLogContextFilter.TRACE_ID)).isNull();
        assertThat(MDC.get(RequestLogContextFilter.USER_ID)).isNull();
    }

    @Test
    @DisplayName("the MDC is cleared even when the chain throws — the leak that matters")
    void clearsMdcWhenChainThrows() {
        // Without the finally block this is the case that leaks: a request that blew up
        // leaves its trace id AND user id on a pooled thread, so the next request served
        // by that thread logs another user's id. Failing requests are exactly the ones
        // whose logs get read, which is what makes the mis-attribution dangerous.
        FilterChain exploding = (request, response) -> {
            throw new ServletException("boom");
        };
        MDC.put(RequestLogContextFilter.USER_ID, "42");

        assertThatThrownBy(() ->
                filter.doFilter(request(null), new MockHttpServletResponse(), exploding))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get(RequestLogContextFilter.TRACE_ID)).isNull();
        assertThat(MDC.get(RequestLogContextFilter.USER_ID)).isNull();
    }

    @Test
    @DisplayName("the async dispatch reuses the same id rather than minting a second one")
    void reusesIdAcrossAsyncDispatch() throws Exception {
        // The AI chat streams over an SseEmitter, so the filter re-enters on the ASYNC
        // dispatch with the same request object. A fresh id there would split one
        // conversation's logs into two unrelated traces.
        MockHttpServletRequest request = request(null);
        MdcCapturingChain first = new MdcCapturingChain();
        filter.doFilter(request, new MockHttpServletResponse(), first);

        MdcCapturingChain second = new MdcCapturingChain();
        filter.doFilter(request, new MockHttpServletResponse(), second);

        assertThat(second.traceId).isEqualTo(first.traceId);
    }

    @Test
    @DisplayName("the filter runs on the async dispatch (SSE), not just the initial request")
    void doesNotSkipAsyncDispatch() {
        assertThat(filter.shouldNotFilterAsyncDispatch()).isFalse();
    }
}
