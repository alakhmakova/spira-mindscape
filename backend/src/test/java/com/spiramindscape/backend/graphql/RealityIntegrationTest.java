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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
class RealityIntegrationTest {

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

    // ─── actions ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Adds action to reality and persists it")
    void addsActionToReality() {
        // Arrange
        String text = "Mapped the current model";

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: $text) {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", text)
                .execute();

        // Assert
        response
                .path("addRealityItem.actions").entityList(Object.class).hasSize(1)
                .path("addRealityItem.actions[0].id").hasValue()
                .path("addRealityItem.actions[0].text").entity(String.class).isEqualTo(text);
    }

    @Test
    @DisplayName("Allows multiple actions to be added to reality")
    void allowsMultipleActionsToBeAdded() {
        // Arrange
        String firstText = "Mapped the current model";
        String secondText = "Reviewed existing tests";

        // Act
        graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: $text) {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", firstText)
                .execute();

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: $text) {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", secondText)
                .execute();

        // Assert
        response
                .path("addRealityItem.actions").entityList(Object.class).hasSize(2)
                .path("addRealityItem.actions[0].text").entity(String.class).isEqualTo(firstText)
                .path("addRealityItem.actions[1].text").entity(String.class).isEqualTo(secondText);
    }

    // ─── obstacles ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Adds obstacle to reality and persists it")
    void addsObstacleToReality() {
        // Arrange
        String text = "Missing automated coverage";

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "obstacles", text: $text) {
                            obstacles { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", text)
                .execute();

        // Assert
        response
                .path("addRealityItem.obstacles").entityList(Object.class).hasSize(1)
                .path("addRealityItem.obstacles[0].id").hasValue()
                .path("addRealityItem.obstacles[0].text").entity(String.class).isEqualTo(text);
    }

    @Test
    @DisplayName("Allows multiple obstacles to be added to reality")
    void allowsMultipleObstaclesToBeAdded() {
        // Arrange
        String firstText = "Missing automated coverage";
        String secondText = "Unclear requirements";

        // Act
        graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "obstacles", text: $text) {
                            obstacles { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", firstText)
                .execute();

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "obstacles", text: $text) {
                            obstacles { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", secondText)
                .execute();

        // Assert
        response
                .path("addRealityItem.obstacles").entityList(Object.class).hasSize(2)
                .path("addRealityItem.obstacles[0].text").entity(String.class).isEqualTo(firstText)
                .path("addRealityItem.obstacles[1].text").entity(String.class).isEqualTo(secondText);
    }

    // ─── isolation ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Actions and obstacles are independent - adding action does not affect obstacles")
    void actionsAndObstaclesAreIndependent() {
        // Arrange
        String actionText = "Mapped the current model";
        String obstacleText = "Missing automated coverage";

        // Act
        graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: $text) {
                            actions { text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", actionText)
                .execute();

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "obstacles", text: $text) {
                            actions { id text }
                            obstacles { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", obstacleText)
                .execute();

        // Assert
        response
                .path("addRealityItem.actions").entityList(Object.class).hasSize(1)
                .path("addRealityItem.actions[0].text").entity(String.class).isEqualTo(actionText)
                .path("addRealityItem.obstacles").entityList(Object.class).hasSize(1)
                .path("addRealityItem.obstacles[0].text").entity(String.class).isEqualTo(obstacleText);
    }

    // ─── query ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns reality by goal id with actions and obstacles")
    void returnsRealityByGoal() {
        // Arrange
        graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: $text) {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", "Mapped the current model")
                .execute();

        graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "obstacles", text: $text) {
                            obstacles { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", "Missing automated coverage")
                .execute();

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        query($goalId: ID!) {
                          realityByGoal(goalId: $goalId) {
                            actions { id text }
                            obstacles { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        response
                .path("realityByGoal.actions").entityList(Object.class).hasSize(1)
                .path("realityByGoal.actions[0].text").entity(String.class).isEqualTo("Mapped the current model")
                .path("realityByGoal.obstacles").entityList(Object.class).hasSize(1)
                .path("realityByGoal.obstacles[0].text").entity(String.class).isEqualTo("Missing automated coverage");
    }

    // ─── validation ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns error when updating action with obstacle kind - kind mismatch")
    void returnsErrorWhenUpdatingActionWithObstacleKind() {
        // Arrange
        String actionId = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: $text) {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", "Mapped the current model")
                .execute()
                .path("addRealityItem.actions[0].id").entity(String.class).get();

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $itemId: ID!) {
                          updateRealityItem(goalId: $goalId, kind: "obstacles", itemId: $itemId, text: "Updated") {
                            obstacles { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("itemId", actionId)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Reality item does not belong to goal/kind") &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    @Test
    @DisplayName("Returns error when updating item that belongs to another goal")
    void returnsErrorWhenUpdatingItemOfAnotherGoal() {
        // Arrange
        String otherGoalId = createGoal("Another goal", 5, "Another description", "2026-06-01T00:00:00Z");

        String actionId = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: $text) {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", otherGoalId)
                .variable("text", "Action belonging to another goal")
                .execute()
                .path("addRealityItem.actions[0].id").entity(String.class).get();

        // Act - try to update the item using our goalId instead of otherGoalId
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $itemId: ID!) {
                          updateRealityItem(goalId: $goalId, kind: "actions", itemId: $itemId, text: "Updated") {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("itemId", actionId)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Reality item does not belong to goal/kind") &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    @Test
    @DisplayName("Returns error when kind is unknown")
    void returnsErrorForUnknownKind() {
        // Arrange
        // No Arrange needed - we are testing schema rejection of an unknown kind

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          addRealityItem(goalId: $goalId, kind: "something", text: "test") {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Unknown reality kind") &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    @Test
    @DisplayName("Trims whitespace from action text on create")
    void trimsWhitespaceFromActionText() {
        // Arrange
        String text = "  Mapped the current model  ";

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: $text) {
                            actions { text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", text)
                .execute();

        // Assert
        response
                .path("addRealityItem.actions[0].text").entity(String.class).isEqualTo("Mapped the current model");
    }

    @Test
    @DisplayName("Trims whitespace from obstacle text on create")
    void trimsWhitespaceFromObstacleText() {
        // Arrange
        String text = "  Missing automated coverage  ";

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "obstacles", text: $text) {
                            obstacles { text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", text)
                .execute();

        // Assert
        response
                .path("addRealityItem.obstacles[0].text").entity(String.class).isEqualTo("Missing automated coverage");
    }

    @Test
    @DisplayName("Returns ValidationError when creating action with blank text")
    void returnsErrorWhenCreatingActionWithBlankText() {
        // Arrange & Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: "   ") {
                            actions { text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Reality item text is required") &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    @Test
    @DisplayName("Returns ValidationError when creating obstacle with blank text")
    void returnsErrorWhenCreatingObstacleWithBlankText() {
        // Arrange & Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          addRealityItem(goalId: $goalId, kind: "obstacles", text: "") {
                            obstacles { text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Reality item text is required") &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    @Test
    @DisplayName("Returns ValidationError when creating action with oversized text")
    void returnsErrorWhenCreatingActionWithOversizedText() {
        // Arrange
        String oversizedText = "A".repeat(501);

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: $text) {
                            actions { text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", oversizedText)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Reality item text must be 500 characters or fewer") &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    @Test
    @DisplayName("Returns ValidationError when creating obstacle with oversized text")
    void returnsErrorWhenCreatingObstacleWithOversizedText() {
        // Arrange
        String oversizedText = "B".repeat(501);

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "obstacles", text: $text) {
                            obstacles { text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", oversizedText)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Reality item text must be 500 characters or fewer") &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    @Test
    @DisplayName("Accepts action text at maximum length")
    void acceptsActionTextAtMaximumLength() {
        // Arrange
        String maxLengthText = "C".repeat(500);

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: $text) {
                            actions { text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", maxLengthText)
                .execute();

        // Assert
        response
                .path("addRealityItem.actions[0].text").entity(String.class).isEqualTo(maxLengthText);
    }

    @Test
    @DisplayName("Accepts obstacle text at maximum length")
    void acceptsObstacleTextAtMaximumLength() {
        // Arrange
        String maxLengthText = "D".repeat(500);

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "obstacles", text: $text) {
                            obstacles { text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", maxLengthText)
                .execute();

        // Assert
        response
                .path("addRealityItem.obstacles[0].text").entity(String.class).isEqualTo(maxLengthText);
    }

    // ─── update ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Updates action text and persists it")
    void updatesActionText() {
        // Arrange
        String originalText = "Mapped the current model";
        String updatedText = "Mapped and documented the current model";

        String actionId = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: $text) {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", originalText)
                .execute()
                .path("addRealityItem.actions[0].id").entity(String.class).get();

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $itemId: ID!, $text: String!) {
                          updateRealityItem(goalId: $goalId, kind: "actions", itemId: $itemId, text: $text) {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("itemId", actionId)
                .variable("text", updatedText)
                .execute();

        // Assert
        response
                .path("updateRealityItem.actions").entityList(Object.class).hasSize(1)
                .path("updateRealityItem.actions[0].text").entity(String.class).isEqualTo(updatedText);
    }

    @Test
    @DisplayName("Updates obstacle text and persists it")
    void updatesObstacleText() {
        // Arrange
        String originalText = "Missing automated coverage";
        String updatedText = "Missing automated coverage for GraphQL mutations";

        String obstacleId = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "obstacles", text: $text) {
                            obstacles { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", originalText)
                .execute()
                .path("addRealityItem.obstacles[0].id").entity(String.class).get();

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $itemId: ID!, $text: String!) {
                          updateRealityItem(goalId: $goalId, kind: "obstacles", itemId: $itemId, text: $text) {
                            obstacles { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("itemId", obstacleId)
                .variable("text", updatedText)
                .execute();

        // Assert
        response
                .path("updateRealityItem.obstacles").entityList(Object.class).hasSize(1)
                .path("updateRealityItem.obstacles[0].text").entity(String.class).isEqualTo(updatedText);
    }

    @Test
    @DisplayName("Returns error when updating non-existent reality item")
    void returnsErrorWhenUpdatingNonExistentItem() {
        // Arrange
        // No Arrange needed - we are testing error handling for a non-existent item

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          updateRealityItem(goalId: $goalId, kind: "actions", itemId: "999999", text: "Updated") {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Reality item not found") &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    // ─── remove ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Removes action from reality")
    void removesActionFromReality() {
        // Arrange
        String actionId = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: $text) {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", "Mapped the current model")
                .execute()
                .path("addRealityItem.actions[0].id").entity(String.class).get();

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $itemId: ID!) {
                          removeRealityItem(goalId: $goalId, kind: "actions", itemId: $itemId) {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("itemId", actionId)
                .execute();

        // Assert
        response
                .path("removeRealityItem.actions").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Removes obstacle from reality")
    void removesObstacleFromReality() {
        // Arrange
        String obstacleId = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: "obstacles", text: $text) {
                            obstacles { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", "Missing automated coverage")
                .execute()
                .path("addRealityItem.obstacles[0].id").entity(String.class).get();

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $itemId: ID!) {
                          removeRealityItem(goalId: $goalId, kind: "obstacles", itemId: $itemId) {
                            obstacles { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("itemId", obstacleId)
                .execute();

        // Assert
        response
                .path("removeRealityItem.obstacles").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Returns error when removing non-existent reality item")
    void returnsErrorWhenRemovingNonExistentItem() {
        // Arrange
        // No Arrange needed - we are testing error handling for a non-existent item

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          removeRealityItem(goalId: $goalId, kind: "actions", itemId: "999999") {
                            actions { id text }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Reality item not found") &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    @Test
    void realityItemHasTimestamps() {
        graphQlTester.document("""
                        mutation($goalId: ID!, $kind: String!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: $kind, text: $text) {
                            actions {
                              id
                              createdAt
                              updatedAt
                            }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("kind", "action")
                .variable("text", "Timestamp test action")
                .execute()
                .path("addRealityItem.actions[0].createdAt").hasValue()
                .path("addRealityItem.actions[0].updatedAt").hasValue()
                .path("addRealityItem.actions[0].createdAt").entity(String.class).satisfies(s -> assertThat(java.time.Instant.parse(s)).isNotNull())
                .path("addRealityItem.actions[0].updatedAt").entity(String.class).satisfies(s -> assertThat(java.time.Instant.parse(s)).isNotNull());
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
}
