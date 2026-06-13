package com.spiramindscape.backend.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    @DisplayName("a ResponseStatusException keeps its status and safe reason")
    void preservesStatus() {
        ProblemDetail pd = handler.handleStatus(
                new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No API key configured"));
        assertThat(pd.getStatus()).isEqualTo(422);
        assertThat(pd.getDetail()).isEqualTo("No API key configured");
    }

    @Test
    @DisplayName("an unexpected exception returns 500 with a correlation id and NO internals")
    void hidesInternals() {
        ProblemDetail pd = handler.handleUnexpected(
                new RuntimeException("NullPointerException at SomeSecretClass:42"),
                new MockHttpServletRequest("POST", "/api/ai/keys"));
        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getProperties()).containsKey("correlationId");
        // The raw exception message / class must never reach the client.
        assertThat(pd.getDetail()).doesNotContain("NullPointer").doesNotContain("SecretClass");
        assertThat(pd.getDetail()).contains("Reference:");
    }
}
