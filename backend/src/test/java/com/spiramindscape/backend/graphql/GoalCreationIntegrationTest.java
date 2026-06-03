package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.goal.GoalService;
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

    private static final String NON_EXISTENT_ID = String.valueOf(Long.MAX_VALUE);

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private GoalRepository goalRepository;

    @AfterEach
    void cleanDatabase() {
        goalRepository.deleteAll();
    }

    @Test
    @DisplayName("Creates goal with required fields only - title and confidence - and returns correct initial state")
    void createsGoalWithRequiredFieldsOnly() {
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
                            achievedAt
                            deadline
                            createdAt
                            progress
                            reality {
                              actions { id }
                              obstacles { id }
                            }
                            options { id }
                            resources { id }
                            targets { id }
                            confidenceHistory { confidence }
                          }
                        }
                        """)
                .variable("title", "Learn GraphQL")
                .variable("confidence", 1)
                .execute();

        response.path("createGoal.id").hasValue();
        response.path("createGoal.title").entity(String.class).isEqualTo("Learn GraphQL");
        response.path("createGoal.description").entity(String.class).isEqualTo("");
        response.path("createGoal.confidence").entity(Integer.class).isEqualTo(1);
        response.path("createGoal.achievedAt").valueIsNull();
        response.path("createGoal.deadline").valueIsNull();
        response.path("createGoal.createdAt").entity(String.class)
                .satisfies(createdAt -> assertThat(Instant.parse(createdAt)).isBeforeOrEqualTo(Instant.now()));
        response.path("createGoal.progress").entity(Double.class).isEqualTo(0d);
        response.path("createGoal.reality.actions").entityList(Object.class).hasSize(0);
        response.path("createGoal.reality.obstacles").entityList(Object.class).hasSize(0);
        response.path("createGoal.options").entityList(Object.class).hasSize(0);
        response.path("createGoal.resources").entityList(Object.class).hasSize(0);
        response.path("createGoal.targets").entityList(Object.class).hasSize(0);
        response.path("createGoal.confidenceHistory").entityList(Object.class).hasSize(1);
        response.path("createGoal.confidenceHistory[0].confidence").entity(Integer.class).isEqualTo(1);
    }

    @Test
    @DisplayName("Creates goal with all optional fields - full initial state is correct")
    void createsGoalWithAllFields() {
        String title = "Master GraphQL fundamentals including queries mutations subscriptions fragments directives schema design and best practices for APIs";
        String description = "Understand queries and mutations in depth, covering schema design, resolvers, and testing strategies";
        String deadline = "2026-12-31T00:00:00Z";

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
                            achievedAt
                            progress
                            reality {
                              actions { id }
                              obstacles { id }
                            }
                            options { id }
                            resources { id }
                            targets { id }
                            confidenceHistory { confidence }
                          }
                        }
                        """)
                .variable("title", title)
                .variable("confidence", 10)
                .variable("description", description)
                .variable("deadline", deadline)
                .execute();

        response.path("createGoal.id").hasValue();
        response.path("createGoal.title").entity(String.class).isEqualTo(title);
        response.path("createGoal.description").entity(String.class).isEqualTo(description);
        response.path("createGoal.confidence").entity(Integer.class).isEqualTo(10);
        response.path("createGoal.deadline").entity(String.class).isEqualTo(deadline);
        response.path("createGoal.achievedAt").valueIsNull();
        response.path("createGoal.progress").entity(Double.class).isEqualTo(0d);
        response.path("createGoal.reality.actions").entityList(Object.class).hasSize(0);
        response.path("createGoal.reality.obstacles").entityList(Object.class).hasSize(0);
        response.path("createGoal.options").entityList(Object.class).hasSize(0);
        response.path("createGoal.resources").entityList(Object.class).hasSize(0);
        response.path("createGoal.targets").entityList(Object.class).hasSize(0);
        response.path("createGoal.confidenceHistory").entityList(Object.class).hasSize(1);
        response.path("createGoal.confidenceHistory[0].confidence").entity(Integer.class).isEqualTo(10);
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
        String title = "A".repeat(GoalService.MAX_GOAL_TITLE_LENGTH);

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
    @DisplayName("Returns ValidationError when creating goal with title longer than the maximum length")
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
                .variable("title", "A".repeat(GoalService.MAX_GOAL_TITLE_LENGTH + 1))
                .execute();

        assertValidationError(response, "Goal title must be " + GoalService.MAX_GOAL_TITLE_LENGTH + " characters or fewer");
        assertNoGoalsCreated();
    }

    @Test
    @DisplayName("Creates goal with description at maximum length")
    void createsGoalWithDescriptionAtMaximumLength() {
        String description = "A".repeat(GoalService.MAX_GOAL_DESCRIPTION_LENGTH);

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
    @DisplayName("Returns ValidationError when creating goal with description longer than the maximum length")
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
                .variable("description", "A".repeat(GoalService.MAX_GOAL_DESCRIPTION_LENGTH + 1))
                .execute();

        assertValidationError(response, "Goal description must be " + GoalService.MAX_GOAL_DESCRIPTION_LENGTH + " characters or fewer");
        assertNoGoalsCreated();
    }

    @Test
    @DisplayName("Rejects createGoal with empty input - title and confidence are required")
    void rejectsCreateGoalWithEmptyInput() {
        graphQlTester.document("""
                        mutation {
                          createGoal(input: {}) {
                            id
                          }
                        }
                        """)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }

    @Test
    @DisplayName("Rejects createGoal when title is missing - title is required")
    void rejectsCreateGoalWithMissingTitle() {
        graphQlTester.document("""
                        mutation {
                          createGoal(input: {
                            confidence: 5
                          }) {
                            id
                          }
                        }
                        """)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isNotEmpty());
    }

    @Test
    @DisplayName("Rejects createGoal when confidence is missing - confidence is required")
    void rejectsCreateGoalWithMissingConfidence() {
        graphQlTester.document("""
                        mutation {
                          createGoal(input: {
                            title: "Learn GraphQL"
                          }) {
                            id
                          }
                        }
                        """)
                .execute()
                .errors()
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
    @DisplayName("Trims whitespace from goal title on update")
    void trimsWhitespaceFromGoalTitleOnUpdate() {
        String goalId = createGoal("Original title", "Original description");

        graphQlTester.document("""
                        mutation($id: ID!, $title: String!) {
                          updateGoal(id: $id, input: {
                            title: $title
                          }) {
                            title
                          }
                        }
                        """)
                .variable("id", goalId)
                .variable("title", "   Updated title   ")
                .execute()
                .path("updateGoal.title").entity(String.class).isEqualTo("Updated title");
    }

    @Test
    @DisplayName("Updates goal title")
    void updatesGoalTitle() {
        String goalId = createGoal("Goal before update", "Before");

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: {
                            title: "Goal after update"
                          }) {
                            title
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute()
                .path("updateGoal.title").entity(String.class).isEqualTo("Goal after update");
    }

    @Test
    @DisplayName("Updates goal confidence")
    void updatesGoalConfidence() {
        String goalId = createGoal("Goal before update", "Before");

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: {
                            confidence: 9
                          }) {
                            confidence
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute()
                .path("updateGoal.confidence").entity(Integer.class).isEqualTo(9);
    }

    @Test
    @DisplayName("Sets goal achievedAt date")
    void setsGoalAchievedAtDate() {
        String goalId = createGoal("Goal with achieved date", "Original description");

        graphQlTester.document("""
                        mutation($id: ID!, $achievedAt: String!) {
                          updateGoal(id: $id, input: { achievedAt: $achievedAt }) {
                            achievedAt
                          }
                        }
                        """)
                .variable("id", goalId)
                .variable("achievedAt", "2027-01-20T00:00:00Z")
                .execute()
                .path("updateGoal.achievedAt").entity(String.class).isEqualTo("2027-01-20T00:00:00Z");
    }

    @Test
    @DisplayName("Clears goal achievedAt date when null is provided")
    void clearsGoalAchievedAtDateWhenNullIsProvided() {
        String goalId = createGoal("Goal with achieved date", "Original description");

        graphQlTester.document("""
                        mutation($id: ID!, $achievedAt: String!) {
                          updateGoal(id: $id, input: { achievedAt: $achievedAt }) {
                            achievedAt
                          }
                        }
                        """)
                .variable("id", goalId)
                .variable("achievedAt", "2027-01-20T00:00:00Z")
                .execute();

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: { achievedAt: null }) {
                            achievedAt
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute()
                .path("updateGoal.achievedAt").valueIsNull();
    }

    @Test
    @DisplayName("Returns ValidationError when updating goal with invalid achievedAt format")
    void returnsErrorWhenUpdatingGoalWithInvalidAchievedAtFormat() {
        String goalId = createGoal("Goal", "Description");

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $achievedAt: String!) {
                          updateGoal(id: $id, input: { achievedAt: $achievedAt }) {
                            id
                          }
                        }
                        """)
                .variable("id", goalId)
                .variable("achievedAt", "2027-06-15")
                .execute();

        assertValidationError(response, "Invalid date format");
    }

    @ParameterizedTest(name = "Updates goal deadline with {0} date")
    @MethodSource("goalDeadlines")
    void updatesGoalDeadline(String label, String deadline) {
        String goalId = createGoal("Goal with deadline", "Original description");

        updateGoalDeadline(goalId, deadline);
    }

    @Test
    @DisplayName("Clears goal deadline when null is provided")
    void clearsGoalDeadlineWhenNullIsProvided() {
        String goalId = createGoal("Goal with deadline", "Original description");
        updateGoalDeadline(goalId, "2026-12-31T00:00:00Z");

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: { deadline: null }) {
                            deadline
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute()
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
    @DisplayName("Adds description to goal that was created without one")
    void addsDescriptionToGoalInitiallyCreatedWithoutOne() {
        String goalId = graphQlTester.document("""
                        mutation {
                          createGoal(input: {
                            title: "No description yet"
                            confidence: 5
                          }) {
                            id
                          }
                        }
                        """)
                .execute()
                .path("createGoal.id").entity(String.class).get();

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: {
                            description: "Now has a description"
                          }) {
                            description
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute()
                .path("updateGoal.description").entity(String.class).isEqualTo("Now has a description");
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
    @DisplayName("Updates goal description to empty string when blank whitespace is provided")
    void updatesGoalDescriptionToEmptyStringWhenBlankIsProvided() {
        String goalId = createGoal("Original title", "Has description");

        graphQlTester.document("""
                        mutation($id: ID!, $description: String!) {
                          updateGoal(id: $id, input: {
                            description: $description
                          }) {
                            description
                          }
                        }
                        """)
                .variable("id", goalId)
                .variable("description", "   ")
                .execute()
                .path("updateGoal.description").entity(String.class).isEqualTo("");
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

    @Test
    @DisplayName("Updates goal title to maximum length")
    void updatesGoalTitleToMaximumLength() {
        String maxTitle = "A".repeat(GoalService.MAX_GOAL_TITLE_LENGTH);
        String goalId = createGoal("Original title", "Original description");

        graphQlTester.document("""
                        mutation($id: ID!, $title: String!) {
                          updateGoal(id: $id, input: { title: $title }) {
                            title
                          }
                        }
                        """)
                .variable("id", goalId)
                .variable("title", maxTitle)
                .execute()
                .path("updateGoal.title").entity(String.class).isEqualTo(maxTitle);
    }

    @Test
    @DisplayName("Updates goal description to maximum length")
    void updatesGoalDescriptionToMaximumLength() {
        String maxDescription = "A".repeat(GoalService.MAX_GOAL_DESCRIPTION_LENGTH);
        String goalId = createGoal("Original title", "Original description");

        graphQlTester.document("""
                        mutation($id: ID!, $description: String!) {
                          updateGoal(id: $id, input: { description: $description }) {
                            description
                          }
                        }
                        """)
                .variable("id", goalId)
                .variable("description", maxDescription)
                .execute()
                .path("updateGoal.description").entity(String.class).isEqualTo(maxDescription);
    }

    @Test
    @DisplayName("Returns ValidationError when updating goal with title longer than the maximum length")
    void returnsErrorWhenUpdatingGoalWithOversizedTitle() {
        String goalId = createGoal("Original title", "Original description");

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $title: String!) {
                          updateGoal(id: $id, input: { title: $title }) {
                            id
                          }
                        }
                        """)
                .variable("id", goalId)
                .variable("title", "A".repeat(GoalService.MAX_GOAL_TITLE_LENGTH + 1))
                .execute();

        assertValidationError(response, "Goal title must be " + GoalService.MAX_GOAL_TITLE_LENGTH + " characters or fewer");
        assertGoalField(goalId, "title", "Original title");
    }

    @Test
    @DisplayName("Returns ValidationError when updating goal with description longer than the maximum length")
    void returnsErrorWhenUpdatingGoalWithOversizedDescription() {
        String goalId = createGoal("Original title", "Original description");

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $description: String!) {
                          updateGoal(id: $id, input: { description: $description }) {
                            id
                          }
                        }
                        """)
                .variable("id", goalId)
                .variable("description", "A".repeat(GoalService.MAX_GOAL_DESCRIPTION_LENGTH + 1))
                .execute();

        assertValidationError(response, "Goal description must be " + GoalService.MAX_GOAL_DESCRIPTION_LENGTH + " characters or fewer");
        assertGoalField(goalId, "description", "Original description");
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
        assertThat(updatedAtInitial).isAfterOrEqualTo(createdAt);
        assertThat(java.time.Duration.between(createdAt, updatedAtInitial))
                .isLessThan(java.time.Duration.ofMillis(50));

        // Wait a bit to ensure updatedAt will be different
        waitForTimestampAdvance();

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
    @DisplayName("goalById returns created goal with correct id, title, description and confidence")
    void goalByIdReturnsCreatedGoalWithCorrectFields() {
        String goalId = createGoal("My goal title", "My description");

        graphQlTester.document("""
                        query($id: ID!) {
                          goalById(id: $id) {
                            id
                            title
                            description
                            confidence
                            achievedAt
                            deadline
                          }
                        }
                        """)
                .variable("id", goalId)
                .execute()
                .path("goalById.id").entity(String.class).isEqualTo(goalId)
                .path("goalById.title").entity(String.class).isEqualTo("My goal title")
                .path("goalById.description").entity(String.class).isEqualTo("My description")
                .path("goalById.confidence").entity(Integer.class).isEqualTo(5)
                .path("goalById.achievedAt").valueIsNull()
                .path("goalById.deadline").valueIsNull();
    }

    @Test
    @DisplayName("Returns error when deleting goal with non-existent id")
    void returnsErrorWhenDeletingNonExistentGoal() {
        // Arrange
        // No Arrange needed - we are testing error handling for a non-existent goal

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          deleteGoal(id: $id)
                        }
                        """)
                .variable("id", NON_EXISTENT_ID)
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
                        query($id: ID!) {
                          goalById(id: $id) {
                            id
                          }
                        }
                        """)
                .variable("id", NON_EXISTENT_ID)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error -> error.getMessage().contains("Goal not found: " + NON_EXISTENT_ID)));
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

    private static void waitForTimestampAdvance() throws InterruptedException {
        Thread.sleep(50);
    }

    private static Stream<Arguments> blankGoalTitles() {
        return Stream.of(
                Arguments.of("blank", "   "),
                Arguments.of("empty", ""),
                Arguments.of("newline", "\n")
        );
    }

    private static Stream<Arguments> goalDeadlines() {
        return Stream.of(
                Arguments.of("past", isoDate(LocalDate.now(ZoneOffset.UTC).minusDays(1))),
                Arguments.of("today", isoDate(LocalDate.now(ZoneOffset.UTC))),
                Arguments.of("future", isoDate(LocalDate.now(ZoneOffset.UTC).plusDays(1)))
        );
    }

}
