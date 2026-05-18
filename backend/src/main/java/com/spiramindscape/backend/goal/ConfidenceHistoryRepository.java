package com.spiramindscape.backend.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfidenceHistoryRepository extends JpaRepository<ConfidenceHistory, Long> {
    List<ConfidenceHistory> findByGoalIdOrderByAtDesc(Long goalId);
    List<ConfidenceHistory> findByGoalIdInOrderByAtDesc(List<Long> goalIds);
}
