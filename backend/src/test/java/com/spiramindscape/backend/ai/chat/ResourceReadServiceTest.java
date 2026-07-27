package com.spiramindscape.backend.ai.chat;

import com.spiramindscape.backend.ai.provider.LlmImage;
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
 * returned.
 */
class ResourceReadServiceTest {

    private final ResourceRepository repo = mock(ResourceRepository.class);
    private final ResourceReadService service = new ResourceReadService(repo);

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
}
