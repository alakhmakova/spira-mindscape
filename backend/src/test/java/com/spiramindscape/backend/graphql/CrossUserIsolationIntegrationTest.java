package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that one authenticated user cannot read or modify another user's data.
 *
 * <p>Pattern used in every test:
 * <ol>
 *   <li>Act as <b>userA</b> (the default {@code testUser}) — create a goal.</li>
 *   <li>Switch to <b>userB</b> via {@link #setCurrentUser}.</li>
 *   <li>Try to access or mutate userA's goal — expect NOT_FOUND (not Forbidden,
 *       not the actual data).</li>
 * </ol>
 *
 * <p>Why NOT_FOUND and not 403?
 * Returning the same error whether an object is missing or belongs to another user
 * prevents information leakage — an attacker cannot tell whether a goal ID exists
 * at all. This is the "opaque not-found" pattern described in the auth guide.
 */
class CrossUserIsolationIntegrationTest extends BaseGraphQlIntegrationTest {

    private String userAGoalId;
    private AppUser userB;

    @BeforeEach
    void setup() {
        // testUser == userA (set up by BaseGraphQlIntegrationTest)
        userAGoalId = createGoalAs(testUser, "User A's secret goal");

        // Create userB but do NOT switch yet — stay as userA for now
        userB = createAdditionalUser("user-b-sub", "userB@example.com");
    }

    // ─── goals { } list query ─────────────────────────────────────────────────

    @Test
    @DisplayName("User B sees only their own goals — not user A's")
    void goalListIsFilteredByOwner() {
        // UserB creates their own goal
        setCurrentUser(userB);
        createGoalAs(userB, "User B's goal");

        // UserB's goal list must contain exactly one goal (their own)
        graphQlTester.document("{ goals { id title } }")
                .execute()
                .path("goals").entityList(Object.class).hasSize(1)
                .path("goals[0].title").entity(String.class).isEqualTo("User B's goal");
    }

    @Test
    @DisplayName("User B sees an empty list when they have no goals")
    void goalListIsEmptyForNewUser() {
        setCurrentUser(userB);

        graphQlTester.document("{ goals { id } }")
                .execute()
                .path("goals").entityList(Object.class).hasSize(0);
    }

    // ─── goalById query ───────────────────────────────────────────────────────

    @Test
    @DisplayName("User B cannot read user A's goal by ID — gets NOT_FOUND")
    void cannotReadAnotherUsersGoalById() {
        setCurrentUser(userB);

        graphQlTester.document("""
                        query($id: ID!) { goalById(id: $id) { id title } }
                        """)
                .variable("id", userAGoalId)
                .execute()
                .errors().satisfy(errors ->
                        errors.stream()
                                .anyMatch(e -> e.getMessage().contains("not found") ||
                                              e.getErrorType().toString().contains("NOT_FOUND")));
    }

    // ─── mutations on another user's goal ────────────────────────────────────

    @Test
    @DisplayName("User B cannot update user A's goal title")
    void cannotUpdateAnotherUsersGoal() {
        setCurrentUser(userB);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: { title: "Hijacked" }) { id }
                        }
                        """)
                .variable("id", userAGoalId)
                .execute()
                .errors().satisfy(errors -> {
                    assert !errors.isEmpty() : "Expected an error but got none";
                });
    }

    @Test
    @DisplayName("User B cannot delete user A's goal")
    void cannotDeleteAnotherUsersGoal() {
        setCurrentUser(userB);

        graphQlTester.document("""
                        mutation($id: ID!) { deleteGoal(id: $id) }
                        """)
                .variable("id", userAGoalId)
                .execute()
                .errors().satisfy(errors -> {
                    assert !errors.isEmpty() : "Expected an error but got none";
                });

        // Confirm the goal still exists (switch back to userA)
        setCurrentUser(testUser);
        graphQlTester.document("""
                        query($id: ID!) { goalById(id: $id) { id } }
                        """)
                .variable("id", userAGoalId)
                .execute()
                .path("goalById.id").entity(String.class).isEqualTo(userAGoalId);
    }

    @Test
    @DisplayName("User B cannot add a target to user A's goal")
    void cannotAddTargetToAnotherUsersGoal() {
        setCurrentUser(userB);

        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createTarget(goalId: $goalId, input: { type: "binary", title: "Injected" }) { id }
                        }
                        """)
                .variable("goalId", userAGoalId)
                .execute()
                .errors().satisfy(errors -> {
                    assert !errors.isEmpty() : "Expected an error but got none";
                });
    }

    @Test
    @DisplayName("User B cannot add an option to user A's goal")
    void cannotAddOptionToAnotherUsersGoal() {
        setCurrentUser(userB);

        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          addOption(goalId: $goalId, text: "Injected option") { id }
                        }
                        """)
                .variable("goalId", userAGoalId)
                .execute()
                .errors().satisfy(errors -> {
                    assert !errors.isEmpty() : "Expected an error but got none";
                });
    }

    @Test
    @DisplayName("User B cannot add a resource to user A's goal")
    void cannotAddResourceToAnotherUsersGoal() {
        setCurrentUser(userB);

        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          createResource(goalId: $goalId, input: { type: "note", title: "Injected" }) { id }
                        }
                        """)
                .variable("goalId", userAGoalId)
                .execute()
                .errors().satisfy(errors -> {
                    assert !errors.isEmpty() : "Expected an error but got none";
                });
    }

    @Test
    @DisplayName("User B cannot add a reality item to user A's goal")
    void cannotAddRealityItemToAnotherUsersGoal() {
        setCurrentUser(userB);

        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          addRealityItem(goalId: $goalId, kind: "actions", text: "Injected action") {
                            actions { id }
                          }
                        }
                        """)
                .variable("goalId", userAGoalId)
                .execute()
                .errors().satisfy(errors -> {
                    assert !errors.isEmpty() : "Expected an error but got none";
                });
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private String createGoalAs(AppUser user, String title) {
        setCurrentUser(user);
        return graphQlTester.document("""
                        mutation($title: String!) {
                          createGoal(input: { title: $title, confidence: 5 }) { id }
                        }
                        """)
                .variable("title", title)
                .execute()
                .path("createGoal.id").entity(String.class).get();
    }
}
