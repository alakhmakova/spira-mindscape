package com.spiramindscape.backend.target;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalService;
import com.spiramindscape.backend.graphql.input.ChecklistItemInput;
import com.spiramindscape.backend.graphql.input.CreateTargetInput;
import com.spiramindscape.backend.graphql.input.UpdateTargetInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TargetService {

    private final TargetRepository targetRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final GoalService goalService;

    @Transactional(readOnly = true)
    public List<Target> findByGoal(Long goalId) {
        goalService.findById(goalId); // owner-scoped check
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

    @Transactional(readOnly = true)
    public Map<Long, Double> calculateGoalProgressByGoalIds(List<Long> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        List<Target> targets = targetRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc(goalIds);
        Map<Long, List<Target>> targetsByGoalId = targets.stream()
                .collect(Collectors.groupingBy(target -> target.getGoal().getId()));
        Map<Long, Double> progressByTargetId = calculateProgressByTargetsInternal(targets);

        Map<Long, Double> result = new LinkedHashMap<>();
        for (Long goalId : goalIds) {
            List<Target> goalTargets = targetsByGoalId.getOrDefault(goalId, List.of());
            double progress = goalTargets.isEmpty() ? 0 :
                    goalTargets.stream()
                            .map(Target::getId)
                            .mapToDouble(targetId -> progressByTargetId.getOrDefault(targetId, 0d))
                            .average()
                            .orElse(0);
            result.put(goalId, progress);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<Long, Double> calculateProgressByTargets(List<Target> targets) {
        return calculateProgressByTargetsInternal(targets);
    }

    @Transactional
    public Target create(Long goalId, CreateTargetInput input) {
        return create(goalId, input, Map.of());
    }

    @Transactional
    public Target create(Long goalId, CreateTargetInput input, Map<String, Object> rawInput) {
        Goal goal = goalService.findById(goalId); // owner-scoped
        if ("binary".equalsIgnoreCase(input.type()) && Boolean.TRUE.equals(input.done())) {
            throw new IllegalArgumentException(
                    "Cannot create binary target as already done - use update to mark as done");
        }
        validateItemsAllowedOnCreate(input);
        validateNumericCreateInput(input, rawInput);
        validateChecklistCreateInput(input);
        Double initialCurrent = "numeric".equalsIgnoreCase(input.type()) ? input.start() : input.current();
        Target target = new Target();
        target.setGoal(goal);
        applyFields(target, input.title(), input.type(), input.deadline(), null,
                input.start(), initialCurrent, input.total(), input.unit(), input.done());
        replaceChecklistItems(target, input.items());
        return targetRepository.save(target);
    }

    @Transactional
    public Target update(Long id, UpdateTargetInput input) {
        return update(id, input, false, Map.of());
    }

    @Transactional
    public Target update(Long id, UpdateTargetInput input, boolean deadlineProvided) {
        return update(id, input, deadlineProvided, Map.of());
    }

    @Transactional
    public Target update(Long id, UpdateTargetInput input, boolean deadlineProvided, Map<String, Object> rawInput) {
        Target target = findById(id);
        validateNumericUpdateInput(target, input, rawInput);
        applyFields(target, input.title(), null, input.deadline(), input.achievedAt(),
                input.start(), input.current(), input.total(), input.unit(), input.done());
        if (deadlineProvided && input.deadline() == null) {
            target.setDeadline(null);
        }
        if (input.items() != null) {
            validateChecklistUpdateInput(target, input);
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
        if (title != null) {
            String normalized = title.trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException("Target title is required");
            target.setTitle(normalized);
        }
        if (type != null)       target.setType(normalizeType(type));
        if (deadline != null)   target.setDeadline(deadline);
        if (achievedAt != null) target.setAchievedAt(achievedAt);
        if (start != null)      target.setStart(start);
        if (current != null)    target.setCurrent(current);
        if (total != null)      target.setTotal(total);
        if (unit != null)       target.setUnit(unit);
        if (done != null)       target.setDone(done);
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
                    : existingById.get(parsedId);
            if (item == null) {
                throw new IllegalArgumentException("Checklist item not found: " + input.id());
            }
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

    private void validateNumericCreateInput(CreateTargetInput input, Map<String, Object> rawInput) {
        if (!"numeric".equalsIgnoreCase(input.type())) {
            return;
        }
        rejectProvidedNull(rawInput, "start", "Numeric target start cannot be null");
        rejectProvidedNull(rawInput, "current", "Numeric target current cannot be null");
        rejectProvidedNull(rawInput, "total", "Numeric target target cannot be null");
        if (input.start() == null) {
            throw new IllegalArgumentException("Numeric target requires start");
        }
        if (input.current() != null) {
            throw new IllegalArgumentException("Numeric target current cannot be set on create");
        }
        if (input.total() == null) {
            throw new IllegalArgumentException("Numeric target requires total");
        }
        validateNonNegative(input.start(), "Numeric target start cannot be negative");
        validateNonNegative(input.total(), "Numeric target target cannot be negative");
        validateNumericDistance(input.start(), input.total());
    }

    private void validateNumericUpdateInput(Target target, UpdateTargetInput input, Map<String, Object> rawInput) {
        if (!"numeric".equalsIgnoreCase(target.getType())) {
            return;
        }

        rejectProvidedNull(rawInput, "start", "Numeric target start cannot be null");
        rejectProvidedNull(rawInput, "current", "Numeric target current cannot be null");
        rejectProvidedNull(rawInput, "total", "Numeric target target cannot be null");

        Double nextStart = input.start() == null ? target.getStart() : input.start();
        Double nextCurrent = input.current() == null ? target.getCurrent() : input.current();
        Double nextTotal = input.total() == null ? target.getTotal() : input.total();

        if (nextStart == null) {
            throw new IllegalArgumentException("Numeric target requires start");
        }
        if (nextCurrent == null) {
            throw new IllegalArgumentException("Numeric target requires current");
        }
        if (nextTotal == null) {
            throw new IllegalArgumentException("Numeric target requires total");
        }

        validateNonNegative(nextStart, "Numeric target start cannot be negative");
        validateNonNegative(nextCurrent, "Numeric target current cannot be negative");
        validateNonNegative(nextTotal, "Numeric target target cannot be negative");
        validateNumericDistance(nextStart, nextTotal);

        double min = Math.min(nextStart, nextTotal);
        double max = Math.max(nextStart, nextTotal);
        if (nextCurrent < min || nextCurrent > max) {
            throw new IllegalArgumentException(
                    "Numeric target current must be between start and target");
        }
    }

    private void rejectProvidedNull(Map<String, Object> rawInput, String field, String message) {
        if (rawInput != null && rawInput.containsKey(field) && rawInput.get(field) == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateNonNegative(Double value, String message) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateNumericDistance(Double start, Double total) {
        if (start != null && total != null && start.equals(total)) {
            throw new IllegalArgumentException("Numeric target start and target must be different");
        }
    }

    private void validateChecklistCreateInput(CreateTargetInput input) {
        if (!"checklist".equalsIgnoreCase(input.type())) {
            return;
        }
        if (input.items() == null || input.items().isEmpty()) {
            throw new IllegalArgumentException("Checklist target requires at least one item");
        }
        validateChecklistItemText(input.items());
    }

    private void validateChecklistUpdateInput(Target target, UpdateTargetInput input) {
        if (!"checklist".equalsIgnoreCase(target.getType())) {
            throw new IllegalArgumentException("Only checklist targets can have items");
        }
        if (input.items().isEmpty()) {
            throw new IllegalArgumentException("Checklist target requires at least one item");
        }
        validateChecklistItemText(input.items());
        validateUniqueChecklistItemIds(input.items());
    }

    private void validateItemsAllowedOnCreate(CreateTargetInput input) {
        if (input.items() != null && !"checklist".equalsIgnoreCase(input.type())) {
            throw new IllegalArgumentException("Only checklist targets can have items");
        }
    }

    private void validateChecklistItemText(List<ChecklistItemInput> inputs) {
        for (ChecklistItemInput input : inputs) {
            if (input.text() == null || input.text().isBlank()) {
                throw new IllegalArgumentException("Checklist item text cannot be blank");
            }
        }
    }

    private void validateUniqueChecklistItemIds(List<ChecklistItemInput> inputs) {
        Set<String> seenIds = new HashSet<>();
        for (ChecklistItemInput input : inputs) {
            if (input.id() != null && !input.id().isBlank() && !seenIds.add(input.id())) {
                throw new IllegalArgumentException("Checklist item ids must be unique");
            }
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private Map<Long, Double> calculateProgressByTargetsInternal(List<Target> targets) {
        List<Long> checklistTargetIds = targets.stream()
                .filter(target -> "checklist".equals(normalizeNullableType(target.getType())))
                .map(Target::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, List<ChecklistItem>> itemsByTargetId = findItemsByTargetIds(checklistTargetIds);

        Map<Long, Double> result = new LinkedHashMap<>();
        for (Target target : targets) {
            if (target.getId() != null) {
                result.put(target.getId(),
                        calculateTargetProgress(target, itemsByTargetId.getOrDefault(target.getId(), List.of())));
            }
        }
        return result;
    }

    private double calculateTargetProgress(Target target, List<ChecklistItem> checklistItems) {
        String type = normalizeNullableType(target.getType());
        if ("binary".equals(type)) {
            return Boolean.TRUE.equals(target.getDone()) ? 1 : 0;
        }

        if ("numeric".equals(type)) {
            double currentValue = target.getCurrent() == null ? 0 : target.getCurrent();
            double totalValue = target.getTotal() == null ? 0 : target.getTotal();
            double startValue = target.getStart() == null
                    ? (currentValue > totalValue ? currentValue : 0)
                    : target.getStart();
            double distance = Math.abs(totalValue - startValue);
            if (distance == 0) {
                return currentValue == totalValue ? 1 : 0;
            }
            double completed = totalValue >= startValue
                    ? currentValue - startValue
                    : startValue - currentValue;
            return Math.max(0, Math.min(1, completed / distance));
        }

        if ("checklist".equals(type)) {
            if (checklistItems.isEmpty()) {
                return 0;
            }
            long completed = checklistItems.stream()
                    .filter(item -> Boolean.TRUE.equals(item.getDone()))
                    .count();
            return (double) completed / checklistItems.size();
        }

        return 0;
    }

    private String normalizeNullableType(String type) {
        return type == null ? "" : type.toLowerCase(Locale.ROOT);
    }
}
