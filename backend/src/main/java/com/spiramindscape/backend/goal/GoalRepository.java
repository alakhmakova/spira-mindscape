package com.spiramindscape.backend.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {

    /** All goals owned by the given user, oldest first. Used by the {@code goals} query. */
    List<Goal> findByUserIdOrderByCreatedAtAsc(Long userId);

    /**
     * Find a goal by id that also belongs to the given user.
     * Returns empty if the goal does not exist OR belongs to a different user —
     * so cross-user access and missing goals are indistinguishable (NOT_FOUND).
     */
    Optional<Goal> findByIdAndUserId(Long id, Long userId);
}
