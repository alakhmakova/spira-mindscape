package com.spiramindscape.backend.ai.cv;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CvSourceReadRepository extends JpaRepository<CvSourceRead, Long> {

    List<CvSourceRead> findByApplicationIdOrderByIdAsc(Long applicationId);

    Optional<CvSourceRead> findByApplicationIdAndSourceKey(Long applicationId, String sourceKey);

    long countByApplicationIdAndOkTrue(Long applicationId);

    void deleteByApplicationId(Long applicationId);
}
