package com.spiramindscape.android.ui.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spiramindscape.android.R
import com.spiramindscape.android.data.goals.ChecklistItemModel
import com.spiramindscape.android.data.goals.TargetItem
import com.spiramindscape.android.ui.components.AttachResourceButton
import com.spiramindscape.android.ui.components.ConfirmDialog
import com.spiramindscape.android.ui.components.DeadlinePickerDialog
import com.spiramindscape.android.ui.components.ElementActionsMenu
import com.spiramindscape.android.ui.components.InlineEditText
import com.spiramindscape.android.ui.components.InlineRichText
import com.spiramindscape.android.ui.components.LocalInlineResources
import com.spiramindscape.android.ui.components.attachTo
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Guava600
import com.spiramindscape.android.ui.theme.Kale200
import com.spiramindscape.android.ui.theme.Kale300
import com.spiramindscape.android.ui.theme.Salt400
import com.spiramindscape.android.ui.theme.Salt800
import com.spiramindscape.android.ui.theme.Salt1000
import com.spiramindscape.android.ui.theme.spiraExtras
import com.spiramindscape.android.ui.util.DeadlineInfo
import com.spiramindscape.android.ui.util.FieldLimits
import com.spiramindscape.android.ui.util.deadlineInfo
import com.spiramindscape.android.ui.util.formatDeadlineDate
import com.spiramindscape.android.ui.util.formatPercent
import com.spiramindscape.android.ui.util.isProgressLocked
import com.spiramindscape.android.ui.util.progressSteps
import com.spiramindscape.android.ui.util.readableText

/**
 * A target card — the Android twin of the web `TargetRow` (`src/components/spira/Targets.tsx`),
 * modelled on the reference card the owner supplied (2026-08-02):
 *
 *  - an **illustrated deadline tile** on the left (calendar / overdue calendar / party popper),
 *    with the date printed on the artwork's blank page and no frame of its own;
 *  - the inline-editable **title** beside it, with a caption line underneath (countdown, or
 *    "Completed · date", or "Created · date" when there is no deadline);
 *  - the **padlock** hanging off the top-right corner — pinned progress can't be nudged by a
 *    stray tap;
 *  - a hairline **progress strip** across the card;
 *  - a full-width **footer on Kale-200** that reveals the progress controls for this target's
 *    type, plus "Attach resource", Close and Delete target.
 *
 * An achieved card stays calm — no tint — and greys the links inside its title to Salt-800: the
 * work is done, so a link there is a reference, not a call to action.
 */
@Composable
fun TargetCard(target: TargetItem, actions: GoalWorkspaceActions, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    // What the numbers say while they are being typed — the card's own percentage follows the bar
    // inside it, so the two never disagree mid-edit. Null whenever nothing is being typed.
    var previewProgress by remember { mutableStateOf<Float?>(null) }

    val done = target.progress >= 1f
    val locked = isProgressLocked(target)
    val info = deadlineInfo(if (done && target.achievedAt != null) target.achievedAt else target.deadline, done)

    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.spiraExtras.surfaceRaised)
                .border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(16.dp)),
        ) {
            // ── Head: the deadline tile, then the title and its caption ──────────
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DeadlineTile(
                    info = info,
                    done = done,
                    modifier = Modifier.clickable(
                        onClickLabel = if (target.deadline != null) "Change the deadline" else "Set a deadline",
                    ) { showDatePicker = true },
                )
                Column(Modifier.weight(1f)) {
                    InlineRichText(
                        value = target.title,
                        onCommit = { actions.onSetTargetTitle(target.id, it) },
                        placeholder = "Target title",
                        required = true,
                        maxLength = FieldLimits.TARGET_TITLE,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        // An achieved target's links drop to Salt-800 — a reference, not an action.
                        linkColor = if (done) Salt800 else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        targetCaption(target, info, done),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            done -> MaterialTheme.colorScheme.primary
                            info?.isOverdue == true -> Guava600
                            else -> MaterialTheme.spiraExtras.mutedForeground
                        },
                    )
                }
            }

            // ── Progress strip ──────────────────────────────────────────────────
            val shown = previewProgress ?: target.progress
            val width by animateFloatAsState(shown.coerceIn(0f, 1f), label = "target-progress")
            Box(Modifier.fillMaxWidth().height(5.dp).background(Salt400)) {
                Box(
                    Modifier
                        .fillMaxWidth(width)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }

            // ── Footer: the label alone carries the state — no chevron ──────────
            // No clip of its own: the card's Column is already clipped to 16dp, so the footer's
            // bottom corners round with the card when it is the last thing in it, and stay square
            // when the panel sits below. (An asymmetric clip here would also make the footer
            // untappable under Robolectric, whose outline hit-test can't handle one.)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Kale200)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        done -> "100%"
                        expanded -> "${formatPercent(shown, progressSteps(target))}% progress"
                        else -> "Update progress"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    when (target) {
                        is TargetItem.Numeric -> NumericProgressBody(
                            target = target,
                            actions = actions,
                            locked = locked,
                            onPreviewProgress = { previewProgress = it },
                        )
                        is TargetItem.Binary -> BinaryProgressBody(target, actions, locked)
                        is TargetItem.Checklist -> ChecklistProgressBody(target, actions, locked)
                        is TargetItem.Other -> Text(
                            "This target type can't be edited here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.spiraExtras.mutedForeground,
                        )
                    }

                    if (locked) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            PROGRESS_LOCKED_MESSAGE,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.spiraExtras.mutedForeground,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    AttachResourceButton(
                        attachedTo = target.title,
                        onAttach = { resourceId ->
                            attachTo(target.title, resourceId, FieldLimits.TARGET_TITLE)
                                ?.let { actions.onSetTargetTitle(target.id, it) }
                        },
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // It collapses the panel; nothing is discarded — every edit here saves as
                        // it is made, so "Cancel" would promise an undo that never existed.
                        CardActionButton("Close", onClick = { expanded = false })
                        CardActionButton(
                            "Delete target",
                            onClick = { confirmDelete = true },
                            container = Salt1000,
                            contentColor = Color.White,
                        )
                    }
                }
            }
        }

        // The padlock deliberately hangs off the corner, the way the rating smiley does on an
        // option card — always present, never in the content flow.
        ProgressLockBadge(
            locked = locked,
            onToggle = { actions.onSetTargetProgressLocked(target.id, it) },
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp),
        )
    }

    if (showDatePicker) {
        DeadlinePickerDialog(
            value = target.deadline,
            onChange = { actions.onSetTargetDeadline(target.id, it) },
            onDismiss = { showDatePicker = false },
        )
    }
    if (confirmDelete) {
        // Quote the title as prose — an attached resource reads as its name, never as a raw tag.
        val resources = LocalInlineResources.current?.resources.orEmpty()
        val quoted = readableText(target.title, resources).ifBlank { "this target" }
        ConfirmDialog(
            title = "Delete this target?",
            message = "\"$quoted\" will be permanently deleted. Progress and checklist tasks " +
                "inside it will be removed. You can't undo this.",
            confirmLabel = "Yes, delete",
            cancelLabel = "No, go back",
            onConfirm = { actions.onDeleteTarget(target.id) },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Shown wherever a locked target's progress is edited — always names the way out. */
internal const val PROGRESS_LOCKED_MESSAGE =
    "This target is locked. Unlock it (the padlock) to change its progress."

/** The caption under a target's title: what its date means right now. */
private fun targetCaption(target: TargetItem, info: DeadlineInfo?, done: Boolean): String = when {
    done && info != null -> "Completed · ${info.dateStr}"
    info != null -> info.countdown
    target.createdAt != null -> "Created · ${formatDeadlineDate(target.createdAt!!)}"
    else -> "No deadline set"
}

/**
 * The deadline as a compact calendar tile — month above, the day in big digits — so a card reads
 * its date at a glance instead of parsing a line of prose. Same footprint in every state (a popper
 * once achieved, a calendar with a plus when no date is set), so the row never jumps.
 *
 * The date is printed ON the illustrated page: the artwork leaves its paper blank for exactly
 * that, which is why the text sits in a Box aligned to the bottom rather than in the flow.
 */
@Composable
fun DeadlineTile(info: DeadlineInfo?, done: Boolean, modifier: Modifier = Modifier) {
    val overdue = info?.isOverdue == true && !done
    val art = when {
        done -> R.drawable.tile_party_popper
        overdue -> R.drawable.tile_calendar_overdue
        info != null -> R.drawable.tile_calendar_date
        else -> R.drawable.tile_calendar_add
    }

    Box(modifier.size(64.dp), contentAlignment = Alignment.BottomCenter) {
        Image(
            painter = painterResource(art),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        if (!done && info != null) {
            // The date is centred on the PAPER, not on the tile — and the two calendars are drawn
            // at different tilts, so the overdue one needs its own nudge: its page sits slightly
            // to the left (the badge hangs off the right edge). The date stays black in both: the
            // red badge is what says "overdue", and red digits on a warm page only muddy it.
            Column(
                Modifier
                    .offset(x = if (overdue) (-3).dp else 0.dp, y = (-3).dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    info.monthLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = Salt1000,
                )
                Text(
                    info.dayLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 19.sp),
                    fontWeight = FontWeight.Bold,
                    color = Salt1000,
                )
            }
        }
    }
}

/**
 * The padlock on a target: pinned progress can't be nudged by a stray tap. An achieved target
 * starts locked; anything else starts open. Either way the toggle records an explicit choice, so
 * a finished target can be reopened to correct it.
 */
@Composable
fun ProgressLockBadge(locked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.spiraExtras.surfaceRaised)
            .border(1.dp, MaterialTheme.spiraExtras.border, CircleShape)
            .clickable { onToggle(!locked) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (locked) SpiraIcons.Lock else SpiraIcons.LockOpen,
            contentDescription = if (locked) "Unlock progress" else "Lock progress",
            tint = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.spiraExtras.mutedForeground,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** A binary target as a labelled toggle, not a bare checkbox. */
@Composable
private fun BinaryProgressBody(
    target: TargetItem.Binary,
    actions: GoalWorkspaceActions,
    locked: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (target.done) "Done" else "Mark done",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (target.done) FontWeight.Normal else FontWeight.Medium,
            color = if (target.done) {
                MaterialTheme.spiraExtras.mutedForeground
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = target.done,
            onCheckedChange = { if (!locked) actions.onSetTargetDone(target.id, it) },
            enabled = !locked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.spiraExtras.mutedForeground,
                uncheckedTrackColor = MaterialTheme.spiraExtras.surfaceSunken,
                uncheckedBorderColor = MaterialTheme.spiraExtras.border,
            ),
        )
    }
}

/**
 * The numeric editor: current / total / unit inline above a bar with ± controls, the percentage
 * printed beside it. Typing previews the bar as you go; the value still commits on blur/Done.
 */
@Composable
private fun NumericProgressBody(
    target: TargetItem.Numeric,
    actions: GoalWorkspaceActions,
    locked: Boolean,
    onPreviewProgress: (Float?) -> Unit,
) {
    var message by remember { mutableStateOf<String?>(null) }
    val start = target.start ?: 0.0
    val total = target.total
    val lo = if (total != null) minOf(start, total) else start
    val hi = if (total != null) maxOf(start, total) else Double.MAX_VALUE

    fun progressFor(current: Double, totalValue: Double?, startValue: Double): Float {
        val end = totalValue ?: return 0f
        val distance = kotlin.math.abs(end - startValue)
        if (distance == 0.0) return if (current == end) 1f else 0f
        val completed = if (end >= startValue) current - startValue else startValue - current
        return (completed / distance).toFloat().coerceIn(0f, 1f)
    }

    fun validate(current: Double, totalValue: Double?, startValue: Double): String? {
        if (startValue < 0 || current < 0 || (totalValue != null && totalValue < 0)) {
            return "Numbers cannot be negative."
        }
        if (totalValue != null && startValue == totalValue) return "Start and target must be different."
        if (totalValue != null) {
            val min = minOf(startValue, totalValue)
            val max = maxOf(startValue, totalValue)
            if (current < min || current > max) return "Current must stay between ${trimNumber(min)} and ${trimNumber(max)}."
        }
        return null
    }

    fun commit(current: Double = target.current, totalValue: Double? = total, startValue: Double = start) {
        onPreviewProgress(null)
        if (locked) {
            message = PROGRESS_LOCKED_MESSAGE
            return
        }
        val error = validate(current, totalValue, startValue)
        if (error != null) {
            message = error
            return
        }
        message = null
        actions.onSetTargetNumbers(
            target.id,
            if (current != target.current) current else null,
            if (totalValue != total) totalValue else null,
            if (startValue != start) startValue else null,
        )
    }

    /** Preview only a plainly valid number; anything else leaves the bar where it was. */
    fun preview(raw: String, apply: (Double) -> Triple<Double, Double?, Double>) {
        val parsed = raw.trim().toDoubleOrNull()
        if (locked || parsed == null) {
            onPreviewProgress(null)
            return
        }
        val (c, t, s) = apply(parsed)
        onPreviewProgress(if (validate(c, t, s) != null) null else progressFor(c, t, s))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberField(
                value = trimNumber(target.current),
                label = "Current value",
                onTyping = { raw -> preview(raw) { Triple(it, total, start) } },
                onCommit = { entered -> entered.toDoubleOrNull()?.let { commit(current = it) } },
            )
            Text("/", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            NumberField(
                value = total?.let { trimNumber(it) } ?: "",
                label = "Total value",
                placeholder = "total",
                onTyping = { raw -> preview(raw) { Triple(target.current, it, start) } },
                onCommit = { entered -> entered.toDoubleOrNull()?.let { commit(totalValue = it) } },
            )
            InlineEditText(
                value = target.unit ?: "",
                onCommit = { actions.onSetTargetUnit(target.id, it.ifBlank { null }) },
                modifier = Modifier.width(56.dp),
                placeholder = "unit",
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "(from",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.spiraExtras.mutedForeground,
            )
            NumberField(
                value = trimNumber(start),
                label = "Start value",
                onTyping = { raw -> preview(raw) { Triple(target.current, total, it) } },
                onCommit = { entered -> entered.toDoubleOrNull()?.let { commit(startValue = it) } },
                muted = true,
            )
            Text(
                ")",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.spiraExtras.mutedForeground,
            )
        }

        if (message != null) {
            Text(
                message!!,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StepButton(SpiraIcons.Minus, "Decrement", enabled = !locked && target.current > lo) {
                commit(current = (target.current - 1).coerceIn(lo, hi))
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.spiraExtras.surfaceSunken),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(target.progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Text(
                "${formatPercent(target.progress, progressSteps(target))}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StepButton(
                SpiraIcons.Plus,
                "Increment",
                enabled = !locked && (total == null || target.current < hi),
            ) { commit(current = (target.current + 1).coerceIn(lo, hi)) }
        }
    }
}

/** A checklist target: the tasks in the "Steps" shape, plus an add-task row. */
@Composable
private fun ChecklistProgressBody(
    target: TargetItem.Checklist,
    actions: GoalWorkspaceActions,
    locked: Boolean,
) {
    var lastItemError by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        target.items.forEach { item ->
            TaskRow(
                item = item,
                locked = locked,
                onToggle = { if (!locked) actions.onToggleChecklistItem(target.id, item.id) },
                onCommitText = { actions.onUpdateChecklistTask(target.id, item.id, it) },
                onSetDeadline = { actions.onSetChecklistTaskDeadline(target.id, item.id, it) },
                onDelete = {
                    if (target.items.size <= 1) {
                        lastItemError = true
                    } else {
                        lastItemError = false
                        actions.onRemoveChecklistTask(target.id, item.id)
                    }
                },
            )
        }
        if (lastItemError && target.items.size <= 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(
                    SpiraIcons.TriangleAlert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "A checklist must have at least one item",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        AddTaskControl(enabled = !locked) { actions.onAddChecklistTask(target.id, it) }
    }
}

/**
 * One checklist task, in the "Steps" shape the owner asked for: no card, no border — a round check
 * on the left, the text beside it, and the row's controls (deadline, ⋮) always visible on the
 * right. A done task greys and strikes through; a resource link inside it never does.
 */
@Composable
private fun TaskRow(
    item: ChecklistItemModel,
    locked: Boolean,
    onToggle: () -> Unit,
    onCommitText: (String) -> Unit,
    onSetDeadline: (String?) -> Unit,
    onDelete: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val info = deadlineInfo(item.deadline, item.done)
    val overdue = info?.isOverdue == true

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TaskCheck(done = item.done, onClick = onToggle)
        InlineRichText(
            value = item.text,
            onCommit = onCommitText,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium,
            maxLength = FieldLimits.CHECKLIST_TEXT,
            required = true,
            strikeThrough = item.done,
            color = if (item.done) {
                MaterialTheme.spiraExtras.mutedForeground
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        // Deadline and ⋮ are always visible on a task row: with a fixed control column on the
        // right there is nothing for them to overlap, and a task is worked on far more often
        // than an option or a reality item.
        Icon(
            if (item.deadline != null) SpiraIcons.Calendar else SpiraIcons.CalendarPlus,
            contentDescription = if (item.deadline != null) "Change the deadline" else "Set a deadline",
            tint = when {
                item.deadline == null -> MaterialTheme.spiraExtras.mutedForeground
                overdue -> Guava600
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(18.dp).clickable { showDatePicker = true },
        )
        ElementActionsMenu(
            contentDescription = "Subtask actions",
            attachedTo = item.text,
            vertical = true,
            onAttach = { resourceId ->
                attachTo(item.text, resourceId, FieldLimits.CHECKLIST_TEXT)?.let(onCommitText)
            },
            onDelete = if (locked) null else onDelete,
            deleteLabel = "Delete task",
        )
    }

    if (showDatePicker) {
        DeadlinePickerDialog(
            value = item.deadline,
            onChange = onSetDeadline,
            onDismiss = { showDatePicker = false },
        )
    }
}

/** The round task check: a filled teal circle with a white tick when done, an outline when not. */
@Composable
private fun TaskCheck(done: Boolean, onClick: () -> Unit) {
    val label = if (done) "Mark subtask not done" else "Mark subtask done"
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .then(
                if (done) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier.border(2.dp, MaterialTheme.spiraExtras.borderStrong, CircleShape)
                },
            )
            .clickable(onClick = onClick)
            // The unticked state draws no glyph, so the row carries the label itself — otherwise
            // there would be nothing to announce (or to find in a test) until a task is done.
            .semantics {
                contentDescription = label
                role = Role.Checkbox
            },
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(SpiraIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

/**
 * Adding a task: a circled + and a link, which swaps itself for an input on tap. Done adds the
 * task; losing focus — with text or without — collapses back to the link, so an abandoned field
 * never sits open and empty.
 */
@Composable
private fun AddTaskControl(enabled: Boolean, onAdd: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var everFocused by remember { mutableStateOf(false) }

    if (!open) {
        Row(
            Modifier
                .clickable(enabled = enabled) { open = true; everFocused = false }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                SpiraIcons.CirclePlus,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.spiraExtras.mutedForeground,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Add task",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.spiraExtras.mutedForeground,
            )
        }
        return
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            SpiraIcons.CirclePlus,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        InlineEditText(
            // Always starts empty; the row is torn down once it closes, so nothing is carried over.
            value = "",
            onCommit = { text ->
                val trimmed = text.trim().take(FieldLimits.CHECKLIST_TEXT)
                if (trimmed.isNotEmpty()) onAdd(trimmed)
            },
            modifier = Modifier.weight(1f),
            placeholder = "Add task…",
            textStyle = MaterialTheme.typography.bodyMedium,
            autoFocus = true,
            onFocusChanged = { focused ->
                if (focused) everFocused = true else if (everFocused) open = false
            },
        )
    }
}

/** A single inline number, right-sized so the current/total row stays on one line. */
@Composable
private fun NumberField(
    value: String,
    label: String,
    onTyping: (String) -> Unit,
    onCommit: (String) -> Unit,
    placeholder: String = "",
    muted: Boolean = false,
) {
    InlineEditText(
        value = value,
        onCommit = onCommit,
        modifier = Modifier
            .width(if (muted) 48.dp else 64.dp)
            .semantics { contentDescription = label },
        textStyle = if (muted) {
            MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.spiraExtras.mutedForeground)
        } else {
            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
        },
        placeholder = placeholder,
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
        required = true,
        textAlign = TextAlign.Center,
        onTextChanged = onTyping,
    )
}

/** A square ± button beside the numeric progress bar. */
@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                2.dp,
                if (enabled) MaterialTheme.spiraExtras.border else Salt400,
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.spiraExtras.mutedForeground,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** The Close / Delete pair at the foot of an expanded card. */
@Composable
private fun CardActionButton(
    label: String,
    onClick: () -> Unit,
    container: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (container != null) {
                    Modifier.background(container)
                } else {
                    Modifier.border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(8.dp))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "3" not "3.0" — a whole number never shows a pointless decimal. */
internal fun trimNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
