package com.spiramindscape.backend.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RealityRepository extends JpaRepository<RealityItem, Long> {
    List<RealityItem> findByGoalIdOrderByCreatedAtAsc(Long goalId);
    List<RealityItem> findByGoalIdAndKindOrderByCreatedAtAsc(Long goalId, String kind);
    List<RealityItem> findByGoalIdInOrderByGoalIdAscCreatedAtAsc(List<Long> goalIds);
}
