package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.Option;
import com.spiramindscape.backend.goal.RealityItem;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.target.ChecklistItem;
import com.spiramindscape.backend.target.Target;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Builds a structured plain-text representation of a goal to inject into the
 * AI system prompt. The format is human-readable and token-efficient.
 *
 * <p>Included in every AI request for the goal-scoped chat:
 * <ul>
 *   <li>Title, description, confidence (1–10)</li>
 *   <li>Reality — current actions and obstacles</li>
 *   <li>Options (with selection state)</li>
 *   <li>Targets (numeric, binary, checklist) with progress</li>
 *   <li>Resource titles (not full content — to stay within token budget)</li>
 * </ul>
 */
@Component
public class GoalContextBuilder {

    private final GoalRepository goalRepository;

    public GoalContextBuilder(GoalRepository goalRepository) {
        this.goalRepository = goalRepository;
    }

    /**
     * Loads the goal and returns its context block, or an empty string if
     * the goal is not found.
     */
    public String build(Long goalId) {
        if (goalId == null) return "";
        Optional<Goal> opt = goalRepository.findById(goalId);
        return opt.map(this::buildContext).orElse("");
    }

    private String buildContext(Goal goal) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Current Goal\n\n");

        sb.append("**Title:** ").append(goal.getTitle()).append('\n');
        sb.append("**Confidence:** ").append(goal.getConfidence()).append("/10\n");

        if (goal.getDeadline() != null) {
            sb.append("**Deadline:** ").append(goal.getDeadline()).append('\n');
        }
        if (goal.getAchievedAt() != null) {
            sb.append("**Achieved:** ").append(goal.getAchievedAt()).append('\n');
        }

        String desc = goal.getDescription();
        if (desc != null && !desc.isBlank()) {
            sb.append("\n**Description:**\n").append(stripHtml(desc)).append('\n');
        }

        // Reality
        var actions = goal.getRealityItems().stream()
                .filter(r -> "actions".equals(r.getKind())).toList();
        var obstacles = goal.getRealityItems().stream()
                .filter(r -> "obstacles".equals(r.getKind())).toList();

        if (!actions.isEmpty()) {
            sb.append("\n**Current actions:**\n");
            actions.forEach(a -> sb.append("- ").append(a.getText()).append('\n'));
        }
        if (!obstacles.isEmpty()) {
            sb.append("\n**Current obstacles:**\n");
            obstacles.forEach(o -> sb.append("- ").append(o.getText()).append('\n'));

        }

        // Options
        var options = goal.getOptions();
        if (!options.isEmpty()) {
            sb.append("\n**Options:**\n");
            for (Option o : options) {
                sb.append(Boolean.TRUE.equals(o.getSelected()) ? "- [x] " : "- [ ] ").append(o.getText()).append('\n');
            }
        }

        // Targets
        var targets = goal.getTargets();
        if (!targets.isEmpty()) {
            sb.append("\n**Targets:**\n");
            for (Target t : targets) {
                sb.append(describeTarget(t)).append('\n');
            }
        }

        // Resources (titles only)
        var resources = goal.getResources();
        if (!resources.isEmpty()) {
            sb.append("\n**Resources:**\n");
            for (Resource r : resources) {
                sb.append("- [").append(r.getType()).append("] ").append(r.getTitle()).append('\n');
            }
        }

        return sb.toString();
    }

    private String describeTarget(Target t) {
        return switch (t.getType()) {
            case "numeric" -> String.format("- [numeric] %s: %s/%s %s%s",
                    t.getTitle(),
                    t.getCurrent() != null ? t.getCurrent() : 0,
                    t.getTotal() != null ? t.getTotal() : "?",
                    t.getUnit() != null ? t.getUnit() : "",
                    t.getAchievedAt() != null ? " ✓" : "");
            case "binary" -> String.format("- [binary] %s: %s%s",
                    t.getTitle(),
                    Boolean.TRUE.equals(t.getDone()) ? "done" : "not done",
                    t.getAchievedAt() != null ? " ✓" : "");
            case "checklist" -> describeChecklist(t);
            default -> "- " + t.getTitle();
        };
    }

    private String describeChecklist(Target t) {
        long done = t.getItems().stream().filter(i -> Boolean.TRUE.equals(i.getDone())).count();
        long total = t.getItems().size();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("- [checklist] %s: %d/%d done", t.getTitle(), done, total));
        for (ChecklistItem item : t.getItems()) {
            sb.append("\n  - [").append(Boolean.TRUE.equals(item.getDone()) ? "x" : " ").append("] ")
              .append(item.getText());
        }
        return sb.toString();
    }

    /** Strips HTML tags for plain-text rendering in the AI prompt. */
    private static String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .trim();
    }
}
