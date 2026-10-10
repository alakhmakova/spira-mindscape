package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Upcoming column's events: a date of the user's own, or a link to a goal, a target or a
 * checklist item. Covers the shapes, the validation, the cascade, the deadline that goes with a
 * deleted link — and the boundary the feature creates: one user cannot link to, read or delete
 * another user's events or items.
 */
class UpcomingEventIntegrationTest extends BaseGraphQlIntegrationTest {

    private static final String DEADLINE = "2026-12-31T00:00:00Z";

    private String goalId;
    private String targetId;
    private String taskId;

    @BeforeEach
    void seed() {
        goalId = graphQlTester.document("""
                        mutation { createGoal(input: { title: "Run a marathon", confidence: 6,
                                                       deadline: "%s" }) { id } }
                        """.formatted(DEADLINE))
                .execute()
                .path("createGoal.id").entity(String.class).get();
        targetId = graphQlTester.document("""
                        mutation($g: ID!) {
                          createTarget(goalId: $g, input: { type: "checklist", title: "Training plan",
                                                            deadline: "%s",
                                                            items: [{ text: "Long run", deadline: "%s" }] }) {
                            id items { id }
                          }
                        }
                        """.formatted(DEADLINE, DEADLINE))
                .variable("g", goalId)
                .execute()
                .path("createTarget.id").entity(String.class).get();
        taskId = graphQlTester.document("{ targetById(id: %s) { items { id } } }".formatted(targetId))
                .execute()
                .path("targetById.items[0].id").entity(String.class).get();
    }

    private String create(String fields) {
        return graphQlTester.document("mutation { createUpcomingEvent(input: { %s }) { id } }".formatted(fields))
                .execute()
                .path("createUpcomingEvent.id").entity(String.class).get();
    }

    private void expectError(String fields, String messagePart) {
        graphQlTester.document("mutation { createUpcomingEvent(input: { %s }) { id } }".formatted(fields))
                .execute()
                .errors().satisfy(errors -> assertThat(errors)
                        .anyMatch(e -> e.getMessage().contains(messagePart)));
    }

    @Test
    @DisplayName("An important date keeps its own name and date")
    void importantDate() {
        create("kind: \"important\", title: \"  Mom's birthday  \", date: \"2026-11-02T00:00:00Z\"");

        graphQlTester.document("{ upcomingEvents { kind title date goalId targetId taskId } }")
                .execute()
                .path("upcomingEvents").entityList(Object.class).hasSize(1)
                .path("upcomingEvents[0].kind").entity(String.class).isEqualTo("important")
                .path("upcomingEvents[0].title").entity(String.class).isEqualTo("Mom's birthday")
                .path("upcomingEvents[0].date").entity(String.class).isEqualTo("2026-11-02T00:00:00Z")
                .path("upcomingEvents[0].goalId").valueIsNull();
    }

    @Test
    @DisplayName("A link names what it points at, and carries no title or date of its own")
    void links() {
        create("kind: \"goal\", goalId: " + goalId);
        create("kind: \"target\", targetId: " + targetId);
        create("kind: \"task\", taskId: " + taskId);

        graphQlTester.document("{ upcomingEvents { kind title date goalId targetId taskId } }")
                .execute()
                .path("upcomingEvents").entityList(Object.class).hasSize(3)
                .path("upcomingEvents[0].goalId").entity(String.class).isEqualTo(goalId)
                .path("upcomingEvents[0].title").valueIsNull()
                .path("upcomingEvents[0].date").valueIsNull()
                .path("upcomingEvents[1].targetId").entity(String.class).isEqualTo(targetId)
                .path("upcomingEvents[2].taskId").entity(String.class).isEqualTo(taskId);
    }

    @Test
    @DisplayName("Invalid input is refused")
    void validation() {
        expectError("kind: \"important\", title: \"   \", date: \"2026-11-02T00:00:00Z\"", "Event name is required");
        expectError("kind: \"important\", title: \"Trip\"", "Event date is required");
        expectError("kind: \"important\", title: \"" + "x".repeat(201) + "\", date: \"2026-11-02T00:00:00Z\"",
                "200 characters");
        expectError("kind: \"recurring\", title: \"Trip\"", "Unknown event type");
        expectError("kind: \"goal\"", "goalId is required");
    }

    @Test
    @DisplayName("The same item cannot be added twice")
    void noDuplicateLinks() {
        create("kind: \"goal\", goalId: " + goalId);
        expectError("kind: \"goal\", goalId: " + goalId, "already in Upcoming");
    }

    @Test
    @DisplayName("Deleting a link clears the deadline it stood for")
    void deletingALinkClearsTheDeadline() {
        String goalEvent = create("kind: \"goal\", goalId: " + goalId);
        String taskEvent = create("kind: \"task\", taskId: " + taskId);

        graphQlTester.document("mutation { deleteUpcomingEvent(id: %s) }".formatted(goalEvent)).execute()
                .path("deleteUpcomingEvent").entity(Boolean.class).isEqualTo(true);
        graphQlTester.document("mutation { deleteUpcomingEvent(id: %s) }".formatted(taskEvent)).execute();

        graphQlTester.document("{ goalById(id: %s) { deadline targets { deadline items { deadline } } } }"
                        .formatted(goalId))
                .execute()
                .path("goalById.deadline").valueIsNull()
                // Only the task was unlinked inside the target — the target's own deadline stays.
                .path("goalById.targets[0].deadline").entity(String.class).isEqualTo(DEADLINE)
                .path("goalById.targets[0].items[0].deadline").valueIsNull();
        graphQlTester.document("{ upcomingEvents { id } }").execute()
                .path("upcomingEvents").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Deleting the goal takes its linked events with it")
    void cascadeOnGoalDelete() {
        create("kind: \"goal\", goalId: " + goalId);
        create("kind: \"target\", targetId: " + targetId);
        create("kind: \"task\", taskId: " + taskId);
        create("kind: \"important\", title: \"Trip\", date: \"2026-11-02T00:00:00Z\"");

        graphQlTester.document("mutation { deleteGoal(id: %s) }".formatted(goalId)).execute();

        graphQlTester.document("{ upcomingEvents { kind } }").execute()
                .path("upcomingEvents").entityList(Object.class).hasSize(1)
                .path("upcomingEvents[0].kind").entity(String.class).isEqualTo("important");
    }

    // ─── the boundary: another user's data ───────────────────────────────────

    @Test
    @DisplayName("User B cannot link to user A's goal, target or task")
    void cannotLinkAnotherUsersItems() {
        AppUser userB = createAdditionalUser("user-b-sub", "userB@example.com");
        setCurrentUser(userB);

        expectError("kind: \"goal\", goalId: " + goalId, "not found");
        expectError("kind: \"target\", targetId: " + targetId, "not found");
        expectError("kind: \"task\", taskId: " + taskId, "not found");
    }

    @Test
    @DisplayName("User B neither sees nor deletes user A's events — and A's deadline survives")
    void cannotReadOrDeleteAnotherUsersEvents() {
        String eventId = create("kind: \"goal\", goalId: " + goalId);

        AppUser userB = createAdditionalUser("user-b-sub", "userB@example.com");
        setCurrentUser(userB);
        graphQlTester.document("{ upcomingEvents { id } }").execute()
                .path("upcomingEvents").entityList(Object.class).hasSize(0);
        graphQlTester.document("mutation { deleteUpcomingEvent(id: %s) }".formatted(eventId))
                .execute()
                .errors().satisfy(errors -> assertThat(errors)
                        .anyMatch(e -> e.getMessage().contains("not found")
                                || e.getErrorType().toString().contains("NOT_FOUND")));

        setCurrentUser(testUser);
        graphQlTester.document("{ upcomingEvents { id } }").execute()
                .path("upcomingEvents").entityList(Object.class).hasSize(1);
        graphQlTester.document("{ goalById(id: %s) { deadline } }".formatted(goalId)).execute()
                .path("goalById.deadline").entity(String.class).isEqualTo(DEADLINE);
    }
}
