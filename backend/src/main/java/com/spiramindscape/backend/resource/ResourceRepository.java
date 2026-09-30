package com.spiramindscape.backend.resource;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceRepository extends JpaRepository<Resource, Long> {

    /**
     * Which of these resource ids still exist on this goal.
     *
     * <p>For pointers held outside the resource table — a CV application naming the notes
     * it produced — where the user may delete the note at any time and nothing tells the
     * holder. Scoped by goal so an id from elsewhere cannot be confirmed through it.
     */
    @Query("SELECT r.id FROM Resource r WHERE r.goal.id = :goalId AND r.id IN :ids")
    List<Long> findExistingIds(@Param("goalId") Long goalId, @Param("ids") List<Long> ids);

    /**
     * Metadata for one goal's resources, without the {@code data_url} bytes — see
     * {@link ResourceView}. Used by the {@code resourcesByGoal} query.
     */
    @Query("""
            SELECT new com.spiramindscape.backend.resource.ResourceView(
                r.id, r.type, r.title, r.body, r.url, r.mime,
                r.name, r.role, r.email, r.phone, r.driveWebViewLink,
                r.goal.id, r.createdAt, r.updatedAt)
            FROM Resource r
            WHERE r.goal.id = :goalId
            ORDER BY r.createdAt ASC
            """)
    List<ResourceView> findViewsByGoalId(@Param("goalId") Long goalId);

    /**
     * The same projection batched across goals, backing the {@code Goal.resources} batch mapping.
     * This is the hot path: it runs on every full goals fetch from every device.
     */
    @Query("""
            SELECT new com.spiramindscape.backend.resource.ResourceView(
                r.id, r.type, r.title, r.body, r.url, r.mime,
                r.name, r.role, r.email, r.phone, r.driveWebViewLink,
                r.goal.id, r.createdAt, r.updatedAt)
            FROM Resource r
            WHERE r.goal.id IN :goalIds
            ORDER BY r.goal.id ASC, r.createdAt ASC
            """)
    List<ResourceView> findViewsByGoalIdIn(@Param("goalIds") List<Long> goalIds);
}
