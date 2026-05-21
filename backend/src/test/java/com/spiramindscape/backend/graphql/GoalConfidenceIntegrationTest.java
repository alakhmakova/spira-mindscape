package com.spiramindscape.backend.graphql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
class GoalConfidenceIntegrationTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM confidence_history");
        jdbcTemplate.execute("DELETE FROM goal");
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

    @Test
    @DisplayName("Returns confidence history newest first after multiple updates")
    void returnsConfidenceHistoryNewestFirstAfterMultipleUpdates() throws InterruptedException {
        String id = createGoal(5);

        Thread.sleep(10);
        updateGoalConfidence(id, 8);
        Thread.sleep(10);
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

    @Test
    @DisplayName("Deletes confidence history rows when a goal is deleted")
    void deletesConfidenceHistoryRowsWhenGoalIsDeleted() {
        String id = createGoal(5);
        updateGoalConfidence(id, 8);

        Long goalId = Long.valueOf(id);
        assertThat(countConfidenceHistory(goalId)).isEqualTo(2);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          deleteGoal(id: $id)
                        }
                        """)
                .variable("id", id)
                .execute()
                .path("deleteGoal").entity(Boolean.class).isEqualTo(true);

        assertThat(countConfidenceHistory(goalId)).isZero();
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

    private int countConfidenceHistory(Long goalId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM confidence_history WHERE goal_id = ?",
                Integer.class,
                goalId
        );
    }
}
