package com.spiramindscape.backend.ai.grow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads the coaching books into {@code book_chunk} at startup.
 *
 * <p>Reads every {@code classpath:books/*.txt}, chunks it, and inserts the
 * chunks with NULL embeddings (embeddings need a user's API key — BYOK — so
 * they are computed lazily on the first GROW session instead). Idempotent per
 * book: a book whose chunks already exist is skipped, so restarts are free.
 *
 * <p>Gated by {@code spira.books.enabled} (default on; off in tests, where the
 * H2 schema has no {@code book_chunk} table).
 */
@Component
@ConditionalOnProperty(name = "spira.books.enabled", havingValue = "true", matchIfMissing = true)
public class BookIngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BookIngestionRunner.class);

    static final int TARGET_CHARS = 1500;
    static final int OVERLAP_CHARS = 200;

    private final BookChunkRepository repository;

    public BookIngestionRunner(BookChunkRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        Resource[] files;
        try {
            files = new PathMatchingResourcePatternResolver().getResources("classpath:books/*.txt");
        } catch (Exception e) {
            log.warn("GROW library: failed to scan classpath:books/ — {}", e.getMessage());
            return;
        }
        if (files.length == 0) {
            log.warn("GROW library: no books found under classpath:books/ — "
                    + "GROW sessions will refuse to start until book .txt files are added.");
            return;
        }
        for (Resource file : files) {
            try {
                ingest(file);
            } catch (Exception e) {
                log.error("GROW library: failed to ingest {} — {}", file.getFilename(), e.getMessage());
            }
        }
        log.info("GROW library ready: {} chunks total, {} awaiting embeddings.",
                repository.countAll(), repository.countUnembedded());
    }

    private void ingest(Resource file) throws Exception {
        String book = bookTitle(file.getFilename());
        if (repository.countForBook(book) > 0) {
            log.info("GROW library: \"{}\" already ingested, skipping.", book);
            return;
        }
        String text;
        try (var in = file.getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> chunks = BookChunker.chunk(text, TARGET_CHARS, OVERLAP_CHARS);
        if (chunks.isEmpty()) {
            log.warn("GROW library: \"{}\" produced no chunks (file empty or all-noise).", book);
            return;
        }
        repository.insertChunks(book, chunks);
        log.info("GROW library: ingested \"{}\" — {} chunks.", book, chunks.size());
    }

    /** {@code coaching-for-performance.txt} → {@code Coaching for Performance}. */
    static String bookTitle(String filename) {
        if (filename == null) return "Unknown";
        String base = filename.replaceFirst("\\.txt$", "");
        String[] words = base.split("[-_\\s]+");
        List<String> small = List.of("a", "an", "and", "for", "of", "or", "the", "to");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i].toLowerCase();
            if (w.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            if (i > 0 && small.contains(w)) {
                sb.append(w);
            } else {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return sb.length() == 0 ? "Unknown" : sb.toString();
    }
}
