package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalService;
import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.test.tester.GraphQlTester;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * **A user may have only so many goals in motion at once** (owner, 2026-08-25).
 *
 * <p>Nothing used to stop a user — or a looping assistant, or a script — from creating goals
 * without end, and the dashboard loads every one of them. Measured on the owner's machine: 598
 * goals made the goals query 162 KB and up to two seconds cold, and the Playwright suite started
 * failing on animations that no longer had time to settle.
 *
 * <p>Two things are worth asserting through GraphQL rather than only in {@code GoalServiceTest}:
 * that the refusal reaches the client as a **ValidationError with a readable sentence** (an
 * {@code IllegalArgumentException} is passed through verbatim by {@code GraphQlExceptionHandler},
 * but only if nothing wraps it on the way), and that the cap is **per user** — one person filling
 * theirs must not lock anybody else out.
 */
class GoalCapIntegrationTest extends BaseGraphQlIntegrationTest {

    /** Fill the current user's allowance directly, rather than through 50 mutations. */
    private void fillGoalsInMotion(AppUser owner, int count) {
        for (int i = 0; i < count; i++) {
            Goal goal = new Goal();
            goal.setUser(owner);
            goal.setTitle("In motion " + i);
            goal.setDescription("");
            goal.setConfidence(5);
            goalRepository.save(goal);
        }
    }

    private GraphQlTester.Response createGoal(String title) {
        return graphQlTester.document("""
                        mutation($title: String!) {
                          createGoal(input: { title: $title, confidence: 5 }) { id title }
                        }
                        """)
                .variable("title", title)
                .execute();
    }

    @Test
    @DisplayName("the goal past the cap is refused, with a sentence the user can act on")
    void refusesTheGoalPastTheCap() {
        fillGoalsInMotion(testUser, GoalService.MAX_ACTIVE_GOALS);

        GraphQlTester.Response response = createGoal("One too many");

        response.errors().satisfy(errors -> assertThat(errors)
                .anyMatch(error ->
                        error.getMessage().contains("50 goals in motion")
                                && error.getMessage().contains("Achieve or delete one")
                                && "ValidationError".equals(error.getExtensions().get("classification"))));
        assertThat(goalRepository.findByUserIdOrderByCreatedAtAsc(testUser.getId()))
                .as("nothing may be written when the cap refuses")
                .hasSize(GoalService.MAX_ACTIVE_GOALS);
    }

    @Test
    @DisplayName("one below the cap still goes through")
    void allowsTheLastOne() {
        fillGoalsInMotion(testUser, GoalService.MAX_ACTIVE_GOALS - 1);

        createGoal("The fiftieth").path("createGoal.title").entity(String.class)
                .isEqualTo("The fiftieth");
    }

    @Test
    @DisplayName("achieving a goal makes room — the cap counts what is in motion, not a lifetime")
    void achievingOneMakesRoom() {
        // The distinction the owner asked for: a person who achieves a lot must never be the one
        // who runs out of room, or the only way forward is deleting their own history.
        fillGoalsInMotion(testUser, GoalService.MAX_ACTIVE_GOALS);
        Goal done = goalRepository.findByUserIdOrderByCreatedAtAsc(testUser.getId()).get(0);
        done.setAchievedAt(Instant.now());
        goalRepository.save(done);

        createGoal("Room again").path("createGoal.title").entity(String.class)
                .isEqualTo("Room again");
    }

    @Test
    @DisplayName("the cap is per user — a full account does not block anybody else")
    void theCapIsPerUser() {
        AppUser other = createAdditionalUser("other-sub", "other@example.com");
        fillGoalsInMotion(other, GoalService.MAX_ACTIVE_GOALS);

        // testUser is still the current user and has none of their own.
        createGoal("Mine").path("createGoal.title").entity(String.class).isEqualTo("Mine");
    }
}
