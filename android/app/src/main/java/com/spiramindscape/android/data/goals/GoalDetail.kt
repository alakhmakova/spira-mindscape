package com.spiramindscape.android.data.goals

/** The full goal for the workspace screen (the five sections). */
data class GoalDetail(
    val id: String,
    val title: String,
    val description: String,
    val confidence: Int,
    val deadline: String?,
    val progress: Float,
    val achieved: Boolean,
    val actions: List<TextItem>,
    val obstacles: List<TextItem>,
    val options: List<OptionItem>,
    val targets: List<TargetItem>,
    val resources: List<ResourceItem>,
    val confidenceHistory: List<ConfidenceHistoryEntry> = emptyList(),
)

/** One past confidence value, newest first (matches the server's ordering). */
data class ConfidenceHistoryEntry(val id: String, val confidence: Int, val at: String)

data class TextItem(val id: String, val text: String)
data class OptionItem(val id: String, val text: String, val selected: Boolean, val position: Int = 0)
/** One checklist task. [deadline]/[achievedAt] are ISO instants, both optional (web parity). */
data class ChecklistItemModel(
    val id: String,
    val text: String,
    val done: Boolean,
    val deadline: String? = null,
    val achievedAt: String? = null,
)

/**
 * A resource, flat across its four kinds (`note` / `link` / `file` / `email`). The UI switches on
 * [type]; each kind uses a subset of the fields (note→[body], link→[url], email→name/email/…).
 */
data class ResourceItem(
    val id: String,
    val type: String,
    val title: String?,
    val body: String? = null,
    val url: String? = null,
    val mime: String? = null,
    val dataUrl: String? = null,
    val driveWebViewLink: String? = null,
    val name: String? = null,
    val email: String? = null,
    val role: String? = null,
    val phone: String? = null,
)

/**
 * A target, modelled by kind so the UI can render the right low-friction control
 * (checkbox / stepper / checklist). Product language: "Done / Not done", "Numeric", "Checklist".
 */
sealed interface TargetItem {
    val id: String
    val title: String
    val progress: Float
    val deadline: String?
    val achieved: Boolean

    /**
     * The user's explicit progress lock, or null when they never chose. Null means "follow the
     * default": an achieved target locks itself so a stray tap can't undo it (see
     * `com.spiramindscape.android.ui.util.isProgressLocked`).
     */
    val progressLocked: Boolean?

    /** When the target was achieved — the date an achieved card prints on its tile. */
    val achievedAt: String?

    /** Fallback caption for a target with no deadline: "Created · <date>". */
    val createdAt: String?

    data class Binary(
        override val id: String,
        override val title: String,
        override val progress: Float,
        override val deadline: String?,
        override val achieved: Boolean,
        val done: Boolean,
        override val progressLocked: Boolean? = null,
        override val achievedAt: String? = null,
        override val createdAt: String? = null,
    ) : TargetItem

    data class Numeric(
        override val id: String,
        override val title: String,
        override val progress: Float,
        override val deadline: String?,
        override val achieved: Boolean,
        val current: Double,
        val total: Double?,
        val start: Double?,
        val unit: String?,
        override val progressLocked: Boolean? = null,
        override val achievedAt: String? = null,
        override val createdAt: String? = null,
    ) : TargetItem

    data class Checklist(
        override val id: String,
        override val title: String,
        override val progress: Float,
        override val deadline: String?,
        override val achieved: Boolean,
        val items: List<ChecklistItemModel>,
        override val progressLocked: Boolean? = null,
        override val achievedAt: String? = null,
        override val createdAt: String? = null,
    ) : TargetItem

    /** Unknown/other type — shown read-only. */
    data class Other(
        override val id: String,
        override val title: String,
        override val progress: Float,
        override val deadline: String?,
        override val achieved: Boolean,
        override val progressLocked: Boolean? = null,
        override val achievedAt: String? = null,
        override val createdAt: String? = null,
    ) : TargetItem
}
