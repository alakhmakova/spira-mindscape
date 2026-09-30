package com.spiramindscape.backend.resource;

import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalService;
import com.spiramindscape.backend.graphql.input.EditNoteInput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceEditNoteTest {

    private static final Instant VERSION = Instant.parse("2026-09-15T10:02:11.123456Z");

    @Mock private ResourceRepository resourceRepository;
    @Mock private GoalService goalService;
    @InjectMocks private ResourceService resourceService;

    private Resource note(String body) {
        Goal goal = new Goal();
        goal.setId(1L);
        Resource r = new Resource();
        r.setId(12L);
        r.setType("note");
        r.setTitle("Profile");
        r.setBody(body);
        r.setGoal(goal);
        r.setUpdatedAt(VERSION);
        when(resourceRepository.findById(12L)).thenReturn(Optional.of(r));
        lenient().when(resourceRepository.save(any(Resource.class))).thenAnswer(i -> i.getArgument(0));
        return r;
    }

    @Test
    @DisplayName("an append goes onto the note as it is now, so a hand edit made after the suggestion survives")
    void appendKeepsALaterHandEdit() {
        note("<p>Written by hand after the card appeared</p>");

        Resource saved = resourceService.editNote(12L,
                new EditNoteInput("append", null, "<p>Suggested line</p>", null, "2020-01-01T00:00:00Z"));

        assertThat(saved.getBody()).isEqualTo("<p>Written by hand after the card appeared</p><p>Suggested line</p>");
    }

    @Test
    @DisplayName("a rewrite based on an older version is refused and nothing is saved")
    void staleRewriteRefused() {
        note("<p>Current</p>");

        assertThatThrownBy(() -> resourceService.editNote(12L,
                new EditNoteInput("replace_all", null, "<p>Old copy</p>", null, "2026-09-15T09:00:00Z")))
                .isInstanceOf(NoteChangedException.class);
        assertThatThrownBy(() -> resourceService.editNote(12L,
                new EditNoteInput("replace_section", "Contact", "<p>x</p>", null, null)))
                .isInstanceOf(NoteChangedException.class);
        verify(resourceRepository, never()).save(any());
    }

    @Test
    @DisplayName("a rewrite based on the current version is applied, to the millisecond")
    void currentRewriteApplied() {
        note("<h2>Contact</h2><p>Old</p>");

        Resource saved = resourceService.editNote(12L,
                new EditNoteInput("replace_section", "Contact", "<p>New</p>", "CV profile", "2026-09-15T10:02:11.123Z"));

        assertThat(saved.getBody()).isEqualTo("<h2>Contact</h2><p>New</p>");
        assertThat(saved.getTitle()).isEqualTo("CV profile");
    }

    @Test
    @DisplayName("the goal's owner is checked, and only notes can be edited")
    void ownerAndTypeChecked() {
        Resource r = note("<p>x</p>");
        resourceService.editNote(12L, new EditNoteInput(null, null, "<p>y</p>", null, null));
        verify(goalService).findById(1L);

        r.setType("link");
        assertThatThrownBy(() -> resourceService.editNote(12L, new EditNoteInput(null, null, "<p>y</p>", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
