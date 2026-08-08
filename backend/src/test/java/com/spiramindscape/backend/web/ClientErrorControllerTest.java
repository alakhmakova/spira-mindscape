package com.spiramindscape.backend.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc — no Spring context, so this runs in milliseconds. It exercises the
 * controller together with the two pieces that actually enforce the contract: bean
 * validation (the size/pattern caps) and {@link RestExceptionHandler} (which turns a
 * validation failure into a 400 rather than a 500).
 */
class ClientErrorControllerTest {

    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ClientErrorController())
                .setValidator(new LocalValidatorFactoryBean() {{ afterPropertiesSet(); }})
                .setControllerAdvice(new RestExceptionHandler())
                .build();

        appender = new ListAppender<>();
        appender.start();
        logger = (Logger) LoggerFactory.getLogger("client.web-error");
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private org.springframework.test.web.servlet.ResultActions postJson(String json) throws Exception {
        return mockMvc.perform(post("/api/client-errors")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Mozilla/5.0 (Test)")
                .content(json));
    }

    private static String repeat(char c, int times) {
        return String.valueOf(c).repeat(times);
    }

    @Test
    @DisplayName("a valid report is accepted and produces one WARN line")
    void acceptsValidReport() throws Exception {
        postJson("""
                {"kind":"render","name":"TypeError","message":"x is not a function",
                 "stack":"at Foo (app.js:1:2)","url":"https://spira.app/goals","appVersion":"1.2.3"}
                """).andExpect(status().isNoContent());

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("web_client_error")
                .contains("kind=render")
                .contains("x is not a function")
                .contains("at Foo (app.js:1:2)");
    }

    @Test
    @DisplayName("an anonymous reporter is logged as anonymous, not rejected")
    void logsAnonymousUser() throws Exception {
        // Errors from logged-out users (login page, expired session) are exactly the ones
        // we could not see before, so they must be accepted, not 401'd.
        postJson("""
                {"kind":"window-error","message":"boom"}
                """).andExpect(status().isNoContent());

        assertThat(appender.list.get(0).getFormattedMessage()).contains("userId=anonymous");
    }

    @Test
    @DisplayName("an over-long message is rejected with 400, not silently truncated")
    void rejectsOversizedMessage() throws Exception {
        postJson("""
                {"kind":"render","message":"%s"}
                """.formatted(repeat('x', 400))).andExpect(status().isBadRequest());

        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("an over-long stack is rejected with 400")
    void rejectsOversizedStack() throws Exception {
        postJson("""
                {"kind":"render","message":"boom","stack":"%s"}
                """.formatted(repeat('y', 5000))).andExpect(status().isBadRequest());

        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("an unrecognised kind is rejected — the enum is the whitelist")
    void rejectsUnknownKind() throws Exception {
        postJson("""
                {"kind":"whatever","message":"boom"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a missing message is rejected")
    void rejectsMissingMessage() throws Exception {
        postJson("""
                {"kind":"render"}
                """).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the crash-trail kind is accepted (attachment-crash diagnostics)")
    void acceptsCrashTrail() throws Exception {
        // specs/2026-08-03-attachment-crash-diagnostics/ reports its breadcrumb trail
        // through this endpoint; the trail rides in `stack` as step names and timings.
        postJson("""
                {"kind":"crash-trail","message":"tab died during attach",
                 "stack":"picker-open t=0\\nhidden t=120\\n(no further entries)"}
                """).andExpect(status().isNoContent());

        assertThat(appender.list.get(0).getFormattedMessage()).contains("kind=crash-trail");
    }

    @Test
    @DisplayName("arbitrary extra fields cannot smuggle data into the log")
    void ignoresUnknownFields() throws Exception {
        // The fixed-field record is the PII control: there is no Map<String,Object> and no
        // @JsonAnySetter, so whatever else a client sends simply does not exist server-side.
        postJson("""
                {"kind":"render","message":"boom","password":"hunter2",
                 "goalText":"my private goal"}
                """).andExpect(status().isNoContent());

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .doesNotContain("hunter2")
                .doesNotContain("my private goal");
    }

    @Test
    @DisplayName("a newline in the User-Agent cannot break the key=value line apart")
    void sanitizesUserAgent() throws Exception {
        mockMvc.perform(post("/api/client-errors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Evil\nweb_client_error kind=forged")
                        .content("""
                                {"kind":"render","message":"boom"}
                                """))
                .andExpect(status().isNoContent());

        String line = appender.list.get(0).getFormattedMessage();
        // The header must not be able to forge a second event on its own line.
        assertThat(line.substring(0, line.indexOf('\n'))).doesNotContain("\n");
        assertThat(line).contains("Evil web_client_error kind=forged");
    }
}
