package com.spiramindscape.android.ui.ai

import androidx.compose.foundation.Image
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.spiramindscape.android.data.goals.ResourceItem
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.HorizontalDivider
import com.spiramindscape.android.ui.components.SpiraButton
import com.spiramindscape.android.ui.components.SpiraSheetHead
import com.spiramindscape.android.ui.components.SpiraTextField
import com.spiramindscape.android.ui.theme.spiraExtras
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import com.spiramindscape.android.ui.goals.decodeDataUrl
import com.spiramindscape.android.ui.theme.Kale300
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
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
import com.spiramindscape.android.data.ai.ProposalStatus
import com.spiramindscape.android.data.ai.ProposalKind
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.ui.components.InlineEditText
import com.spiramindscape.android.ui.components.SpiraBadge
import com.spiramindscape.android.ui.components.SpiraBadgeTone
import com.spiramindscape.android.ui.components.SpiraDropdownMenu
import com.spiramindscape.android.ui.components.SpiraMenuItem
import com.spiramindscape.android.ui.components.SpiraNoticeCard
import com.spiramindscape.android.ui.components.rememberCopyFlash
import com.spiramindscape.android.ui.components.SpiraNoticeKind
import com.spiramindscape.android.ui.goals.copyPlainText
import com.spiramindscape.android.ui.icons.SpiraArt
import com.spiramindscape.android.ui.components.addActionTextStyle
import com.spiramindscape.android.ui.theme.SpiraRadii
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Brand1100
import com.spiramindscape.android.ui.theme.Guava300
import com.spiramindscape.android.ui.theme.Intelligence300
import com.spiramindscape.android.ui.theme.Intelligence500
import com.spiramindscape.android.ui.theme.Intelligence900
import com.spiramindscape.android.ui.theme.Kale500
import com.spiramindscape.android.ui.theme.Kale600

/**
 * The AI coach panel — the same design as the desktop `AiPanel`, on the brand's Kale-600 ground.
 *
 * The whole surface is teal and the type is white: the wordmark and its actions across the top, a
 * provider strip under it, the conversation on the teal itself (user turns in white bubbles, the
 * assistant's in Kale-200 ones leaning the other way), and a single composer at the foot.
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
    /**
     * Opens the note the assistant last created, for the "Open note" action on an applied card.
     * Null in the all-goals chat, which has no goal to hang a resource on.
     */
    onOpenNote: (() -> Unit)? = null,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val provider by viewModel.provider.collectAsStateWithLifecycle()
    val keys by viewModel.keys.collectAsStateWithLifecycle()
    val needsKey by viewModel.needsKey.collectAsStateWithLifecycle()
    val remaining by viewModel.remainingSeconds.collectAsStateWithLifecycle()
    val totalMinutes by viewModel.sessionMinutes.collectAsStateWithLifecycle()
    val composerDraft by viewModel.composerDraft.collectAsStateWithLifecycle()
    val composerAttachments by viewModel.composerAttachments.collectAsStateWithLifecycle()
    val memoryDraft by viewModel.memoryDraft.collectAsStateWithLifecycle()
    val revisingMemory by viewModel.revisingMemory.collectAsStateWithLifecycle()

    // A pending proposal card **is** the input: the web renders it in the footer, where the
    // composer would be, so it sits right above the keyboard instead of scrolling away up the
    // transcript. Android used to draw it inline with the message, which meant a card could be
    // off-screen while its Accept button was the only thing the chat was waiting for.
    val pendingMessage = messages.firstOrNull { m ->
        m.proposals.any { it.status == ProposalStatus.PENDING }
    }

    var providerSheet by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<ChatNotice?>(null) }
    // The attachment being previewed full-screen, or null. Both the composer chips and a sent
    // message's chips open it through LocalOpenAttachment.
    var previewAttachment by remember { mutableStateOf<AiApi.ChatAttachment?>(null) }
    val listState = rememberLazyListState()
    val inGrow = mode != ChatMode.CHAT

    // Follow the answer as it streams, and land on the newest turn when one arrives.
    LaunchedEffect(messages.size, messages.lastOrNull()?.content?.length) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    CompositionLocalProvider(LocalOpenAttachment provides { previewAttachment = it }) {
    Column(
        modifier
            .fillMaxSize()
            .background(PANEL_GROUND),
    ) {
        // The chrome band: wordmark, actions and the provider strip together on Kale-600, so the
        // header reads as chrome rather than as the top of the conversation.
        //
        // It carries the drawer's own top padding, so the **rounded top corners are the band's
        // colour**. With the padding on the body instead, a 14dp sliver of the lighter ground sat
        // above the header and the curve read as an unpainted edge.
        Column(
            Modifier
                .fillMaxWidth()
                .background(PANEL_CHROME)
                // Room for the grab handle the drawer draws over this band.
                .padding(top = 14.dp),
        ) {
            PanelHeader(
                inGrow = inGrow,
                canClear = messages.isNotEmpty(),
                busy = streaming,
                remainingSeconds = remaining,
                totalMinutes = totalMinutes,
                onClose = onClose,
                // No confirmation here, deliberately (owner, 2026-08-23): the empty chat is
                // itself the feedback, and a toast for it was one more thing to dismiss.
                onNewChat = viewModel::clearChat,
                onEndSession = viewModel::closeGrow,
                // Ending early is only on offer while the session is really running: not
                // mid-stream, and not once the ending sequence has begun — it is already ending.
                canEndEarly = !streaming &&
                    (mode == ChatMode.GROW_ACTIVE || mode == ChatMode.GROW_CLOSING),
            )

            if (!inGrow) {
                ProviderStrip(
                    // The MODEL is what the user chose and what answers them — "Mistral" says only
                    // whose door it came through. Falls back to the provider when no model is
                    // stored yet, and the web strip says the same thing (`activeProvider.activeModel`).
                    label = keys.firstOrNull { it.provider.equals(provider, ignoreCase = true) }
                        ?.model?.takeIf { it.isNotBlank() }
                        ?: providerLabel(provider),
                    connected = keys.any { it.provider.equals(provider, ignoreCase = true) },
                    onOpen = { providerSheet = true },
                )
            }
        }

        if (mode == ChatMode.GROW_CLOSING) {
            Banner("The session is gently moving toward a close")
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to CHAT_GRADIENT_TOP,
                            1f to CHAT_GRADIENT_BOTTOM,
                        ),
                    ),
                ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!inGrow && messages.isEmpty()) {
                item {
                    EmptyChat(
                        goal = goal,
                        needsKey = needsKey,
                        onAddKey = { providerSheet = true },
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageRow(
                    message = message,
                    onOpenNote = onOpenNote,
                    onAcceptProposal = { proposal, excluded ->
                        val error = onApplyProposal(proposal, excluded)
                        notice = error?.let { ChatNotice(it, SpiraNoticeKind.Error) }
                        viewModel.settleProposal(message.id, proposal.id, approved = error == null)
                    },
                    onDismissProposal = { proposal ->
                        viewModel.settleProposal(message.id, proposal.id, approved = false)
                    },
                    onReviseProposal = { proposal, instruction ->
                        viewModel.reviseProposal(message.id, proposal, instruction)
                    },
                )
            }
        }

        // The GROW cards sit on the light chat bottom (not a dark block), padded clear of the nav
        // bar and scrollable, so a tall card is never clipped behind the system bar (owner, 2026-08-18).
        //
        // **`imePadding()` comes BEFORE the height cap and the scroll, and the order is the whole
        // fix** (owner, 2026-08-24: the Edit field was half under the keyboard). Modifiers apply
        // outside-in: put `imePadding()` last and the keyboard's height becomes padding on the
        // *scrollable content*, so the box stays exactly where it was — under the keyboard — and
        // the padding it gained is only reachable by scrolling. Put it first and the box itself is
        // lifted clear, which is what the composer has always done (see [Composer]) and why typing
        // a message worked while typing into a card did not.
        val cardHost: @Composable (@Composable () -> Unit) -> Unit = { card ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .background(CHAT_GRADIENT_BOTTOM)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) { card() }
        }
        notice?.let { shown ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(CHAT_GRADIENT_BOTTOM)
                    // The same 12dp gutter the composer card and the footer card use, so the
                    // toast is the width of the field it sits over — on any screen.
                    .padding(horizontal = 12.dp)
                    .padding(top = 4.dp),
            ) {
                ChatToast(shown) { notice = null }
            }
        }

        when {
            mode == ChatMode.GROW_START -> cardHost {
                GrowStartOverlay(
                    onStart = viewModel::startGrow,
                    onCancel = viewModel::cancelGrow,
                )
            }
            // Step 1 of the ending: the session record, shown so the user can read what would
            // be saved before deciding — and change it if it is not right.
            mode == ChatMode.GROW_END -> cardHost {
                GrowEndCard(
                    record = memoryDraft.orEmpty(),
                    revising = revisingMemory,
                    onRevise = viewModel::reviseSessionMemory,
                    onSave = {
                        viewModel.closeSession(save = true) { error ->
                            notice = if (error != null) {
                                ChatNotice(error, SpiraNoticeKind.Error)
                            } else {
                                ChatNotice("Saved to this goal.", SpiraNoticeKind.Success)
                            }
                        }
                    },
                    onDiscard = { viewModel.closeSession(save = false) },
                )
            }
            // Step 3: the goodbye is on screen. Leaving on its own would wipe it off the moment
            // it arrived, so the user closes when they have read it.
            mode == ChatMode.GROW_FAREWELL -> cardHost {
                if (streaming) {
                    Spacer(Modifier.height(12.dp))
                } else {
                    SessionStepFooter(
                        label = "Close session",
                        hint = null,
                        onClick = viewModel::leaveGrow,
                    )
                }
            }
            // The card takes the composer's place while it is waiting to be answered. Capped and
            // scrollable: a stepped card with a long note can outgrow the panel, and without this
            // its Save button ends up below the bottom edge.
            //
            // In a session this is step 2 — what the coach proposed for the goal, released once
            // the record has been decided. Everywhere else it is the ordinary chat card.
            pendingMessage != null && (!inGrow || mode == ChatMode.GROW_REVIEW) -> Box(
                Modifier
                    .fillMaxWidth()
                    // Lifted clear of the keyboard FIRST — see the note on `cardHost` above for
                    // why the order of these two matters more than it looks.
                    .imePadding()
                    .navigationBarsPadding()
                    // The composer's spot is light now (the gradient's bottom), not a dark band.
                    .background(CHAT_GRADIENT_BOTTOM)
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(top = 4.dp, bottom = 12.dp),
            ) {
                ProposalGroup(
                    proposals = pendingMessage.proposals,
                    onAccept = { proposal, excluded ->
                        val error = onApplyProposal(proposal, excluded)
                        notice = error?.let { ChatNotice(it, SpiraNoticeKind.Error) }
                        viewModel.settleProposal(
                            pendingMessage.id, proposal.id, approved = error == null,
                        )
                    },
                    onDismiss = { proposal ->
                        viewModel.settleProposal(pendingMessage.id, proposal.id, approved = false)
                    },
                    onRevise = { proposal, instruction ->
                        viewModel.reviseProposal(pendingMessage.id, proposal, instruction)
                    },
                    onOpenNote = onOpenNote,
                )
            }
            // Step 2 with nothing left to answer, or with the user choosing to stop here.
            mode == ChatMode.GROW_REVIEW -> cardHost {
                if (streaming) {
                    Spacer(Modifier.height(12.dp))
                } else {
                    SessionStepFooter(
                        label = "Finish session",
                        hint = "Anything you leave undecided stays waiting in the goal.",
                        onClick = viewModel::askForGoodbye,
                    )
                }
            }
            // The composer sits on the gradient's light bottom, not a dark band (owner, 2026-08-17).
            else -> Column(Modifier.fillMaxWidth().background(CHAT_GRADIENT_BOTTOM)) {
                // The starters ride with the composer, not with the empty state above it.
                if (!inGrow && messages.isEmpty() && !needsKey) {
                    ComposerSuggestions(goal = goal, onPick = viewModel::send)
                }
                Composer(
                    enabled = !needsKey,
                    streaming = streaming,
                    allowAttachments = !inGrow,
                    // This goal's saved resources, offered as a second attach source (BUG-030).
                    attachResources = if (!inGrow) goal?.resources ?: emptyList() else emptyList(),
                    placeholder = if (inGrow) "Answer in your own words…" else "Ask, plan, or request an action…",
                    growAction = if (!inGrow && viewModel.scopeGoalId != null) viewModel::openGrowStart else null,
                    // In a session the left slot ends it early, where Start GROW would be.
                    endAction = if (inGrow) viewModel::closeGrow else null,
                    draft = composerDraft,
                    onDraftChange = viewModel::setComposerDraft,
                    attachments = composerAttachments,
                    onAddAttachments = viewModel::addComposerAttachments,
                    onRemoveAttachment = viewModel::removeComposerAttachment,
                    onSend = { text, attachments -> viewModel.send(text, attachments) },
                    onStop = viewModel::cancelStream,
                    // A photo that couldn't be read used to say so in a raw platform toast; it is
                    // the panel's own notice now, like everything else the panel has to report.
                    onAttachError = { notice = ChatNotice(it, SpiraNoticeKind.Error) },
                )
            }
        }
    }

    } // CompositionLocalProvider

    previewAttachment?.let { att ->
        ChatAttachmentViewer(
            attachment = att,
            resources = goal?.resources ?: emptyList(),
            onClose = { previewAttachment = null },
        )
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

/**
 * The panel's ground: **Kale-500**, the brand's working primary.
 *
 * It used to be Kale-600 for the whole panel, header included, so the wordmark and the provider
 * strip floated on the same field as the conversation and the chrome had no edge of its own. The
 * two steps are both on the palette, and a darker band over a lighter body is the same idiom the
 * goal workspace uses (2026-08-13).
 */
internal val PANEL_GROUND = Kale500

/** The band behind the wordmark and the provider strip — one step darker than [PANEL_GROUND]. */
internal val PANEL_CHROME = Kale600

/**
 * The assistant bubble's fill — **Kale-200 `#E0F2F5`**, straight from the palette.
 *
 * Not white: white is the user's, and two white capsules on one gradient say nothing about who is
 * speaking. Not the gradient's own `#F2FFFF` either, which at the foot of the chat is the ground
 * itself, so a bubble in it would be invisible exactly where most messages sit.
 */
private val ASSISTANT_BUBBLE = Color(0xFFE0F2F5)

/** Dark type on the white bubbles and composer — brand-1100, the same step the web uses. */
internal val ON_WHITE = Brand1100

/**
 * The conversation sits on a **light teal gradient** (owner's design, 2026-08-15) while the header
 * stays Kale-600 chrome. So message-area text is deep-teal ink instead of the white-on-teal the
 * panel uses everywhere else. These are the chat-area ink tones; the composer/footer keep white.
 */
// Teal at the top, fading to near-white at the bottom (owner, 2026-08-17) — so the conversation
// meets a light composer rather than a dark band.
internal val CHAT_GRADIENT_TOP = Color(0xFF83D2D2)
internal val CHAT_GRADIENT_BOTTOM = Color(0xFFF2FFFF)
internal val CHAT_INK = ON_WHITE
internal val CHAT_INK_MUTED = ON_WHITE.copy(alpha = 0.62f)
/** A pill/chip that sits on the gradient — translucent white plate with dark ink. */
internal val CHAT_PILL_BG = Color.White.copy(alpha = 0.72f)

/**
 * How tall the composer's field may grow before it scrolls instead.
 *
 * 120dp is the web's 128px cap in the units this surface uses. Without it a long message pushed
 * the row of actions below the field off the bottom of the panel (owner, 2026-08-24).
 */
private val COMPOSER_MAX_HEIGHT = 120.dp

/** How long a chat toast stays before it takes itself away. */
private const val TOAST_VISIBLE_MS = 6000L

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
    canEndEarly: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The tag is centred against the wordmark, not sat on its baseline: at 27sp and 16sp a
        // shared baseline hangs the small text off the bottom of the large one. Centring reads as
        // one lockup. (Both carry lineHeight == fontSize, so their boxes are their letters and
        // centring the boxes centres what you see.)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
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
            )
        }

        if (inGrow) {
            TimerPill(remainingSeconds, totalMinutes)
            Spacer(Modifier.size(8.dp))
            val endInk = if (canEndEarly) Color.White else WHITE_35
            Row(
                Modifier
                    .clip(CircleShape)
                    .border(1.dp, if (canEndEarly) WHITE_35 else WHITE_35.copy(alpha = 0.4f), CircleShape)
                    .clickable(enabled = canEndEarly, onClick = onEndSession)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SpiraIcons.X, contentDescription = null, tint = endInk, modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(4.dp))
                Text(
                    "End",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = endInk,
                )
            }
        } else {
            // "New chat" is shaped like every other add-action in the app — a circled plus and a
            // plain label. There is no close ✕ beside it: the panel is a drawer, so it is put away
            // by swiping it down, tapping the page above it, or the back gesture.
            if (canClear) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(enabled = !busy, onClick = onNewChat)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        SpiraIcons.CirclePlus,
                        contentDescription = null,
                        tint = if (busy) WHITE_35 else WHITE_74,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.size(7.dp))
                    Text(
                        "New chat",
                        // Trimmed leading: centring the line box against the plus left the label
                        // riding high, because the box reserves descender room that "New chat"
                        // never uses. Trimming it centres the word on the glyph instead.
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both,
                            ),
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = if (busy) WHITE_35 else WHITE_74,
                    )
                }
            }
        }
    }
}

/** "Bring your own key" on the left, the live provider and its status dot on the right. */
@Composable
private fun ProviderStrip(label: String, connected: Boolean, onOpen: () -> Unit) {
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
            Icon(SpiraIcons.Key, contentDescription = null, tint = WHITE_74, modifier = Modifier.size(13.dp))
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
        // The dot and its halo — the web's `shadow-[0_0_0_3px_rgba(...)]`, which Android was
        // missing entirely. The ring is a fifth of the dot's opacity and three units wide, so the
        // mark reads as a lit indicator rather than a bare speck.
        //
        // The chat gradient's own two colours (owner, 2026-08-17): a connected key takes the teal
        // top-of-gradient step, a missing one the pale bottom step, so the state still reads and
        // the dot ties back to the conversation's palette.
        val dot = if (connected) CHAT_GRADIENT_TOP else CHAT_GRADIENT_BOTTOM
        Box(
            Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(dot.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        }
        Spacer(Modifier.size(6.dp))
        Text(
            label,
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
    // **Past the planned end the clock counts UP, prefixed.** The session is not over until the
    // coach ends it, so a frozen 0:00 would be a lie — and a raw negative would print "-1:-30".
    // The web's pill has read this way since the ending was designed (`timerLabel`).
    val overtime = seconds < 0
    val magnitude = kotlin.math.abs(seconds)
    val minutes = magnitude / 60
    val rest = magnitude % 60
    val low = overtime || (totalMinutes > 0 && seconds <= totalMinutes * 60 / 5)
    Box(
        Modifier
            .clip(CircleShape)
            .background(WHITE_10)
            .border(1.dp, if (low) Guava300 else WHITE_20, CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            "%s%d:%02d".format(if (overtime) "+" else "", minutes, rest),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            color = if (low) Guava300 else Color.White,
        )
    }
}

@Composable
private fun Banner(text: String) {
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
        // The owner's watering-can-and-sprout illustration, never the fat checkmark.
        Image(imageVector = SpiraArt.sprout(), contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

/**
 * What the panel has to tell the user after an action — an approved change that failed to save, a
 * session memory that did. It is the app's one notice card ([SpiraNoticeCard]), the same shape a
 * toast takes, so a message here and a message anywhere else read as the same thing.
 *
 * **It sits directly above the message field, and is exactly as wide as it** (owner, 2026-08-24).
 * It used to hang at the top of the conversation, full-bleed under the header, which put it as far
 * as it could get from whatever the user had just done and made it a second, wider band competing
 * with the chrome. Above the field it is next to the thing that caused it, and it inherits the
 * field's width, so on a narrow phone and a wide panel it is always the same object.
 *
 * It **times itself out** — that is what makes it a toast rather than a banner — but keeps its X,
 * because a message the user has not finished reading should not be on a stopwatch alone.
 *
 * Before that it was a translucent white-on-teal strip with no mark at all, which said something
 * had happened without saying whether it had gone well.
 */
@Composable
private fun ChatToast(notice: ChatNotice, onDismiss: () -> Unit) {
    // Keyed on the notice, so a second message restarts the clock instead of inheriting the
    // remainder of the first one's.
    LaunchedEffect(notice) {
        kotlinx.coroutines.delay(TOAST_VISIBLE_MS)
        onDismiss()
    }
    SpiraNoticeCard(
        message = notice.text,
        kind = notice.kind,
        onDismiss = onDismiss,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/**
 * One step of the ending, waiting on the user: a full-width button where the composer would be,
 * with an optional line under it.
 *
 * It is the same slot the proposal card and the record card use — the ending is a sequence of
 * things to answer, and every one of them is answered in the same place.
 */
@Composable
private fun SessionStepFooter(label: String, hint: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 12.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                hint,
                style = MaterialTheme.typography.labelMedium,
                color = CHAT_INK_MUTED,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A message the panel is showing, and which of the four kinds it is. */
private data class ChatNotice(val text: String, val kind: SpiraNoticeKind)

/**
 * The opening screen: a leaf medallion and one line of orientation.
 *
 * The starters used to live here, at the top of an empty panel, with the whole height of the
 * screen between them and the composer — so the first thing you could tap was as far as possible
 * from where you were about to type. They are [ComposerSuggestions] now, directly above the field.
 */
@Composable
private fun EmptyChat(
    goal: GoalDetail?,
    needsKey: Boolean,
    onAddKey: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            // **The watering can WITH the sprout** — the owner's original illustration, restored
            // on 2026-08-17 after a spell as the leaves alone. Image, not Icon, so its greens and
            // its white body aren't flattened to one tint.
            Image(
                imageVector = SpiraArt.sprout(),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
            )
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
            color = CHAT_INK_MUTED,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        Spacer(Modifier.height(20.dp))

        if (needsKey) {
            SuggestionButton(SpiraIcons.Key, "Add an API key to start chatting", onAddKey)
        }
    }
}

/**
 * The opening prompts, sitting **directly above the composer** — the place the eye and the thumb
 * are already at when the panel opens (the web puts them the same distance from its own field).
 *
 * They are only ever shown on an empty chat: once there is a conversation they would be answering
 * a question nobody asked.
 *
 * **And never inside a goal** (owner, 2026-08-17). On the dashboard they answer "what is this
 * for?"; inside a goal the user already knows why they opened the assistant, and a stack of
 * guesses about their own goal was in the way of typing. The web does the same (`AiPanel.tsx`).
 */
@Composable
private fun ComposerSuggestions(goal: GoalDetail?, onPick: (String) -> Unit) {
    if (goal != null) return
    val suggestions = GLOBAL_SUGGESTIONS
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { suggestion ->
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
            // A white card with a hairline and dark ink — the starters sit on the light composer
            // area now, so white-on-teal would be invisible.
            .background(Color.White)
            .border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.size(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            color = CHAT_INK,
        )
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    onAcceptProposal: (Proposal, Set<String>) -> Unit,
    onDismissProposal: (Proposal) -> Unit,
    onReviseProposal: (Proposal, String) -> Unit,
    /** Opens the note an applied NOTE proposal created — see [AiChatScreen]. */
    onOpenNote: (() -> Unit)? = null,
) {
    when {
        message.role == ChatRole.USER -> UserTurn(message)
        message.role == ChatRole.SYSTEM -> SystemPill(message.content)
        message.isError -> ErrorTurn(message.content)
        else -> AssistantTurn(
            message, onAcceptProposal, onDismissProposal, onReviseProposal, onOpenNote,
        )
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
                Icon(SpiraIcons.Pencil, contentDescription = null, tint = CHAT_INK_MUTED, modifier = Modifier.size(12.dp))
                Spacer(Modifier.size(6.dp))
                Text(
                    "Change to «$it»",
                    style = MaterialTheme.typography.labelMedium,
                    color = CHAT_INK_MUTED,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (message.attachments.isNotEmpty()) {
            val openAttachment = LocalOpenAttachment.current
            Column(horizontalAlignment = Alignment.End) {
                message.attachments.forEach { attachment ->
                    Row(
                        Modifier
                            .padding(bottom = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.85f))
                            .clickable { openAttachment(attachment) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            SpiraIcons.Eye,
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
            // Copy sits on the RIGHT, directly under the (right-aligned) bubble it belongs to
            // (owner, 2026-08-18) — not floated off to the left.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                CopyRow(message.content)
            }
        }
    }
}

/**
 * The assistant's turn — **a bubble, like the user's, in a different colour** (owner, 2026-08-17).
 *
 * It used to be prose set straight on the teal ground, which read as text floating loose in the
 * page rather than as a message from someone. It is the same capsule as [UserTurn], mirrored (the
 * squared corner is bottom-LEFT, so the two turns lean towards their own side) and filled with
 * **Kale-200** instead of white — an allowed palette step, and the one that stays legible under
 * deep-teal ink on this gradient.
 */
@Composable
private fun AssistantTurn(
    message: ChatMessage,
    onAcceptProposal: (Proposal, Set<String>) -> Unit,
    onDismissProposal: (Proposal) -> Unit,
    onReviseProposal: (Proposal, String) -> Unit,
    /** Opens the note an applied NOTE proposal created — see [AiChatScreen]. */
    onOpenNote: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        if (message.streaming && message.content.isBlank()) {
            // Waiting for the answer — the three-dot loader in the chat's own teal.
            ThinkingDots()
        }
        if (message.content.isNotBlank()) {
            Box(
                Modifier
                    .widthIn(max = 340.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                    .background(ASSISTANT_BUBBLE)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                AiMarkdown(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 23.5.sp),
                    color = CHAT_INK,
                    mutedColor = CHAT_INK_MUTED,
                )
            }
            if (!message.streaming) CopyRow(message.content)
        }

        // A pending card lives in the footer (the card IS the input). Once it is answered only a
        // compact result line stays here — the web's `ResultSummary`, not the whole card again.
        //
        // The gate is **"nothing is still pending"**, not "this one's card is in the footer"
        // (owner, 2026-08-24). Only the FIRST pending message gets the footer, so a second one —
        // which is what an Edit used to produce — fell through to `ResultSummary` and, having
        // nothing approved in it, was drawn as a muted "Dismissed" pill. The card the user had
        // just asked for was on screen, labelled as refused, with no way to answer it. The web
        // gates on the same condition (`!m.proposals.some(pr => pr.status === "pending")`).
        if (message.proposals.isNotEmpty() && !message.streaming &&
            message.proposals.none { it.status == ProposalStatus.PENDING }
        ) {
            Spacer(Modifier.height(10.dp))
            ResultSummary(message.proposals, onOpenNote)
        }
    }
}

/**
 * What is left in the transcript once a card has been answered — the web's `ResultSummary`: a
 * pill per approved change with an Open shortcut, or one muted "Dismissed" when nothing was kept.
 */
@Composable
private fun ResultSummary(proposals: List<Proposal>, onOpenNote: (() -> Unit)?) {
    val approved = proposals.filter { it.status == ProposalStatus.APPROVED }
    if (approved.isEmpty()) {
        Row(
            Modifier
                .clip(CircleShape)
                .background(CHAT_PILL_BG)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SpiraIcons.X, contentDescription = null, tint = CHAT_INK_MUTED, modifier = Modifier.size(12.dp))
            Spacer(Modifier.size(6.dp))
            Text("Dismissed", style = MaterialTheme.typography.labelMedium, color = CHAT_INK_MUTED)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        approved.forEach { proposal ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(CHAT_PILL_BG)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    SpiraIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    proposal.title.ifBlank { kindLabel(proposal.kind) },
                    style = MaterialTheme.typography.labelMedium,
                    color = CHAT_INK,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (proposal.kind == ProposalKind.NOTE && onOpenNote != null) {
                    Spacer(Modifier.size(8.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenNote)
                            .padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            SpiraIcons.SwitchArrows,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "Open",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The "waiting for the answer" loader: three dots fading in sequence, in the chat's deep teal
 * (owner, 2026-08-17). Shown while a reply is streaming but has produced no text yet.
 */
@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val spec: @Composable (Int) -> Float = { offsetMs ->
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(offsetMs),
            ),
            label = "dot",
        ).value
    }
    val a0 = spec(0)
    val a1 = spec(200)
    val a2 = spec(400)
    Row(
        Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(a0, a1, a2).forEach { a ->
            Box(Modifier.size(7.dp).clip(CircleShape).background(Kale600.copy(alpha = a)))
        }
    }
}

/** An error reads as a warning line on the ground, never as a bubble. */
@Composable
private fun ErrorTurn(text: String) {
    // A bright yellow from the warning ramp — Warning-500 (#C99500), clearly yellow (not the brown
    // #6B4E00) and legible on the light chat area (owner, 2026-08-18).
    val warnInk = Color(0xFFC99500)
    Row(Modifier.widthIn(max = 340.dp)) {
        Icon(
            SpiraIcons.TriangleAlert,
            contentDescription = null,
            tint = warnInk,
            modifier = Modifier.padding(top = 3.dp).size(15.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = warnInk,
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
                .background(CHAT_PILL_BG)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SpiraIcons.Check, contentDescription = null, tint = CHAT_INK_MUTED, modifier = Modifier.size(12.dp))
            Spacer(Modifier.size(8.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = CHAT_INK_MUTED)
        }
    }
}

@Composable
private fun CopyRow(text: String) {
    val context = LocalContext.current
    // The shared flash, so this row says "Copied" for the same couple of seconds as every other
    // copy button in the app. It used to latch: once pressed it read "Copied" for the rest of the
    // conversation, which stops being about the press that just happened.
    val flash = rememberCopyFlash()
    Row(
        Modifier.padding(top = 3.dp).clickable {
            copyPlainText(context, "spira ai coach", text)
            flash.fire()
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            flash.icon,
            contentDescription = "Copy message",
            tint = CHAT_INK_MUTED,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.size(5.dp))
        Text(
            if (flash.copied) "Copied" else "Copy",
            style = MaterialTheme.typography.labelSmall,
            color = CHAT_INK_MUTED,
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
    attachResources: List<ResourceItem>,
    placeholder: String,
    growAction: (() -> Unit)?,
    /** Ends a live GROW session — shown in the left slot, in place of [growAction], while in one. */
    endAction: (() -> Unit)? = null,
    // Draft and attachments are hoisted to the ViewModel so the camera recreating this activity
    // does not drop them — see AiChatViewModel.composerAttachments.
    draft: String,
    onDraftChange: (String) -> Unit,
    attachments: List<AiApi.ChatAttachment>,
    onAddAttachments: (List<AiApi.ChatAttachment>) -> Unit,
    onRemoveAttachment: (AiApi.ChatAttachment) -> Unit,
    onSend: (String, List<AiApi.ChatAttachment>) -> Unit,
    onStop: () -> Unit,
    /** An attachment that couldn't be read — reported by the panel, not by a platform toast. */
    onAttachError: (String) -> Unit = {},
) {
    var attachMenu by remember { mutableStateOf(false) }
    var showResourcePicker by remember { mutableStateOf(false) }
    val picker = rememberChatAttachmentPicker(
        onPicked = { picked -> onAddAttachments(picked) },
        onError = onAttachError,
    )
    val hasResources = attachResources.isNotEmpty()

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = 12.dp),
    ) {
        Column(
            // A white card, like every message bubble — the field belongs in a container, it just
            // sits on the light chat background now rather than on a dark block (owner, 2026-08-18).
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp)
                .padding(top = 10.dp, bottom = 8.dp),
        ) {
            if (attachments.isNotEmpty()) {
                val openAttachment = LocalOpenAttachment.current
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
                            // Tapping the chip (its eye + name) previews it; the X stays a separate
                            // target so a preview-tap can't remove it by mistake.
                            Row(
                                Modifier
                                    .weight(1f, fill = false)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { openAttachment(attachment) }
                                    .padding(vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    SpiraIcons.Eye,
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
                            Spacer(Modifier.size(4.dp))
                            Icon(
                                SpiraIcons.X,
                                contentDescription = "Remove ${attachment.name}",
                                tint = ON_WHITE.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onRemoveAttachment(attachment) },
                            )
                        }
                    }
                }
            }

            // A plain BasicTextField, NOT the shared InlineEditText.
            //
            // InlineEditText keeps its own copy of the text and re-seeds it from `value` only
            // while the field is **unfocused** — the guard that stops a background refetch wiping
            // what someone is halfway through typing. A chat composer keeps focus when you press
            // Send, so `draft = ""` never reached the field and the message you had just sent sat
            // there waiting to be sent again (GRO-80). Here `draft` IS the field's state, so
            // clearing it clears what you see.
            //
            // It also has no business committing on blur: this field is sent explicitly, and
            // tapping away from it must not do anything at all.
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    color = ON_WHITE,
                ),
                cursorBrush = SolidColor(ON_WHITE),
                // **Capped, and it scrolls itself — following the caret.**
                //
                // Without a ceiling the field grows with the text and pushes the row below it —
                // paperclip, "End session early", Send — off the bottom of the panel, so a long
                // message leaves nothing to press (owner, 2026-08-24). The web has capped this at
                // 128px from the start (`Math.min(el.scrollHeight, 128)` in `AiPanel.tsx`).
                //
                // The cap was first written as `heightIn(...).verticalScroll(...)`, and that
                // **broke typing**: an outer scroll container measures the field unbounded, so
                // `BasicTextField` hands its own scrolling over to the parent — and the parent has
                // no idea where the caret is. Past four lines you were typing into text you could
                // not see, and had to drag the field to find your own cursor (owner, 2026-08-25).
                //
                // Constraining the height on the field **itself**, with no scroll wrapper, is what
                // turns its internal scroller back on, and that one keeps the cursor in view as
                // you type. Do not put a `verticalScroll` back around this.
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = COMPOSER_MAX_HEIGHT)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                decorationBox = { field ->
                    Box {
                        if (draft.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.5.sp,
                                    lineHeight = 21.sp,
                                ),
                                color = ON_WHITE.copy(alpha = 0.45f),
                            )
                        }
                        field()
                    }
                },
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (allowAttachments) {
                    // The paperclip asks WHERE from rather than going straight to the file
                    // chooser: on a phone the camera is the fastest way to put a document in
                    // front of the assistant, and it used to be unreachable from here.
                    Box {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = enabled && attachments.size < ATTACH_MAX_COUNT) {
                                    attachMenu = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                SpiraIcons.Paperclip,
                                contentDescription = "Attach a file",
                                tint = if (enabled) ON_WHITE else ON_WHITE.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        SpiraDropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                            SpiraMenuItem(
                                label = "Take a photo",
                                icon = SpiraIcons.Camera,
                                onClick = { attachMenu = false; picker.takePhoto() },
                            )
                            SpiraMenuItem(
                                label = "Choose a file",
                                icon = SpiraIcons.Paperclip,
                                onClick = { attachMenu = false; picker.pickFile() },
                            )
                            // The third source (BUG-030): the goal's own saved resources, inlined
                            // by the server. Only offered when the goal actually has some.
                            if (hasResources) {
                                SpiraMenuItem(
                                    label = "From resources",
                                    icon = SpiraIcons.FolderOpen,
                                    onClick = { attachMenu = false; showResourcePicker = true },
                                )
                            }
                        }
                    }
                }
                // One left slot: starting a session when out of one, ending it early when in one —
                // ending is now where starting was, rather than a separate link above (owner,
                // 2026-08-17). Kale-500, the working primary: the user is acting, not the assistant.
                val left = when {
                    endAction != null -> Triple(SpiraIcons.X, "End session early", endAction)
                    growAction != null -> Triple(SpiraIcons.SparklesFilled, "Start GROW session", growAction)
                    else -> null
                }
                left?.let { (icon, label, onClick) ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(SpiraRadii.md))
                            .clickable(onClick = onClick)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = Kale500,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            label,
                            style = addActionTextStyle(),
                            fontWeight = FontWeight.Medium,
                            color = Kale500,
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
                                // The ViewModel clears the draft and chips once the send starts.
                                onSend(draft, attachments)
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

    if (showResourcePicker) {
        ResourcePickerSheet(
            resources = attachResources,
            alreadyAttached = attachments.mapNotNull { it.resourceId }.toSet(),
            onPick = { res ->
                val id = res.id.toLongOrNull()
                if (id != null) {
                    onAddAttachments(
                        listOf(
                            AiApi.ChatAttachment(
                                name = resourceDisplayName(res),
                                mime = res.mime ?: "",
                                resourceId = id,
                            ),
                        ),
                    )
                }
                showResourcePicker = false
            },
            onDismiss = { showResourcePicker = false },
        )
    }
}

/** A resource's user-facing name: the title, or the contact's name for an email card. */
private fun resourceDisplayName(res: ResourceItem): String =
    (if (res.type == "email") res.name else res.title)?.takeIf { it.isNotBlank() } ?: "Resource"

/**
 * Picks one of the goal's saved resources to attach (BUG-030). No bytes leave the phone — the chip
 * carries the resource id and the server inlines what it already holds. A search narrows a long
 * list; an already-attached resource is shown disabled so it can't be added twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResourcePickerSheet(
    resources: List<ResourceItem>,
    alreadyAttached: Set<Long>,
    onPick: (ResourceItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The standard sheet shell, same as every form and filter sheet (CLAUDE.md, Design 3e):
    // white card, 12dp top corners, NO drag handle, and the Kale band as its head. This one
    // was the last sheet still wearing a white head with a dark title and Material's grey
    // handle above it, so opening it looked like leaving the app (owner, 2026-08-23).
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = SpiraRadii.lg, topEnd = SpiraRadii.lg),
    ) {
        ResourcePickerSheetContent(resources, alreadyAttached, onPick, onDismiss)
    }
}

/**
 * The picker's card, without the [ModalBottomSheet] around it.
 *
 * Split out for the reason `SpiraFormSheetContent` is: a modal sheet renders in its **own
 * window**, which the `VisualCheck*` helper (it draws the activity's decor view) cannot
 * capture - so an open sheet is simply absent from the PNG, and the check that would have
 * caught this sheet's white head silently checked nothing.
 */
@Composable
fun ResourcePickerSheetContent(
    resources: List<ResourceItem>,
    alreadyAttached: Set<Long>,
    onPick: (ResourceItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = resources.filter {
        resourceDisplayName(it).contains(query.trim(), ignoreCase = true)
    }
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        SpiraSheetHead("Attach a resource", onDismiss)
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 24.dp)) {
            SpiraTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search resources",
            )
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Text(
                    "No matching resources.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.spiraExtras.mutedForeground,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                ) {
                    filtered.forEach { res ->
                        val id = res.id.toLongOrNull()
                        val taken = id != null && id in alreadyAttached
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !taken) { onPick(res) }
                                .padding(horizontal = 4.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                resourceTypeIcon(res.type),
                                contentDescription = null,
                                tint = if (taken) MaterialTheme.spiraExtras.mutedForeground
                                else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                resourceDisplayName(res),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (taken) MaterialTheme.spiraExtras.mutedForeground
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (taken) "Added" else resourceTypeLabel(res.type),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.spiraExtras.mutedForeground,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The kit glyph for a resource type, matching the inline-resource chips. */
private fun resourceTypeIcon(type: String) = when (type) {
    "note" -> SpiraIcons.FileText
    "link" -> SpiraIcons.Link
    "file" -> SpiraIcons.Paperclip
    else -> SpiraIcons.Mail
}

private fun resourceTypeLabel(type: String) = when (type) {
    "note" -> "Note"
    "link" -> "Link"
    "file" -> "File"
    else -> "Email"
}

/** Choosing a session length, as the desktop's start overlay does. */
@Composable
private fun GrowStartOverlay(onStart: (Int) -> Unit, onCancel: () -> Unit) {
    Column(
        // A real white card on the light chat area — not a translucent panel on a dark block.
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                imageVector = SpiraArt.sprout(),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "Start a GROW session",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = ON_WHITE,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "A timed conversation through Goal, Reality, Options and Will.",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            color = ON_WHITE.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(14.dp))
        // The web's four lengths (15 / 30 / 45 / 60), tap-to-start, as bordered tiles.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60).forEach { minutes ->
                Column(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(10.dp))
                        .clickable { onStart(minutes) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "$minutes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ON_WHITE,
                    )
                    Text(
                        "min",
                        style = MaterialTheme.typography.labelSmall,
                        color = ON_WHITE.copy(alpha = 0.6f),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Cancel",
            style = MaterialTheme.typography.labelMedium,
            color = ON_WHITE.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(onClick = onCancel)
                .padding(vertical = 4.dp),
        )
    }
}

/** The closing card: keep what the session worked out on the goal, or let it go. */
@Composable
private fun GrowEndCard(
    record: String,
    revising: Boolean,
    onRevise: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    var instruction by remember { mutableStateOf("") }
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
        Spacer(Modifier.height(12.dp))

        // **The record itself.** The card used to show two buttons and no text, and what it saved
        // was the coach's last chat message — which by then was the goodbye. So the memory kept a
        // farewell instead of a record, and the user could not see either (owner, 2026-08-24).
        //
        // It **gives up height while the keyboard is open**. The card is taller than what is left
        // of the panel then, and although it scrolls, Save memory and Discard ended up half under
        // the keys — reachable only by scrolling past the thing you were reading. Shrinking the
        // preview is the right thing to yield: it is the one part of this card that is already
        // scrollable in its own right.
        val keyboardUp = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = if (keyboardUp) 96.dp else 200.dp)
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(10.dp))
                .background(CHAT_PILL_BG)
                .padding(12.dp),
        ) {
            AiMarkdown(
                text = record.ifBlank { "The coach ended the session without a record." },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                color = CHAT_INK,
                mutedColor = CHAT_INK_MUTED,
            )
        }
        Spacer(Modifier.height(10.dp))

        if (revising) {
            Text(
                "Rewriting…",
                style = MaterialTheme.typography.labelMedium,
                color = ON_WHITE.copy(alpha = 0.6f),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        color = ON_WHITE,
                    ),
                    cursorBrush = SolidColor(ON_WHITE),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { field ->
                        Box {
                            if (instruction.isEmpty()) {
                                Text(
                                    "Change the record…",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = ON_WHITE.copy(alpha = 0.45f),
                                )
                            }
                            field()
                        }
                    },
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Send",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (instruction.isBlank()) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(enabled = instruction.isNotBlank()) {
                            onRevise(instruction)
                            instruction = ""
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ON_WHITE)
                    .clickable(enabled = !revising) { onSave() },
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

/**
 * Opens the tapped chat attachment full-screen. Provided by [AiChatScreen]; consumed by a sent
 * message's chips and the composer's chips, so both can preview without threading a callback
 * through every message row.
 */
val LocalOpenAttachment = androidx.compose.runtime.staticCompositionLocalOf<(AiApi.ChatAttachment) -> Unit> { {} }

/**
 * A full-screen viewer for an attachment (BUG-030). It reads what to show from the attachment
 * itself for a device photo, or from the goal's own [resources] for a resource attachment — the
 * chip only carries a name and an id, never the bytes.
 *
 * Images (a device photo, or an image file resource) show on black like the web's preview. A note
 * shows its text; a link opens in the browser; a PDF/other file opens in whatever app handles it.
 * Anything that can't be shown says so rather than doing nothing.
 */
@Composable
private fun ChatAttachmentViewer(
    attachment: AiApi.ChatAttachment,
    resources: List<com.spiramindscape.android.data.goals.ResourceItem>,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val res = attachment.resourceId?.let { id ->
        resources.firstOrNull { it.id.toLongOrNull() == id }
    }

    val imageDataUrl: String? = when {
        attachment.resourceId == null && attachment.mime.startsWith("image/") -> attachment.dataUrl
        res != null && res.type == "file" && (res.mime ?: "").startsWith("image/") -> res.dataUrl
        else -> null
    }
    // An image is a lightbox (black ground); everything else — a note above all — is a light page,
    // so a note reads like the note editor's own preview rather than white text on black (owner,
    // 2026-08-17).
    val isImage = imageDataUrl != null

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ChatAttachmentViewerContent(attachment, resources, onClose)
    }
}

/**
 * The viewer's page, without the [androidx.compose.ui.window.Dialog] around it.
 *
 * Split out for the reason `SpiraFormSheetContent` and `ResourcePickerSheetContent` are: a
 * Dialog renders in its **own window**, which the `VisualCheck*` helper (it draws the
 * activity's decor view) cannot capture — so a test of the wrapper would quietly assert
 * against an empty screenshot.
 */
@Composable
fun ChatAttachmentViewerContent(
    attachment: AiApi.ChatAttachment,
    resources: List<com.spiramindscape.android.data.goals.ResourceItem>,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val res = attachment.resourceId?.let { id ->
        resources.firstOrNull { it.id.toLongOrNull() == id }
    }
    val imageDataUrl: String? = when {
        attachment.resourceId == null && attachment.mime.startsWith("image/") -> attachment.dataUrl
        res != null && res.type == "file" && (res.mime ?: "").startsWith("image/") -> res.dataUrl
        else -> null
    }

    val title = when {
        res != null && res.type == "email" -> res.name?.takeIf { it.isNotBlank() } ?: "Contact"
        res != null -> res.title?.takeIf { it.isNotBlank() } ?: attachment.name
        else -> attachment.name
    }

    // A CARD on a dimmed ground, not a full-screen page (owner, 2026-08-23). It used to open
    // as a bare `fillMaxSize` with a plain background and the text floating in the middle of
    // it, which read as leaving the app rather than looking at something inside it. The shape
    // here is the web's `ContentModal` and the same one a proposal uses to show a note's full
    // body before you approve it: dimmed backdrop, rounded card, a title row, and the content
    // in its own scrollable block.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x73003737))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                // Swallow taps on the card itself, or every click would dismiss it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    SpiraIcons.X,
                    contentDescription = "Close",
                    tint = MaterialTheme.spiraExtras.mutedForeground,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
            HorizontalDivider(color = MaterialTheme.spiraExtras.border)

            Box(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                when {
                    imageDataUrl != null -> {
                        val bitmap = remember(imageDataUrl) {
                            decodeDataUrl(imageDataUrl)?.let { bytes ->
                                android.graphics.BitmapFactory
                                    .decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = attachment.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            )
                        } else {
                            ViewerBlock { Text("This image could not be shown.") }
                        }
                    }

                    res != null && res.type == "note" -> {
                        val html = res.body ?: ""
                        ViewerBlock {
                            Text(
                                if (looksLikeHtml(html)) rememberHtmlText(html)
                                else androidx.compose.ui.text.AnnotatedString(
                                    html.ifBlank { "(empty note)" },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    res != null && res.type == "link" -> {
                        Column {
                            ViewerBlock {
                                Text(
                                    res.url ?: "(no URL)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            SpiraButton("Open link", { openAttachmentUri(context, res.url) })
                        }
                    }

                    res != null && res.type == "email" -> {
                        ViewerBlock {
                            Text(
                                listOfNotNull(res.role, res.email, res.phone)
                                    .joinToString("\n").ifBlank { "(no contact details)" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    else -> ViewerBlock {
                        Text(
                            "This attachment can't be previewed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.spiraExtras.mutedForeground,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The content's own block inside the viewer card — the shape a proposal uses to show a note's
 * full body before it is approved (`BodySheet` in `ProposalCard.kt`): a sunken, rounded,
 * scrollable panel with a ceiling on its height, so a long note cannot push the card past the
 * screen.
 */
@Composable
private fun ViewerBlock(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.spiraExtras.surfaceSunken)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
    ) { content() }
}

/**
 * A centered message with an optional single action. [onDark] is for the image lightbox's black
 * ground (white text); the light text previews (link / email / file) pass `false` so the message is
 * dark ink on the light page.
 */
@Composable
private fun ViewerMessage(
    text: String,
    actionLabel: String? = null,
    onDark: Boolean = true,
    onAction: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                actionLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (onDark) Kale300 else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Open a URL (a link resource) in the browser. */
private fun openAttachmentUri(context: android.content.Context, uri: String?) {
    if (uri.isNullOrBlank()) return
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri)),
        )
    }
}

/**
 * Write a data: URL to a cache file and open it with whatever app handles the type (a PDF viewer).
 * Silent on failure — the viewer already showed the name, and there is nothing lost.
 */
private fun openDataUrlExternally(
    context: android.content.Context,
    name: String,
    mime: String?,
    dataUrl: String?,
) {
    val bytes = decodeDataUrl(dataUrl) ?: return
    runCatching {
        val dir = java.io.File(context.cacheDir, "attachments").apply { mkdirs() }
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "file" }
        val file = java.io.File(dir, safe)
        file.writeBytes(bytes)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime ?: "application/octet-stream")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
