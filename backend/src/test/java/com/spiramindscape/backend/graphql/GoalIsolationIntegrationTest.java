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

@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
class GoalIsolationIntegrationTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private GoalRepository goalRepository;

    private String goalA;
    private String goalB;

    @BeforeEach
    void createTwoGoals() {
        goalA = createGoal("Goal A", 5);
        goalB = createGoal("Goal B", 7);
    }

    @AfterEach
    void cleanDatabase() {
        goalRepository.deleteAll();
    }

    @Test
    @DisplayName("Options added to goal A are not visible in goal B")
    void optionsAreIsolatedPerGoal() {
        addOption(goalA, "Option only in A");

        graphQlTester.document("""
                        query($id: ID!) {
                          goalById(id: $id) {
                            options { id text }
                          }
                        }
                        """)
                .variable("id", goalB)
                .execute()
                .path("goalById.options").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Reality items added to goal A are not visible in goal B")
    void realityItemsAreIsolatedPerGoal() {
        addRealityItem(goalA, "actions", "Action only in A");
        addRealityItem(goalA, "obstacles", "Obstacle only in A");

        graphQlTester.document("""
                        query($id: ID!) {
                          goalById(id: $id) {
                            reality {
                              actions { id }
                              obstacles { id }
                            }
                          }
                        }
                        """)
                .variable("id", goalB)
                .execute()
                .path("goalById.reality.actions").entityList(Object.class).hasSize(0)
                .path("goalById.reality.obstacles").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Targets added to goal A are not visible in goal B")
    void targetsAreIsolatedPerGoal() {
        createBinaryTarget(goalA, "Target only in A");

        graphQlTester.document("""
                        query($id: ID!) {
                          goalById(id: $id) {
                            targets { id }
                          }
                        }
                        """)
                .variable("id", goalB)
                .execute()
                .path("goalById.targets").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Resources added to goal A are not visible in goal B")
    void resourcesAreIsolatedPerGoal() {
        createNoteResource(goalA, "Resource only in A");

        graphQlTester.document("""
                        query($id: ID!) {
                          goalById(id: $id) {
                            resources { id }
                          }
                        }
                        """)
                .variable("id", goalB)
                .execute()
                .path("goalById.resources").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("Confidence history of goal A does not appear in goal B")
    void confidenceHistoryIsIsolatedPerGoal() {
        updateGoalConfidence(goalA, 9);

        graphQlTester.document("""
                        query($id: ID!) {
                          goalById(id: $id) {
                            confidenceHistory { confidence }
                          }
                        }
                        """)
                .variable("id", goalB)
                .execute()
                .path("goalById.confidenceHistory").entityList(Object.class).hasSize(1)
                .path("goalById.confidenceHistory[0].confidence").entity(Integer.class).isEqualTo(7);
    }

    @Test
    @DisplayName("Progress of goal A does not affect goal B")
    void progressIsIsolatedPerGoal() {
        String targetId = createBinaryTarget(goalA, "Target in A");
        updateBinaryTargetDone(targetId, true);

        graphQlTester.document("""
                        query($id: ID!) {
                          goalById(id: $id) {
                            progress
                          }
                        }
                        """)
                .variable("id", goalB)
                .execute()
                .path("goalById.progress").entity(Double.class).isEqualTo(0d);
    }

    @Test
    @DisplayName("Selecting option in goal A does not affect options in goal B")
    void selectingOptionInGoalADoesNotAffectGoalB() {
        String optionInB = addOption(goalB, "Option in B");

        String optionInA = addOption(goalA, "Option in A");
        selectOption(goalA, optionInA);

        graphQlTester.document("""
                        query($id: ID!) {
                          goalById(id: $id) {
                            options { id selected }
                          }
                        }
                        """)
                .variable("id", goalB)
                .execute()
                .path("goalById.options[0].id").entity(String.class).isEqualTo(optionInB)
                .path("goalById.options[0].selected").entity(Boolean.class).isEqualTo(false);
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

    private void updateBinaryTargetDone(String targetId, boolean done) {
        graphQlTester.document("""
                        mutation($id: ID!, $done: Boolean!) {
                          updateTarget(id: $id, input: { done: $done }) { id }
                        }
                        """)
                .variable("id", targetId)
                .variable("done", done)
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
}
