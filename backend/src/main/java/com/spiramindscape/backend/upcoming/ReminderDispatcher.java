package com.spiramindscape.backend.upcoming;

import com.spiramindscape.backend.push.web.WebPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One run of the reminders: every reminder whose moment has come is marked as fired, then each
 * affected event is pushed to its owner's browsers. Called every minute by Cloud Scheduler in
 * production ({@code POST /internal/reminders/dispatch}) and by {@link LocalReminderScheduler}
 * locally.
 *
 * <p>Idempotent: a reminder is sent when its computed moment has passed and is later than
 * {@code last_fired_for}, which this run then sets — so two runs in the same minute send it once.
 * Marking happens in its own transaction, before any network call, so a slow push service never
 * holds the rows.
 */
@Service
public class ReminderDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ReminderDispatcher.class);

    /**
     * A reminder found more than this late — the server was down, or the scheduler paused — is
     * marked as fired without a push: a notification about a moment long gone is noise. It still
     * shows on the "decide now" page, which is where an unanswered reminder waits.
     */
    static final Duration LATE_LIMIT = Duration.ofHours(1);

    private final UpcomingReminderRepository reminderRepository;
    private final WebPushService webPushService;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ReminderDispatcher(UpcomingReminderRepository reminderRepository, WebPushService webPushService,
                              PlatformTransactionManager transactionManager, Clock clock) {
        this.reminderRepository = reminderRepository;
        this.webPushService = webPushService;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public record Result(int fired, int pushed) {}

    private record Target(Long userId, Long eventId) {}

    public Result dispatch() {
        return dispatch(clock.instant());
    }

    public Result dispatch(Instant now) {
        Set<Target> targets = new LinkedHashSet<>();
        int fired = transactions.execute(status -> {
            int count = 0;
            List<UpcomingReminder> reminders = reminderRepository.findAllForDispatch();
            for (UpcomingReminder reminder : reminders) {
                ZoneId zone = ReminderSchedule.zone(reminder.getUser().getTimeZone());
                Instant fireAt = ReminderSchedule.fireAt(reminder, zone);
                boolean push;
                if (ReminderSchedule.isFreshFire(reminder, fireAt, now)) {
                    reminder.setLastFiredFor(fireAt);
                    reminder.setAnsweredAt(null);
                    reminder.setSnoozedUntil(null);
                    push = Duration.between(fireAt, now).compareTo(LATE_LIMIT) <= 0;
                } else if (ReminderSchedule.isSnoozeOver(reminder, now)) {
                    reminder.setSnoozedUntil(null);
                    push = true;
                } else {
                    continue;
                }
                count++;
                if (push && !"email".equals(reminder.getChannel())) {
                    targets.add(new Target(reminder.getUser().getId(), reminder.getEvent().getId()));
                }
            }
            return count;
        });

        int pushed = 0;
        for (Target target : targets) {
            // The event id only: the service worker fetches the words over the user's session.
            pushed += webPushService.sendToUser(target.userId(),
                    "{\"type\":\"reminder\",\"eventId\":\"" + target.eventId() + "\"}");
        }
        if (fired > 0) {
            log.info("reminders_dispatched fired={} events={} pushes={}", fired, targets.size(), pushed);
        }
        return new Result(fired, pushed);
    }
}
