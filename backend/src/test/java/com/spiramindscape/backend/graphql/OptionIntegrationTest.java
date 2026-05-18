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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
class OptionIntegrationTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private GoalRepository goalRepository;

    private String goalId;

    @BeforeEach
    void createGoal() {
        goalId = graphQlTester.document("""
                        mutation {
                          createGoal(input: { title: "Test goal for options", confidence: 5 }) {
                            id
                          }
                        }
                        """)
                .execute()
                .path("createGoal.id").entity(String.class).get();
    }

    @AfterEach
    void cleanDatabase() {
        goalRepository.deleteAll();
    }

    // --- add option ----------------------------------------------------------

    @Test
    @DisplayName("Adds option to goal and returns it with selected=false by default")
    void addsOptionToGoal() {
        // Arrange
        String text = "Move to a different city";

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addOption(goalId: $goalId, text: $text) {
                            id
                            text
                            selected
                            position
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", text)
                .execute();

        // Assert
        response
                .path("addOption.id").hasValue()
                .path("addOption.text").entity(String.class).isEqualTo(text)
                .path("addOption.selected").entity(Boolean.class).isEqualTo(false)
                .path("addOption.position").entity(Integer.class).isEqualTo(0);
    }

    @Test
    @DisplayName("Allows multiple options to be added - each gets consecutive position")
    void allowsMultipleOptionsToBeAdded() {
        // Arrange
        String firstText  = "Move to a different city";
        String secondText = "Change careers";
        String thirdText  = "Start a side project";

        // Act
        addOption(goalId, firstText);
        addOption(goalId, secondText);
        addOption(goalId, thirdText);

        GraphQlTester.Response response = graphQlTester.document("""
                        query($goalId: ID!) {
                          optionsByGoal(goalId: $goalId) {
                            id
                            text
                            position
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert
        response
                .path("optionsByGoal").entityList(Object.class).hasSize(3)
                .path("optionsByGoal[0].text").entity(String.class).isEqualTo(firstText)
                .path("optionsByGoal[0].position").entity(Integer.class).isEqualTo(0)
                .path("optionsByGoal[1].text").entity(String.class).isEqualTo(secondText)
                .path("optionsByGoal[1].position").entity(Integer.class).isEqualTo(1)
                .path("optionsByGoal[2].text").entity(String.class).isEqualTo(thirdText)
                .path("optionsByGoal[2].position").entity(Integer.class).isEqualTo(2);
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when adding option to non-existent goal")
    void returnsErrorWhenAddingOptionToNonExistentGoal() {
        // Arrange
        // No Arrange needed - we are testing error handling for a non-existent goal

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation {
                          addOption(goalId: "999999", text: "Some option") {
                            id
                          }
                        }
                        """)
                .execute();

        // Assert - "Goal not found: 999999" -> contains "not found" -> NOT_FOUND
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Goal not found") &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    // --- optionsByGoal query -------------------------------------------------

    @Test
    @DisplayName("Returns empty list when goal has no options")
    void returnsEmptyListWhenGoalHasNoOptions() {
        // Arrange
        // No Arrange needed - goal is created in @BeforeEach with no options

        // Act + Assert
        graphQlTester.document("""
                        query($goalId: ID!) {
                          optionsByGoal(goalId: $goalId) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("optionsByGoal").entityList(Object.class).hasSize(0);
    }

    // --- selectOption --------------------------------------------------------

    @Test
    @DisplayName("selectOption marks the chosen option as selected")
    void selectOptionMarksItAsSelected() {
        // Arrange
        String optionId = addOption(goalId, "Move to a different city");

        // Act + Assert
        graphQlTester.document("""
                        mutation($goalId: ID!, $optionId: ID!) {
                          selectOption(goalId: $goalId, optionId: $optionId) {
                            id
                            selected
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionId", optionId)
                .execute()
                .path("selectOption.id").entity(String.class).isEqualTo(optionId)
                .path("selectOption.selected").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    @DisplayName("selectOption deselects all other options - only one can be active at a time")
    void selectOptionDeselectsAllOthers() {
        // Arrange
        String firstId  = addOption(goalId, "Move to a different city");
        String secondId = addOption(goalId, "Change careers");
        String thirdId  = addOption(goalId, "Start a side project");
        selectOption(goalId, firstId);

        // Act - select second option
        graphQlTester.document("""
                        mutation($goalId: ID!, $optionId: ID!) {
                          selectOption(goalId: $goalId, optionId: $optionId) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionId", secondId)
                .execute();

        // Assert - first and third must be false, second must be true
        graphQlTester.document("""
                        query($goalId: ID!) {
                          optionsByGoal(goalId: $goalId) { id selected }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("optionsByGoal[0].id").entity(String.class).isEqualTo(firstId)
                .path("optionsByGoal[0].selected").entity(Boolean.class).isEqualTo(false)
                .path("optionsByGoal[1].id").entity(String.class).isEqualTo(secondId)
                .path("optionsByGoal[1].selected").entity(Boolean.class).isEqualTo(true)
                .path("optionsByGoal[2].id").entity(String.class).isEqualTo(thirdId)
                .path("optionsByGoal[2].selected").entity(Boolean.class).isEqualTo(false);
    }

    @Test
    @DisplayName("selectOption called twice on the same option keeps it selected")
    void selectOptionIsIdempotent() {
        // Arrange
        String optionId = addOption(goalId, "Move to a different city");
        selectOption(goalId, optionId);

        // Act - second call on same option
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $optionId: ID!) {
                          selectOption(goalId: $goalId, optionId: $optionId) {
                            id
                            selected
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionId", optionId)
                .execute();

        // Assert
        response
                .path("selectOption.id").entity(String.class).isEqualTo(optionId)
                .path("selectOption.selected").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when selecting option with non-existent id")
    void returnsErrorWhenSelectingNonExistentOption() {
        // Arrange
        // No Arrange needed - we are testing error handling for a non-existent option

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          selectOption(goalId: $goalId, optionId: "999999") {
                            id
                            selected
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert - "Option not found: ..." -> contains "not found" -> NOT_FOUND
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Option not found") &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    @Test
    @DisplayName("Returns ValidationError when selecting option that belongs to another goal")
    void returnsErrorWhenSelectingOptionOfAnotherGoal() {
        // Arrange
        String otherGoalId = graphQlTester.document("""
                        mutation {
                          createGoal(input: { title: "Other goal", confidence: 3 }) { id }
                        }
                        """)
                .execute()
                .path("createGoal.id").entity(String.class).get();

        String optionId = addOption(otherGoalId, "An option belonging to another goal");

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $optionId: ID!) {
                          selectOption(goalId: $goalId, optionId: $optionId) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionId", optionId)
                .execute();

        // Assert - the option exists, but it is outside the requested goal.
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Option does not belong to goal") &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    // --- updateOption --------------------------------------------------------

    @Test
    @DisplayName("Deselects active option via updateOption with selected=false")
    void deselectsActiveOptionViaUpdate() {
        // Arrange
        String optionId = addOption(goalId, "Move to a different city");
        selectOption(goalId, optionId);

        // Act + Assert
        graphQlTester.document("""
                        mutation($goalId: ID!, $optionId: ID!) {
                          updateOption(goalId: $goalId, optionId: $optionId, input: { selected: false }) {
                            id
                            selected
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionId", optionId)
                .execute()
                .path("updateOption.id").entity(String.class).isEqualTo(optionId)
                .path("updateOption.selected").entity(Boolean.class).isEqualTo(false);
    }

    @Test
    @DisplayName("Updates text of an inactive option")
    void updatesTextOfInactiveOption() {
        // Arrange
        String optionId    = addOption(goalId, "Move to a different city");
        String updatedText = "Move to Berlin specifically";

        // Act + Assert
        graphQlTester.document("""
                        mutation($goalId: ID!, $optionId: ID!, $text: String!) {
                          updateOption(goalId: $goalId, optionId: $optionId, input: { text: $text }) {
                            id
                            text
                            selected
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionId", optionId)
                .variable("text", updatedText)
                .execute()
                .path("updateOption.id").entity(String.class).isEqualTo(optionId)
                .path("updateOption.text").entity(String.class).isEqualTo(updatedText)
                .path("updateOption.selected").entity(Boolean.class).isEqualTo(false);
    }

    @Test
    @DisplayName("Updates text of an active option - selection is preserved")
    void updatesTextOfActiveOption() {
        // Arrange
        String optionId    = addOption(goalId, "Move to a different city");
        selectOption(goalId, optionId);
        String updatedText = "Move to Berlin specifically";

        // Act + Assert
        graphQlTester.document("""
                        mutation($goalId: ID!, $optionId: ID!, $text: String!) {
                          updateOption(goalId: $goalId, optionId: $optionId, input: { text: $text }) {
                            id
                            text
                            selected
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionId", optionId)
                .variable("text", updatedText)
                .execute()
                .path("updateOption.text").entity(String.class).isEqualTo(updatedText)
                .path("updateOption.selected").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when updating option with non-existent id")
    void returnsErrorWhenUpdatingNonExistentOption() {
        // Arrange
        // No Arrange needed - we are testing error handling for a non-existent option

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          updateOption(goalId: $goalId, optionId: "999999", input: { text: "Updated" }) {
                            id text
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert - "Option not found: ..." -> contains "not found" -> NOT_FOUND
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Option not found") &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    @Test
    @DisplayName("Returns ValidationError when updating option that belongs to another goal")
    void returnsErrorWhenUpdatingOptionOfAnotherGoal() {
        // Arrange
        String otherGoalId = graphQlTester.document("""
                        mutation {
                          createGoal(input: { title: "Other goal", confidence: 3 }) { id }
                        }
                        """)
                .execute()
                .path("createGoal.id").entity(String.class).get();

        String optionId = addOption(otherGoalId, "An option belonging to another goal");

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $optionId: ID!) {
                          updateOption(goalId: $goalId, optionId: $optionId, input: { text: "Hijacked" }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionId", optionId)
                .execute();

        // Assert - "Option does not belong to goal" -> no "not found" -> ValidationError
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Option does not belong to goal") &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    // --- removeOption --------------------------------------------------------

    @Test
    @DisplayName("Removes inactive option and returns true")
    void removesInactiveOption() {
        // Arrange
        String optionId = addOption(goalId, "Move to a different city");

        // Act
        graphQlTester.document("""
                        mutation($goalId: ID!, $optionId: ID!) {
                          removeOption(goalId: $goalId, optionId: $optionId)
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionId", optionId)
                .execute()
                .path("removeOption").entity(Boolean.class).isEqualTo(true);

        // Assert - option is actually gone
        graphQlTester.document("""
                        query($goalId: ID!) { optionsByGoal(goalId: $goalId) { id } }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("optionsByGoal").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Removes active option and returns true")
    void removesActiveOption() {
        // Arrange
        String optionId = addOption(goalId, "Move to a different city");
        selectOption(goalId, optionId);

        // Act
        graphQlTester.document("""
                        mutation($goalId: ID!, $optionId: ID!) {
                          removeOption(goalId: $goalId, optionId: $optionId)
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionId", optionId)
                .execute()
                .path("removeOption").entity(Boolean.class).isEqualTo(true);

        // Assert - option is actually gone
        graphQlTester.document("""
                        query($goalId: ID!) { optionsByGoal(goalId: $goalId) { id } }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("optionsByGoal").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when removing option with non-existent id")
    void returnsErrorWhenRemovingNonExistentOption() {
        // Arrange
        // No Arrange needed - we are testing error handling for a non-existent option

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!) {
                          removeOption(goalId: $goalId, optionId: "999999")
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        // Assert - "Option not found: ..." -> contains "not found" -> NOT_FOUND
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Option not found") &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    // --- reorderOptions ------------------------------------------------------

    @Test
    @DisplayName("reorderOptions persists new order - returned list and subsequent query reflect new positions")
    void reorderOptionsPersistsNewOrder() {
        // Arrange
        String firstId  = addOption(goalId, "Move to a different city");
        String secondId = addOption(goalId, "Change careers");
        String thirdId  = addOption(goalId, "Start a side project");

        // Act - reverse order: third, first, second
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $optionIds: [ID!]!) {
                          reorderOptions(goalId: $goalId, optionIds: $optionIds) {
                            id
                            text
                            position
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionIds", List.of(thirdId, firstId, secondId))
                .execute();

        // Assert - mutation response reflects new order
        response
                .path("reorderOptions").entityList(Object.class).hasSize(3)
                .path("reorderOptions[0].id").entity(String.class).isEqualTo(thirdId)
                .path("reorderOptions[0].position").entity(Integer.class).isEqualTo(0)
                .path("reorderOptions[1].id").entity(String.class).isEqualTo(firstId)
                .path("reorderOptions[1].position").entity(Integer.class).isEqualTo(1)
                .path("reorderOptions[2].id").entity(String.class).isEqualTo(secondId)
                .path("reorderOptions[2].position").entity(Integer.class).isEqualTo(2);

        // Assert - order is persisted in subsequent query
        graphQlTester.document("""
                        query($goalId: ID!) {
                          optionsByGoal(goalId: $goalId) { id position }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("optionsByGoal[0].id").entity(String.class).isEqualTo(thirdId)
                .path("optionsByGoal[1].id").entity(String.class).isEqualTo(firstId)
                .path("optionsByGoal[2].id").entity(String.class).isEqualTo(secondId);
    }

    @Test
    @DisplayName("reorderOptions with empty list for goal with no options returns empty list")
    void reorderOptionsEmptyListForGoalWithNoOptions() {
        // Arrange
        // No Arrange needed - goal has no options

        // Act + Assert
        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          reorderOptions(goalId: $goalId, optionIds: []) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("reorderOptions").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Returns ValidationError when optionIds count does not match goal options count")
    void reorderOptionsRejectsWrongCount() {
        // Arrange
        String firstId  = addOption(goalId, "Move to a different city");
        String secondId = addOption(goalId, "Change careers");
        addOption(goalId, "Start a side project");
        // Three options exist, but we pass only two

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $optionIds: [ID!]!) {
                          reorderOptions(goalId: $goalId, optionIds: $optionIds) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionIds", List.of(firstId, secondId))
                .execute();

        // Assert - "Option ids list must contain all options..." -> ValidationError
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Option ids list must contain all options") &&
                                "ValidationError".equals(error.getExtensions().get("classification"))));
    }

    @Test
    @DisplayName("Returns NOT_FOUND error when optionId does not belong to goal")
    void reorderOptionsRejectsOptionFromAnotherGoal() {
        // Arrange
        String otherGoalId = graphQlTester.document("""
                        mutation {
                          createGoal(input: { title: "Other goal", confidence: 3 }) { id }
                        }
                        """)
                .execute()
                .path("createGoal.id").entity(String.class).get();

        String foreignOptionId = addOption(otherGoalId, "Foreign option");
        addOption(goalId, "Own option");
        // goalId has 1 option; we pass 1 id but it belongs to a different goal

        // Act
        GraphQlTester.Response response = graphQlTester.document("""
                        mutation($goalId: ID!, $optionIds: [ID!]!) {
                          reorderOptions(goalId: $goalId, optionIds: $optionIds) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionIds", List.of(foreignOptionId))
                .execute();

        // Assert - "Option not found or does not belong to goal: ..." -> NOT_FOUND
        response.errors()
                .satisfy(errors -> assertThat(errors)
                        .anyMatch(error ->
                                error.getMessage().contains("Option not found or does not belong to goal") &&
                                "NOT_FOUND".equals(error.getExtensions().get("classification"))));
    }

    // --- helpers -------------------------------------------------------------

    @Test
    void optionHasTimestamps() {
        graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addOption(goalId: $goalId, text: $text) {
                            id
                            createdAt
                            updatedAt
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", "Timestamp test option")
                .execute()
                .path("addOption.createdAt").hasValue()
                .path("addOption.updatedAt").hasValue()
                .path("addOption.createdAt").entity(String.class).satisfies(s -> assertThat(java.time.Instant.parse(s)).isNotNull())
                .path("addOption.updatedAt").entity(String.class).satisfies(s -> assertThat(java.time.Instant.parse(s)).isNotNull());
    }

    private String addOption(String goalId, String text) {
        return graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addOption(goalId: $goalId, text: $text) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", text)
                .execute()
                .path("addOption.id").entity(String.class).get();
    }

    private void selectOption(String goalId, String optionId) {
        graphQlTester.document("""
                        mutation($goalId: ID!, $optionId: ID!) {
                          selectOption(goalId: $goalId, optionId: $optionId) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("optionId", optionId)
                .execute();
    }
}
