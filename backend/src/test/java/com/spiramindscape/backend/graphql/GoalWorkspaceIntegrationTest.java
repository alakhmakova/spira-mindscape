package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.goal.GoalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
class GoalWorkspaceIntegrationTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private GoalRepository goalRepository;

    private String goalId;

    @BeforeEach
    void createGoalForTest() {
        goalId = createGoal(
                "Master GraphQL fundamentals including queries and mutations",
                8,
                "A goal created before each test for integration testing",
                "2026-12-31T00:00:00Z"
        );
    }

    @AfterEach
    void cleanDatabase() {
        goalRepository.deleteAll();
    }

    @Test
    @DisplayName("Rejects unknown resource type")
    void rejectsUnknownResourceTypes() {
        // Arrange
        // No Arrange needed - goalId is set in @BeforeEach

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: {
                            type: "contact"
                            name: "Old alias"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error -> error.getMessage().contains("Unknown resource type: contact")));
    }

    @Test
    @DisplayName("Calculates goal and target progress for all target types")
    void calculatesGoalAndTargetProgressForAllTargetTypes() {
        // Arrange
        String numericTargetId = createTarget(goalId, """
                {
                  type: "numeric"
                  title: "Read pages"
                  start: 0
                  total: 10
                  unit: "pages"
                }
                """);
        updateTarget(numericTargetId, "{ current: 5 }");
        String binaryTargetId = createTarget(goalId, """
                {
                  type: "binary"
                  title: "Book session"
                }
                """);
        updateTarget(binaryTargetId, "{ done: true }");
        createTarget(goalId, """
                {
                  type: "checklist"
                  title: "Prepare workspace"
                  items: [
                    { text: "Write requirements", done: true }
                    { text: "Review validation", done: false }
                  ]
                }
                """);

        // Act
        GoalProgress result = graphQlTester.document("""
                        query($goalId: ID!) {
                          goalById(id: $goalId) {
                            progress
                            targets {
                              title
                              type
                              progress
                              items { text done }
                            }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById")
                .entity(GoalProgress.class)
                .get();

        // Assert
        Map<String, TargetProgress> targetsByTitle = result.targets().stream()
                .collect(Collectors.toMap(TargetProgress::title, Function.identity()));

        assertThat(result.progress()).isCloseTo(2d / 3d, offset(0.000001d));
        assertThat(targetsByTitle.get("Read pages").progress()).isEqualTo(0.5d);
        assertThat(targetsByTitle.get("Book session").progress()).isEqualTo(1d);
        assertThat(targetsByTitle.get("Prepare workspace").progress()).isEqualTo(0.5d);
        assertThat(targetsByTitle.get("Prepare workspace").items()).hasSize(2);
    }

    private String createGoal(String title, int confidence, String description, String deadline) {
        return graphQlTester.document("""
                        mutation($title: String!, $confidence: Int!, $description: String!, $deadline: String!) {
                          createGoal(input: {
                            title: $title
                            confidence: $confidence
                            description: $description
                            deadline: $deadline
                          }) {
                            id
                          }
                        }
                        """)
                .variable("title", title)
                .variable("confidence", confidence)
                .variable("description", description)
                .variable("deadline", deadline)
                .execute()
                .path("createGoal.id")
                .entity(String.class)
                .get();
    }

    private String createTarget(String goalId, String input) {
        return graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: INPUT_PLACEHOLDER) {
                            id
                          }
                        }
                        """.replace("INPUT_PLACEHOLDER", input))
                .variable("goalId", goalId)
                .execute()
                .path("createTarget.id")
                .entity(String.class)
                .get();
    }

    private void updateTarget(String targetId, String input) {
        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: INPUT_PLACEHOLDER) {
                            id
                          }
                        }
                        """.replace("INPUT_PLACEHOLDER", input))
                .variable("id", targetId)
                .execute()
                .path("updateTarget.id")
                .entity(String.class)
                .isEqualTo(targetId);
    }

    private record GoalProgress(double progress, java.util.List<TargetProgress> targets) {
    }

    private record TargetProgress(
            String title,
            String type,
            double progress,
            java.util.List<ChecklistItemProgress> items
    ) {
    }

    private record ChecklistItemProgress(String text, boolean done) {
    }
}
