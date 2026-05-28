package com.spiramindscape.backend.graphql;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalService;
import com.spiramindscape.backend.goal.Option;
import com.spiramindscape.backend.goal.RealityItem;
import com.spiramindscape.backend.goal.RealityService;
import com.spiramindscape.backend.goal.ConfidenceHistory;
import com.spiramindscape.backend.graphql.input.CreateGoalInput;
import com.spiramindscape.backend.graphql.input.CreateResourceInput;
import com.spiramindscape.backend.graphql.input.CreateTargetInput;
import com.spiramindscape.backend.graphql.input.UpdateGoalInput;
import com.spiramindscape.backend.graphql.input.UpdateOptionInput;
import com.spiramindscape.backend.graphql.input.UpdateResourceInput;
import com.spiramindscape.backend.graphql.input.UpdateTargetInput;
import com.spiramindscape.backend.graphql.model.RealityPayload;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.resource.ResourceService;
import com.spiramindscape.backend.target.ChecklistItem;
import com.spiramindscape.backend.target.Target;
import com.spiramindscape.backend.target.TargetService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import graphql.schema.DataFetchingEnvironment;

@Controller
@RequiredArgsConstructor
public class SpiraGraphqlController {

    private final GoalService goalService;
    private final RealityService realityService;
    private final TargetService targetService;
    private final ResourceService resourceService;

    // Queries

    @QueryMapping
    public List<Goal> goals() {
        return goalService.findAll();
    }

    @QueryMapping
    public Goal goalById(@Argument Long id) {
        return goalService.findById(id);
    }

    @QueryMapping
    public List<Resource> resourcesByGoal(@Argument Long goalId) {
        return resourceService.findByGoal(goalId);
    }

    @QueryMapping
    public Resource resourceById(@Argument Long id) {
        return resourceService.findById(id);
    }

    @QueryMapping
    public List<Target> targetsByGoal(@Argument Long goalId) {
        return targetService.findByGoal(goalId);
    }

    @QueryMapping
    public Target targetById(@Argument Long id) {
        return targetService.findById(id);
    }

    @QueryMapping
    public List<Option> optionsByGoal(@Argument Long goalId) {
        return goalService.findOptions(goalId);
    }

    @QueryMapping
    public RealityPayload realityByGoal(@Argument Long goalId) {
        return realityService.findByGoal(goalId);
    }

    @QueryMapping
    public RealityItem realityItemById(@Argument Long id) {
        return realityService.findItemById(id);
    }

    // Goal mutations

    @MutationMapping
    public Goal createGoal(@Argument CreateGoalInput input) {
        return goalService.create(input);
    }

    @MutationMapping
    public Goal updateGoal(@Argument Long id, @Argument UpdateGoalInput input,
                           DataFetchingEnvironment environment) {
        Map<String, Object> rawInput = environment.getArgument("input");
        return goalService.update(id, input, rawInput);
    }

    @MutationMapping
    public Boolean deleteGoal(@Argument Long id) {
        goalService.delete(id);
        return true;
    }

    // Reality mutations

    @MutationMapping
    public RealityPayload addRealityItem(@Argument Long goalId,
                                         @Argument String kind,
                                         @Argument String text) {
        return realityService.addItem(goalId, kind, text);
    }

    @MutationMapping
    public RealityPayload updateRealityItem(@Argument Long goalId, @Argument String kind,
                                             @Argument Long itemId, @Argument String text) {
        return realityService.updateItem(goalId, kind, itemId, text);
    }

    @MutationMapping
    public RealityPayload removeRealityItem(@Argument Long goalId,
                                             @Argument String kind,
                                             @Argument Long itemId) {
        return realityService.removeItem(goalId, kind, itemId);
    }

    // Option mutations

    @MutationMapping
    public Option addOption(@Argument Long goalId, @Argument String text) {
        return goalService.addOption(goalId, text);
    }

    @MutationMapping
    public Option updateOption(@Argument Long goalId, @Argument Long optionId,
                                @Argument UpdateOptionInput input) {
        return goalService.updateOption(goalId, optionId, input);
    }

    @MutationMapping
    public Option selectOption(@Argument Long goalId, @Argument Long optionId) {
        return goalService.selectOption(goalId, optionId);
    }

    @MutationMapping
    public Boolean removeOption(@Argument Long goalId, @Argument Long optionId) {
        goalService.removeOption(goalId, optionId);
        return true;
    }

    @MutationMapping
    public List<Option> reorderOptions(@Argument Long goalId, @Argument List<Long> optionIds) {
        return goalService.reorderOptions(goalId, optionIds);
    }

    // Target mutations

    @MutationMapping
    public Target createTarget(@Argument Long goalId, @Argument CreateTargetInput input,
                               DataFetchingEnvironment environment) {
        Map<String, Object> rawInput = environment.getArgument("input");
        return targetService.create(goalId, input, rawInput);
    }

    @MutationMapping
    public Target updateTarget(@Argument Long id, @Argument UpdateTargetInput input,
                               DataFetchingEnvironment environment) {
        Map<String, Object> rawInput = environment.getArgument("input");
        boolean deadlineProvided = rawInput != null && rawInput.containsKey("deadline");
        return targetService.update(id, input, deadlineProvided, rawInput);
    }

    @MutationMapping
    public Boolean deleteTarget(@Argument Long id) {
        targetService.delete(id);
        return true;
    }

    // Resource mutations

    @MutationMapping
    public Resource createResource(@Argument Long goalId, @Argument CreateResourceInput input,
                                   DataFetchingEnvironment environment) {
        Map<String, Object> rawInput = environment.getArgument("input");
        return resourceService.create(goalId, input, rawInput);
    }

    @MutationMapping
    public Resource updateResource(@Argument Long id, @Argument UpdateResourceInput input,
                                   DataFetchingEnvironment environment) {
        Map<String, Object> rawInput = environment.getArgument("input");
        return resourceService.update(id, input, rawInput);
    }

    @MutationMapping
    public Boolean deleteResource(@Argument Long id) {
        resourceService.delete(id);
        return true;
    }

    // Schema field resolvers

    @BatchMapping(typeName = "Goal", field = "reality")
    public Map<Goal, RealityPayload> reality(List<Goal> goals) {
        Map<Long, RealityPayload> payloadsByGoalId = realityService.buildRealityByGoalIds(goalIds(goals));
        Map<Goal, RealityPayload> result = new LinkedHashMap<>();
        for (Goal goal : goals) {
            result.put(goal, payloadsByGoalId.get(goal.getId()));
        }
        return result;
    }

    @BatchMapping(typeName = "Goal", field = "options")
    public Map<Goal, List<Option>> options(List<Goal> goals) {
        Map<Long, List<Option>> optionsByGoalId = goalService.findOptionsByGoalIds(goalIds(goals));
        Map<Goal, List<Option>> result = new LinkedHashMap<>();
        for (Goal goal : goals) {
            result.put(goal, optionsByGoalId.getOrDefault(goal.getId(), List.of()));
        }
        return result;
    }

    @BatchMapping(typeName = "Goal", field = "confidenceHistory")
    public Map<Goal, List<ConfidenceHistory>> confidenceHistory(List<Goal> goals) {
        Map<Long, List<ConfidenceHistory>> historyByGoalId = goalService.findConfidenceHistoryByGoalIds(goalIds(goals));
        Map<Goal, List<ConfidenceHistory>> result = new LinkedHashMap<>();
        for (Goal goal : goals) {
            result.put(goal, historyByGoalId.getOrDefault(goal.getId(), List.of()));
        }
        return result;
    }

    @BatchMapping(typeName = "Goal", field = "resources")
    public Map<Goal, List<Resource>> resources(List<Goal> goals) {
        Map<Long, List<Resource>> resourcesByGoalId = resourceService.findByGoalIds(goalIds(goals));
        Map<Goal, List<Resource>> result = new LinkedHashMap<>();
        for (Goal goal : goals) {
            result.put(goal, resourcesByGoalId.getOrDefault(goal.getId(), List.of()));
        }
        return result;
    }

    @BatchMapping(typeName = "Goal", field = "targets")
    public Map<Goal, List<Target>> targets(List<Goal> goals) {
        Map<Long, List<Target>> targetsByGoalId = targetService.findByGoalIds(goalIds(goals));
        Map<Goal, List<Target>> result = new LinkedHashMap<>();
        for (Goal goal : goals) {
            result.put(goal, targetsByGoalId.getOrDefault(goal.getId(), List.of()));
        }
        return result;
    }

    @BatchMapping(typeName = "Goal", field = "progress")
    public Map<Goal, Double> goalProgress(List<Goal> goals) {
        Map<Long, Double> progressByGoalId = targetService.calculateGoalProgressByGoalIds(goalIds(goals));
        Map<Goal, Double> result = new LinkedHashMap<>();
        for (Goal goal : goals) {
            result.put(goal, progressByGoalId.getOrDefault(goal.getId(), 0d));
        }
        return result;
    }

    @BatchMapping(typeName = "Target", field = "items")
    public Map<Target, List<ChecklistItem>> items(List<Target> targets) {
        List<Long> checklistTargetIds = targets.stream()
                .filter(target -> "checklist".equals(target.getType()))
                .map(Target::getId)
                .distinct()
                .toList();
        Map<Long, List<ChecklistItem>> itemsByTargetId = targetService.findItemsByTargetIds(checklistTargetIds);
        Map<Target, List<ChecklistItem>> result = new LinkedHashMap<>();
        for (Target target : targets) {
            result.put(target, itemsByTargetId.getOrDefault(target.getId(), List.of()));
        }
        return result;
    }

    @BatchMapping(typeName = "Target", field = "progress")
    public Map<Target, Double> progress(List<Target> targets) {
        Map<Long, Double> progressByTargetId = targetService.calculateProgressByTargets(targets);
        Map<Target, Double> result = new LinkedHashMap<>();
        for (Target target : targets) {
            result.put(target, progressByTargetId.getOrDefault(target.getId(), 0d));
        }
        return result;
    }

    private static List<Long> goalIds(List<Goal> goals) {
        return goals.stream()
                .map(Goal::getId)
                .distinct()
                .toList();
    }
}

