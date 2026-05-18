package com.spiramindscape.backend.graphql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureHttpGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureHttpGraphQlTester
@ActiveProfiles("test")
class GoalConfidenceIntegrationTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE goal CASCADE");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11, -1})
    @DisplayName("Rejects creating goal with invalid confidence")
    void rejectsCreatingGoalWithInvalidConfidence(int invalidConfidence) {
        graphQlTester.document("""
                        mutation($confidence: Int!) {
                          createGoal(input: { title: "Invalid Confidence", confidence: $confidence }) {
                            id
                          }
                        }
                        """)
                .variable("confidence", invalidConfidence)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10})
    @DisplayName("Accepts creating goal with valid confidence")
    void acceptsCreatingGoalWithValidConfidence(int validConfidence) {
        graphQlTester.document("""
                        mutation($confidence: Int!) {
                          createGoal(input: { title: "Valid Confidence", confidence: $confidence }) {
                            id
                            confidence
                          }
                        }
                        """)
                .variable("confidence", validConfidence)
                .execute()
                .path("createGoal.confidence").entity(Integer.class).isEqualTo(validConfidence);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11, -5})
    @DisplayName("Rejects updating goal with invalid confidence")
    void rejectsUpdatingGoalWithInvalidConfidence(int invalidConfidence) {
        // Create valid goal first
        String id = graphQlTester.document("""
                        mutation {
                          createGoal(input: { title: "Valid Goal", confidence: 5 }) {
                            id
                          }
                        }
                        """)
                .execute()
                .path("createGoal.id").entity(String.class).get();

        // Try to update with invalid confidence
        graphQlTester.document("""
                        mutation($id: ID!, $confidence: Int!) {
                          updateGoal(id: $id, input: { confidence: $confidence }) {
                            id
                          }
                        }
                        """)
                .variable("id", id)
                .variable("confidence", invalidConfidence)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }

    @Test
    @DisplayName("Rejects updating goal confidence to null")
    void rejectsUpdatingGoalConfidenceToNull() {
        // Create valid goal first
        String id = graphQlTester.document("""
                        mutation {
                          createGoal(input: { title: "Valid Goal", confidence: 5 }) {
                            id
                          }
                        }
                        """)
                .execute()
                .path("createGoal.id").entity(String.class).get();

        // Try to update with null confidence. 
        // Note: in UpdateGoalInput, confidence is Integer, so it can be null.
        // But Goal entity has @NotNull on confidence_rating.
        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: { confidence: null }) {
                            id
                          }
                        }
                        """)
                .variable("id", id)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }

    @Test
    @DisplayName("Rejects non-numeric confidence value")
    void rejectsNonNumericConfidence() {
        // GraphQL should reject this before even reaching the service if we use variables.
        // But if we put it in the document, it should also fail.
        graphQlTester.document("""
                        mutation {
                          createGoal(input: { title: "Non-numeric", confidence: "high" }) {
                            id
                          }
                        }
                        """)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }
}
