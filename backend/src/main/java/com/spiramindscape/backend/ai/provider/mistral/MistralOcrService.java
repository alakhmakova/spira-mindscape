package com.spiramindscape.backend.ai.provider.mistral;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads text out of an image or a scanned PDF with Mistral's document-OCR model
 * ({@code mistral-ocr-latest}, {@code POST /v1/ocr}) — a different product from the chat
 * models, purpose-built for documents and handwriting, and unrelated to whether the selected
 * chat model has vision.
 *
 * <p>Why it exists (BUG-027): most Mistral chat models are text-only, so an attached photo was
 * silently dropped and the model then invented its contents. OCR gives us the real text to hand
 * the model, whatever chat model the user picked — and it also rescues scanned, text-less PDFs.
 *
 * <p>Uses the user's own Mistral key (BYOK), passed in per call. Never throws: on any failure
 * it returns {@link Optional#empty()} and the caller falls back to telling the user plainly
 * that the image could not be read.
 */
@Service
public class MistralOcrService {

    private static final Logger log = LoggerFactory.getLogger(MistralOcrService.class);
    private static final String ENDPOINT = "https://api.mistral.ai/v1/ocr";
    private static final String MODEL = "mistral-ocr-latest";
    /** OCR of a photo takes a few seconds; a scanned multi-page PDF can take longer. */
    private static final Duration TIMEOUT = Duration.ofSeconds(90);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MistralOcrService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Extracts the document text as Markdown.
     *
     * @param apiKey   the user's Mistral key
     * @param dataUrl  a {@code data:<mime>;base64,…} URL of an image or a PDF
     * @param maxChars hard cap on the returned text (bounds the chat context)
     * @return the text, or empty when OCR failed or the document held no text
     */
    public Optional<String> extractText(String apiKey, String dataUrl, int maxChars) {
        if (apiKey == null || apiKey.isBlank() || dataUrl == null || !dataUrl.startsWith("data:")) {
            return Optional.empty();
        }
        try {
            // A PDF is a "document_url" chunk, an image an "image_url" one; both accept the
            // base64 data URL directly, so nothing has to be uploaded first.
            boolean pdf = dataUrl.regionMatches(true, 0, "data:application/pdf", 0, 20);
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("type", pdf ? "document_url" : "image_url");
            document.put(pdf ? "document_url" : "image_url", dataUrl);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", MODEL);
            body.put("document", document);
            body.put("include_image_base64", false);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .header("authorization", "Bearer " + apiKey)
                    .header("content-type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Mistral OCR failed: HTTP {} — {}", response.statusCode(),
                        abbreviate(response.body()));
                return Optional.empty();
            }
            return parseMarkdown(objectMapper.readTree(response.body()), maxChars);
        } catch (Exception e) {
            log.warn("Mistral OCR failed: {}", e.toString());
            return Optional.empty();
        }
    }

    /**
     * Joins the {@code pages[].markdown} of an OCR response, capped at {@code maxChars}.
     * Package-private so the parsing is unit-tested without an HTTP call.
     */
    static Optional<String> parseMarkdown(JsonNode root, int maxChars) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode page : root.path("pages")) {
            String md = page.path("markdown").asText("").trim();
            if (md.isEmpty()) continue;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(md);
            if (sb.length() >= maxChars) break;
        }
        String text = sb.toString().trim();
        if (text.isEmpty()) return Optional.empty();
        return Optional.of(text.length() > maxChars ? text.substring(0, maxChars) + "…" : text);
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
