package com.spiramindscape.backend.ai.cv;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Requirements are reached only through their application, which is itself
 * owner-scoped ({@link CvApplicationRepository}). Nothing here takes a user id
 * because nothing here should ever be called before the application has been
 * resolved for the current user.
 */
public interface CvRequirementRepository extends JpaRepository<CvRequirement, Long> {

    List<CvRequirement> findByApplicationIdOrderByQueueIndexAsc(Long applicationId);

    /** The next thing to ask about: the earliest still-pending row in queue order. */
    Optional<CvRequirement> findFirstByApplicationIdAndStatusOrderByQueueIndexAsc(
            Long applicationId, CvRequirement.Status status);

    /** Everything stated in the same cluster — asked together, because it is one demand. */
    List<CvRequirement> findByApplicationIdAndClusterOrderByQueueIndexAsc(
            Long applicationId, String cluster);

    List<CvRequirement> findByApplicationIdAndStatusOrderByQueueIndexAsc(
            Long applicationId, CvRequirement.Status status);

    long countByApplicationId(Long applicationId);

    long countByApplicationIdAndStatus(Long applicationId, CvRequirement.Status status);

    /** Re-deconstructing a posting replaces the list wholesale. */
    void deleteByApplicationId(Long applicationId);
}
