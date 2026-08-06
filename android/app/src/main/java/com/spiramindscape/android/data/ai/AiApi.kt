package com.spiramindscape.android.data.ai

import com.spiramindscape.android.data.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The AI assistant's HTTP API — the Android twin of the web `src/components/ai/ai-api.ts`,
 * talking to the same `/api/ai` endpoints on the same session cookie.
 *
 * Streaming uses Server-Sent Events exactly as the web does: `event: token|proposal|status|
 * done|error` with a JSON-encoded `data:` payload, dispatched on a blank line.
 */
object AiApi {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private const val BASE = "/api/ai"

    /**
     * A separate client for the chat stream: OkHttp's default 10s read timeout would cut a long
     * answer mid-sentence, and a streamed response has no natural "response received" moment.
     * Everything else (the cookie jar, the CSRF header) is inherited from the shared client.
     */
    private val streamClient: OkHttpClient by lazy {
        Network.okHttp.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun url(path: String) = "${Network.baseUrl}$BASE$path"

    // ── Chat streaming ──────────────────────────────────────────────────────

    /** One turn of the conversation as the model should see it. */
    data class HistoryEntry(val role: String, val content: String)

    /**
     * A file attached directly to a chat message: an image, PDF or DOCX. [dataUrl] is a
     * `data:<mime>;base64,…` URL. Ephemeral — sent with this message only, never saved as a
     * resource.
     */
    data class ChatAttachment(val name: String, val mime: String, val dataUrl: String)

    /** What the stream emits. The flow completes after [Done] or [Error]. */
    sealed interface ChatEvent {
        /** A chunk of the answer. Tokens arrive in order and are appended verbatim. */
        data class Token(val text: String) : ChatEvent

        /** A `propose_goal_change` tool call, as its raw arguments JSON. */
        data class Proposal(val argsJson: String) : ChatEvent

        /** Progress the user should see but which never joins the transcript. */
        data class Status(val message: String) : ChatEvent

        data object Done : ChatEvent

        data class Error(val message: String) : ChatEvent
    }

    /** `NO_KEY` is reported as its own error so the UI can offer to add one. */
    const val ERROR_NO_KEY = "NO_KEY"
    const val ERROR_NETWORK = "NETWORK"

    /**
     * Stream one reply. Cancelling the collecting coroutine cancels the HTTP call, which is how
     * the composer's Stop button works.
     */
    fun streamChat(
        goalId: String? = null,
        message: String,
        history: List<HistoryEntry>,
        provider: String = "ANTHROPIC",
        sessionType: String = "chat",
        attachments: List<ChatAttachment> = emptyList(),
        sessionTotalMinutes: Int? = null,
        sessionRemainingSeconds: Int? = null,
    ): Flow<ChatEvent> = callbackFlow {
        val body = JSONObject().apply {
            put("goalId", goalId?.toLongOrNull() ?: JSONObject.NULL)
            put("message", message)
            put(
                "history",
                JSONArray().apply {
                    history.forEach {
                        put(JSONObject().put("role", it.role).put("content", it.content))
                    }
                },
            )
            put("provider", provider)
            put("sessionType", sessionType)
            put(
                "attachments",
                if (attachments.isEmpty()) {
                    JSONObject.NULL
                } else {
                    JSONArray().apply {
                        attachments.forEach {
                            put(
                                JSONObject()
                                    .put("name", it.name)
                                    .put("mime", it.mime)
                                    .put("dataUrl", it.dataUrl),
                            )
                        }
                    }
                },
            )
            put("sessionTotalMinutes", sessionTotalMinutes ?: JSONObject.NULL)
            put("sessionRemainingSeconds", sessionRemainingSeconds ?: JSONObject.NULL)
        }

        val call = streamClient.newCall(
            Request.Builder()
                .url(url("/chat"))
                .header("Accept", "text/event-stream")
                .post(body.toString().toRequestBody(JSON))
                .build(),
        )

        try {
            val response = try {
                call.execute()
            } catch (e: Exception) {
                trySend(ChatEvent.Error(ERROR_NETWORK))
                close()
                return@callbackFlow
            }

            response.use { res ->
                if (!res.isSuccessful) {
                    // 422 is the backend's "no API key saved for this provider".
                    trySend(
                        ChatEvent.Error(
                            if (res.code == 422) ERROR_NO_KEY else "Server error: ${res.code}",
                        ),
                    )
                    close()
                    return@callbackFlow
                }

                val source = res.body?.source()
                if (source == null) {
                    trySend(ChatEvent.Error(ERROR_NETWORK))
                    close()
                    return@callbackFlow
                }

                var eventName = ""
                val dataLines = mutableListOf<String>()

                /** Returns true once a terminal event (done/error) has been dispatched. */
                fun dispatch(): Boolean {
                    if (eventName.isEmpty() && dataLines.isEmpty()) return false
                    val data = dataLines.joinToString("\n")
                    dataLines.clear()
                    val name = eventName
                    eventName = ""
                    return when (name) {
                        // Tokens are JSON-encoded by the backend so they survive newlines.
                        "token" -> { trySend(ChatEvent.Token(decodeToken(data))); false }
                        "proposal" -> { trySend(ChatEvent.Proposal(data.trim())); false }
                        "status" -> { trySend(ChatEvent.Status(data.trim())); false }
                        "done" -> { trySend(ChatEvent.Done); true }
                        "error" -> {
                            trySend(ChatEvent.Error(data.trim().ifEmpty { "AI service error" }))
                            true
                        }
                        else -> false
                    }
                }

                var finished = false
                while (!finished) {
                    val line = source.readUtf8Line() ?: break
                    when {
                        line.isEmpty() -> if (dispatch()) finished = true
                        line.startsWith("event:") -> eventName = line.substring(6).trim()
                        // Keep the value verbatim — JSON decoding handles leading spaces.
                        line.startsWith("data:") -> dataLines += line.substring(5)
                        // ":" comments and unknown fields are ignored.
                    }
                }

                // The stream can close right after an event without a trailing blank line;
                // flush it, or an error would be swallowed and read as a normal completion.
                if (!finished) {
                    val dispatched = if (eventName.isNotEmpty() || dataLines.isNotEmpty()) dispatch() else false
                    if (!dispatched) trySend(ChatEvent.Done)
                }
            }
        } catch (e: Exception) {
            trySend(ChatEvent.Error(ERROR_NETWORK))
        }
        close()

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    /** A token arrives JSON-encoded; fall back to the raw text if it isn't valid JSON. */
    private fun decodeToken(data: String): String =
        runCatching { JSONArray("[$data]").getString(0) }.getOrDefault(data)

    // ── Keys and models ─────────────────────────────────────────────────────

    /** A saved key as the server describes it — never the key itself. */
    data class KeyInfo(val provider: String, val hint: String?, val model: String?)

    suspend fun listKeys(): List<KeyInfo> = io {
        val text = getText(url("/keys")) ?: return@io emptyList()
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            KeyInfo(
                provider = o.optString("provider"),
                hint = o.optStringOrNull("hint"),
                model = o.optStringOrNull("model"),
            )
        }
    }

    suspend fun saveKey(provider: String, apiKey: String, model: String? = null): KeyInfo = io {
        val body = JSONObject()
            .put("provider", provider)
            .put("apiKey", apiKey)
            .put("model", model ?: JSONObject.NULL)
        val text = postText(url("/keys"), body)
            ?: throw AiException("Couldn't save the $provider key. Please try again.")
        val o = JSONObject(text)
        KeyInfo(o.optString("provider"), o.optStringOrNull("hint"), o.optStringOrNull("model"))
    }

    suspend fun listProviderModels(provider: String): List<String> = io {
        val text = getText(url("/keys/$provider/models"))
            ?: throw AiException("Couldn't load $provider models — check the key is valid.")
        val arr = JSONArray(text)
        (0 until arr.length()).map { arr.getString(it) }
    }

    suspend fun updateKeyModel(provider: String, model: String): Unit = io {
        val request = Request.Builder()
            .url(url("/keys/$provider"))
            .patch(JSONObject().put("model", model).toString().toRequestBody(JSON))
            .build()
        Network.okHttp.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw AiException(friendlyError(res.code, res.body?.string()))
        }
    }

    suspend fun deleteKey(provider: String): Unit = io {
        val request = Request.Builder().url(url("/keys/$provider")).delete().build()
        Network.okHttp.newCall(request).execute().use { }
    }

    // ── Provider preference (follows the user across devices) ───────────────

    suspend fun getProvider(): String? = io {
        val text = getText(url("/preferences")) ?: return@io null
        JSONObject(text).optStringOrNull("provider")
    }

    suspend fun saveProvider(provider: String): Unit = io {
        runCatching { putText(url("/preferences"), JSONObject().put("provider", provider)) }
        Unit
    }

    // ── Transcript sync ─────────────────────────────────────────────────────

    /** A stored transcript for one scope; [content] is the JSON array of messages. */
    data class StoredTranscript(val content: String, val updatedAt: String?)

    private fun transcriptUrl(goalId: String?) =
        url("/chat/transcript") + if (goalId != null) "?goalId=${goalId.toLongOrNull() ?: 0}" else ""

    /** Best-effort: null on any failure, so the chat still works from its local cache. */
    suspend fun getTranscript(goalId: String?): StoredTranscript? = io {
        val text = getText(transcriptUrl(goalId)) ?: return@io null
        val o = JSONObject(text)
        StoredTranscript(o.optString("content"), o.optStringOrNull("updatedAt"))
    }

    /** Last write wins. Returns the server's new `updatedAt`, or null if the write failed. */
    suspend fun putTranscript(goalId: String?, content: String): String? = io {
        val body = JSONObject()
            .put("goalId", goalId?.toLongOrNull() ?: JSONObject.NULL)
            .put("content", content)
        val text = runCatching { putText(url("/chat/transcript"), body) }.getOrNull() ?: return@io null
        JSONObject(text).optStringOrNull("updatedAt")
    }

    /** Clear the stored transcript for a scope ("New chat"). */
    suspend fun deleteTranscript(goalId: String?): Unit = io {
        val request = Request.Builder().url(transcriptUrl(goalId)).delete().build()
        runCatching { Network.okHttp.newCall(request).execute().use { } }
        Unit
    }

    // ── GROW session memory ─────────────────────────────────────────────────

    /** Persist a GROW session summary on the goal, so later sessions continue the thread. */
    suspend fun saveGoalMemory(goalId: String, summary: String): Unit = io {
        val body = JSONObject().put("summary", summary)
        postText(url("/goals/${goalId.toLongOrNull() ?: 0}/memory"), body)
            ?: throw AiException("Couldn't save the session memory. Please try again.")
        Unit
    }

    // ── Proposals ───────────────────────────────────────────────────────────

    /** A proposal as persisted server-side; its status is the source of truth. */
    data class ServerProposal(
        val id: Long,
        val goalId: Long?,
        val type: String,
        val payload: String,
        val status: String,
    )

    suspend fun listGoalProposals(goalId: String): List<ServerProposal> = io {
        val text = getText(url("/proposals/goal/$goalId")) ?: return@io emptyList()
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ServerProposal(
                id = o.optLong("id"),
                goalId = if (o.isNull("goalId")) null else o.optLong("goalId"),
                type = o.optString("type"),
                payload = o.optString("payload"),
                status = o.optString("status"),
            )
        }
    }

    suspend fun approveProposal(id: Long): Unit = io { postText(url("/proposals/$id/approve"), null); Unit }

    suspend fun rejectProposal(id: Long): Unit = io { postText(url("/proposals/$id/reject"), null); Unit }

    // ── plumbing ────────────────────────────────────────────────────────────

    /** Raised when a call fails with a message worth showing the user. */
    class AiException(message: String) : Exception(message)

    private suspend fun <T> io(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(Dispatchers.IO) { block() }

    private fun getText(fullUrl: String): String? =
        Network.okHttp.newCall(Request.Builder().url(fullUrl).build()).execute().use { res ->
            if (res.isSuccessful) res.body?.string() else null
        }

    private fun postText(fullUrl: String, body: JSONObject?): String? {
        val request = Request.Builder()
            .url(fullUrl)
            .post((body?.toString() ?: "{}").toRequestBody(JSON))
            .build()
        return Network.okHttp.newCall(request).execute().use { res ->
            if (res.isSuccessful) res.body?.string() else null
        }
    }

    private fun putText(fullUrl: String, body: JSONObject): String? {
        val request = Request.Builder()
            .url(fullUrl)
            .put(body.toString().toRequestBody(JSON))
            .build()
        return Network.okHttp.newCall(request).execute().use { res ->
            if (res.isSuccessful) res.body?.string() else null
        }
    }

    /**
     * A failed response as one readable sentence — never the raw ProblemDetail JSON. Validation
     * messages are already written for humans; a 5xx `detail` is only an internal reference id,
     * so it is deliberately hidden.
     */
    internal fun friendlyError(status: Int, body: String?): String {
        val trimmed = body?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                runCatching {
                    val o = JSONObject(trimmed)
                    val fieldMsg = o.optJSONArray("errors")
                        ?.optJSONObject(0)
                        ?.optStringOrNull("defaultMessage")
                    val problemMsg = if (status < 500) {
                        o.optStringOrNull("detail") ?: o.optStringOrNull("message")
                    } else {
                        null
                    }
                    (fieldMsg ?: problemMsg)?.let { return it }
                }
            } else {
                return trimmed
            }
        }
        return when {
            status == 401 || status == 403 -> "Your session expired. Please sign in again."
            status >= 500 -> "Something went wrong on the server. Please try again."
            else -> "That didn't work. Please try again."
        }
    }
}

/** `optString` returns "" for a JSON null, which is rarely what a nullable field wants. */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
