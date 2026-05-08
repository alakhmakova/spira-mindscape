package com.spiramindscape.backend.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {
    List<Resource> findByGoalIdOrderByCreatedAtAsc(Long goalId);
    List<Resource> findByGoalIdInOrderByGoalIdAscCreatedAtAsc(List<Long> goalIds);
}
