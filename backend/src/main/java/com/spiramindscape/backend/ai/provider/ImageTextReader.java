package com.spiramindscape.backend.ai.provider;

import java.util.Optional;

/**
 * Something that can read the text out of a picture, so a chat model that cannot see is still
 * given the words rather than silence (BUG-027).
 *
 * <p>There are two implementations and the difference between them is <b>whose key pays</b>:
 *
 * <ul>
 *   <li>{@code MistralOcrService} — Mistral's purpose-built document-OCR model. Better at
 *       handwriting and scanned pages, and the only option for a provider that cannot read
 *       images at all. Needs a Mistral key.</li>
 *   <li>{@code CohereVisionReader} — Cohere's own vision model, on the Cohere key the user
 *       already has. Exists so that picking Cohere does not oblige the user to go and get a
 *       second API key from a second company just to attach a photo.</li>
 * </ul>
 *
 * <p>Which one a turn uses is decided in {@code AiChatService.visionContextFor}, and the rule is
 * <b>the user's own provider first</b> — the same rule that already let a Mistral user's OCR run
 * on their Mistral key rather than asking for another one.
 */
public interface ImageTextReader {

    /**
     * Reads the text out of {@code dataUrl}.
     *
     * <p>Never throws: on any failure it returns empty, and the caller tells the model plainly
     * that the image could not be read — which is the whole point, because the alternative is a
     * model inventing what it could not see.
     *
     * @param apiKey   the user's key for whichever provider this reader belongs to
     * @param dataUrl  a {@code data:<mime>;base64,…} URL
     * @param maxChars hard cap on the returned text, so the chat context stays bounded
     */
    Optional<String> extractText(String apiKey, String dataUrl, int maxChars);

    /**
     * How the reading is described to the model, so it can pass that on to the user honestly.
     * Machine transcription is not sight and must never be reported as if it were.
     */
    String describeReading();
}
