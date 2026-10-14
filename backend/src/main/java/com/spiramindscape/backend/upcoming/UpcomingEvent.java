package com.spiramindscape.backend.upcoming;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.target.ChecklistItem;
import com.spiramindscape.backend.target.Target;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One row of the All-goals page's Upcoming column (V40). Either a date of the user's own
 * ({@code kind = important}: a title and a date) or a link to a goal, a target or a checklist item
 * ({@code kind = goal | target | task}), whose date is that item's deadline, read live.
 *
 * <p>Every link is {@code ON DELETE CASCADE}, in the Flyway DDL and — through {@link OnDelete} —
 * in the schema Hibernate builds for the tests, so an event cannot outlive what it points at.
 */
@Entity
@Table(name = "upcoming_event")
@Getter
@Setter
public class UpcomingEvent {

    public static final int MAX_TITLE_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AppUser user;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "event_date")
    private Instant eventDate;

    /**
     * An optional time of day (V41), for any kind. It belongs to this entry, not to the item: a
     * linked deadline stays a day everywhere else, and moving it keeps this time. Null = all day.
     */
    @Column(name = "event_time")
    private LocalTime eventTime;

    @OneToMany(mappedBy = "event", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<UpcomingReminder> reminders = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goal_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Goal goal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Target target;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_item_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ChecklistItem checklistItem;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * The date this event stands for: its own for an important date, the item's deadline — read
     * live — for a link. Null when a link's deadline has been cleared.
     */
    public Instant effectiveDate() {
        if (goal != null) return goal.getDeadline();
        if (target != null) return target.getDeadline();
        if (checklistItem != null) return checklistItem.getDeadline();
        return eventDate;
    }

    /** Its name: its own for an important date, the item's for a link. */
    public String effectiveTitle() {
        if (goal != null) return goal.getTitle();
        if (target != null) return target.getTitle();
        if (checklistItem != null) return checklistItem.getText();
        return title;
    }

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
