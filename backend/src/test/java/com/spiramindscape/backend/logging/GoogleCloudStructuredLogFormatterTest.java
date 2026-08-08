package com.spiramindscape.backend.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.boot.logging.structured.StructuredLogFormatterFactory;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The formatter is what makes Cloud Logging able to read our logs at all, and every
 * assertion here guards a failure mode that is invisible at runtime: a wrong severity
 * string doesn't throw, it just silently downgrades the entry.
 */
class GoogleCloudStructuredLogFormatterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @AfterEach
    void clearMdc() {
        // MDC is thread-local and JUnit reuses threads — a leak here would show up as a
        // baffling failure in an unrelated test.
        MDC.clear();
    }

    private static GoogleCloudStructuredLogFormatter formatter(String projectId) {
        MockEnvironment environment = new MockEnvironment();
        if (projectId != null) {
            environment.setProperty("spira.gcp.project-id", projectId);
        }
        return new GoogleCloudStructuredLogFormatter(environment);
    }

    private static ILoggingEvent event(Level level, String message, Throwable throwable) {
        Logger logger = (Logger) LoggerFactory.getLogger("com.spiramindscape.test");
        return new LoggingEvent("com.spiramindscape.test", logger, level, message, throwable, null);
    }

    private JsonNode format(Level level, String message, Throwable throwable) throws Exception {
        return JSON.readTree(formatter(null).format(event(level, message, throwable)));
    }

    @ParameterizedTest(name = "{0} is written as {1}")
    @CsvSource({"ERROR,ERROR", "WARN,WARNING", "INFO,INFO", "DEBUG,DEBUG", "TRACE,DEBUG"})
    @DisplayName("levels map onto Cloud Logging's LogSeverity names")
    void mapsSeverity(String logbackLevel, String expected) throws Exception {
        JsonNode json = format(Level.toLevel(logbackLevel), "hello", null);
        assertThat(json.get("severity").asText()).isEqualTo(expected);
    }

    @Test
    @DisplayName("WARN is written as WARNING — 'WARN' would silently ingest as DEFAULT")
    void warnIsSpelledWarning() throws Exception {
        // Called out separately from the table above because it is the whole reason this
        // class exists: Cloud Logging does not recognise "WARN", so every log.warn would
        // be invisible to a severity>=WARNING alert.
        assertThat(format(Level.WARN, "careful", null).get("severity").asText()).isEqualTo("WARNING");
    }

    @Test
    @DisplayName("the basic fields are present and the message is the formatted message")
    void writesBasicFields() throws Exception {
        JsonNode json = format(Level.INFO, "goal saved", null);
        assertThat(json.get("message").asText()).isEqualTo("goal saved");
        assertThat(json.get("logger").asText()).isEqualTo("com.spiramindscape.test");
        assertThat(json.get("time").asText()).isNotBlank();
        assertThat(json.hasNonNull("thread")).isTrue();
    }

    @Test
    @DisplayName("a stack trace goes INSIDE message, so Error Reporting can group it")
    void appendsStackTraceToMessage() throws Exception {
        JsonNode json = format(Level.ERROR, "boom", new IllegalStateException("bad state"));
        String message = json.get("message").asText();
        assertThat(message).startsWith("boom");
        assertThat(message).contains("java.lang.IllegalStateException: bad state");
        assertThat(message).contains("\tat ");
    }

    @Test
    @DisplayName("an event with a stack trace is still ONE line — no split entries")
    void emitsExactlyOneLine() {
        // The stack trace's own newlines live inside a JSON string, so the only raw
        // newline in the output must be the terminator. If this breaks, Cloud Logging
        // turns one exception into twenty separate entries again.
        String output = formatter(null).format(
                event(Level.ERROR, "boom", new IllegalStateException("bad state")));
        assertThat(output).endsWith("\n");
        assertThat(output.chars().filter(c -> c == '\n').count()).isEqualTo(1);
    }

    @Test
    @DisplayName("MDC values become top-level keys so they are queryable")
    void liftsMdcToTopLevel() throws Exception {
        MDC.put("traceId", "abc123");
        MDC.put("userId", "42");
        JsonNode json = format(Level.INFO, "hello", null);
        assertThat(json.get("traceId").asText()).isEqualTo("abc123");
        assertThat(json.get("userId").asText()).isEqualTo("42");
    }

    @Test
    @DisplayName("the Cloud Trace link is emitted when the project id is configured")
    void writesTraceResourceWhenProjectIdKnown() throws Exception {
        MDC.put("traceId", "abc123");
        JsonNode json = JSON.readTree(formatter("my-project").format(event(Level.INFO, "hello", null)));
        assertThat(json.get("logging.googleapis.com/trace").asText())
                .isEqualTo("projects/my-project/traces/abc123");
    }

    @Test
    @DisplayName("no project id (local runs) omits the trace link rather than writing a broken one")
    void omitsTraceResourceWithoutProjectId() throws Exception {
        MDC.put("traceId", "abc123");
        assertThat(format(Level.INFO, "hello", null).has("logging.googleapis.com/trace")).isFalse();
    }

    @Test
    @DisplayName("no traceId in MDC omits the trace link")
    void omitsTraceResourceWithoutTraceId() throws Exception {
        JsonNode json = JSON.readTree(formatter("my-project").format(event(Level.INFO, "hello", null)));
        assertThat(json.has("logging.googleapis.com/trace")).isFalse();
    }

    @Test
    @DisplayName("Boot can instantiate the formatter from the class name in application.properties")
    void isConstructibleByClassName() {
        // The deploy risk this guards: logging.structured.format.console holds a class
        // NAME, resolved reflectively at startup by the same factory used here. A
        // constructor signature Boot cannot satisfy fails the container on boot, long
        // after any test would have caught it. Renaming or moving this class breaks the
        // property too — this test fails loudly if that happens.
        StructuredLogFormatterFactory<ILoggingEvent> factory = new StructuredLogFormatterFactory<>(
                ILoggingEvent.class, new MockEnvironment(), null, commonFormatters -> {});

        StructuredLogFormatter<ILoggingEvent> formatter =
                factory.get(GoogleCloudStructuredLogFormatter.class.getName());

        assertThat(formatter).isInstanceOf(GoogleCloudStructuredLogFormatter.class);
        assertThat(formatter.format(event(Level.WARN, "hello", null))).contains("\"severity\":\"WARNING\"");
    }
}
