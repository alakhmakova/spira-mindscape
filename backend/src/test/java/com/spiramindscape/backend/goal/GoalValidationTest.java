package com.spiramindscape.backend.goal;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GoalValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("goalValidationCases")
    void validatesGoalFields(String caseName, Consumer<Goal> mutateGoal, String field, boolean expectedViolation) {
        Goal goal = validGoal();
        mutateGoal.accept(goal);

        assertThat(hasViolationOn(goal, field)).isEqualTo(expectedViolation);
    }

    private static Stream<Arguments> goalValidationCases() {
        return Stream.of(
                Arguments.of("Rejects null title",
                        (Consumer<Goal>) goal -> goal.setTitle(null), "title", true),
                Arguments.of("Rejects empty title",
                        (Consumer<Goal>) goal -> goal.setTitle(""), "title", true),
                Arguments.of("Rejects blank title",
                        (Consumer<Goal>) goal -> goal.setTitle("   "), "title", true),
                Arguments.of("Accepts title at the maximum length (" + GoalService.MAX_GOAL_TITLE_LENGTH + " characters)",
                        (Consumer<Goal>) goal -> goal.setTitle("A".repeat(GoalService.MAX_GOAL_TITLE_LENGTH)), "title", false),
                Arguments.of("Rejects title one character over the maximum length",
                        (Consumer<Goal>) goal -> goal.setTitle("A".repeat(GoalService.MAX_GOAL_TITLE_LENGTH + 1)), "title", true),
                Arguments.of("Accepts null description",
                        (Consumer<Goal>) goal -> goal.setDescription(null), "description", false),
                Arguments.of("Accepts empty description",
                        (Consumer<Goal>) goal -> goal.setDescription(""), "description", false),
                Arguments.of("Accepts description at the maximum length (" + GoalService.MAX_GOAL_DESCRIPTION_LENGTH + " characters)",
                        (Consumer<Goal>) goal -> goal.setDescription("A".repeat(GoalService.MAX_GOAL_DESCRIPTION_LENGTH)), "description", false),
                Arguments.of("Rejects description one character over the maximum length",
                        (Consumer<Goal>) goal -> goal.setDescription("A".repeat(GoalService.MAX_GOAL_DESCRIPTION_LENGTH + 1)), "description", true),
                Arguments.of("Rejects null confidence",
                        (Consumer<Goal>) goal -> goal.setConfidence(null), "confidence", true),
                Arguments.of("Rejects confidence of 0",
                        (Consumer<Goal>) goal -> goal.setConfidence(0), "confidence", true),
                Arguments.of("Rejects negative confidence",
                        (Consumer<Goal>) goal -> goal.setConfidence(-1), "confidence", true),
                Arguments.of("Accepts confidence of 1",
                        (Consumer<Goal>) goal -> goal.setConfidence(1), "confidence", false),
                Arguments.of("Accepts confidence of 10",
                        (Consumer<Goal>) goal -> goal.setConfidence(10), "confidence", false),
                Arguments.of("Rejects confidence of 11",
                        (Consumer<Goal>) goal -> goal.setConfidence(11), "confidence", true),
                Arguments.of("Accepts null deadline",
                        (Consumer<Goal>) goal -> goal.setDeadline(null), "deadline", false)
        );
    }

    private static Goal validGoal() {
        Goal goal = new Goal();
        goal.setTitle("Learn GraphQL");
        goal.setConfidence(5);
        return goal;
    }

    private static boolean hasViolationOn(Goal goal, String field) {
        Set<ConstraintViolation<Goal>> violations = validator.validate(goal);
        return violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }
}
