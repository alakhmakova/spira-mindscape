package com.spiramindscape.backend.upcoming;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UpcomingReminderRepository extends JpaRepository<UpcomingReminder, Long> {

    /** One user's reminders, with everything their fire time is computed from. */
    @Query("""
            SELECT r FROM UpcomingReminder r
            JOIN FETCH r.event e
            LEFT JOIN FETCH e.goal
            LEFT JOIN FETCH e.target
            LEFT JOIN FETCH e.checklistItem
            WHERE r.user.id = :userId
            """)
    List<UpcomingReminder> findAllForUser(Long userId);

    /**
     * Every reminder in the app, for the dispatcher. Bounded by the caps — five per event, 500
     * events per user — so a run reads at most a few thousand rows for an app of this size.
     */
    @Query("""
            SELECT r FROM UpcomingReminder r
            JOIN FETCH r.event e
            JOIN FETCH r.user
            LEFT JOIN FETCH e.goal
            LEFT JOIN FETCH e.target
            LEFT JOIN FETCH e.checklistItem
            """)
    List<UpcomingReminder> findAllForDispatch();
}
