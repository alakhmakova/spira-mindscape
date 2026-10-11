package com.spiramindscape.backend.upcoming;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.AppUserRepository;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The user's side of reminders: which are due now, answering them, and the time zone they are
 * computed in. Owner-scoped throughout — another user's event is "not found", as everywhere else.
 */
@Service
@RequiredArgsConstructor
public class ReminderService {

    public static final Duration SNOOZE = Duration.ofMinutes(10);

    private static final DateTimeFormatter FAR_DATE =
            DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);

    private final UpcomingReminderRepository reminderRepository;
    private final UpcomingEventRepository eventRepository;
    private final AppUserRepository appUserRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    /** Every event of the current user with a reminder that is due, the longest-waiting first. */
    @Transactional(readOnly = true)
    public List<DueReminderView> due() {
        AppUser user = freshUser();
        ZoneId zone = ReminderSchedule.zone(user.getTimeZone());
        Instant now = clock.instant();
        Map<Long, DueReminderView> byEvent = new LinkedHashMap<>();
        for (UpcomingReminder reminder : reminderRepository.findAllForUser(user.getId())) {
            Instant fireAt = ReminderSchedule.fireAt(reminder, zone);
            if (!ReminderSchedule.isDue(reminder, fireAt, now)) {
                continue;
            }
            UpcomingEvent event = reminder.getEvent();
            DueReminderView view = new DueReminderView(event.getId(), event.getKind(), titleOf(event),
                    event.effectiveDate(), UpcomingEventView.formatTime(event.getEventTime()),
                    goalIdOf(event), fireAt);
            byEvent.merge(event.getId(), view,
                    (a, b) -> a.firedAt().isAfter(b.firedAt()) ? a : b); // the latest fire speaks for the event
        }
        List<DueReminderView> due = new ArrayList<>(byEvent.values());
        due.sort(Comparator.comparing(DueReminderView::firedAt));
        return due;
    }

    /**
     * Answers every due reminder of one event. {@code done} and {@code open} settle it;
     * {@code snooze} quiets it for {@link #SNOOZE}, after which it goes off again. Returns whether
     * anything was due — answering twice, from two devices, is harmless.
     */
    @Transactional
    public boolean answer(Long eventId, String action) {
        String normalized = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        if (!List.of("done", "open", "snooze").contains(normalized)) {
            throw new IllegalArgumentException("Unknown reminder answer");
        }
        AppUser user = freshUser();
        UpcomingEvent event = eventRepository.findByIdAndUserId(eventId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Upcoming event not found: " + eventId));
        ZoneId zone = ReminderSchedule.zone(user.getTimeZone());
        Instant now = clock.instant();
        boolean any = false;
        for (UpcomingReminder reminder : event.getReminders()) {
            Instant fireAt = ReminderSchedule.fireAt(reminder, zone);
            if (!ReminderSchedule.isDue(reminder, fireAt, now)) {
                continue;
            }
            any = true;
            if (reminder.getLastFiredFor() == null || fireAt.isAfter(reminder.getLastFiredFor())) {
                reminder.setLastFiredFor(fireAt); // so the dispatcher does not send it after the fact
            }
            if (normalized.equals("snooze")) {
                reminder.setAnsweredAt(null);
                reminder.setSnoozedUntil(now.plus(SNOOZE));
            } else {
                reminder.setAnsweredAt(now);
                reminder.setSnoozedUntil(null);
            }
        }
        return any;
    }

    /** Records the IANA zone the client runs in. Region ids only ("Europe/Berlin"), never offsets. */
    @Transactional
    public boolean setTimeZone(String timeZone) {
        String zone = timeZone == null ? "" : timeZone.trim();
        if (zone.length() > 64 || !ZoneId.getAvailableZoneIds().contains(zone)) {
            throw new IllegalArgumentException("Unknown time zone");
        }
        AppUser user = freshUser();
        if (!zone.equals(user.getTimeZone())) {
            user.setTimeZone(zone);
            appUserRepository.save(user);
        }
        // The session keeps its own copy of the user; keep it in step for this session's requests.
        currentUserProvider.getCurrentUser().setTimeZone(zone);
        return true;
    }

    /** The notification's words for one of the current user's events, or empty when it is not theirs. */
    @Transactional(readOnly = true)
    public Optional<ReminderNotice> notice(Long eventId) {
        AppUser user = freshUser();
        return eventRepository.findByIdAndUserId(eventId, user.getId())
                .map(event -> notice(event, ReminderSchedule.zone(user.getTimeZone()), clock.instant()));
    }

    /**
     * Title = the event's name; body = when, then what: "Today at 14:00 · Goal", "Tomorrow ·
     * Target", "In 7 days · Important date". English, no emoji, like the rest of the app.
     */
    static ReminderNotice notice(UpcomingEvent event, ZoneId zone, Instant now) {
        String when = "";
        Instant date = event.effectiveDate();
        if (date != null) {
            LocalDate day = date.atZone(zone).toLocalDate();
            long days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), day);
            if (days == 0) when = "Today";
            else if (days == 1) when = "Tomorrow";
            else if (days == -1) when = "Yesterday";
            else if (days > 1 && days <= 7) when = "In " + days + " days";
            else when = day.format(FAR_DATE);
            if (event.getEventTime() != null) {
                when += " at " + UpcomingEventView.formatTime(event.getEventTime());
            }
        }
        String what = switch (event.getKind()) {
            case "goal" -> "Goal";
            case "target" -> "Target";
            case "task" -> "Task";
            default -> "Important date";
        };
        return new ReminderNotice(event.getId(), titleOf(event), when.isEmpty() ? what : when + " · " + what);
    }

    static String titleOf(UpcomingEvent event) {
        String title = event.effectiveTitle();
        return title == null || title.isBlank() ? "Upcoming date" : title;
    }

    static Long goalIdOf(UpcomingEvent event) {
        if (event.getGoal() != null) return event.getGoal().getId();
        if (event.getTarget() != null) return event.getTarget().getGoal().getId();
        if (event.getChecklistItem() != null) return event.getChecklistItem().getTarget().getGoal().getId();
        return null;
    }

    /** The user as stored — the session's copy can be older than a zone set from another device. */
    private AppUser freshUser() {
        Long id = currentUserProvider.getCurrentUser().getId();
        return appUserRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Signed-in user not found"));
    }
}
