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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.spiramindscape.android.ui.icons.SpiraArt
import com.spiramindscape.android.ui.components.AddActionIconGap
import com.spiramindscape.android.ui.components.addActionTextStyle
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Error800
import com.spiramindscape.android.ui.theme.Guava500
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
                            info?.isOverdue == true -> Error800
                            else -> MaterialTheme.spiraExtras.mutedForeground
                        },
                    )
                }
            }

            // ── Progress strip ──────────────────────────────────────────────────
            val shown = previewProgress ?: target.progress
            val width by animateFloatAsState(shown.coerceIn(0f, 1f), label = "target-progress")
            // The part still to go is Kale-300, not a grey: the strip then reads as one teal
            // measure filling up rather than as a coloured bar sitting on dead space.
            Box(Modifier.fillMaxWidth().height(5.dp).background(Kale300)) {
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

                    Spacer(Modifier.height(TARGET_ACTION_GAP))
                    AttachResourceButton(
                        attachedTo = target.title,
                        onAttach = { resourceId ->
                            attachTo(target.title, resourceId, FieldLimits.TARGET_TITLE)
                                ?.let { actions.onSetTargetTitle(target.id, it) }
                        },
                    )

                    // Delete is the **twin of "Attach resource"** above it — a circled mark and the
                    // same 14sp label, in red rather than teal (`Targets.tsx` does the same). As a
                    // filled near-black button it was the heaviest thing on an opened card, which
                    // is the wrong weight for the one action you can't undo.
                    Spacer(Modifier.height(TARGET_ACTION_GAP))
                    Row(
                        Modifier
                            .clickable { confirmDelete = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AddActionIconGap),
                    ) {
                        Icon(
                            SpiraIcons.CircleXmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "Delete target",
                            style = addActionTextStyle(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // It collapses the panel; nothing is discarded — every edit here saves as
                        // it is made, so "Cancel" would promise an undo that never existed.
                        CardActionButton("Close", onClick = { expanded = false })
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
            subject = "\"$quoted\"",
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

    // An achieved target keeps the popper — it isn't a date any more, it's a result.
    if (done) {
        Box(modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.tile_party_popper),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    // One calendar for every date state (the owner's artwork, 2026-08-07), always with its Guava
    // header band — overdue is said by the badge below, and recolouring the band as well made the
    // whole tile shout.
    val calendar = remember { SpiraArt.calendarPage() }
    val plusMark = remember { SpiraArt.plusMark() }

    Box(modifier.size(TILE), contentAlignment = Alignment.Center) {
        Image(
            imageVector = calendar,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )

        // What is printed sits on the PAPER, not on the tile. In the artwork's 50-unit box the
        // page runs y 11 -> 45 and the header band takes y 11 -> 18.6, so the writable paper is
        // y 18.6 -> 45 — its middle is at 0.636 of the height, well below the tile's own centre.
        Column(
            Modifier.fillMaxWidth().offset(y = PAPER_CENTRE_OFFSET),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (info == null) {
                // No date yet: the page shows a plus, so the tile still invites a tap. It is the
                // hand-drawn one, so it belongs to the calendar rather than sitting on top of it.
                Image(
                    imageVector = plusMark,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    info.monthLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 9.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = Salt1000,
                )
                Text(
                    info.dayLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 17.sp),
                    fontWeight = FontWeight.Bold,
                    color = Salt1000,
                )
            }
        }

        // Overdue: an alert badge on the tile's bottom-right corner, clear of the paper so the
        // date keeps its place. Down there it is well away from the red header band — a red mark
        // on a red band would disappear — and it carries a white ring to separate it cleanly.
        if (overdue) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 2.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SpiraIcons.CircleExclamationFilled,
                    contentDescription = "Overdue",
                    tint = Error800,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/** The tile's footprint on a card. */
private val TILE = 64.dp

/**
 * How far below the tile's centre what the calendar prints is centred — the date and the plus
 * alike, so the two states never sit at different heights.
 *
 * It is **the paper's own middle**, nothing else. In the artwork's 50-unit box the page runs
 * y 11 → 45 and the coral band takes y 11 → 18.6, so the writable paper is 18.6 → 45 and its
 * centre is at 0.636 of the height. The block used to hang a little lower than that (0.68) on the
 * theory that it read better hung from the band; beside the web tile it simply looked low, and the
 * plus — which has no ascenders to lift it — looked lower still. The web uses the same figure
 * (`Targets.tsx` → `PAPER_CENTRE`), so the two tiles print at the same place.
 */
private const val PAPER_CENTRE = 0.636f
private val PAPER_CENTRE_OFFSET = (TILE.value * (PAPER_CENTRE - 0.5f)).dp

/**
 * The padlock on a target: pinned progress can't be nudged by a stray tap. An achieved target
 * starts locked; anything else starts open. Either way the toggle records an explicit choice, so
 * a finished target can be reopened to correct it.
 */
@Composable
fun ProgressLockBadge(locked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    // **The option card's smiley badge, exactly** (`OptionCard` in `GoalWorkspaceScreen.kt`, and
    // `OptionsList.tsx` on the web): 28dp, a 2dp shadow, a white plate, a hairline ring and a 16dp
    // glyph. The two hang off the same corner of two cards in the same list, so any difference
    // between them reads as two different components rather than one convention.
    Box(
        modifier
            .size(28.dp)
            .shadow(2.dp, CircleShape)
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
            modifier = Modifier.size(16.dp),
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
            // A locked switch keeps its colours. Material greys a disabled control to say "this
            // does nothing", but here the lock already says that, and fading a done target's teal
            // made a finished target look unfinished — the state is the thing worth reading.
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.spiraExtras.mutedForeground,
                uncheckedTrackColor = MaterialTheme.spiraExtras.surfaceSunken,
                uncheckedBorderColor = MaterialTheme.spiraExtras.border,
                disabledCheckedThumbColor = Color.White,
                disabledCheckedTrackColor = MaterialTheme.colorScheme.primary,
                disabledCheckedBorderColor = MaterialTheme.colorScheme.primary,
                disabledUncheckedThumbColor = MaterialTheme.spiraExtras.mutedForeground,
                disabledUncheckedTrackColor = MaterialTheme.spiraExtras.surfaceSunken,
                disabledUncheckedBorderColor = MaterialTheme.spiraExtras.border,
            ),
        )
    }
}

/**
 * The numeric editor: current / total / unit inline above a bar with ± controls, the percentage
 * printed beside it. Typing previews the bar as you go; the value still commits on blur/Done.
 */
@OptIn(ExperimentalLayoutApi::class)
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
        if (message != null) {
            Text(
                message!!,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // The ± pair with **the numbers between them**, matching `Targets.tsx`. The inner bar and
        // its percentage used to sit here and the values sat on a line above; the card's own strip
        // already prints that measure, so the row said it twice while exiling the thing the
        // buttons actually change.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StepButton(SpiraIcons.Minus, "Decrement", enabled = !locked && target.current > lo) {
                commit(current = (target.current - 1).coerceIn(lo, hi))
            }
            // **A FlowRow, not a Row.** All seven pieces — current, "/", total, unit, "(from",
            // start, ")" — do not fit across a phone, and a plain Row does not wrap: it squeezes
            // the last children to nothing, which is how "65 / 54 kg" ended up as a lone "65" with
            // "(from" set one letter per line down the right edge. Here the "(from …)" group drops
            // to a second line instead, which is what the web does when its row runs out of room.
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(2.dp),
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
                val unitStyle = MaterialTheme.typography.bodyMedium
                InlineEditText(
                    value = target.unit ?: "",
                    onCommit = { actions.onSetTargetUnit(target.id, it.ifBlank { null }) },
                    modifier = Modifier.width(textWidth(target.unit?.ifBlank { null } ?: "unit", unitStyle) + 8.dp),
                    placeholder = "unit",
                    textStyle = unitStyle,
                )
                // The "(from …)" group is **one** flow item, in a Row of its own. Left as three
                // siblings, the wrap could fall between "(from" and its number, or leave the
                // closing bracket alone on the next line — which is exactly what it did.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "(from ",
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
            }
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
        // **On the first line, not centred against the whole task** (owner, 2026-08-18). A task
        // that wraps to two or three lines used to float its check halfway down the block, which
        // read as a mark for the paragraph rather than for the line the task starts on.
        TaskCheck(done = item.done, onClick = onToggle, modifier = Modifier.align(Alignment.Top))
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
        //
        // Both sit on the task's **first line**, like the check on the other side (owner,
        // 2026-08-18) — a row whose text wraps to three lines had its controls floating at the
        // middle of the block, level with nothing.
        Icon(
            if (item.deadline != null) SpiraIcons.Calendar else SpiraIcons.Calendar,
            contentDescription = if (item.deadline != null) "Change the deadline" else "Set a deadline",
            tint = when {
                item.deadline == null -> MaterialTheme.spiraExtras.mutedForeground
                overdue -> Error800
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier
                .align(Alignment.Top)
                .size(18.dp)
                .clickable { showDatePicker = true },
        )
        ElementActionsMenu(
            contentDescription = "Subtask actions",
            attachedTo = item.text,
            vertical = true,
            modifier = Modifier.align(Alignment.Top),
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

/**
 * The air between the opened card's three action rows — Add task, Attach resource, Delete target.
 * **One value, so the three are evenly spaced** (owner, 2026-08-18): Attach used to sit 14dp below
 * the body and Delete 4dp below Attach, which grouped Delete with Attach and left Add task adrift.
 */
private val TARGET_ACTION_GAP = 8.dp

/**
 * The round task check: Gravity's **`circle-dashed`** while the task is still to do, and
 * `circle-check-fill` in **Guava** once it is done (owner, 2026-08-18). The dashed ring says
 * "not yet" in a way a plain outline circle never did — an unfilled `circle-check` still shows a
 * tick, so a task that had not been touched already looked half-agreed-to.
 *
 * This is the one case a solid mark may sit beside an outline one (CLAUDE.md): they are one
 * control's two states, not a column of unrelated icons. The filled glyph knocks the tick OUT of
 * the disc, so tinting it Guava prints a Guava disc with a white tick showing through the card
 * behind it — no second colour to keep in step.
 *
 * [modifier] is how the caller puts it **on the first line** of a task that wraps; see [TaskRow].
 */
@Composable
private fun TaskCheck(done: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (done) "Mark subtask not done" else "Mark subtask done"
    Icon(
        if (done) SpiraIcons.CircleCheckFill else SpiraIcons.CircleDashed,
        contentDescription = null,
        tint = if (done) Guava500 else MaterialTheme.spiraExtras.borderStrong,
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            // The glyph carries no text, so the row carries the label itself — otherwise there
            // would be nothing to announce (or to find in a test).
            .semantics {
                contentDescription = label
                role = Role.Checkbox
            },
    )
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
                // Trimmed leading so the word centres on the plus rather than riding above it.
                style = addActionTextStyle(),
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.spiraExtras.mutedForeground,
            )
        }
        return
    }

    var draft by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Open, the row wears the **dashed ring** the tasks above it wear (owner, 2026-08-18):
        // what is being typed is a task-to-be, so it lines up with the list it is joining rather
        // than repeating the + that opened it.
        Icon(
            SpiraIcons.CircleDashed,
            contentDescription = null,
            tint = MaterialTheme.spiraExtras.borderStrong,
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
            onTextChanged = { draft = it },
            onFocusChanged = { focused ->
                if (focused) everFocused = true else if (everFocused) open = false
            },
        )
        // **A circled plus, not the return arrow** (owner, 2026-08-18) — it is the same mark as the
        // "Add task" link that opened the row, so the thing you press to finish is the thing you
        // pressed to start. (The arrow named the keyboard's key rather than the action, and now
        // that the row leads with a dashed ring there is no longer a + at both ends to confuse.)
        if (draft.isNotBlank()) {
            Icon(
                SpiraIcons.CirclePlus,
                contentDescription = "Add this task",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * How wide a string is in a given style, so a `BasicTextField` can be sized to its own content.
 *
 * Compose gives a text field no intrinsic width — it fills whatever it is offered — so the only
 * honest answer is to measure the glyphs. Used by the numeric row, where seven small pieces have
 * to sit together as one sentence rather than drift apart across the card.
 */
@Composable
private fun textWidth(text: String, style: androidx.compose.ui.text.TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, style, density) {
        with(density) { measurer.measure(text.ifEmpty { "0" }, style).size.width.toDp() }
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
    val style = if (muted) {
        MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.spiraExtras.mutedForeground)
    } else {
        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
    }
    InlineEditText(
        value = value,
        onCommit = onCommit,
        // **Measured to its own text.** A `BasicTextField` takes every pixel it is offered, so
        // these fields have to be told how wide they are or they each swallow a whole line: at a
        // flat 64dp a two-digit value floated in the middle of an empty box and "65 / 54 kg" read
        // as four things scattered across the row; with only a minimum they wrapped one per line.
        // Measuring the string is the one way to get "65" to occupy exactly as much room as "65".
        //
        // The muted (start) field is measured **tight**: the 8dp of caret room is what printed
        // "(from 0 )" with a gap before the bracket. The bracket has to sit against its number,
        // so that one gets 2dp — enough for a caret, not enough to read as a space.
        modifier = Modifier
            .width(textWidth(value.ifBlank { placeholder }, style) + if (muted) 0.dp else 8.dp)
            .semantics { contentDescription = label },
        textStyle = style,
        placeholder = placeholder,
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
        required = true,
        onTextChanged = onTyping,
    )
}

/** The ± button's side, and the box the gradient ring is measured against. */
private val STEP_BUTTON = 36.dp

/**
 * The gradient ring on a ± button, translated from the web's `.gradient-ring` (`styles.css`):
 * `linear-gradient(228.47deg, #ff4833 48.94%, #ed7ffe 89.8%, #7f8eff 143.09%)`.
 *
 * CSS measures a gradient's angle clockwise from "up" and its line from the box's centre, so the
 * direction is `(sin θ, -cos θ)` with y growing downward — down and to the LEFT here — and the
 * line is `w·|sin θ| + h·|cos θ|` long. Compose instead wants two pixel offsets, and it has no way
 * to express a stop past 100%: the line is extended to 143.09% and the three stops re-scaled onto
 * it (48.94/143.09, 89.8/143.09, 1) so the sweep lands where the browser puts it.
 */
@Composable
private fun stepButtonRingBrush(): Brush {
    val s = with(LocalDensity.current) { STEP_BUTTON.toPx() }
    return Brush.linearGradient(
        0.342f to Color(0xFFFF4833),
        0.628f to Color(0xFFED7FFE),
        1f to Color(0xFF7F8EFF),
        start = Offset(1.0284f * s, 0.0319f * s),
        end = Offset(-0.4837f * s, 1.3717f * s),
    )
}

/**
 * A ± button beside the numeric row — the **web's exact button** (`Targets.tsx`): a **36dp square,
 * `rounded-md` (6dp), a near-black sign**, and the owner's **1dp gradient ring** in place of the
 * old flat 2dp grey. The pair stays split, one either side of the values they act on.
 */
@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val ring = stepButtonRingBrush()
    Box(
        Modifier
            .size(STEP_BUTTON)
            .clip(shape)
            // A disabled button keeps the flat grey: a ring that colourful on a dead control
            // reads as "press me".
            .border(1.dp, if (enabled) ring else SolidColor(Salt400), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.spiraExtras.mutedForeground,
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
