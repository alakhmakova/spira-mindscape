package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import com.spiramindscape.backend.goal.GoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GoalContextBuilder}.
 *
 * <p>The key behaviour is what the AI sees on the All-Goals overview (no goal id):
 * a list of the user's goals plus the actions available there (edit a goal's card
 * fields, open a goal, start deletion, create a new goal). With a goal id it
 * returns that goal's full block.
 *
 * <p>The other behaviour worth a test is the security boundary (BUG-054): the goal id
 * arrives in the chat request body, so the lookup must be owner-scoped. A goal that
 * belongs to someone else has to be indistinguishable from one that does not exist.
 */
@ExtendWith(MockitoExtension.class)
class GoalContextBuilderTest {

    private static final long CURRENT_USER = 7L;

    @Mock private GoalRepository goalRepository;
    @Mock private GoalService goalService;
    @Mock private CurrentUserProvider currentUserProvider;
    @InjectMocks private GoalContextBuilder builder;

    @BeforeEach
    void signIn() {
        AppUser me = new AppUser();
        me.setId(CURRENT_USER);
        lenient().when(currentUserProvider.getCurrentUser()).thenReturn(me);
    }

    @Test
    @DisplayName("build(null) lists the user's goals and the All-Goals actions")
    void globalContextListsGoals() {
        when(goalService.findAll()).thenReturn(List.of(
                goal(12L, "Learn GraphQL", 7, LocalDate.parse("2026-12-31")),
                goal(15L, "Run a marathon", 4, null)));

        String context = builder.build(null);

        assertThat(context)
                .contains("All Goals")
                .contains("goal id=12").contains("Learn GraphQL").contains("7/10").contains("2026-12-31")
                .contains("goal id=15").contains("Run a marathon").contains("no deadline")
                // available actions are advertised so the AI knows what it may propose
                .contains("edit_goal").contains("open_goal").contains("delete_goal").contains("new_goal");
    }

    @Test
    @DisplayName("build(null) with no goals invites creating one")
    void globalContextEmpty() {
        when(goalService.findAll()).thenReturn(List.of());

        String context = builder.build(null);

        assertThat(context).contains("no goals yet").contains("new_goal");
    }

    @Test
    @DisplayName("build(existingId) returns the goal's context with its id, title and confidence")
    void existingGoalReturnsGoalContext() {
        Goal goal = goal(1L, "Learn GraphQL", 7, null);
        when(goalRepository.findByIdAndUserId(1L, CURRENT_USER)).thenReturn(Optional.of(goal));

        String context = builder.build(1L);

        assertThat(context)
                .contains("## Current Goal")
                .contains("Goal id:").contains("1")
                .contains("Learn GraphQL")
                .contains("7/10");
        assertThat(context).doesNotContain("All Goals");
    }

    // ─── Owner scoping (BUG-054) ──────────────────────────────────────────────

    @Test
    @DisplayName("A goal belonging to another user is never put in the prompt")
    void anotherUsersGoalIsNotReadable() {
        // The row exists, but not for THIS user, so the owner-scoped lookup misses.
        when(goalRepository.findByIdAndUserId(99L, CURRENT_USER)).thenReturn(Optional.empty());
        when(goalService.findAll()).thenReturn(List.of(goal(3L, "My own goal", 5, null)));

        String context = builder.build(99L);

        // Falls back to the caller's OWN overview — no trace of the other goal.
        assertThat(context).contains("All Goals").contains("My own goal");
        assertThat(context).doesNotContain("## Current Goal");
    }

    @Test
    @DisplayName("A goal id is never looked up unscoped")
    void neverUsesTheUnscopedLookup() {
        when(goalRepository.findByIdAndUserId(99L, CURRENT_USER)).thenReturn(Optional.empty());
        when(goalService.findAll()).thenReturn(List.of());

        builder.build(99L);

        // The regression this guards: findById(id) reads any user's row. If it ever comes
        // back, the test above could still pass on a mock that returns empty for both.
        verify(goalRepository, never()).findById(any());
    }

    @Test
    @DisplayName("A goal that does not exist reads exactly like one owned by someone else")
    void missingAndForeignAreIndistinguishable() {
        when(goalRepository.findByIdAndUserId(any(), eq(CURRENT_USER))).thenReturn(Optional.empty());
        when(goalService.findAll()).thenReturn(List.of());

        assertThat(builder.build(99L)).isEqualTo(builder.build(123456L));
    }

    // ─── The sketch, and what has to be asked for (2026-08-30) ────────────────

    @Test
    @DisplayName("A long checklist is counted — its items are the part that grows without limit")
    void aLongChecklistIsCounted() {
        // The owner's question: "I ask it to add up the calories in my breakfast — why does it
        // need my whole goal for that?" A shopping list is exactly the part that grows without
        // limit while being least often what a message is about.
        Goal goal = goal(1L, "Bake a cake", 7, null);
        goal.getTargets().add(checklist(50L, "Shopping", List.of(
                "Levain chocolate 548 kcal/100 g", "Marabou chocolate 525 kcal/100 g",
                "Butter 745 kcal/100 g", "Flour 364 kcal/100 g", "Eggs 155 kcal/100 g",
                "Sugar 387 kcal/100 g", "Cream 340 kcal/100 g", "Vanilla 288 kcal/100 g",
                "Cocoa 228 kcal/100 g", "Berries 57 kcal/100 g")));
        when(goalRepository.findByIdAndUserId(1L, CURRENT_USER)).thenReturn(Optional.of(goal));

        String sketch = builder.build(1L);

        // The target, its id and its progress stay — only the items go.
        assertThat(sketch).contains("[checklist id=50] Shopping: 0/10 done");
        assertThat(sketch).doesNotContain("Levain").doesNotContain("Marabou");
        assertThat(sketch).contains("read_goal");
    }

    @Test
    @DisplayName("A SHORT checklist travels whole — nothing is banned, only measured")
    void aShortChecklistIsStillListed() {
        // A three-item checklist costs almost nothing to carry, and a goal that small should
        // reach the model exactly as it always did. Asking for it would cost a whole model call.
        Goal goal = goal(1L, "Ship the release", 7, null);
        goal.getTargets().add(checklist(50L, "Checks", List.of("Tests", "Changelog", "Tag")));
        when(goalRepository.findByIdAndUserId(1L, CURRENT_USER)).thenReturn(Optional.of(goal));

        String sketch = builder.build(1L);

        assertThat(sketch).contains("Tests").contains("Changelog").contains("Tag");
        // Nothing was left out, so the sketch does not claim anything was.
        assertThat(sketch).doesNotContain("Sketch:");
    }

    @Test
    @DisplayName("Many targets keep their id lines long after their items are dropped")
    void targetLinesSurviveWhenTheItemsDoNot() {
        // The ids on these lines are what nearly every change needs — completing a target,
        // setting progress, renaming one. Summarising them away buys a few hundred characters
        // and costs a round trip on the next message that touches a target.
        Goal goal = goal(1L, "Job hunt", 7, null);
        for (long i = 1; i <= 8; i++) {
            goal.getTargets().add(checklist(i, "Application round " + i,
                    List.of("Write the letter", "Send it", "Follow up", "Log the reply")));
        }
        when(goalRepository.findByIdAndUserId(1L, CURRENT_USER)).thenReturn(Optional.of(goal));

        String sketch = builder.build(1L);

        assertThat(sketch).contains("[checklist id=8] Application round 8: 0/4 done");
        assertThat(sketch).doesNotContain("Follow up");
        assertThat(sketch).contains("checklist items summarised");
    }

    @Test
    @DisplayName("read_goal \"targets\" is where the items and their ids actually are")
    void readGoalReturnsTheChecklistItems() {
        Goal goal = goal(1L, "Bake a cake", 7, null);
        goal.getTargets().add(checklist(50L, "Shopping", List.of("Levain chocolate", "Butter")));
        when(goalRepository.findByIdAndUserId(1L, CURRENT_USER)).thenReturn(Optional.of(goal));

        String section = builder.readSection(1L, GoalContextBuilder.Section.TARGETS);

        assertThat(section).contains("Levain chocolate").contains("Butter").contains("id=");
    }

    @Test
    @DisplayName("A short section still travels — a lookup costs a whole extra model call")
    void smallSectionsAreStillInlined() {
        // Fetching is not free: read_goal means another turn with the whole prompt and
        // conversation attached. Two options are cheaper carried than fetched.
        Goal goal = goal(1L, "Learn GraphQL", 7, null);
        goal.getOptions().add(option(1L, "Work through the tutorial", true));
        goal.getOptions().add(option(2L, "Build a toy API", false));
        when(goalRepository.findByIdAndUserId(1L, CURRENT_USER)).thenReturn(Optional.of(goal));

        assertThat(builder.build(1L))
                .contains("Work through the tutorial").contains("Build a toy API");
    }

    @Test
    @DisplayName("A few resources travel; many become a count of each kind")
    void resourcesFollowTheSameRuleAsEverythingElse() {
        // "Why re-send all the resources every time?" was right about the goal that prompted it
        // — a long list riding on every call — and the answer is the size rule, not a ban: two
        // notes cost almost nothing to carry and a whole round trip to learn about.
        Goal few = goal(1L, "Learn GraphQL", 7, null);
        few.getResources().add(resource(1L, "note", "Interview notes"));
        few.getResources().add(resource(2L, "link", "Official docs"));
        when(goalRepository.findByIdAndUserId(1L, CURRENT_USER)).thenReturn(Optional.of(few));

        assertThat(builder.build(1L)).contains("Interview notes").contains("Official docs");
    }

    @Test
    @DisplayName("A long section becomes a count, and says how to get the rest")
    void largeSectionsBecomeACount() {
        Goal goal = goal(1L, "Job hunt", 7, null);
        for (long i = 1; i <= 25; i++) {
            goal.getResources().add(resource(i, "note", "A note with a reasonably long title " + i));
        }
        when(goalRepository.findByIdAndUserId(1L, CURRENT_USER)).thenReturn(Optional.of(goal));

        String sketch = builder.build(1L);

        assertThat(sketch).contains("**Resources:** 25").contains("25 note");
        assertThat(sketch).doesNotContain("A note with a reasonably long title 7");
        assertThat(sketch).contains("read_goal \"resources\"");
        // And they are one read_goal away when the conversation is actually about them.
        assertThat(builder.readSection(1L, GoalContextBuilder.Section.RESOURCES))
                .contains("A note with a reasonably long title 7");
        // The whole sketch stays small — that is the point of all of this.
        assertThat(sketch.length()).isLessThan(1_200);
    }

    @Test
    @DisplayName("A long description is measured, not pasted")
    void aLongDescriptionIsSummarised() {
        Goal goal = goal(1L, "Write a book", 7, null);
        goal.setDescription("<p>" + "The plan, in some detail. ".repeat(40) + "</p>");
        when(goalRepository.findByIdAndUserId(1L, CURRENT_USER)).thenReturn(Optional.of(goal));

        String sketch = builder.build(1L);

        assertThat(sketch).contains("**Description:**").contains("characters");
        assertThat(sketch).doesNotContain("The plan, in some detail. The plan");
        assertThat(builder.readSection(1L, GoalContextBuilder.Section.DESCRIPTION))
                .contains("The plan, in some detail.");
    }

    @Test
    @DisplayName("An empty section answers, rather than returning silence")
    void anEmptySectionSaysSo() {
        // Silence reads to the model as a failed read, and it tries again — a second wasted
        // call for a section that is simply empty.
        Goal goal = goal(1L, "Learn GraphQL", 7, null);
        when(goalRepository.findByIdAndUserId(1L, CURRENT_USER)).thenReturn(Optional.of(goal));

        assertThat(builder.readSection(1L, GoalContextBuilder.Section.OPTIONS))
                .contains("nothing in \"options\"");
    }

    @Test
    @DisplayName("read_goal is owner-scoped too — it is reached through the model's own argument")
    void readSectionIsOwnerScoped() {
        when(goalRepository.findByIdAndUserId(99L, CURRENT_USER)).thenReturn(Optional.empty());

        assertThat(builder.readSection(99L, GoalContextBuilder.Section.ALL))
                .isEqualTo("That goal is not available.");
        verify(goalRepository, never()).findById(any());
    }

    @Test
    @DisplayName("An unknown section name loads the whole goal rather than failing")
    void anUnknownSectionFallsBackToAll() {
        assertThat(GoalContextBuilder.Section.parse("targets"))
                .isEqualTo(GoalContextBuilder.Section.TARGETS);
        assertThat(GoalContextBuilder.Section.parse("everything"))
                .isEqualTo(GoalContextBuilder.Section.ALL);
        assertThat(GoalContextBuilder.Section.parse(null))
                .isEqualTo(GoalContextBuilder.Section.ALL);
    }

    private static com.spiramindscape.backend.target.Target checklist(
            Long id, String title, List<String> items) {
        var t = new com.spiramindscape.backend.target.Target();
        t.setId(id);
        t.setTitle(title);
        t.setType("checklist");
        long itemId = 1;
        for (String text : items) {
            var item = new com.spiramindscape.backend.target.ChecklistItem();
            item.setId(itemId++);
            item.setText(text);
            t.getItems().add(item);
        }
        return t;
    }

    private static com.spiramindscape.backend.goal.Option option(
            Long id, String text, boolean selected) {
        var o = new com.spiramindscape.backend.goal.Option();
        o.setId(id);
        o.setText(text);
        o.setSelected(selected);
        return o;
    }

    private static com.spiramindscape.backend.resource.Resource resource(
            Long id, String type, String title) {
        var r = new com.spiramindscape.backend.resource.Resource();
        r.setId(id);
        r.setType(type);
        r.setTitle(title);
        return r;
    }

    private static Goal goal(Long id, String title, int confidence, LocalDate deadline) {
        Goal g = new Goal();
        g.setId(id);
        g.setTitle(title);
        g.setConfidence(confidence);
        if (deadline != null) {
            g.setDeadline(deadline.atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        }
        return g;
    }
}
