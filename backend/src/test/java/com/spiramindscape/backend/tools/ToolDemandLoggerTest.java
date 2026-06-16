package com.spiramindscape.backend.tools;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demand logger turns rejected AI tool schemas into a signal of which
 * catalog primitives are missing — without leaking user content.
 */
class ToolDemandLoggerTest {

    private final ToolDemandLogger logger = new ToolDemandLogger(new ObjectMapper());
    private Logger slf4jLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void capture() {
        slf4jLogger = (Logger) LoggerFactory.getLogger("tools.demand");
        appender = new ListAppender<>();
        appender.start();
        slf4jLogger.addAppender(appender);
        slf4jLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void detach() {
        slf4jLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("an unsupported primitive in a rejected schema is logged by name")
    void logsUnsupportedPrimitive() {
        String schema = "{\"layout\":\"table\",\"columns\":["
                + "{\"key\":\"a\",\"primitive\":\"text\"},"
                + "{\"key\":\"loc\",\"primitive\":\"map\"}]}"; // 'map' not in the catalog
        logger.recordRejectedSchema(schema, "Trip planner", "Unsupported field type 'map'.");

        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getFormattedMessage()).contains("tool_unmet_demand");
            assertThat(e.getFormattedMessage()).contains("kind=primitive");
            assertThat(e.getFormattedMessage()).contains("map");
            assertThat(e.getFormattedMessage()).contains("Trip planner");
        });
    }

    @Test
    @DisplayName("approved primitives are not logged as demand")
    void doesNotLogApproved() {
        String schema = "{\"layout\":\"table\",\"columns\":[{\"key\":\"a\",\"primitive\":\"text\"}]}";
        logger.recordRejectedSchema(schema, "Fine tool", "some reason");
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("an unparseable schema is still recorded, without raw content")
    void logsUnparseable() {
        logger.recordRejectedSchema("not json", "Weird tool", "bad");
        assertThat(appender.list).anySatisfy(e ->
                assertThat(e.getFormattedMessage()).contains("unparseable_schema"));
    }
}
