package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class TargetIntegrationTest extends BaseGraphQlIntegrationTest {

    private static final String NON_EXISTENT_ID = String.valueOf(Long.MAX_VALUE);

    private String goalId;

    @BeforeEach
    void createGoal() {
        goalId = graphQlTester.document("""
                        mutation {
                          createGoal(input: { title: "Test goal for targets", confidence: 5 }) {
                            id
                          }
                        }
                        """)
                .execute()
                .path("createGoal.id").entity(String.class).get();
    }

    // --- createTarget: binary ------------------------------------------------

    @Test
    @DisplayName("Creates binary target when all required fields are provided")
    void createsBinaryTargetWithRequiredFieldsOnly() {
        // Arrange
        String title = "Add integration tests";
        boolean expectedDone = false;
        double expectedProgress = 0d;

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $title: String!) {
                          createTarget(goalId: $goalId, input: {
                            type: "binary"
                            title: $title
                          }) {
                            id type title done deadline progress
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", title)
                .execute();

        // Assert
        response
                .path("createTarget.id").hasValue()
                .path("createTarget.type").entity(String.class).isEqualTo("binary")
                .path("createTarget.title").entity(String.class).isEqualTo(title)
                .path("createTarget.done").entity(Boolean.class).isEqualTo(expectedDone)
                .path("createTarget.deadline").valueIsNull()
                .path("createTarget.progress").entity(Double.class).isEqualTo(expectedProgress);
    }

    @Test
    @DisplayName("Returns ValidationError when creating binary target without type")
    void returnsErrorWhenCreatingBinaryTargetWithoutType() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            title: "Add integration tests"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        assertGraphQlValidationError(response, "missing required fields", "type");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating binary target with blank title")
    void returnsErrorWhenCreatingBinaryTargetWithBlankTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "binary"
                            title: "   "
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Target title is required");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating numeric target with blank title")
    void returnsErrorWhenCreatingNumericTargetWithBlankTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "numeric"
                            title: "   "
                            start: 0
                            total: 10
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Target title is required");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating checklist target with blank title")
    void returnsErrorWhenCreatingChecklistTargetWithBlankTitle() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: "   "
                            items: [{ text: "Step 1" }]
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Target title is required");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when updating target with blank title - original title is preserved")
    void returnsErrorWhenUpdatingTargetWithBlankTitle() {
        String targetId = createBinaryTarget(goalId, "Original title", false);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: { title: "   " }) {
                            id title
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute();

        assertValidationError(response, "Target title is required");
        graphQlTester.document("""
                        query($id: ID!) {
                          targetById(id: $id) { title }
                        }
                        """)
                .variable("id", targetId)
                .execute()
                .path("targetById.title").entity(String.class).isEqualTo("Original title");
    }

    @Test
    @DisplayName("Returns ValidationError when creating binary target without title")
    void returnsErrorWhenCreatingBinaryTargetWithoutTitle() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "binary"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        assertGraphQlValidationError(response, "missing required fields", "title");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Creates binary target with optional fields")
    void createsBinaryTargetWithOptionalFields() {
        // Arrange
        String title = "Book session";
        String deadline = "2026-12-31T00:00:00Z";

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $title: String!, $deadline: String!) {
                          createTarget(goalId: $goalId, input: {
                            type: "binary"
                            title: $title
                            done: false
                            deadline: $deadline
                          }) {
                            id type title done deadline progress
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", title)
                .variable("deadline", deadline)
                .execute();

        // Assert
        response
                .path("createTarget.id").hasValue()
                .path("createTarget.type").entity(String.class).isEqualTo("binary")
                .path("createTarget.title").entity(String.class).isEqualTo(title)
                .path("createTarget.done").entity(Boolean.class).isEqualTo(false)
                .path("createTarget.deadline").entity(String.class).isEqualTo(deadline)
                .path("createTarget.progress").entity(Double.class).isEqualTo(0d);
    }

    @Test
    @DisplayName("Returns ValidationError when creating binary target with done=true")
    void returnsErrorWhenCreatingBinaryTargetAsDone() {
        // Arrange
        // No Arrange needed - we are testing validation of initial state

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "binary"
                            title: "Add integration tests"
                            done: true
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
                        .anyMatch(error ->
                                error.getMessage().contains("Cannot create binary target as already done") &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    @ParameterizedTest(name = "Returns ValidationError when creating target with invalid deadline: {0}")
    @ValueSource(strings = {"not-a-date", "2026-12-31"})
    void returnsErrorWhenCreatingTargetWithInvalidDeadline(String deadline) {
        // Act + Assert
        assertCreateTargetWithInvalidDeadlineReturnsValidationError(deadline);
        assertNoTargetsCreated();
    }

    // --- createTarget: numeric -----------------------------------------------

    @Test
    @DisplayName("Creates numeric target when all required numeric fields are provided")
    void createsNumericTargetWithRequiredFieldsOnly() {
        // Arrange
        String title = "Read pages";
        double start = 0d;
        double total = 10d;
        double expectedCurrent = start;
        double expectedProgress = 0d;

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $title: String!, $start: Float!, $total: Float!) {
                          createTarget(goalId: $goalId, input: {
                            type: "numeric"
                            title: $title
                            start: $start
                            total: $total
                          }) {
                            id type title start current total unit deadline progress
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", title)
                .variable("start", start)
                .variable("total", total)
                .execute();

        // Assert
        response
                .path("createTarget.id").hasValue()
                .path("createTarget.type").entity(String.class).isEqualTo("numeric")
                .path("createTarget.title").entity(String.class).isEqualTo(title)
                .path("createTarget.start").entity(Double.class).isEqualTo(start)
                .path("createTarget.current").entity(Double.class).isEqualTo(expectedCurrent)
                .path("createTarget.total").entity(Double.class).isEqualTo(total)
                .path("createTarget.unit").valueIsNull()
                .path("createTarget.deadline").valueIsNull()
                .path("createTarget.progress").entity(Double.class).isEqualTo(expectedProgress);
    }

    @Test
    @DisplayName("Creates numeric target with optional fields")
    void createsNumericTargetWithOptionalFields() {
        // Arrange
        String title = "Read pages";
        double start = 5d;
        double total = 20d;
        String unit = "pages";
        String deadline = "2026-12-31T00:00:00Z";

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $title: String!, $start: Float!, $total: Float!, $unit: String!, $deadline: String!) {
                          createTarget(goalId: $goalId, input: {
                            type: "numeric"
                            title: $title
                            start: $start
                            total: $total
                            unit: $unit
                            deadline: $deadline
                          }) {
                            id type title start current total unit deadline progress
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", title)
                .variable("start", start)
                .variable("total", total)
                .variable("unit", unit)
                .variable("deadline", deadline)
                .execute();

        // Assert
        response
                .path("createTarget.id").hasValue()
                .path("createTarget.type").entity(String.class).isEqualTo("numeric")
                .path("createTarget.title").entity(String.class).isEqualTo(title)
                .path("createTarget.start").entity(Double.class).isEqualTo(start)
                .path("createTarget.current").entity(Double.class).isEqualTo(start)
                .path("createTarget.total").entity(Double.class).isEqualTo(total)
                .path("createTarget.unit").entity(String.class).isEqualTo(unit)
                .path("createTarget.deadline").entity(String.class).isEqualTo(deadline)
                .path("createTarget.progress").entity(Double.class).isEqualTo(0d);
    }

    @Test
    @DisplayName("Creates numeric target with descending start and target")
    void createsNumericTargetWithDescendingRange() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "numeric"
                            title: "Reduce weight"
                            start: 64
                            total: 54
                          }) {
                            id start current total progress
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        response
                .path("createTarget.start").entity(Double.class).isEqualTo(64d)
                .path("createTarget.current").entity(Double.class).isEqualTo(64d)
                .path("createTarget.total").entity(Double.class).isEqualTo(54d)
                .path("createTarget.progress").entity(Double.class).isEqualTo(0d);
    }

    @Test
    @DisplayName("Returns ValidationError when creating numeric target without start")
    void returnsErrorWhenCreatingNumericTargetWithoutStart() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "numeric"
                            title: "Read pages"
                            total: 10
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        assertValidationError(response, "Numeric target requires start");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating numeric target without type")
    void returnsErrorWhenCreatingNumericTargetWithoutType() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            title: "Read pages"
                            start: 0
                            total: 10
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        assertGraphQlValidationError(response, "missing required fields", "type");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating numeric target without title")
    void returnsErrorWhenCreatingNumericTargetWithoutTitle() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "numeric"
                            start: 0
                            total: 10
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        assertGraphQlValidationError(response, "missing required fields", "title");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating target with empty input")
    void returnsErrorWhenCreatingTargetWithEmptyInput() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {}) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> {
                    assertThat(errors)
                            .anyMatch(error ->
                                    "ValidationError".equals(error.getErrorType().toString()) &&
                                    error.getMessage().contains("missing required fields") &&
                                    error.getMessage().contains("type"));
                    assertThat(errors)
                            .anyMatch(error ->
                                    "ValidationError".equals(error.getErrorType().toString()) &&
                                    error.getMessage().contains("missing required fields") &&
                                    error.getMessage().contains("title"));
                });
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating numeric target with current")
    void returnsErrorWhenCreatingNumericTargetWithCurrent() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "numeric"
                            title: "Read pages"
                            start: 0
                            current: 5
                            total: 10
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        assertValidationError(response, "Numeric target current cannot be set on create");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating numeric target without total")
    void returnsErrorWhenCreatingNumericTargetWithoutTotal() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "numeric"
                            title: "Read pages"
                            start: 0
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        assertValidationError(response, "Numeric target requires total");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating numeric target with negative values")
    void returnsErrorWhenCreatingNumericTargetWithNegativeValues() {
        assertCreateNumericTargetValidationError(-1d, 10d, "Numeric target start cannot be negative");
        assertCreateNumericTargetValidationError(1d, -10d, "Numeric target target cannot be negative");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating numeric target with equal start and target")
    void returnsErrorWhenCreatingNumericTargetWithEqualStartAndTarget() {
        assertCreateNumericTargetValidationError(10d, 10d, "Numeric target start and target must be different");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating numeric target with explicit null start")
    void returnsErrorWhenCreatingNumericTargetWithExplicitNullStart() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "numeric"
                            title: "Read pages"
                            start: null
                            total: 10
                          }) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Numeric target start cannot be null");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating numeric target with explicit null total")
    void returnsErrorWhenCreatingNumericTargetWithExplicitNullTotal() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "numeric"
                            title: "Read pages"
                            start: 0
                            total: null
                          }) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Numeric target target cannot be null");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating numeric target with explicit null current")
    void returnsErrorWhenCreatingNumericTargetWithExplicitNullCurrent() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "numeric"
                            title: "Read pages"
                            start: 0
                            current: null
                            total: 10
                          }) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Numeric target current cannot be null");
        assertNoTargetsCreated();
    }

    // --- createTarget: checklist ---------------------------------------------

    @Test
    @DisplayName("Creates checklist target when all required fields are provided")
    void createsChecklistTargetWithRequiredFieldsOnly() {
        // Arrange
        String title = "Prepare workspace";
        String firstTask = "Write requirements";
        String secondTask = "Review validation";
        double expectedProgress = 0d;

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $title: String!, $firstTask: String!, $secondTask: String!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: $title
                            items: [
                              { text: $firstTask }
                              { text: $secondTask }
                            ]
                          }) {
                            id type title deadline items { id text done } progress
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", title)
                .variable("firstTask", firstTask)
                .variable("secondTask", secondTask)
                .execute();

        // Assert
        response
                .path("createTarget.id").hasValue()
                .path("createTarget.type").entity(String.class).isEqualTo("checklist")
                .path("createTarget.title").entity(String.class).isEqualTo(title)
                .path("createTarget.deadline").valueIsNull()
                .path("createTarget.items").entityList(Object.class).hasSize(2)
                .path("createTarget.items[0].text").entity(String.class).isEqualTo(firstTask)
                .path("createTarget.items[0].done").entity(Boolean.class).isEqualTo(false)
                .path("createTarget.items[1].text").entity(String.class).isEqualTo(secondTask)
                .path("createTarget.items[1].done").entity(Boolean.class).isEqualTo(false)
                .path("createTarget.progress").entity(Double.class).isEqualTo(expectedProgress);
    }

    @Test
    @DisplayName("Creates checklist target with optional fields")
    void createsChecklistTargetWithOptionalFields() {
        // Arrange
        String title = "Prepare workspace";
        String targetDeadline = "2026-12-31T00:00:00Z";
        String firstTask = "Write requirements";
        String secondTask = "Review validation";
        String firstTaskDeadline = "2026-06-01T00:00:00Z";

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $title: String!, $targetDeadline: String!, $firstTask: String!, $secondTask: String!, $firstTaskDeadline: String!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: $title
                            deadline: $targetDeadline
                            items: [
                              { text: $firstTask, done: true, deadline: $firstTaskDeadline }
                              { text: $secondTask, done: false }
                            ]
                          }) {
                            id type title deadline items { id text done deadline } progress
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", title)
                .variable("targetDeadline", targetDeadline)
                .variable("firstTask", firstTask)
                .variable("secondTask", secondTask)
                .variable("firstTaskDeadline", firstTaskDeadline)
                .execute();

        // Assert
        response
                .path("createTarget.id").hasValue()
                .path("createTarget.type").entity(String.class).isEqualTo("checklist")
                .path("createTarget.title").entity(String.class).isEqualTo(title)
                .path("createTarget.deadline").entity(String.class).isEqualTo(targetDeadline)
                .path("createTarget.items").entityList(Object.class).hasSize(2)
                .path("createTarget.items[0].text").entity(String.class).isEqualTo(firstTask)
                .path("createTarget.items[0].done").entity(Boolean.class).isEqualTo(true)
                .path("createTarget.items[0].deadline").entity(String.class).isEqualTo(firstTaskDeadline)
                .path("createTarget.items[1].text").entity(String.class).isEqualTo(secondTask)
                .path("createTarget.items[1].done").entity(Boolean.class).isEqualTo(false)
                .path("createTarget.progress").entity(Double.class).isEqualTo(0.5d);
    }

    @Test
    @DisplayName("Returns ValidationError when creating checklist target without type")
    void returnsErrorWhenCreatingChecklistTargetWithoutType() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            title: "Prepare workspace"
                            items: [
                              { text: "Write requirements" }
                            ]
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        assertGraphQlValidationError(response, "missing required fields", "type");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating checklist target without title")
    void returnsErrorWhenCreatingChecklistTargetWithoutTitle() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            items: [
                              { text: "Write requirements" }
                            ]
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        assertGraphQlValidationError(response, "missing required fields", "title");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating checklist target without items")
    void returnsErrorWhenCreatingChecklistTargetWithoutItems() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: "Prepare workspace"
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        assertValidationError(response, "Checklist target requires at least one item");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating checklist target with empty items")
    void returnsErrorWhenCreatingChecklistTargetWithEmptyItems() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: "Prepare workspace"
                            items: []
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        assertValidationError(response, "Checklist target requires at least one item");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating checklist target with item without text")
    void returnsErrorWhenCreatingChecklistTargetWithItemWithoutText() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: "Prepare workspace"
                            items: [
                              { done: false }
                            ]
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        assertGraphQlValidationError(response, "missing required fields", "text");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating checklist target with blank item text")
    void returnsErrorWhenCreatingChecklistTargetWithBlankItemText() {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: "Prepare workspace"
                            items: [
                              { text: "   " }
                            ]
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertValidationError(response, "Checklist item text cannot be blank");
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns ValidationError when creating checklist target with invalid item deadline")
    void returnsErrorWhenCreatingChecklistTargetWithInvalidItemDeadline() {
        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $deadline: String!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: "Prepare workspace"
                            items: [
                              { text: "Write requirements", deadline: $deadline }
                            ]
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("deadline", "not-a-date")
                .execute();

        // Assert
        assertValidationError(response, "Invalid date format");
        assertNoTargetsCreated();
    }

    // --- createTarget: validation --------------------------------------------

    @Test
    @DisplayName("Returns ValidationError when target type is unknown")
    void returnsErrorForUnknownTargetType() {
        // Arrange
        // No Arrange needed - we are testing error handling for an unknown type

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "something"
                            title: "Invalid target"
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
                        .anyMatch(error ->
                                error.getMessage().contains("Unknown target type") &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when creating target for non-existent goal")
    void returnsErrorWhenCreatingTargetForNonExistentGoal() {
        // Arrange
        // No Arrange needed - we are testing error handling for a non-existent goal

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "binary"
                            title: "Some target"
                            done: false
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", NON_EXISTENT_ID)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Goal not found") &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    // --- queries -------------------------------------------------------------

    @Test
    @DisplayName("Returns targets by goal id")
    void returnsTargetsByGoal() {
        // Arrange
        createBinaryTarget(goalId, "First target", false);
        createBinaryTarget(goalId, "Second target", false);

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        query($goalId: ID!) {
                          targetsByGoal(goalId: $goalId) {
                            id title type progress
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        response
                .path("targetsByGoal").entityList(Object.class).hasSize(2)
                .path("targetsByGoal[0].title").entity(String.class).isEqualTo("First target")
                .path("targetsByGoal[1].title").entity(String.class).isEqualTo("Second target");
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when querying targets for non-existent goal")
    void returnsErrorWhenQueryingTargetsForNonExistentGoal() {
        GraphQlTester.Response response = graphQlTester.document("""
                        query($goalId: ID!) {
                          targetsByGoal(goalId: $goalId) { id }
                        }
                        """)
                .variable("goalId", NON_EXISTENT_ID)
                .execute();

        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Goal not found: " + NON_EXISTENT_ID) &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    @Test
    @DisplayName("Returns empty list when goal has no targets")
    void returnsEmptyListWhenGoalHasNoTargets() {
        // Arrange
        // No Arrange needed - goal has no targets

        // Act + Assert
        graphQlTester.document("""
                        query($goalId: ID!) {
                          targetsByGoal(goalId: $goalId) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("targetsByGoal").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Returns target by id")
    void returnsTargetById() {
        // Arrange
        String targetId = createBinaryTarget(goalId, "Find me", false);

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        query($id: ID!) {
                          targetById(id: $id) {
                            id title type done progress
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute();

        // Assert
        response
                .path("targetById.id").entity(String.class).isEqualTo(targetId)
                .path("targetById.title").entity(String.class).isEqualTo("Find me")
                .path("targetById.done").entity(Boolean.class).isEqualTo(false)
                .path("targetById.progress").entity(Double.class).isEqualTo(0d);
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when querying target by non-existent id")
    void returnsErrorWhenQueryingNonExistentTarget() {
        // Arrange
        // No Arrange needed - we are testing error handling for a non-existent target

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        query($id: ID!) {
                          targetById(id: $id) { id }
                        }
                        """)
                .variable("id", NON_EXISTENT_ID)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Target not found") &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    // --- updateTarget --------------------------------------------------------

    @Test
    @DisplayName("Updates binary target from not done to done - progress changes to 1")
    void updatesBinaryTargetToDone() {
        // Arrange
        String targetId = createBinaryTarget(goalId, "Add integration tests", false);

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: { done: true }) {
                            id done progress
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute();

        // Assert
        response
                .path("updateTarget.done").entity(Boolean.class).isEqualTo(true)
                .path("updateTarget.progress").entity(Double.class).isEqualTo(1d);
    }

    @Test
    @DisplayName("Updates binary target from done to not done - progress changes to 0")
    void updatesBinaryTargetToNotDone() {
        // Arrange
        String targetId = createBinaryTarget(goalId, "Reset integration tests", false);
        updateBinaryTargetDone(targetId, true);

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: { done: false }) {
                            id done progress
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute();

        // Assert
        response
                .path("updateTarget.done").entity(Boolean.class).isEqualTo(false)
                .path("updateTarget.progress").entity(Double.class).isEqualTo(0d);
    }

    @Test
    @DisplayName("Updates numeric target current value - progress recalculates")
    void updatesNumericTargetCurrentValue() {
        // Arrange
        String targetId = createNumericTarget(goalId, "Read pages", 0d, 10d);

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: { current: 10 }) {
                            id current progress
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute();

        // Assert
        response
                .path("updateTarget.current").entity(Double.class).isEqualTo(10d)
                .path("updateTarget.progress").entity(Double.class).isEqualTo(1d);
    }

    @Test
    @DisplayName("Updates numeric target current inside ascending range in both directions")
    void updatesNumericTargetCurrentInsideAscendingRangeBothDirections() {
        String targetId = createNumericTarget(goalId, "Read pages", 1d, 10d);

        updateNumericTargetCurrent(targetId, 6d);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: { current: 4 }) {
                            current progress
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute()
                .path("updateTarget.current").entity(Double.class).isEqualTo(4d)
                .path("updateTarget.progress").entity(Double.class)
                .satisfies(progress -> assertThat(progress).isCloseTo(1d / 3d, offset(0.000001d)));
    }

    @Test
    @DisplayName("Updates numeric target current inside descending range and recalculates progress")
    void updatesNumericTargetCurrentInsideDescendingRange() {
        String targetId = createNumericTarget(goalId, "Reduce weight", 64d, 54d);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: { current: 63 }) {
                            current progress
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute()
                .path("updateTarget.current").entity(Double.class).isEqualTo(63d)
                .path("updateTarget.progress").entity(Double.class).isEqualTo(0.1d);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: { current: 60 }) {
                            current progress
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute()
                .path("updateTarget.current").entity(Double.class).isEqualTo(60d)
                .path("updateTarget.progress").entity(Double.class).isEqualTo(0.4d);
    }

    @Test
    @DisplayName("Returns ValidationError when numeric current is outside ascending or descending range")
    void returnsErrorWhenNumericCurrentIsOutsideRange() {
        String ascendingTargetId = createNumericTarget(goalId, "Read pages", 1d, 10d);
        assertUpdateTargetValidationError(ascendingTargetId, "current: 0",
                "Numeric target current must be between start and target");
        assertUpdateTargetValidationError(ascendingTargetId, "current: 11",
                "Numeric target current must be between start and target");

        String descendingTargetId = createNumericTarget(goalId, "Reduce weight", 64d, 54d);
        assertUpdateTargetValidationError(descendingTargetId, "current: 65",
                "Numeric target current must be between start and target");
        assertUpdateTargetValidationError(descendingTargetId, "current: 53",
                "Numeric target current must be between start and target");
    }

    @Test
    @DisplayName("Returns ValidationError when numeric update sends negative or null values")
    void returnsErrorWhenNumericUpdateSendsNegativeOrNullValues() {
        String targetId = createNumericTarget(goalId, "Read pages", 1d, 10d);

        assertUpdateTargetValidationError(targetId, "current: -1",
                "Numeric target current cannot be negative");
        assertUpdateTargetValidationError(targetId, "current: null",
                "Numeric target current cannot be null");
        assertUpdateTargetValidationError(targetId, "start: null",
                "Numeric target start cannot be null");
        assertUpdateTargetValidationError(targetId, "total: null",
                "Numeric target target cannot be null");
    }

    @Test
    @DisplayName("Returns ValidationError when updating numeric target with negative start")
    void returnsErrorWhenUpdatingNumericTargetWithNegativeStart() {
        String targetId = createNumericTarget(goalId, "Read pages", 0d, 10d);

        assertUpdateTargetValidationError(targetId, "start: -1",
                "Numeric target start cannot be negative");
    }

    @Test
    @DisplayName("Returns ValidationError when updating numeric target with negative total")
    void returnsErrorWhenUpdatingNumericTargetWithNegativeTotal() {
        String targetId = createNumericTarget(goalId, "Read pages", 0d, 10d);

        assertUpdateTargetValidationError(targetId, "total: -1",
                "Numeric target target cannot be negative");
    }

    @Test
    @DisplayName("Returns ValidationError when updating numeric target start to equal total")
    void returnsErrorWhenUpdatingNumericTargetWithStartEqualToTotal() {
        // start=0, current=0, total=10 → update start to 10 → start == total
        String targetId = createNumericTarget(goalId, "Read pages", 0d, 10d);

        assertUpdateTargetValidationError(targetId, "start: 10",
                "Numeric target start and target must be different");
    }

    @Test
    @DisplayName("Updates numeric target start value and recalculates progress")
    void updatesNumericTargetStartValue() {
        // create: start=5, current=5, total=10 → update start to 2 → progress=(5-2)/(10-2)=3/8
        String targetId = createNumericTarget(goalId, "Read pages", 5d, 10d);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: { start: 2 }) {
                            start current total progress
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute()
                .path("updateTarget.start").entity(Double.class).isEqualTo(2d)
                .path("updateTarget.current").entity(Double.class).isEqualTo(5d)
                .path("updateTarget.total").entity(Double.class).isEqualTo(10d)
                .path("updateTarget.progress").entity(Double.class)
                .satisfies(p -> assertThat(p).isCloseTo(3d / 8d, offset(0.000001d)));
    }

    @Test
    @DisplayName("Updates numeric target total value and recalculates progress")
    void updatesNumericTargetTotalValue() {
        // create: start=0, current=0, total=10; advance to 5 → update total to 20 → progress=5/20=0.25
        String targetId = createNumericTarget(goalId, "Read pages", 0d, 10d);
        updateNumericTargetCurrent(targetId, 5d);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: { total: 20 }) {
                            start current total progress
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute()
                .path("updateTarget.start").entity(Double.class).isEqualTo(0d)
                .path("updateTarget.current").entity(Double.class).isEqualTo(5d)
                .path("updateTarget.total").entity(Double.class).isEqualTo(20d)
                .path("updateTarget.progress").entity(Double.class).isEqualTo(0.25d);
    }

    @Test
    @DisplayName("Updates checklist target items - progress recalculates")
    void updatesChecklistTargetItems() {
        // Arrange
        String targetId = createChecklistTarget(goalId, "Prepare workspace");

        // Act - mark all items done
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { text: "Write requirements", done: true }
                              { text: "Review validation", done: true }
                            ]
                          }) {
                            id items { text done } progress
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute();

        // Assert
        response
                .path("updateTarget.items[0].done").entity(Boolean.class).isEqualTo(true)
                .path("updateTarget.items[1].done").entity(Boolean.class).isEqualTo(true)
                .path("updateTarget.progress").entity(Double.class).isEqualTo(1d);
    }

    @Test
    @DisplayName("Can add, edit and delete checklist target tasks after creation")
    void canAddEditAndDeleteChecklistTargetTasksAfterCreation() {
        // Arrange
        String targetId = createChecklistTarget(goalId, "Prepare workspace");
        GraphQlTester.Response initial = getTargetItems(targetId);
        String firstItemId = initial.path("targetById.items[0].id").entity(String.class).get();
        String secondItemId = initial.path("targetById.items[1].id").entity(String.class).get();

        // Act + Assert - add one task
        GraphQlTester.Response added = graphQlTester.document("""
                        mutation($id: ID!, $firstItemId: ID!, $secondItemId: ID!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { id: $firstItemId, text: "Write requirements", done: true }
                              { id: $secondItemId, text: "Review validation", done: false }
                              { text: "Run build", done: false }
                            ]
                          }) {
                            items { id text done }
                            progress
                          }
                        }
                        """)
                .variable("id", targetId)
                .variable("firstItemId", firstItemId)
                .variable("secondItemId", secondItemId)
                .execute();

        String thirdItemId = added.path("updateTarget.items[2].id").entity(String.class).get();
        added
                .path("updateTarget.items").entityList(Object.class).hasSize(3)
                .path("updateTarget.items[2].text").entity(String.class).isEqualTo("Run build")
                .path("updateTarget.progress").entity(Double.class)
                .satisfies(progress -> assertThat(progress).isCloseTo(1d / 3d, offset(0.000001d)));

        // Act + Assert - edit task text and done state
        graphQlTester.document("""
                        mutation($id: ID!, $firstItemId: ID!, $secondItemId: ID!, $thirdItemId: ID!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { id: $firstItemId, text: "Write requirements", done: true }
                              { id: $secondItemId, text: "Review validation carefully", done: true }
                              { id: $thirdItemId, text: "Run build", done: false }
                            ]
                          }) {
                            items { id text done }
                            progress
                          }
                        }
                        """)
                .variable("id", targetId)
                .variable("firstItemId", firstItemId)
                .variable("secondItemId", secondItemId)
                .variable("thirdItemId", thirdItemId)
                .execute()
                .path("updateTarget.items[1].text").entity(String.class).isEqualTo("Review validation carefully")
                .path("updateTarget.items[1].done").entity(Boolean.class).isEqualTo(true)
                .path("updateTarget.progress").entity(Double.class)
                .satisfies(progress -> assertThat(progress).isCloseTo(2d / 3d, offset(0.000001d)));

        // Act + Assert - delete tasks while leaving one task
        graphQlTester.document("""
                        mutation($id: ID!, $secondItemId: ID!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { id: $secondItemId, text: "Review validation carefully", done: true }
                            ]
                          }) {
                            items { id text done }
                            progress
                          }
                        }
                        """)
                .variable("id", targetId)
                .variable("secondItemId", secondItemId)
                .execute()
                .path("updateTarget.items").entityList(Object.class).hasSize(1)
                .path("updateTarget.items[0].id").entity(String.class).isEqualTo(secondItemId)
                .path("updateTarget.progress").entity(Double.class).isEqualTo(1d);
    }

    @Test
    @DisplayName("Returns ValidationError when updating checklist target to empty tasks")
    void returnsErrorWhenUpdatingChecklistTargetToEmptyTasks() {
        String targetId = createChecklistTarget(goalId, "Prepare workspace");

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: { items: [] }) {
                            id
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute();

        assertValidationError(response, "Checklist target requires at least one item");
        getTargetItems(targetId)
                .path("targetById.items").entityList(Object.class).hasSize(2);
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when updating checklist target with non-existent task id")
    void returnsErrorWhenUpdatingChecklistTargetWithNonExistentTaskId() {
        String targetId = createChecklistTarget(goalId, "Prepare workspace");

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $itemId: ID!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { id: $itemId, text: "Ghost task", done: false }
                            ]
                          }) {
                            id
                          }
                        }
                        """)
                .variable("id", targetId)
                .variable("itemId", NON_EXISTENT_ID)
                .execute();

        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Checklist item not found: " + NON_EXISTENT_ID) &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
        getTargetItems(targetId)
                .path("targetById.items").entityList(Object.class).hasSize(2);
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when updating checklist target with task id from another target")
    void returnsErrorWhenUpdatingChecklistTargetWithTaskIdFromAnotherTarget() {
        String targetId = createChecklistTarget(goalId, "Prepare workspace");
        String otherTargetId = createChecklistTarget(goalId, "Other checklist");
        String otherItemId = getTargetItems(otherTargetId)
                .path("targetById.items[0].id").entity(String.class).get();

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $otherItemId: ID!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { id: $otherItemId, text: "Wrong task", done: false }
                            ]
                          }) {
                            id
                          }
                        }
                        """)
                .variable("id", targetId)
                .variable("otherItemId", otherItemId)
                .execute();

        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Checklist item not found: " + otherItemId) &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
        getTargetItems(targetId)
                .path("targetById.items").entityList(Object.class).hasSize(2);
    }

    @Test
    @DisplayName("Returns ValidationError when updating checklist target with blank task text")
    void returnsErrorWhenUpdatingChecklistTargetWithBlankTaskText() {
        String targetId = createChecklistTarget(goalId, "Prepare workspace");
        String itemId = getTargetItems(targetId)
                .path("targetById.items[0].id").entity(String.class).get();

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $itemId: ID!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { id: $itemId, text: "   ", done: false }
                            ]
                          }) {
                            id
                          }
                        }
                        """)
                .variable("id", targetId)
                .variable("itemId", itemId)
                .execute();

        assertValidationError(response, "Checklist item text cannot be blank");
        getTargetItems(targetId)
                .path("targetById.items[0].text").entity(String.class).isEqualTo("Write requirements");
    }

    @Test
    @DisplayName("Returns ValidationError when updating checklist target with duplicate task ids")
    void returnsErrorWhenUpdatingChecklistTargetWithDuplicateTaskIds() {
        String targetId = createChecklistTarget(goalId, "Prepare workspace");
        String itemId = getTargetItems(targetId)
                .path("targetById.items[0].id").entity(String.class).get();

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $itemId: ID!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { id: $itemId, text: "First", done: false }
                              { id: $itemId, text: "Second", done: true }
                            ]
                          }) {
                            id
                          }
                        }
                        """)
                .variable("id", targetId)
                .variable("itemId", itemId)
                .execute();

        assertValidationError(response, "Checklist item ids must be unique");
        getTargetItems(targetId)
                .path("targetById.items").entityList(Object.class).hasSize(2);
    }

    @Test
    @DisplayName("Returns ValidationError when updating non-checklist target with tasks")
    void returnsErrorWhenUpdatingNonChecklistTargetWithTasks() {
        String binaryTargetId = createBinaryTarget(goalId, "Book session", false);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { text: "Not allowed", done: false }
                            ]
                          }) {
                            id
                          }
                        }
                        """)
                .variable("id", binaryTargetId)
                .execute();

        assertValidationError(response, "Only checklist targets can have items");
    }

    @Test
    @DisplayName("Can set, change and clear binary target deadline with past, today and future dates")
    void updatesBinaryTargetDeadlineThroughPastTodayFutureAndClear() {
        // Arrange
        String targetId = createBinaryTarget(goalId, "Book session", false);

        // Act + Assert
        assertCanSetChangeAndClearDeadline(targetId);
    }

    @Test
    @DisplayName("Can set, change and clear numeric target deadline with past, today and future dates")
    void updatesNumericTargetDeadlineThroughPastTodayFutureAndClear() {
        // Arrange
        String targetId = createNumericTarget(goalId, "Read pages", 0d, 10d);

        // Act + Assert
        assertCanSetChangeAndClearDeadline(targetId);
    }

    @Test
    @DisplayName("Can set, change and clear checklist target deadline with past, today and future dates")
    void updatesChecklistTargetDeadlineThroughPastTodayFutureAndClear() {
        // Arrange
        String targetId = createChecklistTarget(goalId, "Prepare workspace");

        // Act + Assert
        assertCanSetChangeAndClearDeadline(targetId);
    }

    @Test
    @DisplayName("Can set, change and clear checklist task deadline with past, today and future dates")
    void updatesChecklistTaskDeadlineThroughPastTodayFutureAndClear() {
        String targetId = createChecklistTarget(goalId, "Prepare workspace");
        String firstItemId = getFirstChecklistItemId(targetId);

        String past = deadlineAtStartOfDay(LocalDate.now(ZoneOffset.UTC).minusDays(1));
        String today = deadlineAtStartOfDay(LocalDate.now(ZoneOffset.UTC));
        String future = deadlineAtStartOfDay(LocalDate.now(ZoneOffset.UTC).plusDays(1));

        updateChecklistTaskDeadline(targetId, firstItemId, past);
        updateChecklistTaskDeadline(targetId, firstItemId, today);
        updateChecklistTaskDeadline(targetId, firstItemId, future);
        clearChecklistTaskDeadline(targetId, firstItemId);
    }

    @ParameterizedTest(name = "Returns ValidationError when updating target with invalid deadline: {0}")
    @ValueSource(strings = {"not-a-date", "2026-12-31"})
    void returnsErrorWhenUpdatingTargetWithInvalidDeadline(String deadline) {
        // Arrange
        String targetId = createBinaryTarget(goalId, "Book session", false);

        // Act + Assert
        assertUpdateTargetWithInvalidDeadlineReturnsValidationError(targetId, deadline);
    }

    @ParameterizedTest(name = "Returns ValidationError when updating checklist task with invalid deadline: {0}")
    @ValueSource(strings = {"not-a-date", "2026-12-31"})
    void returnsErrorWhenUpdatingChecklistTaskWithInvalidDeadline(String deadline) {
        String targetId = createChecklistTarget(goalId, "Prepare workspace");
        String firstItemId = getFirstChecklistItemId(targetId);

        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $itemId: ID!, $deadline: String!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { id: $itemId, text: "Write requirements", done: true, deadline: $deadline }
                              { text: "Review validation", done: false }
                            ]
                          }) {
                            id
                          }
                        }
                        """)
                .variable("id", targetId)
                .variable("itemId", firstItemId)
                .variable("deadline", deadline)
                .execute();

        assertValidationError(response, "Invalid date format");
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when updating non-existent target")
    void returnsErrorWhenUpdatingNonExistentTarget() {
        // Arrange
        // No Arrange needed - we are testing error handling for a non-existent target

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: { done: true }) { id }
                        }
                        """)
                .variable("id", NON_EXISTENT_ID)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Target not found") &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    // --- deleteTarget --------------------------------------------------------

    @Test
    @DisplayName("Deletes binary target and returns true - target is gone from goal")
    void deletesBinaryTarget() {
        // Arrange
        String targetId = createBinaryTarget(goalId, "Target to delete", false);

        // Act
        deleteTarget(targetId);

        // Assert
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Deletes numeric target and returns true - target is gone from goal")
    void deletesNumericTarget() {
        // Arrange
        String targetId = createNumericTarget(goalId, "Read pages", 0d, 10d);

        // Act
        deleteTarget(targetId);

        // Assert
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Deletes checklist target and returns true - target is gone from goal")
    void deletesChecklistTarget() {
        // Arrange
        String targetId = createChecklistTarget(goalId, "Prepare workspace");

        // Act
        deleteTarget(targetId);

        // Assert
        assertNoTargetsCreated();
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when deleting non-existent target")
    void returnsErrorWhenDeletingNonExistentTarget() {
        // Arrange
        // No Arrange needed - we are testing error handling for a non-existent target

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          deleteTarget(id: $id)
                        }
                        """)
                .variable("id", NON_EXISTENT_ID)
                .execute();

        // Assert
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Target not found") &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    // --- goal progress -------------------------------------------------------

    @Test
    @DisplayName("Goal progress increases when binary target is toggled to done")
    void goalProgressIncreasesWhenBinaryTargetIsToggledToDone() {
        // Arrange
        createBinaryTarget(goalId, "Book session", false);

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        query($goalId: ID!) {
                          goalById(id: $goalId) {
                            progress
                            targets { id title done progress }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert - progress should be 0 before toggle
        response
                .path("goalById.progress").entity(Double.class).isEqualTo(0d)
                .path("goalById.targets[0].done").entity(Boolean.class).isEqualTo(false)
                .path("goalById.targets[0].progress").entity(Double.class).isEqualTo(0d);

        // Act 2 - toggle to done
        String targetId = response.path("goalById.targets[0].id").entity(String.class).get();
        updateBinaryTargetDone(targetId, true);

        // Assert 2 - goal progress should now be 1
        graphQlTester.document("""
                        query($goalId: ID!) {
                          goalById(id: $goalId) {
                            progress
                            targets { id title done progress }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(1d)
                .path("goalById.targets[0].done").entity(Boolean.class).isEqualTo(true)
                .path("goalById.targets[0].progress").entity(Double.class).isEqualTo(1d);
    }

    @Test
    @DisplayName("Goal progress reaches 100% when all targets are completed, then drops when one is reverted")
    void goalProgressReachesFullThenDropsWhenTargetIsReverted() {
        String binaryId = createBinaryTarget(goalId, "Binary task", false);
        String numericId = createNumericTarget(goalId, "Numeric task", 0d, 10d);

        updateBinaryTargetDone(binaryId, true);
        updateNumericTargetCurrent(numericId, 10d);

        graphQlTester.document("""
                        query($goalId: ID!) {
                          goalById(id: $goalId) { progress }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(1d);

        updateBinaryTargetDone(binaryId, false);

        graphQlTester.document("""
                        query($goalId: ID!) {
                          goalById(id: $goalId) { progress }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(0.5d);
    }

    @Test
    @DisplayName("Goal progress reaches 100% when single numeric target reaches its total")
    void goalProgressReachesOneHundredPercentWithSingleNumericTarget() {
        String numericId = createNumericTarget(goalId, "Read pages", 0d, 10d);
        updateNumericTargetCurrent(numericId, 10d);

        graphQlTester.document("""
                        query($goalId: ID!) {
                          goalById(id: $goalId) { progress }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(1d);
    }

    @Test
    @DisplayName("Goal progress reaches 100% when single checklist target has all items done")
    void goalProgressReachesOneHundredPercentWithSingleChecklistTarget() {
        GraphQlTester.Response createResponse = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: "Full checklist"
                            items: [{ text: "Step 1" }, { text: "Step 2" }]
                          }) { id items { id } }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        String checklistId = createResponse.path("createTarget.id").entity(String.class).get();
        String item1Id = createResponse.path("createTarget.items[0].id").entity(String.class).get();
        String item2Id = createResponse.path("createTarget.items[1].id").entity(String.class).get();

        graphQlTester.document("""
                        mutation($id: ID!, $i1: ID!, $i2: ID!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { id: $i1, text: "Step 1", done: true }
                              { id: $i2, text: "Step 2", done: true }
                            ]
                          }) { id }
                        }
                        """)
                .variable("id", checklistId)
                .variable("i1", item1Id)
                .variable("i2", item2Id)
                .execute();

        graphQlTester.document("""
                        query($goalId: ID!) {
                          goalById(id: $goalId) { progress }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(1d);
    }

    @Test
    @DisplayName("Goal progress drops when new incomplete binary target is added to a 100% goal")
    void goalProgressDropsWhenNewIncompleteBinaryTargetIsAdded() {
        String binaryId = createBinaryTarget(goalId, "Done task", false);
        updateBinaryTargetDone(binaryId, true);

        graphQlTester.document("""
                        query($goalId: ID!) { goalById(id: $goalId) { progress } }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(1d);

        createBinaryTarget(goalId, "Not yet done", false);

        graphQlTester.document("""
                        query($goalId: ID!) { goalById(id: $goalId) { progress } }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(0.5d);
    }

    @Test
    @DisplayName("Goal progress drops when new incomplete numeric target is added to a 100% goal")
    void goalProgressDropsWhenNewIncompleteNumericTargetIsAdded() {
        String binaryId = createBinaryTarget(goalId, "Done task", false);
        updateBinaryTargetDone(binaryId, true);

        graphQlTester.document("""
                        query($goalId: ID!) { goalById(id: $goalId) { progress } }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(1d);

        createNumericTarget(goalId, "Pages to read", 0d, 10d);

        graphQlTester.document("""
                        query($goalId: ID!) { goalById(id: $goalId) { progress } }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(0.5d);
    }

    @Test
    @DisplayName("Goal progress drops when new incomplete checklist target is added to a 100% goal")
    void goalProgressDropsWhenNewIncompleteChecklistTargetIsAdded() {
        String binaryId = createBinaryTarget(goalId, "Done task", false);
        updateBinaryTargetDone(binaryId, true);

        graphQlTester.document("""
                        query($goalId: ID!) { goalById(id: $goalId) { progress } }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(1d);

        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: "Not started yet"
                            items: [{ text: "Step 1" }, { text: "Step 2" }]
                          }) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        graphQlTester.document("""
                        query($goalId: ID!) { goalById(id: $goalId) { progress } }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(0.5d);
    }

    @Test
    @DisplayName("Goal progress drops when a checklist item is unchecked from a 100% goal")
    void goalProgressDropsWhenChecklistItemIsUnchecked() {
        GraphQlTester.Response createResponse = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: "Full checklist"
                            items: [
                              { text: "Step 1", done: true }
                              { text: "Step 2", done: true }
                            ]
                          }) { id items { id } }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        String checklistId = createResponse.path("createTarget.id").entity(String.class).get();
        String item1Id = createResponse.path("createTarget.items[0].id").entity(String.class).get();
        String item2Id = createResponse.path("createTarget.items[1].id").entity(String.class).get();

        graphQlTester.document("""
                        query($goalId: ID!) { goalById(id: $goalId) { progress } }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(1d);

        graphQlTester.document("""
                        mutation($id: ID!, $i1: ID!, $i2: ID!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { id: $i1, text: "Step 1", done: true }
                              { id: $i2, text: "Step 2", done: false }
                            ]
                          }) { id }
                        }
                        """)
                .variable("id", checklistId)
                .variable("i1", item1Id)
                .variable("i2", item2Id)
                .execute();

        graphQlTester.document("""
                        query($goalId: ID!) { goalById(id: $goalId) { progress } }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(0.5d);
    }

    @Test
    @DisplayName("Goal progress recalculates after target deletion")
    void goalProgressRecalculatesAfterTargetDeletion() {
        String binaryId = createBinaryTarget(goalId, "Done task", false);
        updateBinaryTargetDone(binaryId, true);

        String checklistId = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: "Not started yet"
                            items: [{ text: "Step 1" }, { text: "Step 2" }]
                          }) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("createTarget.id").entity(String.class).get();

        graphQlTester.document("""
                        query($goalId: ID!) { goalById(id: $goalId) { progress } }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(0.5d);

        deleteTarget(checklistId);

        graphQlTester.document("""
                        query($goalId: ID!) { goalById(id: $goalId) { progress } }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(1d);
    }

    @Test
    @DisplayName("Goal progress is average of all target progress values")
    void goalProgressIsAverageOfAllTargets() {
        // Arrange - numeric 50%, binary 0%, checklist 50% -> average 1/3
        String numericTargetId = createNumericTarget(goalId, "Read pages", 0d, 10d);
        updateNumericTargetCurrent(numericTargetId, 5d);
        createBinaryTarget(goalId, "Book session", false);
        createChecklistTarget(goalId, "Prepare workspace");

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        query($goalId: ID!) {
                          goalById(id: $goalId) {
                            progress
                            targets { title progress }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        response
                .path("goalById.targets").entityList(Object.class).hasSize(3)
                .path("goalById.progress").entity(Double.class)
                .satisfies(p -> assertThat(p).isCloseTo(1d / 3d, offset(0.000001d)));
    }

    // --- helpers -------------------------------------------------------------

    private String createBinaryTarget(String goalId, String title, boolean done) {
        return graphQlTester.document("""
                        mutation($goalId: ID!, $title: String!, $done: Boolean!) {
                          createTarget(goalId: $goalId, input: {
                            type: "binary"
                            title: $title
                            done: $done
                          }) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", title)
                .variable("done", done)
                .execute()
                .path("createTarget.id").entity(String.class).get();
    }

    private String createNumericTarget(String goalId, String title, double start, double total) {
        return graphQlTester.document("""
                        mutation($goalId: ID!, $title: String!, $start: Float!, $total: Float!) {
                          createTarget(goalId: $goalId, input: {
                            type: "numeric"
                            title: $title
                            start: $start
                            total: $total
                          }) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", title)
                .variable("start", start)
                .variable("total", total)
                .execute()
                .path("createTarget.id").entity(String.class).get();
    }

    private void updateNumericTargetCurrent(String targetId, double current) {
        graphQlTester.document("""
                        mutation($id: ID!, $current: Float!) {
                          updateTarget(id: $id, input: { current: $current }) { id }
                        }
                        """)
                .variable("id", targetId)
                .variable("current", current)
                .execute()
                .path("updateTarget.id").hasValue();
    }

    private void updateBinaryTargetDone(String targetId, boolean done) {
        graphQlTester.document("""
                        mutation($id: ID!, $done: Boolean!) {
                          updateTarget(id: $id, input: { done: $done }) { id }
                        }
                        """)
                .variable("id", targetId)
                .variable("done", done)
                .execute()
                .path("updateTarget.id").hasValue();
    }

    private void assertCanSetChangeAndClearDeadline(String targetId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String pastDeadline = deadlineAtStartOfDay(today.minusDays(1));
        String todayDeadline = deadlineAtStartOfDay(today);
        String futureDeadline = deadlineAtStartOfDay(today.plusDays(1));

        updateTargetDeadline(targetId, pastDeadline);
        updateTargetDeadline(targetId, todayDeadline);
        updateTargetDeadline(targetId, futureDeadline);
        clearTargetDeadline(targetId);
    }

    private String deadlineAtStartOfDay(LocalDate date) {
        return date.atStartOfDay().toInstant(ZoneOffset.UTC).toString();
    }

    private void updateTargetDeadline(String targetId, String deadline) {
        graphQlTester.document("""
                        mutation($id: ID!, $deadline: String!) {
                          updateTarget(id: $id, input: { deadline: $deadline }) {
                            id deadline
                          }
                        }
                        """)
                .variable("id", targetId)
                .variable("deadline", deadline)
                .execute()
                .path("updateTarget.id").entity(String.class).isEqualTo(targetId)
                .path("updateTarget.deadline").entity(String.class).isEqualTo(deadline);
    }

    private void assertCreateTargetWithInvalidDeadlineReturnsValidationError(String deadline) {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $deadline: String!) {
                          createTarget(goalId: $goalId, input: {
                            type: "binary"
                            title: "Book session"
                            deadline: $deadline
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("deadline", deadline)
                .execute();

        assertValidationError(response, "Invalid date format");
    }

    private void assertUpdateTargetWithInvalidDeadlineReturnsValidationError(String targetId, String deadline) {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!, $deadline: String!) {
                          updateTarget(id: $id, input: { deadline: $deadline }) {
                            id
                          }
                        }
                        """)
                .variable("id", targetId)
                .variable("deadline", deadline)
                .execute();

        assertValidationError(response, "Invalid date format");
    }

    private void assertCreateNumericTargetValidationError(double start, double total, String message) {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $start: Float!, $total: Float!) {
                          createTarget(goalId: $goalId, input: {
                            type: "numeric"
                            title: "Read pages"
                            start: $start
                            total: $total
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("start", start)
                .variable("total", total)
                .execute();

        assertValidationError(response, message);
    }

    private void assertUpdateTargetValidationError(String targetId, String inputFields, String message) {
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: { %s }) {
                            id
                          }
                        }
                        """.formatted(inputFields))
                .variable("id", targetId)
                .execute();

        assertValidationError(response, message);
    }

    private GraphQlTester.Response getTargetItems(String targetId) {
        return graphQlTester.document("""
                        query($id: ID!) {
                          targetById(id: $id) {
                            items { id text done }
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute();
    }

    private String getFirstChecklistItemId(String targetId) {
        return graphQlTester.document("""
                        query($id: ID!) {
                          targetById(id: $id) {
                            items { id }
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute()
                .path("targetById.items[0].id").entity(String.class).get();
    }

    private void updateChecklistTaskDeadline(String targetId, String itemId, String deadline) {
        graphQlTester.document("""
                        mutation($id: ID!, $itemId: ID!, $deadline: String!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { id: $itemId, text: "Write requirements", done: true, deadline: $deadline }
                              { text: "Review validation", done: false }
                            ]
                          }) {
                            items { id deadline }
                          }
                        }
                        """)
                .variable("id", targetId)
                .variable("itemId", itemId)
                .variable("deadline", deadline)
                .execute()
                .path("updateTarget.items[0].deadline").entity(String.class).isEqualTo(deadline);
    }

    private void clearChecklistTaskDeadline(String targetId, String itemId) {
        graphQlTester.document("""
                        mutation($id: ID!, $itemId: ID!) {
                          updateTarget(id: $id, input: {
                            items: [
                              { id: $itemId, text: "Write requirements", done: true, deadline: null }
                              { text: "Review validation", done: false }
                            ]
                          }) {
                            items { id deadline }
                          }
                        }
                        """)
                .variable("id", targetId)
                .variable("itemId", itemId)
                .execute()
                .path("updateTarget.items[0].deadline").valueIsNull();
    }

    private void clearTargetDeadline(String targetId) {
        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateTarget(id: $id, input: { deadline: null }) {
                            id deadline
                          }
                        }
                        """)
                .variable("id", targetId)
                .execute()
                .path("updateTarget.id").entity(String.class).isEqualTo(targetId)
                .path("updateTarget.deadline").valueIsNull();
    }

    private String createChecklistTarget(String goalId, String title) {
        return graphQlTester.document("""
                        mutation($goalId: ID!, $title: String!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: $title
                            items: [
                              { text: "Write requirements", done: true }
                              { text: "Review validation", done: false }
                            ]
                          }) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", title)
                .execute()
                .path("createTarget.id").entity(String.class).get();
    }

    private void deleteTarget(String targetId) {
        graphQlTester.document("""
                        mutation($id: ID!) {
                          deleteTarget(id: $id)
                        }
                        """)
                .variable("id", targetId)
                .execute()
                .path("deleteTarget").entity(Boolean.class).isEqualTo(true);
    }

    private void assertValidationError(GraphQlTester.Response response, String message) {
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains(message) &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    private void assertGraphQlValidationError(GraphQlTester.Response response, String... messageParts) {
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                "ValidationError".equals(error.getErrorType().toString()) &&
                                Arrays.stream(messageParts)
                                        .allMatch(part -> error.getMessage().contains(part))));
    }

    private void assertNoTargetsCreated() {
        graphQlTester.document("""
                        query($goalId: ID!) {
                          targetsByGoal(goalId: $goalId) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("targetsByGoal").entityList(Object.class).hasSize(0);
    }
}
