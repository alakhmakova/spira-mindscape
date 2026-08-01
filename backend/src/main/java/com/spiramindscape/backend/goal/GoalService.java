package com.spiramindscape.backend.goal;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.graphql.input.CreateGoalInput;
import com.spiramindscape.backend.graphql.input.UpdateGoalInput;
import com.spiramindscape.backend.graphql.input.UpdateOptionInput;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoalService {

    public static final int MAX_GOAL_TITLE_LENGTH = 200;
    public static final int MAX_GOAL_DESCRIPTION_LENGTH = 5000;
    public static final int MAX_OPTION_TEXT_LENGTH = 500;

    private final GoalRepository goalRepository;
    private final OptionRepository optionRepository;
    private final ConfidenceHistoryRepository confidenceHistoryRepository;
    private final CurrentUserProvider currentUserProvider;
    private final EntityManager entityManager;

    /**
     * One aggregate per table in the goal graph: the latest {@code updatedAt} and the row count,
     * owner-scoped. Combined they form {@link #currentRevision()} — {@code max} catches edits,
     * {@code count} catches inserts/deletes. Confidence history is intentionally omitted: a
     * confidence change also updates the parent goal, so {@code goal.updatedAt} already covers it.
     *
     * <p>Kept as six separate JPQL aggregates rather than one native {@code UNION}/multi-subselect
     * query on purpose: JPQL has no UNION, and hand-written SQL here would have to juggle the
     * reserved {@code option} table name plus H2-vs-Postgres differences in {@code GREATEST}/NULL
     * and epoch handling. These are tiny indexed {@code count}/{@code max} scans, so the extra
     * round-trips are cheap, and portability + null-safety are worth more than saving them. Revisit
     * only if this shows up as real DB load.
     */
    private static final List<String> REVISION_AGGREGATES = List.of(
            "SELECT max(g.updatedAt), count(g) FROM Goal g WHERE g.user.id = :userId",
            "SELECT max(o.updatedAt), count(o) FROM Option o WHERE o.goal.user.id = :userId",
            "SELECT max(t.updatedAt), count(t) FROM Target t WHERE t.goal.user.id = :userId",
            "SELECT max(c.updatedAt), count(c) FROM ChecklistItem c WHERE c.target.goal.user.id = :userId",
            "SELECT max(r.updatedAt), count(r) FROM RealityItem r WHERE r.goal.user.id = :userId",
            "SELECT max(res.updatedAt), count(res) FROM Resource res WHERE res.goal.user.id = :userId");

    @Transactional(readOnly = true)
    public List<Goal> findAll() {
        Long userId = currentUserProvider.getCurrentUser().getId();
        return goalRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    /**
     * A cheap change-signature for the current user's entire goal graph, backing the
     * {@code goalsRevision} query. Lets background sync poll a few scalars instead of re-fetching
     * the full {@code goals} payload every time — a large cut in outbound data transfer. Format:
     * {@code "<maxUpdatedAtMicros>:<totalRowCount>"}; {@code "0:0"} when the user has no goals.
     */
    @Transactional(readOnly = true)
    public String currentRevision() {
        Long userId = currentUserProvider.getCurrentUser().getId();
        Instant maxUpdated = null;
        long totalCount = 0;
        for (String jpql : REVISION_AGGREGATES) {
            Object[] row = (Object[]) entityManager.createQuery(jpql)
                    .setParameter("userId", userId)
                    .getSingleResult();
            Instant updated = (Instant) row[0];
            if (updated != null && (maxUpdated == null || updated.isAfter(maxUpdated))) {
                maxUpdated = updated;
            }
            totalCount += ((Number) row[1]).longValue();
        }
        // Microsecond resolution (not millis): shrinks the window in which a same-instant edit on
        // another device could share the last-seen max and be skipped by the client's
        // change-check. Postgres timestamptz is microsecond-precise.
        long micros = maxUpdated == null
                ? 0L
                : maxUpdated.getEpochSecond() * 1_000_000L + maxUpdated.getNano() / 1_000;
        return micros + ":" + totalCount;
    }

    /**
     * Finds a goal by id scoped to the current user.
     * Returns a NOT_FOUND error if the goal does not exist OR belongs to another user —
     * the caller cannot distinguish the two cases (defense in depth).
     */
    @Transactional(readOnly = true)
    public Goal findById(Long id) {
        Long userId = currentUserProvider.getCurrentUser().getId();
        return goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found: " + id));
    }

    @Transactional
    public Goal create(CreateGoalInput input) {
        AppUser currentUser = currentUserProvider.getCurrentUser();
        Goal goal = new Goal();
        goal.setUser(currentUser);
        goal.setTitle(normalizeRequiredText(input.title(), "Goal title is required"));
        goal.setDescription(normalizeOptionalText(input.description()));
        goal.setConfidence(input.confidence());
        goal.setDeadline(input.deadline());
        validateGoal(goal);
        Goal saved = goalRepository.save(goal);
        saveConfidenceHistory(saved);
        return saved;
    }

    @Transactional
    public Goal update(Long id, UpdateGoalInput input) {
        return update(id, input, Map.of());
    }

    @Transactional
    public Goal update(Long id, UpdateGoalInput input, Map<String, Object> rawInput) {
        Goal goal = findById(id);
        Integer oldConfidence = goal.getConfidence();

        if (input.title() != null)       goal.setTitle(normalizeRequiredText(input.title(), "Goal title is required"));
        if (input.description() != null) goal.setDescription(normalizeOptionalText(input.description()));
        if (input.confidence() != null)  goal.setConfidence(input.confidence());
        if (input.deadline() != null)    goal.setDeadline(input.deadline());
        if (input.achievedAt() != null)  goal.setAchievedAt(input.achievedAt());
        if (rawInput != null && rawInput.containsKey("achievedAt") && input.achievedAt() == null) {
            goal.setAchievedAt(null);
        }
        if (rawInput != null && rawInput.containsKey("description") && input.description() == null) {
            goal.setDescription("");
        }
        if (rawInput != null && rawInput.containsKey("deadline") && input.deadline() == null) {
            goal.setDeadline(null);
        }
        if (rawInput != null && rawInput.containsKey("confidence") && input.confidence() == null) {
            goal.setConfidence(null);
        }
        validateGoal(goal);
        Goal saved = goalRepository.save(goal);

        if (input.confidence() != null && !input.confidence().equals(oldConfidence)) {
            saveConfidenceHistory(saved);
        }

        return saved;
    }

    private void saveConfidenceHistory(Goal goal) {
        ConfidenceHistory history = new ConfidenceHistory();
        history.setGoal(goal);
        history.setConfidence(goal.getConfidence());
        history.setAt(java.time.Instant.now());
        confidenceHistoryRepository.save(history);
    }

    @Transactional
    public void delete(Long id) {
        goalRepository.delete(findById(id));
    }

    @Transactional(readOnly = true)
    public List<Option> findOptions(Long goalId) {
        findById(goalId);
        return optionRepository.findByGoalIdOrderByPositionAscCreatedAtAsc(goalId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Option>> findOptionsByGoalIds(List<Long> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        return optionRepository.findByGoalIdInOrderByGoalIdAscPositionAscCreatedAtAsc(goalIds)
                .stream()
                .collect(Collectors.groupingBy(o -> o.getGoal().getId()));
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ConfidenceHistory>> findConfidenceHistoryByGoalIds(List<Long> goalIds) {
        if (goalIds.isEmpty()) {
            return Map.of();
        }
        return confidenceHistoryRepository.findByGoalIdInOrderByAtDesc(goalIds)
                .stream()
                .collect(Collectors.groupingBy(h -> h.getGoal().getId()));
    }

    @Transactional
    public Option addOption(Long goalId, String text) {
        Goal goal = findById(goalId);
        int nextPosition = optionRepository.findMaxPositionByGoalId(goalId) + 1;
        Option option = new Option();
        option.setGoal(goal);
        String normalized = normalizeRequiredText(text, "Option text is required");
        validateOptionText(normalized);
        option.setText(normalized);
        option.setSelected(false);
        option.setPosition(nextPosition);
        return optionRepository.save(option);
    }

    @Transactional
    public Option updateOption(Long goalId, Long optionId, UpdateOptionInput input) {
        findById(goalId);
        Option option = getOption(goalId, optionId);
        if (input.text() != null) {
            String normalized = normalizeRequiredText(input.text(), "Option text is required");
            validateOptionText(normalized);
            option.setText(normalized);
        }
        // `selected` (the "active" radio) and `status` (the good_idea/didnt_work/none thumb
        // lean) are INDEPENDENT — an option can be both active and thumbed. Apply each input
        // separately; neither derives the other.
        boolean becameActive = false;
        if (input.selected() != null) {
            option.setSelected(input.selected());
            becameActive = input.selected();
        }
        if (input.status() != null) {
            option.setStatus(normalizeOptionStatus(input.status()));
        }
        Option saved = optionRepository.save(option);
        if (becameActive) {
            // "active" is single-select across the goal (radio behaviour) — clear the others.
            deselectOtherActiveOptions(goalId, optionId);
        }
        return saved;
    }

    @Transactional
    public Option selectOption(Long goalId, Long optionId) {
        findById(goalId);
        Option selected = getOption(goalId, optionId);
        List<Option> all = optionRepository.findByGoalIdOrderByPositionAscCreatedAtAsc(goalId);
        // Only toggle the "active" radio (`selected`). The thumb lean (`status`) is independent
        // and must survive selection.
        all.forEach(o -> o.setSelected(o.getId().equals(optionId)));
        optionRepository.saveAll(all);
        return selected;
    }

    /** Clear any OTHER option that is still active, so only one option is active per goal. */
    private void deselectOtherActiveOptions(Long goalId, Long keepOptionId) {
        for (Option o : optionRepository.findByGoalIdOrderByPositionAscCreatedAtAsc(goalId)) {
            if (!o.getId().equals(keepOptionId) && Boolean.TRUE.equals(o.getSelected())) {
                o.setSelected(false);
                optionRepository.save(o);
            }
        }
    }

    @Transactional
    public List<Option> reorderOptions(Long goalId, List<Long> optionIds) {
        findById(goalId);
        List<Option> all = optionRepository.findByGoalIdOrderByPositionAscCreatedAtAsc(goalId);

        if (optionIds.size() != all.size()) {
            throw new IllegalArgumentException(
                    "Option ids list must contain all options for this goal. Expected " +
                    all.size() + ", got " + optionIds.size());
        }

        Map<Long, Option> byId = all.stream()
                .collect(Collectors.toMap(Option::getId, o -> o));

        for (int i = 0; i < optionIds.size(); i++) {
            Long id = optionIds.get(i);
            Option option = byId.get(id);
            if (option == null) {
                throw new IllegalArgumentException(
                        "Option not found or does not belong to goal: " + id);
            }
            option.setPosition(i);
        }

        optionRepository.saveAll(all);
        return optionRepository.findByGoalIdOrderByPositionAscCreatedAtAsc(goalId);
    }

    @Transactional
    public void removeOption(Long goalId, Long optionId) {
        findById(goalId);
        optionRepository.delete(getOption(goalId, optionId));
    }

    private Option getOption(Long goalId, Long optionId) {
        Option option = optionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found: " + optionId));
        if (!option.getGoal().getId().equals(goalId)) {
            throw new IllegalArgumentException("Option does not belong to goal");
        }
        return option;
    }

    private void validateOptionText(String text) {
        if (text.length() > MAX_OPTION_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Option text must be " + MAX_OPTION_TEXT_LENGTH + " characters or fewer");
        }
    }

    // The thumb lean is independent of the "active" radio (`selected`), so "active" is NOT a
    // status value — it lives in `selected`.
    private static final java.util.Set<String> OPTION_STATUSES =
            java.util.Set.of("none", "good_idea", "didnt_work");

    private String normalizeOptionStatus(String status) {
        String normalized = status == null ? "none" : status.trim();
        if (!OPTION_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Invalid option status: " + status);
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        return value == null ? "" : value.trim();
    }

    private void validateGoal(Goal goal) {
        if (goal.getTitle().length() > MAX_GOAL_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "Goal title must be " + MAX_GOAL_TITLE_LENGTH + " characters or fewer");
        }
        if (goal.getDescription() != null && goal.getDescription().length() > MAX_GOAL_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Goal description must be " + MAX_GOAL_DESCRIPTION_LENGTH + " characters or fewer");
        }
        if (goal.getConfidence() == null) {
            throw new IllegalArgumentException("Confidence rating is required");
        }
        if (goal.getConfidence() < 1 || goal.getConfidence() > 10) {
            throw new IllegalArgumentException("Confidence rating must be between 1 and 10");
        }
    }
}
