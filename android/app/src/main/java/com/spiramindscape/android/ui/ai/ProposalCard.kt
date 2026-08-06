package com.spiramindscape.android.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spiramindscape.android.data.ai.Proposal
import com.spiramindscape.android.data.ai.ProposalKind
import com.spiramindscape.android.data.ai.ProposalStatus
import com.spiramindscape.android.data.ai.createAspects
import com.spiramindscape.android.data.ai.createSummary
import com.spiramindscape.android.data.ai.editDisplay
import com.spiramindscape.android.data.ai.stripHtml
import com.spiramindscape.android.ui.components.InlineEditText
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Guava100
import com.spiramindscape.android.ui.theme.Guava600
import com.spiramindscape.android.ui.theme.Kale100
import com.spiramindscape.android.ui.theme.Kale500
import com.spiramindscape.android.ui.theme.Salt400
import com.spiramindscape.android.ui.theme.spiraExtras

/**
 * A proposal card — the Android twin of the web `ProposalCard` family
 * (`specs/2026-06-07-ai-assistant-cards-and-drawers/`).
 *
 * The assistant never changes anything on its own: it proposes, and the card is where the user
 * says yes or no. A create proposal with optional extras (a deadline, a confidence, a description)
 * shows them as individually-tickable **aspects**, so the user can keep the goal but skip the date.
 * "Edit" hands the whole proposal back to the model with an instruction, so a second change can't
 * lose the first.
 */
@Composable
fun ProposalCard(
    proposal: Proposal,
    onAccept: (Proposal, Set<String>) -> Unit,
    onDismiss: (Proposal) -> Unit,
    onRevise: (Proposal, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var excluded by remember(proposal.id) { mutableStateOf(emptySet<String>()) }
    var editing by remember(proposal.id) { mutableStateOf(false) }
    var showBody by remember(proposal.id) { mutableStateOf(false) }

    val settled = proposal.status != ProposalStatus.PENDING
    val aspects = remember(proposal) { createAspects(proposal) }
    val summary = remember(proposal) { createSummary(proposal) }
    val display = remember(proposal) {
        if (proposal.kind == ProposalKind.EDIT) editDisplay(proposal) else null
    }
    val headline = display?.headline ?: proposal.title
    val detail = display?.detail ?: proposal.detail
    val body = display?.body ?: proposal.body

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.spiraExtras.surfaceRaised)
            .border(1.dp, if (settled) Salt400 else MaterialTheme.spiraExtras.border, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        KindBadge(proposal.kind, settled)
        Spacer(Modifier.height(10.dp))

        Text(
            headline.ifBlank { "Proposed change" },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (settled) MaterialTheme.spiraExtras.mutedForeground else MaterialTheme.colorScheme.onSurface,
        )
        // The detail line restates the badge for a create ("NEW TARGET" / "New checklist · 0/3"),
        // and the summary below says the same again — so it only earns its place when there is no
        // structural summary to carry it.
        if (!detail.isNullOrBlank() && summary == null) {
            Spacer(Modifier.height(3.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.spiraExtras.mutedForeground,
            )
        }
        summary?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // A checklist target names its steps up front — the count alone doesn't say what they are.
        proposal.items?.takeIf { it.isNotEmpty() }?.let { items ->
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items.take(6).forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (item.done) SpiraIcons.CircleCheck else SpiraIcons.CirclePlus,
                            contentDescription = null,
                            tint = MaterialTheme.spiraExtras.mutedForeground,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.size(7.dp))
                        Text(
                            item.text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (items.size > 6) {
                    Text(
                        "and ${items.size - 6} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.spiraExtras.mutedForeground,
                    )
                }
            }
        }

        // Optional extras, each on its own checkbox: keep the goal, skip the date.
        if (!settled && aspects.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            aspects.forEach { aspect ->
                AspectRow(
                    label = aspect.label,
                    checked = aspect.id !in excluded,
                    onToggle = {
                        excluded = if (aspect.id in excluded) excluded - aspect.id else excluded + aspect.id
                    },
                )
            }
        }

        if (!body.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (showBody) "Hide content" else "Read full content",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showBody = !showBody },
            )
            if (showBody) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.spiraExtras.surfaceSunken)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                ) {
                    Text(
                        stripHtml(body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        proposal.reasoning?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.spiraExtras.mutedForeground,
            )
        }

        if (settled) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (proposal.status == ProposalStatus.APPROVED) "Applied" else "Dismissed",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (proposal.status == ProposalStatus.APPROVED) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.spiraExtras.mutedForeground
                },
            )
            return@Column
        }

        if (editing) {
            Spacer(Modifier.height(10.dp))
            ReviseBox(
                onSend = { instruction -> editing = false; onRevise(proposal, instruction) },
                onCancel = { editing = false },
            )
            return@Column
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardButton("Accept", primary = true, modifier = Modifier.weight(1f)) {
                onAccept(proposal, excluded)
            }
            CardButton("Edit", modifier = Modifier.weight(1f)) { editing = true }
            CardButton("Dismiss", modifier = Modifier.weight(1f)) { onDismiss(proposal) }
        }
    }
}

/** The kind badge: what sort of change this is, in one word. */
@Composable
private fun KindBadge(kind: ProposalKind, settled: Boolean) {
    val creating = kind in CREATE_KINDS
    val destructive = kind.wire.startsWith("delete_")
    val background = when {
        settled -> MaterialTheme.spiraExtras.surfaceSunken
        destructive -> Guava100
        creating -> Kale100
        else -> MaterialTheme.spiraExtras.surfaceSunken
    }
    val tint = when {
        settled -> MaterialTheme.spiraExtras.mutedForeground
        destructive -> Guava600
        creating -> Kale500
        else -> MaterialTheme.spiraExtras.mutedForeground
    }
    Box(
        Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            kindLabel(kind),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.Bold,
            color = tint,
        )
    }
}

private val CREATE_KINDS = setOf(
    ProposalKind.NEW_GOAL,
    ProposalKind.TARGET,
    ProposalKind.TASK,
    ProposalKind.OPTION,
    ProposalKind.NOTE,
    ProposalKind.LINK,
    ProposalKind.EMAIL,
    ProposalKind.OBSTACLE,
    ProposalKind.ACTION,
    ProposalKind.ADD_CHECKLIST_ITEM,
)

internal fun kindLabel(kind: ProposalKind): String = when (kind) {
    ProposalKind.NEW_GOAL -> "NEW GOAL"
    ProposalKind.TARGET -> "NEW TARGET"
    ProposalKind.TASK -> "NEW TASK"
    ProposalKind.OPTION -> "NEW OPTION"
    ProposalKind.NOTE -> "NEW NOTE"
    ProposalKind.LINK -> "NEW LINK"
    ProposalKind.EMAIL -> "NEW CONTACT"
    ProposalKind.OBSTACLE -> "NEW OBSTACLE"
    ProposalKind.ACTION -> "NEW ACTION"
    ProposalKind.ADD_CHECKLIST_ITEM -> "NEW SUB-TASK"
    ProposalKind.EDIT -> "EDIT GOAL"
    ProposalKind.CONFIDENCE -> "CONFIDENCE"
    ProposalKind.DEADLINE -> "DEADLINE"
    ProposalKind.EDIT_TARGET -> "EDIT TARGET"
    ProposalKind.EDIT_OPTION -> "EDIT OPTION"
    ProposalKind.EDIT_OBSTACLE -> "EDIT OBSTACLE"
    ProposalKind.EDIT_ACTION -> "EDIT ACTION"
    ProposalKind.EDIT_NOTE -> "EDIT NOTE"
    ProposalKind.EDIT_LINK -> "EDIT LINK"
    ProposalKind.EDIT_EMAIL -> "EDIT CONTACT"
    ProposalKind.COMPLETE_TARGET -> "TARGET STATUS"
    ProposalKind.TARGET_PROGRESS -> "PROGRESS"
    ProposalKind.SELECT_OPTION -> "ACTIVE OPTION"
    ProposalKind.CHECKLIST_ITEM -> "CHECKLIST"
    ProposalKind.EDIT_GOAL -> "EDIT GOAL"
    ProposalKind.OPEN_GOAL -> "OPEN GOAL"
    ProposalKind.DELETE_GOAL -> "DELETE GOAL"
    ProposalKind.DELETE_TARGET -> "DELETE TARGET"
    ProposalKind.DELETE_OPTION -> "DELETE OPTION"
    ProposalKind.DELETE_OBSTACLE -> "DELETE OBSTACLE"
    ProposalKind.DELETE_ACTION -> "DELETE ACTION"
    ProposalKind.DELETE_CHECKLIST_ITEM -> "DELETE ITEM"
    ProposalKind.UNKNOWN -> "CHANGE"
}

/** One optional field of a create proposal, with its own tick. */
@Composable
private fun AspectRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(5.dp))
                .then(
                    if (checked) {
                        Modifier.background(MaterialTheme.colorScheme.primary)
                    } else {
                        Modifier.border(2.dp, MaterialTheme.spiraExtras.borderStrong, RoundedCornerShape(5.dp))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    SpiraIcons.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The Edit box: an instruction that is sent with the whole proposal attached. */
@Composable
private fun ReviseBox(onSend: (String) -> Unit, onCancel: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.spiraExtras.surfaceSunken)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            InlineEditText(
                value = draft,
                onCommit = { draft = it },
                onTextChanged = { draft = it },
                placeholder = "What should change?",
                textStyle = MaterialTheme.typography.bodySmall,
                singleLine = false,
                autoFocus = true,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardButton("Send", primary = true, modifier = Modifier.weight(1f)) {
                if (draft.isNotBlank()) onSend(draft)
            }
            CardButton("Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
        }
    }
}

@Composable
private fun CardButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (primary) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier.border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(8.dp))
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}
