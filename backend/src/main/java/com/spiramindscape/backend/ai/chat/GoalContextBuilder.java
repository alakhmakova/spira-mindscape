package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalService;
import com.spiramindscape.backend.goal.Option;
import com.spiramindscape.backend.goal.RealityItem;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.target.ChecklistItem;
import com.spiramindscape.backend.target.Target;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The goal, as the model sees it — a small always-present sketch, and the parts it can ask for.
 *
 * <h2>Why it is not the whole goal any more (2026-08-30)</h2>
 *
 * <p>It used to paste the entire goal into the system prompt of <b>every</b> call: the
 * description in full, every reality item, every option, every target <i>with every checklist
 * item</i>, and the title of every resource. The agentic loop re-sends the system prompt on each
 * of its iterations, so a goal with a long checklist was paid for two or three times per message,
 * on messages that never touched it. The owner's Mistral key started answering
 * {@code Rate limit exceeded} — a per-minute token allowance, spent by single turns — and their
 * question was the right one: <i>"I ask the assistant to add up the calories in my breakfast. Why
 * does it need my whole goal for that?"</i>
 *
 * <h2>What is sent, and what is asked for</h2>
 *
 * <p>Always: the goal's identity and its shape. Then <b>every section travels while it is
 * small and is summarised once it is not</b> — one rule, no exceptions, so what the model sees
 * is predictable from the size of the goal alone. What is summarised is one {@code read_goal}
 * call away, and the model makes that call when the conversation is actually about that part.
 *
 * <h2>Why the rule is a size and not a list of sections</h2>
 *
 * <p><b>A lookup is not free.</b> Answering {@code read_goal} means another turn through the
 * model with the whole prompt and the conversation attached — several thousand tokens. A section
 * that renders in a few hundred characters is therefore <i>cheaper carried than fetched</i>, and
 * one that does not is cheaper fetched than carried. That is the whole of {@link #INLINE_BUDGET}:
 * not tidiness, arithmetic. It is also why nothing is banned outright — a two-note goal pays
 * nothing to carry its two notes, and banning them would cost it a round trip to learn they
 * exist.
 *
 * <p>Two numbers rather than one, because targets are not like the other sections:
 * {@link #TARGET_LINES_BUDGET} keeps the one-line-per-target list — the ids that nearly every
 * change needs — long after a goal has outgrown carrying the checklist ITEMS inside them. So a
 * small goal arrives whole, a middling one arrives as its targets plus counts, and a large one
 * arrives as counts. Nothing is ever invisible; some of it is one call away.
 */
@Component
public class GoalContextBuilder {

    private final GoalRepository goalRepository;
    private final GoalService goalService;
    private final CurrentUserProvider currentUserProvider;

    public GoalContextBuilder(
            GoalRepository goalRepository,
            GoalService goalService,
            CurrentUserProvider currentUserProvider) {
        this.goalRepository = goalRepository;
        this.goalService = goalService;
        this.currentUserProvider = currentUserProvider;
    }

    /** Header used for the All-Goals overview (no goal open). */
    static final String NO_GOAL_HEADER = "## All Goals (no goal open)";

    /**
     * The most characters a section may take before it is summarised instead of listed.
     *
     * <p>Set against the cost of the alternative: a {@code read_goal} call re-sends the system
     * prompt and the conversation, which is thousands of tokens, so anything under a few hundred
     * characters is worth carrying. It is deliberately generous — the goals that were breaking
     * the budget were breaking it by thousands of characters, not by tens.
     */
    static final int INLINE_BUDGET = 400;

    /**
     * The same, for the one-line-per-target list.
     *
     * <p>Bigger, because those lines carry the ids that nearly every change the assistant makes
     * needs — completing a target, setting progress, renaming one. Losing them to a summary buys
     * a few hundred characters and costs a whole extra model call on the next message that
     * touches a target, which is most of them.
     */
    static final int TARGET_LINES_BUDGET = 900;

    /** One item's text, beyond which it is clipped in the sketch. The section can be fetched. */
    private static final int ITEM_CLIP = 120;

    /** What {@code read_goal} can be asked for. */
    public enum Section {
        DESCRIPTION, REALITY, OPTIONS, TARGETS, RESOURCES, ALL;

        /** The tool's own spelling, and what the model sends. */
        public String wire() {
            return name().toLowerCase();
        }

        static Section parse(String raw) {
            if (raw == null) return ALL;
            for (Section s : values()) {
                if (s.wire().equalsIgnoreCase(raw.trim())) return s;
            }
            return ALL;
        }
    }

    /**
     * Builds the AI context. With a goal id → the sketch of that goal. With no id (the All-Goals
     * overview) → a list of the user's goals plus the actions available there.
     *
     * <p><b>The goal id is user-supplied and untrusted, so the lookup is owner-scoped</b>
     * (BUG-054). This used to call {@code findById}, which meant a signed-in user could post any
     * {@code goalId} on {@code /api/ai/chat} and have another person's goal — title, description,
     * reality items, options, targets and resource titles — pasted into the system prompt and
     * read back to them by the model. A goal that is missing and a goal that belongs to someone
     * else are deliberately indistinguishable here: both fall back to the user's own overview,
     * exactly as an unknown id always did.
     */
    @Transactional(readOnly = true)
    public String build(Long goalId) {
        if (goalId == null) return buildGlobalContext();
        return goalRepository.findByIdAndUserId(goalId, currentUserId())
                .map(this::buildSketch)
                .orElseGet(this::buildGlobalContext);
    }

    /**
     * One section of a goal, in full — what {@code read_goal} answers with.
     *
     * <p>Owner-scoped for the same reason {@link #build} is, and more sharply: this one is
     * reached by a tool call whose argument the model writes, so the id has been through the
     * conversation. A goal that is not the caller's answers as though it did not exist.
     */
    @Transactional(readOnly = true)
    public String readSection(Long goalId, Section section) {
        if (goalId == null) return "No goal is open, so there is nothing to read.";
        return goalRepository.findByIdAndUserId(goalId, currentUserId())
                .map(goal -> renderSection(goal, section))
                .orElse("That goal is not available.");
    }

    private Long currentUserId() {
        return currentUserProvider.getCurrentUser().getId();
    }

    /** Overview context for the All-Goals page: the user's goals + allowed actions. */
    private String buildGlobalContext() {
        StringBuilder sb = new StringBuilder();
        sb.append(NO_GOAL_HEADER).append("\n\n");
        sb.append("The user is on the All-Goals overview — no goal is open. These are the goals "
                + "they see on their cards. You can edit each goal's card fields (NAME, "
                + "CONFIDENCE, DEADLINE) here with kind='edit_goal' (the goal's id + 'field').\n\n");

        List<Goal> goals = goalService.findAll();
        if (goals.isEmpty()) {
            sb.append("The user has no goals yet. Create one with kind='new_goal'.\n\n");
        } else {
            sb.append("Your goals:\n");
            for (Goal g : goals) {
                sb.append("- [goal id=").append(g.getId()).append("] \"")
                  .append(g.getTitle()).append("\" · confidence ")
                  .append(g.getConfidence()).append("/10 · ")
                  .append(g.getDeadline() != null ? "deadline " + g.getDeadline() : "no deadline")
                  .append(" · ").append(g.getTargets().size()).append(" target(s)\n");
            }
            sb.append("\n");
        }

        sb.append("To work with anything INSIDE a goal (targets, options, reality, notes), the "
                + "goal must be open. Either tell the user to open it, or propose opening it "
                + "(kind='open_goal' with its id) — they confirm.\n");
        sb.append("To delete a goal, use kind='delete_goal' with its id: this opens a "
                + "confirmation dialog for the user — you NEVER delete anything yourself.\n");
        sb.append("To create a new goal use kind='new_goal'. A request to \"create a goal\" in "
                + "any language — e.g. Russian «цель» — always means a new Goal, never a target.\n");
        return sb.toString();
    }

    // ── The sketch ──────────────────────────────────────────────────────────────

    private String buildSketch(Goal goal) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Current Goal\n\n");

        sb.append("**Goal id:** ").append(goal.getId()).append('\n');
        sb.append("**Title:** ").append(goal.getTitle()).append('\n');
        sb.append("**Confidence:** ").append(goal.getConfidence()).append("/10\n");
        if (goal.getDeadline() != null) {
            sb.append("**Deadline:** ").append(goal.getDeadline()).append('\n');
        }
        if (goal.getAchievedAt() != null) {
            sb.append("**Achieved:** ").append(goal.getAchievedAt()).append('\n');
        }

        // Every section renders twice — once in full, once as a count — and the shorter
        // promise wins. See INLINE_BUDGET for why that is arithmetic and not taste.
        List<String> fetchable = new java.util.ArrayList<>();
        appendSection(sb, Section.DESCRIPTION, renderDescription(goal), fetchable);
        appendSection(sb, Section.REALITY, renderReality(goal), fetchable);
        appendSection(sb, Section.OPTIONS, renderOptions(goal), fetchable);
        appendTargets(sb, goal, fetchable);
        appendSection(sb, Section.RESOURCES, renderResources(goal), fetchable);

        // One line, not a paragraph: what read_goal is for is explained once, in the system
        // prompt. Explaining it here as well would spend, on every call of every conversation,
        // exactly what this class exists to save.
        if (fetchable.isEmpty()) {
            // The whole goal fitted, so there is nothing to fetch and nothing to say.
            return sb.toString();
        }
        sb.append("\n(Sketch: ").append(String.join(", ", fetchable))
          .append(" summarised — read_goal loads them.)\n");
        return sb.toString();
    }

    /**
     * Appends {@code rendered} when it is small enough to be worth carrying, otherwise its
     * one-line summary — and records the section as one the model may want to fetch.
     */
    private void appendSection(
            StringBuilder sb, Section section, Rendered rendered, List<String> fetchable) {

        if (rendered.isEmpty()) return;
        if (rendered.full().length() <= INLINE_BUDGET) {
            sb.append(rendered.full());
            return;
        }
        sb.append(rendered.summary());
        fetchable.add(section.wire());
    }

    /**
     * Targets, at whichever of three sizes fits: everything including the checklist items, the
     * one line per target that carries the ids, or the count.
     *
     * <p>The middle tier is why targets have a rule of their own — see
     * {@link #TARGET_LINES_BUDGET}. It also means a small goal's checklist arrives in the sketch
     * exactly as it always did; it is only a list long enough to dominate the prompt that has to
     * be asked for.
     */
    private void appendTargets(StringBuilder sb, Goal goal, List<String> fetchable) {
        if (goal.getTargets().isEmpty()) return;

        String withItems = renderTargetsInFull(goal);
        if (withItems.length() <= INLINE_BUDGET) {
            sb.append(withItems);
            return;
        }
        Rendered lines = renderTargetLines(goal);
        if (lines.full().length() <= TARGET_LINES_BUDGET) {
            sb.append(lines.full());
            fetchable.add("checklist items");
            return;
        }
        sb.append(lines.summary());
        fetchable.add(Section.TARGETS.wire());
    }

    /** A section as both the thing itself and the promise of it. */
    private record Rendered(String full, String summary) {
        boolean isEmpty() {
            return full.isBlank();
        }

        static Rendered none() {
            return new Rendered("", "");
        }
    }

    private Rendered renderDescription(Goal goal) {
        String desc = goal.getDescription();
        if (desc == null || desc.isBlank()) return Rendered.none();
        String text = stripHtml(desc);
        if (text.isBlank()) return Rendered.none();
        return new Rendered(
                "\n**Description:**\n" + text + "\n",
                "\n**Description:** " + text.length() + " characters (read_goal \"description\")\n");
    }

    private Rendered renderReality(Goal goal) {
        var actions = goal.getRealityItems().stream()
                .filter(r -> "actions".equals(r.getKind())).toList();
        var obstacles = goal.getRealityItems().stream()
                .filter(r -> "obstacles".equals(r.getKind())).toList();
        if (actions.isEmpty() && obstacles.isEmpty()) return Rendered.none();

        StringBuilder full = new StringBuilder();
        if (!actions.isEmpty()) {
            full.append("\n**Current actions:**\n");
            actions.forEach(a -> full.append(realityLine(a)));
        }
        if (!obstacles.isEmpty()) {
            full.append("\n**Current obstacles:**\n");
            obstacles.forEach(o -> full.append(realityLine(o)));
        }
        String summary = String.format(
                "\n**Reality:** %d action(s), %d obstacle(s) (read_goal \"reality\")\n",
                actions.size(), obstacles.size());
        return new Rendered(full.toString(), summary);
    }

    private String realityLine(RealityItem item) {
        return "- (id=" + item.getId() + ") " + clip(item.getText()) + "\n";
    }

    private Rendered renderOptions(Goal goal) {
        var options = goal.getOptions();
        if (options.isEmpty()) return Rendered.none();

        StringBuilder full = new StringBuilder("\n**Options:**\n");
        for (Option o : options) {
            full.append(Boolean.TRUE.equals(o.getSelected()) ? "- [x] " : "- [ ] ")
                .append("(id=").append(o.getId()).append(") ").append(clip(o.getText()))
                .append('\n');
        }
        long selected = options.stream().filter(o -> Boolean.TRUE.equals(o.getSelected())).count();
        String summary = String.format(
                "\n**Options:** %d, %s (read_goal \"options\")\n",
                options.size(),
                selected == 0 ? "none selected" : selected + " selected");
        return new Rendered(full.toString(), summary);
    }

    /**
     * One line per target — id, type, title, progress. <b>Never the checklist items</b>: they are
     * the part of a goal that grows without limit, and the part a conversation is least often
     * about. A checklist target still says how much of it is done, which is what a question about
     * progress needs; {@code read_goal "targets"} has the items and their ids.
     */
    private Rendered renderTargetLines(Goal goal) {
        var targets = goal.getTargets();
        if (targets.isEmpty()) return Rendered.none();

        StringBuilder full = new StringBuilder("\n**Targets:**\n");
        for (Target t : targets) {
            full.append(describeTarget(t)).append('\n');
        }
        long achieved = targets.stream().filter(t -> t.getAchievedAt() != null).count();
        String summary = String.format(
                "\n**Targets:** %d (%d achieved) (read_goal \"targets\")\n",
                targets.size(), achieved);
        return new Rendered(full.toString(), summary);
    }

    /**
     * The resource list — ids, types and titles, never contents.
     *
     * <p>This is the section the owner named outright: <i>"why re-send all the resources every
     * time? the user can attach the one they mean"</i>. They were right about the goal that
     * prompted it — a long list of notes had been travelling on every call of every conversation
     * — and the fix is the size rule, not a ban: a goal with two notes pays almost nothing to
     * carry them, and would pay a whole round trip to learn they exist. Past
     * {@link #INLINE_BUDGET} it becomes a count of each kind, and {@code read_goal "resources"}
     * names them when that is actually the question.
     */
    private Rendered renderResources(Goal goal) {
        var resources = goal.getResources();
        if (resources.isEmpty()) return Rendered.none();

        StringBuilder full = new StringBuilder(
                "\n**Resources** (use the read_resource tool with the id to read one):\n");
        for (Resource r : resources) {
            full.append(resourceLine(r));
        }

        Map<String, Integer> byType = new LinkedHashMap<>();
        for (Resource r : resources) {
            byType.merge(r.getType() == null ? "other" : r.getType(), 1, Integer::sum);
        }
        StringBuilder counts = new StringBuilder();
        byType.forEach((type, n) -> counts.append(counts.isEmpty() ? "" : ", ")
                                          .append(n).append(' ').append(type));
        String summary = "\n**Resources:** " + resources.size() + " (" + counts
                + ") — read_goal \"resources\" lists them, then read_resource reads one\n";
        return new Rendered(full.toString(), summary);
    }

    private String resourceLine(Resource r) {
        StringBuilder sb = new StringBuilder("- [").append(r.getType())
                .append(" id=").append(r.getId()).append("] ").append(r.getTitle());
        if ("file".equals(r.getType()) && r.getMime() != null) {
            sb.append(" (").append(r.getMime()).append(")");
        }
        return sb.append('\n').toString();
    }

    // ── The sections, in full ───────────────────────────────────────────────────

    private String renderSection(Goal goal, Section section) {
        String body = switch (section) {
            case DESCRIPTION -> renderDescription(goal).full();
            case REALITY -> renderReality(goal).full();
            case OPTIONS -> renderOptions(goal).full();
            case TARGETS -> renderTargetsInFull(goal);
            case RESOURCES -> renderResources(goal).full();
            case ALL -> renderDescription(goal).full()
                    + renderReality(goal).full()
                    + renderOptions(goal).full()
                    + renderTargetsInFull(goal)
                    + renderResources(goal).full();
        };
        if (body.isBlank()) {
            // An empty section is an answer, and a useful one — without saying so the model
            // reads silence as a failed read and tries again.
            return "The goal has nothing in \"" + section.wire() + "\".";
        }
        return body.strip();
    }

    /** Targets with their checklist items — the detail the sketch leaves out. */
    private String renderTargetsInFull(Goal goal) {
        var targets = goal.getTargets();
        if (targets.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n**Targets:**\n");
        for (Target t : targets) {
            sb.append("checklist".equals(t.getType()) ? describeChecklist(t) : describeTarget(t))
              .append('\n');
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
            case "checklist" -> checklistHeadline(t);
            default -> String.format("- [id=%s] %s", t.getId(), t.getTitle());
        };
    }

    /** A checklist target without its items: how much of it is done, and its id. */
    private String checklistHeadline(Target t) {
        long done = t.getItems().stream().filter(i -> Boolean.TRUE.equals(i.getDone())).count();
        return String.format("- [checklist id=%s] %s: %d/%d done (read_goal \"targets\" for items)",
                t.getId(), t.getTitle(), done, t.getItems().size());
    }

    private String describeChecklist(Target t) {
        StringBuilder sb = new StringBuilder();
        long done = t.getItems().stream().filter(i -> Boolean.TRUE.equals(i.getDone())).count();
        sb.append(String.format("- [checklist id=%s] %s: %d/%d done",
                t.getId(), t.getTitle(), done, t.getItems().size()));
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

    /** One item's text, short enough that a long one cannot dominate the sketch. */
    private static String clip(String text) {
        if (text == null) return "";
        return text.length() <= ITEM_CLIP ? text : text.substring(0, ITEM_CLIP - 1) + "…";
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
