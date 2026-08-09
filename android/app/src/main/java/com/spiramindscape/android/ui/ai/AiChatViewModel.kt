package com.spiramindscape.android.ui.ai

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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which conversation the panel is in. Mirrors the web `Mode` state machine. */
enum class ChatMode { CHAT, GROW_START, GROW_ACTIVE, GROW_CLOSING, GROW_END }

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
) : ViewModel() {

    /** The goal this panel is scoped to, or null for the all-goals chat. */
    val scopeGoalId: String? = goalId

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _mode = MutableStateFlow(ChatMode.CHAT)
    val mode: StateFlow<ChatMode> = _mode.asStateFlow()

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

    private var streamJob: Job? = null
    private var timerJob: Job? = null

    /** The server's `updatedAt` for our own last write, so polling doesn't re-adopt it. */
    private var lastSyncedAt: String? = null

    init {
        viewModelScope.launch {
            _provider.value = runCatching { api.getProvider() }.getOrNull() ?: DEFAULT_PROVIDER
            refreshKeys()
            loadTranscript()
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
        _messages.value = mergeAttachmentBytes(_messages.value, parsed)
    }

    /**
     * Adopt the server's transcript when another device has moved it on. Called when the panel
     * comes back to the foreground; our own writes are recognised by their `updatedAt` and
     * skipped, so an in-flight conversation is never clobbered by its own echo.
     */
    fun syncTranscript() {
        if (_streaming.value) return
        viewModelScope.launch {
            val stored = runCatching { api.getTranscript(goalId) }
                .onFailure { SpiraLog.w(TAG, "ai_transcript_sync_failed goalId=$goalId", it) }
                .getOrNull() ?: return@launch
            if (stored.updatedAt != null && stored.updatedAt == lastSyncedAt) return@launch
            val parsed = parseTranscript(stored.content) ?: return@launch
            lastSyncedAt = stored.updatedAt
            _messages.value = mergeAttachmentBytes(_messages.value, parsed)
        }
    }

    private fun persist() {
        val json = encodeTranscript(_messages.value)
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
        _messages.value = emptyList()
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
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachments.isEmpty()) return
        if (_streaming.value) return

        val userMessage = ChatMessage(
            id = randomProposalId(),
            role = ChatRole.USER,
            content = trimmed,
            attachments = attachments,
            revisedLabel = revisedLabel,
        )
        val placeholderId = randomProposalId()
        _messages.update {
            it + userMessage + ChatMessage(placeholderId, ChatRole.ASSISTANT, "", streaming = true)
        }

        val history = buildHistory(_messages.value.dropLast(2) + userMessage)
        val growing = _mode.value == ChatMode.GROW_ACTIVE || _mode.value == ChatMode.GROW_CLOSING

        _streaming.value = true
        streamJob = viewModelScope.launch {
            val answer = StringBuilder()
            val proposals = mutableListOf<Proposal>()
            var failed = false

            api.streamChat(
                goalId = goalId,
                message = trimmed,
                history = history,
                provider = _provider.value,
                sessionType = if (growing) "grow" else "chat",
                attachments = attachments,
                sessionTotalMinutes = if (growing) _sessionMinutes.value else null,
                sessionRemainingSeconds = if (growing) _remainingSeconds.value else null,
            ).collect { event ->
                when (event) {
                    is AiApi.ChatEvent.Token -> {
                        answer.append(event.text)
                        updateMessage(placeholderId) { it.copy(content = answer.toString()) }
                    }
                    is AiApi.ChatEvent.Proposal ->
                        proposalFromToolArgs(event.argsJson)?.let { proposals += it }
                    is AiApi.ChatEvent.Status ->
                        updateMessage(placeholderId) { it.copy(status = event.message) }
                    AiApi.ChatEvent.Done -> Unit
                    is AiApi.ChatEvent.Error -> {
                        failed = true
                        if (event.message == AiApi.ERROR_NO_KEY) _needsKey.value = true
                        updateMessage(placeholderId) {
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
                updateMessage(placeholderId) {
                    it.copy(
                        // A reply that is only a tool call has no prose; say something rather
                        // than leaving an empty bubble above the card.
                        content = answer.toString().ifBlank {
                            if (proposals.isEmpty()) "" else "Here's what I suggest."
                        },
                        streaming = false,
                        status = null,
                        proposals = dedupCreates(proposals),
                    )
                }
                // An assistant turn that produced nothing at all is noise — drop it.
                _messages.update { list ->
                    list.filterNot { it.id == placeholderId && it.content.isBlank() && it.proposals.isEmpty() }
                }
            }

            _streaming.value = false
            if (growing && _mode.value == ChatMode.GROW_CLOSING) _mode.value = ChatMode.GROW_END
            persist()
        }
    }

    /** Stop button: abandon the in-flight answer but keep what has streamed so far. */
    fun cancelStream() {
        streamJob?.cancel()
        streamJob = null
        _streaming.value = false
        _messages.update { list ->
            list.mapNotNull { m ->
                when {
                    !m.streaming -> m
                    m.content.isBlank() -> null
                    else -> m.copy(streaming = false)
                }
            }
        }
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage) {
        _messages.update { list -> list.map { if (it.id == id) transform(it) else it } }
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
        _messages.update { list ->
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
        persist()
    }

    /**
     * "Edit" on a card: send the user's instruction along with the WHOLE current proposal, so a
     * second change can't lose the first. The instruction is written into the transcript, which is
     * why it is traceable afterwards.
     */
    fun reviseProposal(proposal: Proposal, instruction: String) {
        val prompt = buildString {
            append("Revise this proposal:\n")
            append(proposalContext(proposal))
            append("\n\nChange requested: ")
            append(instruction.trim())
        }
        send(prompt, revisedLabel = proposal.title)
    }

    // ── GROW session ────────────────────────────────────────────────────────

    fun openGrowStart() {
        _mode.value = ChatMode.GROW_START
    }

    fun cancelGrow() {
        _mode.value = ChatMode.CHAT
        stopTimer()
    }

    /** Begin a coaching session of [minutes] and let the coach open the conversation. */
    fun startGrow(minutes: Int) {
        _sessionMinutes.value = minutes
        _remainingSeconds.value = minutes * 60
        _mode.value = ChatMode.GROW_ACTIVE
        startTimer()
        send("Let's start a GROW session.")
    }

    /** End early, or when the timer runs out: the coach closes in its next reply. */
    fun closeGrow() {
        if (_mode.value != ChatMode.GROW_ACTIVE) return
        _mode.value = ChatMode.GROW_CLOSING
        stopTimer()
        _remainingSeconds.value = 0
        send("We're out of time — please close the session now.")
    }

    fun finishGrow() {
        _mode.value = ChatMode.CHAT
        stopTimer()
    }

    /** "Save memory" on the end card: later GROW sessions read it back. */
    fun saveSessionMemory(summary: String, onResult: (String?) -> Unit) {
        val id = goalId
        if (id == null) {
            onResult("A session summary can only be saved on a goal.")
            return
        }
        viewModelScope.launch {
            val error = runCatching { api.saveGoalMemory(id, summary) }.exceptionOrNull()
            onResult(error?.message)
        }
    }

    private fun startTimer() {
        stopTimer()
        timerJob = viewModelScope.launch {
            while (_remainingSeconds.value > 0) {
                kotlinx.coroutines.delay(1000)
                _remainingSeconds.update { (it - 1).coerceAtLeast(0) }
            }
            // Time is up: ask the coach to close rather than cutting it off mid-thought.
            if (_mode.value == ChatMode.GROW_ACTIVE && !_streaming.value) closeGrow()
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

        fun factory(goalId: String?, api: AiChat = LiveAiChat): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AiChatViewModel(goalId, api) as T
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
