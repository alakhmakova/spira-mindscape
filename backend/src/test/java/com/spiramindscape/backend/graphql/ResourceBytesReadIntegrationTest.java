package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import com.spiramindscape.backend.support.SqlCapture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the rule that list-shaped reads never touch {@code resource.data_url}.
 *
 * <p>A file resource stores its bytes as a base64 {@code TEXT} column, up to ~6.7 MB per file.
 * Fetching whole {@code Resource} entities for the goals graph pulled all of that out of the
 * database on every background poll from every device and then dropped it during serialization —
 * invisible in the response, but the largest single source of metered database egress
 * ({@code backlog/background-sync-refetches-full-goals-egress.md}).
 *
 * <p>These tests assert on the SQL rather than the response, because the response was always
 * correct. A future refactor that swaps the projection back for the entity would pass every
 * existing test and silently reintroduce the cost — this is the test that would fail.
 */
@Import(SqlCapture.Config.class)
class ResourceBytesReadIntegrationTest extends BaseGraphQlIntegrationTest {

    private static final String PDF_DATA_URL = "data:application/pdf;base64,JVBERi0xLjQ=";

    private String goalId;
    private String resourceId;

    @BeforeEach
    void createGoalWithFile() {
        goalId = graphQlTester.document("""
                        mutation {
                          createGoal(input: { title: "Goal with an attachment", confidence: 5 }) {
                            id
                          }
                        }
                        """)
                .execute()
                .path("createGoal.id").entity(String.class).get();

        resourceId = graphQlTester.document("""
                        mutation($goalId: ID!, $dataUrl: String!) {
                          createResource(goalId: $goalId, input: {
                            type: "file"
                            title: "Contract"
                            mime: "application/pdf"
                            dataUrl: $dataUrl
                          }) {
                            id
                          }
                        }
                        """)
                .variable("goalId", goalId)
                .variable("dataUrl", PDF_DATA_URL)
                .execute()
                .path("createResource.id").entity(String.class).get();
    }

    @Test
    @DisplayName("The goals query never selects resource.data_url")
    void goalsQueryDoesNotSelectFileBytes() {
        SqlCapture.start();
        graphQlTester.document("""
                        query {
                          goals {
                            id
                            resources { id type title mime }
                          }
                        }
                        """)
                .execute()
                .path("goals[0].resources[0].id").entity(String.class).isEqualTo(resourceId);
        List<String> sql = SqlCapture.stop();

        assertThat(sql).isNotEmpty();
        assertThat(selectsDataUrl(sql))
                .as("the goals graph must read resource metadata only — see ResourceView")
                .isFalse();
    }

    @Test
    @DisplayName("resourcesByGoal never selects resource.data_url")
    void resourcesByGoalDoesNotSelectFileBytes() {
        SqlCapture.start();
        graphQlTester.document("""
                        query($goalId: ID!) {
                          resourcesByGoal(goalId: $goalId) { id title mime }
                        }
                        """)
                .variable("goalId", goalId)
                .execute()
                .path("resourcesByGoal[0].id").entity(String.class).isEqualTo(resourceId);
        List<String> sql = SqlCapture.stop();

        assertThat(sql).isNotEmpty();
        assertThat(selectsDataUrl(sql)).isFalse();
    }

    @Test
    @DisplayName("dataUrl resolves to null in list responses, so clients know to load it lazily")
    void listResponsesReportDataUrlAsNull() {
        graphQlTester.document("""
                        query {
                          goals { resources { id dataUrl } }
                        }
                        """)
                .execute()
                .path("goals[0].resources[0].dataUrl").valueIsNull();
    }

    @Test
    @DisplayName("resourceById still reads and returns the bytes — the lazy-load path")
    void resourceByIdStillReturnsFileBytes() {
        SqlCapture.start();
        graphQlTester.document("""
                        query($id: ID!) {
                          resourceById(id: $id) { id dataUrl }
                        }
                        """)
                .variable("id", resourceId)
                .execute()
                .path("resourceById.dataUrl").entity(String.class).isEqualTo(PDF_DATA_URL);
        List<String> sql = SqlCapture.stop();

        assertThat(selectsDataUrl(sql))
                .as("the on-demand query is the one place that may read file bytes")
                .isTrue();
    }

    private static boolean selectsDataUrl(List<String> statements) {
        return statements.stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(s -> s.startsWith("select") && s.contains("data_url"));
    }
}
