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
 *
 * <p>{@code read} and {@code readImage} answer to the same **user** boundary since
 * BUG-054. Matching the resource against the tool call's {@code goalId} was never a
 * boundary on its own, because that id is itself part of the request: naming another
 * person's goal and one of its resources used to hand back their note body or PDF text.
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

    /** A file on {@code goalId}, owned by {@link #CURRENT_USER} unless stated otherwise. */
    private Resource fileResource(long goalId, String mime, String dataUrl) {
        return fileResource(goalId, CURRENT_USER, mime, dataUrl);
    }

    private Resource fileResource(long goalId, long ownerId, String mime, String dataUrl) {
        AppUser owner = new AppUser();
        owner.setId(ownerId);
        Goal goal = new Goal();
        goal.setId(goalId);
        goal.setUser(owner);
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
    void aResourceOnAnotherUsersGoalIsNeverRead() {
        // The tool call names goal 5 and resource 10, and the resource really is on goal 5 —
        // but goal 5 belongs to user 999. Before BUG-054 the goal match alone let this through,
        // so a guessed pair of ids read a stranger's file straight into the conversation.
        when(repo.findById(10L)).thenReturn(
                Optional.of(fileResource(5L, 999L, "image/png", "data:image/png;base64,SECRET")));

        assertThat(service.readImage(5L, 10L)).isEmpty();
        assertThat(service.read(5L, 10L)).isEqualTo("Resource not found.");
    }

    @Test
    void aNoteOnAnotherUsersGoalIsNeverRead() {
        AppUser stranger = new AppUser();
        stranger.setId(999L);
        Goal theirGoal = new Goal();
        theirGoal.setId(5L);
        theirGoal.setUser(stranger);
        Resource note = new Resource();
        note.setType("note");
        note.setBody("<p>my salary is</p>");
        note.setGoal(theirGoal);
        when(repo.findById(11L)).thenReturn(Optional.of(note));

        assertThat(service.read(5L, 11L)).isEqualTo("Resource not found.");
    }

    @Test
    void anOrphanedResourceIsNeverRead() {
        // A goal with no owner cannot pass the check either — belongsTo… must not read
        // null as "anyone's".
        Goal ownerless = new Goal();
        ownerless.setId(5L);
        Resource r = new Resource();
        r.setType("note");
        r.setBody("<p>text</p>");
        r.setGoal(ownerless);
        when(repo.findById(12L)).thenReturn(Optional.of(r));

        assertThat(service.read(5L, 12L)).isEqualTo("Resource not found.");
        assertThat(service.readImage(5L, 12L)).isEmpty();
    }

    @Test
    void aForeignResourceReadsExactlyLikeAMissingOne() {
        when(repo.findById(10L)).thenReturn(
                Optional.of(fileResource(5L, 999L, "application/pdf", "data:application/pdf;base64,X")));
        when(repo.findById(20L)).thenReturn(Optional.empty());

        assertThat(service.read(5L, 10L)).isEqualTo(service.read(5L, 20L));
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
    void ownedLinkResolvesToItsUrl() {
        Resource r = ownedResource(CURRENT_USER, "link");
        r.setUrl("https://example.com/jobs/1");
        when(repo.findById(12L)).thenReturn(Optional.of(r));

        Optional<ResourceReadService.AttachmentContent> c = service.resolveOwnedAttachment(12L);

        assertThat(c).isPresent();
        assertThat(c.get().isFile()).isFalse();
        assertThat(c.get().text()).contains("https://example.com/jobs/1");
    }

    @Test
    void aLinkWithNoUrlSaysSoRatherThanResolvingToNothing() {
        // An empty string here would reach the model as a blank attachment it would then
        // answer about. The words are what stop it inventing a page.
        Resource r = ownedResource(CURRENT_USER, "link");
        r.setUrl(null);
        when(repo.findById(12L)).thenReturn(Optional.of(r));

        assertThat(service.resolveOwnedAttachment(12L))
                .get().extracting(ResourceReadService.AttachmentContent::text)
                .isEqualTo("(no URL)");
    }

    @Test
    void ownedContactResolvesToItsDetails() {
        Resource r = ownedResource(CURRENT_USER, "email");
        r.setName("Ann Lee");
        r.setEmail("ann@example.com");
        when(repo.findById(13L)).thenReturn(Optional.of(r));

        Optional<ResourceReadService.AttachmentContent> c = service.resolveOwnedAttachment(13L);

        assertThat(c).isPresent();
        assertThat(c.get().isFile()).isFalse();
        assertThat(c.get().text()).contains("ann@example.com");
    }

    @Test
    void anUnknownResourceTypeResolvesToNothing() {
        // Better an explicit "not available" note to the model than a half-read resource.
        Resource r = ownedResource(CURRENT_USER, "sculpture");
        when(repo.findById(14L)).thenReturn(Optional.of(r));

        assertThat(service.resolveOwnedAttachment(14L)).isEmpty();
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
