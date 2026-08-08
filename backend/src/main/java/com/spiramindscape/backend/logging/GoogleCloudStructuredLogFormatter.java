package com.spiramindscape.backend.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Emits one JSON object per log line in the shape Google Cloud Logging understands.
 *
 * <p>Why this exists: Cloud Run ships container stdout straight to Cloud Logging,
 * but Logback's default output is plain text. Cloud Logging cannot find a severity
 * in plain text, so <em>every</em> line — including {@code log.error} — ingests as
 * {@code DEFAULT}, and a multi-line stack trace splits into one entry per line.
 * That silently breaks log-based error alerting.
 *
 * <p>Spring Boot 3.5 ships structured formatters for ECS, GELF and Logstash but
 * <strong>not</strong> for GCP — so there is no {@code logging.structured.format.console=gcp}
 * to set. The property does, however, accept a fully-qualified class name, which is
 * how this class is wired (see {@code application.properties}). Boot's
 * {@code StructuredLogFormatterFactory} instantiates it by name and injects the
 * constructor arguments it knows about, {@link Environment} among them.
 *
 * <p>Two details carry most of the value and are easy to break:
 * <ul>
 *   <li><b>{@code WARN} must be written as {@code "WARNING"}</b> — Cloud Logging does
 *       not recognise {@code WARN} and quietly downgrades the entry to {@code DEFAULT}.</li>
 *   <li><b>The stack trace goes inside {@code message}</b>, not a sibling field. That
 *       is what keeps it in a single entry and what lets Cloud Error Reporting group
 *       occurrences of the same exception.</li>
 * </ul>
 *
 * @see <a href="https://cloud.google.com/logging/docs/structured-logging">Cloud Logging structured logging</a>
 */
public class GoogleCloudStructuredLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

    /** MDC key holding the per-request correlation id (see {@code RequestLogContextFilter}). */
    static final String TRACE_ID = "traceId";

    private final ThrowableProxyConverter throwableConverter = new ThrowableProxyConverter();

    private final JsonWriter<ILoggingEvent> writer;

    public GoogleCloudStructuredLogFormatter(Environment environment) {
        this.throwableConverter.setOptionList(List.of("full"));
        this.throwableConverter.start();
        String projectId = environment.getProperty("spira.gcp.project-id", "");
        this.writer = JsonWriter.<ILoggingEvent>of(members -> {
            members.add("severity", event -> severityOf(event.getLevel()));
            members.add("time", event -> event.getInstant().toString());
            members.add("message", this::messageWithStackTrace);
            members.add("logger", ILoggingEvent::getLoggerName);
            members.add("thread", ILoggingEvent::getThreadName);
            // Lifts traceId/userId to top-level keys so they are queryable in
            // Logs Explorer as jsonPayload.traceId rather than buried in a nested map.
            members.addMapEntries(ILoggingEvent::getMDCPropertyMap);
            // Joins the line to Cloud Trace. Only emitted once the project id is
            // known (GOOGLE_CLOUD_PROJECT), so local runs stay clean.
            members.add("logging.googleapis.com/trace",
                            event -> traceResource(projectId, event.getMDCPropertyMap().get(TRACE_ID)))
                    .whenHasLength();
        }).withNewLineAtEnd();
    }

    @Override
    public String format(ILoggingEvent event) {
        return this.writer.writeToString(event);
    }

    /**
     * Cloud Logging's LogSeverity enum spells the warning level {@code WARNING} and has
     * no {@code TRACE}; anything it does not recognise falls back to {@code DEFAULT}.
     */
    private static String severityOf(Level level) {
        if (level == null) {
            return "DEFAULT";
        }
        return switch (level.toInt()) {
            case Level.ERROR_INT -> "ERROR";
            case Level.WARN_INT -> "WARNING";
            case Level.INFO_INT -> "INFO";
            case Level.DEBUG_INT, Level.TRACE_INT -> "DEBUG";
            default -> "DEFAULT";
        };
    }

    /** The message, plus the stack trace appended so the whole error stays in one entry. */
    private String messageWithStackTrace(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (event.getThrowableProxy() == null) {
            return message;
        }
        return message + "\n" + this.throwableConverter.convert(event);
    }

    private static String traceResource(String projectId, String traceId) {
        if (!StringUtils.hasLength(projectId) || !StringUtils.hasLength(traceId)) {
            return "";
        }
        return "projects/" + projectId + "/traces/" + traceId;
    }
}
