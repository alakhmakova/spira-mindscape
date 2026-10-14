package com.spiramindscape.backend.upcoming;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalService;
import com.spiramindscape.backend.graphql.input.CreateUpcomingEventInput;
import com.spiramindscape.backend.target.ChecklistItem;
import com.spiramindscape.backend.target.ChecklistItemRepository;
import com.spiramindscape.backend.target.Target;
import com.spiramindscape.backend.target.TargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UpcomingEventService} in isolation: every rule it enforces, with the repositories
 * mocked. The GraphQL round trip, the cascades and the second user are
 * {@code UpcomingEventIntegrationTest}'s; this is the validation and the ownership logic, one
 * rule per test, so a failure names the rule that broke.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UpcomingEventServiceTest {

    private static final Instant DEADLINE = Instant.parse("2026-12-31T00:00:00Z");

    @Mock private UpcomingEventRepository repository;
    @Mock private GoalService goalService;
    @Mock private TargetRepository targetRepository;
    @Mock private ChecklistItemRepository checklistItemRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private Clock clock;

    @InjectMocks private UpcomingEventService service;

    private AppUser me;
    private AppUser someoneElse;

    @BeforeEach
    void signIn() {
        me = user(1L);
        someoneElse = user(2L);
        when(currentUserProvider.getCurrentUser()).thenReturn(me);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-11T12:00:00Z"));
        when(repository.save(any(UpcomingEvent.class))).thenAnswer(inv -> {
            UpcomingEvent e = inv.getArgument(0);
            e.setId(100L);
            return e;
        });
    }

    // ─── important dates ──────────────────────────────────────────────────────

    @Test
    @DisplayName("An important date is saved trimmed, with its date, for the current user")
    void createsImportantDate() {
        UpcomingEventView view = service.create(important("  Mom's birthday  ", DEADLINE));

        UpcomingEvent saved = captureSaved();
        assertThat(saved.getUser()).isSameAs(me);
        assertThat(saved.getKind()).isEqualTo("important");
        assertThat(saved.getTitle()).isEqualTo("Mom's birthday");
        assertThat(saved.getEventDate()).isEqualTo(DEADLINE);
        assertThat(view.id()).isEqualTo(100L);
        assertThat(view.goalId()).isNull();
    }

    @Test
    @DisplayName("An important date needs a name that is not blank")
    void importantDateNeedsAName() {
        assertRefused(important("   ", DEADLINE), "Event name is required");
        assertRefused(important(null, DEADLINE), "Event name is required");
    }

    @Test
    @DisplayName("An important date's name may be 200 characters, not 201")
    void importantDateNameLimit() {
        service.create(important("x".repeat(UpcomingEvent.MAX_TITLE_LENGTH), DEADLINE));
        assertRefused(important("x".repeat(UpcomingEvent.MAX_TITLE_LENGTH + 1), DEADLINE),
                "200 characters or fewer");
    }

    @Test
    @DisplayName("An important date needs a date")
    void importantDateNeedsADate() {
        assertRefused(important("Trip", null), "Event date is required");
    }

    // ─── the type ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("The type is required, case does not matter, and an unknown one is refused without echoing it")
    void kind() {
        assertRefused(new CreateUpcomingEventInput(null, "Trip", DEADLINE, null, null, null, null, null),
                "Event type is required");
        assertRefused(new CreateUpcomingEventInput("recurring", "Trip", DEADLINE, null, null, null, null, null),
                "Unknown event type");
        assertThatThrownBy(() -> service.create(
                new CreateUpcomingEventInput("<script>", "Trip", DEADLINE, null, null, null, null, null)))
                .hasMessage("Unknown event type");

        service.create(new CreateUpcomingEventInput(" IMPORTANT ", "Trip", DEADLINE, null, null, null, null, null));
        assertThat(captureSaved().getKind()).isEqualTo("important");
    }

    // ─── links ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("A goal link goes through the owner-scoped lookup and stores no title or date")
    void linksAGoal() {
        Goal goal = goal(10L, me, DEADLINE);
        when(goalService.findById(10L)).thenReturn(goal);

        service.create(link("goal", 10L, null, null));

        UpcomingEvent saved = captureSaved();
        assertThat(saved.getGoal()).isSameAs(goal);
        assertThat(saved.getTitle()).isNull();
        assertThat(saved.getEventDate()).isNull();
    }

    @Test
    @DisplayName("A link needs the id of what it points at")
    void linkNeedsAnId() {
        assertRefused(link("goal", null, null, null), "goalId is required");
        assertRefused(link("target", null, null, null), "targetId is required");
        assertRefused(link("task", null, null, null), "taskId is required");
    }

    @Test
    @DisplayName("Only an item with a deadline can be linked")
    void linkNeedsADeadline() {
        when(goalService.findById(10L)).thenReturn(goal(10L, me, null));
        assertRefused(link("goal", 10L, null, null), "This goal has no deadline");

        Target target = target(20L, goal(10L, me, null), null);
        when(targetRepository.findById(20L)).thenReturn(Optional.of(target));
        assertRefused(link("target", null, 20L, null), "This target has no deadline");

        when(checklistItemRepository.findById(30L)).thenReturn(Optional.of(task(30L, target, null)));
        assertRefused(link("task", null, null, 30L), "This task has no deadline");
    }

    @Test
    @DisplayName("Another user's target or task is 'not found' — the same answer as a missing one")
    void cannotLinkSomeoneElsesItems() {
        Target theirs = target(20L, goal(10L, someoneElse, DEADLINE), DEADLINE);
        when(targetRepository.findById(20L)).thenReturn(Optional.of(theirs));
        when(checklistItemRepository.findById(30L)).thenReturn(Optional.of(task(30L, theirs, DEADLINE)));
        when(targetRepository.findById(21L)).thenReturn(Optional.empty());

        assertRefused(link("target", null, 20L, null), "Target not found: 20");
        assertRefused(link("target", null, 21L, null), "Target not found: 21");
        assertRefused(link("task", null, null, 30L), "Task not found: 30");
    }

    @Test
    @DisplayName("The same item cannot be linked twice")
    void noDuplicateLinks() {
        when(goalService.findById(10L)).thenReturn(goal(10L, me, DEADLINE));
        when(repository.existsByUserIdAndGoalId(1L, 10L)).thenReturn(true);

        assertRefused(link("goal", 10L, null, null), "already in Upcoming");
    }

    @Test
    @DisplayName("An account holds at most MAX_EVENTS_PER_USER events")
    void cap() {
        when(repository.countByUserId(1L)).thenReturn((long) UpcomingEventService.MAX_EVENTS_PER_USER);

        assertRefused(important("Trip", DEADLINE), "at most " + UpcomingEventService.MAX_EVENTS_PER_USER);
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Deleting a goal link clears the goal's deadline and removes the event")
    void deletingALinkClearsTheDeadline() {
        Goal goal = goal(10L, me, DEADLINE);
        UpcomingEvent event = event(5L, "goal");
        event.setGoal(goal);
        when(repository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(event));

        service.delete(5L);

        assertThat(goal.getDeadline()).isNull();
        verify(repository).delete(event);
    }

    @Test
    @DisplayName("Deleting a task link clears the task's deadline, and leaves its target's alone")
    void deletingATaskLinkLeavesTheTarget() {
        Target target = target(20L, goal(10L, me, DEADLINE), DEADLINE);
        ChecklistItem task = task(30L, target, DEADLINE);
        UpcomingEvent event = event(5L, "task");
        event.setChecklistItem(task);
        when(repository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.of(event));

        service.delete(5L);

        assertThat(task.getDeadline()).isNull();
        assertThat(target.getDeadline()).isEqualTo(DEADLINE);
    }

    @Test
    @DisplayName("Deleting another user's event — or a missing one — is 'not found', and deletes nothing")
    void cannotDeleteSomeoneElsesEvent() {
        when(repository.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Upcoming event not found: 5");
        verify(repository, never()).delete(any(UpcomingEvent.class));
    }

    @Test
    @DisplayName("The list is the current user's own")
    void listsOnlyMine() {
        UpcomingEvent mine = event(5L, "important");
        mine.setTitle("Trip");
        when(repository.findAllForUser(1L)).thenReturn(List.of(mine));

        assertThat(service.findAll()).extracting(UpcomingEventView::id).containsExactly(5L);
        verify(repository).findAllForUser(1L);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private void assertRefused(CreateUpcomingEventInput input, String message) {
        assertThatThrownBy(() -> service.create(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(message);
    }

    private UpcomingEvent captureSaved() {
        ArgumentCaptor<UpcomingEvent> captor = ArgumentCaptor.forClass(UpcomingEvent.class);
        verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private static CreateUpcomingEventInput important(String title, Instant date) {
        return new CreateUpcomingEventInput("important", title, date, null, null, null, null, null);
    }

    private static CreateUpcomingEventInput link(String kind, Long goalId, Long targetId, Long taskId) {
        return new CreateUpcomingEventInput(kind, null, null, goalId, targetId, taskId, null, null);
    }

    private static AppUser user(Long id) {
        AppUser user = new AppUser();
        user.setId(id);
        return user;
    }

    private static Goal goal(Long id, AppUser owner, Instant deadline) {
        Goal goal = new Goal();
        goal.setId(id);
        goal.setUser(owner);
        goal.setDeadline(deadline);
        return goal;
    }

    private static Target target(Long id, Goal goal, Instant deadline) {
        Target target = new Target();
        target.setId(id);
        target.setGoal(goal);
        target.setDeadline(deadline);
        return target;
    }

    private static ChecklistItem task(Long id, Target target, Instant deadline) {
        ChecklistItem item = new ChecklistItem();
        item.setId(id);
        item.setTarget(target);
        item.setDeadline(deadline);
        return item;
    }

    private UpcomingEvent event(Long id, String kind) {
        UpcomingEvent event = new UpcomingEvent();
        event.setId(id);
        event.setKind(kind);
        event.setUser(me);
        return event;
    }
}
