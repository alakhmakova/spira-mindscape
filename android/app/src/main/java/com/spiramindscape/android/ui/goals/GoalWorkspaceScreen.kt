package com.spiramindscape.android.ui.goals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
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
import com.spiramindscape.android.ui.components.SpiraCard
import com.spiramindscape.android.ui.components.SpiraDropdownMenu
import com.spiramindscape.android.ui.components.SpiraMenuDivider
import com.spiramindscape.android.ui.components.SpiraMenuItem
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Guava300
import com.spiramindscape.android.ui.theme.Kale200
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
        onSelectOption = viewModel::selectOption,
        onDeselectOption = viewModel::deselectOption,
        onRemoveOption = viewModel::removeOption,
        onReorderOption = viewModel::reorderOptions,
        onAddResource = viewModel::addResource,
        onUpdateResource = { id, title, body, url, name, email, role, phone, mime, dataUrl ->
            viewModel.updateResource(id, title, body, url, name, email, role, phone, mime, dataUrl)
        },
        onRemoveResource = viewModel::removeResource,
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
    // The "add option" FAB (below, in this same Scaffold) opens a create form — like the goal and
    // target create sheets — rather than an inline draft card.
    var showNewOptionSheet by remember { mutableStateOf(false) }
    var showNewResourceSheet by remember { mutableStateOf(false) }
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
                val addAction: Pair<String, () -> Unit>? = when {
                    resourcesOpen -> "Add resource" to { showNewResourceSheet = true }
                    pagerState.currentPage == GoalTab.Options.ordinal ->
                        "Add option" to { showNewOptionSheet = true }
                    pagerState.currentPage == GoalTab.Reality.ordinal ->
                        (if (realityKind == "obstacles") "Add obstacle" else "Add action") to
                            { showNewRealitySheet = true }
                    else -> null
                }
                if (addAction != null) {
                    // Kale on the white pages, Guava on the teal Options page: on white the teal
                    // button belongs to the app's chrome, while against the teal ground it would
                    // vanish — that is the one place the coral accent is needed to stand out.
                    val addInKale = resourcesOpen ||
                        pagerState.currentPage != GoalTab.Options.ordinal
                    FloatingActionButton(
                        onClick = addAction.second,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        shape = CircleShape,
                        containerColor = if (addInKale) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                        contentColor = if (addInKale) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onTertiary
                        },
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
        if (showNewOptionSheet) {
            NewOptionSheet(
                onDismiss = { showNewOptionSheet = false },
                onCreate = { text ->
                    actions.onAddOption(text)
                    showNewOptionSheet = false
                },
            )
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

@Composable
private fun TargetSortMenu(
    sort: TargetSort,
    ascending: Boolean,
    onSortChange: (TargetSort) -> Unit,
    onToggleDir: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(SpiraIcons.ArrowUpDown, contentDescription = "Sort targets", modifier = Modifier.width(24.dp).height(24.dp))
        }
        SpiraDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TargetSort.entries.forEach { key ->
                SpiraMenuItem(
                    label = key.label,
                    onClick = { onSortChange(key); expanded = false },
                    selected = key == sort,
                )
            }
            SpiraMenuDivider()
            SpiraMenuItem(
                label = if (ascending) "Ascending" else "Descending",
                onClick = { onToggleDir(); expanded = false },
                icon = if (ascending) SpiraIcons.ArrowUp else SpiraIcons.ArrowDown,
            )
        }
    }
}

@Composable
private fun TargetFilterMenu(filter: TargetFilter, onChange: (TargetFilter) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                SpiraIcons.SlidersHorizontal,
                contentDescription = "Filter targets",
                modifier = Modifier.width(24.dp).height(24.dp),
                tint = if (filter != TargetFilter.All) MaterialTheme.colorScheme.primary
                else androidx.compose.material3.LocalContentColor.current,
            )
        }
        SpiraDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TargetFilter.entries.forEach { f ->
                SpiraMenuItem(
                    label = f.label,
                    onClick = { onChange(f); expanded = false },
                    selected = f == filter,
                )
            }
        }
    }
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
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Cards manage their own 18dp side margin, so no extra side padding here.
        contentPadding = PaddingValues(top = 0.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { ResourcesTabContent(goal = goal, actions = actions, onOpenFull = onOpenFull) }
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
) {
    var showNewTarget by remember { mutableStateOf(false) }
    // While an Options card is being long-press-dragged, freeze the list's own scroll so the
    // vertical drag reorders the card instead of scrolling the page (fixes drag-and-drop).
    var optionsDragging by remember { mutableStateOf(false) }

    LazyColumn(
        // The Options tab is a full teal screen (white cards on teal, mirroring the reference);
        // every other tab keeps the off-white page background.
        modifier = Modifier
            .fillMaxSize()
            .then(if (tab == GoalTab.Options) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier),
        // Bottom padding keeps content clear of the floating "add" button. The Options tab pads
        // to its own edges (its cards manage their own 18dp side margin), so no extra side
        // padding there — otherwise the cards would be doubly inset.
        contentPadding = if (tab == GoalTab.Options) {
            PaddingValues(top = 0.dp, bottom = 96.dp)
        } else {
            PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)
        },
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
                    // Sort and filter sit beside "Add target"; the choice is remembered between
                    // visits, so the list opens the way it was left.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TargetSortMenu(
                            sort = targetView.sort,
                            ascending = targetView.ascending,
                            onSortChange = { targetView.sort = it },
                            onToggleDir = { targetView.ascending = !targetView.ascending },
                        )
                        TargetFilterMenu(
                            filter = targetView.filter,
                            onChange = { targetView.filter = it },
                        )
                        Spacer(Modifier.weight(1f))
                        // The app's add-action shape: a circled plus and a plain label, the same
                        // as "New chat". Guava marks a target — the accent for the thing a goal
                        // is actually measured by.
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .clickable { showNewTarget = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Icon(
                                SpiraIcons.CirclePlus,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(17.dp),
                            )
                            Text(
                                "Add target",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
                val visible = applyTargetView(
                    goal.targets, targetView.sort, targetView.ascending, targetView.filter,
                )
                if (goal.targets.isEmpty()) item { EmptyLine("No targets yet.") }
                else if (visible.isEmpty()) item { EmptyLine("No targets match the filter.") }
                else items(visible, key = { "target-${it.id}" }) { target ->
                    TargetCard(target, actions)
                }
            }
        }
    }

    if (showNewTarget) {
        NewTargetSheet(
            onDismiss = { showNewTarget = false },
            onCreate = { title, type, deadline, start, total, unit, checklist ->
                actions.onAddTarget(title, type, deadline, start, total, unit, checklist)
                showNewTarget = false
            },
        )
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
                onClick = { },
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
 * The Options tab. The whole page background is teal (set in [GoalTabContent] for this tab); the
 * header uses the shared [GoalTabIntro] (left kicker + centered title/desc, white for the teal
 * page), and each option is a white card with a centered "Option N" title + centered strategy text.
 * Cards are reordered by **drag-and-drop** (long-press a card, drag it into place) — the "Option N"
 * number renumbers automatically. New options are created through the "add option" FAB (which opens
 * [NewOptionSheet] from the Scaffold, outside this composable).
 */
@Composable
private fun OptionsTabContent(
    goal: GoalDetail,
    actions: GoalWorkspaceActions,
    onDraggingChange: (Boolean) -> Unit = {},
) {
    val sortedOptions = goal.options.sortedBy { it.position }

    // Local, drag-reorderable copy of the option order. It shadows [sortedOptions] so the list can
    // shuffle live under the finger; it re-seeds from the source whenever a real change lands (add/
    // remove/refetch/committed reorder) and while no drag is in progress.
    var order by remember { mutableStateOf(sortedOptions.map { it.id }) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sortedOptions.map { it.id }) {
        if (draggingId == null) order = sortedOptions.map { it.id }
    }
    val byId = sortedOptions.associateBy { it.id }
    val ordered = order.mapNotNull { byId[it] }

    // The kebab opens a bottom-sheet menu for one card (by id); null = no menu open.
    var menuForId by remember { mutableStateOf<String?>(null) }

    // Drag-and-drop reorder: the dragged card follows the finger (dragTranslation) while, as it
    // clears each neighbour, that neighbour's real measured height is used to swap it in `order` —
    // so a single drag can travel to ANY position and the card stays continuously under the finger.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val spacingPx = with(density) { 16.dp.toPx() }
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 18.dp)) {
        // The same intro block every phase uses — light, because this screen is teal. It used to
        // be a one-off 32sp head, which is what made Options read a size larger than its siblings.
        GoalTabIntro(
            title = "Options",
            description = "Different ways this goal could be reached. Compare them, then make one " +
                "active — the one you're actually pursuing.",
            onTeal = true,
        )

        if (ordered.isEmpty()) {
            Text(
                "No options yet — tap the plus button to add your first strategy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        ordered.forEachIndexed { index, opt ->
            androidx.compose.runtime.key(opt.id) {
                OptionCard(
                    option = opt,
                    displayNumber = index + 1,
                    isDragging = draggingId == opt.id,
                    dragTranslationY = if (draggingId == opt.id) dragTranslation else 0f,
                    onCommitText = { actions.onSetOptionText(opt.id, it) },
                    onOpenMenu = { menuForId = opt.id },
                    onHeightMeasured = { heights[opt.id] = it },
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

    val menuOption = ordered.firstOrNull { it.id == menuForId }
    if (menuOption != null) {
        val number = ordered.indexOfFirst { it.id == menuForId } + 1
        OptionMenuSheet(
            optionNumber = number,
            isActive = menuOption.selected,
            onMakeActive = { actions.onSelectOption(menuOption.id); menuForId = null },
            onRemoveActive = { actions.onDeselectOption(menuOption.id); menuForId = null },
            onDelete = { actions.onRemoveOption(menuOption.id); menuForId = null },
            onDismiss = { menuForId = null },
            optionText = menuOption.text,
            onAttach = { resourceId ->
                attachTo(menuOption.text, resourceId, FieldLimits.OPTION_TEXT)
                    ?.let { actions.onSetOptionText(menuOption.id, it) }
            },
        )
    }
}

/**
 * One saved Option (design mockup): a white card with a centered serif "Option N" title ([N] is
 * just its position — it renumbers automatically after a reorder) and centered, inline-editable
 * strategy text. A **kebab (⋮)** in the top-right corner opens the [OptionMenuSheet] bottom sheet
 * (Make active / Remove active status / Delete). Cards are reordered by **long-press-and-drag**
 * from anywhere on the card, the strategy text included; a short tap on that text opens an attached
 * resource, or starts editing. The **active** option gets a Guava border, a full-width "ACTIVE"
 * Guava band across its top edge, and a Guava-toned title with a short underline.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OptionCard(
    option: com.spiramindscape.android.data.goals.OptionItem,
    displayNumber: Int,
    isDragging: Boolean,
    dragTranslationY: Float,
    onCommitText: (String) -> Unit,
    onOpenMenu: () -> Unit,
    onHeightMeasured: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragBy: (delta: Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val active = option.selected

    // Long-press-and-drag reorders (the parent owns the reorder math — this just streams the finger
    // delta). The SAME modifier also goes on the strategy text: that text runs its own tap detector
    // (open an attached resource, or start editing), which would otherwise swallow the gesture and
    // make the card undraggable by its biggest surface. With both detectors on one node the tap
    // cancels itself as soon as the drag starts consuming.
    // True from the moment a drag starts until the finger comes up, so the release doesn't also
    // read as a tap on the strategy text (see `tapSuppressed` on InlineRichText).
    var dragging by remember { mutableStateOf(false) }
    val gestureMod = Modifier
        .pointerInput(option.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { dragging = true; onDragStart() },
                onDragEnd = { dragging = false; onDragEnd() },
                onDragCancel = { dragging = false; onDragEnd() },
                onDrag = { change, amount ->
                    change.consume()
                    onDragBy(amount.y)
                },
            )
        }

    val guava = MaterialTheme.colorScheme.tertiary       // Guava-500 accent (band / border)
    val guavaDark = MaterialTheme.colorScheme.error       // Guava-600 (active name)
    val borderColor = when {
        active -> guava
        isDragging -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightMeasured(it.height.toFloat()) }
            .zIndex(if (isDragging) 1f else 0f)
            .offset { IntOffset(0, dragTranslationY.roundToInt()) }
            // Soft drop shadow so the white card floats on the teal page (a Card's own elevation
            // tints the surface grey in some renderers — this keeps the card pure white).
            .shadow(
                elevation = if (isDragging) 14.dp else 6.dp,
                shape = RoundedCornerShape(18.dp),
                clip = false,
            )
            .then(gestureMod),
    ) {
        SpiraCard(
            contentPadding = PaddingValues(0.dp),
            borderColor = borderColor,
            borderWidth = if (active || isDragging) 1.5.dp else 1.dp,
            shape = RoundedCornerShape(18.dp),
            elevation = 0.dp,
        ) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    // Active option: a full-width Guava band across the card's top edge.
                    if (active) {
                        Box(
                            Modifier.fillMaxWidth().background(guava).padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.3.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(
                            start = 24.dp, end = 24.dp,
                            top = if (active) 18.dp else 22.dp, bottom = 24.dp,
                        ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Option $displayNumber",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                            ),
                            color = if (active) guavaDark else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                        // Active name gets a short Guava underline accent.
                        if (active) {
                            Spacer(Modifier.height(9.dp))
                            Box(
                                Modifier.width(34.dp).height(2.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Guava300),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        // The strategy text renders its `{{res:id}}` attachments as links — tapping
                        // one opens the resource, tapping the words starts editing. The card is
                        // dragged by long-pressing anywhere outside this text.
                        InlineRichText(
                            value = option.text,
                            onCommit = onCommitText,
                            modifier = Modifier.fillMaxWidth().then(gestureMod),
                            placeholder = "What's this strategy?",
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp, lineHeight = 23.sp,
                            ),
                            required = true,
                            maxLength = FieldLimits.OPTION_TEXT,
                            textAlign = TextAlign.Center,
                            tapSuppressed = { dragging },
                        )
                    }
                }
                // Kebab (⋮) top-right — opens the bottom-sheet menu. Pushed below the band on
                // active cards so it doesn't collide with the ACTIVE strip.
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = if (active) 34.dp else 6.dp, end = 6.dp)
                        .size(36.dp),
                ) {
                    Icon(
                        SpiraIcons.MoreVertical,
                        contentDescription = "Option menu",
                        tint = MaterialTheme.spiraExtras.mutedForeground,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
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
