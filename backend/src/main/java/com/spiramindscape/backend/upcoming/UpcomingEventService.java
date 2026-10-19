package com.spiramindscape.backend.upcoming;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalService;
import com.spiramindscape.backend.graphql.input.CreateUpcomingEventInput;
import com.spiramindscape.backend.graphql.input.ReminderInput;
import com.spiramindscape.backend.graphql.input.UpdateUpcomingEventInput;
import com.spiramindscape.backend.target.ChecklistItem;
import com.spiramindscape.backend.target.ChecklistItemRepository;
import com.spiramindscape.backend.target.Target;
import com.spiramindscape.backend.target.TargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

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

    private static final Pattern HH_MM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Set<String> UNITS = Set.of("minute", "hour", "day", "week");
    private static final Set<String> CHANNELS = Set.of("notification", "email", "fullscreen");
    private static final Set<String> RESOLVED_STATUSES = Set.of("done", "wont_do");

    private final UpcomingEventRepository repository;
    private final GoalService goalService;
    private final TargetRepository targetRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

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
        applySchedule(event, user, input.time(), input.reminders());
        return UpcomingEventView.of(repository.save(event));
    }

    /**
     * Edits an event: its schedule (time and reminders, replaced as sent) and, for an important
     * date, its name and date. Owner-scoped like everything else here.
     */
    @Transactional
    public UpcomingEventView update(Long id, UpdateUpcomingEventInput input) {
        Objects.requireNonNull(input, "input is required");
        AppUser user = currentUserProvider.getCurrentUser();
        UpcomingEvent event = repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Upcoming event not found: " + id));
        if ("important".equals(event.getKind())) {
            if (input.title() != null) {
                String title = input.title().trim();
                if (title.isEmpty()) {
                    throw new IllegalArgumentException("Event name is required");
                }
                if (title.length() > UpcomingEvent.MAX_TITLE_LENGTH) {
                    throw new IllegalArgumentException(
                            "Event name must be " + UpcomingEvent.MAX_TITLE_LENGTH + " characters or fewer");
                }
                event.setTitle(title);
            }
            if (input.date() != null) {
                event.setEventDate(input.date());
            }
        }
        applySchedule(event, user, input.time(), input.reminders());
        return UpcomingEventView.of(repository.save(event));
    }

    /**
     * Sets the event's time and reminders, after validating all of it — nothing is written unless
     * every reminder passes. A reminder identical to one the event already has keeps its row, and
     * with it whether it has fired and been answered, so re-saving the form cannot make a reminder
     * that went off this morning go off again.
     */
    private void applySchedule(UpcomingEvent event, AppUser user, String time, List<ReminderInput> inputs) {
        LocalTime eventTime = parseTime(time);
        List<UpcomingReminder> wanted = new ArrayList<>();
        if (inputs != null) {
            for (ReminderInput input : inputs) {
                UpcomingReminder reminder = validReminder(input, eventTime != null);
                if (wanted.stream().noneMatch(w -> sameReminder(w, reminder))) {
                    wanted.add(reminder);
                }
            }
        }
        if (wanted.size() > UpcomingReminder.MAX_PER_EVENT) {
            throw new IllegalArgumentException(
                    "An event can have at most " + UpcomingReminder.MAX_PER_EVENT + " reminders");
        }

        // A changed time moves every fire, so nothing of the old state carries over.
        boolean timeChanged = !Objects.equals(event.getEventTime(), eventTime);
        event.setEventTime(eventTime);
        List<UpcomingReminder> current = event.getReminders();
        for (Iterator<UpcomingReminder> it = current.iterator(); it.hasNext(); ) {
            UpcomingReminder existing = it.next();
            if (timeChanged || wanted.stream().noneMatch(w -> sameReminder(w, existing))) {
                it.remove();
            }
        }
        for (UpcomingReminder reminder : wanted) {
            if (current.stream().noneMatch(c -> sameReminder(c, reminder))) {
                reminder.setEvent(event);
                reminder.setUser(user);
                // The reminders' own "now": a reminder never fires for a moment before this.
                reminder.setCreatedAt(clock.instant());
                current.add(reminder);
            }
        }
    }

    private static UpcomingReminder validReminder(ReminderInput input, boolean timed) {
        if (input == null || input.amount() == null) {
            throw new IllegalArgumentException("A reminder needs a number");
        }
        int amount = input.amount();
        if (amount < 0 || amount > UpcomingReminder.MAX_AMOUNT) {
            throw new IllegalArgumentException("A reminder's number must be from 0 to " + UpcomingReminder.MAX_AMOUNT);
        }
        String unit = input.unit() == null ? "" : input.unit().trim().toLowerCase(Locale.ROOT);
        if (!UNITS.contains(unit)) {
            throw new IllegalArgumentException("Unknown reminder unit");
        }
        String channel = input.channel() == null ? "notification" : input.channel().trim().toLowerCase(Locale.ROOT);
        if (!CHANNELS.contains(channel)) {
            throw new IllegalArgumentException("Unknown reminder channel");
        }
        Integer minute = input.atMinuteOfDay();
        if (timed) {
            if (minute != null) {
                throw new IllegalArgumentException("A time of day is only for reminders of all-day events");
            }
        } else {
            if (unit.equals("minute") || unit.equals("hour")) {
                throw new IllegalArgumentException("Minutes and hours before need the event to have a time");
            }
            if (minute == null) {
                minute = UpcomingReminder.DEFAULT_MINUTE_OF_DAY;
            }
            if (minute < 0 || minute > 1439) {
                throw new IllegalArgumentException("A reminder's time of day must be from 00:00 to 23:59");
            }
        }
        UpcomingReminder reminder = new UpcomingReminder();
        reminder.setAmount(amount);
        reminder.setUnit(unit);
        reminder.setAtMinuteOfDay(minute);
        reminder.setChannel(channel);
        return reminder;
    }

    private static boolean sameReminder(UpcomingReminder a, UpcomingReminder b) {
        return a.getAmount() == b.getAmount()
                && a.getUnit().equals(b.getUnit())
                && Objects.equals(a.getAtMinuteOfDay(), b.getAtMinuteOfDay())
                && a.getChannel().equals(b.getChannel());
    }

    private static LocalTime parseTime(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        String trimmed = time.trim();
        if (!HH_MM.matcher(trimmed).matches()) {
            // A fixed message: the value the client sent is not echoed back.
            throw new IllegalArgumentException("Event time must be HH:mm");
        }
        return LocalTime.parse(trimmed);
    }

    /**
     * Settles the event (owner, 2026-10-19): {@code done} or {@code wont_do}. It leaves the Upcoming
     * column, stays on the timeline with its mark, and its reminders never fire again. Ticking a
     * linked task or target is the client's, through the goal store, as every goal edit is.
     */
    @Transactional
    public UpcomingEventView resolve(Long id, String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        if (!RESOLVED_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Unknown event status");
        }
        Long userId = currentUserProvider.getCurrentUser().getId();
        UpcomingEvent event = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Upcoming event not found: " + id));
        Instant now = clock.instant();
        event.setStatus(normalized);
        event.setResolvedAt(now);
        for (UpcomingReminder reminder : event.getReminders()) {
            reminder.setSnoozedUntil(null); // nothing left to come back for
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
