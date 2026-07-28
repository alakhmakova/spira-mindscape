package com.spiramindscape.android.ui.goals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.spiramindscape.android.ui.components.AddItemRow
import com.spiramindscape.android.ui.components.ConfidenceStepper
import com.spiramindscape.android.ui.components.ConfirmDialog
import com.spiramindscape.android.ui.components.DeadlineLinkField
import com.spiramindscape.android.ui.components.EmptyLine
import com.spiramindscape.android.ui.components.FieldLabel
import com.spiramindscape.android.ui.components.InlineEditText
import com.spiramindscape.android.ui.components.SectionLabel
import com.spiramindscape.android.ui.components.SpiraButton
import com.spiramindscape.android.ui.components.SpiraButtonVariant
import com.spiramindscape.android.ui.components.SpiraCard
import com.spiramindscape.android.ui.components.SpiraTopBar
import com.spiramindscape.android.ui.components.SpiraDropdownMenu
import com.spiramindscape.android.ui.components.SpiraLinearProgress
import com.spiramindscape.android.ui.components.SpiraMenuDivider
import com.spiramindscape.android.ui.components.SpiraMenuItem
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Guava300
import com.spiramindscape.android.ui.theme.Kale200
import com.spiramindscape.android.ui.theme.SpiraRadii
import com.spiramindscape.android.ui.theme.spiraExtras
import com.spiramindscape.android.ui.util.deadlineCountdownParts
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
    val onToggleChecklistItem: (targetId: String, itemId: String) -> Unit = { _, _ -> },
    val onAddChecklistTask: (targetId: String, text: String) -> Unit = { _, _ -> },
    val onUpdateChecklistTask: (targetId: String, itemId: String, text: String) -> Unit = { _, _, _ -> },
    val onRemoveChecklistTask: (targetId: String, itemId: String) -> Unit = { _, _ -> },
    val onSetTargetTitle: (targetId: String, title: String) -> Unit = { _, _ -> },
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
    val allGoals by GoalsStore.goals.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        // Silent refetch on resume (no spinner) so returning to the goal doesn't flash the loader.
        if (state is GoalUiState.Content) viewModel.refresh()
        onPauseOrDispose { }
    }

    GoalWorkspaceScreen(
        state = state,
        user = user,
        allGoals = allGoals,
        onLogout = onLogout,
        onOpenGoal = onOpenGoal,
        actions = GoalWorkspaceActions(
            onBack = onBack,
            onRetry = viewModel::load,
            onSetGoalTitle = viewModel::setGoalTitle,
            onSetGoalDescription = viewModel::setGoalDescription,
            onSetConfidence = viewModel::setConfidence,
            onSetDeadline = viewModel::setDeadline,
            onDeleteGoal = { viewModel.deleteGoal(onDeleted = onBack) },
            onSetTargetDone = viewModel::setTargetDone,
            onSetNumeric = viewModel::setNumericCurrent,
            onToggleChecklistItem = viewModel::toggleChecklistItem,
            onAddChecklistTask = viewModel::addChecklistTask,
            onUpdateChecklistTask = viewModel::updateChecklistTask,
            onRemoveChecklistTask = viewModel::removeChecklistTask,
            onSetTargetTitle = viewModel::setTargetTitle,
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
        ),
    )
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
) {
    var confirmDeleteGoal by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // Tabs switch either by tapping the bottom nav or by swiping the pager; both drive/read
    // the same pagerState so they always agree on which tab is showing.
    val pagerState = rememberPagerState(pageCount = { GoalTab.entries.size })
    // Switching tabs clears focus off any inline field being edited — this commits the pending edit
    // AND stops the text caret's blink (otherwise a field left focused keeps blinking forever).
    val rootFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(pagerState.currentPage) { rootFocusManager.clearFocus() }
    // Sort/filter in this header apply to the TARGETS tab.
    var targetSort by remember { mutableStateOf(TargetSort.Name) }
    var targetSortAscending by remember { mutableStateOf(true) }
    var targetFilter by remember { mutableStateOf(TargetFilter.All) }
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

    // Search is a goal switcher: results appear only once something is typed.
    if (searchOpen) {
        GoalSearchScreen(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            results = if (searchQuery.isBlank()) emptyList()
            else allGoals.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) },
            onOpen = { id -> onOpenGoal(id); searchOpen = false; searchQuery = "" },
            onClose = { searchOpen = false; searchQuery = "" },
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { SpiraDrawer(user, onLogout) },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                // Shared dark-teal header (design mockup): SPIRA + Search / AI / Profile.
                SpiraTopBar(
                    onMenu = { scope.launch { drawerState.open() } },
                    onSearch = { searchOpen = true },
                    onAssistant = { /* AI assistant — no screen yet */ },
                    onProfile = { scope.launch { drawerState.open() } },
                    onBrandClick = actions.onBack, // SPIRA wordmark → back to All goals
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.spiraExtras.surfaceRaised) {
                    GoalTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = pagerState.currentPage == t.ordinal,
                            onClick = { scope.launch { pagerState.animateScrollToPage(t.ordinal) } },
                            icon = { Icon(t.icon(), contentDescription = null, modifier = Modifier.width(22.dp).height(22.dp)) },
                            label = {
                                Text(
                                    t.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.spiraExtras.primarySoft,
                            ),
                        )
                    }
                }
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
                    is GoalUiState.Content -> HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        GoalTabContent(
                            goal = state.goal,
                            tab = GoalTab.entries[page],
                            actions = actions,
                            targetSort = targetSort,
                            targetSortAscending = targetSortAscending,
                            targetFilter = targetFilter,
                            onOpenFullResource = { fullScreenResourceId = it },
                            realityKind = realityKind,
                            onRealityKindChange = { realityKind = it },
                        )
                    }
                }
                // FABs live directly in this full-width content box (not Scaffold's dedicated
                // floatingActionButton slot) — that slot sizes itself to its content rather than
                // the screen width, which clipped a BottomEnd-aligned second FAB off-screen.
                FloatingActionButton(
                    onClick = actions.onBack,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(SpiraIcons.ArrowLeft, contentDescription = "Back to all goals") }
                // "Add option" — only on the Options tab. Guava (coral accent) FAB with a white +,
                // popping against that tab's teal page background.
                if (pagerState.currentPage == GoalTab.Options.ordinal) {
                    FloatingActionButton(
                        onClick = { showNewOptionSheet = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ) { Icon(SpiraIcons.Plus, contentDescription = "Add option") }
                }
                // "Add resource" — only on the Resources tab. Guava FAB (matches the design mockup).
                if (pagerState.currentPage == GoalTab.Resources.ordinal) {
                    FloatingActionButton(
                        onClick = { showNewResourceSheet = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ) { Icon(SpiraIcons.Plus, contentDescription = "Add resource") }
                }
                // "Add action/obstacle" — only on the Reality tab. Guava FAB.
                if (pagerState.currentPage == GoalTab.Reality.ordinal) {
                    FloatingActionButton(
                        onClick = { showNewRealitySheet = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ) { Icon(SpiraIcons.Plus, contentDescription = if (realityKind == "obstacles") "Add obstacle" else "Add action") }
                }
            }
        }

        if (confirmDeleteGoal) {
            ConfirmDialog(
                title = "Delete this goal?",
                message = "This removes the goal and everything in it.",
                confirmLabel = "Delete goal",
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

/** The five bottom-navigation tabs of the goal workspace. */
enum class GoalTab(val label: String) {
    Goal("Goal"), Reality("Reality"), Resources("Resources"), Options("Options"), Targets("Targets");

    fun icon() = when (this) {
        Goal -> SpiraIcons.Trophy
        Reality -> SpiraIcons.Eye
        Resources -> SpiraIcons.Folder
        Options -> SpiraIcons.Lightbulb
        Targets -> SpiraIcons.Target
    }
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

/** Full-screen goal search (from the workspace header): pick a goal to open it. */
@Composable
private fun GoalSearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<GoalSummary>,
    onOpen: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(64.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search for goals") },
                    leadingIcon = { Icon(SpiraIcons.Search, contentDescription = null, modifier = Modifier.width(18.dp).height(18.dp)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                )
                IconButton(onClick = onClose) { Icon(SpiraIcons.X, contentDescription = "Close search") }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.id }) { goal ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(goal.id) }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        SpiraIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(16.dp).height(16.dp),
                    )
                    Text(
                        goal.title.ifBlank { "Untitled goal" },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Content of the currently selected bottom-navigation tab. */
@Composable
private fun GoalTabContent(
    goal: GoalDetail,
    tab: GoalTab,
    actions: GoalWorkspaceActions,
    targetSort: TargetSort,
    targetSortAscending: Boolean,
    targetFilter: TargetFilter,
    onOpenFullResource: (String) -> Unit = {},
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
        // Bottom padding keeps content clear of the floating "back to all goals" button. The
        // Options and Resources tabs pad to their own edges (cards manage their own 18dp side
        // margin), so no extra side padding there — otherwise the cards would be doubly inset.
        contentPadding = if (tab == GoalTab.Options || tab == GoalTab.Resources) {
            androidx.compose.foundation.layout.PaddingValues(top = 0.dp, bottom = 96.dp)
        } else {
            androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)
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
            GoalTab.Resources -> {
                item { ResourcesTabContent(goal = goal, actions = actions, onOpenFull = onOpenFullResource) }
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
                        label = "Will do",
                        title = "Commit to the next steps",
                        description = "Turn your chosen option into concrete targets you'll act on, " +
                            "and track your progress as you go.",
                    )
                }
                item { AddSectionButton("Add target") { showNewTarget = true } }
                val visible = applyTargetView(goal.targets, targetSort, targetSortAscending, targetFilter)
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
 * The consistent intro block at the top of every goal-workspace screen (CLAUDE.md → "Goal-workspace
 * screen header"): a **left-aligned** kicker [label] (the screen/phase name, in the brand label
 * style) followed by a **centered** [title] and [description], with clear space between the kicker
 * and the heading. [onTeal] flips the colours for the teal-backgrounded Options tab.
 */
@Composable
private fun GoalTabIntro(
    label: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onTeal: Boolean = false,
) {
    val kickerColor = if (onTeal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val titleColor = if (onTeal) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val descColor = if (onTeal) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
    else MaterialTheme.spiraExtras.mutedForeground

    Column(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = kickerColor,
            textAlign = TextAlign.Start,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
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

/** A right-aligned "add" action row used under a tab's intro (Resources, Targets). */
@Composable
private fun AddSectionButton(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClick) { Text(text) }
    }
}

@Composable
private fun GoalHeader(goal: GoalDetail, actions: GoalWorkspaceActions) {
    var historyOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Full title, same size as the "All goals" dashboard heading.
        InlineEditText(
            value = goal.title,
            onCommit = actions.onSetGoalTitle,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Goal title",
            textStyle = MaterialTheme.typography.headlineMedium,
            // Multiline so a long title wraps and is fully visible (no horizontal scrolling).
            singleLine = false,
            required = true,
        )

        StatCard(
            statValue = "${(goal.progress * 100).roundToInt()}%",
            statCaption = if (goal.achieved) "Achieved" else "Progress across all targets",
        ) {
            FieldLabel("Description")
            Spacer(Modifier.height(6.dp))
            InlineEditText(
                value = goal.description,
                onCommit = actions.onSetGoalDescription,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Add a description",
                singleLine = false,
                minLines = 2,
                textStyle = MaterialTheme.typography.bodyMedium,
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

@Composable
private fun TargetCard(target: TargetItem, actions: GoalWorkspaceActions) {
    var confirmDelete by remember { mutableStateOf(false) }
    SpiraCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InlineEditText(
                    value = target.title,
                    onCommit = { actions.onSetTargetTitle(target.id, it) },
                    modifier = Modifier.weight(1f),
                    placeholder = "Target title",
                    // Target title: GCentra Medium (500) — matches the Options strategy weight.
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    required = true,
                )
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(SpiraIcons.Trash, contentDescription = "Delete target", tint = MaterialTheme.colorScheme.error)
                }
            }
            when (target) {
                is TargetItem.Binary -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = target.done, onCheckedChange = { actions.onSetTargetDone(target.id, it) })
                    Text(if (target.done) "Done" else "Not done", style = MaterialTheme.typography.bodyMedium)
                }
                is TargetItem.Numeric -> NumericBody(target, actions)
                is TargetItem.Checklist -> ChecklistBody(target, actions)
                is TargetItem.Other -> ProgressRow(target.progress)
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this target?",
            message = "\"${target.title}\" will be removed.",
            confirmLabel = "Delete target",
            onConfirm = { actions.onDeleteTarget(target.id) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun NumericBody(target: TargetItem.Numeric, actions: GoalWorkspaceActions) {
    val start = target.start ?: 0.0
    val total = target.total
    val lo = if (total != null) minOf(start, total) else start
    val hi = if (total != null) maxOf(start, total) else Double.MAX_VALUE
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { actions.onSetNumeric(target.id, (target.current - 1).coerceIn(lo, hi)) },
                enabled = target.current > lo,
            ) { Text("−") }
            // Tap the number to type a value directly (for big jumps); ± is for small nudges.
            InlineEditText(
                value = trim(target.current),
                onCommit = { entered -> entered.toDoubleOrNull()?.let { actions.onSetNumeric(target.id, it.coerceIn(lo, hi)) } },
                modifier = Modifier.width(72.dp),
                textStyle = MaterialTheme.typography.titleMedium,
                keyboardType = KeyboardType.Number,
                required = true,
            )
            val meta = buildString {
                target.total?.let { append("/ ${trim(it)}") }
                target.unit?.let { append(" $it") }
            }
            Text(meta, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { actions.onSetNumeric(target.id, (target.current + 1).coerceIn(lo, hi)) },
                enabled = total == null || target.current < hi,
            ) { Text("+") }
        }
        ProgressRow(target.progress)
    }
}

@Composable
private fun ChecklistBody(target: TargetItem.Checklist, actions: GoalWorkspaceActions) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        target.items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.done, onCheckedChange = { actions.onToggleChecklistItem(target.id, item.id) })
                InlineEditText(
                    value = item.text,
                    onCommit = { actions.onUpdateChecklistTask(target.id, item.id, it) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    required = true,
                )
                IconButton(onClick = { actions.onRemoveChecklistTask(target.id, item.id) }) {
                    Icon(SpiraIcons.X, contentDescription = "Remove task", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        AddItemRow("Add task", onAdd = { actions.onAddChecklistTask(target.id, it) })
        Spacer(Modifier.height(4.dp))
        ProgressRow(target.progress)
    }
}

@Composable
private fun ProgressRow(progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SpiraLinearProgress(progress, Modifier.weight(1f))
        Text(
            "${(progress * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
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
            label = "Reality",
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
    // Long-press reveals the kebab; tapping it opens Delete/Exit. "Exit" (and picking Delete)
    // both collapse back to the plain row — there must always be a way out of this mode without
    // deleting anything.
    var showKebab by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var textFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        Modifier
            .fillMaxWidth()
            // A short tap focuses the text field explicitly (routed through focusRequester so it
            // doesn't fight the BasicTextField's own tap-to-place-cursor gesture); a long-press
            // reveals the kebab menu instead of always showing it.
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { focusRequester.requestFocus() },
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
        Box(Modifier.weight(1f)) {
            InlineEditText(
                value = text,
                onCommit = onCommit,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyMedium,
                required = true,
                singleLine = false,
                imeAction = ImeAction.Done,
                onFocusChanged = { textFocused = it },
            )
            // While unfocused, an overlay claims touches on the text itself so the row's
            // long-press-to-delete works there too — a long-press landing directly on the
            // BasicTextField would otherwise be swallowed by its own selection gesture. Once
            // focused, the overlay steps aside so normal cursor placement/typing works.
            if (!textFocused) {
                Box(
                    Modifier
                        .matchParentSize()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { focusRequester.requestFocus() },
                            onLongClick = { showKebab = true },
                        ),
                )
            }
        }
        if (showKebab) {
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        SpiraIcons.MoreVertical,
                        contentDescription = "More options",
                        tint = MaterialTheme.spiraExtras.mutedForeground,
                        modifier = Modifier.size(18.dp),
                    )
                }
                // Tapping outside the menu (or back) dismisses it — that's the "exit", so there's
                // no explicit Exit item; the shared white dropdown handles it.
                SpiraDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false; showKebab = false },
                ) {
                    SpiraMenuItem(
                        label = "Delete",
                        icon = SpiraIcons.Trash,
                        destructive = true,
                        onClick = { onRemove(); menuExpanded = false; showKebab = false },
                    )
                }
            }
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
        // Page head — light on the teal Options surface (title + description, no kicker), matching
        // the mockup: 32sp ITC Clearface title, 14sp GCentra description on a light teal tint.
        Column(
            Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Options",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 32.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Different ways this goal could be reached. Compare them, then make one active — the one you're actually pursuing.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
                color = Kale200,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

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
        )
    }
}

/**
 * One saved Option (design mockup): a white card with a centered serif "Option N" title ([N] is
 * just its position — it renumbers automatically after a reorder) and centered, inline-editable
 * strategy text. A **kebab (⋮)** in the top-right corner opens the [OptionMenuSheet] bottom sheet
 * (Make active / Remove active status / Delete). Cards are reordered by **long-press-and-drag**; a
 * short tap focuses the strategy text. The **active** option gets a Guava border, a full-width
 * "ACTIVE" Guava band across its top edge, and a Guava-toned title with a short underline.
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
    var textFocused by remember { mutableStateOf(false) }
    val textFocusRequester = remember { FocusRequester() }

    // Long-press-and-drag reorders (the parent owns the reorder math — this just streams the finger
    // delta); a short tap focuses the strategy text. Both live in one shared modifier so the whole
    // card responds.
    val gestureMod = Modifier
        .pointerInput(option.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { onDragStart() },
                onDragEnd = { onDragEnd() },
                onDragCancel = { onDragEnd() },
                onDrag = { change, amount ->
                    change.consume()
                    onDragBy(amount.y)
                },
            )
        }
        .pointerInput(option.id) {
            detectTapGestures(onTap = { textFocusRequester.requestFocus() })
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
                        Box(Modifier.fillMaxWidth()) {
                            InlineEditText(
                                value = option.text,
                                onCommit = onCommitText,
                                modifier = Modifier.fillMaxWidth().focusRequester(textFocusRequester),
                                placeholder = "What's this strategy?",
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp, lineHeight = 23.sp,
                                ),
                                required = true,
                                singleLine = false,
                                imeAction = ImeAction.Done,
                                textAlign = TextAlign.Center,
                                onFocusChanged = { textFocused = it },
                            )
                            // While unfocused, an overlay claims touches on the text so tap-to-focus
                            // and long-press-to-drag work there too (a gesture landing on the
                            // BasicTextField would otherwise be swallowed by its selection gesture).
                            if (!textFocused) {
                                Box(Modifier.matchParentSize().then(gestureMod))
                            }
                        }
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
private fun trim(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
