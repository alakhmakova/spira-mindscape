package com.spiramindscape.backend.target;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TargetRepository extends JpaRepository<Target, Long> {
    List<Target> findByGoalIdOrderByCreatedAtAsc(Long goalId);
    List<Target> findByGoalIdInOrderByGoalIdAscCreatedAtAsc(List<Long> goalIds);
}
