package com.spiramindscape.backend.resource;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Edits a note's HTML body without throwing away what the user wrote.
 *
 * <p><b>Why this exists.</b> An AI {@code edit_note} used to be a whole-body replacement: the
 * model sent back "the note", from its own memory of it, and whatever the user had typed into
 * the note since was gone the moment the card was approved. Found live on 2026-09-15 — the
 * CV writer proposed five profile rewrites in one session, each dropping the owner's own
 * corrections. So the default is now to ADD, and rewriting is a mode that has to be asked for
 * and is checked against the version the model actually read (see
 * {@link ResourceService#editNote}).
 *
 * <p>Pure and framework-free: blocks are the note's top-level nodes, a section is a heading
 * plus every block up to the next heading of the same or a higher level.
 */
public final class NoteEdit {

    /** Most entries per side of a {@link Diff}, and most characters per entry. */
    static final int DIFF_MAX_ENTRIES = 40;
    static final int DIFF_MAX_CHARS = 300;

    private NoteEdit() {
    }

    public enum Mode {
        /** Add the content at the end of the note. Never removes anything. */
        APPEND,
        /** Add the content at the end of one section, creating the section when missing. */
        APPEND_TO_SECTION,
        /** Content carries its own headings; each part is added to the section of that name. */
        MERGE_SECTIONS,
        /** Replace the body of one section. Rewrites existing text. */
        REPLACE_SECTION,
        /** Replace the whole note. Rewrites existing text. */
        REPLACE_ALL;

        /** Parses the wire form ({@code append_to_section}); blank means {@link #APPEND}. */
        public static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) return APPEND;
            try {
                return valueOf(raw.trim().replace('-', '_').toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown note edit mode: " + raw);
            }
        }

        /** True for the modes that can remove text already in the note. */
        public boolean rewritesExisting() {
            return this == REPLACE_SECTION || this == REPLACE_ALL;
        }

        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What an edit adds and removes, as plain text per block. */
    public record Diff(List<String> added, List<String> removed) {
        public boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty();
        }
    }

    /** One top-level node: its serialised HTML, its normalised text and its heading level (0 = not a heading). */
    private record Block(String html, String text, int level, String headingText) {
    }

    /**
     * Applies an edit and returns the new body.
     *
     * <p>Append modes skip blocks (and list items) whose text is already in the place they
     * would be added to, so a model that sends the whole note back with one new line adds that
     * one line rather than a second copy of the note.
     */
    public static String apply(String currentHtml, Mode mode, String section, String contentHtml) {
        String current = currentHtml == null ? "" : currentHtml;
        String content = contentHtml == null ? "" : contentHtml;
        boolean hasSection = section != null && !section.isBlank();
        return switch (mode) {
            case REPLACE_ALL -> content;
            case APPEND -> appendAtEnd(current, content);
            case APPEND_TO_SECTION -> hasSection
                    ? intoSection(current, section, content, false)
                    : appendAtEnd(current, content);
            case REPLACE_SECTION -> hasSection
                    ? intoSection(current, section, content, true)
                    : content;
            case MERGE_SECTIONS -> mergeSections(current, content);
        };
    }

    /** The note's headings, in order, as written. */
    public static List<String> sections(String html) {
        List<String> out = new ArrayList<>();
        for (Block b : blocks(html)) {
            if (b.level() > 0 && !b.headingText().isBlank()) out.add(b.headingText());
        }
        return out;
    }

    /** Block-level difference between two bodies (longest common subsequence over block text). */
    public static Diff diff(String beforeHtml, String afterHtml) {
        List<String> before = texts(blocks(beforeHtml));
        List<String> after = texts(blocks(afterHtml));
        int n = before.size();
        int m = after.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = before.get(i).equals(after.get(j))
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (before.get(i).equals(after.get(j))) {
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                addClipped(removed, before.get(i++));
            } else {
                addClipped(added, after.get(j++));
            }
        }
        while (i < n) addClipped(removed, before.get(i++));
        while (j < m) addClipped(added, after.get(j++));
        return new Diff(added, removed);
    }

    // ── modes ──────────────────────────────────────────────────────────────

    private static String appendAtEnd(String current, String content) {
        List<Block> existing = blocks(current);
        List<Block> fresh = freshBlocks(content, existing, null);
        if (fresh.isEmpty()) return current;
        return current.stripTrailing() + join(fresh);
    }

    private static String intoSection(String current, String section, String content, boolean replace) {
        List<Block> note = blocks(current);
        int start = findHeading(note, section);
        if (start < 0) {
            // A section that is not there yet is created at the end, named as asked.
            List<Block> fresh = freshBlocks(content, List.of(), section);
            if (fresh.isEmpty()) return current;
            return current.stripTrailing() + "<h2>" + escape(section.trim()) + "</h2>" + join(fresh);
        }
        int end = sectionEnd(note, start);
        List<Block> body = note.subList(start + 1, end);
        List<Block> fresh = freshBlocks(content, replace ? List.of() : body, section);
        if (fresh.isEmpty() && !replace) return current;

        List<Block> out = new ArrayList<>(note.subList(0, start + 1));
        if (!replace) out.addAll(body);
        out.addAll(fresh);
        out.addAll(note.subList(end, note.size()));
        return join(out);
    }

    private static String mergeSections(String current, String content) {
        String result = current;
        List<Block> incoming = blocks(content);
        StringBuilder preamble = new StringBuilder();
        int i = 0;
        while (i < incoming.size() && incoming.get(i).level() == 0) {
            preamble.append(incoming.get(i++).html());
        }
        if (!preamble.isEmpty()) result = appendAtEnd(result, preamble.toString());
        while (i < incoming.size()) {
            Block heading = incoming.get(i);
            int end = sectionEnd(incoming, i);
            StringBuilder part = new StringBuilder();
            for (int k = i + 1; k < end; k++) part.append(incoming.get(k).html());
            result = intoSection(result, heading.headingText(), part.toString(), false);
            i = end;
        }
        return result;
    }

    // ── parsing ────────────────────────────────────────────────────────────

    private static Document parse(String html) {
        Document doc = Jsoup.parseBodyFragment(html == null ? "" : html);
        doc.outputSettings().prettyPrint(false);
        return doc;
    }

    private static List<Block> blocks(String html) {
        Document doc = parse(html);
        List<Block> out = new ArrayList<>();
        for (Node n : doc.body().childNodes()) {
            if (n instanceof TextNode t && t.isBlank()) continue;
            out.add(toBlock(n));
        }
        return out;
    }

    private static Block toBlock(Node n) {
        int level = 0;
        String raw = "";
        if (n instanceof Element e) {
            raw = e.text();
            String tag = e.normalName();
            if (tag.length() == 2 && tag.charAt(0) == 'h' && tag.charAt(1) >= '1' && tag.charAt(1) <= '6') {
                level = tag.charAt(1) - '0';
            }
        } else if (n instanceof TextNode t) {
            raw = t.text();
        }
        return new Block(n.outerHtml(), norm(raw), level, level > 0 ? raw.trim() : "");
    }

    /**
     * The content's blocks that are not already in {@code existing}. List items are compared
     * one by one, so a list carrying one new item adds a list of just that item. A leading
     * heading repeating the target section's name is dropped — the section already has it.
     */
    private static List<Block> freshBlocks(String content, List<Block> existing, String section) {
        Set<String> known = new HashSet<>(texts(existing));
        Set<String> knownItems = new HashSet<>();
        for (Block b : existing) {
            Document d = parse(b.html());
            for (Element li : d.select("li")) knownItems.add(norm(li.text()));
        }

        Document doc = parse(content);
        List<Block> out = new ArrayList<>();
        boolean first = true;
        for (Node n : new ArrayList<>(doc.body().childNodes())) {
            if (n instanceof TextNode t && t.isBlank()) continue;
            Block b = toBlock(n);
            if (first && section != null && b.level() > 0 && headingMatches(b.headingText(), section)) {
                first = false;
                continue;
            }
            first = false;
            if (n instanceof Element e && (e.normalName().equals("ul") || e.normalName().equals("ol"))) {
                for (Element li : e.select("> li")) {
                    if (knownItems.contains(norm(li.text()))) li.remove();
                }
                if (e.select("> li").isEmpty()) continue;
                b = toBlock(e);
            }
            if (b.text().isEmpty() && !(n instanceof Element el && el.normalName().equals("hr"))) continue;
            if (known.contains(b.text())) continue;
            known.add(b.text());
            out.add(b);
        }
        return out;
    }

    private static int findHeading(List<Block> note, String section) {
        String wanted = norm(stripColon(section));
        for (int i = 0; i < note.size(); i++) {
            Block b = note.get(i);
            if (b.level() > 0 && norm(stripColon(b.headingText())).equals(wanted)) return i;
        }
        for (int i = 0; i < note.size(); i++) {
            Block b = note.get(i);
            if (b.level() > 0 && !wanted.isEmpty() && norm(b.headingText()).contains(wanted)) return i;
        }
        return -1;
    }

    private static boolean headingMatches(String heading, String section) {
        return norm(stripColon(heading)).equals(norm(stripColon(section)));
    }

    private static int sectionEnd(List<Block> blocks, int headingIndex) {
        int level = blocks.get(headingIndex).level();
        for (int i = headingIndex + 1; i < blocks.size(); i++) {
            int l = blocks.get(i).level();
            if (l > 0 && l <= level) return i;
        }
        return blocks.size();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    static String norm(String s) {
        if (s == null) return "";
        return s.replace(' ', ' ').replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String stripColon(String s) {
        String t = s == null ? "" : s.trim();
        return t.endsWith(":") ? t.substring(0, t.length() - 1) : t;
    }

    private static List<String> texts(List<Block> blocks) {
        List<String> out = new ArrayList<>(blocks.size());
        for (Block b : blocks) out.add(b.text());
        return out;
    }

    private static String join(List<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) sb.append(b.html());
        return sb.toString();
    }

    private static void addClipped(List<String> into, String text) {
        if (text.isEmpty() || into.size() >= DIFF_MAX_ENTRIES) return;
        into.add(text.length() > DIFF_MAX_CHARS ? text.substring(0, DIFF_MAX_CHARS - 1) + "…" : text);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
