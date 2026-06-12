package com.spiramindscape.backend.ai.grow;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a book's plain text into retrieval chunks for the GROW coaching
 * library. Pure logic, no Spring dependencies.
 *
 * <p>Chunks are built from whole paragraphs (blank-line separated), packed up
 * to a target size with a small overlap carried into the next chunk so an idea
 * cut at a boundary is still findable. Very short paragraphs (page numbers,
 * TOC lines, headings noise from the docx export) are dropped.
 */
public final class BookChunker {

    /** Paragraphs shorter than this are export noise (page numbers, TOC). */
    private static final int MIN_PARAGRAPH_CHARS = 40;

    private BookChunker() {}

    /**
     * @param text         the whole book as plain text, paragraphs separated by blank lines
     * @param targetChars  soft maximum chunk size (a chunk closes once it exceeds this)
     * @param overlapChars approximate tail of a chunk repeated at the start of the next
     * @return ordered chunk contents, never null
     */
    public static List<String> chunk(String text, int targetChars, int overlapChars) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;

        List<String> paragraphs = new ArrayList<>();
        for (String p : text.split("\\R{2,}")) {
            String trimmed = p.strip();
            if (trimmed.length() < MIN_PARAGRAPH_CHARS) continue;
            if (trimmed.length() > targetChars) {
                paragraphs.addAll(splitLongParagraph(trimmed, targetChars));
            } else {
                paragraphs.add(trimmed);
            }
        }

        StringBuilder current = new StringBuilder();
        List<String> currentParagraphs = new ArrayList<>();
        for (String p : paragraphs) {
            if (current.length() > 0 && current.length() + p.length() > targetChars) {
                chunks.add(current.toString());
                // Carry trailing paragraphs (up to ~overlapChars) into the next chunk.
                List<String> overlap = tailWithin(currentParagraphs, overlapChars);
                current.setLength(0);
                currentParagraphs.clear();
                for (String o : overlap) {
                    appendParagraph(current, o);
                    currentParagraphs.add(o);
                }
            }
            appendParagraph(current, p);
            currentParagraphs.add(p);
        }
        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }

    /** Splits an over-long paragraph on sentence ends, hard-cutting as a last resort. */
    private static List<String> splitLongParagraph(String paragraph, int targetChars) {
        List<String> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        for (String sentence : paragraph.split("(?<=[.!?])\\s+")) {
            if (part.length() > 0 && part.length() + sentence.length() + 1 > targetChars) {
                parts.add(part.toString());
                part.setLength(0);
            }
            while (sentence.length() > targetChars) {
                parts.add(sentence.substring(0, targetChars));
                sentence = sentence.substring(targetChars);
            }
            if (part.length() > 0) part.append(' ');
            part.append(sentence);
        }
        if (part.length() > 0) parts.add(part.toString());
        return parts;
    }

    /** Last paragraphs of the list whose combined length stays within {@code budget}. */
    private static List<String> tailWithin(List<String> paragraphs, int budget) {
        List<String> tail = new ArrayList<>();
        int used = 0;
        for (int i = paragraphs.size() - 1; i >= 0; i--) {
            String p = paragraphs.get(i);
            if (used + p.length() > budget) break;
            tail.add(0, p);
            used += p.length();
        }
        return tail;
    }

    private static void appendParagraph(StringBuilder sb, String paragraph) {
        if (sb.length() > 0) sb.append("\n\n");
        sb.append(paragraph);
    }
}
