package com.spiramindscape.backend.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    /** Method reference used only to build a {@link MethodParameter} for the test exception. */
    @SuppressWarnings("unused")
    private void dummy() {}

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

    @Test
    @DisplayName("a body-validation failure returns 400 with the readable field message, not a 500")
    void validationBecomes400WithFieldMessage() throws NoSuchMethodException {
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "saveKeyRequest");
        binding.addError(new FieldError(
                "saveKeyRequest", "apiKey", "apiKey must be between 8 and 512 characters"));
        MethodParameter param = new MethodParameter(
                RestExceptionHandlerTest.class.getDeclaredMethod("dummy"), -1);

        ProblemDetail pd = handler.handleValidation(new MethodArgumentNotValidException(param, binding));

        assertThat(pd.getStatus()).isEqualTo(400);
        assertThat(pd.getDetail()).isEqualTo("apiKey must be between 8 and 512 characters");
        // A client validation error must NOT masquerade as an internal 500 with a reference id.
        assertThat(pd.getDetail()).doesNotContain("Reference:");
    }
}
