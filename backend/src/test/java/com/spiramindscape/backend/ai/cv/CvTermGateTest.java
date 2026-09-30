package com.spiramindscape.backend.ai.cv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CvTermGateTest {

    /** Roughly what the owner's repository and profile actually contain. */
    private static final String CORPUS = """
            <h2>Projects</h2><p>Spira — Spring Boot, GraphQL, PostgreSQL, React</p>
            spring-boot-starter-oauth2-client spring-boot-starter-graphql flyway-core
            react vitest @playwright/test apollo-runtime
            """;

    @Test
    @DisplayName("the technologies invented in the owner's profile are flagged; the ones she used are not")
    void inventedTermsAreFlagged() {
        String proposed = "<p>Secured the API with JWT authentication, built the UI with Apollo Client, "
                + "tested with Testcontainers and Playwright, on Spring Boot and PostgreSQL.</p>";

        assertThat(CvTermGate.unsupportedTerms(proposed, CORPUS))
                .contains("JWT", "Apollo Client", "Testcontainers")
                .doesNotContain("Spring Boot", "PostgreSQL", "Playwright", "Spring", "Apollo");
    }

    @Test
    @DisplayName("matching is on whole words and ignores case and dashes")
    void wordBoundaries() {
        assertThat(CvTermGate.unsupportedTerms("Worked on GitHub pages", "")).doesNotContain("Git");
        assertThat(CvTermGate.unsupportedTerms("spring boot services", "Spring-Boot-Starter")).isEmpty();
        assertThat(CvTermGate.unsupportedTerms("", CORPUS)).isEmpty();
    }

    @Test
    @DisplayName("the warning is addressed to the user, in their language")
    void warning() {
        assertThat(CvTermGate.warning(java.util.List.of("JWT"), "ru")).contains("JWT").contains("профиле");
        assertThat(CvTermGate.warning(java.util.List.of("JWT", "Kafka"), "en")).contains("JWT, Kafka");
        assertThat(CvTermGate.warning(java.util.List.of(), "en")).isEmpty();
    }
}
