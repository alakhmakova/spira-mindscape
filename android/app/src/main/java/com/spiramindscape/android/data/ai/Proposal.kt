package com.spiramindscape.android.data.ai

import org.json.JSONObject

/**
 * The AI assistant's proposal model — a Kotlin port of the web
 * `src/components/ai/proposal-logic.ts`, kept pure so it can be unit-tested without Compose.
 *
 * The model answers with a `propose_goal_change` tool call; its arguments JSON becomes a
 * [Proposal], which the UI shows as a card the user accepts or dismisses. Both surfaces must
 * read the same arguments the same way — see `specs/2026-06-07-ai-assistant-cards-and-drawers/`.
 */

/** Every change the assistant is allowed to propose. */
enum class ProposalKind(val wire: String) {
    // create a brand-new goal (from the global / All-Goals chat)
    NEW_GOAL("new_goal"),

    // create / goal-level
    TARGET("target"),
    TASK("task"),
    OPTION("option"),
    NOTE("note"),
    LINK("link"),
    EMAIL("email"),
    EDIT("edit"),
    OBSTACLE("obstacle"),
    ACTION("action"),
    CONFIDENCE("confidence"),
    DEADLINE("deadline"),

    // edit existing
    EDIT_TARGET("edit_target"),
    EDIT_OPTION("edit_option"),
    EDIT_OBSTACLE("edit_obstacle"),
    EDIT_ACTION("edit_action"),
    EDIT_NOTE("edit_note"),
    EDIT_LINK("edit_link"),
    EDIT_EMAIL("edit_email"),

    // state changes
    COMPLETE_TARGET("complete_target"),
    TARGET_PROGRESS("target_progress"),
    SELECT_OPTION("select_option"),
    CHECKLIST_ITEM("checklist_item"),
    ADD_CHECKLIST_ITEM("add_checklist_item"),

    // goal-level by id (All-Goals) + deletion
    EDIT_GOAL("edit_goal"),
    OPEN_GOAL("open_goal"),
    DELETE_GOAL("delete_goal"),
    DELETE_TARGET("delete_target"),
    DELETE_OPTION("delete_option"),
    DELETE_OBSTACLE("delete_obstacle"),
    DELETE_ACTION("delete_action"),
    DELETE_CHECKLIST_ITEM("delete_checklist_item"),

    /** Anything the model invents that this build doesn't know — shown, never applied. */
    UNKNOWN("unknown"),
    ;

    companion object {
        fun from(wire: String?): ProposalKind =
            entries.firstOrNull { it.wire == wire } ?: UNKNOWN
    }
}

enum class ProposalStatus { PENDING, APPROVED, REJECTED }

/** One checklist item inside a target-creation proposal. */
data class ProposalItem(val text: String, val done: Boolean = false, val deadline: String? = null)

/** The target shape a create proposal describes. */
enum class TargetShape { BINARY, NUMERIC, CHECKLIST }

data class Proposal(
    val id: String,
    val kind: ProposalKind,
    val title: String,
    val detail: String? = null,
    val reasoning: String? = null,
    val status: ProposalStatus = ProposalStatus.PENDING,
    /** For `edit`: "title" or "description". */
    val field: String? = null,
    /** For note/`new_goal` description — the long text shown behind "Read full content". */
    val body: String? = null,
    val deadline: String? = null,
    /** The raw tool argument value (a number for confidence/progress, an ISO date for deadline). */
    val rawValue: String? = null,
    /** The persisted `ai_proposals` row, when the backend stored one. */
    val serverId: Long? = null,
    /** The existing item this proposal edits or changes. */
    val itemId: String? = null,
    val done: Boolean? = null,
    val targetType: TargetShape? = null,
    val total: String? = null,
    val current: String? = null,
    val unit: String? = null,
    val items: List<ProposalItem>? = null,
    /** Resource fields to update (edit_link / edit_email). */
    val patch: Map<String, String>? = null,
    /** The goal a goal-level operation targets (edit_goal / open_goal / delete_goal). */
    val goalId: String? = null,
    /** open_goal: the concrete thing that can't be edited from the overview. */
    val openSubject: String? = null,
    /** new_goal: the initial confidence 1-10 the model extracted. */
    val confidence: Int? = null,
)

/** Plain-text snippet from (possibly) HTML note content, for one-line card previews. */
fun stripHtml(s: String): String =
    s.replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun clip(s: String, n: Int) = if (s.length > n) s.take(n - 1) + "…" else s

/**
 * Honour every DISTINCT creation the model proposes — the user may legitimately ask for several
 * at once ("create 3 goals: …"). Only EXACT duplicates (same kind + same title fired twice, which
 * some models emit by mistake) are dropped.
 */
fun dedupCreates(creates: List<Proposal>): List<Proposal> {
    val seen = mutableSetOf<String>()
    return creates.filter { seen.add("${it.kind.wire}|${it.title.trim().lowercase()}") }
}

/** An option can also be made the active one — its own checkbox on the card. */
fun isOptionActivate(p: Proposal): Boolean = p.kind == ProposalKind.OPTION && p.done == true

/** Upper bound on [proposalContext] — a revise prompt stays bounded even for a 40-item checklist. */
private const val PROPOSAL_CONTEXT_MAX_CHARS = 4000

/**
 * Every populated field of a proposal, as labelled lines — what the model must see to revise one
 * without dropping anything.
 *
 * The card's headline is a DISPLAY string: clipped, and blind to everything outside `title` — a
 * note's body, a checklist's items, the numeric measure, a deadline. Feeding that back as "your
 * proposal" is what made the assistant lose the user's earlier change on the next revise.
 */
fun proposalContext(p: Proposal): String {
    val lines = mutableListOf<String>()
    fun add(label: String, value: String?) {
        val v = value?.trim().orEmpty()
        if (v.isNotEmpty()) lines += "$label: $v"
    }

    add("kind", p.kind.wire + (p.field?.let { " ($it)" } ?: ""))
    // The whole value, never clipped — this is the text being revised.
    add(if (p.kind == ProposalKind.EDIT && p.field != null) "new ${p.field}" else "title", p.title)
    add("description", p.body)
    add("deadline", p.deadline)
    p.done?.let { add("done", it.toString()) }
    p.targetType?.let { add("target type", it.name.lowercase()) }
    if (!p.total.isNullOrBlank()) {
        add("measure", "${p.current ?: 0} / ${p.total}${p.unit?.let { " $it" } ?: ""}")
    }
    if (!p.items.isNullOrEmpty()) {
        lines += "checklist items:"
        p.items.forEach {
            lines += "  - ${it.text}" +
                (if (it.done) " (done)" else "") +
                (it.deadline?.let { d -> " (due $d)" } ?: "")
        }
    }
    p.patch?.forEach { (k, v) -> add("  $k", v) }

    val out = lines.joinToString("\n")
    return if (out.length > PROPOSAL_CONTEXT_MAX_CHARS) {
        out.take(PROPOSAL_CONTEXT_MAX_CHARS - 1) + "…"
    } else {
        out
    }
}

/**
 * The transcript as the model should see it: real user/assistant turns only (no error bubbles, no
 * in-flight placeholder, no empties), with **consecutive same-role turns merged**.
 *
 * The merge matters because revising a card writes the user's instruction into the transcript: if
 * that turn fails or is cancelled, the transcript holds two user messages in a row, and nothing
 * downstream normalises roles.
 */
fun buildHistory(messages: List<ChatMessage>): List<AiApi.HistoryEntry> {
    val out = mutableListOf<AiApi.HistoryEntry>()
    messages
        .filter {
            (it.role == ChatRole.USER || it.role == ChatRole.ASSISTANT) &&
                it.content.isNotBlank() &&
                !it.isError &&
                !it.streaming
        }
        .forEach { m ->
            val role = if (m.role == ChatRole.USER) "user" else "assistant"
            val last = out.lastOrNull()
            if (last != null && last.role == role) {
                out[out.size - 1] = last.copy(content = last.content + "\n\n" + m.content)
            } else {
                out += AiApi.HistoryEntry(role, m.content)
            }
        }
    return out
}

/** One optional, individually-toggleable field of a create proposal. */
data class CreateAspect(val id: String, val label: String, val body: String? = null)

/**
 * The extras the user can include or skip on a create card. The entity itself (its name) is always
 * the first checkbox; a bare goal (name only) returns an empty list and falls back to the one-tap
 * confirm card.
 */
fun createAspects(p: Proposal): List<CreateAspect> {
    val a = mutableListOf<CreateAspect>()
    when (p.kind) {
        ProposalKind.NEW_GOAL -> {
            p.confidence?.let { a += CreateAspect("confidence", "Confidence $it/10") }
            p.deadline?.let { a += CreateAspect("deadline", "Deadline · ${formatProposalDate(it)}") }
            // The description carries its own content so it can be read at the checkbox itself.
            if (!p.body.isNullOrBlank()) a += CreateAspect("description", "Description", p.body)
        }
        ProposalKind.TARGET, ProposalKind.TASK -> {
            p.deadline?.let { a += CreateAspect("deadline", "Deadline · ${formatProposalDate(it)}") }
            if (p.done == true) a += CreateAspect("done", "Already done")
        }
        else -> Unit
    }
    return a
}

/** A copy with any unchecked aspect stripped, so only the fields left ticked are saved. */
fun applyExcludedAspects(p: Proposal, excluded: Set<String>): Proposal {
    if (excluded.isEmpty()) return p
    return p.copy(
        confidence = if ("confidence" in excluded) null else p.confidence,
        deadline = if ("deadline" in excluded) null else p.deadline,
        body = if ("description" in excluded) null else p.body,
        done = if ("done" in excluded) false else p.done,
    )
}

/**
 * How a goal edit is shown on its card. The proposal carries the WHOLE new text in `title`; a
 * rewritten description runs to several paragraphs, so it shows a one-line preview and moves the
 * full text behind "Read full content" — the same treatment a note gets.
 */
data class EditDisplay(val headline: String, val detail: String?, val body: String?)

fun editDisplay(p: Proposal): EditDisplay =
    if (p.field == "description") {
        EditDisplay(
            headline = "New description",
            detail = clip(p.title.replace(Regex("\\s+"), " ").trim(), 120),
            body = p.title,
        )
    } else {
        EditDisplay(clip(p.title, 120), p.detail, null)
    }

/**
 * Structural, non-toggleable summary of a create proposal (the numeric measure or the checklist
 * count) — information that defines the target rather than an optional field. Goals and bare
 * binary targets have none.
 */
fun createSummary(p: Proposal): String? {
    if (p.kind != ProposalKind.TARGET && p.kind != ProposalKind.TASK) return null
    return when (p.targetType) {
        TargetShape.NUMERIC -> "${p.current ?: 0} / ${p.total ?: "?"}${p.unit?.let { " $it" } ?: ""}"
        TargetShape.CHECKLIST -> {
            val total = p.items?.size ?: 0
            val done = p.items?.count { it.done } ?: 0
            "Checklist · $done/$total done"
        }
        else -> null
    }
}

/** A human-readable date for a card label; falls back to the raw ISO string. */
fun formatProposalDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val date = if (iso.length > 10) {
            java.time.Instant.parse(iso).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        } else {
            java.time.LocalDate.parse(iso)
        }
        date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
    }.getOrDefault(iso)
}

/** Builds a [Proposal] from the `propose_goal_change` tool-call arguments JSON. */
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun proposalFromToolArgs(argsJson: String, id: String = randomProposalId()): Proposal? = runCatching {
    val data = JSONObject(argsJson)
    val kind = ProposalKind.from(data.optStringOrNull("kind") ?: "edit")
    val value = data.optStringOrNull("value").orEmpty()
    val name = data.optStringOrNull("title").orEmpty()
    val deadlineVal = data.optStringOrNull("deadline_value")
    val itemId = data.optStringOrNull("id")
    val done = when (data.optStringOrNull("done")) {
        "true" -> true
        "false" -> false
        else -> null
    }

    val total = data.optStringOrNull("total")
    val current = data.optStringOrNull("current")
    val unit = data.optStringOrNull("unit")
    val items = data.optJSONArray("items")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val text = o.optStringOrNull("text")?.trim() ?: return@mapNotNull null
            if (text.isEmpty()) null
            else ProposalItem(text, o.optBoolean("done", false), o.optStringOrNull("deadline"))
        }
    }?.takeIf { it.isNotEmpty() }

    val targetType = when {
        !items.isNullOrEmpty() -> TargetShape.CHECKLIST
        !total.isNullOrBlank() -> TargetShape.NUMERIC
        else -> TargetShape.BINARY
    }

    var title = value.ifEmpty { name }
    var detail: String? = null
    var body: String? = null
    var patch: Map<String, String>? = null

    when (kind) {
        ProposalKind.NEW_GOAL -> {
            // goal title in `title`; optional description in `value`
            title = name.ifEmpty { value }
            body = if (name.isNotEmpty() && value.isNotEmpty()) value else null
            // The "NEW GOAL" badge already names the kind.
            detail = body?.let { clip(it, 61) } ?: deadlineVal?.let { "Due $it" }
        }
        ProposalKind.EDIT -> {
            title = value
            detail = if (data.optStringOrNull("field") == "description") "New description" else "New title"
        }
        ProposalKind.CONFIDENCE -> {
            title = "Confidence → $value/10"
            detail = "Goal confidence"
        }
        ProposalKind.DEADLINE -> {
            title = value
            detail = "Goal deadline"
        }
        ProposalKind.TARGET, ProposalKind.TASK -> {
            title = name.ifEmpty { value }
            val noun = if (kind == ProposalKind.TASK) "task" else "target"
            detail = when (targetType) {
                TargetShape.CHECKLIST -> {
                    val n = items?.size ?: 0
                    val checked = items?.count { it.done } ?: 0
                    "New checklist · $checked/$n done"
                }
                TargetShape.NUMERIC ->
                    "New target · ${current ?: 0}/$total${unit?.let { " $it" } ?: ""}"
                TargetShape.BINARY -> when {
                    done == true -> "New $noun · done"
                    deadlineVal != null -> "New $noun · due $deadlineVal"
                    else -> "New $noun"
                }
            }
        }
        ProposalKind.OPTION -> {
            title = value.ifEmpty { name }
            detail = "Strategy option"
        }
        ProposalKind.OBSTACLE -> {
            title = value.ifEmpty { name }
            detail = "New obstacle"
        }
        ProposalKind.ACTION -> {
            title = value.ifEmpty { name }
            detail = "Current action"
        }
        ProposalKind.NOTE -> {
            title = name.ifEmpty { "Note" }
            body = value
            detail = clip(stripHtml(value), 61)
        }
        ProposalKind.LINK -> {
            patch = buildMap {
                put("url", value)
                if (name.isNotEmpty()) put("title", name)
            }
            title = name.ifEmpty { value.ifEmpty { "New link" } }
            detail = "New link"
        }
        ProposalKind.EMAIL -> {
            patch = buildMap {
                if (name.isNotEmpty()) put("name", name)
                if (value.isNotEmpty()) put("email", value)
                data.optStringOrNull("role")?.let { put("role", it) }
                data.optStringOrNull("phone")?.let { put("phone", it) }
            }
            title = name.ifEmpty { value.ifEmpty { "New contact" } }
            detail = "New contact"
        }
        ProposalKind.EDIT_TARGET -> {
            title = value.ifEmpty { name }
            detail = deadlineVal?.let { "Edit target · due $it" } ?: "Edit target"
        }
        ProposalKind.EDIT_OPTION -> {
            title = value.ifEmpty { name }
            detail = "Edit option"
        }
        ProposalKind.EDIT_OBSTACLE -> {
            title = value.ifEmpty { name }
            detail = "Edit obstacle"
        }
        ProposalKind.EDIT_ACTION -> {
            title = value.ifEmpty { name }
            detail = "Edit action"
        }
        ProposalKind.EDIT_NOTE -> {
            title = name.ifEmpty { "Note" }
            body = value
            detail = "Edit note"
        }
        ProposalKind.EDIT_LINK -> {
            patch = buildMap {
                if (name.isNotEmpty()) put("title", name)
                if (value.isNotEmpty()) put("url", value)
            }
            title = name.ifEmpty { value.ifEmpty { "Update link" } }
            detail = "Edit link"
        }
        ProposalKind.EDIT_EMAIL -> {
            patch = buildMap {
                if (name.isNotEmpty()) put("name", name)
                if (value.isNotEmpty()) put("email", value)
                data.optStringOrNull("role")?.let { put("role", it) }
                data.optStringOrNull("phone")?.let { put("phone", it) }
            }
            title = name.ifEmpty { value.ifEmpty { "Update contact" } }
            detail = "Edit contact"
        }
        ProposalKind.COMPLETE_TARGET -> {
            title = if (done == false) "Mark target not done" else "Mark target done"
            detail = "Target status"
        }
        ProposalKind.TARGET_PROGRESS -> {
            title = "Progress → $value"
            detail = "Target progress"
        }
        ProposalKind.SELECT_OPTION -> {
            title = "Select this option"
            detail = "Strategy option"
        }
        ProposalKind.CHECKLIST_ITEM -> {
            title = value.ifEmpty {
                when (done) {
                    true -> "Check item"
                    false -> "Uncheck item"
                    null -> "Update item"
                }
            }
            detail = deadlineVal?.let { "Checklist item · due $it" } ?: "Checklist item"
        }
        ProposalKind.ADD_CHECKLIST_ITEM -> {
            title = value.ifEmpty { name }
            detail = deadlineVal?.let { "New sub-task · due $it" } ?: "New sub-task"
        }
        ProposalKind.EDIT_GOAL -> {
            when (data.optStringOrNull("field")) {
                "confidence" -> { title = "Confidence → $value/10"; detail = "Edit goal" }
                "deadline" -> { title = value; detail = "Goal deadline" }
                else -> { title = value; detail = "Rename goal" }
            }
        }
        ProposalKind.OPEN_GOAL -> {
            title = "Open this goal"
            detail = "Open goal"
        }
        ProposalKind.DELETE_GOAL -> {
            title = "Delete this goal"
            detail = "Opens a confirmation"
        }
        ProposalKind.DELETE_TARGET -> {
            title = "Delete this target"
            detail = "Opens a confirmation"
        }
        ProposalKind.DELETE_OPTION,
        ProposalKind.DELETE_OBSTACLE,
        ProposalKind.DELETE_ACTION,
        ProposalKind.DELETE_CHECKLIST_ITEM,
        -> {
            // The real text is resolved from the item id when the card is displayed.
            title = "Delete this item"
            detail = "Remove"
        }
        ProposalKind.UNKNOWN -> Unit
    }

    val goalScoped = kind == ProposalKind.EDIT_GOAL ||
        kind == ProposalKind.OPEN_GOAL ||
        kind == ProposalKind.DELETE_GOAL

    Proposal(
        id = id,
        kind = kind,
        title = title,
        detail = detail,
        reasoning = data.optStringOrNull("reasoning"),
        field = data.optStringOrNull("field"),
        body = body,
        deadline = deadlineVal,
        rawValue = value.ifEmpty { null },
        itemId = itemId,
        done = done,
        targetType = targetType,
        total = total,
        current = current,
        unit = unit,
        items = items,
        patch = patch,
        goalId = if (goalScoped) itemId else null,
        openSubject = if (kind == ProposalKind.OPEN_GOAL) value.ifEmpty { null } else null,
        confidence = if (kind == ProposalKind.NEW_GOAL) {
            data.optStringOrNull("confidence")?.toIntOrNull()?.coerceIn(1, 10)
        } else {
            null
        },
        serverId = if (data.has("proposalId") && !data.isNull("proposalId")) {
            data.optLong("proposalId")
        } else {
            null
        },
    )
}.getOrNull()

internal fun randomProposalId(): String =
    java.util.UUID.randomUUID().toString().replace("-", "").take(7)
