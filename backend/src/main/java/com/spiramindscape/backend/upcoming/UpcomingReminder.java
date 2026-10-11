package com.spiramindscape.backend.upcoming;

import com.spiramindscape.backend.auth.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

/**
 * One reminder of an {@link UpcomingEvent} (V41): "{@code amount} {@code unit}s before", and for an
 * all-day event "… at {@code atMinuteOfDay}". The moment it fires is computed by
 * {@link ReminderSchedule} on every run and never stored, because a link's date is the item's
 * deadline, read live.
 *
 * <p>Three fields are its delivery state:
 * <ul>
 *   <li>{@code lastFiredFor} — the fire instant last sent (or answered). A reminder fires when the
 *       computed instant has passed and is later than this, so a deadline moved later re-arms it
 *       and one moved earlier cannot fire twice.</li>
 *   <li>{@code answeredAt} — set when the fire in {@code lastFiredFor} is answered (Done or Open);
 *       null while it is due and unanswered, which is what the "decide now" page blocks on.</li>
 *   <li>{@code snoozedUntil} — a Snooze; the reminder is quiet until then and fires again after.</li>
 * </ul>
 */
@Entity
@Table(name = "upcoming_reminder")
@Getter
@Setter
public class UpcomingReminder {

    public static final int MAX_AMOUNT = 999;
    public static final int MAX_PER_EVENT = 5;
    /** "At 09:00" — what an all-day reminder means when no time of day is given. */
    public static final int DEFAULT_MINUTE_OF_DAY = 9 * 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UpcomingEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AppUser user;

    @Column(nullable = false)
    private int amount;

    /** minute | hour | day | week */
    @Column(nullable = false, length = 10)
    private String unit;

    @Column(name = "at_minute_of_day")
    private Integer atMinuteOfDay;

    /** notification | email | fullscreen — only {@code notification} has a sender today. */
    @Column(nullable = false, length = 20)
    private String channel;

    @Column(name = "last_fired_for")
    private Instant lastFiredFor;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "snoozed_until")
    private Instant snoozedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
