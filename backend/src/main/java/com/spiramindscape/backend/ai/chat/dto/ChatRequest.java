package com.spiramindscape.backend.ai.chat.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatRequest(

        /** Goal ID to scope the conversation. Null means global (all-goals) context. */
        Long goalId,

        /**
         * The user's text. May be **blank when the message carries attachments** — sending a photo
         * or a resource on its own, with no typed question, is allowed (the server supplies a
         * default prompt). It is only required when there is nothing else to send; see
         * {@link #isNotEmpty()}. A blank-message-only request used to 400 with "must not be blank",
         * which is exactly what an attachment-only send from the phone hit (BUG-030 follow-up).
         */
        @Size(max = MAX_MESSAGE_CHARS, message = "That message is too long — "
                + "keep it under 50000 characters, or attach it as a file instead.")
        String message,

        /**
         * Which provider to use. Defaults to {@code ANTHROPIC} if omitted.
         * Must match a key that the user has previously saved.
         */
        @Pattern(regexp = "ANTHROPIC|OPENAI|MISTRAL|GEMINI|COHERE"
                + "|anthropic|openai|mistral|gemini|cohere")
        String provider,

        /**
         * Session type — controls which system prompt is used.
         * {@code "chat"} (default): regular assistant mode.
         * {@code "grow"}: GROW coaching session mode.
         */
        String sessionType,

        /**
         * Optional conversation history to maintain context across messages.
         * Each entry has role ("user"|"assistant") and content.
         *
         * <p>The caps here are an abuse stop, not the real limit: what a turn actually replays
         * to the model is decided by {@code ChatHistory.trim}, which bounds the characters as
         * well as the count and keeps the newest turns (BUG-056). These bound what is
         * <b>deserialized</b>, which trimming cannot — by the time {@code trim} runs, the whole
         * body is already objects on the heap.
         *
         * <p><b>Both the count and each entry are capped</b>, and {@code @Valid} is what makes
         * the second one happen: without it the per-entry {@code @Size} on
         * {@link MessageEntry#content()} is never evaluated, and 200 entries of unbounded
         * length is not a bound at all. Two hundred merged turns, each up to 100k characters,
         * is orders of magnitude beyond any real conversation and still finite.
         */
        @Valid
        @Size(max = 200, message = "Too many history entries")
        java.util.List<MessageEntry> history,

        /**
         * GROW only: the session length the user chose, in minutes. Lets the
         * coach pace the conversation instead of being cut off by the UI timer.
         */
        Integer sessionTotalMinutes,

        /**
         * GROW only: seconds left on the session timer when this message was
         * sent. {@code <= 0} means time is up — the coach must close the
         * session in this reply.
         */
        Integer sessionRemainingSeconds,

        /**
         * Files attached directly to THIS message (images, PDFs, DOCX) — a
         * lightweight alternative to saving a Resource. Ephemeral: they inform
         * only this turn and are never persisted. Capped in count so a request
         * can't smuggle in a huge payload.
         */
        @Valid
        @Size(max = 6, message = "At most 6 files can be attached to a message")
        java.util.List<Attachment> attachments
) {
    /**
     * How much text one message may carry.
     *
     * <p>Raised from 10 000 on 2026-09-08. Ten thousand characters is less than a pasted job
     * advert plus a CV, which is an ordinary thing to bring to a goal about finding work — and
     * the request died in validation before any provider saw it, so the panel reported it as
     * "no provider works even with keys" (owner, same day). Fifty thousand matches the longest
     * text the app already accepts anywhere, a note's body
     * ({@code ResourceService.MAX_NOTE_BODY_LENGTH}), and mirrors {@code FIELD_LIMITS.chatMessage}
     * on the web.
     *
     * <p>It stays bounded, and comfortably: 50 000 characters is roughly 12 500 tokens, a
     * fraction of the smallest context window any supported provider offers (128 000), with the
     * system prompt and replayed history still to fit beside it. It is also below the per-entry
     * history cap ({@link MessageEntry#content()}), so a message that was accepted can still be
     * replayed as history next turn rather than becoming a request the server would refuse.
     */
    public static final int MAX_MESSAGE_CHARS = 50_000;

    /**
     * A request must carry **something** — text, or at least one attachment. This replaces the old
     * {@code @NotBlank} on {@code message}, which rejected an attachment-only send (a photo with no
     * typed question) as a 400.
     */
    @AssertTrue(message = "A message needs text or at least one attachment")
    public boolean isNotEmpty() {
        boolean hasText = message != null && !message.isBlank();
        boolean hasAttachments = attachments != null && !attachments.isEmpty();
        return hasText || hasAttachments;
    }

    /** Backwards-compatible constructor for callers/tests that predate attachments. */
    public ChatRequest(
            Long goalId,
            String message,
            String provider,
            String sessionType,
            java.util.List<MessageEntry> history,
            Integer sessionTotalMinutes,
            Integer sessionRemainingSeconds) {
        this(goalId, message, provider, sessionType, history,
                sessionTotalMinutes, sessionRemainingSeconds, null);
    }

    /**
     * One replayed turn. {@code content} is capped far above anything a model produces or a
     * person types — a reply runs to a few thousand characters and the current message is
     * capped at 10,000 — but the cap has to exist, because this is the one field of the
     * request whose size nothing else bounds. See the note on {@link #history()}.
     */
    public record MessageEntry(
            String role,
            @Size(max = 100_000, message = "A history entry is too long") String content) {}

    /**
     * One file attached to this message, from one of two sources (BUG-030):
     *
     * <ul>
     *   <li><b>A device file</b> — {@code dataUrl} carries the bytes as a
     *       {@code data:<mime>;base64,<payload>} URL, and {@code mime} decides how it is used
     *       (image → vision, PDF/DOCX → extracted text). Bounded to keep a request sane
     *       (~5 MB of file ≈ 6.8 MB base64; the cap leaves headroom).</li>
     *   <li><b>A saved resource</b> — {@code resourceId} names one of the goal's existing
     *       resources and {@code dataUrl} is omitted. The server inlines its bytes/text itself,
     *       so the client never re-uploads what the backend already holds. The id is
     *       user-supplied and untrusted: the server re-checks the resource belongs to the
     *       requesting user before reading it (see {@code ResourceReadService}).</li>
     * </ul>
     *
     * Either {@code dataUrl} or {@code resourceId} must be present. Both are ephemeral — an
     * attachment informs only this turn and is never persisted.
     */
    public record Attachment(
            @Size(max = 300) String name,
            @Size(max = 200) String mime,
            @Size(max = 7_500_000, message = "Attached file is too large") String dataUrl,
            Long resourceId
    ) {
        /** Legacy shape (device file only), kept so existing callers/tests compile unchanged. */
        public Attachment(String name, String mime, String dataUrl) {
            this(name, mime, dataUrl, null);
        }

        /** A resource attachment carries an id and no inline bytes. */
        public boolean isResource() {
            return resourceId != null;
        }

        /**
         * Exactly one source must be given. Validated so a request can neither smuggle both
         * (ambiguous) nor attach nothing (a blank chip).
         */
        @jakarta.validation.constraints.AssertTrue(
                message = "An attachment needs either a file or a resource id, not both or neither")
        public boolean isExactlyOneSource() {
            boolean hasData = dataUrl != null && !dataUrl.isBlank();
            return hasData ^ (resourceId != null);
        }
    }
}
