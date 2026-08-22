package com.spiramindscape.android.ui.goals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.data.goals.ApolloGoalsRepository
import com.spiramindscape.android.data.goals.ConfidenceHistoryEntry
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.GoalSummary
import com.spiramindscape.android.data.goals.GoalsStore
import com.spiramindscape.android.data.goals.ResourceItem
import com.spiramindscape.android.data.goals.TargetItem
import com.spiramindscape.android.data.net.Network
import kotlinx.coroutines.launch
import com.spiramindscape.android.ui.ai.WithAiAssistant
import com.spiramindscape.android.ui.components.CelebrationOverlay
import com.spiramindscape.android.ui.components.GoalWorkspaceBottomBar
import com.spiramindscape.android.ui.components.GoalWorkspaceTopBar
import com.spiramindscape.android.ui.components.GrowTabsRow
import com.spiramindscape.android.ui.components.SpiraInlineBanner
import com.spiramindscape.android.ui.components.ConfidenceStepper
import com.spiramindscape.android.ui.components.ConfirmDialog
import com.spiramindscape.android.ui.components.DeadlineLinkField
import com.spiramindscape.android.ui.components.ElementActionsMenu
import com.spiramindscape.android.ui.components.EmptyLine
import com.spiramindscape.android.ui.components.FieldLabel
import com.spiramindscape.android.ui.components.InlineEditText
import com.spiramindscape.android.ui.components.InlineRichText
import com.spiramindscape.android.ui.components.InlineResourcesValue
import com.spiramindscape.android.ui.components.attachTo
import com.spiramindscape.android.ui.components.ProvideInlineResources
import com.spiramindscape.android.ui.components.SectionLabel
import com.spiramindscape.android.ui.components.SpiraButton
import com.spiramindscape.android.ui.components.SpiraButtonVariant
import com.spiramindscape.android.ui.components.SpiraAddButton
import com.spiramindscape.android.ui.components.SpiraChoice
import com.spiramindscape.android.ui.components.SpiraBadgeTone
import com.spiramindscape.android.ui.components.SpiraFilterTrigger
import com.spiramindscape.android.ui.components.SpiraNoticeCard
import com.spiramindscape.android.ui.components.SpiraNoticeKind
import com.spiramindscape.android.ui.components.SpiraSheetDateRange
import com.spiramindscape.android.ui.components.SpiraSheetGroup
import com.spiramindscape.android.ui.components.SpiraSheetPills
import com.spiramindscape.android.ui.components.SpiraListToolbar
import com.spiramindscape.android.ui.components.SpiraMenuChoice
import com.spiramindscape.android.ui.components.SpiraMenuColumns
import com.spiramindscape.android.ui.components.SpiraMenuGroup
import com.spiramindscape.android.ui.components.SpiraSortTrigger
import com.spiramindscape.android.ui.components.SpiraDropdownMenu
import com.spiramindscape.android.ui.components.SpiraMenuDivider
import com.spiramindscape.android.ui.components.SpiraMenuItem
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.SpiraRadii
import com.spiramindscape.android.ui.theme.spiraExtras
import com.spiramindscape.android.ui.util.FieldLimits
import com.spiramindscape.android.ui.util.deadlineCountdownParts
import com.spiramindscape.android.ui.util.formatPercent
import com.spiramindscape.android.ui.util.goalProgressSteps
import kotlin.math.roundToInt

/**
 * Shape/border language for "hero" buttons — the Reality Actions/Obstacles toggle and the
 * Deadline Remove button share this look (thick border). Corners match the web's `rounded-md`
 * (6px) buttons — nearly square, not pill-shaped.
 */
private val HeroButtonShape = RoundedCornerShape(SpiraRadii.sm)
private val HeroButtonBorderWidth = 2.dp

/** All the edit actions the workspace can invoke. Defaults let tests pass a subset. */
data class GoalWorkspaceActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSetGoalTitle: (String) -> Unit = {},
    val onSetGoalDescription: (String) -> Unit = {},
    val onSetConfidence: (Int) -> Unit = {},
    val onSetDeadline: (String?) -> Unit = {},
    val onDeleteGoal: () -> Unit = {},
    val onSetTargetDone: (targetId: String, done: Boolean) -> Unit = { _, _ -> },
    val onSetNumeric: (targetId: String, current: Double) -> Unit = { _, _ -> },
    /** Current / total / start together — the card edits all three in place (null = unchanged). */
    val onSetTargetNumbers: (
        targetId: String, current: Double?, total: Double?, start: Double?,
    ) -> Unit = { _, _, _, _ -> },
    val onSetTargetUnit: (targetId: String, unit: String?) -> Unit = { _, _ -> },
    val onToggleChecklistItem: (targetId: String, itemId: String) -> Unit = { _, _ -> },
    val onAddChecklistTask: (targetId: String, text: String) -> Unit = { _, _ -> },
    val onUpdateChecklistTask: (targetId: String, itemId: String, text: String) -> Unit = { _, _, _ -> },
    val onRemoveChecklistTask: (targetId: String, itemId: String) -> Unit = { _, _ -> },
    val onSetChecklistTaskDeadline: (
        targetId: String, itemId: String, deadline: String?,
    ) -> Unit = { _, _, _ -> },
    val onSetTargetTitle: (targetId: String, title: String) -> Unit = { _, _ -> },
    val onSetTargetDeadline: (targetId: String, deadline: String?) -> Unit = { _, _ -> },
    val onSetTargetProgressLocked: (targetId: String, locked: Boolean) -> Unit = { _, _ -> },
    val onAddTarget: (
        title: String, type: String, deadline: String?,
        start: Double?, total: Double?, unit: String?, checklist: List<String>,
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    val onDeleteTarget: (targetId: String) -> Unit = {},
    val onAddReality: (kind: String, text: String) -> Unit = { _, _ -> },
    val onUpdateReality: (kind: String, itemId: String, text: String) -> Unit = { _, _, _ -> },
    val onRemoveReality: (kind: String, itemId: String) -> Unit = { _, _ -> },
    val onAddOption: (text: String) -> Unit = {},
    val onSetOptionText: (optionId: String, text: String) -> Unit = { _, _ -> },
    /** The smiley badge's thumb lean: "none" | "good_idea" | "didnt_work". */
    val onSetOptionStatus: (optionId: String, status: String) -> Unit = { _, _ -> },
    val onSelectOption: (optionId: String) -> Unit = {},
    val onDeselectOption: (optionId: String) -> Unit = {},
    val onRemoveOption: (optionId: String) -> Unit = {},
    val onReorderOption: (optionId: String, toPosition: Int) -> Unit = { _, _ -> },
    val onAddResource: (
        type: String, title: String?, body: String?, url: String?,
        name: String?, email: String?, role: String?, phone: String?,
        mime: String?, dataUrl: String?,
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    val onUpdateResource: (
        id: String, title: String?, body: String?, url: String?,
        name: String?, email: String?, role: String?, phone: String?,
        mime: String?, dataUrl: String?,
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    val onRemoveResource: (id: String) -> Unit = {},
    /** Ask for a file resource's bytes (omitted from the goal query — see `GetGoal.graphql`). */
    val onLoadResourceFile: (id: String) -> Unit = {},
)

@Composable
fun GoalWorkspaceRoute(
    goalId: String,
    user: AuthUser,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenGoal: (String) -> Unit,
) {
    val viewModel: GoalWorkspaceViewModel = viewModel(
        factory = GoalWorkspaceViewModel.factory(goalId, ApolloGoalsRepository(Network.apollo)),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val allGoals by GoalsStore.goals.collectAsStateWithLifecycle()
    // The note the assistant last created, so its card can offer to open it.
    val lastCreatedNote by viewModel.lastCreatedNote.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    LifecycleResumeEffect(Unit) {
        // Silent refetch on resume (no spinner) so returning to the goal doesn't flash the loader.
        if (state is GoalUiState.Content) viewModel.refresh()
        onPauseOrDispose { }
    }

    // The assistant, scoped to this goal, swiped in from the right edge.
    var assistantOpen by remember { mutableStateOf(false) }
    val workspaceActions = GoalWorkspaceActions(
        onBack = onBack,
        onRetry = viewModel::load,
        onSetGoalTitle = viewModel::setGoalTitle,
        onSetGoalDescription = viewModel::setGoalDescription,
        onSetConfidence = viewModel::setConfidence,
        onSetDeadline = viewModel::setDeadline,
        onDeleteGoal = { viewModel.deleteGoal(onDeleted = onBack) },
        onSetTargetDone = viewModel::setTargetDone,
        onSetNumeric = viewModel::setNumericCurrent,
        onSetTargetNumbers = viewModel::setTargetNumbers,
        onSetTargetUnit = viewModel::setTargetUnit,
        onToggleChecklistItem = viewModel::toggleChecklistItem,
        onAddChecklistTask = viewModel::addChecklistTask,
        onUpdateChecklistTask = viewModel::updateChecklistTask,
        onRemoveChecklistTask = viewModel::removeChecklistTask,
        onSetChecklistTaskDeadline = viewModel::setChecklistTaskDeadline,
        onSetTargetTitle = viewModel::setTargetTitle,
        onSetTargetDeadline = viewModel::setTargetDeadline,
        onSetTargetProgressLocked = viewModel::setTargetProgressLocked,
        onAddTarget = viewModel::addTarget,
        onDeleteTarget = viewModel::deleteTarget,
        onAddReality = viewModel::addReality,
        onUpdateReality = viewModel::updateReality,
        onRemoveReality = viewModel::removeReality,
        onAddOption = viewModel::addOption,
        onSetOptionText = viewModel::setOptionText,
        onSetOptionStatus = viewModel::setOptionStatus,
        onSelectOption = viewModel::selectOption,
        onDeselectOption = viewModel::deselectOption,
        onRemoveOption = viewModel::removeOption,
        onReorderOption = viewModel::reorderOptions,
        onAddResource = viewModel::addResource,
        onUpdateResource = { id, title, body, url, name, email, role, phone, mime, dataUrl ->
            viewModel.updateResource(id, title, body, url, name, email, role, phone, mime, dataUrl)
        },
        onRemoveResource = viewModel::removeResource,
        onLoadResourceFile = viewModel::loadResourceFile,
    )

    WithAiAssistant(
        goalId = goalId,
        open = assistantOpen,
        onOpenChange = { assistantOpen = it },
        onApplyProposal = { proposal, excluded ->
            val goal = (state as? GoalUiState.Content)?.goal
            if (goal == null) {
                "The goal is still loading — try again in a moment."
            } else {
                applyProposalToGoal(proposal, excluded, goal, workspaceActions)
            }
        },
        goal = (state as? GoalUiState.Content)?.goal,
        // "Open note" on an applied card. Offered only once the note actually exists — the
        // proposal is applied optimistically, so the row it created only has an id after the
        // refetch lands, and a link that opened an empty editor would be worse than no link.
        onOpenNote = lastCreatedNote?.let { note ->
            {
                context.startActivity(
                    NoteEditorActivity.intent(context, note.id, note.title.orEmpty(), note.body.orEmpty()),
                )
            }
        },
    ) { swipeUpGesture ->
        GoalWorkspaceScreen(
            state = state,
            user = user,
            allGoals = allGoals,
            onLogout = onLogout,
            onOpenGoal = onOpenGoal,
            onOpenAssistant = { assistantOpen = true },
            assistantSwipeUpGesture = swipeUpGesture,
            actions = workspaceActions,
            actionError = actionError,
            onDismissActionError = viewModel::clearActionError,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalWorkspaceScreen(
    state: GoalUiState,
    actions: GoalWorkspaceActions,
    user: AuthUser,
    allGoals: List<GoalSummary> = emptyList(),
    onLogout: () -> Unit = {},
    onOpenGoal: (String) -> Unit = {},
    onOpenAssistant: () -> Unit = {},
    /** Attached to the footer so swiping up there opens the assistant (see `AiChatHost`). */
    assistantSwipeUpGesture: Modifier = Modifier,
    /** An action that failed without changing the screen (e.g. a delete that didn't land). */
    actionError: String? = null,
    onDismissActionError: () -> Unit = {},
) {
    var confirmDeleteGoal by remember { mutableStateOf(false) }
    // The header's goal switcher. It is local to this screen and starts empty on every visit —
    // a search typed on the All-goals dashboard must never carry into an opened goal.
    var searchQuery by remember { mutableStateOf("") }
    // Resources is a page of its own (reached from the footer), not one of the GROW phases and
    // not a drawer.
    var resourcesOpen by remember { mutableStateOf(false) }
    // Tabs switch either by tapping the GROW tab bar or by swiping the pager; both drive/read
    // the same pagerState so they always agree on which tab is showing.
    val pagerState = rememberPagerState(pageCount = { GoalTab.entries.size })
    // Switching tabs clears focus off any inline field being edited — this commits the pending edit
    // AND stops the text caret's blink (otherwise a field left focused keeps blinking forever).
    val rootFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(pagerState.currentPage) { rootFocusManager.clearFocus() }
    // Sort/filter in this header apply to the TARGETS tab.
    // The chosen sort/filter is remembered across sessions (web parity: the filters that persist
    // in localStorage), so a user who only ever looks at open targets doesn't re-pick every visit.
    val targetView = rememberTargetViewState()
    // Screen-local searches: they start empty on every visit to the goal (CLAUDE.md: a search
    // typed on one screen must never follow the user onto the next) but survive a tab swipe.
    var targetsQuery by remember { mutableStateOf("") }
    var optionsQuery by remember { mutableStateOf("") }
    var resourcesQuery by remember { mutableStateOf("") }
    var showNewResourceSheet by remember { mutableStateOf(false) }
    // Hosted here, not inside the tab content: the round add button lives in this
    // Scaffold, so the flags it sets have to live beside it.
    var showNewTarget by remember { mutableStateOf(false) }
    var showNewOption by remember { mutableStateOf(false) }
    var showNewRealitySheet by remember { mutableStateOf(false) }
    // Which Reality list ("actions"/"obstacles") is shown — hoisted so the tab's "+" FAB knows
    // which kind to add to.
    var realityKind by remember { mutableStateOf("actions") }
    // The resource (by id) currently shown full-screen (note editor / file viewer), or null.
    var fullScreenResourceId by remember { mutableStateOf<String?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // The Resources page is a step "inside" the workspace, so back leaves it rather than the goal.
    BackHandler(enabled = resourcesOpen) { resourcesOpen = false }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SpiraDrawer(
                user = user,
                onLogout = onLogout,
                goalTitle = (state as? GoalUiState.Content)?.goal?.title,
                // Which place the drawer should mark — the pager's page, or Resources over it.
                currentPlace = if (resourcesOpen) GoalTab.entries.size else pagerState.currentPage,
                onClose = { scope.launch { drawerState.close() } },
                onHome = { scope.launch { drawerState.close() }; actions.onBack() },
                onGoalPlace = { place ->
                    scope.launch { drawerState.close() }
                    if (place < GoalTab.entries.size) {
                        resourcesOpen = false
                        scope.launch { pagerState.scrollToPage(place) }
                    } else {
                        resourcesOpen = true
                    }
                },
            )
        },
    ) {
        Scaffold(
            // **The keyboard must not cover what is being typed** (BUG-042). `MainActivity` calls
            // `enableEdgeToEdge()`, and that switches off the window's own `adjustResize` — from
            // then on the IME inset is the app's to apply. The three screens that already handled
            // typing well (the AI panel, the note editor, the provider sheet) are exactly the three
            // that had an `imePadding()`; this one did not, so "Add task" opened its field behind
            // the keyboard, as did every inline edit on a phase screen.
            modifier = Modifier.imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    // Home / goal search / delete — the workspace's own header (the All-goals
                    // dashboard keeps the SPIRA wordmark bar).
                    GoalWorkspaceTopBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onHome = actions.onBack,
                        onDelete = { confirmDeleteGoal = true },
                    )
                    // The GROW tabs stay put on every phase screen. On the Resources page nothing
                    // is underlined (-1) — it isn't a GROW phase — but the row is still there, so
                    // one tap leads back into the flow.
                    GrowTabsRow(
                        labels = GoalTab.entries.map { it.label },
                        selectedIndex = if (resourcesOpen) -1 else pagerState.currentPage,
                        onSelect = { index ->
                            resourcesOpen = false
                            // A tap JUMPS to the tab rather than scrolling to it: a tab three
                            // pages away would otherwise flick the two screens in between past
                            // the user, which reads as noise, not as motion. Swiping still
                            // animates — that is the pager following the finger.
                            scope.launch { pagerState.scrollToPage(index) }
                        },
                    )
                    // An action that failed without changing the screen — a delete that didn't
                    // land, for instance. Under the chrome so it can't be missed, and above the
                    // content so the goal stays visible behind it.
                    SpiraInlineBanner(
                        message = actionError,
                        onDismiss = onDismissActionError,
                    )
                }
            },
            bottomBar = {
                GoalWorkspaceBottomBar(
                    onMenu = { scope.launch { drawerState.open() } },
                    onAssistant = onOpenAssistant,
                    onResources = { resourcesOpen = !resourcesOpen },
                    resourcesSelected = resourcesOpen,
                    swipeUpGesture = assistantSwipeUpGesture,
                )
            },
        ) { padding ->
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // Tapping any empty (non-interactive) area clears focus off whatever inline
                    // field is being edited — which commits the edit AND stops the text cursor's
                    // blink. detectTapGestures only fires for taps the children didn't consume, so
                    // taps on fields/buttons/cards still work; only truly-blank taps land here.
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    },
            ) {
                when (state) {
                    GoalUiState.Loading -> Centered { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                    is GoalUiState.Error -> Centered {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.message, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = actions.onRetry) { Text("Try again") }
                        }
                    }
                    is GoalUiState.Content -> {
                        // Everything inside the workspace can render `{{res:id}}` tokens as links
                        // and offer "Attach resource" — outside a goal there is no list to pick
                        // from, so inline fields degrade to plain text (LocalInlineResources = null).
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val inlineResources = remember(state.goal.resources) {
                            InlineResourcesValue(
                                resources = state.goal.resources,
                                openResource = { id ->
                                    openInlineResource(
                                        context = context,
                                        resource = state.goal.resources.firstOrNull { it.id == id },
                                        onOpenFullScreen = { fullScreenResourceId = it },
                                    )
                                },
                            )
                        }
                        // Celebrate a target crossing the line. It lives here, not on the card:
                        // completing a target can filter its card out of the list, so the card
                        // unmounts before any effect of its own could run.
                        CelebrationOverlay(
                            achievedCount = state.goal.targets.count { it.progress >= 1f },
                            modifier = Modifier.fillMaxSize().zIndex(2f),
                        )
                        ProvideInlineResources(inlineResources) {
                            if (resourcesOpen) {
                                ResourcesPage(
                                    goal = state.goal,
                                    actions = actions,
                                    onOpenFull = { fullScreenResourceId = it },
                                    query = resourcesQuery,
                                    onQueryChange = { resourcesQuery = it },
                                )
                            } else {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize(),
                                ) { page ->
                                    GoalTabContent(
                                        goal = state.goal,
                                        tab = GoalTab.entries[page],
                                        actions = actions,
                                        targetView = targetView,
                                        realityKind = realityKind,
                                        onRealityKindChange = { realityKind = it },
                                        targetsQuery = targetsQuery,
                                        onTargetsQueryChange = { targetsQuery = it },
                                        optionsQuery = optionsQuery,
                                        onOptionsQueryChange = { optionsQuery = it },
                                    )
                                }
                            }
                        }
                    }
                }
                // FABs live directly in this full-width content box (not Scaffold's dedicated
                // floatingActionButton slot) — that slot sizes itself to its content rather than
                // the screen width, which clipped a BottomEnd-aligned second FAB off-screen.
                // Guava (coral accent) FAB with a white +, one per page that can add something.
                // EVERY page that can add something adds it the same way: this round
                // button, bottom-right, exactly like "new goal" on the dashboard.
                // An add action never sits at the top of a list.
                val addAction: Pair<String, () -> Unit>? = when {
                    resourcesOpen -> "Add resource" to { showNewResourceSheet = true }
                    pagerState.currentPage == GoalTab.Reality.ordinal ->
                        (if (realityKind == "obstacles") "Add obstacle" else "Add action") to
                            { showNewRealitySheet = true }
                    pagerState.currentPage == GoalTab.Options.ordinal ->
                        "Add option" to { showNewOption = true }
                    pagerState.currentPage == GoalTab.Targets.ordinal ->
                        "Add target" to { showNewTarget = true }
                    else -> null
                }
                if (addAction != null) {
                    FloatingActionButton(
                        onClick = addAction.second,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Icon(SpiraIcons.Plus, contentDescription = addAction.first) }
                }

                // The goal switcher's results hang under the header, over the page, and only
                // once something has been typed.
                if (searchQuery.isNotBlank()) {
                    GoalSearchResults(
                        results = allGoals.filter {
                            it.title.contains(searchQuery.trim(), ignoreCase = true)
                        },
                        onOpen = { id -> searchQuery = ""; onOpenGoal(id) },
                        modifier = Modifier.align(Alignment.TopCenter).zIndex(3f),
                    )
                }
            }
        }

        if (confirmDeleteGoal) {
            // Worded like the target dialog, so the two read as one pattern rather than two
            // different warnings, and the goal's own name is what stands out in it.
            val goalName = (state as? GoalUiState.Content)?.goal?.title.orEmpty()
            ConfirmDialog(
                title = "Delete this goal?",
                message = "\"$goalName\" will be permanently deleted. Targets, options and " +
                    "everything else inside it will be removed. You can't undo this.",
                subject = "\"$goalName\"",
                confirmLabel = "Yes, delete",
                cancelLabel = "No, go back",
                onConfirm = actions.onDeleteGoal,
                onDismiss = { confirmDeleteGoal = false },
            )
        }
        if (showNewOption) {
            NewOptionSheet(
                onDismiss = { showNewOption = false },
                onCreate = { text -> actions.onAddOption(text); showNewOption = false },
            )
        }
        if (showNewTarget) {
            // **The sheet needs the goal's resource list too**, because its form offers "Attach
            // resource" now (owner, 2026-08-20). It is mounted out here, beside the Scaffold rather
            // than inside its content, so the workspace's own `ProvideInlineResources` does not
            // reach it — and the attach control renders nothing when `LocalInlineResources` is
            // null, which would simply have left the form without it.
            val newTargetResources = (state as? GoalUiState.Content)?.goal?.resources.orEmpty()
            val sheetContext = androidx.compose.ui.platform.LocalContext.current
            ProvideInlineResources(
                remember(newTargetResources) {
                    InlineResourcesValue(
                        resources = newTargetResources,
                        // **The same routing as the workspace's own provider**, not a shortcut to
                        // the full-screen viewer: a link belongs in the browser and only a note or
                        // a file opens in the viewer. Nothing in this sheet renders a token today,
                        // so the difference is invisible — and it would stop being invisible the
                        // moment one did.
                        openResource = { id ->
                            openInlineResource(
                                context = sheetContext,
                                resource = newTargetResources.firstOrNull { it.id == id },
                                onOpenFullScreen = { fullScreenResourceId = it },
                            )
                        },
                    )
                },
            ) {
                NewTargetSheet(
                    onDismiss = { showNewTarget = false },
                    onCreate = { title, type, deadline, start, total, unit, checklist ->
                        actions.onAddTarget(title, type, deadline, start, total, unit, checklist)
                        showNewTarget = false
                    },
                )
            }
        }
        if (showNewResourceSheet) {
            NewResourceSheet(
                onDismiss = { showNewResourceSheet = false },
                onSubmit = { type, title, body, url, name, email, role, phone, mime, dataUrl ->
                    actions.onAddResource(type, title, body, url, name, email, role, phone, mime, dataUrl)
                    showNewResourceSheet = false
                },
            )
        }
        if (showNewRealitySheet) {
            NewRealitySheet(
                kind = realityKind,
                onDismiss = { showNewRealitySheet = false },
                onCreate = { text -> actions.onAddReality(realityKind, text); showNewRealitySheet = false },
            )
        }
        val fullRes = (state as? GoalUiState.Content)?.goal?.resources?.firstOrNull { it.id == fullScreenResourceId }
        if (fullRes != null) {
            ResourceFullScreen(res = fullRes, actions = actions, onClose = { fullScreenResourceId = null })
        }
    }
}

/**
 * The four GROW phases of the goal workspace, in the order the tab bar shows them. The labels are
 * the tab bar's words — note [Targets] reads "Will do" (the GROW "Will" step), matching its
 * on-screen kicker. Resources is deliberately absent: it is a page of its own, reached from the
 * footer, not a phase of the method.
 */
enum class GoalTab(val label: String) {
    Goal("Goal"), Reality("Reality"), Options("Options"), Targets("Will do")
}

/**
 * Results for the header's goal switcher — a floating white card hanging under the header, in the
 * same language as the app's dropdowns (pure white, rounded, hairline border, soft shadow).
 * Picking one opens that goal.
 */
@Composable
private fun GoalSearchResults(
    results: List<GoalSummary>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp), clip = false)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.spiraExtras.surfaceRaised)
            .border(1.dp, MaterialTheme.spiraExtras.border, RoundedCornerShape(20.dp)),
    ) {
        if (results.isEmpty()) {
            Text(
                "No goals found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.spiraExtras.mutedForeground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
        // Capped so the card can never grow taller than the page it floats over.
        LazyColumn(Modifier.heightIn(max = 320.dp)) {
            items(results, key = { it.id }) { goal ->
                Text(
                    goal.title.ifBlank { "Untitled goal" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(goal.id) }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                )
            }
        }
    }
}

/**
 * The Resources page — everything attached to this goal. It is a page in its own right (reached
 * from the footer), not a GROW phase and not a drawer, so it gets the same scrolling frame the
 * phase pages use.
 */
@Composable
private fun ResourcesPage(
    goal: GoalDetail,
    actions: GoalWorkspaceActions,
    onOpenFull: (String) -> Unit,
    query: String = "",
    onQueryChange: (String) -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Cards manage their own 18dp side margin, so no extra side padding here.
        contentPadding = PaddingValues(top = 0.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ResourcesTabContent(
                goal = goal,
                actions = actions,
                onOpenFull = onOpenFull,
                query = query,
                onQueryChange = onQueryChange,
            )
        }
    }
}

/** Content of the currently selected GROW tab. */
@Composable
private fun GoalTabContent(
    goal: GoalDetail,
    tab: GoalTab,
    actions: GoalWorkspaceActions,
    targetView: TargetViewState,
    realityKind: String = "actions",
    onRealityKindChange: (String) -> Unit = {},
    // One query per page, hoisted to the screen so a pager swipe doesn't wipe what was typed while
    // leaving the goal still does — and so an Options search can never leak into Targets.
    targetsQuery: String = "",
    onTargetsQueryChange: (String) -> Unit = {},
    optionsQuery: String = "",
    onOptionsQueryChange: (String) -> Unit = {},
) {
    // While an Options card is being dragged in reorder mode, freeze the list's own scroll so the
    // vertical drag reorders the card instead of scrolling the page (fixes drag-and-drop). The
    // page still scrolls PROGRAMMATICALLY underneath — see [optionsAutoScroll] — because
    // `userScrollEnabled` only gates the gesture, not `LazyListState.scrollBy`.
    var optionsDragging by remember { mutableStateOf(false) }

    // Auto-scroll for a dragged Options card, the web's behaviour: while the finger sits within
    // AUTOSCROLL_EDGE of the page's top or bottom, the list scrolls toward it (speed ramps with
    // proximity), so a list longer than one screen can be traversed in a single drag. The list's
    // own bounds are captured in root coordinates, because that is the space the card reports its
    // finger position in.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var listTop by remember { mutableStateOf(0f) }
    var listBottom by remember { mutableStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Returns the distance actually scrolled, which the caller folds back into the drag so the
    // card stays under the finger and the slot maths keeps working while the page moves.
    val optionsAutoScroll: suspend (Float) -> Float = scroll@{ pointerYInRoot ->
        val edge = with(density) { AUTOSCROLL_EDGE.toPx() }
        val maxStep = with(density) { AUTOSCROLL_MAX_STEP.toPx() }
        if (listBottom <= listTop) return@scroll 0f
        val dy = when {
            pointerYInRoot < listTop + edge ->
                -(((listTop + edge - pointerYInRoot).coerceAtMost(edge) / edge) * maxStep)
            pointerYInRoot > listBottom - edge ->
                ((pointerYInRoot - (listBottom - edge)).coerceAtMost(edge) / edge) * maxStep
            else -> 0f
        }
        if (dy == 0f) 0f else listState.scrollBy(dy)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                listTop = it.positionInRoot().y
                listBottom = listTop + it.size.height
            },
        // Bottom padding keeps content clear of the floating "add" button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = !optionsDragging,
    ) {
        when (tab) {
            GoalTab.Goal -> {
                item { GoalHeader(goal, actions) }
            }
            GoalTab.Reality -> {
                item {
                    RealityTabContent(
                        goal = goal,
                        actions = actions,
                        kind = realityKind,
                        onKindChange = onRealityKindChange,
                    )
                }
            }
            GoalTab.Options -> {
                item {
                    OptionsTabContent(
                        goal = goal,
                        actions = actions,
                        onDraggingChange = { optionsDragging = it },
                        onAutoScroll = optionsAutoScroll,
                        query = optionsQuery,
                        onQueryChange = onOptionsQueryChange,
                    )
                }
            }
            GoalTab.Targets -> {
                item {
                    GoalTabIntro(
                        title = "Commit to the next steps",
                        description = "Turn your chosen option into concrete targets you'll act on, " +
                            "and track your progress as you go.",
                    )
                }
                item {
                    // The shared list chrome — search, then sort / filter / add. The sort and
                    // filter choice is remembered between visits; the query never is.
                    SpiraListToolbar(
                        query = targetsQuery,
                        onQueryChange = onTargetsQueryChange,
                        placeholder = "Search targets",
                        sort = {
                            SpiraSortTrigger(
                                options = TargetSort.entries.map { SpiraChoice(it, it.label) },
                                selected = targetView.sort,
                                onSelect = { targetView.sort = it },
                                ascending = targetView.ascending,
                                onAscendingChange = { targetView.ascending = it },
                                contentDescription = "Sort targets",
                                // One padlock per **list**, not per sheet: the sort and the filter
                                // are two halves of one arrangement.
                                locked = targetView.locked,
                                onLockedChange = { targetView.locked = it },
                                onReset = { targetView.resetAll() },
                            )
                        },
                        filter = {
                            // Three independent questions, three columns. Each is `All` until the
                            // user narrows it, and the trigger says how many are narrowing.
                            SpiraFilterTrigger(
                                count = targetView.activeCount,
                                contentDescription = "Filter targets",
                                // **Everything**, the sort included (owner, 2026-08-21).
                                onReset = { targetView.resetAll() },
                                locked = targetView.locked,
                                onLockedChange = { targetView.locked = it },
                            ) {
                                // Five independent questions, each a line of pills - the All-goals
                                // sheet's shape. As menu columns they could not fit across a phone,
                                // which is what this replaces.
                                //
                                // Done-ness and started-ness are one mutually-exclusive filter shown
                                // as two questions: the started answers read as a different question
                                // from the done ones. **Done-ness is the "Progress" question and
                                // started-ness the "Status" one** (owner, 2026-08-18) — finishing is
                                // the far end of a progress bar, while having begun is a state the
                                // target is in.
                                SpiraSheetGroup("Progress") {
                                    SpiraSheetPills(
                                        options = listOf(TargetFilter.All, TargetFilter.Done, TargetFilter.NotDone)
                                            .map { SpiraChoice(it, it.label) },
                                        value = targetView.filter,
                                        onChange = { targetView.filter = it },
                                    )
                                }
                                SpiraSheetGroup("Status") {
                                    SpiraSheetPills(
                                        options = listOf(TargetFilter.Started, TargetFilter.NotStarted)
                                            .map { SpiraChoice(it, it.label) },
                                        value = targetView.filter,
                                        onChange = { targetView.filter = it },
                                        tone = SpiraBadgeTone.Info,
                                    )
                                }
                                SpiraSheetGroup("Deadline") {
                                    SpiraSheetPills(
                                        options = TargetDeadlineFilter.entries.map { SpiraChoice(it, it.label) },
                                        value = targetView.deadlineFilter,
                                        onChange = { targetView.deadlineFilter = it },
                                        tone = SpiraBadgeTone.Warning,
                                    )
                                }
                                SpiraSheetGroup("Type") {
                                    SpiraSheetPills(
                                        options = TargetTypeFilter.entries.map { SpiraChoice(it, it.label) },
                                        value = targetView.typeFilter,
                                        onChange = { targetView.typeFilter = it },
                                        tone = SpiraBadgeTone.Intelligence,
                                    )
                                }
                                SpiraSheetGroup("Lock") {
                                    SpiraSheetPills(
                                        options = TargetLockFilter.entries.map { SpiraChoice(it, it.label) },
                                        value = targetView.lockFilter,
                                        onChange = { targetView.lockFilter = it },
                                        tone = SpiraBadgeTone.Teal,
                                    )
                                }
                                // The dates themselves, under the "Overdue / Not overdue" question
                                // that reads them relative to today. The web sheet has carried this
                                // pair since 2026-08-17 (`Targets.tsx`); this is the phone's twin.
                                SpiraSheetGroup("Deadline range") {
                                    SpiraSheetDateRange(
                                        from = targetView.deadlineFrom,
                                        to = targetView.deadlineTo,
                                        onFromChange = { targetView.deadlineFrom = it },
                                        onToChange = { targetView.deadlineTo = it },
                                    )
                                }
                            }
                        },
                    )
                }
                val visible = applyTargetView(
                    goal.targets, targetView.sort, targetView.ascending, targetView.filter,
                    targetsQuery, targetView.deadlineFilter, targetView.lockFilter,
                    targetView.deadlineFrom, targetView.deadlineTo, targetView.typeFilter,
                )
                if (goal.targets.isEmpty()) item { EmptyLine("No targets yet.") }
                // Not a grey line: the list has targets, and it is the user's own search or filter
                // that is hiding them (owner, 2026-08-18).
                else if (visible.isEmpty()) item {
                    SpiraNoticeCard(
                        message = "No targets match that search or filter.",
                        kind = SpiraNoticeKind.Warning,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                else items(visible, key = { "target-${it.id}" }) { target ->
                    TargetCard(target, actions)
                }
            }
        }
    }

}

/**
 * The intro block at the top of every goal-workspace screen: a **centered** [title] and
 * [description]. [onTeal] flips the colours for the teal-backgrounded Options screen.
 *
 * There used to be a coloured kicker above the title naming the phase ("REALITY", "WILL DO").
 * The GROW tab bar now sits directly above this block and names the phase itself, so the kicker
 * only said the same word twice.
 *
 * Every phase heading — this one and the goal's own title — is set at [PHASE_HEADING_STYLE], and
 * every screen starts with the same [PHASE_HEADING_TOP_GAP] of air under the tabs, so moving
 * between phases never shifts the type.
 */
@Composable
private fun GoalTabIntro(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onTeal: Boolean = false,
) {
    val titleColor = if (onTeal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val descColor = if (onTeal) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
    else MaterialTheme.spiraExtras.mutedForeground

    Column(modifier.fillMaxWidth().padding(top = PHASE_HEADING_TOP_GAP, bottom = 12.dp)) {
        Text(
            title,
            style = PHASE_HEADING_STYLE(),
            // Regular serif weight — a phase name is a heading, not a bold label (owner asked for
            // Options / Resources / Targets and the rest not to read as bold).
            fontWeight = FontWeight.Normal,
            color = titleColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = descColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One size for every phase heading, including the goal's own title. */
@Composable
private fun PHASE_HEADING_STYLE() = MaterialTheme.typography.headlineMedium

/** The air between the GROW tab row and the first heading, on every phase. */
private val PHASE_HEADING_TOP_GAP = 28.dp

@Composable
private fun GoalHeader(goal: GoalDetail, actions: GoalWorkspaceActions) {
    var historyOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // The goal's own title is a phase heading like any other: same size, same air under the
        // tab row, so the four screens read as one set. Attached resources render as links here
        // too — a goal's title can reference the brief it came from.
        InlineRichText(
            value = goal.title,
            onCommit = actions.onSetGoalTitle,
            modifier = Modifier.fillMaxWidth().padding(top = PHASE_HEADING_TOP_GAP),
            placeholder = "Goal title",
            textStyle = PHASE_HEADING_STYLE(),
            textAlign = TextAlign.Center,
            required = true,
            maxLength = FieldLimits.GOAL_TITLE,
        )

        StatCard(
            statValue = "${formatPercent(goal.progress, goalProgressSteps(goal))}%",
            statCaption = if (goal.achieved) "Achieved" else "Progress across all targets",
        ) {
            FieldLabel("Description")
            Spacer(Modifier.height(6.dp))
            InlineRichText(
                value = goal.description,
                onCommit = actions.onSetGoalDescription,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Add a description",
                minLines = 2,
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLength = FieldLimits.GOAL_DESCRIPTION,
            )
        }

        StatCard(statValue = "${goal.confidence}/10", statCaption = "Current confidence level") {
            ConfidenceStepper(goal.confidence, actions.onSetConfidence)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.clickable { historyOpen = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Confidence history",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    SpiraIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        val deadlineParts = deadlineCountdownParts(goal.deadline)
        StatCard(statValue = deadlineParts.bigText, statCaption = deadlineParts.caption) {
            Text(
                "Click to change or remove",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.spiraExtras.mutedForeground,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeadlineLinkField(goal.deadline, actions.onSetDeadline, modifier = Modifier.weight(1f))
                if (goal.deadline != null) {
                    // Same hero pill shape/border as the Reality Actions/Obstacles toggle —
                    // red/error-tinted, no icon.
                    OutlinedButton(
                        onClick = { actions.onSetDeadline(null) },
                        shape = HeroButtonShape,
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = androidx.compose.foundation.BorderStroke(HeroButtonBorderWidth, MaterialTheme.colorScheme.error),
                    ) {
                        Text("Remove", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (historyOpen) {
        ConfidenceHistorySheet(
            current = goal.confidence,
            history = goal.confidenceHistory,
            onDismiss = { historyOpen = false },
        )
    }
}

/**
 * A stat card (mirrors the reference design): a big number + caption on the app's primary
 * (teal) top band — the number is much larger than the caption — and free-form content on a
 * white bottom band.
 */
@Composable
private fun StatCard(
    statValue: String,
    statCaption: String,
    bottomContent: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.spiraExtras.surfaceRaised),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                statValue,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                statCaption,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(Modifier.fillMaxWidth().padding(16.dp)) { bottomContent() }
    }
}

/** Bottom sheet listing past confidence values (mirrors the web `ConfidenceHistorySheet`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfidenceHistorySheet(
    current: Int,
    history: List<ConfidenceHistoryEntry>,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.spiraExtras.surfaceRaised,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Confidence history", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Current: $current/10",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.spiraExtras.mutedForeground,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(SpiraIcons.X, contentDescription = "Close") }
            }
            Spacer(Modifier.height(8.dp))
            if (history.isEmpty()) {
                EmptyLine("No changes yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    history.forEachIndexed { i, entry ->
                        val delta = history.getOrNull(i + 1)?.let { entry.confidence - it.confidence } ?: 0
                        ConfidenceHistoryRow(entry, delta)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceHistoryRow(entry: ConfidenceHistoryEntry, delta: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.spiraExtras.surfaceSunken)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row {
                Text("${entry.confidence}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text("/10", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.spiraExtras.mutedForeground)
            }
            Text(
                "${com.spiramindscape.android.ui.util.formatHistoryTimestamp(entry.at)} · " +
                    com.spiramindscape.android.ui.util.relativeTime(entry.at),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.spiraExtras.mutedForeground,
            )
        }
        if (delta != 0) {
            val positive = delta > 0
            val tone = if (positive) MaterialTheme.spiraExtras.success else MaterialTheme.colorScheme.error
            Row(
                Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .background(tone.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (positive) SpiraIcons.ArrowUp else SpiraIcons.ArrowDown,
                    contentDescription = null,
                    tint = tone,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    "${kotlin.math.abs(delta)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tone,
                )
            }
        }
    }
}

/**
 * The Reality tab: a short explanation of the GROW "reality" phase, an Actions/Obstacles
 * toggle, and the list for whichever is selected.
 */
@Composable
private fun RealityTabContent(
    goal: GoalDetail,
    actions: GoalWorkspaceActions,
    kind: String,
    onKindChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        GoalTabIntro(
            title = "Get honest about where you stand",
            description = "List the actions you've already taken and what's standing in your way. " +
                "Stick to facts, not judgment — a clear picture of your reality often points " +
                "straight at the next step.",
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RealityToggleButton("Actions", selected = kind == "actions", modifier = Modifier.weight(1f)) { onKindChange("actions") }
            RealityToggleButton("Obstacles", selected = kind == "obstacles", modifier = Modifier.weight(1f)) { onKindChange("obstacles") }
        }

        if (kind == "actions") {
            RealitySection(
                items = goal.actions,
                markerIcon = SpiraIcons.Check,
                markerColor = MaterialTheme.colorScheme.primary,
                emptyText = "No actions yet — tap the plus button to add one.",
                onUpdate = { id, text -> actions.onUpdateReality("actions", id, text) },
                onRemove = { id -> actions.onRemoveReality("actions", id) },
            )
        } else {
            RealitySection(
                items = goal.obstacles,
                markerIcon = SpiraIcons.X,
                markerColor = MaterialTheme.spiraExtras.warning,
                emptyText = "No obstacles yet — tap the plus button to add one.",
                onUpdate = { id, text -> actions.onUpdateReality("obstacles", id, text) },
                onRemove = { id -> actions.onRemoveReality("obstacles", id) },
            )
        }
    }
}

/** Actions/Obstacles segmented toggle — layout mirrors a side-by-side pair, styled as a hero pill. */
@Composable
private fun RealityToggleButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = HeroButtonShape,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            border = androidx.compose.foundation.BorderStroke(HeroButtonBorderWidth, MaterialTheme.colorScheme.primary),
        ) { Text(label, fontWeight = FontWeight.Medium) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = HeroButtonShape,
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            border = androidx.compose.foundation.BorderStroke(HeroButtonBorderWidth, MaterialTheme.colorScheme.primary),
        ) { Text(label, fontWeight = FontWeight.Medium) }
    }
}

/**
 * The list for one Reality kind (actions/obstacles): existing items and the Add button. New items
 * are created through [NewRealitySheet] (a form, like the goal/target/option create sheets), keyed
 * off [stateKey] ("actions"/"obstacles").
 */
@Composable
private fun RealitySection(
    items: List<com.spiramindscape.android.data.goals.TextItem>,
    markerIcon: ImageVector,
    markerColor: Color,
    emptyText: String,
    onUpdate: (id: String, text: String) -> Unit,
    onRemove: (id: String) -> Unit,
) {
    Column {
        if (items.isEmpty()) {
            EmptyLine(emptyText)
        }
        items.forEachIndexed { index, item ->
            RealityItemRow(
                text = item.text,
                markerIcon = markerIcon,
                markerColor = markerColor,
                onCommit = { onUpdate(item.id, it) },
                onRemove = { onRemove(item.id) },
            )
            if (index != items.lastIndex) {
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.spiraExtras.border)
            }
        }
    }
}

/**
 * One saved Reality item: a colored marker (teal check for actions / orange X for obstacles),
 * inline-editable text that wraps across lines instead of scrolling (items run up to 200
 * characters), and a kebab menu (Delete / Exit) that only appears after a long-press on the row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RealityItemRow(
    text: String,
    markerIcon: ImageVector,
    markerColor: Color,
    onCommit: (String) -> Unit,
    onRemove: () -> Unit,
) {
    // Long-press reveals the kebab; tapping it opens Attach resource / Delete. Dismissing the menu
    // collapses back to the plain row — there must always be a way out of this mode without
    // deleting anything.
    var showKebab by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        Modifier
            .fillMaxWidth()
            // A long-press anywhere on the row reveals the kebab menu instead of always showing
            // it; the text itself handles its own tap (open a link, or start editing).
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                // A plain tap reveals the row's actions. It used to do nothing at all, so the only
                // way to reach Delete was a long press nobody had been told about.
                onClick = { showKebab = true },
                onLongClick = { showKebab = true },
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            markerIcon,
            contentDescription = null,
            tint = markerColor,
            modifier = Modifier.padding(top = 2.dp).size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        // Inline-editable text that renders `{{res:id}}` attachments as links. A long press on it
        // reveals the row's kebab (the read view would otherwise treat it as a tap-to-edit).
        InlineRichText(
            value = text,
            onCommit = onCommit,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium,
            required = true,
            maxLength = FieldLimits.REALITY_TEXT,
            onLongPress = { showKebab = true },
            // The words swallow the row's tap (they run their own detector), so the actions have
            // to be revealed from here too — a tap into the editor is a tap on the item.
            onEditingChange = { if (it) showKebab = true },
        )
        if (showKebab) {
            // Tapping outside the menu (or back) dismisses it — that's the "exit", so there's no
            // explicit Exit item; the shared white dropdown handles it.
            ElementActionsMenu(
                contentDescription = "More options",
                attachedTo = text,
                vertical = true,
                onAttach = { resourceId ->
                    showKebab = false
                    attachTo(text, resourceId, FieldLimits.REALITY_TEXT)?.let(onCommit)
                },
                onDelete = { onRemove(); showKebab = false },
            )
        }
    }
}

/**
 * The Options tab — the web `OptionsList` (`src/components/spira/OptionsList.tsx`), phase-screen
 * shaped. Each option is a bordered row on the ordinary off-white page (this tab used to be a
 * full teal screen with centered "Option N" cards; the two surfaces now read as one design):
 *
 *  - a 48dp left cell holding the goal-wide single-select **active** radio,
 *  - the inline-editable option text, clamped to [OPTION_CLAMP_LINES] with a Show more/less
 *    toggle, plus the shared ⋯ menu (attach a resource / delete) in its own right-hand column,
 *  - a **smiley badge** on the card's top-right edge that cycles the thumb lean
 *    (none → good idea → didn't work), independent of the active radio.
 *
 * A new option is created from the page's own [NewOptionSheet]. Reordering is a **mode**, not a
 * long press: the Reorder button (shown from two options up) turns every card into a drag handle
 * and makes its per-card controls inert; Save leaves the mode. The order is committed to the
 * server when the finger comes up.
 */
@Composable
private fun OptionsTabContent(
    goal: GoalDetail,
    actions: GoalWorkspaceActions,
    onDraggingChange: (Boolean) -> Unit = {},
    /**
     * Scrolls the page when the given finger position (root coordinates) is near its top/bottom
     * edge, returning how far it actually moved. Supplied by [GoalTabContent], which owns the
     * `LazyListState`; the default makes the card draggable but not auto-scrolling, which is all a
     * test that renders the tab in isolation needs.
     */
    onAutoScroll: suspend (pointerYInRoot: Float) -> Float = { 0f },
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    onAddOption: () -> Unit = {},
) {
    val sortedOptions = goal.options.sortedBy { it.position }
    // In its own store, so this list has a padlock like the other three.
    val optionView = rememberOptionViewState()
    val optionFilter = optionView.filter
    // "Narrowed" covers both ways the drawn list can differ from the real one — a search and the
    // lean filter. Everything that depends on the two agreeing has to watch both, not just search.
    val narrowed = query.isNotBlank() || optionFilter != OptionFilter.All

    // Reorder mode (the Reorder/Save toggle). Only ever on with 2+ options — deleting down to one
    // leaves the mode rather than stranding the user in a list that can't be reordered.
    //
    // And never while the list is narrowed. A drop sends the index of the card in the RENDERED
    // list to the server as an absolute `position`; on a filtered list that index means something
    // else entirely and a wrong order is saved with no sign anything went wrong.
    var reordering by remember { mutableStateOf(false) }
    LaunchedEffect(sortedOptions.size) { if (sortedOptions.size < 2) reordering = false }
    LaunchedEffect(narrowed) { if (narrowed) reordering = false }

    // Local, drag-reorderable copy of the option order. It shadows [sortedOptions] so the list can
    // shuffle live under the finger; it re-seeds from the source whenever a real change lands (add/
    // remove/refetch/committed reorder) and while no drag is in progress.
    var order by remember { mutableStateOf(sortedOptions.map { it.id }) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sortedOptions.map { it.id }) {
        if (draggingId == null) order = sortedOptions.map { it.id }
    }
    val byId = sortedOptions.associateBy { it.id }
    // The drag maths always runs over the FULL list; the search only narrows what is drawn, and
    // reordering is off whenever the two could differ.
    val ordered = order.mapNotNull { byId[it] }
        .filter { !narrowed || it in applyOptionView(sortedOptions, query, optionFilter) }

    // Drag-and-drop reorder: the dragged card follows the finger (dragTranslation) while, as it
    // clears each neighbour, that neighbour's real measured height is used to swap it in `order` —
    // so a single drag can travel to ANY position and the card stays continuously under the finger.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val spacingPx = with(density) { OPTION_LIST_GAP.toPx() }
    val heights = remember { androidx.compose.runtime.mutableStateMapOf<String, Float>() }
    var dragTranslation by remember { mutableStateOf(0f) }
    fun onDragBy(id: String, delta: Float) {
        dragTranslation += delta
        var guard = 0
        while (guard++ < 64) {
            val from = order.indexOf(id)
            if (from == -1) break
            if (dragTranslation > 0f) {
                val nextId = order.getOrNull(from + 1) ?: break
                val step = (heights[nextId] ?: 0f) + spacingPx
                if (step > 0f && dragTranslation >= step) {
                    order = order.toMutableList().apply { add(from + 1, removeAt(from)) }
                    dragTranslation -= step
                } else break
            } else if (dragTranslation < 0f) {
                val prevId = order.getOrNull(from - 1) ?: break
                val step = (heights[prevId] ?: 0f) + spacingPx
                if (step > 0f && dragTranslation <= -step) {
                    order = order.toMutableList().apply { add(from - 1, removeAt(from)) }
                    dragTranslation += step
                } else break
            } else break
        }
    }

    // Where the finger is right now, in root coordinates — fed by the dragged card, read by the
    // auto-scroll loop. The loop has to be a loop rather than something driven by drag events: a
    // finger held STILL at the screen edge produces no events, and that is exactly when the page
    // must keep scrolling. It exists only while a card is held, so it can't keep the frame clock
    // busy afterwards (which is what once hung the whole visual-test suite — BUG-009).
    var pointerYInRoot by remember { mutableStateOf(0f) }
    LaunchedEffect(draggingId) {
        val id = draggingId ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val scrolled = onAutoScroll(pointerYInRoot)
            // Scrolling the page under a stationary finger is, to the reorder maths, the same as
            // moving the finger the other way: fold it in so slots keep swapping as the page moves.
            if (scrolled != 0f) onDragBy(id, scrolled)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(OPTION_LIST_GAP)) {
        GoalTabIntro(
            title = "Options",
            description = "Different ways this goal could be reached. Compare them, then make one " +
                "active — the one you're actually pursuing.",
        )

        // Options has **no sort** — position IS the meaning of this list. Its second line is the
        // lean filter first, then the Reorder toggle in the slot the other pages give to sort, so
        // the three toolbars line their controls up at the same x (owner, 2026-08-17: filter first).
        SpiraListToolbar(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = "Search options",
            // **The far right of the row, not beside the filter** (owner, 2026-08-18). Reorder is
            // the page's action, not a third way of narrowing the list, and sitting shoulder to
            // shoulder with the filter it read as one of them.
            action = {
                if (sortedOptions.size > 1) {
                    // A filled Kale button, like "Add target" on the web — not a bare teal word.
                    // It stays put while the list is narrowed and is **disabled** instead: a
                    // control that vanishes rearranges the toolbar under the user's hand and never
                    // says the filter is what took it away. What must not happen is a *drop* on a
                    // narrowed list, and a dead button prevents that just as well.
                    SpiraAddButton(
                        label = if (reordering) "Save" else "Reorder",
                        enabled = !narrowed,
                        onClick = { reordering = !reordering },
                    )
                }
            },
            filter = {
                // Icons here, not in a column menu: the badge on the card IS a smiley, so the
                // menu row that picks it carries the same mark rather than only its name.
                SpiraFilterTrigger(
                    count = optionView.activeCount,
                    contentDescription = "Filter options",
                    onReset = { optionView.resetAll() },
                    locked = optionView.locked,
                    onLockedChange = { optionView.locked = it },
                ) {
                    SpiraSheetGroup("Idea") {
                        SpiraSheetPills(
                            // "Good idea" and "Bad idea" carry the very glyphs the cards wear on
                            // their badge, so the answer and the thing it hides are one mark rather
                            // than two words that happen to agree (owner, 2026-08-18).
                            options = OptionFilter.entries.map { SpiraChoice(it, it.label, it.icon) },
                            value = optionFilter,
                            onChange = { optionView.filter = it },
                            tone = SpiraBadgeTone.Teal,
                        )
                    }
                }
            },
        )

        // The reason Reorder is unavailable. The app's one notice card, like every other thing the
        // app says to the user (owner, 2026-08-18) — it used to be a grey line with a bare glyph.
        if (sortedOptions.size > 1 && narrowed) {
            SpiraNoticeCard(
                message = "Clear the search and filter to rearrange.",
                kind = SpiraNoticeKind.Info,
            )
        }

        if (ordered.isEmpty()) {
            // Two different things: an empty list is an invitation, while a list emptied by the
            // user's own search or lean filter is a warning (owner, 2026-08-18).
            if (narrowed) {
                SpiraNoticeCard(
                    message = "No options match that search or filter.",
                    kind = SpiraNoticeKind.Warning,
                )
            } else {
                Text(
                    "What options could move you forward? Add a few, then choose one.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.spiraExtras.mutedForeground,
                )
            }
        }

        if (reordering) {
            SpiraNoticeCard(
                message = "Drag a card by the handle at its top. Swiping still scrolls the page.",
                kind = SpiraNoticeKind.Info,
            )
        }

        ordered.forEach { opt ->
            androidx.compose.runtime.key(opt.id) {
                OptionCard(
                    option = opt,
                    reordering = reordering,
                    isDragging = draggingId == opt.id,
                    dragTranslationY = if (draggingId == opt.id) dragTranslation else 0f,
                    onCommitText = { actions.onSetOptionText(opt.id, it) },
                    onToggleSelect = {
                        if (opt.selected) actions.onDeselectOption(opt.id)
                        else actions.onSelectOption(opt.id)
                    },
                    onCycleStatus = { actions.onSetOptionStatus(opt.id, nextOptionStatus(opt.status)) },
                    onAttach = { resourceId ->
                        attachTo(opt.text, resourceId, FieldLimits.OPTION_TEXT)
                            ?.let { actions.onSetOptionText(opt.id, it) }
                    },
                    onRemove = { actions.onRemoveOption(opt.id) },
                    onHeightMeasured = { heights[opt.id] = it },
                    onPointerY = { pointerYInRoot = it },
                    onDragStart = { draggingId = opt.id; dragTranslation = 0f; onDraggingChange(true) },
                    onDragBy = { delta -> onDragBy(opt.id, delta) },
                    onDragEnd = {
                        val to = order.indexOf(opt.id)
                        draggingId = null
                        dragTranslation = 0f
                        onDraggingChange(false)
                        if (to != -1 && sortedOptions.getOrNull(to)?.id != opt.id) {
                            actions.onReorderOption(opt.id, to)
                        }
                    },
                )
            }
        }
    }
}

/** The test tag on an option card, so a drag test can reach the grip inside a known card. */
fun optionCardTag(optionId: String) = "option-card-$optionId"

/** Vertical gap between option cards — the web's `space-y-3` (12px). */
private val OPTION_LIST_GAP = 12.dp

/** How far the smiley badge hangs off the card's top-right corner (the web's `-top-2 -right-2`). */
private val OPTION_BADGE_OVERHANG = 8.dp

/** A option longer than this collapses behind a Show more toggle (web parity). */
private const val OPTION_CLAMP_LINES = 3

/** How close to the page's top/bottom edge a dragged card must be before the page auto-scrolls. */
private val AUTOSCROLL_EDGE = 64.dp

/** Auto-scroll speed at the very edge, per frame; it ramps down to zero at [AUTOSCROLL_EDGE]. */
private val AUTOSCROLL_MAX_STEP = 16.dp

/** The badge's tap cycle: none → good idea → didn't work → none (same order as the web). */
private fun nextOptionStatus(current: String): String = when (current) {
    "good_idea" -> "didnt_work"
    "didnt_work" -> "none"
    else -> "good_idea"
}

/**
 * One option row (the web's `OptionRow`). The rating badge sits on the card's top-right EDGE as
 * a circle, half off the card; the ⋯ actions menu lives inside, in a fixed right-hand column so it
 * lines up across cards and never sits on top of the words (the web can float it over the text
 * because there it stays hidden until the row is hovered — a phone has no hover).
 *
 * In [reordering] the whole card is the drag target and every per-card control — the radio, the
 * badge, the ⋯ menu, the inline editor and the Show more toggle — goes inert, so a touch anywhere
 * moves the card instead of changing it.
 */
@Composable
private fun OptionCard(
    option: com.spiramindscape.android.data.goals.OptionItem,
    reordering: Boolean,
    isDragging: Boolean,
    dragTranslationY: Float,
    onCommitText: (String) -> Unit,
    onToggleSelect: () -> Unit,
    onCycleStatus: () -> Unit,
    onAttach: (resourceId: String) -> Unit,
    onRemove: () -> Unit,
    onHeightMeasured: (Float) -> Unit,
    /** The finger's Y in root coordinates while dragging — what the auto-scroll loop watches. */
    onPointerY: (Float) -> Unit = {},
    onDragStart: () -> Unit,
    onDragBy: (delta: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val active = option.selected
    val shape = RoundedCornerShape(SpiraRadii.sm)
    val primary = MaterialTheme.colorScheme.primary

    // "Show more" only exists once the text really is clipped. `overflowed` latches the first
    // clipped layout: expanding sets maxLines to unbounded, which reports "no overflow" again and
    // would otherwise make the toggle vanish the moment it is used. Both reset when the text does.
    var expanded by remember(option.text) { mutableStateOf(false) }
    var overflowed by remember(option.text) { mutableStateOf(false) }
    // A dragged or reordered card is forced back to the collapsed view so a long option doesn't
    // need a screen-height of finger travel to move one slot.
    val collapsed = !expanded || isDragging || reordering

    // The ⋯ menu is revealed by tapping the option text (which is also what starts editing it),
    // exactly as on the web. `menuOpen` keeps it alive once its dropdown is up: the dropdown lives
    // inside the menu composable, so losing the caret while it is open would take it away too.
    var editingText by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val menuVisible = !reordering && (editingText || menuOpen)

    // The card's top edge in root coordinates. `detectDragGestures` reports the finger in the
    // card's OWN space, so this is what turns it into a page position the auto-scroll can use.
    var cardTopInRoot by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .fillMaxWidth()
            // Tagged so a drag test can find THIS card's grip while the list reorders under it —
            // an index into "every grip on screen" points at a different card after the first swap.
            .testTag(optionCardTag(option.id))
            .onSizeChanged { onHeightMeasured(it.height.toFloat()) }
            .zIndex(if (isDragging) 1f else 0f)
            .offset { IntOffset(0, dragTranslationY.roundToInt()) }
            .onGloballyPositioned { cardTopInRoot = it.positionInRoot().y }
            .then(
                if (reordering) {
                    Modifier.pointerInput(option.id) {
                        // **After a long press**, not on any touch (owner, 2026-08-18). While the
                        // whole card grabbed every drag, scrolling a reorder list was close to
                        // impossible: a swipe meant to move the page picked a card up instead.
                        // A scroll is a swipe and a drag is a press — so the press is what this
                        // waits for, and everything shorter falls through to the list's own scroll.
                        // The handle at the top of the card is the immediate way in; this is the
                        // shortcut for someone whose finger is already on the card.
                        //
                        // Tracked by hand rather than re-read from layout: the finger must stay
                        // located even when it stops moving and only the page scrolls.
                        var fingerY = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start ->
                                fingerY = cardTopInRoot + start.y
                                onPointerY(fingerY)
                                onDragStart()
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                            onDrag = { change, amount ->
                                change.consume()
                                fingerY += amount.y
                                onPointerY(fingerY)
                                onDragBy(amount.y)
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // Room for the badge to hang off the top-right corner without being clipped.
                .padding(top = OPTION_BADGE_OVERHANG, end = OPTION_BADGE_OVERHANG)
                .shadow(if (isDragging) 10.dp else 0.dp, shape, clip = false)
                .clip(shape)
                .background(MaterialTheme.spiraExtras.surfaceRaised)
                .border(1.dp, if (active || isDragging) primary else MaterialTheme.spiraExtras.border, shape)
                // The radio cell is a full-height column beside text of any length.
                .height(IntrinsicSize.Min),
        ) {
            Box(
                Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .background(
                        if (active) MaterialTheme.spiraExtras.primarySoft
                        else MaterialTheme.spiraExtras.surfaceRaised,
                    )
                    .then(
                        if (reordering) Modifier
                        else Modifier.clickable(onClick = onToggleSelect),
                    )
                    .then(
                        // While reordering the cell IS the grip, so it must not also announce
                        // itself as the select control.
                        if (reordering) {
                            Modifier
                        } else {
                            Modifier.semantics {
                                contentDescription =
                                    if (active) "Deselect option" else "Select option"
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // **The grip stands in the radio's place** while reordering (owner, 2026-08-18):
                // the same cell, the same width, and nothing else on the card moves. The cell keeps
                // its teal wash and the card its teal border for the active option, so which one is
                // active stays visible while the list is being rearranged.
                if (reordering) {
                    OptionDragHandle(
                        active = active,
                        onPointerY = onPointerY,
                        onDragStart = onDragStart,
                        onDragBy = onDragBy,
                        onDragEnd = onDragEnd,
                    )
                } else {
                    Box(
                        Modifier
                            .size(20.dp)
                            .border(
                                2.dp,
                                if (active) primary else MaterialTheme.spiraExtras.borderStrong,
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (active) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(primary))
                        }
                    }
                }
            }
            // The hairline the web draws with `border-r` on the radio cell.
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(if (active) primary else MaterialTheme.spiraExtras.border),
            )

            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    InlineRichText(
                        value = option.text,
                        onCommit = onCommitText,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            lineHeight = 26.sp,
                        ),
                        placeholder = "What's this option?",
                        required = true,
                        maxLength = FieldLimits.OPTION_TEXT,
                        editable = !reordering,
                        maxLines = if (collapsed) OPTION_CLAMP_LINES else Int.MAX_VALUE,
                        onOverflowChange = { if (it) overflowed = true },
                        onEditingChange = { editingText = it },
                    )
                    if (overflowed && !reordering && !isDragging) {
                        Text(
                            if (expanded) "Show less" else "Show more",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { expanded = !expanded },
                        )
                    }
                }
                // The ⋯ menu appears with the editing caret and not before — tapping the option
                // text is what asks for it. It FLOATS over the text's top-right corner rather than
                // taking a column of its own: a column would sit empty most of the time, and
                // appearing would reflow the words the user is editing.
                if (menuVisible) {
                    ElementActionsMenu(
                        contentDescription = "Option actions",
                        attachedTo = option.text,
                        deleteLabel = "Delete option",
                        onAttach = onAttach,
                        onDelete = onRemove,
                        onOpenChange = { menuOpen = it },
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }

        // The thumb lean, on the card's top-right edge: one button that cycles on tap. Guava for
        // "good idea", Kale for "didn't work", a grey outline for no opinion.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.spiraExtras.surfaceRaised)
                .border(1.dp, MaterialTheme.spiraExtras.border, CircleShape)
                .then(if (reordering) Modifier else Modifier.clickable(onClick = onCycleStatus)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (option.status == "didnt_work") SpiraIcons.Frown else SpiraIcons.Smile,
                contentDescription = "Rate option",
                tint = when (option.status) {
                    "good_idea" -> MaterialTheme.colorScheme.tertiary
                    "didnt_work" -> primary
                    else -> MaterialTheme.spiraExtras.borderStrong
                },
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The grip that stands in the radio button's place while the list is in reorder mode.
 *
 * It exists because a scroll and a drag are different gestures and were sharing one target: with
 * the whole card listening for a drag, a swipe meant to move the page picked a card up instead, and
 * reaching anything below the fold was a fight (owner, 2026-08-18). Now a touch that lands **here**
 * moves the card immediately, and a touch anywhere else on the card scrolls the page exactly as it
 * does outside reorder mode. (Pressing and holding the card still works too — see [OptionCard] —
 * for the finger that is already on it.)
 *
 * **Where it sits took three goes, so don't move it again without asking.** A full-width band
 * across the top of the card was rejected: it lay over the radio cell's column, cutting the card's
 * left edge in two, and on its teal wash a list of cards read as a row of headers. Inside the
 * content column above the words was rejected too. It belongs in the **left cell, exactly where the
 * radio is** — the card's shape does not change at all between the two modes, only what that one
 * cell holds.
 *
 * [active] keeps the chosen option legible while the list is being rearranged: the cell's teal wash
 * and the card's teal border stay, and the grip takes the teal too.
 */
@Composable
private fun OptionDragHandle(
    active: Boolean,
    onPointerY: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (delta: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    // Where the cell is on the page, so the finger can be reported in root coordinates and the
    // auto-scroll loop can tell how near the screen edge it is.
    var topInRoot by remember { mutableStateOf(0f) }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { topInRoot = it.positionInRoot().y }
            .pointerInput(Unit) {
                var fingerY = 0f
                detectDragGestures(
                    onDragStart = { start ->
                        fingerY = topInRoot + start.y
                        onPointerY(fingerY)
                        onDragStart()
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onDrag = { change, amount ->
                        change.consume()
                        fingerY += amount.y
                        onPointerY(fingerY)
                        onDragBy(amount.y)
                    },
                )
            }
            .semantics { contentDescription = "Drag to reorder" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            // Gravity's `dots-9` — the owner's pick for a grip.
            SpiraIcons.Dots9,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.spiraExtras.borderStrong,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SectionHeaderRow(title: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title, Modifier.weight(1f))
        action()
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

/** Show a number without a trailing ".0" for whole values. */
