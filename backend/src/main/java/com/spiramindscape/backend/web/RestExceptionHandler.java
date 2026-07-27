package com.spiramindscape.backend.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * Bean-validation failures on a request body ({@code @Valid}) are the
     * CLIENT's fault, not a server error — return {@code 400} with the readable
     * field messages (e.g. "apiKey must be between 8 and 512 characters") instead
     * of falling through to the generic 500. Without this, a bad input surfaced as
     * an opaque "Something went wrong. Reference: …" 500, which is meaningless to
     * the user (and to the UI, which then had nothing friendly to show).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(RestExceptionHandler::describeFieldError)
                .distinct()
                .collect(Collectors.joining("; "));
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setDetail(detail.isBlank() ? "Some fields are invalid." : detail);
        return pd;
    }

    private static String describeFieldError(FieldError fe) {
        String msg = fe.getDefaultMessage();
        return (msg != null && !msg.isBlank()) ? msg : fe.getField() + " is invalid";
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
