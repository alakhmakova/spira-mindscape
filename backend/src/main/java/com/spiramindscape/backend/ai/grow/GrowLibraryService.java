package com.spiramindscape.backend.ai.grow;

import com.spiramindscape.backend.ai.chat.dto.ChatRequest;
import com.spiramindscape.backend.goal.GoalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The GROW coaching library: book chunks in pgvector, searched per turn so the
 * coach is grounded in the source books rather than a generic prompt.
 *
 * <p>HARD GUARANTEE: every public method here either returns usable excerpts
 * or throws. There is no degraded result — the chat layer turns the exception
 * into an SSE error and the session refuses to continue promptless.
 */
@Service
public class GrowLibraryService {

    private static final Logger log = LoggerFactory.getLogger(GrowLibraryService.class);

    /** Top-k chunks injected per turn (~2.2k tokens at 1500 chars each). */
    static final int TOP_K = 6;

    private final BookChunkRepository repository;
    private final MistralEmbeddingClient embeddingClient;
    private final GoalRepository goalRepository;

    public GrowLibraryService(
            BookChunkRepository repository,
            MistralEmbeddingClient embeddingClient,
            GoalRepository goalRepository) {
        this.repository = repository;
        this.embeddingClient = embeddingClient;
        this.goalRepository = goalRepository;
    }

    /**
     * Embeds any chunks that don't have embeddings yet (one-time after
     * ingestion, ~1k chunks in batches). Synchronized: two concurrent GROW
     * requests must not embed the same chunks twice — the loser of the race
     * re-checks the count inside the lock and finds nothing to do.
     *
     * @param statusSink receives short progress lines ("Preparing the coaching
     *                   library… 240/1100") for SSE status events
     */
    public synchronized void ensureEmbedded(String apiKey, Consumer<String> statusSink) {
        long remaining = repository.countUnembedded();
        if (remaining == 0) return;
        long total = repository.countAll();
        log.info("GROW library: embedding {} of {} chunks", remaining, total);
        while (true) {
            List<BookChunkRepository.UnembeddedChunk> batch =
                    repository.findUnembedded(MistralEmbeddingClient.BATCH_SIZE);
            if (batch.isEmpty()) break;
            List<String> contents = batch.stream()
                    .map(BookChunkRepository.UnembeddedChunk::content).toList();
            List<float[]> vectors = embeddingClient.embed(contents, apiKey);
            List<Long> ids = batch.stream()
                    .map(BookChunkRepository.UnembeddedChunk::id).toList();
            repository.saveEmbeddings(ids, vectors, MistralEmbeddingClient.MODEL);
            long done = total - repository.countUnembedded();
            statusSink.accept("Preparing the coaching library… " + done + "/" + total);
        }
        log.info("GROW library: all {} chunks embedded", total);
    }

    /**
     * What to search the library for. The opening message of a session is
     * "Let's start." (or a short focus line) with empty history — useless as a
     * search query on its own, so it is enriched with the goal's title and
     * description. Later turns search by the user's actual message.
     */
    public String buildQuery(ChatRequest request) {
        boolean opening = request.history() == null || request.history().isEmpty();
        if (!opening || request.goalId() == null) return request.message();
        return goalRepository.findById(request.goalId())
                .map(goal -> {
                    String description = stripHtml(goal.getDescription());
                    return (goal.getTitle() + ". " + description + " " + request.message()).strip();
                })
                .orElse(request.message());
    }

    /**
     * Retrieves the top passages for the query, formatted as a prompt block.
     *
     * @throws IllegalStateException if the library is empty or nothing matches
     *                               — the session must refuse, never coach promptless
     */
    public String retrieveExcerpts(String query, String apiKey) {
        if (repository.countAll() == 0) {
            throw new IllegalStateException(
                    "The coaching library is empty — no book texts have been ingested. "
                    + "Add the book .txt files to the backend and restart it.");
        }
        float[] queryVector = embeddingClient.embed(List.of(query), apiKey).get(0);
        List<BookChunkRepository.FoundChunk> found = repository.search(queryVector, TOP_K);
        if (found.isEmpty()) {
            throw new IllegalStateException(
                    "The coaching library returned no passages — its embeddings may "
                    + "still be missing. Try again in a moment.");
        }
        return format(mergeAdjacent(found));
    }

    /**
     * Consecutive chunks of the same book overlap by design; when both land in
     * the top-k, merging them reads as one passage instead of two near-copies.
     */
    static List<BookChunkRepository.FoundChunk> mergeAdjacent(
            List<BookChunkRepository.FoundChunk> found) {
        List<BookChunkRepository.FoundChunk> sorted = new ArrayList<>(found);
        sorted.sort((a, b) -> a.book().equals(b.book())
                ? Integer.compare(a.ord(), b.ord())
                : a.book().compareTo(b.book()));
        List<BookChunkRepository.FoundChunk> merged = new ArrayList<>();
        for (BookChunkRepository.FoundChunk chunk : sorted) {
            BookChunkRepository.FoundChunk last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && last.book().equals(chunk.book()) && chunk.ord() == last.ord() + 1) {
                merged.set(merged.size() - 1, new BookChunkRepository.FoundChunk(
                        last.book(), chunk.ord(), last.content() + "\n\n" + chunk.content()));
            } else {
                merged.add(chunk);
            }
        }
        return merged;
    }

    private static String format(List<BookChunkRepository.FoundChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("COACHING LIBRARY — the ONLY source for your coaching method. ")
          .append("These excerpts were retrieved from the source books for this turn; ")
          .append("ground every question, framing, and method you use in them:\n");
        int i = 1;
        for (BookChunkRepository.FoundChunk chunk : chunks) {
            sb.append("\n[Excerpt ").append(i++)
              .append(" — \"").append(chunk.book()).append("\"]\n")
              .append(chunk.content()).append('\n');
        }
        return sb.toString();
    }

    private static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&amp;", "&")
                   .replaceAll("\\s+", " ")
                   .strip();
    }
}
