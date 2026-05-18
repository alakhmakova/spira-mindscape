package com.spiramindscape.backend.graphql;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

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
        if (ex instanceof BindException bindException && hasInvalidDeadline(bindException)) {
            return invalidDateFormatError(env);
        }

        if (ex instanceof DateTimeParseException
                || ex.getCause() instanceof DateTimeParseException) {
            return invalidDateFormatError(env);
        }
        log.error("Unhandled GraphQL exception type: {}, cause: {}",
                ex.getClass().getName(),
                ex.getCause() != null ? ex.getCause().getClass().getName() : "none");
        return null;
    }

    private boolean hasInvalidDeadline(BindException bindException) {
        return bindException.getFieldErrors().stream()
                .anyMatch(this::isInvalidDeadline);
    }

    private boolean isInvalidDeadline(FieldError fieldError) {
        return fieldError.getField().endsWith(".deadline")
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
