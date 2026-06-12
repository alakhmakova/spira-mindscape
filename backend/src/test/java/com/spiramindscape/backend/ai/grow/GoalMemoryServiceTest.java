package com.spiramindscape.backend.ai.grow;

import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GoalMemoryService}: session memory must be scoped to
 * the goal's owner, dated, and bounded — old sessions fall off, never new ones.
 */
@ExtendWith(MockitoExtension.class)
class GoalMemoryServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long GOAL_ID = 7L;

    @Mock private GoalRepository goalRepository;
    @Mock private CurrentUserProvider currentUserProvider;
    @InjectMocks private GoalMemoryService service;

    private Goal goal;

    @BeforeEach
    void stubCurrentUser() {
        AppUser user = new AppUser();
        user.setId(USER_ID);
        lenient().when(currentUserProvider.getCurrentUser()).thenReturn(user);
        goal = new Goal();
        goal.setTitle("Find a job");
    }

    @Test
    @DisplayName("the first saved session becomes a dated memory entry")
    void appendsFirstEntry() {
        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));

        service.append(GOAL_ID, "User wants a senior QA role; fear of imperfect results blocks starting.");

        assertThat(goal.getAiMemory())
                .startsWith("[" + LocalDate.now() + "]")
                .contains("senior QA role");
        verify(goalRepository).save(goal);
    }

    @Test
    @DisplayName("later sessions append after the earlier ones")
    void appendsAfterExisting() {
        goal.setAiMemory("[2026-06-11]\nFirst session: clarified the role.");
        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));

        service.append(GOAL_ID, "Second session: committed to 15-minute steps.");

        assertThat(goal.getAiMemory())
                .contains("First session: clarified the role.")
                .contains("Second session: committed to 15-minute steps.");
        assertThat(goal.getAiMemory().indexOf("First session"))
                .isLessThan(goal.getAiMemory().indexOf("Second session"));
    }

    @Test
    @DisplayName("memory is trimmed from the front — the newest sessions survive the cap")
    void capsMemoryKeepingNewest() {
        goal.setAiMemory("x".repeat(GoalMemoryService.MAX_MEMORY_CHARS));
        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));

        service.append(GOAL_ID, "The newest insight.");

        assertThat(goal.getAiMemory().length())
                .isLessThanOrEqualTo(GoalMemoryService.MAX_MEMORY_CHARS + 1); // leading ellipsis
        assertThat(goal.getAiMemory()).endsWith("The newest insight.");
    }

    @Test
    @DisplayName("a single oversized entry is truncated, not stored whole")
    void capsSingleEntry() {
        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));

        service.append(GOAL_ID, "y".repeat(GoalMemoryService.MAX_ENTRY_CHARS * 3));

        assertThat(goal.getAiMemory().length())
                .isLessThanOrEqualTo(GoalMemoryService.MAX_ENTRY_CHARS + 20); // date header + ellipsis
    }

    @Test
    @DisplayName("blank summaries are rejected")
    void rejectsBlankSummary() {
        assertThatThrownBy(() -> service.append(GOAL_ID, "   "))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("saving to someone else's goal is a 404, not a write")
    void rejectsForeignGoal() {
        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.append(GOAL_ID, "text"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("memoryBlock wraps stored memory in a continuation instruction")
    void memoryBlockFormats() {
        goal.setAiMemory("[2026-06-11]\nClarified: senior QA role.");
        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));

        String block = service.memoryBlock(GOAL_ID);

        assertThat(block).startsWith("PREVIOUS GROW SESSIONS")
                .contains("Clarified: senior QA role.");
    }

    @Test
    @DisplayName("memoryBlock is empty for no goal, no memory, or a foreign goal")
    void memoryBlockEmptyCases() {
        assertThat(service.memoryBlock(null)).isEmpty();

        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.of(goal));
        assertThat(service.memoryBlock(GOAL_ID)).isEmpty(); // goal has no memory yet

        when(goalRepository.findByIdAndUserId(GOAL_ID, USER_ID)).thenReturn(Optional.empty());
        assertThat(service.memoryBlock(GOAL_ID)).isEmpty(); // not the owner's goal
    }
}
