package com.spiramindscape.backend.graphql;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.spiramindscape.backend.logging.RequestLogContextFilter;
import graphql.GraphQLError;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ResultPath;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GraphQlExceptionHandlerTest {

    private final GraphQlExceptionHandler handler = new GraphQlExceptionHandler();

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void captureLogs() {
        // A ListAppender collects log events in memory so the test can assert on what was
        // logged, not just on what was returned. It is the only way to prove a stack trace
        // was actually recorded.
        appender = new ListAppender<>();
        appender.start();
        logger = (Logger) LoggerFactory.getLogger(GraphQlExceptionHandler.class);
        logger.addAppender(appender);
    }

    @AfterEach
    void releaseLogs() {
        // Detaching matters: a leaked appender keeps collecting events from other tests.
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    private static DataFetchingEnvironment environment() {
        DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
        Field field = Field.newField("updateGoal").build();
        ExecutionStepInfo stepInfo = mock(ExecutionStepInfo.class);
        when(env.getField()).thenReturn(field);
        when(env.getExecutionStepInfo()).thenReturn(stepInfo);
        when(stepInfo.getPath()).thenReturn(ResultPath.rootPath().segment("updateGoal"));
        return env;
    }

    @Test
    @DisplayName("'not found' still classifies as NOT_FOUND and is not treated as a server error")
    void keepsNotFoundClassification() {
        GraphQLError error = handler.resolveToSingleError(
                new IllegalArgumentException("Goal not found"), environment());

        assertThat(error.getMessage()).isEqualTo("Goal not found");
        assertThat(error.getErrorType().toString()).isEqualTo("NOT_FOUND");
        // An expected client error must not be logged as a server fault.
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("a validation error keeps its message and logs nothing")
    void keepsValidationClassification() {
        GraphQLError error = handler.resolveToSingleError(
                new IllegalArgumentException("Title must not be blank"), environment());

        assertThat(error.getErrorType()).isEqualTo(graphql.ErrorType.ValidationError);
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("an unexpected exception is logged WITH its stack trace")
    void logsTheThrowable() {
        // The defect this guards: the handler used to log only ex.getClass().getName(),
        // so a production NullPointerException produced one line naming the class and
        // nothing about where it came from.
        handler.resolveToSingleError(new RuntimeException("secret internals"), environment());

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getClassName()).isEqualTo("java.lang.RuntimeException");
        // The failing field is what makes the line actionable.
        assertThat(event.getFormattedMessage()).contains("updateGoal");
    }

    @Test
    @DisplayName("the client gets a reference matching the request's trace id, and no internals")
    void returnsSanitizedErrorWithCorrelationId() {
        MDC.put(RequestLogContextFilter.TRACE_ID, "abc123");

        GraphQLError error = handler.resolveToSingleError(
                new RuntimeException("secret internals"), environment());

        assertThat(error.getExtensions()).containsEntry("correlationId", "abc123");
        assertThat(error.getMessage()).contains("Reference: abc123");
        // The exception message must never reach the client (docs/security-model.md §9).
        assertThat(error.getMessage()).doesNotContain("secret internals");
        // Same id on the log line, so the reference the user quotes actually finds it.
        assertThat(appender.list.get(0).getFormattedMessage()).contains("abc123");
    }

    @Test
    @DisplayName("without a trace id in the MDC a reference is still issued")
    void fallsBackToAGeneratedReference() {
        GraphQLError error = handler.resolveToSingleError(new RuntimeException("boom"), environment());

        assertThat(error.getExtensions().get("correlationId")).asString().isNotBlank();
        assertThat(error.getMessage()).contains("Reference:");
    }
}
