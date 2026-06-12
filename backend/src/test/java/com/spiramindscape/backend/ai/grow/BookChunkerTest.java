package com.spiramindscape.backend.ai.grow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BookChunker}: the GROW library's quality depends on
 * chunks being complete paragraphs of a sane size, with enough overlap that an
 * idea cut at a boundary is still findable from either side.
 */
class BookChunkerTest {

    private static final int TARGET = 1500;
    private static final int OVERLAP = 200;

    /** A paragraph of exactly {@code length} characters (no blank lines inside). */
    private static String paragraph(char filler, int length) {
        return String.valueOf(filler).repeat(length);
    }

    @Test
    @DisplayName("null and blank input produce no chunks")
    void emptyInput() {
        assertThat(BookChunker.chunk(null, TARGET, OVERLAP)).isEmpty();
        assertThat(BookChunker.chunk("", TARGET, OVERLAP)).isEmpty();
        assertThat(BookChunker.chunk("   \n\n  \n\n ", TARGET, OVERLAP)).isEmpty();
    }

    @Test
    @DisplayName("short paragraphs (page numbers, TOC noise) are dropped")
    void dropsShortParagraphs() {
        String text = "12\n\nChapter 3\n\n" + paragraph('a', 100);
        List<String> chunks = BookChunker.chunk(text, TARGET, OVERLAP);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo(paragraph('a', 100));
    }

    @Test
    @DisplayName("paragraphs are packed together up to the target size")
    void packsParagraphs() {
        // Three 600-char paragraphs: the first two fit one 1500-char chunk,
        // the third starts a new one.
        String text = paragraph('a', 600) + "\n\n" + paragraph('b', 600) + "\n\n" + paragraph('c', 600);
        List<String> chunks = BookChunker.chunk(text, TARGET, OVERLAP);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).contains(paragraph('a', 600)).contains(paragraph('b', 600));
        assertThat(chunks.get(1)).contains(paragraph('c', 600));
    }

    @Test
    @DisplayName("a small trailing paragraph is repeated at the start of the next chunk (overlap)")
    void overlapsChunks() {
        String tail = paragraph('t', 150); // fits the 200-char overlap budget
        String text = paragraph('a', 700) + "\n\n" + paragraph('b', 600) + "\n\n"
                + tail + "\n\n" + paragraph('c', 800);
        List<String> chunks = BookChunker.chunk(text, TARGET, OVERLAP);
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        // The tail paragraph closes one chunk and re-opens the next.
        int closing = -1;
        for (int i = 0; i < chunks.size() - 1; i++) {
            if (chunks.get(i).endsWith(tail)) closing = i;
        }
        assertThat(closing).as("a chunk should end with the small tail paragraph").isNotNegative();
        assertThat(chunks.get(closing + 1)).startsWith(tail);
    }

    @Test
    @DisplayName("a paragraph longer than the target is split on sentence boundaries")
    void splitsLongParagraph() {
        String sentence = "This sentence is exactly long enough to count as real prose for chunking. ";
        String longParagraph = sentence.repeat(60).strip(); // ~4500 chars, no blank lines
        List<String> chunks = BookChunker.chunk(longParagraph, TARGET, OVERLAP);
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(TARGET + OVERLAP);
        }
    }

    @Test
    @DisplayName("no paragraph text is lost — every paragraph lands in some chunk")
    void preservesAllParagraphs() {
        String[] paragraphs = new String[10];
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < paragraphs.length; i++) {
            paragraphs[i] = "Paragraph number " + i + " " + paragraph((char) ('a' + i), 400);
            text.append(paragraphs[i]).append("\n\n");
        }
        List<String> chunks = BookChunker.chunk(text.toString(), TARGET, OVERLAP);
        String all = String.join("\n---\n", chunks);
        for (String p : paragraphs) {
            assertThat(all).contains(p);
        }
    }
}
