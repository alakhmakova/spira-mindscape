package com.spiramindscape.android.data.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * The chat transcript model, wire-compatible with the web `Msg` type in
 * `src/components/ai/AiPanel.tsx`.
 *
 * The transcript is synced through `/api/ai/chat/transcript` as a JSON array, so a conversation
 * started on the phone opens on the laptop and vice versa. That makes the field names here a
 * **contract**, not an implementation detail — keep them in step with the web.
 */

enum class ChatRole(val wire: String) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system"),

    /** The GROW end card — a settled session summary, not a turn of conversation. */
    END("end"),
    ;

    companion object {
        fun from(wire: String?): ChatRole = entries.firstOrNull { it.wire == wire } ?: ASSISTANT
    }
}

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    /** The in-flight placeholder. Never persisted and never sent as history. */
    val streaming: Boolean = false,
    val proposals: List<Proposal> = emptyList(),
    /** An error bubble — shown with a warning icon, excluded from the model's history. */
    val isError: Boolean = false,
    /** Ephemeral progress (e.g. one-time GROW library indexing). Display-only. */
    val status: String? = null,
    /** Files attached to this user message. Shown as chips; the bytes are not persisted. */
    val attachments: List<AiApi.ChatAttachment> = emptyList(),
    /** Set on a user message from a card's "Edit" box: the headline of the card being revised. */
    val revisedLabel: String? = null,
)

/** Cap the stored history so a synced transcript stays small. Matches the web. */
const val CHAT_MAX_MESSAGES = 100

/**
 * The messages to persist: settled only (no in-flight placeholder), capped, with attachment file
 * bytes stripped — only names and labels survive, so the synced blob stays small.
 */
fun messagesForStore(messages: List<ChatMessage>): List<ChatMessage> =
    messages.filterNot { it.streaming }
        .takeLast(CHAT_MAX_MESSAGES)
        .map { m ->
            if (m.attachments.isEmpty()) m
            else m.copy(attachments = m.attachments.map { it.copy(dataUrl = "") })
        }

/**
 * Carry local attachment bytes into a transcript adopted from the server. The synced copy strips
 * file bytes, so adopting it would otherwise blank out an image still held in memory — and an
 * image chip only previews while its bytes are present.
 */
fun mergeAttachmentBytes(previous: List<ChatMessage>, next: List<ChatMessage>): List<ChatMessage> {
    val byId = previous.associateBy { it.id }
    return next.map { m ->
        if (m.attachments.isEmpty()) return@map m
        val old = byId[m.id] ?: return@map m
        if (old.attachments.isEmpty()) return@map m
        m.copy(
            attachments = m.attachments.mapIndexed { i, a ->
                if (a.dataUrl.isNotEmpty()) return@mapIndexed a
                val prev = old.attachments.getOrNull(i)
                if (prev != null && prev.dataUrl.isNotEmpty() && prev.name == a.name) {
                    a.copy(dataUrl = prev.dataUrl)
                } else {
                    a
                }
            },
        )
    }
}

// ── JSON (the cross-device transcript format) ───────────────────────────────

fun encodeTranscript(messages: List<ChatMessage>): String =
    JSONArray().apply { messagesForStore(messages).forEach { put(it.toJson()) } }.toString()

/** Parses a stored transcript, or null when it is unusable (so the caller keeps what it has). */
fun parseTranscript(content: String?): List<ChatMessage>? {
    if (content.isNullOrBlank()) return null
    return runCatching {
        val arr = JSONArray(content)
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { chatMessageFromJson(it) } }
    }.getOrNull()
}

private fun ChatMessage.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("role", role.wire)
    put("content", content)
    if (isError) put("error", true)
    if (proposals.isNotEmpty()) {
        put("proposals", JSONArray().apply { proposals.forEach { put(it.toJson()) } })
    }
    if (attachments.isNotEmpty()) {
        put(
            "attachments",
            JSONArray().apply {
                attachments.forEach {
                    put(
                        JSONObject()
                            .put("name", it.name)
                            .put("mime", it.mime)
                            .put("dataUrl", it.dataUrl),
                    )
                }
            },
        )
    }
    revisedLabel?.let { put("revisedLabel", it) }
}

private fun chatMessageFromJson(o: JSONObject): ChatMessage = ChatMessage(
    id = o.optStringOrNull("id") ?: randomProposalId(),
    role = ChatRole.from(o.optStringOrNull("role")),
    content = o.optString("content"),
    isError = o.optBoolean("error", false),
    proposals = o.optJSONArray("proposals")?.let { arr ->
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { proposalFromJson(it) } }
    } ?: emptyList(),
    attachments = o.optJSONArray("attachments")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            AiApi.ChatAttachment(
                name = a.optString("name"),
                mime = a.optString("mime"),
                dataUrl = a.optString("dataUrl"),
            )
        }
    } ?: emptyList(),
    revisedLabel = o.optStringOrNull("revisedLabel"),
)

private fun Proposal.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("kind", kind.wire)
    put("title", title)
    detail?.let { put("detail", it) }
    reasoning?.let { put("reasoning", it) }
    put("status", status.name.lowercase())
    field?.let { put("field", it) }
    body?.let { put("body", it) }
    deadline?.let { put("deadline", it) }
    rawValue?.let { put("rawValue", it) }
    serverId?.let { put("serverId", it) }
    itemId?.let { put("itemId", it) }
    done?.let { put("done", it) }
    targetType?.let { put("targetType", it.name.lowercase()) }
    total?.let { put("total", it) }
    current?.let { put("current", it) }
    unit?.let { put("unit", it) }
    items?.let { list ->
        put(
            "items",
            JSONArray().apply {
                list.forEach { item ->
                    put(
                        JSONObject().put("text", item.text).put("done", item.done).also { j ->
                            item.deadline?.let { j.put("deadline", it) }
                        },
                    )
                }
            },
        )
    }
    patch?.let { map -> put("patch", JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }) }
    goalId?.let { put("goalId", it) }
    openSubject?.let { put("openSubject", it) }
    confidence?.let { put("confidence", it) }
}

private fun proposalFromJson(o: JSONObject): Proposal = Proposal(
    id = o.optStringOrNull("id") ?: randomProposalId(),
    kind = ProposalKind.from(o.optStringOrNull("kind")),
    title = o.optString("title"),
    detail = o.optStringOrNull("detail"),
    reasoning = o.optStringOrNull("reasoning"),
    status = when (o.optStringOrNull("status")) {
        "approved" -> ProposalStatus.APPROVED
        "rejected" -> ProposalStatus.REJECTED
        else -> ProposalStatus.PENDING
    },
    field = o.optStringOrNull("field"),
    body = o.optStringOrNull("body"),
    deadline = o.optStringOrNull("deadline"),
    rawValue = o.optStringOrNull("rawValue"),
    serverId = if (o.has("serverId") && !o.isNull("serverId")) o.optLong("serverId") else null,
    itemId = o.optStringOrNull("itemId"),
    done = if (o.has("done") && !o.isNull("done")) o.optBoolean("done") else null,
    targetType = when (o.optStringOrNull("targetType")) {
        "numeric" -> TargetShape.NUMERIC
        "checklist" -> TargetShape.CHECKLIST
        "binary" -> TargetShape.BINARY
        else -> null
    },
    total = o.optStringOrNull("total"),
    current = o.optStringOrNull("current"),
    unit = o.optStringOrNull("unit"),
    items = o.optJSONArray("items")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            val item = arr.optJSONObject(i) ?: return@mapNotNull null
            val text = item.optStringOrNull("text") ?: return@mapNotNull null
            ProposalItem(text, item.optBoolean("done", false), item.optStringOrNull("deadline"))
        }
    },
    patch = o.optJSONObject("patch")?.let { p ->
        p.keys().asSequence().associateWith { p.optString(it) }
    },
    goalId = o.optStringOrNull("goalId"),
    openSubject = o.optStringOrNull("openSubject"),
    confidence = if (o.has("confidence") && !o.isNull("confidence")) o.optInt("confidence") else null,
)
