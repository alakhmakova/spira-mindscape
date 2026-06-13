package com.spiramindscape.backend.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Clean, safe error handling for REST/MVC endpoints (OWASP A10 — mishandling
 * exceptional conditions). Without this, an unexpected error renders Spring's
 * Whitelabel error page ("no explicit mapping for /error") — which is exactly
 * what surfaced during the session-serialization 500.
 *
 * <p>Returns RFC-7807 {@link ProblemDetail} JSON with NO internals (no stack
 * trace, no exception class), plus a correlation id the user can quote to
 * support; the full error is logged server-side against that id.
 *
 * <p>GraphQL has its own resolver ({@code GraphQlExceptionHandler}); this advice
 * applies only to MVC controllers, so the two don't collide.
 */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    /** Intentional HTTP errors (e.g. 422 no-key, 404) — preserve status, safe reason. */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleStatus(ResponseStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(ex.getStatusCode());
        pd.setDetail(ex.getReason() != null ? ex.getReason() : "Request could not be completed.");
        return pd;
    }

    /** Anything unexpected → 500 with a correlation id, never leaking internals. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unhandled REST exception [{}] on {} {}", correlationId,
                request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setDetail("Something went wrong. Please try again. Reference: " + correlationId);
        pd.setProperty("correlationId", correlationId);
        return pd;
    }
}
