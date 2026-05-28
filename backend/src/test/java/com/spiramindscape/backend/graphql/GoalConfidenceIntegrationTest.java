package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoalConfidenceIntegrationTest extends BaseGraphQlIntegrationTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 11, -1})
    @DisplayName("Rejects creating goal with invalid confidence")
    void rejectsCreatingGoalWithInvalidConfidence(int invalidConfidence) {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($confidence: Int!) {
                          createGoal(input: { title: "Invalid Confidence", confidence: $confidence }) {
                            id
                          }
                        }
                        """)
                .variable("confidence", invalidConfidence)
                .execute();

        assertValidationErrorContains(response, "confidence");
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

    @Test
    @DisplayName("Rejects updating goal confidence to null")
    void rejectsUpdatingGoalConfidenceToNull() {
        String id = graphQlTester.document("""
                        mutation {
                          createGoal(input: { title: "Valid Goal", confidence: 5 }) {
                            id
                          }
                        }
                        """)
                .execute()
                .path("createGoal.id").entity(String.class).get();

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: { confidence: null }) {
                            id
                          }
                        }
                        """)
                .variable("id", id)
                .execute();

        assertValidationErrorContains(response, "confidence");
    }

    @Test
    @DisplayName("Rejects non-numeric confidence value")
    void rejectsNonNumericConfidence() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation {
                          createGoal(input: { title: "Non-numeric", confidence: "high" }) {
                            id
                          }
                        }
                        """)
                .execute();

        assertValidationErrorContains(response, "int");
    }

    @Test
    @DisplayName("Updating goal with same confidence does not add a new history entry")
    void updatingGoalWithSameConfidenceDoesNotAddHistoryEntry() {
        String id = createGoal(5);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: { confidence: 5 }) {
                            confidence
                            confidenceHistory { confidence }
                          }
                        }
                        """)
                .variable("id", id)
                .execute()
                .path("updateGoal.confidence").entity(Integer.class).isEqualTo(5)
                .path("updateGoal.confidenceHistory").entityList(Object.class).hasSize(1)
                .path("updateGoal.confidenceHistory[0].confidence").entity(Integer.class).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11, -5})
    @DisplayName("Updating goal with invalid confidence returns error and preserves original confidence")
    void updatingGoalWithInvalidConfidencePreservesOriginal(int invalidConfidence) {
        String id = createGoal(5);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $confidence: Int!) {
                          updateGoal(id: $id, input: { confidence: $confidence }) {
                            id
                          }
                        }
                        """)
                .variable("id", id)
                .variable("confidence", invalidConfidence)
                .execute();

        assertValidationErrorContains(response, "confidence");

        graphQlTester.document("""
                        query($id: ID!) {
                          goalById(id: $id) { confidence confidenceHistory { confidence } }
                        }
                        """)
                .variable("id", id)
                .execute()
                .path("goalById.confidence").entity(Integer.class).isEqualTo(5)
                .path("goalById.confidenceHistory").entityList(Object.class).hasSize(1);
    }

    @Test
    @DisplayName("Returns confidence history newest first after multiple updates")
    void returnsConfidenceHistoryNewestFirstAfterMultipleUpdates() throws InterruptedException {
        String id = createGoal(5);

        waitForTimestampAdvance();
        updateGoalConfidence(id, 8);
        waitForTimestampAdvance();
        GraphQlTester.Response response = updateGoalConfidence(id, 3);

        List<Integer> history = response.path("updateGoal.confidenceHistory[*].confidence")
                .entityList(Integer.class)
                .get();
        List<Instant> timestamps = response.path("updateGoal.confidenceHistory[*].at")
                .entityList(String.class)
                .get()
                .stream()
                .map(Instant::parse)
                .toList();

        assertThat(history).containsExactly(3, 8, 5);
        assertThat(timestamps).isSortedAccordingTo(Comparator.reverseOrder());
    }

    private String createGoal(int confidence) {
        return graphQlTester.document("""
                        mutation($confidence: Int!) {
                          createGoal(input: { title: "Confidence Goal", confidence: $confidence }) {
                            id
                          }
                        }
                        """)
                .variable("confidence", confidence)
                .execute()
                .path("createGoal.id").entity(String.class).get();
    }

    private GraphQlTester.Response updateGoalConfidence(String id, int confidence) {
        return graphQlTester.document("""
                        mutation($id: ID!, $confidence: Int!) {
                          updateGoal(id: $id, input: { confidence: $confidence }) {
                            confidenceHistory {
                              confidence
                              at
                            }
                          }
                        }
                        """)
                .variable("id", id)
                .variable("confidence", confidence)
                .execute();
    }

    private static void waitForTimestampAdvance() throws InterruptedException {
        Thread.sleep(50);
    }

    private void assertValidationErrorContains(GraphQlTester.Response response, String messageFragment) {
        String expectedFragment = messageFragment.toLowerCase();
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                isValidationError(error)
                                        && error.getMessage() != null
                                        && error.getMessage().toLowerCase().contains(expectedFragment)));
    }

    private boolean isValidationError(ResponseError error) {
        Object classification = error.getExtensions() == null ? null : error.getExtensions().get("classification");
        return "ValidationError".equals(classification)
                || "ValidationError".equals(error.getErrorType().toString());
    }
}
