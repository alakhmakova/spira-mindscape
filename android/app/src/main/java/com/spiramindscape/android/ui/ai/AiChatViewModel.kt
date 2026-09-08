package com.spiramindscape.android.ui.ai

import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spiramindscape.android.core.SpiraLog
import com.spiramindscape.android.data.ai.AiApi
import com.spiramindscape.android.data.ai.ChatMessage
import com.spiramindscape.android.data.ai.ChatRole
import com.spiramindscape.android.data.ai.Proposal
import com.spiramindscape.android.data.ai.ProposalStatus
import com.spiramindscape.android.data.ai.buildHistory
import com.spiramindscape.android.data.ai.dedupCreates
import com.spiramindscape.android.data.ai.encodeTranscript
import com.spiramindscape.android.data.ai.mergeAttachmentBytes
import com.spiramindscape.android.data.ai.parseTranscript
import com.spiramindscape.android.data.ai.proposalContext
import com.spiramindscape.android.data.ai.proposalFromToolArgs
import com.spiramindscape.android.data.ai.randomProposalId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.io.File

/**
 * Which conversation the panel is in. Mirrors the web `Mode` state machine.
 *
 * The last three are **the coach-owned ending**, in order: `GROW_END` is the session record,
 * `GROW_REVIEW` the changes it proposes for the goal, `GROW_FAREWELL` the goodbye. Android used to
 * stop at `GROW_END` and exit, so the user got a farewell and a save-memory question the instant
 * the clock hit zero and never saw the rest (owner, 2026-08-24).
 */
enum class ChatMode {
    CHAT, GROW_START, GROW_ACTIVE, GROW_CLOSING, GROW_END, GROW_REVIEW, GROW_FAREWELL
}

/**
 * Whether a GROW session is live (its own messages are showing). `GROW_START` is the setup screen,
 * before any message exists, so it is deliberately excluded — same as the web's `inGrow`.
 */
private fun isGrowMode(mode: ChatMode): Boolean = mode != ChatMode.CHAT && mode != ChatMode.GROW_START

/**
 * The AI assistant on Android — the conversation, its transcript, the chosen provider and the
 * GROW session. The Android twin of the state half of the web `AiPanel`.
 *
 * Scope: [goalId] null is the global (all-goals) chat; a value scopes the conversation to one
 * goal. Each scope has its own transcript, synced through the server so a conversation started on
 * the phone opens on the laptop.
 */
class AiChatViewModel(
    private val goalId: String?,
    private val api: AiChat = LiveAiChat,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    /** The goal this panel is scoped to, or null for the all-goals chat. */
    val scopeGoalId: String? = goalId

    /** The persisted general chat — the only list ever written to the server transcript. */
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())

    /**
     * The live GROW session's own messages. A GROW session is deliberately **ephemeral**: its
     * conversation is kept apart from the chat and is **never persisted**, mirroring the web's
     * `gmsgs`/`msgs` split (`AiPanel.tsx` — "GROW sessions are intentionally ephemeral and not
     * persisted"). Folding it into [_chatMessages] — as this used to — dropped the whole coaching
     * session into the general chat, which is exactly what a separate session must not do.
     */
    private val _growMessages = MutableStateFlow<List<ChatMessage>>(emptyList())

    private val _mode = MutableStateFlow(ChatMode.CHAT)
    val mode: StateFlow<ChatMode> = _mode.asStateFlow()

    /**
     * The list the panel shows: the GROW session while one is live, otherwise the chat transcript.
     */
    val messages: StateFlow<List<ChatMessage>> =
        combine(_mode, _chatMessages, _growMessages) { m, chat, grow ->
            if (isGrowMode(m)) grow else chat
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The message list writes go to right now — the GROW session's, or the chat's. */
    private fun activeList(): MutableStateFlow<List<ChatMessage>> =
        if (isGrowMode(_mode.value)) _growMessages else _chatMessages

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    /** The provider the user last chose (synced across devices), e.g. "ANTHROPIC". */
    private val _provider = MutableStateFlow(DEFAULT_PROVIDER)
    val provider: StateFlow<String> = _provider.asStateFlow()

    /** The keys the user has saved, so the panel knows whether it can talk at all. */
    private val _keys = MutableStateFlow<List<AiApi.KeyInfo>>(emptyList())
    val keys: StateFlow<List<AiApi.KeyInfo>> = _keys.asStateFlow()

    /** True once the panel knows there is no usable key — the UI offers to add one. */
    private val _needsKey = MutableStateFlow(false)
    val needsKey: StateFlow<Boolean> = _needsKey.asStateFlow()

    /** GROW: the length the user picked, and what is left of it. */
    private val _sessionMinutes = MutableStateFlow(DEFAULT_SESSION_MINUTES)
    val sessionMinutes: StateFlow<Int> = _sessionMinutes.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    /**
     * The session record the coach passed to `end_session`, shown on the end card for the user to
     * read before deciding whether to keep it. Null when no session is ending.
     *
     * It used to be nothing at all: the card showed two buttons and no text, and what it saved was
     * **the coach's last chat message** — which, by then, was the goodbye. So the memory carried a
     * farewell rather than a record of the work (owner, 2026-08-24: "что именно сохранилось в
     * память?").
     */
    private val _memoryDraft = MutableStateFlow<String?>(null)
    val memoryDraft: StateFlow<String?> = _memoryDraft.asStateFlow()

    /** True while the coach is rewriting the record from the user's instruction. */
    private val _revisingMemory = MutableStateFlow(false)
    val revisingMemory: StateFlow<Boolean> = _revisingMemory.asStateFlow()

    /**
     * What the coach proposed in its ending turn, kept back until the record has been decided.
     *
     * The method's rule is that nothing is written down until the session is over; the plumbing's
     * is record first, then goal changes. Holding them here is what makes the second half true —
     * released into the transcript by [closeSession] as the review step.
     */
    private var heldProposals: List<Proposal> = emptyList()

    /** Guards, so each step of the ending happens exactly once. */
    private var ended = false
    private var wrapUpSent = false
    private var goodbyeSent = false
    private var memorySaved = false

    /**
     * Guard `closeSession`/`leaveGrow` against running twice (owner report, 2026-09-02: "I've
     * prepared this for your review." / "Session memory saved." each showed up twice in a row).
     * Neither function previously guarded itself, unlike every other one-shot step of the ending
     * ([wrapUpSent], [goodbyeSent]) — a double-tap on Save/Discard, or a recomposition re-firing
     * the same click, ran the whole body (including the transcript append) twice.
     */
    private var closeSessionCalled = false
    private var leaveGrowCalled = false

    /**
     * Seconds of overtime with no message from the user. Incremented once a second while the
     * clock is negative, reset to 0 by any real user turn (never by a wrap-up/goodbye control
     * turn) — see [startTimer]. Distinct from *total* overtime elapsed: a session the user is
     * actively continuing past its planned length must not be cut off just because the planned
     * length was exceeded a while ago.
     */
    private var overtimeInactivitySeconds = 0

    /**
     * The composer's unsent draft and its pending attachments live **here**, not in the composable.
     *
     * Taking a photo launches the camera, and a memory-hungry camera app routinely gets this
     * activity recreated while it is in front. A `remember` in the composer loses everything in that
     * moment — so a note attached before the photo simply vanished, leaving only the photo
     * (BUG-030 follow-up). The ViewModel survives that recreation, so what was half-composed
     * survives with it.
     */
    private val _composerDraft = MutableStateFlow(saved.get<String>(KEY_DRAFT).orEmpty())
    val composerDraft: StateFlow<String> = _composerDraft.asStateFlow()

    private val _composerAttachments =
        MutableStateFlow(restoreComposerChips())
    val composerAttachments: StateFlow<List<AiApi.ChatAttachment>> = _composerAttachments.asStateFlow()

    fun setComposerDraft(text: String) {
        _composerDraft.value = text
        saved[KEY_DRAFT] = text
    }

    /** Append attachments (from the file picker, the camera, or the resource sheet), capped. */
    fun addComposerAttachments(picked: List<AiApi.ChatAttachment>) {
        val next = (_composerAttachments.value + picked).takeLast(ATTACH_MAX_COUNT)
        // Anything pushed past the cap is gone from the composer, so its parked bytes are litter.
        discardParkedBytes(_composerAttachments.value - next.toSet())
        _composerAttachments.value = next
        persistComposerChips()
    }

    fun removeComposerAttachment(attachment: AiApi.ChatAttachment) {
        _composerAttachments.value = _composerAttachments.value - attachment
        discardParkedBytes(listOf(attachment))
        persistComposerChips()
    }

    private fun clearComposer() {
        discardParkedBytes(_composerAttachments.value)
        _composerDraft.value = ""
        _composerAttachments.value = emptyList()
        saved[KEY_DRAFT] = ""
        saved[KEY_COMPOSER_CHIPS] = ArrayList<String>()
    }

    /**
     * Delete the cache copies of chips that have left the composer — sent, removed, or pushed off
     * the end by the cap. Best-effort: a file missed here is collected by `pruneComposerCache`.
     */
    private fun discardParkedBytes(gone: List<AiApi.ChatAttachment>) {
        gone.forEach { chip -> chip.cachePath?.let { runCatching { File(it).delete() } } }
    }

    /**
     * Every composer chip, written through to saved state so it survives the process being killed.
     *
     * This is the other half of a fix that was left half-done. The camera's destination Uri was
     * already `rememberSaveable`, precisely because a camera app routinely gets this process
     * killed — so after a kill the photo came back and everything attached before it did not
     * (owner, 2026-08-23: "attach a resource, take a photo, the resource disappears and the photo
     * stays"). Saving the destination without saving the composer produced exactly that asymmetry.
     *
     * **What is saved is never the bytes.** Saved state travels to the system in a Bundle, and a
     * multi-megabyte data URL in one is a `TransactionTooLargeException` waiting to happen. So a
     * row is only ever a handful of characters:
     *
     * | Chip | Row | Restored from |
     * |---|---|---|
     * | a goal's saved resource | `r`&#124;id&#124;name&#124;mime | the id alone — the server already holds the file |
     * | a photo or a picked file | `f`&#124;path&#124;name&#124;mime | the cache copy `ChatAttachments.readAttachment` parked there |
     *
     * The first round of this fix saved **only** the resource chips and said so, on the grounds
     * that a photo "returns by its own road" — the redelivered camera result. That is true of the
     * photo being taken and of nothing else: a file picked from storage, or a photo taken *before*
     * the one that caused the kill, was still lost without a word (owner, 2026-08-24). Parking the
     * bytes in the app's own cache and keeping the path costs nothing and covers both.
     *
     * A row whose file has since gone — swept, or cleared with the app's cache — is dropped. That
     * is the old behaviour for that chip, and there is nothing better to do with a missing file.
     *
     * Rows are `kind|payload|name|mime` strings rather than a Parcelable list, so no new type has
     * to be Parcelable for the sake of a Bundle. `name` may contain `|`, so it is split last.
     */
    private fun persistComposerChips() {
        saved[KEY_COMPOSER_CHIPS] = ArrayList(
            _composerAttachments.value.mapNotNull { chip ->
                val (kind, payload) = when {
                    chip.resourceId != null -> CHIP_RESOURCE to chip.resourceId.toString()
                    chip.cachePath != null -> CHIP_FILE to chip.cachePath
                    // Bytes with nowhere to park them (the spill failed). Unsaveable, as before.
                    else -> return@mapNotNull null
                }
                listOf(kind, payload, chip.mime, chip.name).joinToString("|")
            },
        )
    }

    private fun restoreComposerChips(): List<AiApi.ChatAttachment> =
        saved.get<ArrayList<String>>(KEY_COMPOSER_CHIPS).orEmpty().mapNotNull { row ->
            val parts = row.split("|", limit = 4)
            if (parts.size < 4) return@mapNotNull null
            val (kind, payload, mime) = parts
            val name = parts[3]
            when (kind) {
                CHIP_RESOURCE -> payload.toLongOrNull()?.let {
                    AiApi.ChatAttachment(name = name, mime = mime, resourceId = it)
                }
                CHIP_FILE -> {
                    val bytes = runCatching { File(payload).readBytes() }.getOrNull()
                    bytes?.let {
                        AiApi.ChatAttachment(
                            name = name,
                            mime = mime,
                            dataUrl = "data:$mime;base64," + Base64.encodeToString(it, Base64.NO_WRAP),
                            cachePath = payload,
                        )
                    }
                }
                else -> null
            }
        }

    private var streamJob: Job? = null
    private var timerJob: Job? = null

    /** The server's `updatedAt` for our own last write, so polling doesn't re-adopt it. */
    private var lastSyncedAt: String? = null

    /**
     * Completes once the stored transcript is in, so a message sent the instant this screen
     * opens cannot be swallowed by the load landing after it — see [sendOnArrival].
     */
    private val initialLoad = kotlinx.coroutines.CompletableDeferred<Unit>()

    init {
        viewModelScope.launch {
            _provider.value = runCatching { api.getProvider() }.getOrNull() ?: DEFAULT_PROVIDER
            refreshKeys()
            loadTranscript()
            // A session left running — here or on another device — is picked up after the
            // transcript, so the chat is already whole underneath it.
            restoreGrowSession()
            initialLoad.complete(Unit)
        }
    }

    /**
     * Sends a message the moment this chat is usable — the All-Goals assistant's handoff, which
     * arrives with the navigation rather than from the composer (see `AiHandoff`).
     *
     * <p>It waits, because [loadTranscript] REPLACES the message list with the server's copy:
     * a send that lands first would be wiped a moment later by the load, and the user would
     * watch their own question disappear. The web has the same wait, spelt as an 80 ms timer;
     * this one waits for the actual event.
     */
    fun sendOnArrival(text: String) {
        viewModelScope.launch {
            initialLoad.await()
            send(text)
        }
    }

    // ── Keys and provider ───────────────────────────────────────────────────

    fun refreshKeys() {
        viewModelScope.launch {
            // A failure here looks identical to "no keys saved", so the user is told to
            // add a key they already have.
            val saved = runCatching { api.listKeys() }
                .onFailure { SpiraLog.w(TAG, "ai_keys_load_failed", it) }
                .getOrDefault(emptyList())
            _keys.value = saved
            _needsKey.value = saved.none { it.provider.equalsIgnoreCase(_provider.value) }
        }
    }

    fun chooseProvider(provider: String) {
        _provider.value = provider
        _needsKey.value = _keys.value.none { it.provider.equalsIgnoreCase(provider) }
        // The picker updates either way, so a failure means the choice silently reverts
        // on the next launch.
        viewModelScope.launch {
            runCatching { api.saveProvider(provider) }
                .onFailure { SpiraLog.w(TAG, "ai_provider_save_failed", it) }
        }
    }

    /** Save a key, then adopt its provider — the user just told us what they want to use. */
    fun saveKey(provider: String, apiKey: String, model: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = runCatching { api.saveKey(provider, apiKey, model) }.exceptionOrNull()
            if (error == null) {
                chooseProvider(provider)
                refreshKeys()
            }
            onResult(error?.message)
        }
    }

    fun loadModels(provider: String, onResult: (List<String>, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { api.listProviderModels(provider) }
                .onSuccess { onResult(it, null) }
                .onFailure { onResult(emptyList(), it.message) }
        }
    }

    fun chooseModel(provider: String, model: String) {
        viewModelScope.launch {
            runCatching { api.updateKeyModel(provider, model) }
                .onFailure { SpiraLog.w(TAG, "ai_model_save_failed", it) }
            refreshKeys()
        }
    }

    // ── Transcript ──────────────────────────────────────────────────────────

    private suspend fun loadTranscript() {
        // Indistinguishable from "no history yet" — the chat just opens empty.
        val stored = runCatching { api.getTranscript(goalId) }
            .onFailure { SpiraLog.w(TAG, "ai_transcript_load_failed goalId=$goalId", it) }
            .getOrNull() ?: return
        val parsed = parseTranscript(stored.content) ?: return
        lastSyncedAt = stored.updatedAt
        _chatMessages.value = mergeAttachmentBytes(_chatMessages.value, parsed)
    }

    /**
     * Adopt the server's transcript when another device has moved it on. Called when the panel
     * comes back to the foreground; our own writes are recognised by their `updatedAt` and
     * skipped, so an in-flight conversation is never clobbered by its own echo.
     */
    fun syncTranscript() {
        if (_streaming.value) return
        // A live GROW session is ephemeral and off to the side; never let a sync pull the chat
        // transcript in over it (the web's sync effect returns early the same way).
        if (isGrowMode(_mode.value)) return
        viewModelScope.launch {
            val stored = runCatching { api.getTranscript(goalId) }
                .onFailure { SpiraLog.w(TAG, "ai_transcript_sync_failed goalId=$goalId", it) }
                .getOrNull() ?: return@launch
            if (stored.updatedAt != null && stored.updatedAt == lastSyncedAt) return@launch
            val parsed = parseTranscript(stored.content) ?: return@launch
            lastSyncedAt = stored.updatedAt
            _chatMessages.value = mergeAttachmentBytes(_chatMessages.value, parsed)
        }
    }

    /**
     * **Mirror the live session to the server** (owner, 2026-09-08).
     *
     * A GROW session is still ephemeral in the sense that matters — it is deleted the moment it
     * ends, and only the record the user keeps outlives it — but it is no longer tied to the
     * device it began on. The payload is deliberately the web's own shape (`mins`, `total` in
     * seconds, the wall-clock `endsAt`, and the messages), so a session started on the phone
     * resumes on the laptop and back.
     *
     * Best-effort: a session that cannot reach the server still runs perfectly well here.
     */
    private fun persistGrowSession() {
        val gid = goalId ?: return
        if (!isGrowMode(_mode.value) || ended) return
        val payload = org.json.JSONObject()
            .put("mins", _sessionMinutes.value)
            .put("total", _sessionMinutes.value * 60)
            .put("endsAt", System.currentTimeMillis() + _remainingSeconds.value * 1000L)
            .put(
                "msgs",
                org.json.JSONArray(
                    encodeTranscript(_growMessages.value.filterNot { it.streaming }),
                ),
            )
        viewModelScope.launch { runCatching { api.putGrowSession(gid, payload.toString()) } }
    }

    /**
     * Pick up a session left running — here or on another device. Called once, when the panel
     * opens: if the clock has already run out, the restored negative remainder puts the session
     * straight into the ending it was heading for rather than losing it.
     */
    private fun restoreGrowSession() {
        val gid = goalId ?: return
        viewModelScope.launch {
            val content = runCatching { api.getGrowSession(gid) }.getOrNull() ?: return@launch
            if (isGrowMode(_mode.value)) return@launch // already in one here — this device wins
            val stored = runCatching { org.json.JSONObject(content) }.getOrNull() ?: return@launch
            val msgs = parseTranscript(stored.optJSONArray("msgs")?.toString()) ?: return@launch
            val endsAt = stored.optLong("endsAt", 0L)
            if (endsAt == 0L) return@launch
            val remaining = ((endsAt - System.currentTimeMillis()) / 1000L).toInt()
            val hasContent = msgs.any { it.role == ChatRole.ASSISTANT && it.content.isNotBlank() }
            if (remaining <= 0 && !hasContent) {
                // Expired with nothing said — nothing worth closing ceremonially.
                clearGrowSessionRemote()
                return@launch
            }
            _growMessages.value = msgs
            _sessionMinutes.value = stored.optInt("mins", DEFAULT_SESSION_MINUTES)
            _remainingSeconds.value = remaining
            ended = false
            wrapUpSent = false
            goodbyeSent = false
            closeSessionCalled = false
            leaveGrowCalled = false
            overtimeInactivitySeconds = 0
            _mode.value = ChatMode.GROW_ACTIVE
            startTimer()
        }
    }

    private fun clearGrowSessionRemote() {
        val gid = goalId ?: return
        viewModelScope.launch { runCatching { api.deleteGrowSession(gid) } }
    }

    private fun persist() {
        val json = encodeTranscript(_chatMessages.value)
        viewModelScope.launch {
            // The highest-value one: a failure here means the whole conversation is not
            // saved, and the UI gives no sign of it until the next device shows nothing.
            lastSyncedAt = runCatching { api.putTranscript(goalId, json) }
                .onFailure { SpiraLog.w(TAG, "ai_transcript_save_failed goalId=$goalId", it) }
                .getOrNull() ?: lastSyncedAt
        }
    }

    /** "New chat" — clears this scope everywhere. */
    fun clearChat() {
        cancelStream()
        _chatMessages.value = emptyList()
        _mode.value = ChatMode.CHAT
        stopTimer()
        // The screen clears regardless, so a failure leaves the old chat on the server and
        // it reappears on the next sync — "New chat" that didn't take.
        viewModelScope.launch {
            runCatching { api.deleteTranscript(goalId) }
                .onFailure { SpiraLog.w(TAG, "ai_transcript_delete_failed goalId=$goalId", it) }
        }
    }

    // ── Sending ─────────────────────────────────────────────────────────────

    /**
     * Send one message. [revisedLabel] marks a turn that came from a proposal card's Edit box, so
     * the bubble can say which card it is revising.
     */
    fun send(
        text: String,
        attachments: List<AiApi.ChatAttachment> = emptyList(),
        revisedLabel: String? = null,
        /**
         * This turn asks the coach to close. It reports **zero** seconds left whatever the clock
         * says — the coach is being asked to wrap up, and "there is room to explore" would argue
         * against it — and, if the coach still does not call `end_session`, its reply is taken as
         * the closing one so the session cannot be left with nothing able to end it.
         */
        wrapUp: Boolean = false,
        /** This turn is the goodbye: the last thing the user hears before the session closes. */
        goodbye: Boolean = false,
        /**
         * This turn is STEP 2 of the ending — "what belongs in the goal" — asked on its own,
         * after the record is decided. It used to share a reply with `end_session`, and that is
         * the half models dropped: whole sessions ended having proposed nothing at all (owner,
         * 2026-09-08). Its proposals are attached to the turn like any other, so the footer
         * shows them; if it proposes nothing, the ending moves straight to the goodbye.
         */
        proposalsTurn: Boolean = false,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        if (_streaming.value) return

        // **A control turn is not something the user said.** The wrap-up and goodbye
        // instructions are addressed to the coach — they belong in the request, not in the
        // conversation — and one of them appeared on screen as a chat bubble reading "[I am
        // ending this session now, before it reached its natural end. Close it honestly: …]"
        // (owner, 2026-08-24). The web has hidden both from the transcript from the start
        // (`if (!wrapUp)` in `sendGrow`); the model still receives them as the message.
        val fromTheUser = !wrapUp && !goodbye && !proposalsTurn

        // The message is on its way — empty the composer so the draft and chips don't linger.
        // A control turn leaves it alone: the user may be halfway through a sentence.
        if (fromTheUser) clearComposer()
        // The user just picked the session back up — the overtime-inactivity clock starts over.
        if (fromTheUser) overtimeInactivitySeconds = 0

        val userMessage = ChatMessage(
            id = randomProposalId(),
            role = ChatRole.USER,
            content = trimmed,
            attachments = attachments,
            revisedLabel = revisedLabel,
        )
        val placeholderId = randomProposalId()
        // Whichever conversation we're in, this turn stays in it — a GROW turn never touches the
        // chat list, and the captured [target] is used for every update below so a mode flip
        // mid-stream (active → closing → end) can't misroute the streaming reply.
        val growing = _mode.value == ChatMode.GROW_ACTIVE || _mode.value == ChatMode.GROW_CLOSING
        val target = if (isGrowMode(_mode.value)) _growMessages else _chatMessages
        val placeholder = ChatMessage(placeholderId, ChatRole.ASSISTANT, "", streaming = true)
        target.update { if (fromTheUser) it + userMessage + placeholder else it + placeholder }

        val history = buildHistory(
            if (fromTheUser) target.value.dropLast(2) + userMessage else target.value.dropLast(1),
        )

        _streaming.value = true
        streamJob = viewModelScope.launch {
            val answer = StringBuilder()
            val proposals = mutableListOf<Proposal>()
            var failed = false

            var endRecord: String? = null

            // The `finally` is load-bearing: without it, any exception the collector doesn't
            // turn into a well-formed `ChatEvent.Error` (a genuine uncaught failure, not the
            // provider errors `AiApi.streamChat` already catches) leaves `_streaming` stuck
            // `true` forever — which disables the End pill (`canEndEarly = !streaming && …`)
            // and the composer, with no way out short of leaving the screen (owner report,
            // 2026-09-02: the End pill stayed dead once the session ran into overtime).
            // `CancellationException` is rethrown rather than swallowed: a genuinely cancelled
            // turn (Stop, or leaving the screen) must not fall through to `beginEnding` below.
            try {
                api.streamChat(
                    goalId = goalId,
                    message = trimmed,
                    history = history,
                    provider = _provider.value,
                    sessionType = if (growing) "grow" else "chat",
                    attachments = attachments,
                    sessionTotalMinutes = if (growing) _sessionMinutes.value else null,
                    sessionRemainingSeconds = when {
                        !growing -> null
                        wrapUp -> 0
                        else -> _remainingSeconds.value
                    },
                ).collect { event ->
                    when (event) {
                        is AiApi.ChatEvent.Token -> {
                            answer.append(event.text)
                            updateMessage(target, placeholderId) { it.copy(content = answer.toString()) }
                        }
                        is AiApi.ChatEvent.Proposal ->
                            proposalFromToolArgs(event.argsJson)?.let { proposals += it }
                        is AiApi.ChatEvent.Status ->
                            updateMessage(target, placeholderId) { it.copy(status = event.message) }
                        is AiApi.ChatEvent.SessionEnd -> endRecord = sessionRecordOf(event.argsJson)
                        AiApi.ChatEvent.Done -> Unit
                        is AiApi.ChatEvent.Error -> {
                            failed = true
                            if (event.message == AiApi.ERROR_NO_KEY) _needsKey.value = true
                            updateMessage(target, placeholderId) {
                                it.copy(
                                    content = errorText(event.message),
                                    streaming = false,
                                    isError = true,
                                    status = null,
                                )
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                SpiraLog.w(TAG, "ai_stream_collect_failed", e)
                updateMessage(target, placeholderId) {
                    it.copy(
                        content = errorText(AiApi.ERROR_NETWORK),
                        streaming = false,
                        isError = true,
                        status = null,
                    )
                }
            } finally {
                _streaming.value = false
            }

            val settled = dedupCreates(proposals)
            // The ending turn's cards WAIT: the record is decided first, then they are released
            // as the review step. Mid-session cards (which the method forbids anyway) still show.
            // A STEP 2 turn is never an ending turn, whatever the model calls — see the note on
            // the web's `ending` in `AiPanel.tsx`. Without this, a coach that called
            // `end_session` again on the proposals turn lost every proposal in it.
            val ending = endRecord != null && !proposalsTurn

            if (!failed) {
                updateMessage(target, placeholderId) {
                    it.copy(
                        // A reply that is only a tool call has no prose; say something rather
                        // than leaving an empty bubble above the card.
                        //
                        // The goodbye is the case that made this matter. When the coach answered
                        // it with a tool call and no words, the blank turn was dropped as noise
                        // and the session closed with **no farewell at all** — after a whole
                        // sequence built to end on one (owner, 2026-08-24). The web has always
                        // had this net; Android only had half of it.
                        // **Never write words the coach did not say.** A turn that is only tool
                        // calls has no prose, and inventing some produced "Here's what I suggest."
                        // above nothing at all — the owner asked, twice, what exactly was being
                        // suggested (2026-09-08), and the same sentence then became the session
                        // record (see `beginEnding`). An empty turn is dropped just below; the
                        // card it produced is what speaks for it.
                        //
                        // The goodbye keeps its fallback, and only it: a farewell that arrives as
                        // a bare tool call would otherwise end the session in silence after a
                        // whole sequence built to end on one (owner, 2026-08-24). That sentence is
                        // a real goodbye, not a description of something the user cannot see.
                        content = answer.toString().ifBlank {
                            if (goodbye) "Thank you for the session." else ""
                        },
                        streaming = false,
                        status = null,
                        proposals = if (ending || wrapUp) emptyList() else settled,
                    )
                }
                // An assistant turn that produced nothing at all is noise — drop it.
                target.update { list ->
                    list.filterNot { it.id == placeholderId && it.content.isBlank() && it.proposals.isEmpty() }
                }
            }

            when {
                // The goodbye has been said. Do NOT exit here: leaving now would wipe it off
                // the screen the moment it arrived. The user closes when they have read it.
                goodbye -> goodbyeSent = false
                // A wrap-up turn that failed must still end the session. `wrapUpSent` was set
                // true before this call (both here and in the overrun backstop); if nothing
                // resets it and nothing ends the session, the End pill and the backstop are
                // both permanently locked out (owner report, 2026-09-02 — this is what made
                // "End" stay dead once the session had gone into overtime). Ending on an empty
                // record falls back to the last real coach message, same as any other ending
                // with nothing better to show — see `beginEnding`. The web's `onError` does the
                // same thing (`finishGrow()`) for the identical reason: the session must never
                // be left with nothing able to close it.
                failed && wrapUp -> beginEnding("")
                // The goal keeps whatever it already had; the session still has to end.
                failed && proposalsTurn -> askForGoodbye()
                failed -> Unit
                // STEP 2 answered. Cards → the footer shows them and the user decides; nothing
                // proposed → nothing to decide, so hand straight over to the goodbye. Either way
                // the session must not be parked in review with no card, which is the one state
                // with no way forward.
                proposalsTurn -> if (sessionProposalsPending() == 0) askForGoodbye()
                ending -> {
                    heldProposals = settled
                    beginEnding(endRecord ?: "")
                }
                // Told to wrap up and it did not call `end_session`. Its reply is all there is,
                // so end on that rather than leaving a session nothing can close.
                wrapUp -> {
                    heldProposals = settled
                    beginEnding(answer.toString().trim())
                }
            }
            // The chat transcript persists as it always has; the live session now mirrors
            // itself too, so it is not tied to this device.
            if (growing) persistGrowSession() else persist()
        }
    }

    /** Stop button: abandon the in-flight answer but keep what has streamed so far. */
    fun cancelStream() {
        streamJob?.cancel()
        streamJob = null
        _streaming.value = false
        // **A cancelled turn never happened, so its one-shot guards must not survive it.**
        // `wrapUpSent`/`goodbyeSent` are set BEFORE the request and cleared by the completion
        // handler — which a cancellation never reaches, because the collector throws
        // CancellationException and the `when` block below it is skipped. So End (which bails on
        // `wrapUpSent`) stayed dead for the rest of the session: press End, press Stop, and there
        // was no way left to finish (owner, 2026-09-08, on a session where the provider was
        // rate-limiting). BUG-076 fixed the same lock-out for a FAILED wrap-up and missed the
        // cancelled one.
        wrapUpSent = false
        goodbyeSent = false
        // And the inactivity clock restarts. In overtime it is already past the threshold, so
        // releasing `wrapUpSent` without this let the backstop re-send the wrap-up on the very
        // next tick: Stop would have been unstoppable, one second at a time.
        overtimeInactivitySeconds = 0
        activeList().update { list ->
            list.mapNotNull { m ->
                when {
                    !m.streaming -> m
                    m.content.isBlank() -> null
                    else -> m.copy(streaming = false)
                }
            }
        }
    }

    private fun updateMessage(
        target: MutableStateFlow<List<ChatMessage>>,
        id: String,
        transform: (ChatMessage) -> ChatMessage,
    ) {
        target.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun errorText(code: String): String = when (code) {
        AiApi.ERROR_NO_KEY -> "No API key saved for this provider. Add one to start chatting."
        AiApi.ERROR_NETWORK -> "Couldn't reach the assistant. Check your connection and try again."
        else -> code
    }

    // ── Proposals ───────────────────────────────────────────────────────────

    /**
     * Record the user's decision on a card. The change itself is applied by the host screen
     * (which owns the goal's mutations); this only settles the card and tells the server, so the
     * decision survives a reload and follows the user to their other devices.
     */
    fun settleProposal(messageId: String, proposalId: String, approved: Boolean) {
        var serverId: Long? = null
        val next = if (approved) ProposalStatus.APPROVED else ProposalStatus.REJECTED
        // A GROW session's cards live in the ephemeral list; settle them there and do not persist
        // (the change itself is still applied on the server below).
        val inGrow = isGrowMode(_mode.value)
        activeList().update { list ->
            list.map { message ->
                if (message.id != messageId) {
                    message
                } else {
                    message.copy(
                        proposals = message.proposals.map { p ->
                            if (p.id != proposalId) {
                                p
                            } else {
                                serverId = p.serverId
                                p.copy(status = next)
                            }
                        },
                    )
                }
            }
        }
        serverId?.let { id ->
            viewModelScope.launch {
                runCatching { if (approved) api.approveProposal(id) else api.rejectProposal(id) }
            }
        }
        if (!inGrow) persist()
        // Step 2 of the ending is over as soon as nothing is waiting — then the goodbye.
        if (_mode.value == ChatMode.GROW_REVIEW && sessionProposalsPending() == 0) askForGoodbye()
    }

    /**
     * "Edit" on a card: re-ask the model, then swap the answer into **the same card**.
     *
     * This used to be `send(...)`, an ordinary turn — which meant the revised proposal arrived as
     * a *new* card while the original stayed PENDING in the footer. The user got two cards for one
     * change, had to dismiss the card they had just corrected before the correction was even
     * reachable, and the new one was drawn in the transcript as a muted "Dismissed" pill it could
     * never be answered from (owner, 2026-08-24: "остаётся висеть старая карточка, а под ней
     * появляется исправленная — карточка должна быть только 1"). The web has revised in place from
     * the start (`reviseInPlace` in `AiPanel.tsx`); this is its twin, and the two must stay level.
     *
     * Three things it does that a plain turn does not:
     *
     * - **The transcript gets the user's own words**, captioned with the card's name. The prompt's
     *   scaffolding ("Revise this proposal: kind: note ...") is for the model; it was going
     *   straight into the conversation as a chat bubble.
     * - **The old server row is rejected.** It is superseded, and a row left PENDING is one the
     *   proposal-restore path can bring back later as a card nobody asked for.
     * - **The model is given the WHOLE current proposal** ([proposalContext]) and told to repeat
     *   what it is not changing, because the re-proposal *replaces* the old one — anything it
     *   leaves out is something the user silently loses.
     *
     * [messageId] is the message the card hangs off, so the answer can be put back in its slot.
     */
    fun reviseProposal(messageId: String, proposal: Proposal, instruction: String) {
        val ask = instruction.trim()
        if (ask.isEmpty() || _streaming.value) return

        val growing = isGrowMode(_mode.value)
        val target = activeList()
        val placeholderId = randomProposalId()
        target.update {
            it + ChatMessage(
                id = randomProposalId(),
                role = ChatRole.USER,
                content = ask,
                revisedLabel = proposal.title,
            ) + ChatMessage(placeholderId, ChatRole.ASSISTANT, "", streaming = true)
        }

        proposal.serverId?.let { id ->
            viewModelScope.launch {
                // Orphaning the row only costs a stale PENDING proposal, but that is exactly what
                // resurfaces later as a card the user never asked for -- worth seeing.
                runCatching { api.rejectProposal(id) }
                    .onFailure { SpiraLog.w(TAG, "ai_proposal_supersede_failed", it) }
            }
        }

        val history = buildHistory(target.value.dropLast(1))
        val prompt = buildString {
            append("Revise the ").append(kindLabel(proposal.kind).lowercase())
            append(" you proposed. Keep everything the user has already asked for and apply only ")
            append("the new change on top of it.\n\nCurrent proposal:\n")
            append(proposalContext(proposal))
            append("\n\nNew change: ").append(ask)
            append("\n\nRe-propose it with the change applied - one proposal, complete: repeat ")
            append("every field you are not changing.")
        }

        _streaming.value = true
        streamJob = viewModelScope.launch {
            val answer = StringBuilder()
            val revised = mutableListOf<Proposal>()
            var failed = false

            // See the matching `finally` in `send()`: without it, an exception the collector
            // doesn't turn into a `ChatEvent.Error` leaves `_streaming` stuck `true` forever.
            try {
                api.streamChat(
                    goalId = goalId,
                    message = prompt,
                    history = history,
                    provider = _provider.value,
                    sessionType = if (growing) "grow" else "chat",
                    attachments = emptyList(),
                    sessionTotalMinutes = if (growing) _sessionMinutes.value else null,
                    sessionRemainingSeconds = if (growing) _remainingSeconds.value else null,
                ).collect { event ->
                    when (event) {
                        is AiApi.ChatEvent.Token -> {
                            answer.append(event.text)
                            updateMessage(target, placeholderId) { it.copy(content = answer.toString()) }
                        }
                        is AiApi.ChatEvent.Proposal ->
                            proposalFromToolArgs(event.argsJson)?.let { revised += it }
                        is AiApi.ChatEvent.Status ->
                            updateMessage(target, placeholderId) { it.copy(status = event.message) }
                        // A revision is not a session, so an `end_session` here is not ours to act on.
                        is AiApi.ChatEvent.SessionEnd -> Unit
                        AiApi.ChatEvent.Done -> Unit
                        is AiApi.ChatEvent.Error -> {
                            failed = true
                            if (event.message == AiApi.ERROR_NO_KEY) _needsKey.value = true
                            updateMessage(target, placeholderId) {
                                it.copy(
                                    content = errorText(event.message),
                                    streaming = false,
                                    isError = true,
                                    status = null,
                                )
                            }
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                SpiraLog.w(TAG, "ai_stream_collect_failed", e)
                updateMessage(target, placeholderId) {
                    it.copy(
                        content = errorText(AiApi.ERROR_NETWORK),
                        streaming = false,
                        isError = true,
                        status = null,
                    )
                }
            } finally {
                _streaming.value = false
            }

            if (!failed) {
                val fresh = dedupCreates(revised)
                if (fresh.isEmpty()) {
                    // The model answered with words -- a clarifying question, or a refusal --
                    // rather than a revised proposal. Say so, and leave the card exactly as it is.
                    updateMessage(target, placeholderId) {
                        it.copy(content = answer.toString(), streaming = false, status = null)
                    }
                    target.update { list ->
                        list.filterNot { it.id == placeholderId && it.content.isBlank() }
                    }
                } else {
                    // The first revision takes the original card's slot -- same id, still pending.
                    // Any extras join the SAME message, so it stays one card group.
                    val replacement = fresh.first().copy(
                        id = proposal.id,
                        status = ProposalStatus.PENDING,
                    )
                    val extras = fresh.drop(1).map { it.copy(status = ProposalStatus.PENDING) }
                    target.update { list ->
                        list.map { m ->
                            if (m.id != messageId) {
                                m
                            } else {
                                m.copy(
                                    proposals = m.proposals.flatMap { existing ->
                                        if (existing.id == proposal.id) {
                                            listOf(replacement) + extras
                                        } else {
                                            listOf(existing)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    updateMessage(target, placeholderId) {
                        it.copy(
                            content = answer.toString().ifBlank {
                                "Updated \u00ab${replacement.title}\u00bb."
                            },
                            streaming = false,
                            status = null,
                        )
                    }
                }
            }

            if (!growing) persist()
        }
    }

    // ── GROW session ────────────────────────────────────────────────────────

    fun openGrowStart() {
        _mode.value = ChatMode.GROW_START
    }

    fun cancelGrow() {
        _mode.value = ChatMode.CHAT
        _growMessages.value = emptyList()
        stopTimer()
    }

    /** Begin a coaching session of [minutes] and let the coach open the conversation. */
    fun startGrow(minutes: Int) {
        _sessionMinutes.value = minutes
        _remainingSeconds.value = minutes * 60
        // A fresh, empty session — never carrying over a previous one's messages.
        _growMessages.value = emptyList()
        _mode.value = ChatMode.GROW_ACTIVE
        heldProposals = emptyList()
        _memoryDraft.value = null
        ended = false
        closeSessionCalled = false
        leaveGrowCalled = false
        wrapUpSent = false
        goodbyeSent = false
        memorySaved = false
        overtimeInactivitySeconds = 0
        startTimer()
        persistGrowSession()
        send("Let's start a GROW session.")
    }

    /**
     * Ask the coach to wrap up. It does not end the session by itself.
     *
     * **This is not what the End button does** (owner, 2026-09-08) — End is [endGrowNow], which
     * needs nobody and sends nothing. This is the coach-driven close: the overtime backstop's
     * path, and the shape the ending takes when the conversation itself finishes.
     *
     * The difference matters and is the whole of the owner's complaint. This used to send "We're
     * out of time — please close the session now" and then flip straight to the record card as
     * soon as the reply finished, so the coach never got to run its own close: no *"Are we
     * complete?"*, no asking the user what they are taking away, no chance to propose anything.
     * Now it sends [WRAP_UP_INSTRUCTION] and the coach ends the session itself by calling
     * `end_session`.
     *
     * The timer calls this rather than repeating its body, so the guard and the instruction have
     * one home. They briefly had two — and once End stopped going through here, this function was
     * left reachable only from its own tests, sending an instruction nothing else used.
     */
    fun closeGrow() {
        if (_mode.value != ChatMode.GROW_ACTIVE && _mode.value != ChatMode.GROW_CLOSING) return
        if (ended || _streaming.value || wrapUpSent) return
        wrapUpSent = true
        _mode.value = ChatMode.GROW_CLOSING
        send(WRAP_UP_INSTRUCTION, wrapUp = true)
    }

    /**
     * **End: leave the session now, without the coach.**
     *
     * The one action in the panel that must never depend on the provider (owner, 2026-09-08:
     * "сессия по end должна завершаться даже если провайдер не отвечает… если end то это
     * завершение без участия ai"). It asks nobody, waits for nothing, and saves nothing — no
     * record, no memory, no goodbye. Everything it touches is local state.
     *
     * The graceful close — record, proposals, farewell — is still there; it is what happens when
     * the *coach* ends the session, which is where it belongs. End is the way out, and a way out
     * that can be blocked by a rate-limited provider is not one.
     */
    fun endGrowNow() {
        if (!isGrowMode(_mode.value)) return
        cancelStream()
        ended = true
        stopTimer()
        _memoryDraft.value = null
        heldProposals = emptyList()
        leaveGrow()
    }

    /**
     * **Step 1 of the ending.** The coach has closed the session and handed over its record; show
     * it for the user to keep or discard. Nothing has been written anywhere yet.
     */
    private fun beginEnding(record: String) {
        if (ended) return
        ended = true
        stopTimer()
        // The session is over the moment the record appears, so it stops being resumable here —
        // exactly where the web's `finishGrow` calls `clearGrowSession`. Left behind, the row
        // outlives the session and the next open (here or on another device) picks it back up as
        // an ACTIVE session with a stale clock, taking the record card off the screen with it.
        clearGrowSessionRemote()
        // **An empty record stays empty.** It used to fall back to the last thing the coach said,
        // which on a tool-call-only ending was the sentence the app itself had invented — so the
        // card offered to save "Here's what I suggest." as the memory of the session (owner,
        // 2026-09-08). A record nobody wrote is not a record: the card shows it as missing, and
        // `closeSession` already refuses to save a blank one, so nothing is written either way.
        // The user can still ask for a proper one through "Change the record…".
        _memoryDraft.value = record
        _mode.value = ChatMode.GROW_END
    }

    /**
     * **Step 1 → 2.** The user has decided on the record. This does *not* leave the session: the
     * held proposals are released next, and the goodbye comes after those.
     */
    fun closeSession(save: Boolean, onResult: (String?) -> Unit = {}) {
        if (closeSessionCalled) return
        closeSessionCalled = true
        val record = _memoryDraft.value.orEmpty().trim()
        memorySaved = save && record.isNotEmpty() && goalId != null
        if (memorySaved) {
            viewModelScope.launch {
                val error = runCatching { api.saveGoalMemory(goalId!!, record) }.exceptionOrNull()
                if (error != null) memorySaved = false
                onResult(error?.message)
            }
        } else {
            onResult(null)
        }
        _memoryDraft.value = null

        // **Anything still pending goes to review — held back or not.** The review step used to
        // look only at [heldProposals], which are filled on the turn that calls `end_session`. A
        // proposal that arrived on any other turn was attached to its message instead, and the
        // footer shows a pending card only in `GROW_REVIEW` — so it was never reachable: the
        // session went straight to the goodbye and signed off with "1 proposal awaits your
        // review", pointing at something with nowhere to be answered (owner, 2026-09-08).
        if (heldProposals.isNotEmpty()) {
            _growMessages.update {
                it + ChatMessage(
                    id = randomProposalId(),
                    role = ChatRole.ASSISTANT,
                    content = "I've prepared this for your review.",
                    proposals = heldProposals,
                )
            }
            heldProposals = emptyList()
        }
        if (sessionProposalsPending() > 0) {
            _mode.value = ChatMode.GROW_REVIEW
        } else {
            askForProposals()
        }
    }

    /**
     * **Step 2 → 3.** Everything has been decided, so ask the coach for its goodbye.
     *
     * It is told what the user actually kept, because a farewell thanking someone for accepting
     * what they rejected is worse than none at all.
     */
    /**
     * **STEP 2 of the ending, as a turn of its own** (owner, 2026-09-08: "не смешивай память
     * сессии и то, что нужно сохранить в цель").
     *
     * The record and the goal changes used to be asked for in one reply, and the second half was
     * the half that went missing — Cohere proposed nothing across a whole session, Gemini managed
     * one. Asked separately, after the record is decided, it is a question the coach has to answer
     * on its own. The web twin is `askForProposals` in `AiPanel.tsx`.
     */
    private fun askForProposals() {
        _mode.value = ChatMode.GROW_REVIEW
        send(
            "[The record is decided. Now the second question, and only this one: looking back " +
                "over the WHOLE of today's conversation, what — if anything — should change " +
                "about this goal? Call `propose_goal_change` for each one in this reply: an " +
                "obstacle or an action that surfaced, a strategy option, something that deserves " +
                "to be a target (the commitment, if there was one), a resource worth keeping, or " +
                "a rewording of the goal itself now that they can say what they actually want. " +
                "Judge each against THIS goal, not against the aim of the session, and use their " +
                "words. If nothing from today belongs in the goal, proposing nothing is the right " +
                "answer — say so in one line. No goodbye yet, and do not call end_session again.]",
            proposalsTurn = true,
        )
    }

    fun askForGoodbye() {
        if (goodbyeSent || _streaming.value) return
        goodbyeSent = true
        val all = _growMessages.value.flatMap { it.proposals }
        val kept = all.count { it.status == ProposalStatus.APPROVED }
        val declined = all.count { it.status == ProposalStatus.REJECTED }
        _mode.value = ChatMode.GROW_FAREWELL
        send(
            buildString {
                append("[The user has now decided what to keep from this session. ")
                append(
                    if (memorySaved) "They saved the session record. "
                    else "They chose not to save the session record. ",
                )
                append("They accepted ").append(kept).append(" and declined ").append(declined)
                append(" of the changes you proposed. ")
                // There is no "they ended early" line any more: End leaves without the coach,
                // so the only close that reaches a goodbye is one the coach ran itself.
                append("Say your goodbye now, in the language we have been speaking: short, ")
                append("human, and shaped by what they actually kept. Do not repeat the summary, ")
                append("do not propose anything, do not call any tools, and do not ask a question.]")
            },
            goodbye = true,
        )
    }

    /** **Step 4.** The user has read the goodbye: leave the session and note what became of it. */
    fun leaveGrow() {
        if (leaveGrowCalled) return
        leaveGrowCalled = true
        val pending = _growMessages.value
            .flatMap { it.proposals }
            .count { it.status == ProposalStatus.PENDING }
        val note = (if (memorySaved) "Session memory saved." else "Session ended without saving memory.") +
            if (pending > 0) {
                " $pending proposal${if (pending == 1) " awaits" else "s await"} your review."
            } else {
                ""
            }
        _mode.value = ChatMode.CHAT
        // The session is over and was never persisted — drop its messages so they can't reappear.
        _growMessages.value = emptyList()
        heldProposals = emptyList()
        _memoryDraft.value = null
        stopTimer()
        clearGrowSessionRemote()
        val noteId = randomProposalId()
        _chatMessages.update {
            it + ChatMessage(noteId, ChatRole.SYSTEM, note)
        }
        persist()
        // The note carries no information into the plain chat — `buildHistory` already drops
        // SYSTEM messages from what the model sees — so leaving it sitting there forever served
        // no purpose, and had a second, worse effect: the plain chat's own "New chat" affordance
        // gates on `messages.isNotEmpty()`, so it appeared over a chat the user never actually
        // typed a word into. It now self-clears, like the web panel's own toast notices do
        // (owner, 2026-09-03).
        viewModelScope.launch {
            kotlinx.coroutines.delay(SESSION_NOTE_MS)
            _chatMessages.update { list -> list.filterNot { it.id == noteId } }
            persist()
        }
    }

    /** Proposals from this session still awaiting a decision. */
    fun sessionProposalsPending(): Int =
        _growMessages.value.flatMap { it.proposals }.count { it.status == ProposalStatus.PENDING }

    /** Edit the record on the end card: the coach rewrites it from the user's instruction. */
    fun reviseSessionMemory(instruction: String) {
        val current = _memoryDraft.value.orEmpty()
        if (current.isBlank() || _revisingMemory.value) return
        _revisingMemory.value = true
        viewModelScope.launch {
            val revised = StringBuilder()
            runCatching {
                api.streamChat(
                    goalId = goalId,
                    message = "[The user wants the session record changed before it is saved. " +
                        "Apply their request and reply with ONLY the revised record — no " +
                        "preamble, no commentary, no tools.]\n\nCurrent record:\n$current" +
                        "\n\nChange requested: ${instruction.trim()}",
                    history = emptyList(),
                    provider = _provider.value,
                    sessionType = "grow",
                    attachments = emptyList(),
                    sessionTotalMinutes = _sessionMinutes.value,
                    sessionRemainingSeconds = 0,
                ).collect { event ->
                    if (event is AiApi.ChatEvent.Token) revised.append(event.text)
                }
            }.onFailure { SpiraLog.w(TAG, "grow_memory_revise_failed", it) }
            // A failed or empty rewrite leaves the record exactly as it was, rather than
            // replacing something real with nothing.
            revised.toString().trim().takeIf { it.isNotEmpty() }?.let { _memoryDraft.value = it }
            _revisingMemory.value = false
        }
    }

    /**
     * The session clock — **a guide, not a cut-off**.
     *
     * It used to stop at zero and end the session there, which is exactly what the owner saw: the
     * time ran out and a goodbye plus a save-memory question appeared by themselves. The coach owns
     * the ending now, so the clock is allowed to run **negative** and does only two things:
     *
     *  - at 80% elapsed it moves the session to [ChatMode.GROW_CLOSING], which is a note to the
     *    coach (and a quiet line on screen) that the session should start heading for a close;
     *  - [OVERTIME_INACTIVITY_SECONDS] after the **user's last message**, once the session is in
     *    overtime, it sends the wrap-up instruction — the backstop for a coach that never calls
     *    `end_session`, and for a session the user has simply walked away from.
     *
     * The backstop is keyed on **inactivity**, not on total overtime elapsed (owner, 2026-09-02).
     * It used to be the latter — ten minutes past the *planned* end, whether or not the user was
     * still actively talking — which is wrong for the same reason the clock does not stop at zero
     * at all: a session running long because the user is genuinely still in it must not be cut off
     * out from under them. [overtimeInactivitySeconds] resets on every real user turn (`send`,
     * `fromTheUser`), so picking the conversation back up buys another ten minutes; going quiet for
     * ten minutes — whether that is the first ten or the fifth — ends it.
     */
    private fun startTimer() {
        stopTimer()
        timerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (_mode.value != ChatMode.GROW_ACTIVE && _mode.value != ChatMode.GROW_CLOSING) {
                    continue
                }
                val left = _remainingSeconds.updateAndGet { it - 1 }
                val total = _sessionMinutes.value * 60
                if (total > 0 && left <= total / 5 && _mode.value == ChatMode.GROW_ACTIVE) {
                    _mode.value = ChatMode.GROW_CLOSING
                }
                if (left < 0) {
                    overtimeInactivitySeconds++
                    if (overtimeInactivitySeconds >= OVERTIME_INACTIVITY_SECONDS) {
                        closeGrow()
                    }
                } else {
                    overtimeInactivitySeconds = 0
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        stopTimer()
        streamJob?.cancel()
    }

    companion object {
        private const val TAG = "AiChatVM"

        const val DEFAULT_PROVIDER = "ANTHROPIC"
        const val DEFAULT_SESSION_MINUTES = 20

        /**
         * How long the user can go quiet, once the session is in overtime, before the coach is
         * TOLD to wrap up. Measured from the last real user message, not from the planned end —
         * see [startTimer]. Ten minutes is generous on purpose: long enough that it never cuts off
         * a reply that is merely slow to arrive, short enough that a session the user has actually
         * left does not sit open indefinitely.
         */
        private const val OVERTIME_INACTIVITY_SECONDS = 10 * 60

        /** How long a GROW session's end note sits in the plain chat before it self-clears. */
        private const val SESSION_NOTE_MS = 6000L

        /** The backstop's instruction: close it, honestly, from what actually happened. */
        private const val WRAP_UP_INSTRUCTION =
            "[We are well past the time set for this session, so wrap it up now. Work only " +
                "from what actually happened — if we never got to a commitment, say so rather " +
                "than writing it up as though we did. Call end_session with the record, and in " +
                "the same reply propose only what this session genuinely supports adding to the " +
                "goal (which may be nothing). No goodbye yet.]"

        /** The record the coach passed to `end_session`, or "" when the payload is unusable. */
        private fun sessionRecordOf(argsJson: String): String = runCatching {
            org.json.JSONObject(argsJson).optString("summary").trim()
        }.getOrDefault("")

        private const val KEY_DRAFT = "composer.draft"
        private const val KEY_COMPOSER_CHIPS = "composer.chips"

        /** Row prefixes for [persistComposerChips]. */
        private const val CHIP_RESOURCE = "r"
        private const val CHIP_FILE = "f"

        fun factory(goalId: String?, api: AiChat = LiveAiChat): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: androidx.lifecycle.viewmodel.CreationExtras,
                ): T = AiChatViewModel(goalId, api, extras.createSavedStateHandle()) as T
            }
    }
}

private fun String.equalsIgnoreCase(other: String) = equals(other, ignoreCase = true)

/**
 * The slice of [AiApi] the view model uses, as an interface so tests can drive the conversation
 * without a server.
 */
interface AiChat {
    fun streamChat(
        goalId: String?,
        message: String,
        history: List<AiApi.HistoryEntry>,
        provider: String,
        sessionType: String,
        attachments: List<AiApi.ChatAttachment>,
        sessionTotalMinutes: Int?,
        sessionRemainingSeconds: Int?,
    ): kotlinx.coroutines.flow.Flow<AiApi.ChatEvent>

    suspend fun listKeys(): List<AiApi.KeyInfo>
    suspend fun saveKey(provider: String, apiKey: String, model: String?): AiApi.KeyInfo
    suspend fun listProviderModels(provider: String): List<String>
    suspend fun updateKeyModel(provider: String, model: String)
    suspend fun getProvider(): String?
    suspend fun saveProvider(provider: String)
    suspend fun getTranscript(goalId: String?): AiApi.StoredTranscript?
    suspend fun putTranscript(goalId: String?, content: String): String?
    suspend fun deleteTranscript(goalId: String?)
    /**
     * The live GROW session, synced so it belongs to the user rather than to one device.
     * Defaulted because every test fake implements this interface, and none of them care.
     */
    suspend fun getGrowSession(goalId: String): String? = null
    suspend fun putGrowSession(goalId: String, content: String) = Unit
    suspend fun deleteGrowSession(goalId: String) = Unit

    suspend fun saveGoalMemory(goalId: String, summary: String)
    suspend fun approveProposal(id: Long)
    suspend fun rejectProposal(id: Long)
}

/** The real implementation — a thin pass-through to [AiApi]. */
object LiveAiChat : AiChat {
    override fun streamChat(
        goalId: String?,
        message: String,
        history: List<AiApi.HistoryEntry>,
        provider: String,
        sessionType: String,
        attachments: List<AiApi.ChatAttachment>,
        sessionTotalMinutes: Int?,
        sessionRemainingSeconds: Int?,
    ) = AiApi.streamChat(
        goalId, message, history, provider, sessionType,
        attachments, sessionTotalMinutes, sessionRemainingSeconds,
    )

    override suspend fun listKeys() = AiApi.listKeys()
    override suspend fun saveKey(provider: String, apiKey: String, model: String?) =
        AiApi.saveKey(provider, apiKey, model)
    override suspend fun listProviderModels(provider: String) = AiApi.listProviderModels(provider)
    override suspend fun updateKeyModel(provider: String, model: String) =
        AiApi.updateKeyModel(provider, model)
    override suspend fun getProvider() = AiApi.getProvider()
    override suspend fun saveProvider(provider: String) = AiApi.saveProvider(provider)
    override suspend fun getTranscript(goalId: String?) = AiApi.getTranscript(goalId)
    override suspend fun putTranscript(goalId: String?, content: String) =
        AiApi.putTranscript(goalId, content)
    override suspend fun deleteTranscript(goalId: String?) = AiApi.deleteTranscript(goalId)
    override suspend fun getGrowSession(goalId: String) = AiApi.getGrowSession(goalId)
    override suspend fun putGrowSession(goalId: String, content: String) =
        AiApi.putGrowSession(goalId, content)
    override suspend fun deleteGrowSession(goalId: String) = AiApi.deleteGrowSession(goalId)
    override suspend fun saveGoalMemory(goalId: String, summary: String) =
        AiApi.saveGoalMemory(goalId, summary)
    override suspend fun approveProposal(id: Long) = AiApi.approveProposal(id)
    override suspend fun rejectProposal(id: Long) = AiApi.rejectProposal(id)
}
