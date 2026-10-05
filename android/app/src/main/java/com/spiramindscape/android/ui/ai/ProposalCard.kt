package com.spiramindscape.android.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.spiramindscape.android.data.ai.Proposal
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.BasicTextField
import com.spiramindscape.android.data.ai.CreateAspect
import com.spiramindscape.android.data.ai.ProposalItem
import com.spiramindscape.android.data.ai.ProposalKind
import com.spiramindscape.android.data.ai.ProposalStatus
import com.spiramindscape.android.data.ai.applyExcludedAspects
import com.spiramindscape.android.data.ai.createAspects
import com.spiramindscape.android.data.ai.createSummary
import com.spiramindscape.android.data.ai.editDisplay
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Brand1100
import com.spiramindscape.android.ui.theme.Kale600
import com.spiramindscape.android.ui.theme.Salt300
import com.spiramindscape.android.ui.theme.Salt500
import com.spiramindscape.android.ui.theme.Success100
import com.spiramindscape.android.ui.theme.Success900
import com.spiramindscape.android.ui.theme.spiraExtras

/**
 * The proposal cards — a **faithful port of the web** family in `AiPanel.tsx`
 * (`ProposalCard`, `CreateConfirmCard`, `CreateChecklistCard`, `SteppedProposalCard`).
 *
 * The web is the spec for these, and this file is not the place to have opinions: when the two
 * differ, the web wins and this is what changes. Android had drifted — three equal-width buttons
 * in a row instead of the web's Accept / Edit / quiet Dismiss, a status pill where the web has an
 * uppercase kicker, per-step Accept where the web reviews the whole set and saves once — and every
 * one of those differences was a small redesign nobody asked for.
 *
 * The measurements below come straight from the web's classes, so a change there can be found
 * here: card `rounded-[14px] p-4`, kicker `10.5px` uppercase Kale-600, headline `17px` serif,
 * detail `13.5px` at 60% ink, buttons `rounded-[9px]`, Kale-600 fills.
 */

/** The card shell: white, softly rounded, hairline bordered — the web's `CARD_CLS`. */
@Composable
private fun ProposalCardShell(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(16.dp),
        content = content,
    )
}

// ── shared pieces ───────────────────────────────────────────────────────────

/** The kind kicker: a 12dp mark and an uppercase word in Kale-600. Not a pill — the web's is not. */
@Composable
private fun KindKicker(kind: ProposalKind) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            kindIcon(kind),
            contentDescription = null,
            tint = Kale600,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            kindLabel(kind).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.07.em,
            color = Kale600,
        )
    }
}

/** The headline: the brand serif, as the web sets it in Playfair. */
@Composable
private fun Headline(text: String, compact: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontSize = if (compact) 16.sp else 17.sp,
        lineHeight = if (compact) 20.sp else 21.sp,
        fontWeight = FontWeight.SemiBold,
        color = Brand1100,
    )
}

@Composable
private fun DetailLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
        color = Brand1100.copy(alpha = 0.60f),
        modifier = modifier,
    )
}

/** "Read full content" — 12.5px medium Kale-600 with the expand mark. */
@Composable
private fun ReadFullContent(expanded: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(SpiraIcons.Expand, contentDescription = null, tint = Kale600, modifier = Modifier.size(13.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            if (expanded) "Hide content" else "Read full content",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = Kale600,
        )
    }
}

/** The body sheet under "Read full content" — markup rendered as markup (GRO-80). */
@Composable
private fun BodySheet(body: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.spiraExtras.surfaceSunken)
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
    ) {
        if (looksLikeHtml(body)) {
            Text(rememberHtmlText(body), style = MaterialTheme.typography.bodySmall, color = Brand1100)
        } else {
            Text(body, style = MaterialTheme.typography.bodySmall, color = Brand1100)
        }
    }
}

/**
 * The web's `CheckBox` — a 20dp rounded square, filled Kale-600 with a white tick when on and a
 * hairline outline when off. Visual only: the row around it is what you press.
 */
@Composable
private fun CheckBox(checked: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (checked) Modifier.background(Kale600)
                else Modifier.background(Color.White).border(1.dp, Salt500, RoundedCornerShape(6.dp)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(SpiraIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

/** A tickable row: the box, then its label — the shape every checkbox on these cards takes. */
@Composable
private fun CheckRow(
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CheckBox(checked)
        Spacer(Modifier.size(10.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/** The filled action — Kale-600 on white, `rounded-[9px]`, a 14dp mark then the word. */
@Composable
private fun PrimaryAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) Kale600 else Kale600.copy(alpha = 0.40f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (fillWidth) Arrangement.Center else Arrangement.Start,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

/** The outlined action beside it — a hairline box, dark ink. */
@Composable
private fun SecondaryAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, Salt500, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Brand1100, modifier = Modifier.size(13.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Brand1100,
        )
    }
}

/**
 * Dismiss — **a quiet word, not a button**, pushed to the far right.
 *
 * This is the difference the owner caught: Android had Accept / Edit / Dismiss as three equal
 * buttons in a row, which gives refusing a change the same weight as making it.
 */
@Composable
private fun QuietAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontSize = 13.sp,
        color = Brand1100.copy(alpha = 0.50f),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
    )
}

/** The settled result: a badge, and — once the thing exists — an Open button on the right. */
@Composable
private fun SettledRow(
    approved: Boolean,
    label: String,
    openLabel: String? = null,
    onOpen: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(if (approved) Success100 else Salt300)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (approved) SpiraIcons.Check else SpiraIcons.X,
                contentDescription = null,
                tint = if (approved) Success900 else Brand1100.copy(alpha = 0.50f),
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.size(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (approved) Success900 else Brand1100.copy(alpha = 0.50f),
            )
        }
        if (approved && onOpen != null && openLabel != null) {
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Kale600)
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    SpiraIcons.SwitchArrows,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    openLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }
    }
}

/** "Type a change for the AI…" — the quiet link that opens [InstructBox]. */
@Composable
private fun InstructLink(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            SpiraIcons.Sparkles,
            contentDescription = null,
            tint = Brand1100.copy(alpha = 0.55f),
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            "Type a change for the AI…",
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.5.sp,
            color = Brand1100.copy(alpha = 0.55f),
        )
    }
}

/** The hairline the web draws above a card's footer (`border-t border-[#F3F3F3]`). */
@Composable
private fun FooterRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Salt300))
}

/** The instruction box — "tell the AI how to change this", the web's `InstructBox`. */
@Composable
private fun InstructBox(headline: String, onSend: (String) -> Unit, onCancel: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Headline(headline, compact = true)
        Text(
            "Tell the AI how to change this - it will re-propose.",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp,
            color = Brand1100.copy(alpha = 0.55f),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(1.dp, Salt500, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, color = Brand1100),
                cursorBrush = SolidColor(Kale600),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { field ->
                    if (draft.isEmpty()) {
                        Text(
                            "e.g. make it shorter, or due next Friday",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            color = Brand1100.copy(alpha = 0.40f),
                        )
                    }
                    field()
                },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PrimaryAction(
                label = "Send to AI",
                icon = SpiraIcons.Sparkles,
                enabled = draft.isNotBlank(),
                onClick = { if (draft.isNotBlank()) onSend(draft.trim()) },
            )
            Spacer(Modifier.weight(1f))
            QuietAction("Cancel", onClick = onCancel)
        }
    }
}

/** A create proposal's optional field, with its own tick and - for a body - its own preview. */
@Composable
private fun AspectRow(
    label: String,
    body: String?,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    var showBody by remember(label) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        CheckRow(checked = checked, enabled = enabled, onToggle = onToggle) {
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Brand1100.copy(alpha = if (enabled) 1f else 0.45f),
                )
                if (!body.isNullOrBlank()) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.5.sp,
                        color = Brand1100.copy(alpha = 0.55f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (!body.isNullOrBlank()) {
            ReadFullContent(showBody, Modifier.padding(start = 30.dp)) { showBody = !showBody }
            if (showBody) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.padding(start = 30.dp)) { BodySheet(body) }
            }
        }
    }
}

/** A checklist target's steps, each tickable - the web's `ChecklistItems`. */
@Composable
private fun ChecklistItems(
    items: List<ProposalItem>,
    excluded: Set<Int>,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
    Column(Modifier.padding(start = 30.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { i, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onToggle(i) }
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckBox(i !in excluded && enabled)
                Spacer(Modifier.size(8.dp))
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.5.sp,
                    color = Brand1100.copy(alpha = if (i in excluded || !enabled) 0.45f else 0.80f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * One proposed change.
 *
 * The web picks between three shapes here, and so does this: a **create carrying optional fields**
 * becomes a checklist of ticks, a **bare create** becomes a one-tap confirm, and everything else
 * is the plain Accept / Edit / Dismiss card.
 */
@Composable
fun ProposalCard(
    proposal: Proposal,
    onAccept: (Proposal, Set<String>) -> Unit,
    onDismiss: (Proposal) -> Unit,
    onRevise: (Proposal, String) -> Unit,
    modifier: Modifier = Modifier,
    /** Opens what this proposal created. Null until it exists (and for kinds that create nothing). */
    onOpenNote: (() -> Unit)? = null,
) {
    var instructing by remember(proposal.id) { mutableStateOf(false) }
    val display = remember(proposal) {
        if (proposal.kind == ProposalKind.EDIT) editDisplay(proposal) else null
    }
    val headline = (display?.headline ?: proposal.title).ifBlank { "Proposed change" }

    if (instructing) {
        ProposalCardShell(modifier) {
            InstructBox(
                headline = headline,
                onSend = { instructing = false; onRevise(proposal, it) },
                onCancel = { instructing = false },
            )
        }
        return
    }

    val aspects = remember(proposal) { createAspects(proposal) }
    val isCreate = proposal.kind in CREATE_ENTITY_KINDS
    val structured = proposal.items?.isNotEmpty() == true

    when {
        isCreate && (aspects.isNotEmpty() || structured) -> CreateChecklistCard(
            proposal, headline, aspects, modifier, onAccept, onDismiss, { instructing = true },
        )
        isCreate -> CreateConfirmCard(proposal, headline, modifier, onAccept, onDismiss)
        else -> PlainProposalCard(
            proposal, headline, display?.detail ?: proposal.detail, display?.body ?: proposal.body,
            modifier, onAccept, onDismiss, { instructing = true }, onOpenNote,
        )
    }
}

/** The web's `ProposalCard`: the body, then Accept / Edit / a quiet Dismiss on the right. */
@Composable
private fun PlainProposalCard(
    proposal: Proposal,
    headline: String,
    detail: String?,
    body: String?,
    modifier: Modifier,
    onAccept: (Proposal, Set<String>) -> Unit,
    onDismiss: (Proposal) -> Unit,
    onInstruct: () -> Unit,
    onOpenNote: (() -> Unit)?,
) {
    var showBody by remember(proposal.id) { mutableStateOf(false) }
    val settled = proposal.status != ProposalStatus.PENDING

    ProposalCardShell(modifier) {
        KindKicker(proposal.kind)
        Spacer(Modifier.height(8.dp))
        Headline(headline)
        // The kicker already names the action; a detail line that only repeats it earns nothing.
        if (!detail.isNullOrBlank() && !detail.equals(kindLabel(proposal.kind), ignoreCase = true)) {
            Spacer(Modifier.height(6.dp))
            DetailLine(detail)
        }
        if (!body.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            ReadFullContent(showBody) { showBody = !showBody }
            if (showBody) {
                Spacer(Modifier.height(6.dp))
                BodySheet(body)
            }
        }

        Spacer(Modifier.height(14.dp))
        if (settled) {
            SettledRow(
                approved = proposal.status == ProposalStatus.APPROVED,
                label = if (proposal.status == ProposalStatus.APPROVED) "Added to goal" else "Dismissed",
                openLabel = "Open note".takeIf { proposal.kind == ProposalKind.NOTE },
                onOpen = onOpenNote.takeIf { proposal.kind == ProposalKind.NOTE },
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PrimaryAction("Accept", SpiraIcons.Check) { onAccept(proposal, emptySet()) }
                Spacer(Modifier.width(8.dp))
                SecondaryAction("Edit", SpiraIcons.Pencil, onInstruct)
                Spacer(Modifier.weight(1f))
                QuietAction("Dismiss") { onDismiss(proposal) }
            }
        }
    }
}

/** The web's `CreateConfirmCard`: a name and one button. Deliberately not a wizard. */
@Composable
private fun CreateConfirmCard(
    proposal: Proposal,
    headline: String,
    modifier: Modifier,
    onAccept: (Proposal, Set<String>) -> Unit,
    onDismiss: (Proposal) -> Unit,
) {
    val settled = proposal.status != ProposalStatus.PENDING
    val isGoal = proposal.kind == ProposalKind.NEW_GOAL

    ProposalCardShell(modifier) {
        KindKicker(proposal.kind)
        Spacer(Modifier.height(8.dp))
        Headline(headline)
        Spacer(Modifier.height(14.dp))
        if (settled) {
            SettledRow(
                approved = proposal.status == ProposalStatus.APPROVED,
                label = when {
                    proposal.status != ProposalStatus.APPROVED -> "Dismissed"
                    isGoal -> "Goal created"
                    else -> "Target added"
                },
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PrimaryAction(createLabel(proposal.kind), SpiraIcons.Check) { onAccept(proposal, emptySet()) }
                Spacer(Modifier.weight(1f))
                QuietAction("Dismiss") { onDismiss(proposal) }
            }
        }
    }
}

/** The web's `CreateChecklistCard`: the thing itself, then one tick per optional field. */
@Composable
private fun CreateChecklistCard(
    proposal: Proposal,
    headline: String,
    aspects: List<CreateAspect>,
    modifier: Modifier,
    onAccept: (Proposal, Set<String>) -> Unit,
    onDismiss: (Proposal) -> Unit,
    onInstruct: () -> Unit,
) {
    var createOn by remember(proposal.id) { mutableStateOf(true) }
    var excluded by remember(proposal.id) { mutableStateOf(emptySet<String>()) }
    var excludedItems by remember(proposal.id) { mutableStateOf(emptySet<Int>()) }
    val settled = proposal.status != ProposalStatus.PENDING
    val items = proposal.items.orEmpty()
    val summary = remember(proposal) { createSummary(proposal) }

    ProposalCardShell(modifier) {
        KindKicker(proposal.kind)
        Spacer(Modifier.height(if (settled) 8.dp else 12.dp))

        if (settled) {
            Headline(headline)
            Spacer(Modifier.height(12.dp))
            SettledRow(
                approved = proposal.status == ProposalStatus.APPROVED,
                label = when {
                    proposal.status != ProposalStatus.APPROVED -> "Dismissed"
                    proposal.kind == ProposalKind.NEW_GOAL -> "Goal created"
                    else -> "Target added"
                },
            )
            return@ProposalCardShell
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CheckRow(checked = createOn, onToggle = { createOn = !createOn }) {
                Text(
                    // The web quotes the name in guillemets: "Add «Run 5km three times a week»".
                    createVerb(proposal.kind) + " «" + headline + "»",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Brand1100.copy(alpha = if (createOn) 1f else 0.45f),
                )
            }
            // A checklist shows its steps as real ticks, so a count line would say it twice.
            if (summary != null && items.isEmpty()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.5.sp,
                    color = Brand1100.copy(alpha = 0.55f),
                    modifier = Modifier.padding(start = 30.dp),
                )
            }
            if (items.isNotEmpty()) {
                ChecklistItems(items, excludedItems, createOn) { i ->
                    excludedItems = if (i in excludedItems) excludedItems - i else excludedItems + i
                }
            }
            aspects.forEach { aspect ->
                AspectRow(
                    label = aspect.label,
                    body = aspect.body,
                    checked = aspect.id !in excluded && createOn,
                    enabled = createOn,
                    onToggle = {
                        excluded = if (aspect.id in excluded) excluded - aspect.id else excluded + aspect.id
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        InstructLink(onInstruct)
        Spacer(Modifier.height(14.dp))
        FooterRule()
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PrimaryAction(createLabel(proposal.kind), SpiraIcons.Check) {
                if (!createOn) {
                    onDismiss(proposal)
                } else {
                    val trimmed = if (excludedItems.isEmpty()) {
                        proposal
                    } else {
                        proposal.copy(items = items.filterIndexed { i, _ -> i !in excludedItems })
                    }
                    onAccept(trimmed, excluded)
                }
            }
            Spacer(Modifier.weight(1f))
            QuietAction("Dismiss") { onDismiss(proposal) }
        }
    }
}

/**
 * Several changes from one reply — the web's `SteppedProposalCard`, and it is a **review**, not a
 * queue of decisions.
 *
 * You page through the steps ticking what you want, and the card saves the lot with one button.
 * Android had it as per-step Accept / Dismiss, which made a three-change answer into six taps and
 * six chances to lose track of what had already been applied.
 */
@Composable
fun ProposalGroup(
    proposals: List<Proposal>,
    onAccept: (Proposal, Set<String>) -> Unit,
    onDismiss: (Proposal) -> Unit,
    onRevise: (Proposal, String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenNote: (() -> Unit)? = null,
) {
    if (proposals.isEmpty()) return
    if (proposals.size == 1) {
        ProposalCard(
            proposal = proposals.first(),
            onAccept = onAccept,
            onDismiss = onDismiss,
            onRevise = onRevise,
            modifier = modifier,
            onOpenNote = onOpenNote,
        )
        return
    }

    val total = proposals.size
    val allSettled = proposals.all { it.status != ProposalStatus.PENDING }
    val saved = proposals.count { it.status == ProposalStatus.APPROVED }

    // Once every step is answered the card collapses to a single line, as the web's does.
    if (allSettled) {
        ProposalCardShell(modifier) {
            SettledRow(
                approved = saved > 0,
                label = if (saved > 0) "Saved $saved of $total changes" else "All dismissed",
            )
        }
        return
    }

    var step by remember(proposals.map { it.id }) { mutableStateOf(0) }
    var instructing by remember(proposals.map { it.id }) { mutableStateOf(false) }
    var off by remember(proposals.map { it.id }) { mutableStateOf(emptySet<String>()) }
    var aspectOff by remember(proposals.map { it.id }) { mutableStateOf(emptySet<String>()) }

    val index = step.coerceIn(0, proposals.lastIndex)
    val current = proposals[index]
    val display = remember(current) {
        if (current.kind == ProposalKind.EDIT) editDisplay(current) else null
    }
    val headline = (display?.headline ?: current.title).ifBlank { "Proposed change" }
    val detail = display?.detail ?: current.detail
    val body = display?.body ?: current.body
    val aspects = remember(current) { createAspects(current) }
    val isCreate = current.kind in CREATE_ENTITY_KINDS
    val summary = remember(current) { createSummary(current) }
    val items = current.items.orEmpty()
    val isOff = current.id in off || current.status == ProposalStatus.REJECTED
    val includedCount = proposals.count { it.id !in off && it.status != ProposalStatus.REJECTED }
    var showBody by remember(current.id) { mutableStateOf(false) }

    ProposalCardShell(modifier) {
        if (instructing) {
            InstructBox(
                headline = headline,
                onSend = { instructing = false; onRevise(current, it) },
                onCancel = { instructing = false },
            )
            return@ProposalCardShell
        }

        // Header: how many changes, where you are, and a way out of the whole set.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (total > 1) "$total CHANGES" else "1 CHANGE"),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.07.em,
                color = Kale600,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${index + 1} / $total",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Brand1100.copy(alpha = 0.45f),
            )
            Spacer(Modifier.size(8.dp))
            Icon(
                SpiraIcons.X,
                contentDescription = "Dismiss all",
                tint = Brand1100.copy(alpha = 0.35f),
                modifier = Modifier
                    .size(16.dp)
                    .clickable { proposals.forEach(onDismiss) },
            )
        }

        // The progress rail: one bar that fills as you move through, not a row of dots.
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Kale600.copy(alpha = 0.12f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((index + 1).toFloat() / total)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Kale600),
            )
        }
        Spacer(Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CheckRow(
                checked = !isOff,
                enabled = current.status != ProposalStatus.REJECTED,
                onToggle = { off = if (current.id in off) off - current.id else off + current.id },
            ) {
                Column {
                    Text(
                        headline,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Brand1100.copy(alpha = if (isOff) 0.45f else 1f),
                    )
                    // For a create the fields have their own ticks below — never restate them.
                    if (!isCreate && !detail.isNullOrBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.5.sp,
                            color = Brand1100.copy(alpha = 0.55f),
                        )
                    }
                    if (isCreate && summary != null && items.isEmpty()) {
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.5.sp,
                            color = Brand1100.copy(alpha = 0.55f),
                        )
                    }
                }
            }
            if (!body.isNullOrBlank() && !isCreate) {
                ReadFullContent(showBody, Modifier.padding(start = 30.dp)) { showBody = !showBody }
                if (showBody) Box(Modifier.padding(start = 30.dp)) { BodySheet(body) }
            }
            if (items.isNotEmpty()) {
                ChecklistItems(items, emptySet(), !isOff) { }
            }
            aspects.forEach { aspect ->
                val key = current.id + "::" + aspect.id
                AspectRow(
                    label = aspect.label,
                    body = aspect.body,
                    checked = key !in aspectOff && !isOff,
                    enabled = !isOff,
                    onToggle = { aspectOff = if (key in aspectOff) aspectOff - key else aspectOff + key },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        InstructLink { instructing = true }

        Spacer(Modifier.height(14.dp))
        FooterRule()
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StepNav("Back", enabled = index > 0, leading = true) { step = index - 1 }
            Spacer(Modifier.weight(1f))
            if (index < proposals.lastIndex) {
                StepNav("Next", enabled = true, leading = false) { step = index + 1 }
            } else {
                Text(
                    "End of review",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 12.sp,
                    color = Brand1100.copy(alpha = 0.40f),
                )
            }
        }

        // One button for the whole set — the point of reviewing rather than deciding step by step.
        Spacer(Modifier.height(12.dp))
        PrimaryAction(
            label = if (includedCount == total) "Save all $total" else "Save $includedCount of $total",
            icon = SpiraIcons.Check,
            enabled = includedCount > 0,
            fillWidth = true,
        ) {
            proposals.forEach { p ->
                if (p.status != ProposalStatus.PENDING) return@forEach
                if (p.id in off) {
                    onDismiss(p)
                } else {
                    val excluded = aspects
                        .map { p.id + "::" + it.id }
                        .filter { it in aspectOff }
                        .map { it.substringAfter("::") }
                        .toSet()
                    onAccept(p, excluded)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            QuietAction("Dismiss all") { proposals.forEach(onDismiss) }
        }
    }
}

/** A step-navigation word with its chevron — Back leads with it, Next trails it. */
@Composable
private fun StepNav(label: String, enabled: Boolean, leading: Boolean, onClick: () -> Unit) {
    val ink = if (!enabled) {
        Brand1100.copy(alpha = 0.30f)
    } else if (leading) {
        Brand1100.copy(alpha = 0.70f)
    } else {
        Kale600
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading) {
            Icon(SpiraIcons.ChevronLeft, contentDescription = null, tint = ink, modifier = Modifier.size(13.dp))
            Spacer(Modifier.size(4.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontSize = 13.sp,
            fontWeight = if (leading) FontWeight.Medium else FontWeight.SemiBold,
            color = ink,
        )
        if (!leading) {
            Spacer(Modifier.size(4.dp))
            Icon(SpiraIcons.ChevronRight, contentDescription = null, tint = ink, modifier = Modifier.size(13.dp))
        }
    }
}

/** "Add target" / "Create goal" — the web words its confirm by what it makes. */
private fun createLabel(kind: ProposalKind): String = when (kind) {
    ProposalKind.NEW_GOAL -> "Create goal"
    ProposalKind.TARGET, ProposalKind.TASK -> "Add target"
    else -> "Accept"
}

private fun createVerb(kind: ProposalKind): String =
    if (kind == ProposalKind.NEW_GOAL) "Create" else "Add"

/** The kinds the web routes to its create cards (`CREATE_KINDS`). */
private val CREATE_ENTITY_KINDS = setOf(
    ProposalKind.NEW_GOAL,
    ProposalKind.TARGET,
    ProposalKind.TASK,
)

/** The mark beside each kind — the web's `KIND_META` icons. */
internal fun kindIcon(kind: ProposalKind): ImageVector = when (kind) {
    ProposalKind.NEW_GOAL, ProposalKind.OPTION, ProposalKind.LINK,
    ProposalKind.EDIT_OPTION, ProposalKind.SELECT_OPTION,
    -> SpiraIcons.Sparkles
    ProposalKind.EMAIL -> SpiraIcons.Mail
    ProposalKind.TARGET, ProposalKind.EDIT_TARGET, ProposalKind.TARGET_PROGRESS -> SpiraIcons.Target
    ProposalKind.TASK, ProposalKind.COMPLETE_TARGET, ProposalKind.CHECKLIST_ITEM -> SpiraIcons.Check
    ProposalKind.ADD_CHECKLIST_ITEM -> SpiraIcons.Plus
    ProposalKind.NOTE, ProposalKind.EDIT, ProposalKind.EDIT_NOTE, ProposalKind.EDIT_LINK,
    ProposalKind.EDIT_EMAIL, ProposalKind.EDIT_GOAL,
    -> SpiraIcons.Pencil
    ProposalKind.OBSTACLE, ProposalKind.EDIT_OBSTACLE -> SpiraIcons.Shield
    ProposalKind.ACTION, ProposalKind.EDIT_ACTION -> SpiraIcons.CheckShape
    ProposalKind.CONFIDENCE -> SpiraIcons.Idea
    ProposalKind.DEADLINE -> SpiraIcons.Clock
    ProposalKind.OPEN_GOAL -> SpiraIcons.SwitchArrows
    ProposalKind.DELETE_GOAL, ProposalKind.DELETE_TARGET, ProposalKind.DELETE_OPTION,
    ProposalKind.DELETE_OBSTACLE, ProposalKind.DELETE_ACTION, ProposalKind.DELETE_CHECKLIST_ITEM,
    -> SpiraIcons.Trash
    ProposalKind.UNKNOWN -> SpiraIcons.Sparkles
}

/** The word for each kind — the web's `KIND_META` labels. */
internal fun kindLabel(kind: ProposalKind): String = when (kind) {
    ProposalKind.NEW_GOAL -> "New goal"
    ProposalKind.TARGET -> "New target"
    ProposalKind.TASK -> "New task"
    ProposalKind.OPTION -> "Strategy option"
    ProposalKind.NOTE -> "Resource note"
    ProposalKind.LINK -> "New link"
    ProposalKind.EMAIL -> "New email"
    ProposalKind.OBSTACLE -> "New obstacle"
    ProposalKind.ACTION -> "Current action"
    ProposalKind.ADD_CHECKLIST_ITEM -> "New sub-task"
    ProposalKind.EDIT -> "Goal edit"
    ProposalKind.CONFIDENCE -> "Confidence"
    ProposalKind.DEADLINE -> "Deadline"
    ProposalKind.EDIT_TARGET -> "Edit target"
    ProposalKind.EDIT_OPTION -> "Edit option"
    ProposalKind.EDIT_OBSTACLE -> "Edit obstacle"
    ProposalKind.EDIT_ACTION -> "Edit action"
    ProposalKind.EDIT_NOTE -> "Edit note"
    ProposalKind.EDIT_LINK -> "Edit link"
    ProposalKind.EDIT_EMAIL -> "Edit email"
    ProposalKind.COMPLETE_TARGET -> "Target status"
    ProposalKind.TARGET_PROGRESS -> "Target progress"
    ProposalKind.SELECT_OPTION -> "Select option"
    ProposalKind.CHECKLIST_ITEM -> "Checklist item"
    ProposalKind.EDIT_GOAL -> "Edit goal"
    ProposalKind.OPEN_GOAL -> "Open goal"
    ProposalKind.DELETE_GOAL -> "Delete goal"
    ProposalKind.DELETE_TARGET -> "Delete target"
    ProposalKind.DELETE_OPTION -> "Delete option"
    ProposalKind.DELETE_OBSTACLE -> "Delete obstacle"
    ProposalKind.DELETE_ACTION -> "Delete action"
    ProposalKind.DELETE_CHECKLIST_ITEM -> "Delete item"
    ProposalKind.UNKNOWN -> "Change"
}
