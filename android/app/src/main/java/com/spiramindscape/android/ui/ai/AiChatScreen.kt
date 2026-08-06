package com.spiramindscape.android.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spiramindscape.android.data.ai.AiApi
import com.spiramindscape.android.data.ai.ChatMessage
import com.spiramindscape.android.data.ai.ChatRole
import com.spiramindscape.android.data.ai.Proposal
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.ui.components.InlineEditText
import com.spiramindscape.android.ui.goals.copyPlainText
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Guava300
import com.spiramindscape.android.ui.theme.Kale300
import com.spiramindscape.android.ui.theme.Kale600

/**
 * The AI coach panel — the same design as the desktop `AiPanel`, on the brand's Kale-600 ground.
 *
 * The whole surface is teal and the type is white: the wordmark and its actions across the top, a
 * provider strip under it, the conversation on the teal itself (user turns in white bubbles,
 * the assistant's prose set straight on the ground with no bubble at all), and a single white
 * composer card at the foot.
 */
@Composable
fun AiChatScreen(
    viewModel: AiChatViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** The open goal, for the opening prompts. Null in the all-goals chat. */
    goal: GoalDetail? = null,
    /** Apply an accepted proposal. Returns the message to show, or null when it was applied. */
    onApplyProposal: (Proposal, Set<String>) -> String? = { _, _ -> "This build can't apply that yet." },
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val provider by viewModel.provider.collectAsStateWithLifecycle()
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val needsKey by viewModel.needsKey.collectAsStateWithLifecycle()
    val remaining by viewModel.remainingSeconds.collectAsStateWithLifecycle()
    val totalMinutes by viewModel.sessionMinutes.collectAsStateWithLifecycle()

    var providerSheet by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val inGrow = mode != ChatMode.CHAT

    // Follow the answer as it streams, and land on the newest turn when one arrives.
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Kale600)
            .statusBarsPadding(),
    ) {
        PanelHeader(
            inGrow = inGrow,
            canClear = messages.isNotEmpty(),
            busy = streaming,
            remainingSeconds = remaining,
            totalMinutes = totalMinutes,
            onClose = onClose,
            onNewChat = viewModel::clearChat,
            onEndSession = viewModel::closeGrow,
        )

        if (!inGrow) {
            ProviderStrip(
                provider = provider,
                connected = keys.any { it.provider.equals(provider, ignoreCase = true) },
                onOpen = { providerSheet = true },
            )
        }

        if (mode == ChatMode.GROW_CLOSING) {
            Banner(SpiraIcons.Leaf, "The session is gently moving toward a close")
        }
        notice?.let { NoticeBanner(it) { notice = null } }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!inGrow && messages.isEmpty()) {
                item {
                    EmptyChat(
                        goal = goal,
                        needsKey = needsKey,
                        onPick = viewModel::send,
                        onAddKey = { providerSheet = true },
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageRow(
                    message = message,
                    onAcceptProposal = { proposal, excluded ->
                        notice = onApplyProposal(proposal, excluded)
                        viewModel.settleProposal(message.id, proposal.id, approved = notice == null)
                    },
                    onDismissProposal = { proposal ->
                        viewModel.settleProposal(message.id, proposal.id, approved = false)
                    },
                    onReviseProposal = viewModel::reviseProposal,
                )
            }
        }

        when (mode) {
            ChatMode.GROW_START -> GrowStartOverlay(
                onStart = viewModel::startGrow,
                onCancel = viewModel::cancelGrow,
            )
            ChatMode.GROW_END -> GrowEndCard(
                summary = messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.content.orEmpty(),
                onSave = { summary ->
                    viewModel.saveSessionMemory(summary) { error ->
                        notice = error ?: "Saved to this goal."
                    }
                    viewModel.finishGrow()
                },
                onDiscard = viewModel::finishGrow,
            )
            else -> {
                if (inGrow) {
                    Text(
                        "End session early",
                        style = MaterialTheme.typography.labelMedium,
                        color = WHITE_60,
                        modifier = Modifier
                            .padding(start = 16.dp, bottom = 4.dp)
                            .clickable(onClick = viewModel::closeGrow),
                    )
                }
                Composer(
                    enabled = !needsKey,
                    streaming = streaming,
                    allowAttachments = !inGrow,
                    placeholder = if (inGrow) "Answer in your own words…" else "Ask, plan, or request an action…",
                    growAction = if (!inGrow && viewModel.scopeGoalId != null) viewModel::openGrowStart else null,
                    onSend = { text, attachments -> viewModel.send(text, attachments) },
                    onStop = viewModel::cancelStream,
                )
            }
        }
    }

    if (providerSheet) {
        ProviderSheet(viewModel = viewModel, onDismiss = { providerSheet = false })
    }
}

// ── the panel's own tones: white at the opacities the web uses on teal ──────

internal val WHITE_74 = Color.White.copy(alpha = 0.74f)
internal val WHITE_60 = Color.White.copy(alpha = 0.60f)
internal val WHITE_35 = Color.White.copy(alpha = 0.35f)
internal val WHITE_20 = Color.White.copy(alpha = 0.20f)
internal val WHITE_10 = Color.White.copy(alpha = 0.10f)

/** Dark type on the white bubbles and composer, kept in the Kale family as the web has it. */
internal val ON_WHITE = Kale600

/**
 * The wordmark and the panel's actions. In a GROW session the right-hand side becomes the timer
 * and an End button; otherwise "New chat" (only once there is something to clear) and Close.
 */
@Composable
private fun PanelHeader(
    inGrow: Boolean,
    canClear: Boolean,
    busy: Boolean,
    remainingSeconds: Int,
    totalMinutes: Int,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onEndSession: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            Text(
                "spira",
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 27.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = Color.White,
            )
            Spacer(Modifier.size(7.dp))
            Text(
                "ai coach",
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp,
                lineHeight = 16.sp,
                color = WHITE_74,
                modifier = Modifier.padding(bottom = 1.dp),
            )
        }

        if (inGrow) {
            TimerPill(remainingSeconds, totalMinutes)
            Spacer(Modifier.size(8.dp))
            Row(
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, WHITE_35, CircleShape)
                    .clickable(onClick = onEndSession)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SpiraIcons.X, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(4.dp))
                Text(
                    "End",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        } else {
            if (canClear) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(enabled = !busy, onClick = onNewChat)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        SpiraIcons.Plus,
                        contentDescription = null,
                        tint = if (busy) WHITE_35 else WHITE_74,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "New chat",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (busy) WHITE_35 else WHITE_74,
                    )
                }
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SpiraIcons.X,
                    contentDescription = "Close the assistant",
                    tint = WHITE_74,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** "Bring your own key" on the left, the live provider and its status dot on the right. */
@Composable
private fun ProviderStrip(provider: String, connected: Boolean, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpen)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SpiraIcons.Key, contentDescription = null, tint = WHITE_74, modifier = Modifier.size(12.dp))
            Spacer(Modifier.size(6.dp))
            Text(
                "Bring your own key",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = WHITE_74,
            )
            Spacer(Modifier.size(6.dp))
            Icon(
                SpiraIcons.ChevronDown,
                contentDescription = null,
                tint = WHITE_60,
                modifier = Modifier.size(12.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                // Brand tones rather than the web's ad-hoc mint/amber: Kale-300 reads as "ready",
                // Guava-300 as "needs attention", and both hold up on the teal ground.
                .background(if (connected) Kale300 else Guava300),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            providerLabel(provider),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

/** How much of the session is left, as the web's pill. */
@Composable
private fun TimerPill(seconds: Int, totalMinutes: Int) {
    val minutes = seconds / 60
    val rest = seconds % 60
    val low = totalMinutes > 0 && seconds <= totalMinutes * 60 / 5
    Box(
        Modifier
            .clip(CircleShape)
            .background(WHITE_10)
            .border(1.dp, if (low) Guava300 else WHITE_20, CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            "%d:%02d".format(minutes, rest),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = if (low) Guava300 else Color.White,
        )
    }
}

@Composable
private fun Banner(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WHITE_10)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun NoticeBanner(text: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WHITE_10)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Icon(
            SpiraIcons.X,
            contentDescription = "Dismiss",
            tint = WHITE_60,
            modifier = Modifier.size(14.dp).clickable(onClick = onDismiss),
        )
    }
}

/** The opening screen: a leaf medallion, one line of orientation, then the starters. */
@Composable
private fun EmptyChat(
    goal: GoalDetail?,
    needsKey: Boolean,
    onPick: (String) -> Unit,
    onAddKey: () -> Unit,
) {
    val suggestions = remember(goal) { if (goal != null) buildGoalSuggestions(goal) else GLOBAL_SUGGESTIONS }

    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(WHITE_10)
                .border(1.dp, WHITE_20, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SpiraIcons.Leaf, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (goal != null) {
                "I'm here to help with \"${goal.title}\". Ask anything or start a GROW session."
            } else {
                "I'm here to help you think. Ask me anything, or just say what you want to achieve " +
                    "and I'll help you create a new goal."
            },
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = WHITE_74,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        Spacer(Modifier.height(20.dp))

        if (needsKey) {
            SuggestionButton(SpiraIcons.Key, "Add an API key to start chatting", onAddKey)
            Spacer(Modifier.height(8.dp))
        }
        suggestions.forEachIndexed { index, suggestion ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            SuggestionButton(suggestion.icon, suggestion.text) { onPick(suggestion.text) }
        }
    }
}

@Composable
private fun SuggestionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WHITE_10)
            .border(1.dp, WHITE_20, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = WHITE_74, modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            color = Color.White,
        )
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    onAcceptProposal: (Proposal, Set<String>) -> Unit,
    onDismissProposal: (Proposal) -> Unit,
    onReviseProposal: (Proposal, String) -> Unit,
) {
    when {
        message.role == ChatRole.USER -> UserTurn(message)
        message.role == ChatRole.SYSTEM -> SystemPill(message.content)
        message.isError -> ErrorTurn(message.content)
        else -> AssistantTurn(message, onAcceptProposal, onDismissProposal, onReviseProposal)
    }
}

/** The user's turn: a white bubble hugging the right edge, with its attachments above it. */
@Composable
private fun UserTurn(message: ChatMessage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        message.revisedLabel?.let {
            Row(
                Modifier.widthIn(max = 300.dp).padding(end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SpiraIcons.Pencil, contentDescription = null, tint = WHITE_60, modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    "Change to «$it»",
                    style = MaterialTheme.typography.labelMedium,
                    color = WHITE_60,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (message.attachments.isNotEmpty()) {
            Column(horizontalAlignment = Alignment.End) {
                message.attachments.forEach { attachment ->
                    Row(
                        Modifier
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            SpiraIcons.Paperclip,
                            contentDescription = null,
                            tint = ON_WHITE.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            attachment.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = ON_WHITE,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp),
                        )
                    }
                }
            }
        }

        if (message.content.isNotBlank()) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                    .background(Color.White)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = ON_WHITE,
                )
            }
            // Copy hangs off the LEFT under the bubble, as it does on the desktop — the bubble is
            // right-aligned, the action that follows it is not.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                CopyRow(message.content)
            }
        }
    }
}

/** The assistant's turn: prose set straight on the teal, no bubble — as on the desktop. */
@Composable
private fun AssistantTurn(
    message: ChatMessage,
    onAcceptProposal: (Proposal, Set<String>) -> Unit,
    onDismissProposal: (Proposal) -> Unit,
    onReviseProposal: (Proposal, String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (message.streaming && message.content.isBlank()) {
            Text(
                message.status ?: "Thinking…",
                style = MaterialTheme.typography.labelMedium,
                color = WHITE_60,
            )
        }
        if (message.content.isNotBlank()) {
            AiMarkdown(
                text = message.content,
                modifier = Modifier.widthIn(max = 340.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 23.5.sp),
                color = Color.White,
                mutedColor = WHITE_60,
            )
            if (!message.streaming) CopyRow(message.content)
        }

        if (message.proposals.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                message.proposals.forEach { proposal ->
                    ProposalCard(
                        proposal = proposal,
                        onAccept = onAcceptProposal,
                        onDismiss = onDismissProposal,
                        onRevise = onReviseProposal,
                    )
                }
            }
        }
    }
}

/** An error reads as a warning line on the ground, never as a bubble. */
@Composable
private fun ErrorTurn(text: String) {
    Row(Modifier.widthIn(max = 340.dp)) {
        Icon(
            SpiraIcons.TriangleAlert,
            contentDescription = null,
            tint = Guava300,
            modifier = Modifier.padding(top = 3.dp).size(15.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = Guava300,
        )
    }
}

@Composable
private fun SystemPill(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .wrapContentWidth()
                .clip(CircleShape)
                .background(WHITE_10)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SpiraIcons.Check, contentDescription = null, tint = WHITE_74, modifier = Modifier.size(12.dp))
            Spacer(Modifier.size(8.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = WHITE_74)
        }
    }
}

@Composable
private fun CopyRow(text: String) {
    val context = LocalContext.current
    var copied by remember(text) { mutableStateOf(false) }
    Row(
        Modifier.padding(top = 3.dp).clickable {
            copyPlainText(context, "spira ai coach", text)
            copied = true
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (copied) SpiraIcons.Check else SpiraIcons.Copy,
            contentDescription = "Copy message",
            tint = WHITE_60,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.size(5.dp))
        Text(
            if (copied) "Copied" else "Copy",
            style = MaterialTheme.typography.labelSmall,
            color = WHITE_60,
        )
    }
}

/**
 * The composer: one white card holding the field, with the quick actions and Send along its foot —
 * the layout the desktop borrowed from the Claude app.
 */
@Composable
private fun Composer(
    enabled: Boolean,
    streaming: Boolean,
    allowAttachments: Boolean,
    placeholder: String,
    growAction: (() -> Unit)?,
    onSend: (String, List<AiApi.ChatAttachment>) -> Unit,
    onStop: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<AiApi.ChatAttachment>>(emptyList()) }
    val picker = rememberChatAttachmentPicker { picked ->
        attachments = (attachments + picked).takeLast(ATTACH_MAX_COUNT)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, WHITE_35, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp)
                .padding(top = 10.dp, bottom = 8.dp),
        ) {
            if (attachments.isNotEmpty()) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    attachments.forEach { attachment ->
                        Row(
                            Modifier
                                .padding(bottom = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ON_WHITE.copy(alpha = 0.04f))
                                .border(1.dp, ON_WHITE.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                SpiraIcons.Paperclip,
                                contentDescription = null,
                                tint = ON_WHITE.copy(alpha = 0.6f),
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text(
                                attachment.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = ON_WHITE,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            Icon(
                                SpiraIcons.X,
                                contentDescription = "Remove ${attachment.name}",
                                tint = ON_WHITE.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { attachments = attachments - attachment },
                            )
                        }
                    }
                }
            }

            InlineEditText(
                value = draft,
                onCommit = { draft = it },
                onTextChanged = { draft = it },
                placeholder = placeholder,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    color = ON_WHITE,
                ),
                singleLine = false,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (allowAttachments) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled && attachments.size < ATTACH_MAX_COUNT) { picker() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            SpiraIcons.Paperclip,
                            contentDescription = "Attach a file",
                            tint = if (enabled) ON_WHITE else ON_WHITE.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                growAction?.let { start ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = start)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(SpiraIcons.Leaf, contentDescription = null, tint = ON_WHITE, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            "Start GROW session",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = ON_WHITE,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))

                val canSend = enabled && (draft.isNotBlank() || attachments.isNotEmpty())
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (streaming || canSend) ON_WHITE else ON_WHITE.copy(alpha = 0.4f))
                        .clickable(enabled = streaming || canSend) {
                            if (streaming) {
                                onStop()
                            } else {
                                onSend(draft, attachments)
                                draft = ""
                                attachments = emptyList()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (streaming) {
                        Box(Modifier.size(11.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
                    } else {
                        Icon(
                            SpiraIcons.ArrowUp,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Choosing a session length, as the desktop's start overlay does. */
@Composable
private fun GrowStartOverlay(onStart: (Int) -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(WHITE_10)
            .border(1.dp, WHITE_20, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SpiraIcons.Leaf, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                "Start a GROW session",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "A timed conversation through Goal, Reality, Options and Will.",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            color = WHITE_74,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 30, 45).forEach { minutes ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .clickable { onStart(minutes) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$minutes min",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ON_WHITE,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Cancel",
            style = MaterialTheme.typography.labelMedium,
            color = WHITE_60,
            modifier = Modifier.clickable(onClick = onCancel).padding(vertical = 4.dp),
        )
    }
}

/** The closing card: keep what the session worked out on the goal, or let it go. */
@Composable
private fun GrowEndCard(summary: String, onSave: (String) -> Unit, onDiscard: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(16.dp),
    ) {
        Text(
            "Session complete",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = ON_WHITE,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Save what you worked out, so the next session picks up the thread.",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            color = ON_WHITE.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ON_WHITE)
                    .clickable { onSave(summary) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Save memory",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, ON_WHITE.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onDiscard),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Discard",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ON_WHITE,
                )
            }
        }
    }
}

internal fun providerLabel(provider: String): String = when (provider.uppercase()) {
    "ANTHROPIC" -> "Claude"
    "OPENAI" -> "OpenAI"
    "MISTRAL" -> "Mistral"
    "GEMINI" -> "Gemini"
    else -> provider
}
