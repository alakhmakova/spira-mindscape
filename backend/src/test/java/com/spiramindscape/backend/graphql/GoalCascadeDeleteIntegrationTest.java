package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class GoalCascadeDeleteIntegrationTest extends BaseGraphQlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("deleteGoal removes all options belonging to that goal")
    void deleteGoalRemovesOptions() {
        String goalId = createGoal("Goal with options", 5);
        addOption(goalId, "Option 1");
        addOption(goalId, "Option 2");

        Long id = Long.valueOf(goalId);
        assertThat(countRows("option", "goal_id", id)).isEqualTo(2);

        deleteGoal(goalId);

        assertThat(countRows("option", "goal_id", id)).isZero();
    }

    @Test
    @DisplayName("deleteGoal removes all reality items belonging to that goal")
    void deleteGoalRemovesRealityItems() {
        String goalId = createGoal("Goal with reality", 5);
        addRealityItem(goalId, "actions", "Action 1");
        addRealityItem(goalId, "obstacles", "Obstacle 1");

        Long id = Long.valueOf(goalId);
        assertThat(countRows("reality_item", "goal_id", id)).isEqualTo(2);

        deleteGoal(goalId);

        assertThat(countRows("reality_item", "goal_id", id)).isZero();
    }

    @Test
    @DisplayName("deleteGoal removes all resources belonging to that goal")
    void deleteGoalRemovesResources() {
        String goalId = createGoal("Goal with resources", 5);
        createNoteResource(goalId, "Note A");
        createNoteResource(goalId, "Note B");

        Long id = Long.valueOf(goalId);
        assertThat(countRows("resource", "goal_id", id)).isEqualTo(2);

        deleteGoal(goalId);

        assertThat(countRows("resource", "goal_id", id)).isZero();
    }

    @Test
    @DisplayName("deleteGoal removes all targets and their checklist items")
    void deleteGoalRemovesTargetsAndChecklistItems() {
        String goalId = createGoal("Goal with targets", 5);
        createBinaryTarget(goalId, "Binary target");
        String checklistTargetId = createChecklistTarget(goalId, "Checklist target");

        Long id = Long.valueOf(goalId);
        Long targetId = Long.valueOf(checklistTargetId);
        assertThat(countRows("target", "goal_id", id)).isEqualTo(2);
        assertThat(countRows("checklist_item", "target_id", targetId)).isEqualTo(2);

        deleteGoal(goalId);

        assertThat(countRows("target", "goal_id", id)).isZero();
        assertThat(countRows("checklist_item", "target_id", targetId)).isZero();
    }

    @Test
    @DisplayName("deleteGoal removes confidence history for that goal")
    void deleteGoalRemovesConfidenceHistory() {
        String goalId = createGoal("Goal with history", 5);
        updateGoalConfidence(goalId, 8);

        Long id = Long.valueOf(goalId);
        assertThat(countRows("confidence_history", "goal_id", id)).isEqualTo(2);

        deleteGoal(goalId);

        assertThat(countRows("confidence_history", "goal_id", id)).isZero();
    }

    @Test
    @DisplayName("deleteTarget removes all checklist items belonging to that target")
    void deleteTargetRemovesChecklistItems() {
        String goalId = createGoal("Goal for target delete", 5);
        String targetId = createChecklistTarget(goalId, "Checklist with items");

        Long id = Long.valueOf(targetId);
        assertThat(countRows("checklist_item", "target_id", id)).isEqualTo(2);

        graphQlTester.document("""
                        mutation($id: ID!) {
                          deleteTarget(id: $id)
                        }
                        """)
                .variable("id", targetId)
                .execute()
                .path("deleteTarget").entity(Boolean.class).isEqualTo(true);

        assertThat(countRows("checklist_item", "target_id", id)).isZero();
    }

    @Test
    @DisplayName("deleteGoal does not affect other goals or their data")
    void deleteGoalDoesNotAffectOtherGoals() {
        String goalA = createGoal("Goal A", 5);
        String goalB = createGoal("Goal B", 7);
        addOption(goalA, "Option in A");
        addOption(goalB, "Option in B");

        deleteGoal(goalA);

        graphQlTester.document("""
                        query($id: ID!) {
                          goalById(id: $id) {
                            title
                            options { text }
                          }
                        }
                        """)
                .variable("id", goalB)
                .execute()
                .path("goalById.title").entity(String.class).isEqualTo("Goal B")
                .path("goalById.options").entityList(Object.class).hasSize(1)
                .path("goalById.options[0].text").entity(String.class).isEqualTo("Option in B");
    }

    private String createGoal(String title, int confidence) {
        return graphQlTester.document("""
                        mutation($title: String!, $confidence: Int!) {
                          createGoal(input: { title: $title, confidence: $confidence }) { id }
                        }
                        """)
                .variable("title", title)
                .variable("confidence", confidence)
                .execute()
                .path("createGoal.id").entity(String.class).get();
    }

    private void deleteGoal(String goalId) {
        graphQlTester.document("""
                        mutation($id: ID!) {
                          deleteGoal(id: $id)
                        }
                        """)
                .variable("id", goalId)
                .execute()
                .path("deleteGoal").entity(Boolean.class).isEqualTo(true);
    }

    private void addOption(String goalId, String text) {
        graphQlTester.document("""
                        mutation($goalId: ID!, $text: String!) {
                          addOption(goalId: $goalId, text: $text) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("text", text)
                .execute();
    }

    private void addRealityItem(String goalId, String kind, String text) {
        graphQlTester.document("""
                        mutation($goalId: ID!, $kind: String!, $text: String!) {
                          addRealityItem(goalId: $goalId, kind: $kind, text: $text) {
                            actions { id }
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("kind", kind)
                .variable("text", text)
                .execute();
    }

    private void createNoteResource(String goalId, String title) {
        graphQlTester.document("""
                        mutation($goalId: ID!, $title: String!) {
                          createResource(goalId: $goalId, input: { type: "note", title: $title }) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", title)
                .execute();
    }

    private String createBinaryTarget(String goalId, String title) {
        return graphQlTester.document("""
                        mutation($goalId: ID!, $title: String!) {
                          createTarget(goalId: $goalId, input: { type: "binary", title: $title }) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", title)
                .execute()
                .path("createTarget.id").entity(String.class).get();
    }

    private String createChecklistTarget(String goalId, String title) {
        return graphQlTester.document("""
                        mutation($goalId: ID!, $title: String!) {
                          createTarget(goalId: $goalId, input: {
                            type: "checklist"
                            title: $title
                            items: [
                              { text: "Item 1", done: false }
                              { text: "Item 2", done: false }
                            ]
                          }) { id }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("title", title)
                .execute()
                .path("createTarget.id").entity(String.class).get();
    }

    private void updateGoalConfidence(String goalId, int confidence) {
        graphQlTester.document("""
                        mutation($id: ID!, $confidence: Int!) {
                          updateGoal(id: $id, input: { confidence: $confidence }) { id }
                        }
                        """)
                .variable("id", goalId)
                .variable("confidence", confidence)
                .execute();
    }

    private int countRows(String table, String column, Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class,
                id
        );
    }
}
