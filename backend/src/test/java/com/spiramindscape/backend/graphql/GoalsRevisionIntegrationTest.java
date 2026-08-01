package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code goalsRevision} query: a cheap change-signature over the current user's whole goal
 * graph, used by background sync to skip re-fetching every goal when nothing changed (a large cut
 * in outbound data transfer / Neon egress). These tests pin the contract the client relies on:
 * the value is stable while nothing changes, moves on any insert / update / delete anywhere in the
 * graph, and is strictly per-user.
 */
class GoalsRevisionIntegrationTest extends BaseGraphQlIntegrationTest {

    @Test
    @DisplayName("Empty account has a stable zero revision")
    void emptyAccountHasZeroRevision() {
        assertThat(revision()).isEqualTo("0:0");
    }

    @Test
    @DisplayName("Revision is stable across polls when nothing changes")
    void revisionIsStableWhenNothingChanges() {
        createGoal("Stable goal");
        String first = revision();
        assertThat(revision()).isEqualTo(first);
        assertThat(revision()).isEqualTo(first);
    }

    @Test
    @DisplayName("Creating a goal changes the revision")
    void creatingAGoalChangesTheRevision() {
        String before = revision();
        createGoal("A new goal");
        assertThat(revision()).isNotEqualTo(before);
    }

    @Test
    @DisplayName("Editing a goal changes the revision")
    void editingAGoalChangesTheRevision() throws InterruptedException {
        String goalId = createGoal("Original");
        String before = revision();
        // Sleep so the new updatedAt is measurably later than the create: an edit doesn't change
        // the row count, so the revision moves only via its timestamp, and the host clock's
        // granularity could otherwise make create and update read the same instant.
        Thread.sleep(5);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          updateGoal(id: $id, input: { title: "Renamed" }) { id }
                        }
                        """)
                .variable("id", goalId)
                .execute();

        assertThat(revision()).isNotEqualTo(before);
    }

    @Test
    @DisplayName("Adding a nested option changes the revision (nested edits are caught)")
    void addingANestedOptionChangesTheRevision() {
        String goalId = createGoal("Goal with options");
        String before = revision();

        graphQlTester.document("""
                        mutation($goalId: ID!) {
                          addOption(goalId: $goalId, text: "First option") { id }
                        }
                        """)
                .variable("goalId", goalId)
                .execute();

        assertThat(revision()).isNotEqualTo(before);
    }

    @Test
    @DisplayName("Deleting a goal changes the revision (count-based)")
    void deletingAGoalChangesTheRevision() {
        String goalId = createGoal("Doomed goal");
        String withGoal = revision();

        graphQlTester.document("""
                        mutation($id: ID!) { deleteGoal(id: $id) }
                        """)
                .variable("id", goalId)
                .execute();

        assertThat(revision()).isNotEqualTo(withGoal);
        assertThat(revision()).isEqualTo("0:0");
    }

    @Test
    @DisplayName("Another user's changes never move this user's revision")
    void revisionIsPerUser() {
        createGoal("My goal");
        String mine = revision();

        // A second user creating goals must not affect the first user's signature.
        AppUser other = createAdditionalUser("other-sub", "other@example.com");
        setCurrentUser(other);
        createGoal("Their goal");
        createGoal("Their second goal");

        setCurrentUser(testUser);
        assertThat(revision()).isEqualTo(mine);
    }

    // ---- helpers ----

    private String revision() {
        return graphQlTester.document("{ goalsRevision }")
                .execute()
                .path("goalsRevision").entity(String.class).get();
    }

    private String createGoal(String title) {
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
