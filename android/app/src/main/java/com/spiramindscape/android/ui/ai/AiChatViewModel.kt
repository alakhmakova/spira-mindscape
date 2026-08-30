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
    private var endedEarly = false
    private var memorySaved = false

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
        val fromTheUser = !wrapUp && !goodbye

        // The message is on its way — empty the composer so the draft and chips don't linger.
        // A control turn leaves it alone: the user may be halfway through a sentence.
        if (fromTheUser) clearComposer()

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

            val settled = dedupCreates(proposals)
            // The ending turn's cards WAIT: the record is decided first, then they are released
            // as the review step. Mid-session cards (which the method forbids anyway) still show.
            val ending = endRecord != null

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
                        content = answer.toString().ifBlank {
                            when {
                                settled.isNotEmpty() -> "Here's what I suggest."
                                goodbye -> "Thank you for the session."
                                else -> ""
                            }
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

            _streaming.value = false

            when {
                // The goodbye has been said. Do NOT exit here: leaving now would wipe it off
                // the screen the moment it arrived. The user closes when they have read it.
                goodbye -> goodbyeSent = false
                failed -> Unit
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
            // Only the chat transcript is persisted; a GROW session is ephemeral by design.
            if (!growing) persist()
        }
    }

    /** Stop button: abandon the in-flight answer but keep what has streamed so far. */
    fun cancelStream() {
        streamJob?.cancel()
        streamJob = null
        _streaming.value = false
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

            _streaming.value = false
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
        wrapUpSent = false
        goodbyeSent = false
        endedEarly = false
        memorySaved = false
        startTimer()
        send("Let's start a GROW session.")
    }

    /**
     * The **End** button: ask the coach to wrap up. It does not end the session by itself.
     *
     * The difference matters and is the whole of the owner's complaint. This used to send "We're
     * out of time — please close the session now" and then flip straight to the record card as
     * soon as the reply finished, so the coach never got to run its own close: no *"Are we
     * complete?"*, no asking the user what they are taking away, no chance to propose anything.
     * Now it sends the same wrap-up instruction the overrun backstop sends, and the coach ends the
     * session itself by calling `end_session`.
     */
    fun closeGrow() {
        if (_mode.value != ChatMode.GROW_ACTIVE && _mode.value != ChatMode.GROW_CLOSING) return
        if (_streaming.value || wrapUpSent) return
        wrapUpSent = true
        endedEarly = true
        _mode.value = ChatMode.GROW_CLOSING
        send(EARLY_END_INSTRUCTION, wrapUp = true)
    }

    /**
     * **Step 1 of the ending.** The coach has closed the session and handed over its record; show
     * it for the user to keep or discard. Nothing has been written anywhere yet.
     */
    private fun beginEnding(record: String) {
        if (ended) return
        ended = true
        stopTimer()
        _memoryDraft.value = record.ifBlank {
            // A coach that ended without a record still ended: say so rather than showing a card
            // with nothing on it.
            _growMessages.value.lastOrNull { it.role == ChatRole.ASSISTANT && !it.isError }
                ?.content.orEmpty()
        }
        _mode.value = ChatMode.GROW_END
    }

    /**
     * **Step 1 → 2.** The user has decided on the record. This does *not* leave the session: the
     * held proposals are released next, and the goodbye comes after those.
     */
    fun closeSession(save: Boolean, onResult: (String?) -> Unit = {}) {
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
            _mode.value = ChatMode.GROW_REVIEW
        } else {
            askForGoodbye()
        }
    }

    /**
     * **Step 2 → 3.** Everything has been decided, so ask the coach for its goodbye.
     *
     * It is told what the user actually kept, because a farewell thanking someone for accepting
     * what they rejected is worse than none at all.
     */
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
                if (endedEarly) append("Remember they ended this session early, so keep it honest. ")
                append("Say your goodbye now, in the language we have been speaking: short, ")
                append("human, and shaped by what they actually kept. Do not repeat the summary, ")
                append("do not propose anything, do not call any tools, and do not ask a question.]")
            },
            goodbye = true,
        )
    }

    /** **Step 4.** The user has read the goodbye: leave the session and note what became of it. */
    fun leaveGrow() {
        val pending = _growMessages.value
            .flatMap { it.proposals }
            .count { it.status == ProposalStatus.PENDING }
        val note = (if (memorySaved) "Session memory saved." else "Session ended without saving memory.") +
            if (pending > 0) {
                " $pending proposal${if (pending == 1) "" else "s"} from the session await your review."
            } else {
                ""
            }
        _mode.value = ChatMode.CHAT
        // The session is over and was never persisted — drop its messages so they can't reappear.
        _growMessages.value = emptyList()
        heldProposals = emptyList()
        _memoryDraft.value = null
        stopTimer()
        _chatMessages.update {
            it + ChatMessage(randomProposalId(), ChatRole.SYSTEM, note)
        }
        persist()
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
     *  - at [OVERRUN_GRACE_SECONDS] past the planned end it sends the wrap-up instruction — the
     *    backstop for a coach that never calls `end_session`. Even then the ending is *asked for*
     *    rather than fabricated by the UI.
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
                if (left <= -OVERRUN_GRACE_SECONDS && !ended && !wrapUpSent && !_streaming.value) {
                    wrapUpSent = true
                    send(WRAP_UP_INSTRUCTION, wrapUp = true)
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
         * How far past the planned end the coach is left alone before it is TOLD to wrap up.
         *
         * The clock does not end a session — the coach does. This is the backstop for one that
         * never calls `end_session`, and it is generous on purpose: a session running ten minutes
         * over is usually a session in the middle of the part that mattered.
         */
        private const val OVERRUN_GRACE_SECONDS = 10 * 60

        /** The backstop's instruction: close it, honestly, from what actually happened. */
        private const val WRAP_UP_INSTRUCTION =
            "[We are well past the time set for this session, so wrap it up now. Work only " +
                "from what actually happened — if we never got to a commitment, say so rather " +
                "than writing it up as though we did. Call end_session with the record, and in " +
                "the same reply propose only what this session genuinely supports adding to the " +
                "goal (which may be nothing). No goodbye yet.]"

        /** The user pressed End: they asked for a proper close, not for the session to stop. */
        private const val EARLY_END_INSTRUCTION =
            "[I am ending this session now, before it reached its natural end. Close it " +
                "honestly: base everything only on what we actually covered, name what we did " +
                "and did not get to, and do not present it as a completed session. Call " +
                "end_session with that record, and propose something for the goal only if this " +
                "conversation really supports it — most likely nothing. No goodbye yet.]"

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
    override suspend fun saveGoalMemory(goalId: String, summary: String) =
        AiApi.saveGoalMemory(goalId, summary)
    override suspend fun approveProposal(id: Long) = AiApi.approveProposal(id)
    override suspend fun rejectProposal(id: Long) = AiApi.rejectProposal(id)
}
