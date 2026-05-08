package com.spiramindscape.backend.graphql;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

import java.util.Locale;

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
                    .location(env.getField().getSourceLocation())
                    .path(env.getExecutionStepInfo().getPath())
                    .build();
        }
        return null;
    }

    private enum SpiraErrorType implements ErrorClassification {
        NOT_FOUND
    }
}
