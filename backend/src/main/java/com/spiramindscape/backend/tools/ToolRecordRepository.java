package com.spiramindscape.backend.tools;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ToolRecordRepository extends JpaRepository<ToolRecord, Long> {

    List<ToolRecord> findByToolDefIdOrderByCreatedAtAsc(Long toolDefId);

    Optional<ToolRecord> findByIdAndToolDefId(Long id, Long toolDefId);

    long countByToolDefId(Long toolDefId);

    void deleteByToolDefId(Long toolDefId);
}
