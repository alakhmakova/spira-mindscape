package com.spiramindscape.backend.ai.chat;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

/**
 * Extracts plain text from uploaded file resources so the AI can read them
 * (e.g. a CV PDF) without any provider-specific document/vision support.
 *
 * <p>Only text-based PDFs are supported — scanned/image-only PDFs and images
 * have no text layer and yield an empty result. Output is bounded so a large
 * document cannot blow up the chat context.
 */
final class ResourceTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(ResourceTextExtractor.class);

    /** Cap pages parsed (a CV is 1–3 pages; this guards against huge PDFs). */
    private static final int MAX_PAGES = 15;

    private ResourceTextExtractor() {}

    /**
     * Extracts text from a {@code data:application/pdf;base64,...} URL.
     *
     * @param dataUrl  the resource's base64 data URL
     * @param maxChars truncation cap for the returned text
     * @return extracted text (truncated), or empty string if nothing could be read
     */
    static String extractPdfText(String dataUrl, int maxChars) {
        if (dataUrl == null || dataUrl.isBlank()) return "";
        try {
            int comma = dataUrl.indexOf(',');
            String base64 = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
            byte[] bytes = Base64.getDecoder().decode(base64);

            try (PDDocument doc = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(1);
                stripper.setEndPage(Math.min(MAX_PAGES, Math.max(1, doc.getNumberOfPages())));
                String text = stripper.getText(doc);
                if (text == null) return "";
                text = text.strip();
                return text.length() > maxChars ? text.substring(0, maxChars) + "…[truncated]" : text;
            }
        } catch (Exception e) {
            log.debug("PDF text extraction failed: {}", e.getMessage());
            return "";
        }
    }
}
