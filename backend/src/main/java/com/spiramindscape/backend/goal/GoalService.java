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

    private final GoalRepository goalRepository;
    private final OptionRepository optionRepository;

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
        goal.setTitle(input.title());
        goal.setDescription(input.description() == null ? "" : input.description());
        goal.setConfidence(input.confidence());
        goal.setDeadline(input.deadline());
        return goalRepository.save(goal);
    }

    @Transactional
    public Goal update(Long id, UpdateGoalInput input) {
        Goal goal = findById(id);
        if (input.title() != null)       goal.setTitle(input.title());
        if (input.description() != null) goal.setDescription(input.description());
        if (input.confidence() != null)  goal.setConfidence(input.confidence());
        if (input.deadline() != null)    goal.setDeadline(input.deadline());
        if (input.achievedAt() != null)  goal.setAchievedAt(input.achievedAt());
        return goalRepository.save(goal);
    }

    @Transactional
    public void delete(Long id) {
        goalRepository.delete(findById(id));
    }

    @Transactional(readOnly = true)
    public List<Option> findOptions(Long goalId) {
        findById(goalId);
        return optionRepository.findByGoalIdOrderByCreatedAtAsc(goalId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Option>> findOptionsByGoalIds(List<Long> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        return optionRepository.findByGoalIdInOrderByGoalIdAscCreatedAtAsc(goalIds)
                .stream()
                .collect(Collectors.groupingBy(o -> o.getGoal().getId()));
    }

    @Transactional
    public Option addOption(Long goalId, String text) {
        Goal goal = findById(goalId);
        Option option = new Option();
        option.setGoal(goal);
        option.setText(text);
        option.setSelected(false);
        return optionRepository.save(option);
    }

    @Transactional
    public Option updateOption(Long goalId, Long optionId, UpdateOptionInput input) {
        findById(goalId);
        Option option = getOption(goalId, optionId);
        if (input.text() != null)     option.setText(input.text());
        if (input.selected() != null) option.setSelected(input.selected());
        return optionRepository.save(option);
    }

    @Transactional
    public Option selectOption(Long goalId, Long optionId) {
        findById(goalId);
        Option selected = getOption(goalId, optionId);
        List<Option> all = optionRepository.findByGoalIdOrderByCreatedAtAsc(goalId);
        all.forEach(o -> o.setSelected(o.getId().equals(optionId)));
        optionRepository.saveAll(all);
        return selected;
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
}
