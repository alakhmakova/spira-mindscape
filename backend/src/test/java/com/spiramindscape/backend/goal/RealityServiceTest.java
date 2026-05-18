package com.spiramindscape.backend.goal;

import com.spiramindscape.backend.graphql.model.RealityPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealityServiceTest {

    @Mock
    private RealityRepository realityRepository;

    @Mock
    private GoalRepository goalRepository;

    @InjectMocks
    private RealityService realityService;

    @Test
    void normalizesSingularAndPluralKinds() {
        assertThat(realityService.normalizeKind("action")).isEqualTo("actions");
        assertThat(realityService.normalizeKind("ACTIONS")).isEqualTo("actions");
        assertThat(realityService.normalizeKind("obstacle")).isEqualTo("obstacles");
        assertThat(realityService.normalizeKind("OBSTACLES")).isEqualTo("obstacles");
    }

    @Test
    void rejectsUnknownKind() {
        assertThatThrownBy(() -> realityService.normalizeKind("risk"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown reality kind: risk");
    }

    @Test
    void buildsRealityByGoalIdsWithEmptyListsForGoalsWithoutItems() {
        Goal firstGoal = goal(1L);
        Goal secondGoal = goal(2L);
        when(realityRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(
                        item(10L, firstGoal, "actions"),
                        item(11L, firstGoal, "obstacles"),
                        item(12L, secondGoal, "actions")
                ));

        Map<Long, RealityPayload> payloads = realityService.buildRealityByGoalIds(List.of(1L, 2L, 3L));

        assertThat(payloads.get(1L).actions()).extracting(RealityItem::getId).containsExactly(10L);
        assertThat(payloads.get(1L).obstacles()).extracting(RealityItem::getId).containsExactly(11L);
        assertThat(payloads.get(2L).actions()).extracting(RealityItem::getId).containsExactly(12L);
        assertThat(payloads.get(2L).obstacles()).isEmpty();
        assertThat(payloads.get(3L).actions()).isEmpty();
        assertThat(payloads.get(3L).obstacles()).isEmpty();
    }

    private static Goal goal(Long id) {
        Goal goal = new Goal();
        goal.setId(id);
        goal.setTitle("Goal " + id);
        goal.setDescription("");
        goal.setConfidence(7);
        return goal;
    }

    private static RealityItem item(Long id, Goal goal, String kind) {
        RealityItem item = new RealityItem();
        item.setId(id);
        item.setGoal(goal);
        item.setKind(kind);
        item.setText("Item " + id);
        return item;
    }
}
