package com.spiramindscape.backend.goal;

import com.spiramindscape.backend.graphql.input.CreateGoalInput;
import com.spiramindscape.backend.graphql.input.UpdateGoalInput;
import com.spiramindscape.backend.graphql.input.UpdateOptionInput;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoalService {

    public static final int MAX_GOAL_TITLE_LENGTH = 200;
    public static final int MAX_GOAL_DESCRIPTION_LENGTH = 5000;
    public static final int MAX_OPTION_TEXT_LENGTH = 500;

    private final GoalRepository goalRepository;
    private final OptionRepository optionRepository;
    private final ConfidenceHistoryRepository confidenceHistoryRepository;

    @Transactional(readOnly = true)
    public List<Goal> findAll() {
        return goalRepository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public Goal findById(Long id) {
        return goalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + id));
    }

    @Transactional
    public Goal create(CreateGoalInput input) {
        Goal goal = new Goal();
        goal.setTitle(normalizeRequiredText(input.title(), "Goal title is required"));
        goal.setDescription(normalizeOptionalText(input.description()));
        goal.setConfidence(input.confidence());
        goal.setDeadline(input.deadline());
        validateGoal(goal);
        Goal saved = goalRepository.save(goal);
        saveConfidenceHistory(saved);
        return saved;
    }

    @Transactional
    public Goal update(Long id, UpdateGoalInput input) {
        return update(id, input, Map.of());
    }

    @Transactional
    public Goal update(Long id, UpdateGoalInput input, Map<String, Object> rawInput) {
        Goal goal = findById(id);
        Integer oldConfidence = goal.getConfidence();

        if (input.title() != null)       goal.setTitle(normalizeRequiredText(input.title(), "Goal title is required"));
        if (input.description() != null) goal.setDescription(normalizeOptionalText(input.description()));
        if (input.confidence() != null)  goal.setConfidence(input.confidence());
        if (input.deadline() != null)    goal.setDeadline(input.deadline());
        if (input.achievedAt() != null)  goal.setAchievedAt(input.achievedAt());
        if (rawInput != null && rawInput.containsKey("achievedAt") && input.achievedAt() == null) {
            goal.setAchievedAt(null);
        }
        if (rawInput != null && rawInput.containsKey("description") && input.description() == null) {
            goal.setDescription("");
        }
        if (rawInput != null && rawInput.containsKey("deadline") && input.deadline() == null) {
            goal.setDeadline(null);
        }
        if (rawInput != null && rawInput.containsKey("confidence") && input.confidence() == null) {
            goal.setConfidence(null);
        }
        validateGoal(goal);
        Goal saved = goalRepository.save(goal);

        if (input.confidence() != null && !input.confidence().equals(oldConfidence)) {
            saveConfidenceHistory(saved);
        }

        return saved;
    }

    private void saveConfidenceHistory(Goal goal) {
        ConfidenceHistory history = new ConfidenceHistory();
        history.setGoal(goal);
        history.setConfidence(goal.getConfidence());
        history.setAt(java.time.Instant.now());
        confidenceHistoryRepository.save(history);
    }

    @Transactional
    public void delete(Long id) {
        goalRepository.delete(findById(id));
    }

    @Transactional(readOnly = true)
    public List<Option> findOptions(Long goalId) {
        findById(goalId);
        return optionRepository.findByGoalIdOrderByPositionAscCreatedAtAsc(goalId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Option>> findOptionsByGoalIds(List<Long> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        return optionRepository.findByGoalIdInOrderByGoalIdAscPositionAscCreatedAtAsc(goalIds)
                .stream()
                .collect(Collectors.groupingBy(o -> o.getGoal().getId()));
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ConfidenceHistory>> findConfidenceHistoryByGoalIds(List<Long> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        return confidenceHistoryRepository.findByGoalIdInOrderByAtDesc(goalIds)
                .stream()
                .collect(Collectors.groupingBy(h -> h.getGoal().getId()));
    }

    @Transactional
    public Option addOption(Long goalId, String text) {
        Goal goal = findById(goalId);
        int nextPosition = optionRepository.findMaxPositionByGoalId(goalId) + 1;
        Option option = new Option();
        option.setGoal(goal);
        String normalized = normalizeRequiredText(text, "Option text is required");
        validateOptionText(normalized);
        option.setText(normalized);
        option.setSelected(false);
        option.setPosition(nextPosition);
        return optionRepository.save(option);
    }

    @Transactional
    public Option updateOption(Long goalId, Long optionId, UpdateOptionInput input) {
        findById(goalId);
        Option option = getOption(goalId, optionId);
        if (input.text() != null) {
            String normalized = normalizeRequiredText(input.text(), "Option text is required");
            validateOptionText(normalized);
            option.setText(normalized);
        }
        if (input.selected() != null) option.setSelected(input.selected());
        return optionRepository.save(option);
    }

    @Transactional
    public Option selectOption(Long goalId, Long optionId) {
        findById(goalId);
        Option selected = getOption(goalId, optionId);
        List<Option> all = optionRepository.findByGoalIdOrderByPositionAscCreatedAtAsc(goalId);
        all.forEach(o -> o.setSelected(o.getId().equals(optionId)));
        optionRepository.saveAll(all);
        return selected;
    }

    @Transactional
    public List<Option> reorderOptions(Long goalId, List<Long> optionIds) {
        findById(goalId);
        List<Option> all = optionRepository.findByGoalIdOrderByPositionAscCreatedAtAsc(goalId);

        if (optionIds.size() != all.size()) {
            throw new IllegalArgumentException(
                    "Option ids list must contain all options for this goal. Expected " +
                    all.size() + ", got " + optionIds.size());
        }

        Map<Long, Option> byId = all.stream()
                .collect(Collectors.toMap(Option::getId, o -> o));

        for (int i = 0; i < optionIds.size(); i++) {
            Long id = optionIds.get(i);
            Option option = byId.get(id);
            if (option == null) {
                throw new IllegalArgumentException(
                        "Option not found or does not belong to goal: " + id);
            }
            option.setPosition(i);
        }

        optionRepository.saveAll(all);
        return optionRepository.findByGoalIdOrderByPositionAscCreatedAtAsc(goalId);
    }

    @Transactional
    public void removeOption(Long goalId, Long optionId) {
        findById(goalId);
        optionRepository.delete(getOption(goalId, optionId));
    }

    private Option getOption(Long goalId, Long optionId) {
        Option option = optionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found: " + optionId));
        if (!option.getGoal().getId().equals(goalId)) {
            throw new IllegalArgumentException("Option does not belong to goal");
        }
        return option;
    }

    private void validateOptionText(String text) {
        if (text.length() > MAX_OPTION_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Option text must be " + MAX_OPTION_TEXT_LENGTH + " characters or fewer");
        }
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        return value == null ? "" : value.trim();
    }

    private void validateGoal(Goal goal) {
        if (goal.getTitle().length() > MAX_GOAL_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "Goal title must be " + MAX_GOAL_TITLE_LENGTH + " characters or fewer");
        }
        if (goal.getDescription() != null && goal.getDescription().length() > MAX_GOAL_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Goal description must be " + MAX_GOAL_DESCRIPTION_LENGTH + " characters or fewer");
        }
        if (goal.getConfidence() == null) {
            throw new IllegalArgumentException("Confidence rating is required");
        }
        if (goal.getConfidence() < 1 || goal.getConfidence() > 10) {
            throw new IllegalArgumentException("Confidence rating must be between 1 and 10");
        }
    }
}
