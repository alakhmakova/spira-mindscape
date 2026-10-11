package com.spiramindscape.backend.upcoming;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the dispatcher every minute inside the app — for local development only. Production scales
 * to zero, where an in-process timer would not run while nobody uses the app; there Cloud
 * Scheduler calls {@code POST /internal/reminders/dispatch} instead. Off unless
 * {@code spira.reminders.local-scheduler=true} (the {@code local} profile sets it).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "spira.reminders.local-scheduler", havingValue = "true")
@RequiredArgsConstructor
public class LocalReminderScheduler {

    private final ReminderDispatcher dispatcher;

    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void run() {
        dispatcher.dispatch();
    }
}
