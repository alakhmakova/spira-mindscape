package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.goal.GoalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
class GoalCreationIntegrationTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private GoalRepository goalRepository;

    @AfterEach
    void cleanDatabase() {
        goalRepository.deleteAll();
    }

    @Test
    @DisplayName("Creates goal with required fields only - title and confidence - and returns empty nested collections")
    void createsGoalWithRequiredFieldsOnly() {
        // Arrange
        String title = "Learn GraphQL";
        int confidence = 1;

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($title: String!, $confidence: Int!) {
                          createGoal(input: {
                            title: $title
                            confidence: $confidence
                          }) {
                            id
                            title
                            description
                            confidence
                            createdAt
                            achievedAt
                            progress
                            reality {
                              actions { id text }
                              obstacles { id text }
                            }
                            options { id }
                            resources { id }
                            targets { id }
                          }
                        }
                        """)
                .variable("title", title)
                .variable("confidence", confidence)
                .execute();

        // Assert
        response
                .path("createGoal.id").hasValue()
                .path("createGoal.title").entity(String.class).isEqualTo(title)
                .path("createGoal.description").entity(String.class).isEqualTo("")
                .path("createGoal.confidence").entity(Integer.class).isEqualTo(confidence)
                .path("createGoal.createdAt").entity(String.class)
                .satisfies(createdAt -> assertThat(Instant.parse(createdAt)).isBeforeOrEqualTo(Instant.now()))
                .path("createGoal.achievedAt").valueIsNull()
                .path("createGoal.progress").entity(Double.class).isEqualTo(0d)
                .path("createGoal.reality.actions").entityList(Object.class).hasSize(0)
                .path("createGoal.reality.obstacles").entityList(Object.class).hasSize(0)
                .path("createGoal.options").entityList(Object.class).hasSize(0)
                .path("createGoal.resources").entityList(Object.class).hasSize(0)
                .path("createGoal.targets").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Creates goal with all fields - long title, max confidence, description and deadline")
    void createsGoalWithAllFields() {
        // Arrange
        String title = "Master GraphQL fundamentals including queries mutations subscriptions fragments directives schema design and best practices for APIs";
        int confidence = 10;
        String description = "Understand queries and mutations in depth, covering schema design, resolvers, and testing strategies";
        String deadline = "2026-12-31T00:00:00Z";

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($title: String!, $confidence: Int!, $description: String!, $deadline: String!) {
                          createGoal(input: {
                            title: $title
                            confidence: $confidence
                            description: $description
                            deadline: $deadline
                          }) {
                            id
                            title
                            description
                            confidence
                            deadline
                            createdAt
                            achievedAt
                            progress
                            reality {
                              actions { id text }
                              obstacles { id text }
                            }
                            options { id }
                            resources { id }
                            targets { id }
                          }
                        }
                        """)
                .variable("title", title)
                .variable("confidence", confidence)
                .variable("description", description)
                .variable("deadline", deadline)
                .execute();

        // Assert
        response
                .path("createGoal.id").hasValue()
                .path("createGoal.title").entity(String.class).isEqualTo(title)
                .path("createGoal.description").entity(String.class).isEqualTo(description)
                .path("createGoal.confidence").entity(Integer.class).isEqualTo(confidence)
                .path("createGoal.deadline").hasValue()
                .path("createGoal.createdAt").entity(String.class)
                .satisfies(createdAt -> assertThat(Instant.parse(createdAt)).isBeforeOrEqualTo(Instant.now()))
                .path("createGoal.achievedAt").valueIsNull()
                .path("createGoal.progress").entity(Double.class).isEqualTo(0d)
                .path("createGoal.reality.actions").entityList(Object.class).hasSize(0)
                .path("createGoal.reality.obstacles").entityList(Object.class).hasSize(0)
                .path("createGoal.options").entityList(Object.class).hasSize(0)
                .path("createGoal.resources").entityList(Object.class).hasSize(0)
                .path("createGoal.targets").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Trims whitespace from goal title and description on create")
    void trimsWhitespaceFromGoalTitleAndDescriptionOnCreate() {
        graphQlTester.document("""
                        mutation {
                          createGoal(input: {
                            title: "   Learn GraphQL   "
                            description: "   Study resolvers   "
                            confidence: 5
                          }) {
                            title
                            description
                          }
                        }
                        """)
                .execute()
                .path("createGoal.title").entity(String.class).isEqualTo("Learn GraphQL")
                .path("createGoal.description").entity(String.class).isEqualTo("Study resolvers");
    }

    @ParameterizedTest(name = "Returns ValidationError when creating goal with {0} title")
    @MethodSource("blankGoalTitles")
    void returnsErrorWhenCreatingGoalWithBlankTitle(String label, String title) {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($title: String!) {
                          createGoal(input: {
                            title: $title
                            confidence: 5
                          }) {
                            id
                          }
                        }
                        """)
                .variable("title", title)
                .execute();

        assertValidationError(response, "Goal title is required");
        assertNoGoalsCreated();
    }

    @Test
    @DisplayName("Creates goal with title at maximum length")
    void createsGoalWithTitleAtMaximumLength() {
        String title = "A".repeat(200);

        graphQlTester.document("""
                        mutation($title: String!) {
                          createGoal(input: {
                            title: $title
                            confidence: 5
                          }) {
                            title
                          }
                        }
                        """)
                .variable("title", title)
                .execute()
                .path("createGoal.title").entity(String.class).isEqualTo(title);
    }

    @Test
    @DisplayName("Returns ValidationError when creating goal with title longer than 200 characters")
    void returnsErrorWhenCreatingGoalWithOversizedTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($title: String!) {
                          createGoal(input: {
                            title: $title
                            confidence: 5
                          }) {
                            id
                          }
                        }
                        """)
                .variable("title", "A".repeat(201))
                .execute();

        assertValidationError(response, "Goal title must be 200 characters or fewer");
        assertNoGoalsCreated();
    }

    @Test
    @DisplayName("Creates goal with description at maximum length")
    void createsGoalWithDescriptionAtMaximumLength() {
        String description = "A".repeat(5000);

        graphQlTester.document("""
                        mutation($description: String!) {
                          createGoal(input: {
                            title: "Learn GraphQL"
                            description: $description
                            confidence: 5
                          }) {
                            description
                          }
                        }
                        """)
                .variable("description", description)
                .execute()
                .path("createGoal.description").entity(String.class).isEqualTo(description);
    }

    @Test
    @DisplayName("Returns ValidationError when creating goal with description longer than 5000 characters")
    void returnsErrorWhenCreatingGoalWithOversizedDescription() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($description: String!) {
                          createGoal(input: {
                            title: "Learn GraphQL"
                            description: $description
                            confidence: 5
                          }) {
                            id
                          }
                        }
                        """)
                .variable("description", "A".repeat(5001))
                .execute();

        assertValidationError(response, "Goal description must be 5000 characters or fewer");
        assertNoGoalsCreated();
    }

    @Test
    @DisplayName("Rejects createGoal with empty input - title and confidence are required")
    void rejectsCreateGoalWithEmptyInput() {
        // Arrange
        // No Arrange needed - we are testing schema rejection of an empty input object

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation {
                          createGoal(input: {}) {
                            id
                          }
                        }
                        """)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }

    @Test
    @DisplayName("Rejects createGoal when title is missing - title is required")
    void rejectsCreateGoalWithMissingTitle() {
        // Arrange
        // No Arrange needed - we are testing schema rejection of a missing required field

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation {
                          createGoal(input: {
                            confidence: 5
                          }) {
                            id
                          }
                        }
                        """)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }

    @Test
    @DisplayName("Rejects createGoal when confidence is missing - confidence is required")
    void rejectsCreateGoalWithMissingConfidence() {
        // Arrange
        // No Arrange needed - we are testing schema rejection of a missing required field

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation {
                          createGoal(input: {
                            title: "Learn GraphQL"
                          }) {
                            id
                          }
                        }
                        """)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }

    @ParameterizedTest(name = "Rejects createGoal with invalid deadline: {0}")
    @ValueSource(strings = {"not-a-date", "2026-12-31"})
    void rejectsCreateGoalWithInvalidDeadlineFormat(String invalidDeadline) {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($deadline: String!) {
                          createGoal(input: {
                            title: "Learn GraphQL"
                            confidence: 5
                            deadline: $deadline
                          }) {
                            id
                          }
                        }
                        """)
                .variable("deadline", invalidDeadline)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getMessage()).contains("Invalid date format");
                    assertThat(errors.get(0).getExtensions().get("classification")).isEqualTo("ValidationError");
                });
    }

    @Test
    @DisplayName("Updates and clears mutable goal fields including deadline")
    void updatesAndClearsMutableGoalFields() {
        // Arrange
        String goalId = graphQlTester.document("""
                        mutation {
                          createGoal(input: {
                            title: "Goal before update"
                            confidence: 4
                            description: "Before"
                            deadline: "2026-12-31T00:00:00Z"
                          }) {
                            id
                          }
                        }
                        """)
                .execute()
                .path("createGoal.id").entity(String.class).get();

        GraphQlTester.Response updated = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: {
                            title: "Goal after update"
                            confidence: 9
                            description: "After"
                            deadline: "2027-01-15T00:00:00Z"
                          }) {
                            title
                            description
                            confidence
                            deadline
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute();

        updated
                .path("updateGoal.title").entity(String.class).isEqualTo("Goal after update")
                .path("updateGoal.description").entity(String.class).isEqualTo("After")
                .path("updateGoal.confidence").entity(Integer.class).isEqualTo(9)
                .path("updateGoal.deadline").entity(String.class).isEqualTo("2027-01-15T00:00:00Z");

        // Act
        GraphQlTester.Response cleared = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: {
                            deadline: null
                          }) {
                            deadline
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute();

        // Assert
        cleared
                .path("updateGoal.deadline").valueIsNull();
    }

    @Test
    @DisplayName("Sets and clears achievedAt date")
    void setsAndClearsAchievedAtDate() {
        String goalId = createGoal("Goal with achieved date", "Original description");

        // Set achievedAt
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $achievedAt: String!) {
                          updateGoal(id: $id, input: { achievedAt: $achievedAt }) {
                            achievedAt
                          }
                        }
                        """)
                .variable("id", goalId)
                .variable("achievedAt", "2027-01-20T00:00:00Z")
                .execute();

        response.path("updateGoal.achievedAt").entity(String.class).isEqualTo("2027-01-20T00:00:00Z");

        // Clear achievedAt
        GraphQlTester.Response cleared = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: { achievedAt: null }) {
                            achievedAt
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute();

        cleared.path("updateGoal.achievedAt").valueIsNull();
    }

    @Test
    @DisplayName("Can set, change and clear goal deadline with past, today and future dates")
    void updatesGoalDeadlineThroughPastTodayFutureAndClear() {
        String goalId = createGoal("Goal with deadline", "Original description");

        String past = isoDate(LocalDate.now(ZoneOffset.UTC).minusDays(1));
        String today = isoDate(LocalDate.now(ZoneOffset.UTC));
        String future = isoDate(LocalDate.now(ZoneOffset.UTC).plusDays(1));

        updateGoalDeadline(goalId, past);
        updateGoalDeadline(goalId, today);
        updateGoalDeadline(goalId, future);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: { deadline: null }) {
                            id
                            deadline
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute()
                .path("updateGoal.id").entity(String.class).isEqualTo(goalId)
                .path("updateGoal.deadline").valueIsNull();
    }

    @ParameterizedTest(name = "Returns ValidationError when updating goal with invalid deadline: {0}")
    @ValueSource(strings = {"not-a-date", "2026-12-31"})
    void returnsErrorWhenUpdatingGoalWithInvalidDeadline(String deadline) {
        String goalId = createGoal("Goal with deadline", "Original description");

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $deadline: String!) {
                          updateGoal(id: $id, input: { deadline: $deadline }) {
                            id
                          }
                        }
                        """)
                .variable("id", goalId)
                .variable("deadline", deadline)
                .execute();

        assertValidationError(response, "Invalid date format");
    }

    @Test
    @DisplayName("Updates goal description")
    void updatesGoalDescription() {
        String goalId = createGoal("Original title", "Original description");

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: {
                            description: "Updated description"
                          }) {
                            description
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute()
                .path("updateGoal.description").entity(String.class).isEqualTo("Updated description");
    }

    @Test
    @DisplayName("Trims whitespace from goal description on update")
    void trimsWhitespaceFromGoalDescriptionOnUpdate() {
        String goalId = createGoal("Original title", "Original description");

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: {
                            description: "   Updated description   "
                          }) {
                            description
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute()
                .path("updateGoal.description").entity(String.class).isEqualTo("Updated description");
    }

    @Test
    @DisplayName("Clears goal description when null is provided")
    void clearsGoalDescriptionWhenNullIsProvided() {
        String goalId = createGoal("Original title", "Original description");

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: {
                            description: null
                          }) {
                            description
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute()
                .path("updateGoal.description").entity(String.class).isEqualTo("");
    }

    @Test
    @DisplayName("Clearing goal description does not clear goal title")
    void clearingGoalDescriptionDoesNotClearGoalTitle() {
        String goalId = createGoal("Original title", "Original description");

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: {
                            description: null
                          }) {
                            title
                            description
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute()
                .path("updateGoal.title").entity(String.class).isEqualTo("Original title")
                .path("updateGoal.description").entity(String.class).isEqualTo("");
    }

    @Test
    @DisplayName("Does not clear goal title when update sends title null")
    void doesNotClearGoalTitleWhenUpdateSendsNullTitle() {
        String goalId = createGoal("Original title", "Original description");

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: {
                            title: null
                          }) {
                            title
                            description
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute()
                .path("updateGoal.title").entity(String.class).isEqualTo("Original title")
                .path("updateGoal.description").entity(String.class).isEqualTo("Original description");
    }

    @ParameterizedTest(name = "Returns ValidationError when updating goal title to {0}")
    @MethodSource("blankGoalTitles")
    void returnsErrorWhenUpdatingGoalWithBlankTitle(String label, String title) {
        String goalId = createGoal("Original title", "Original description");

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $title: String) {
                          updateGoal(id: $id, input: {
                            title: $title
                          }) {
                            id
                          }
                        }
                        """)
                .variable("id", goalId)
                .variable("title", title)
                .execute();

        assertValidationError(response, "Goal title is required");
        assertGoalField(goalId, "title", "Original title");
    }

    @ParameterizedTest(name = "Updates goal with {0} at maximum length")
    @MethodSource("goalFieldsAtMaximumLength")
    void updatesGoalFieldAtMaximumLength(String fieldName, String updateInput, String expectedValue) {
        String goalId = createGoal("Original title", "Original description");

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: {
                            %s
                          }) {
                            %s
                          }
                        }
                        """.formatted(updateInput, fieldName))
                .variable("id", goalId)
                .execute()
                .path("updateGoal." + fieldName).entity(String.class).isEqualTo(expectedValue);
    }

    @ParameterizedTest(name = "Returns ValidationError when updating goal with oversized {0}")
    @MethodSource("oversizedGoalFieldUpdates")
    void returnsErrorWhenUpdatingGoalWithOversizedField(String fieldName, String updateInput,
                                                       String expectedOriginalValue, String message) {
        String goalId = createGoal("Original title", "Original description");

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: {
                            %s
                          }) {
                            id
                          }
                        }
                        """.formatted(updateInput))
                .variable("id", goalId)
                .execute();

        assertValidationError(response, message);
        assertGoalField(goalId, fieldName, expectedOriginalValue);
    }

    @Test
    @DisplayName("CreatedAt is set automatically and updatedAt changes on every update")
    void createdAtIsSetAutomaticallyAndUpdatedAtChangesOnUpdate() throws InterruptedException {
        // Arrange
        String title = "Time Tracking Goal";
        int confidence = 5;

        // Act - Create
        GraphQlTester.Response createResponse = graphQlTester.document("""
                        mutation($title: String!, $confidence: Int!) {
                          createGoal(input: {
                            title: $title
                            confidence: $confidence
                          }) {
                            id
                            createdAt
                            updatedAt
                          }
                        }
                        """)
                .variable("title", title)
                .variable("confidence", confidence)
                .execute();

        String id = createResponse.path("createGoal.id").entity(String.class).get();
        Instant createdAt = Instant.parse(createResponse.path("createGoal.createdAt").entity(String.class).get());
        Instant updatedAtInitial = Instant.parse(createResponse.path("createGoal.updatedAt").entity(String.class).get());

        assertThat(createdAt).isBeforeOrEqualTo(Instant.now());
        assertThat(updatedAtInitial).isEqualTo(createdAt);

        // Wait a bit to ensure updatedAt will be different
        Thread.sleep(10);

        // Act - Update
        GraphQlTester.Response updateResponse = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: {
                            confidence: 6
                          }) {
                            createdAt
                            updatedAt
                          }
                        }
                        """)
                .variable("id", id)
                .execute();

        // Assert
        Instant createdAtAfterUpdate = Instant.parse(updateResponse.path("updateGoal.createdAt").entity(String.class).get());
        Instant updatedAtAfterUpdate = Instant.parse(updateResponse.path("updateGoal.updatedAt").entity(String.class).get());

        assertThat(createdAtAfterUpdate.truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                .isEqualTo(createdAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThat(updatedAtAfterUpdate).isAfter(updatedAtInitial);
    }

    @Test
    @DisplayName("Confidence history is recorded on creation and update")
    void confidenceHistoryIsRecorded() {
        // Create goal
        GraphQlTester.Response createResponse = graphQlTester.document("""
                        mutation($title: String!, $confidence: Int!) {
                          createGoal(input: { title: $title, confidence: $confidence }) {
                            id
                            confidenceHistory {
                              confidence
                            }
                          }
                        }
                        """)
                .variable("title", "History Test")
                .variable("confidence", 5)
                .execute();

        String id = createResponse.path("createGoal.id").entity(String.class).get();
        createResponse.path("createGoal.confidenceHistory[0].confidence").entity(Integer.class).isEqualTo(5);

        // Update confidence
        graphQlTester.document("""
                        mutation($id: ID!, $confidence: Int!) {
                          updateGoal(id: $id, input: { confidence: $confidence }) {
                            confidenceHistory {
                              confidence
                            }
                          }
                        }
                        """)
                .variable("id", id)
                .variable("confidence", 8)
                .execute()
                .path("updateGoal.confidenceHistory[0].confidence").entity(Integer.class).isEqualTo(8)
                .path("updateGoal.confidenceHistory[1].confidence").entity(Integer.class).isEqualTo(5);
    }

    @Test
    @DisplayName("Deletes goal by id and returns true")
    void deletesGoalById() {
        // Arrange
        String goalId = graphQlTester.document("""
                        mutation {
                          createGoal(input: {
                            title: "Goal to delete"
                            confidence: 5
                          }) {
                            id
                          }
                        }
                        """)
                .execute()
                .path("createGoal.id").entity(String.class).get();

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          deleteGoal(id: $id)
                        }
                        """)
                .variable("id", goalId)
                .execute();

        // Assert
        response.path("deleteGoal").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    @DisplayName("Returns error when deleting goal with non-existent id")
    void returnsErrorWhenDeletingNonExistentGoal() {
        // Arrange
        // No Arrange needed - we are testing error handling for a non-existent goal

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation {
                          deleteGoal(id: "999999")
                        }
                        """)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Goal not found") &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    @Test
    @DisplayName("Returns error for unknown goal id")
    void returnsValidationErrorForUnknownGoal() {
        // Arrange
        // No Arrange needed - we are testing error handling for a non-existent goal

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        query {
                          goalById(id: "999999") {
                            id
                          }
                        }
                        """)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error -> error.getMessage().contains("Goal not found: 999999")));
    }

    private String createGoal(String title, String description) {
        return graphQlTester.document("""
                        mutation($title: String!, $description: String!) {
                          createGoal(input: {
                            title: $title
                            description: $description
                            confidence: 5
                          }) {
                            id
                          }
                        }
                        """)
                .variable("title", title)
                .variable("description", description)
                .execute()
                .path("createGoal.id").entity(String.class).get();
    }

    private void updateGoalDeadline(String goalId, String deadline) {
        graphQlTester.document("""
                        mutation($id: ID!, $deadline: String!) {
                          updateGoal(id: $id, input: { deadline: $deadline }) {
                            id
                            deadline
                          }
                        }
                        """)
                .variable("id", goalId)
                .variable("deadline", deadline)
                .execute()
                .path("updateGoal.id").entity(String.class).isEqualTo(goalId)
                .path("updateGoal.deadline").entity(String.class).isEqualTo(deadline);
    }

    private static String isoDate(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC).toString();
    }

    private void assertValidationError(GraphQlTester.Response response, String message) {
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains(message) &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    private void assertGoalField(String goalId, String fieldName, String expectedValue) {
        graphQlTester.document("""
                        query($id: ID!) {
                          goalById(id: $id) {
                            title
                            description
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute()
                .path("goalById." + fieldName).entity(String.class).isEqualTo(expectedValue);
    }

    private void assertNoGoalsCreated() {
        assertThat(goalRepository.count()).isZero();
    }

    private static Stream<Arguments> blankGoalTitles() {
        return Stream.of(
                Arguments.of("blank", "   "),
                Arguments.of("empty", ""),
                Arguments.of("newline", "\n")
        );
    }

    private static Stream<Arguments> goalFieldsAtMaximumLength() {
        return Stream.of(
                Arguments.of("title",
                        "title: \"" + "A".repeat(200) + "\"",
                        "A".repeat(200)),
                Arguments.of("description",
                        "description: \"" + "A".repeat(5000) + "\"",
                        "A".repeat(5000))
        );
    }

    private static Stream<Arguments> oversizedGoalFieldUpdates() {
        return Stream.of(
                Arguments.of("title",
                        "title: \"" + "A".repeat(201) + "\"",
                        "Original title",
                        "Goal title must be 200 characters or fewer"),
                Arguments.of("description",
                        "description: \"" + "A".repeat(5001) + "\"",
                        "Original description",
                        "Goal description must be 5000 characters or fewer")
        );
    }
}
