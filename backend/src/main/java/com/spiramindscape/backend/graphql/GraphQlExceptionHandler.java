package com.spiramindscape.backend.graphql;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import com.spiramindscape.backend.logging.RequestLogContextFilter;
import graphql.schema.DataFetchingEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class GraphQlExceptionHandler extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof IllegalArgumentException) {
            String message = ex.getMessage() != null ? ex.getMessage() : "Invalid argument";
            ErrorClassification classification = message.toLowerCase(Locale.ROOT).contains("not found")
                    ? SpiraErrorType.NOT_FOUND
                    : ErrorType.ValidationError;
            return GraphQLError.newError()
                    .message(message)
                    .errorType(classification)
                    .extensions(Map.of("classification", classification.toString()))
                    .location(env.getField().getSourceLocation())
                    .path(env.getExecutionStepInfo().getPath())
                    .build();
        }
        if (ex instanceof BindException bindException && hasInvalidDateField(bindException)) {
            return invalidDateFormatError(env);
        }

        if (ex instanceof DateTimeParseException
                || ex.getCause() instanceof DateTimeParseException) {
            return invalidDateFormatError(env);
        }
        // Everything below is unexpected. Log the exception ITSELF (trailing argument, so
        // SLF4J records the stack trace) together with the field and path that failed —
        // this used to log only the exception's class name, which told you a
        // NullPointerException happened but nothing whatsoever about where.
        String reference = currentTraceId();
        log.error("Unhandled GraphQL exception on field '{}' path={} [{}]",
                env.getField().getName(), env.getExecutionStepInfo().getPath(), reference, ex);
        // Return a sanitized error carrying the same reference the REST handler gives out,
        // instead of returning null and letting Spring's default resolver stamp it with an
        // execution id that appears in no other log line.
        return GraphQLError.newError()
                .message("Something went wrong. Please try again. Reference: " + reference)
                // Spring GraphQL's ErrorType, not graphql-java's — the latter has no
                // INTERNAL_ERROR, and this is the classification its default resolver
                // would have used, so clients see no change in shape.
                .errorType(org.springframework.graphql.execution.ErrorType.INTERNAL_ERROR)
                .extensions(Map.of(
                        "classification",
                        org.springframework.graphql.execution.ErrorType.INTERNAL_ERROR.toString(),
                        "correlationId", reference))
                .location(env.getField().getSourceLocation())
                .path(env.getExecutionStepInfo().getPath())
                .build();
    }

    /**
     * The request's trace id, so the reference the user quotes matches every log line of
     * their request. Falls back to a fresh id when there is no MDC (e.g. a unit test).
     *
     * @see com.spiramindscape.backend.logging.RequestLogContextFilter
     */
    private static String currentTraceId() {
        String traceId = MDC.get(RequestLogContextFilter.TRACE_ID);
        return (traceId != null && !traceId.isBlank()) ? traceId : UUID.randomUUID().toString();
    }

    private boolean hasInvalidDateField(BindException bindException) {
        return bindException.getFieldErrors().stream()
                .anyMatch(this::isInvalidDateField);
    }

    private boolean isInvalidDateField(FieldError fieldError) {
        String field = fieldError.getField();
        return (field.endsWith(".deadline") || field.endsWith(".achievedAt"))
                && fieldError.getCodes() != null
                && java.util.Arrays.asList(fieldError.getCodes()).contains("typeMismatch");
    }

    private GraphQLError invalidDateFormatError(DataFetchingEnvironment env) {
        return GraphQLError.newError()
                .message("Invalid date format. Expected ISO-8601, for example: 2026-12-31T00:00:00Z")
                .errorType(ErrorType.ValidationError)
                .extensions(Map.of("classification", ErrorType.ValidationError.toString()))
                .location(env.getField().getSourceLocation())
                .path(env.getExecutionStepInfo().getPath())
                .build();
    }

    private enum SpiraErrorType implements ErrorClassification {
        NOT_FOUND
    }
}
