package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.support.BaseGraphQlIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The caps {@link GraphQlLimitsConfig} puts on one request: a request the clients really send
 * passes, and the three ways of turning one request into a flood of work are refused — and a
 * refused mutation writes <b>nothing</b>, not the first ten of its copies.
 */
class GraphQlLimitsIntegrationTest extends BaseGraphQlIntegrationTest {

    /** The web client's own {@code Goals} query, field for field (`GOAL_FIELDS` in api.ts). */
    private static final String WEB_GOALS_QUERY = """
            query Goals {
              goals {
                id title description confidence deadline createdAt achievedAt
                reality { id actions { id text } obstacles { id text } }
                options { id text selected status position }
                resources { id type title body url mime name role email phone driveWebViewLink }
                targets {
                  id type title start current total unit done deadline achievedAt progressLocked createdAt
                  items { id text done deadline achievedAt }
                }
                confidenceHistory { confidence at }
              }
            }
            """;

    private static String createEvent(String alias) {
        return alias + ": createUpcomingEvent(input: { kind: \"important\", title: \"Flood\","
                + " date: \"2026-12-31T00:00:00Z\" }) { id }";
    }

    private int eventCount() {
        return graphQlTester.document("{ upcomingEvents { id } }")
                .execute()
                .path("upcomingEvents").entityList(Object.class).get().size();
    }

    private void expectRefused(String document, String messagePart) {
        graphQlTester.document(document)
                .execute()
                .errors().satisfy(errors -> assertThat(errors)
                        .anyMatch(e -> e.getMessage() != null && e.getMessage().contains(messagePart)));
    }

    @Test
    @DisplayName("The web client's largest query is well inside the limits")
    void realQueryPasses() {
        graphQlTester.document(WEB_GOALS_QUERY).execute().errors().verify();
    }

    @Test
    @DisplayName("Hundreds of aliased copies of a query are refused by complexity")
    void aliasedQueriesRefused() {
        String flood = IntStream.range(0, 300)
                .mapToObj(i -> "a" + i + ": goals { id title }")
                .collect(Collectors.joining(" ", "{ ", " }"));
        expectRefused(flood, "maximum query complexity exceeded");
    }

    @Test
    @DisplayName("A query nested past the depth limit is refused")
    void deepQueryRefused() {
        String nested = "name";
        for (int i = 0; i < 16; i++) nested = "ofType { " + nested + " }";
        expectRefused("{ __type(name: \"Goal\") { " + nested + " } }", "maximum query depth exceeded");
    }

    @Test
    @DisplayName("One mutation per request is fine; eleven aliased copies are refused and write nothing")
    void aliasedMutationsRefused() {
        graphQlTester.document("mutation { " + createEvent("one") + " }").execute().errors().verify();
        assertThat(eventCount()).isEqualTo(1);

        String flood = IntStream.range(0, 11)
                .mapToObj(i -> createEvent("m" + i))
                .collect(Collectors.joining(" ", "mutation { ", " }"));
        expectRefused(flood, "Too many mutations in one request");
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Hiding the copies inside a fragment counts the same")
    void mutationsThroughFragmentRefused() {
        String fields = IntStream.range(0, 11)
                .mapToObj(i -> createEvent("m" + i))
                .collect(Collectors.joining(" "));
        expectRefused("mutation { ...F } fragment F on Mutation { " + fields + " }",
                "Too many mutations in one request");
        assertThat(eventCount()).isZero();
    }
}
