package com.spiramindscape.backend.target;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.graphql.input.ChecklistItemInput;
import com.spiramindscape.backend.graphql.input.CreateTargetInput;
import com.spiramindscape.backend.graphql.input.UpdateTargetInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TargetService {

    private final TargetRepository targetRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final GoalRepository goalRepository;

    @Transactional(readOnly = true)
    public List<Target> findByGoal(Long goalId) {
        goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));
        return targetRepository.findByGoalIdOrderByCreatedAtAsc(goalId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Target>> findByGoalIds(List<Long> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        return targetRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc(goalIds)
                .stream()
                .collect(Collectors.groupingBy(t -> t.getGoal().getId()));
    }

    @Transactional(readOnly = true)
    public Target findById(Long id) {
        return targetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Target not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ChecklistItem> findItems(Target target) {
        if (!"checklist".equals(target.getType())) {
            return List.of();
        }
        return checklistItemRepository.findByTargetIdOrderByCreatedAtAsc(target.getId());
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ChecklistItem>> findItemsByTargetIds(List<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        return checklistItemRepository.findByTargetIdInOrderByTargetIdAscCreatedAtAsc(targetIds)
                .stream()
                .collect(Collectors.groupingBy(item -> item.getTarget().getId()));
    }

    @Transactional
    public Target create(Long goalId, CreateTargetInput input) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + goalId));
        Target target = new Target();
        target.setGoal(goal);
        applyFields(target, input.title(), input.type(), input.deadline(), null,
                input.start(), input.current(), input.total(), input.unit(), input.done());
        replaceChecklistItems(target, input.items());
        return targetRepository.save(target);
    }

    @Transactional
    public Target update(Long id, UpdateTargetInput input) {
        Target target = findById(id);
        applyFields(target, input.title(), null, input.deadline(), input.achievedAt(),
                input.start(), input.current(), input.total(), input.unit(), input.done());
        if (input.items() != null) {
            replaceChecklistItems(target, input.items());
        }
        return targetRepository.save(target);
    }

    @Transactional
    public void delete(Long id) {
        targetRepository.delete(findById(id));
    }

    private void applyFields(Target target, String title, String type, Instant deadline,
                              Instant achievedAt, Double start, Double current,
                              Double total, String unit, Boolean done) {
        if (title != null)     target.setTitle(title);
        if (type != null)      target.setType(normalizeType(type));
        if (deadline != null)  target.setDeadline(deadline);
        if (achievedAt != null) target.setAchievedAt(achievedAt);
        if (start != null)     target.setStart(start);
        if (current != null)   target.setCurrent(current);
        if (total != null)     target.setTotal(total);
        if (unit != null)      target.setUnit(unit);
        if (done != null)      target.setDone(done);
    }

    private void replaceChecklistItems(Target target, List<ChecklistItemInput> inputs) {
        if (inputs == null) return;
        Map<Long, ChecklistItem> existingById = target.getItems().stream()
                .filter(i -> i.getId() != null)
                .collect(Collectors.toMap(ChecklistItem::getId, Function.identity()));
        List<ChecklistItem> next = new ArrayList<>();
        for (ChecklistItemInput input : inputs) {
            Long parsedId = parseLong(input.id());
            ChecklistItem item = parsedId == null
                    ? new ChecklistItem()
                    : existingById.getOrDefault(parsedId, new ChecklistItem());
            item.setTarget(target);
            item.setText(input.text());
            item.setDone(Boolean.TRUE.equals(input.done()));
            item.setDeadline(input.deadline());
            item.setAchievedAt(input.achievedAt());
            next.add(item);
        }
        target.getItems().clear();
        target.getItems().addAll(next);
    }

    private String normalizeType(String type) {
        String normalized = Objects.requireNonNull(type).toLowerCase(Locale.ROOT);
        if (!List.of("numeric", "binary", "checklist").contains(normalized)) {
            throw new IllegalArgumentException("Unknown target type: " + type);
        }
        return normalized;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return null; }
    }
}
