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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The Upcoming column's events, owner-scoped throughout: every lookup goes through the current
 * user, and a goal, target or checklist item that is missing and one that belongs to someone else
 * give the same "not found" — the caller cannot tell the two apart.
 */
@Service
@RequiredArgsConstructor
public class UpcomingEventService {

    /** Generous for a column that shows ten; it only bounds what one account can store. */
    public static final int MAX_EVENTS_PER_USER = 500;

    private final UpcomingEventRepository repository;
    private final GoalService goalService;
    private final TargetRepository targetRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<UpcomingEventView> findAll() {
        Long userId = currentUserProvider.getCurrentUser().getId();
        return repository.findAllForUser(userId).stream().map(UpcomingEventView::of).toList();
    }

    @Transactional
    public UpcomingEventView create(CreateUpcomingEventInput input) {
        Objects.requireNonNull(input, "input is required");
        AppUser user = currentUserProvider.getCurrentUser();
        if (repository.countByUserId(user.getId()) >= MAX_EVENTS_PER_USER) {
            throw new IllegalArgumentException(
                    "You can keep at most " + MAX_EVENTS_PER_USER + " upcoming events");
        }

        UpcomingEvent event = new UpcomingEvent();
        event.setUser(user);
        String kind = normalizeKind(input.kind());
        event.setKind(kind);

        switch (kind) {
            case "important" -> {
                String title = input.title() == null ? "" : input.title().trim();
                if (title.isEmpty()) {
                    throw new IllegalArgumentException("Event name is required");
                }
                if (title.length() > UpcomingEvent.MAX_TITLE_LENGTH) {
                    throw new IllegalArgumentException(
                            "Event name must be " + UpcomingEvent.MAX_TITLE_LENGTH + " characters or fewer");
                }
                if (input.date() == null) {
                    throw new IllegalArgumentException("Event date is required");
                }
                event.setTitle(title);
                event.setEventDate(input.date());
            }
            case "goal" -> {
                Goal goal = goalService.findById(require(input.goalId(), "goalId")); // owner-scoped
                requireDeadline(goal.getDeadline(), "goal");
                if (repository.existsByUserIdAndGoalId(user.getId(), goal.getId())) {
                    throw new IllegalArgumentException("This goal is already in Upcoming");
                }
                event.setGoal(goal);
            }
            case "target" -> {
                Target target = ownedTarget(require(input.targetId(), "targetId"), user.getId());
                requireDeadline(target.getDeadline(), "target");
                if (repository.existsByUserIdAndTargetId(user.getId(), target.getId())) {
                    throw new IllegalArgumentException("This target is already in Upcoming");
                }
                event.setTarget(target);
            }
            case "task" -> {
                ChecklistItem item = ownedChecklistItem(require(input.taskId(), "taskId"), user.getId());
                requireDeadline(item.getDeadline(), "task");
                if (repository.existsByUserIdAndChecklistItemId(user.getId(), item.getId())) {
                    throw new IllegalArgumentException("This task is already in Upcoming");
                }
                event.setChecklistItem(item);
            }
            // A fixed message: the value the client sent is not echoed back.
            default -> throw new IllegalArgumentException("Unknown event type");
        }
        return UpcomingEventView.of(repository.save(event));
    }

    /**
     * Removes the event — and, for a link, the deadline it stood for (owner, 2026-10-09: removing a
     * linked date from Upcoming removes that goal's, target's or task's deadline; the confirm dialog
     * says so before it happens). Both in one transaction, so neither can happen without the other.
     */
    @Transactional
    public void delete(Long id) {
        Long userId = currentUserProvider.getCurrentUser().getId();
        UpcomingEvent event = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Upcoming event not found: " + id));
        if (event.getGoal() != null) {
            event.getGoal().setDeadline(null);
        }
        if (event.getTarget() != null) {
            event.getTarget().setDeadline(null);
        }
        if (event.getChecklistItem() != null) {
            event.getChecklistItem().setDeadline(null);
        }
        repository.delete(event);
    }

    private Target ownedTarget(Long id, Long userId) {
        return targetRepository.findById(id)
                .filter(target -> target.getGoal().getUser().getId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Target not found: " + id));
    }

    private ChecklistItem ownedChecklistItem(Long id, Long userId) {
        return checklistItemRepository.findById(id)
                .filter(item -> item.getTarget().getGoal().getUser().getId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
    }

    /**
     * A link stands for a deadline (owner, 2026-10-09: only goals, targets and tasks that have one
     * can be added). The form only offers those; this refuses the same thing from any other client.
     */
    private static void requireDeadline(Instant deadline, String what) {
        if (deadline == null) {
            throw new IllegalArgumentException("This " + what + " has no deadline to add");
        }
    }

    private static Long require(Long value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String normalizeKind(String kind) {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("Event type is required");
        }
        return kind.trim().toLowerCase(Locale.ROOT);
    }
}
