package com.spiramindscape.backend.goal;

import com.spiramindscape.backend.graphql.model.RealityPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RealityService {

    private final RealityRepository realityRepository;
    private final GoalRepository goalRepository;

    @Transactional(readOnly = true)
    public RealityPayload findByGoal(Long goalId) {
        return buildReality(goalId);
    }

    @Transactional
    public RealityPayload addItem(Long goalId, String kind, String text) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));
        RealityItem item = new RealityItem();
        item.setGoal(goal);
        item.setKind(normalizeKind(kind));
        item.setText(text);
        realityRepository.save(item);
        return buildReality(goalId);
    }

    @Transactional
    public RealityPayload updateItem(Long goalId, String kind, Long itemId, String text) {
        RealityItem item = getItem(goalId, kind, itemId);
        item.setText(text);
        realityRepository.save(item);
        return buildReality(goalId);
    }

    @Transactional
    public RealityPayload removeItem(Long goalId, String kind, Long itemId) {
        realityRepository.delete(getItem(goalId, kind, itemId));
        return buildReality(goalId);
    }

    @Transactional(readOnly = true)
    public RealityPayload buildReality(Long goalId) {
        return buildRealityFromItems(goalId, realityRepository.findByGoalIdOrderByCreatedAtAsc(goalId));
    }

    @Transactional(readOnly = true)
    public Map<Long, RealityPayload> buildRealityByGoalIds(List<Long> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<RealityItem>> itemsByGoalId = realityRepository
                .findByGoalIdInOrderByGoalIdAscCreatedAtAsc(goalIds)
                .stream()
                .collect(Collectors.groupingBy(item -> item.getGoal().getId()));
        return goalIds.stream()
                .collect(Collectors.toMap(
                        goalId -> goalId,
                        goalId -> buildRealityFromItems(goalId, itemsByGoalId.getOrDefault(goalId, List.of()))
                ));
    }

    private RealityItem getItem(Long goalId, String kind, Long itemId) {
        String normalizedKind = normalizeKind(kind);
        RealityItem item = realityRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Reality item not found: " + itemId));
        if (!item.getGoal().getId().equals(goalId) || !item.getKind().equals(normalizedKind)) {
            throw new IllegalArgumentException("Reality item does not belong to goal/kind");
        }
        return item;
    }

    public String normalizeKind(String kind) {
        String normalized = Objects.requireNonNull(kind, "kind is required")
                .toLowerCase(Locale.ROOT);
        if ("action".equals(normalized) || "actions".equals(normalized))   return "actions";
        if ("obstacle".equals(normalized) || "obstacles".equals(normalized)) return "obstacles";
        throw new IllegalArgumentException("Unknown reality kind: " + kind);
    }

    private RealityPayload buildRealityFromItems(Long goalId, List<RealityItem> items) {
        return new RealityPayload(
                goalId,
                items.stream().filter(item -> "actions".equals(item.getKind())).toList(),
                items.stream().filter(item -> "obstacles".equals(item.getKind())).toList()
        );
    }
}
