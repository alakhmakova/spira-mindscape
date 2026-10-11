package com.spiramindscape.backend.upcoming;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UpcomingEventRepository extends JpaRepository<UpcomingEvent, Long> {

    @Query("""
            SELECT e FROM UpcomingEvent e
            LEFT JOIN FETCH e.goal
            LEFT JOIN FETCH e.target
            LEFT JOIN FETCH e.checklistItem
            WHERE e.user.id = :userId
            ORDER BY e.createdAt ASC
            """)
    List<UpcomingEvent> findAllForUser(Long userId);

    Optional<UpcomingEvent> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    boolean existsByUserIdAndGoalId(Long userId, Long goalId);

    boolean existsByUserIdAndTargetId(Long userId, Long targetId);

    boolean existsByUserIdAndChecklistItemId(Long userId, Long checklistItemId);
}
