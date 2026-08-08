package com.spiramindscape.backend.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stamps every log line of a request with the same {@code traceId}.
 *
 * <p>Before this filter, an error produced exactly one line carrying a correlation id
 * (minted inside {@code RestExceptionHandler} at the moment it caught the exception),
 * so the reference a user quoted could not be used to find anything else that happened
 * during their request. Putting the id in the MDC at the very start means every line —
 * including the ones logged before the failure — carries it, and
 * {@code GoogleCloudStructuredLogFormatter} lifts it to a top-level JSON key so
 * {@code jsonPayload.traceId="…"} is a Logs Explorer query.
 *
 * <p>Cloud Run puts a trace id on every inbound request in {@code X-Cloud-Trace-Context};
 * reusing it (rather than generating our own) is what lets our application logs sit
 * alongside the platform's request log for the same call.
 *
 * <p>Registered outside the Spring Security chain (see {@code LoggingConfig}) so 401s,
 * 403s, CSRF rejections and rate-limit 429s — the lines you most want to correlate —
 * are covered too.
 */
public class RequestLogContextFilter extends OncePerRequestFilter {

    /** MDC key for the request correlation id. */
    public static final String TRACE_ID = "traceId";

    /** MDC key for the authenticated user; set by {@code CurrentUserProvider}, cleared here. */
    public static final String USER_ID = "userId";

    static final String CLOUD_TRACE_HEADER = "X-Cloud-Trace-Context";

    /** Echoed back so a user (or the SPA) can quote the reference without hitting an error. */
    static final String RESPONSE_HEADER = "X-Trace-Id";

    /** Reused across the async dispatch so a streamed response keeps one id. */
    private static final String REQUEST_ATTRIBUTE = RequestLogContextFilter.class.getName() + ".traceId";

    private static final Pattern TRACE_ID_FORMAT = Pattern.compile("[0-9a-fA-F]{32}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        request.setAttribute(REQUEST_ATTRIBUTE, traceId);
        MDC.put(TRACE_ID, traceId);
        if (!response.isCommitted()) {
            response.setHeader(RESPONSE_HEADER, traceId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Tomcat pools threads: without this, the next request served by this thread
            // would inherit the previous request's trace id AND user id, quietly attributing
            // one user's log lines to another. The finally is the point of the whole block.
            MDC.remove(TRACE_ID);
            MDC.remove(USER_ID);
        }
    }

    /**
     * The AI chat streams over an SseEmitter, which re-enters the chain on the ASYNC
     * dispatch. Without this the stream's log lines would lose the id (or get a new one).
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private static String resolveTraceId(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ATTRIBUTE);
        if (existing instanceof String cached) {
            return cached;
        }
        String fromCloudRun = parseCloudTraceHeader(request.getHeader(CLOUD_TRACE_HEADER));
        return fromCloudRun != null ? fromCloudRun : UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * {@code X-Cloud-Trace-Context: TRACE_ID/SPAN_ID;o=TRACE_TRUE}. Only the trace id is
     * wanted, and only if it is well-formed — a malformed header must not be propagated
     * into the Cloud Trace link, so anything unexpected falls back to a generated id.
     */
    private static String parseCloudTraceHeader(String header) {
        if (!StringUtils.hasText(header)) {
            return null;
        }
        int slash = header.indexOf('/');
        String candidate = (slash >= 0) ? header.substring(0, slash) : header;
        candidate = candidate.trim();
        return TRACE_ID_FORMAT.matcher(candidate).matches() ? candidate.toLowerCase() : null;
    }
}
