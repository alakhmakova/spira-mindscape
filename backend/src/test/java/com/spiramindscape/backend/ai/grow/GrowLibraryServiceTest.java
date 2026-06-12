package com.spiramindscape.backend.ai.grow;

import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.goal.Goal;
import com.spiramindscape.backend.goal.GoalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GrowLibraryService}. The hard requirement is that GROW
 * sessions never run without book excerpts, so the failure modes (empty
 * library, no matches) must throw rather than return something usable.
 */
@ExtendWith(MockitoExtension.class)
class GrowLibraryServiceTest {

    private static final String API_KEY = "mistral-key";
    private static final float[] VECTOR = new float[] {0.1f, 0.2f};

    @Mock private BookChunkRepository repository;
    @Mock private MistralEmbeddingClient embeddingClient;
    @Mock private GoalRepository goalRepository;
    @InjectMocks private GrowLibraryService service;

    private static ChatRequest request(Long goalId, String message, List<ChatRequest.MessageEntry> history) {
        return new ChatRequest(goalId, message, "ANTHROPIC", "grow", history, null, null);
    }

    // ── ensureEmbedded ────────────────────────────────────────────────────────

    @Test
    @DisplayName("ensureEmbedded is a no-op when every chunk already has an embedding")
    void ensureEmbeddedNoOp() {
        when(repository.countUnembedded()).thenReturn(0L);
        service.ensureEmbedded(API_KEY, status -> {});
        verifyNoInteractions(embeddingClient);
        verify(repository, never()).saveEmbeddings(anyList(), anyList(), anyString());
    }

    @Test
    @DisplayName("ensureEmbedded embeds batch after batch until none remain, reporting progress")
    void ensureEmbeddedLoops() {
        var chunk1 = new BookChunkRepository.UnembeddedChunk(1L, "first");
        var chunk2 = new BookChunkRepository.UnembeddedChunk(2L, "second");
        when(repository.countUnembedded()).thenReturn(2L, 1L, 0L);
        when(repository.countAll()).thenReturn(2L);
        when(repository.findUnembedded(anyInt()))
                .thenReturn(List.of(chunk1), List.of(chunk2), List.of());
        when(embeddingClient.embed(anyList(), eq(API_KEY)))
                .thenReturn(List.of(VECTOR));

        List<String> statuses = new ArrayList<>();
        service.ensureEmbedded(API_KEY, statuses::add);

        verify(repository).saveEmbeddings(eq(List.of(1L)), anyList(), eq(MistralEmbeddingClient.MODEL));
        verify(repository).saveEmbeddings(eq(List.of(2L)), anyList(), eq(MistralEmbeddingClient.MODEL));
        assertThat(statuses).hasSize(2);
        assertThat(statuses.get(0)).contains("1/2");
        assertThat(statuses.get(1)).contains("2/2");
    }

    @Test
    @DisplayName("an embedding failure propagates — the chat layer must refuse the session")
    void ensureEmbeddedPropagatesFailure() {
        when(repository.countUnembedded()).thenReturn(5L);
        when(repository.countAll()).thenReturn(5L);
        when(repository.findUnembedded(anyInt()))
                .thenReturn(List.of(new BookChunkRepository.UnembeddedChunk(1L, "text")));
        when(embeddingClient.embed(anyList(), eq(API_KEY)))
                .thenThrow(new RuntimeException("Mistral embeddings failed: HTTP 401"));

        assertThatThrownBy(() -> service.ensureEmbedded(API_KEY, status -> {}))
                .hasMessageContaining("401");
        verify(repository, never()).saveEmbeddings(anyList(), anyList(), anyString());
    }

    // ── retrieveExcerpts ──────────────────────────────────────────────────────

    @Test
    @DisplayName("an empty library refuses instead of returning a usable prompt block")
    void retrieveThrowsOnEmptyLibrary() {
        when(repository.countAll()).thenReturn(0L);
        assertThatThrownBy(() -> service.retrieveExcerpts("query", API_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
        verifyNoInteractions(embeddingClient);
    }

    @Test
    @DisplayName("zero search hits refuse instead of returning a usable prompt block")
    void retrieveThrowsOnNoHits() {
        when(repository.countAll()).thenReturn(10L);
        when(embeddingClient.embed(anyList(), eq(API_KEY))).thenReturn(List.of(VECTOR));
        when(repository.search(any(), anyInt())).thenReturn(List.of());
        assertThatThrownBy(() -> service.retrieveExcerpts("query", API_KEY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("hits are formatted as a COACHING LIBRARY block naming the source books")
    void retrieveFormatsExcerpts() {
        when(repository.countAll()).thenReturn(10L);
        when(embeddingClient.embed(anyList(), eq(API_KEY))).thenReturn(List.of(VECTOR));
        when(repository.search(any(), anyInt())).thenReturn(List.of(
                new BookChunkRepository.FoundChunk("Coaching for Performance", 4, "Ask, don't tell."),
                new BookChunkRepository.FoundChunk("Coach the Person", 9, "Reflect their words.")));

        String block = service.retrieveExcerpts("how to ask questions", API_KEY);

        assertThat(block).startsWith("COACHING LIBRARY");
        assertThat(block).contains("Coaching for Performance").contains("Ask, don't tell.");
        assertThat(block).contains("Coach the Person").contains("Reflect their words.");
    }

    @Test
    @DisplayName("adjacent chunks of the same book merge into one passage")
    void mergesAdjacentChunks() {
        var merged = GrowLibraryService.mergeAdjacent(List.of(
                new BookChunkRepository.FoundChunk("Book A", 5, "part one"),
                new BookChunkRepository.FoundChunk("Book A", 6, "part two"),
                new BookChunkRepository.FoundChunk("Book B", 6, "other book")));
        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).content()).isEqualTo("part one\n\npart two");
        assertThat(merged.get(1).book()).isEqualTo("Book B");
    }

    // ── buildQuery ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the session's opening message is enriched with the goal's title and description")
    void buildQueryEnrichesOpening() {
        Goal goal = new Goal();
        goal.setTitle("Find a new job");
        goal.setDescription("<p>Move into a senior QA role.</p>");
        when(goalRepository.findById(7L)).thenReturn(Optional.of(goal));

        String query = service.buildQuery(request(7L, "Let's start.", List.of()));

        assertThat(query).contains("Find a new job")
                .contains("Move into a senior QA role.")
                .contains("Let's start.")
                .doesNotContain("<p>");
    }

    @Test
    @DisplayName("later turns search by the user's message alone")
    void buildQueryUsesMessageMidSession() {
        String query = service.buildQuery(request(7L, "I feel stuck about the interview",
                List.of(new ChatRequest.MessageEntry("user", "earlier"))));
        assertThat(query).isEqualTo("I feel stuck about the interview");
        verifyNoInteractions(goalRepository);
    }

    @Test
    @DisplayName("without a goal the opening message is used as-is")
    void buildQueryNoGoal() {
        String query = service.buildQuery(request(null, "Let's start.", List.of()));
        assertThat(query).isEqualTo("Let's start.");
        verifyNoInteractions(goalRepository);
    }
}
