package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.goal.GoalRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureGraphQlTester
@ActiveProfiles("test")
class GoalListIntegrationTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @Autowired
    private GoalRepository goalRepository;

    @AfterEach
    void cleanDatabase() {
        goalRepository.deleteAll();
    }

    @Test
    @DisplayName("goals query returns empty list when no goals exist")
    void goalsQueryReturnsEmptyListWhenNoGoalsExist() {
        graphQlTester.document("""
                        query {
                          goals { id }
                        }
                        """)
                .execute()
                .path("goals").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("goals query returns all goals ordered by creation time")
    void goalsQueryReturnsAllGoalsOrderedByCreatedAt() {
        createGoal("First goal", 5);
        createGoal("Second goal", 7);
        createGoal("Third goal", 3);

        graphQlTester.document("""
                        query {
                          goals {
                            id
                            title
                            confidence
                          }
                        }
                        """)
                .execute()
                .path("goals").entityList(Object.class).hasSize(3)
                .path("goals[0].title").entity(String.class).isEqualTo("First goal")
                .path("goals[1].title").entity(String.class).isEqualTo("Second goal")
                .path("goals[2].title").entity(String.class).isEqualTo("Third goal");
    }

    @Test
    @DisplayName("goals query resolves reality BatchMapping - empty actions and obstacles for new goal")
    void goalsQueryResolvesRealityBatchMapping() {
        createGoal("BatchMapping goal", 5);

        graphQlTester.document("""
                        query {
                          goals {
                            id
                            reality {
                              actions { id text }
                              obstacles { id text }
                            }
                          }
                        }
                        """)
                .execute()
                .path("goals[0].reality.actions").entityList(Object.class).hasSize(0)
                .path("goals[0].reality.obstacles").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("goals query resolves options BatchMapping - empty list for new goal")
    void goalsQueryResolvesOptionsBatchMapping() {
        createGoal("Options goal", 5);

        graphQlTester.document("""
                        query {
                          goals {
                            id
                            options { id text selected position }
                          }
                        }
                        """)
                .execute()
                .path("goals[0].options").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("goals query resolves targets BatchMapping - empty list for new goal")
    void goalsQueryResolvesTargetsBatchMapping() {
        createGoal("Targets goal", 5);

        graphQlTester.document("""
                        query {
                          goals {
                            id
                            targets { id title type progress }
                          }
                        }
                        """)
                .execute()
                .path("goals[0].targets").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("goals query resolves resources BatchMapping - empty list for new goal")
    void goalsQueryResolvesResourcesBatchMapping() {
        createGoal("Resources goal", 5);

        graphQlTester.document("""
                        query {
                          goals {
                            id
                            resources { id type title }
                          }
                        }
                        """)
                .execute()
                .path("goals[0].resources").entityList(Object.class).hasSize(0);
    }

    @Test
    @DisplayName("goals query resolves confidenceHistory BatchMapping - one entry on creation")
    void goalsQueryResolvesConfidenceHistoryBatchMapping() {
        createGoal("History goal", 7);

        graphQlTester.document("""
                        query {
                          goals {
                            id
                            confidenceHistory {
                              confidence
                              at
                            }
                          }
                        }
                        """)
                .execute()
                .path("goals[0].confidenceHistory").entityList(Object.class).hasSize(1)
                .path("goals[0].confidenceHistory[0].confidence").entity(Integer.class).isEqualTo(7);
    }

    @Test
    @DisplayName("goals query resolves progress BatchMapping - 0 for goal with no targets")
    void goalsQueryResolvesProgressBatchMapping() {
        createGoal("Progress goal", 5);

        graphQlTester.document("""
                        query {
                          goals {
                            id
                            progress
                          }
                        }
                        """)
                .execute()
                .path("goals[0].progress").entity(Double.class).isEqualTo(0d);
    }

    @Test
    @DisplayName("goals query resolves all BatchMapping fields for multiple goals in one round trip")
    void goalsQueryResolvesAllBatchMappingsForMultipleGoals() {
        String firstId = createGoal("First", 5);
        String secondId = createGoal("Second", 8);

        addOption(firstId, "Option A");
        addOption(secondId, "Option B");

        graphQlTester.document("""
                        query {
                          goals {
                            id
                            title
                            options { text }
                            reality { actions { id } obstacles { id } }
                            targets { id }
                            resources { id }
                            confidenceHistory { confidence }
                            progress
                          }
                        }
                        """)
                .execute()
                .path("goals").entityList(Object.class).hasSize(2)
                .path("goals[0].options").entityList(Object.class).hasSize(1)
                .path("goals[0].options[0].text").entity(String.class).isEqualTo("Option A")
                .path("goals[0].reality.actions").entityList(Object.class).hasSize(0)
                .path("goals[0].reality.obstacles").entityList(Object.class).hasSize(0)
                .path("goals[0].targets").entityList(Object.class).hasSize(0)
                .path("goals[0].resources").entityList(Object.class).hasSize(0)
                .path("goals[0].confidenceHistory").entityList(Object.class).hasSize(1)
                .path("goals[0].confidenceHistory[0].confidence").entity(Integer.class).isEqualTo(5)
                .path("goals[0].progress").entity(Double.class).isEqualTo(0d)
                .path("goals[1].options").entityList(Object.class).hasSize(1)
                .path("goals[1].options[0].text").entity(String.class).isEqualTo("Option B")
                .path("goals[1].reality.actions").entityList(Object.class).hasSize(0)
                .path("goals[1].reality.obstacles").entityList(Object.class).hasSize(0)
                .path("goals[1].targets").entityList(Object.class).hasSize(0)
                .path("goals[1].resources").entityList(Object.class).hasSize(0)
                .path("goals[1].confidenceHistory").entityList(Object.class).hasSize(1)
                .path("goals[1].confidenceHistory[0].confidence").entity(Integer.class).isEqualTo(8)
                .path("goals[1].progress").entity(Double.class).isEqualTo(0d);
    }

    private String createGoal(String title, int confidence) {
        return graphQlTester.document("""
                        mutation($title: String!, $confidence: Int!) {
                          createGoal(input: { title: $title, confidence: $confidence }) {
                            id
                          }
                        }
                        """)
                .variable("title", title)
                .variable("confidence", confidence)
                .execute()
                .path("createGoal.id").entity(String.class).get();
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
}
