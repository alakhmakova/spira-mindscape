package com.spiramindscape.backend.tools;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ToolDefinitionRepository extends JpaRepository<ToolDefinition, Long> {

    /** Ownership-scoped lookup — never load another user's tool. */
    Optional<ToolDefinition> findByIdAndAppUserId(Long id, Long appUserId);

    List<ToolDefinition> findByAppUserIdOrderByCreatedAtDesc(Long appUserId);

    List<ToolDefinition> findByAppUserIdAndGoalIdOrderByCreatedAtDesc(Long appUserId, Long goalId);

    long countByAppUserId(Long appUserId);
}
