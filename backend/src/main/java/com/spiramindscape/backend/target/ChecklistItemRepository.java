package com.spiramindscape.backend.target;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {
    List<ChecklistItem> findByTargetIdOrderByCreatedAtAsc(Long targetId);
    List<ChecklistItem> findByTargetIdInOrderByTargetIdAscCreatedAtAsc(List<Long> targetIds);
}
