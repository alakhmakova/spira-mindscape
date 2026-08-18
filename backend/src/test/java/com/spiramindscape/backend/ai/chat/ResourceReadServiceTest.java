package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.provider.LlmImage;
import com.spiramindscape.backend.auth.AppUser;
import com.spiramindscape.backend.auth.CurrentUserProvider;
import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.resource.Resource;
import com.spiramindscape.backend.resource.ResourceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The image-reading path for the AI's {@code read_resource} tool: a viewable
 * image is returned as an {@link LlmImage}, unsupported image subtypes fall back
 * to text, and — the security boundary — a resource on a different goal is never
 * returned. Plus {@code resolveOwnedAttachment} (BUG-030), whose boundary is the
 * requesting **user**, not just the goal.
 */
class ResourceReadServiceTest {

    private static final long CURRENT_USER = 7L;

    private final ResourceRepository repo = mock(ResourceRepository.class);
    private final CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    private final ResourceReadService service = new ResourceReadService(repo, currentUser);

    ResourceReadServiceTest() {
        AppUser me = new AppUser();
        me.setId(CURRENT_USER);
        when(currentUser.getCurrentUser()).thenReturn(me);
    }

    private Resource fileResource(long goalId, String mime, String dataUrl) {
        Goal goal = new Goal();
        goal.setId(goalId);
        Resource r = new Resource();
        r.setType("file");
        r.setMime(mime);
        r.setDataUrl(dataUrl);
        r.setGoal(goal);
        return r;
    }

    /** A resource owned by the given user (via its goal's user). */
    private Resource ownedResource(long ownerId, String type) {
        AppUser owner = new AppUser();
        owner.setId(ownerId);
        Goal goal = new Goal();
        goal.setId(5L);
        goal.setUser(owner);
        Resource r = new Resource();
        r.setType(type);
        r.setGoal(goal);
        r.setName("My file");
        return r;
    }

    @Test
    void pngIsReturnedAsAViewableImage() {
        when(repo.findById(10L)).thenReturn(
                Optional.of(fileResource(5L, "image/png", "data:image/png;base64,AAAABBBB")));

        Optional<LlmImage> img = service.readImage(5L, 10L);

        assertThat(img).isPresent();
        assertThat(img.get().mediaType()).isEqualTo("image/png");
        assertThat(img.get().base64Data()).isEqualTo("AAAABBBB");
        // The text path notes the image is delivered separately.
        assertThat(service.read(5L, 10L)).contains("attached below");
    }

    @Test
    void svgIsNotViewable_fallsBackToText() {
        when(repo.findById(10L)).thenReturn(
                Optional.of(fileResource(5L, "image/svg+xml", "data:image/svg+xml;base64,AAAA")));

        assertThat(service.readImage(5L, 10L)).isEmpty();
        assertThat(service.read(5L, 10L)).contains("can't be viewed");
    }

    @Test
    void imageFromAnotherGoalIsNeverReturned() {
        // resource belongs to goal 5, but the loop is scoped to goal 999
        when(repo.findById(10L)).thenReturn(
                Optional.of(fileResource(5L, "image/png", "data:image/png;base64,AAAA")));

        assertThat(service.readImage(999L, 10L)).isEmpty();
        assertThat(service.read(999L, 10L)).isEqualTo("Resource not found.");
    }

    @Test
    void missingResourceReturnsEmpty() {
        when(repo.findById(10L)).thenReturn(Optional.empty());
        assertThat(service.readImage(5L, 10L)).isEmpty();
    }

    // ── resolveOwnedAttachment (BUG-030): the boundary is the requesting user ──────────

    @Test
    void ownedFileResolvesToItsDataUrl() {
        Resource r = ownedResource(CURRENT_USER, "file");
        r.setMime("application/pdf");
        r.setDataUrl("data:application/pdf;base64,AAAA");
        when(repo.findById(10L)).thenReturn(Optional.of(r));

        Optional<ResourceReadService.AttachmentContent> c = service.resolveOwnedAttachment(10L);

        assertThat(c).isPresent();
        assertThat(c.get().isFile()).isTrue();
        assertThat(c.get().dataUrl()).isEqualTo("data:application/pdf;base64,AAAA");
        assertThat(c.get().mime()).isEqualTo("application/pdf");
    }

    @Test
    void ownedNoteResolvesToText() {
        Resource r = ownedResource(CURRENT_USER, "note");
        r.setBody("<p>Remember the milk</p>");
        when(repo.findById(11L)).thenReturn(Optional.of(r));

        Optional<ResourceReadService.AttachmentContent> c = service.resolveOwnedAttachment(11L);

        assertThat(c).isPresent();
        assertThat(c.get().isFile()).isFalse();
        assertThat(c.get().text()).contains("Remember the milk");
    }

    @Test
    void anotherUsersResourceIsNeverResolved() {
        // The resource is real and readable, but it belongs to user 999 — not the caller (user 7).
        Resource r = ownedResource(999L, "file");
        r.setMime("application/pdf");
        r.setDataUrl("data:application/pdf;base64,SECRET");
        when(repo.findById(10L)).thenReturn(Optional.of(r));

        assertThat(service.resolveOwnedAttachment(10L)).isEmpty();
    }

    @Test
    void resolveMissingResourceIsEmpty() {
        when(repo.findById(10L)).thenReturn(Optional.empty());
        assertThat(service.resolveOwnedAttachment(10L)).isEmpty();
        assertThat(service.resolveOwnedAttachment(null)).isEmpty();
    }
}
