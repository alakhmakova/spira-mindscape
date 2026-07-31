package com.spiramindscape.backend.ai.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts plain text from a DOCX attachment so the AI can read it without any
 * office-document library. A {@code .docx} is just a ZIP whose main content is
 * {@code word/document.xml}; the visible text sits in {@code <w:t>} runs, with
 * paragraph breaks at {@code </w:p>} and tabs at {@code <w:tab/>}. Parsing those
 * three markers covers ordinary documents (a CV, a brief) without pulling in
 * Apache POI. Output is bounded so a large document can't blow up the chat
 * context.
 *
 * <p>This is deliberately minimal: it ignores tables' structure, images, and
 * styling — it recovers the running text, which is what the model needs. A file
 * that yields nothing (empty, or a non-Word ZIP) returns an empty string, and
 * the caller tells the user rather than inventing contents.
 */
final class DocxTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocxTextExtractor.class);

    /** Guards against a zip-bomb / accidental huge doc: stop reading document.xml past this. */
    private static final int MAX_XML_BYTES = 8 * 1024 * 1024;

    private static final Pattern RUN_OR_BREAK = Pattern.compile(
            "<w:t(?:\\s[^>]*)?>(.*?)</w:t>"  // a text run (<w:t> or <w:t …>, NOT <w:tab>) → group 1
                    + "|<w:tab\\b[^>]*/?>"    // a tab
                    + "|</w:p>"               // end of paragraph
                    + "|<w:br\\b[^>]*/?>",    // an explicit line break
            Pattern.DOTALL);

    private DocxTextExtractor() {}

    /**
     * Extracts text from a {@code data:...;base64,...} DOCX data URL.
     *
     * @param dataUrl  the attachment's base64 data URL
     * @param maxChars truncation cap for the returned text
     * @return extracted text (truncated), or empty string if nothing could be read
     */
    static String extractDocxText(String dataUrl, int maxChars) {
        if (dataUrl == null || dataUrl.isBlank()) return "";
        try {
            int comma = dataUrl.indexOf(',');
            String base64 = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
            byte[] bytes = Base64.getDecoder().decode(base64);

            String documentXml = readDocumentXml(bytes);
            if (documentXml == null || documentXml.isBlank()) return "";

            String text = extractFromXml(documentXml);
            text = text.strip();
            return text.length() > maxChars ? text.substring(0, maxChars) + "…[truncated]" : text;
        } catch (Exception e) {
            log.debug("DOCX text extraction failed: {}", e.getMessage());
            return "";
        }
    }

    /** Returns the raw {@code word/document.xml} entry from the DOCX zip, or null. */
    private static String readDocumentXml(byte[] zipBytes) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    int total = 0;
                    while ((n = zip.read(buf)) != -1) {
                        total += n;
                        if (total > MAX_XML_BYTES) break;
                        out.write(buf, 0, n);
                    }
                    return out.toString(java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }

    /** Turns the run/tab/paragraph markers in document.xml into running text. */
    private static String extractFromXml(String xml) {
        StringBuilder sb = new StringBuilder();
        Matcher m = RUN_OR_BREAK.matcher(xml);
        while (m.find()) {
            String run = m.group(1);
            if (run != null) {
                sb.append(unescapeXml(run));
            } else {
                String token = m.group();
                if (token.startsWith("</w:p>")) sb.append('\n');
                else sb.append(token.startsWith("<w:tab") ? '\t' : '\n');
            }
        }
        // Collapse the runs of blank lines a paragraph-per-line produces.
        return sb.toString().replaceAll("\n{3,}", "\n\n");
    }

    private static String unescapeXml(String s) {
        return s.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
