package com.spiramindscape.backend.ai.chat;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the dependency-free DOCX extractor recovers running text from a real
 * (minimal) .docx zip — text runs, tabs, and paragraph breaks — and degrades
 * gracefully when there is nothing to read.
 */
class DocxTextExtractorTest {

    /** Wraps document.xml bytes into a minimal .docx zip and returns its data URL. */
    private static String docxDataUrl(String documentXml) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(documentXml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        String base64 = Base64.getEncoder().encodeToString(out.toByteArray());
        return "data:application/vnd.openxmlformats-officedocument.wordprocessingml.document;base64,"
                + base64;
    }

    private static final String BODY = """
            <w:document><w:body>
              <w:p><w:r><w:t>Hello</w:t></w:r><w:r><w:tab/></w:r><w:r><w:t>world</w:t></w:r></w:p>
              <w:p><w:r><w:t>Second &amp; last line</w:t></w:r></w:p>
            </w:body></w:document>
            """;

    @Test
    void extractsRunsTabsAndParagraphBreaks() throws Exception {
        String text = DocxTextExtractor.extractDocxText(docxDataUrl(BODY), 10_000);

        assertThat(text).contains("Hello\tworld");
        assertThat(text).contains("Second & last line"); // XML entity unescaped
        // The two paragraphs are on separate lines.
        assertThat(text.indexOf("world")).isLessThan(text.indexOf("Second"));
        assertThat(text).contains("\n");
    }

    @Test
    void truncatesToTheCharacterCap() throws Exception {
        String longRun = "x".repeat(500);
        String xml = "<w:document><w:body><w:p><w:r><w:t>" + longRun
                + "</w:t></w:r></w:p></w:body></w:document>";

        String text = DocxTextExtractor.extractDocxText(docxDataUrl(xml), 100);

        assertThat(text).endsWith("…[truncated]");
        assertThat(text.length()).isLessThan(200);
    }

    @Test
    void returnsEmptyForNonDocxOrEmptyInput() {
        assertThat(DocxTextExtractor.extractDocxText("", 100)).isEmpty();
        assertThat(DocxTextExtractor.extractDocxText(null, 100)).isEmpty();
        // A base64 blob that isn't a zip at all → empty, not an exception.
        String notAZip = "data:application/octet-stream;base64,"
                + Base64.getEncoder().encodeToString("not a zip".getBytes(StandardCharsets.UTF_8));
        assertThat(DocxTextExtractor.extractDocxText(notAZip, 100)).isEmpty();
    }
}
