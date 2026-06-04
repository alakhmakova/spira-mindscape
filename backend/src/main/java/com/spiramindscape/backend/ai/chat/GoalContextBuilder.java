package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.Option;
import com.spiramindscape.backend.goal.RealityItem;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.target.ChecklistItem;
import com.spiramindscape.backend.target.Target;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
     * Context used when no goal is open (the All-Goals overview / global chat).
     * Spells out that targets/options/etc. cannot exist here, so a "create"
     * request — including the Russian «цель», which can mean either Goal or
     * Target — can only be a NEW Goal.
     */
    static final String NO_GOAL_CONTEXT = """
            ## No goal is open

            The user is on the All-Goals overview, not inside a specific goal. There is
            no "current goal" to read or modify, so you CANNOT add or edit targets,
            options, reality items, or notes — those exist only inside an open goal.

            The only data action available here is creating a NEW goal (kind='new_goal').
            If the user asks to create a goal in any language — e.g. Russian «цель»,
            «новая цель» — that always means a new Goal, never a target.
            """;

    /**
     * Loads the goal and returns its context block. When no goal is open, returns
     * {@link #NO_GOAL_CONTEXT} so the AI knows only goal creation is possible.
     */
    @Transactional(readOnly = true)
    public String build(Long goalId) {
        if (goalId == null) return NO_GOAL_CONTEXT;
        Optional<Goal> opt = goalRepository.findById(goalId);
        return opt.map(this::buildContext).orElse(NO_GOAL_CONTEXT);
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
            actions.forEach(a -> sb.append("- (id=").append(a.getId()).append(") ").append(a.getText()).append('\n'));
        }
        if (!obstacles.isEmpty()) {
            sb.append("\n**Current obstacles:**\n");
            obstacles.forEach(o -> sb.append("- (id=").append(o.getId()).append(") ").append(o.getText()).append('\n'));

        }

        // Options
        var options = goal.getOptions();
        if (!options.isEmpty()) {
            sb.append("\n**Options:**\n");
            for (Option o : options) {
                sb.append(Boolean.TRUE.equals(o.getSelected()) ? "- [x] " : "- [ ] ")
                  .append("(id=").append(o.getId()).append(") ").append(o.getText()).append('\n');
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

        // Resources — list only (id/type/title). The actual content is read
        // on demand via the `read_resource` tool, so we don't spend tokens
        // embedding note bodies / PDF text in every request.
        var resources = goal.getResources();
        if (!resources.isEmpty()) {
            sb.append("\n**Resources** (use the read_resource tool with the id to read one):\n");
            for (Resource r : resources) {
                sb.append("- [").append(r.getType()).append(" id=").append(r.getId()).append("] ")
                  .append(r.getTitle());
                if ("file".equals(r.getType()) && r.getMime() != null) {
                    sb.append(" (").append(r.getMime()).append(")");
                }
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    private String describeTarget(Target t) {
        return switch (t.getType()) {
            case "numeric" -> String.format("- [numeric id=%s] %s: %s/%s %s%s",
                    t.getId(),
                    t.getTitle(),
                    t.getCurrent() != null ? t.getCurrent() : 0,
                    t.getTotal() != null ? t.getTotal() : "?",
                    t.getUnit() != null ? t.getUnit() : "",
                    t.getAchievedAt() != null ? " ✓" : "");
            case "binary" -> String.format("- [binary id=%s] %s: %s%s",
                    t.getId(),
                    t.getTitle(),
                    Boolean.TRUE.equals(t.getDone()) ? "done" : "not done",
                    t.getAchievedAt() != null ? " ✓" : "");
            case "checklist" -> describeChecklist(t);
            default -> String.format("- [id=%s] %s", t.getId(), t.getTitle());
        };
    }

    private String describeChecklist(Target t) {
        long done = t.getItems().stream().filter(i -> Boolean.TRUE.equals(i.getDone())).count();
        long total = t.getItems().size();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("- [checklist id=%s] %s: %d/%d done", t.getId(), t.getTitle(), done, total));
        for (ChecklistItem item : t.getItems()) {
            sb.append("\n  - (id=").append(item.getId()).append(") [")
              .append(Boolean.TRUE.equals(item.getDone()) ? "x" : " ").append("] ")
              .append(item.getText());
            if (item.getDeadline() != null) {
                sb.append(" · due ").append(item.getDeadline());
            }
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
