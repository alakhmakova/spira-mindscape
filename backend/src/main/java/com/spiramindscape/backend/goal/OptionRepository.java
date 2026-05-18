package com.spiramindscape.backend.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptionRepository extends JpaRepository<Option, Long> {
    List<Option> findByGoalIdOrderByPositionAscCreatedAtAsc(Long goalId);
    List<Option> findByGoalIdInOrderByGoalIdAscPositionAscCreatedAtAsc(List<Long> goalIds);

    @Query("SELECT COALESCE(MAX(o.position), -1) FROM Option o WHERE o.goal.id = :goalId")
    int findMaxPositionByGoalId(Long goalId);
}
