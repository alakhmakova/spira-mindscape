package com.spiramindscape.android.ui.goals

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.data.goals.GoalSummary
import com.spiramindscape.android.data.goals.GoalsStore
import com.spiramindscape.android.ui.ai.WithAiAssistant
import com.spiramindscape.android.ui.components.CircularProgress
import com.spiramindscape.android.ui.components.EmptyState
import com.spiramindscape.android.ui.components.SpiraDropdownMenu
import com.spiramindscape.android.ui.components.SpiraInlineBanner
import com.spiramindscape.android.ui.components.SpiraMenuDivider
import com.spiramindscape.android.ui.components.SpiraMenuItem
import com.spiramindscape.android.ui.components.HeaderCircleClose
import com.spiramindscape.android.ui.components.SpiraSearchField
import com.spiramindscape.android.ui.components.SpiraBadgeTone
import com.spiramindscape.android.ui.components.SpiraChoice
import com.spiramindscape.android.ui.components.SpiraFilterSheet
import com.spiramindscape.android.ui.components.SpiraFilterSortTrigger
import com.spiramindscape.android.ui.components.SpiraSegmented
import com.spiramindscape.android.ui.components.SpiraSheetCards
import com.spiramindscape.android.ui.components.SpiraNoticeCard
import com.spiramindscape.android.ui.components.SpiraNoticeKind
import com.spiramindscape.android.ui.components.SpiraSheetConfidence
import com.spiramindscape.android.ui.components.SpiraSheetDateRange
import com.spiramindscape.android.ui.components.SpiraSheetChoices
import com.spiramindscape.android.ui.components.SpiraSheetGroup
import com.spiramindscape.android.ui.components.SpiraSheetPills
import com.spiramindscape.android.ui.components.SpiraTopBar
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.Kale600
import com.spiramindscape.android.ui.theme.confidenceColor
import com.spiramindscape.android.ui.theme.spiraExtras
import com.spiramindscape.android.ui.util.deadlineCountdown
import kotlinx.coroutines.launch

@Composable
fun GoalsRoute(
    user: AuthUser,
    onGoalClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    val viewModel: GoalsViewModel = viewModel(factory = GoalsViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val allGoals by GoalsStore.goals.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sortKey by viewModel.sortKey.collectAsStateWithLifecycle()
    val sortAscending by viewModel.sortAscending.collectAsStateWithLifecycle()
    val status by viewModel.statusFilter.collectAsStateWithLifecycle()
    val deadlineFilter by viewModel.deadlineFilter.collectAsStateWithLifecycle()
    val deadlineFrom by viewModel.deadlineFrom.collectAsStateWithLifecycle()
    val deadlineTo by viewModel.deadlineTo.collectAsStateWithLifecycle()
    val confidence by viewModel.confidence.collectAsStateWithLifecycle()

    val visibleGoals = remember(
        allGoals, query, sortKey, sortAscending, status, deadlineFilter,
        deadlineFrom, deadlineTo, confidence,
    ) {
        applyGoalView(
            allGoals, query, sortKey, sortAscending, status, deadlineFilter,
            deadlineFrom, deadlineTo, confidence,
        )
    }

    // Sort and filters are a standing preference, remembered across sessions (web parity: they
    // live in localStorage there). The search box deliberately starts empty every time.
    val viewPreferences = rememberGoalViewPreferences()
    LaunchedEffect(viewPreferences) {
        viewModel.sortKey.value = viewPreferences.sort
        viewModel.sortAscending.value = viewPreferences.ascending
        viewModel.statusFilter.value = viewPreferences.status
        viewModel.deadlineFilter.value = viewPreferences.deadline
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    // The all-goals assistant: it can see every goal and start a new one.
    var assistantOpen by remember { mutableStateOf(false) }

    WithAiAssistant(
        goalId = null,
        open = assistantOpen,
        onOpenChange = { assistantOpen = it },
        onApplyProposal = { proposal, excluded -> applyGlobalProposal(proposal, excluded, viewModel) },
    ) { _ ->
        // The dashboard has no bottom bar to swipe up from, so the assistant opens from the
        // header icon here; the swipe is the goal workspace's affordance.
        GoalsDashboardScreen(
            state = state,
            visibleGoals = visibleGoals,
            hasAnyGoals = allGoals.isNotEmpty(),
            user = user,
            query = query,
            onQueryChange = { viewModel.query.value = it },
            sortKey = sortKey,
            sortAscending = sortAscending,
            onSortChange = { viewModel.sortKey.value = it; viewPreferences.sort = it },
            onToggleSortDir = {
                val next = !viewModel.sortAscending.value
                viewModel.sortAscending.value = next
                viewPreferences.ascending = next
            },
            status = status,
            onStatusChange = { viewModel.statusFilter.value = it; viewPreferences.status = it },
            deadlineFilter = deadlineFilter,
            // Through the view model, which is where the "no deadline OR a range, never both"
            // rule lives — so the stored preference follows whatever it settled on.
            onDeadlineFilterChange = {
                viewModel.setDeadlineFilter(it)
                viewPreferences.deadline = viewModel.deadlineFilter.value
            },
            // The range and the confidence are not written to [viewPreferences] — see the view
            // model, where the reason lives with the fields.
            deadlineFrom = deadlineFrom,
            onDeadlineFromChange = {
                viewModel.setDeadlineFrom(it)
                viewPreferences.deadline = viewModel.deadlineFilter.value
            },
            deadlineTo = deadlineTo,
            onDeadlineToChange = {
                viewModel.setDeadlineTo(it)
                viewPreferences.deadline = viewModel.deadlineFilter.value
            },
            confidence = confidence,
            onConfidenceChange = { viewModel.confidence.value = it },
            creating = creating,
            onCreateGoal = { title, description, confidence, deadline ->
                viewModel.createGoal(title, description, confidence, deadline)
            },
            onGoalClick = onGoalClick,
            onRetry = viewModel::load,
            onLogout = onLogout,
            onOpenSettings = onOpenSettings,
            onOpenAssistant = { assistantOpen = true },
            actionError = actionError,
            onDismissActionError = viewModel::clearActionError,
        )
    }
}

/**
 * Applies a proposal from the all-goals chat. Only goal-level creation belongs here; anything
 * scoped to one goal is applied inside that goal's own assistant, which has its data loaded.
 */
private fun applyGlobalProposal(
    proposal: com.spiramindscape.android.data.ai.Proposal,
    excluded: Set<String>,
    viewModel: GoalsViewModel,
): String? {
    val p = com.spiramindscape.android.data.ai.applyExcludedAspects(proposal, excluded)
    return when (p.kind) {
        com.spiramindscape.android.data.ai.ProposalKind.NEW_GOAL -> {
            viewModel.createGoal(
                title = p.title,
                description = p.body,
                confidence = p.confidence ?: 5,
                deadline = p.deadline,
            )
            null
        }
        else -> "Open that goal and ask there — I can only create goals from here."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/**
 * The status question as **three short words**, so it fits on one line of pills. The enum's own
 * labels ("All goals" / "Only achieved" / "Only not achieved") carry a qualifier the sheet's
 * heading already supplies, and three of those cannot fit across a phone. The web's
 * `GOAL_STATUS_CHOICES` uses the same three words.
 */
private val STATUS_PILLS = listOf(
    SpiraChoice(StatusFilter.All, "All"),
    SpiraChoice(StatusFilter.Achieved, "Achieved"),
    SpiraChoice(StatusFilter.NotAchieved, "Not achieved"),
)

/**
 * The deadline question, also as **one line of pills** (owner, 2026-08-17). The enum's labels
 * ("Any deadline" / "Has a deadline" / "No deadline") repeat the heading the pills already sit
 * under, and three of those cannot fit across a phone.
 */
private val DEADLINE_PILLS = listOf(
    SpiraChoice(DeadlineFilter.Any, "Any"),
    SpiraChoice(DeadlineFilter.Has, "Deadline"),
    SpiraChoice(DeadlineFilter.None, "No deadline"),
)

/** Sort direction as a two-value question, so it can sit in a column beside the sort key. */
private val DIRECTION_CHOICES = listOf(
    SpiraChoice(true, "Ascending"),
    SpiraChoice(false, "Descending"),
)

@Composable
fun GoalsDashboardScreen(
    state: GoalsUiState,
    visibleGoals: List<GoalSummary>,
    user: AuthUser,
    hasAnyGoals: Boolean = visibleGoals.isNotEmpty(),
    query: String = "",
    onQueryChange: (String) -> Unit = {},
    sortKey: SortKey = SortKey.Recent,
    sortAscending: Boolean = false,
    onSortChange: (SortKey) -> Unit = {},
    onToggleSortDir: () -> Unit = {},
    status: StatusFilter = StatusFilter.All,
    onStatusChange: (StatusFilter) -> Unit = {},
    deadlineFilter: DeadlineFilter = DeadlineFilter.Any,
    onDeadlineFilterChange: (DeadlineFilter) -> Unit = {},
    /** The deadline range's two ends, as ISO instants; "" is an open end. */
    deadlineFrom: String = "",
    onDeadlineFromChange: (String) -> Unit = {},
    deadlineTo: String = "",
    onDeadlineToChange: (String) -> Unit = {},
    /** One confidence 1..10, or 0 for "any". */
    confidence: Int = 0,
    onConfidenceChange: (Int) -> Unit = {},
    creating: Boolean = false,
    onCreateGoal: (title: String, description: String?, confidence: Int, deadline: String?) -> Unit =
        { _, _, _, _ -> },
    onGoalClick: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onLogout: () -> Unit = {},
    /** The header's figure: the account's own page, not the navigation drawer. */
    onOpenSettings: () -> Unit = {},
    onOpenAssistant: () -> Unit = {},
    /** An action that failed without changing the screen (e.g. a create that didn't land). */
    actionError: String? = null,
    onDismissActionError: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showNewGoal by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var filterSheetOpen by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
                SpiraDrawer(
                    user = user,
                    onLogout = onLogout,
                    onClose = { scope.launch { drawerState.close() } },
                )
            },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (searchOpen) {
                    SearchTopBar(
                        query = query,
                        onQueryChange = onQueryChange,
                        onClose = { searchOpen = false; onQueryChange("") },
                    )
                } else {
                    // Shared dark-teal header (design mockup): SPIRA + Search / AI / Profile.
                    SpiraTopBar(
                        onMenu = { scope.launch { drawerState.open() } },
                        onSearch = { searchOpen = true },
                        onAssistant = onOpenAssistant,
                        onProfile = onOpenSettings,
                    )
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showNewGoal = true },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(SpiraIcons.Plus, contentDescription = "New goal") }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                // A create that failed used to leave the sheet open with no explanation.
                SpiraInlineBanner(
                    message = actionError,
                    onDismiss = onDismissActionError,
                )
                // The page's own name and its **Filter & Sort** opener on ONE line (owner,
                // 2026-08-17). The screen used to have no filter chrome at all — `SortMenu` and
                // `FilterMenu` were written and then never mounted — so the sort and status the
                // view model kept could only be changed on the web.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 10.dp, top = 28.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "All goals",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f),
                    )
                    SpiraFilterSortTrigger(
                        // Only the narrowing questions are counted. The sort is not a filter — it
                        // reorders the same goals — so "(1)" for having picked Deadline would say
                        // something is hidden when nothing is.
                        count = listOf(
                            status != StatusFilter.All,
                            deadlineFilter != DeadlineFilter.Any,
                            // One question, whichever of its ends is set: "(2)" for picking a From
                            // and a To would say two things are hidden.
                            deadlineFrom.isNotBlank() || deadlineTo.isNotBlank(),
                            confidence in 1..10,
                        ).count { it },
                        onClick = { filterSheetOpen = true },
                    )
                }

                Box(Modifier.fillMaxSize()) {
                    when {
                        state is GoalsUiState.Loading && !hasAnyGoals ->
                            Centered { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                        state is GoalsUiState.Error && !hasAnyGoals -> Centered {
                            EmptyState(title = state.message) { Button(onClick = onRetry) { Text("Try again") } }
                        }
                        !hasAnyGoals -> Centered {
                            EmptyState(title = "No goals yet", subtitle = "Create your first goal to get started.")
                        }
                        // The list is not empty — the user's own search or filter emptied it, which
                        // is a **warning**, not the blank-page empty state above (owner,
                        // 2026-08-18). It sits at the top where the cards would start, not centred
                        // in the middle of nothing.
                        visibleGoals.isEmpty() -> SpiraNoticeCard(
                            message = "No goals match that search or filter.",
                            kind = SpiraNoticeKind.Warning,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(visibleGoals, key = { it.id }) { goal ->
                                GoalCard(goal, onClick = { onGoalClick(goal.id) })
                            }
                        }
                    }
                }
            }

            if (filterSheetOpen) {
                // The same questions, in the same shapes, as the web's mobile drawer
                // (`AppShell.tsx` → `ToolbarSheet`): status as one line of success pills, then
                // deadline, then sort with its direction in a column beside it.
                SpiraFilterSheet(
                    title = "Filter & Sort",
                    onDismiss = { filterSheetOpen = false },
                    onReset = {
                        onStatusChange(StatusFilter.All)
                        onDeadlineFilterChange(DeadlineFilter.Any)
                        onDeadlineFromChange("")
                        onDeadlineToChange("")
                        onConfidenceChange(0)
                        onSortChange(SortKey.Recent)
                        if (sortAscending) onToggleSortDir()
                    },
                    resetEnabled = status != StatusFilter.All ||
                        deadlineFilter != DeadlineFilter.Any ||
                        deadlineFrom.isNotBlank() ||
                        deadlineTo.isNotBlank() ||
                        confidence in 1..10 ||
                        sortKey != SortKey.Recent ||
                        sortAscending,
                ) {
                    SpiraSheetGroup("Status") {
                        SpiraSheetPills(
                            options = STATUS_PILLS,
                            value = status,
                            onChange = onStatusChange,
                        )
                    }
                    SpiraSheetGroup("Deadline") {
                        // One line of pills, like Status: three short words that would otherwise
                        // take three full-width rows. The words are shortened for the row — the
                        // enum's own "Any deadline" / "Has a deadline" repeat the heading above.
                        SpiraSheetPills(
                            options = DEADLINE_PILLS,
                            value = deadlineFilter,
                            onChange = onDeadlineFilterChange,
                            tone = SpiraBadgeTone.Info,
                        )
                    }
                    // The dates themselves, under the question that only asks whether there is one.
                    // Both are the web's dashboard filters (`AppShell.tsx`), which the phone could
                    // not reach at all until now (owner, 2026-08-18).
                    SpiraSheetGroup("Deadline range") {
                        SpiraSheetDateRange(
                            from = deadlineFrom,
                            to = deadlineTo,
                            onFromChange = onDeadlineFromChange,
                            onToChange = onDeadlineToChange,
                        )
                    }
                    SpiraSheetGroup("Confidence") {
                        SpiraSheetConfidence(value = confidence, onChange = onConfidenceChange)
                    }
                    // Three questions, three shapes (owner, 2026-08-17): filter values are pills,
                    // sort keys are **cards in a grid**, and the direction — a modifier, not a
                    // value — is a **segmented control**. Stacked in two tall columns the sort
                    // pushed Apply off the bottom of a short phone.
                    // **Direction first, then the key** (owner, 2026-08-17): the direction is one
                    // short line and the keys are a grid, so asking the small question first keeps
                    // the sheet from opening on a wall of cards.
                    SpiraSheetGroup("Direction") {
                        SpiraSegmented(
                            options = DIRECTION_CHOICES,
                            value = sortAscending,
                            onChange = { asc -> if (asc != sortAscending) onToggleSortDir() },
                        )
                    }
                    SpiraSheetGroup("Sort by") {
                        SpiraSheetCards(
                            options = SortKey.entries.map { SpiraChoice(it, it.label) },
                            value = sortKey,
                            onChange = onSortChange,
                        )
                    }
                }
            }

            if (showNewGoal) {
                NewGoalSheet(
                    onDismiss = { showNewGoal = false },
                    creating = creating,
                    onCreate = { title, description, confidence, deadline ->
                        onCreateGoal(title, description, confidence, deadline)
                        showNewGoal = false
                    },
                )
            }
        }
    }
}

/** Side navigation menu (uses app colors, not the reference screenshot's). Shared by both screens. */
/**
 * The main drawer, shared by the dashboard and the goal workspace.
 *
 * [goalTitle] is what tells the two apart: opened from inside a goal it adds a **Goal** rubric —
 * the goal's own name over the places inside it — and marks *that* as where the user is. Home
 * stays unmarked there, because Home is the All-goals page, not the goal being worked on; showing
 * it as current from within a goal was simply telling the user the wrong thing.
 */
@Composable
fun SpiraDrawer(
    user: AuthUser,
    onLogout: () -> Unit,
    /** The open goal's name — non-null only when the drawer was opened from inside a goal. */
    goalTitle: String? = null,
    /** Where the user is inside the goal: 0..3 are the GROW phases in order, 4 is Resources. */
    currentPlace: Int = -1,
    onHome: () -> Unit = {},
    onGoalPlace: (Int) -> Unit = {},
    onClose: () -> Unit = {},
) {
    ModalDrawerSheet(
        // Narrow enough that the sheet is a list of places rather than a mostly-empty panel: the
        // longest row is a goal title, which truncates anyway.
        modifier = Modifier.width(244.dp),
        drawerShape = androidx.compose.ui.graphics.RectangleShape, // no rounded corners
        drawerContainerColor = MaterialTheme.spiraExtras.surfaceRaised,
    ) {
        // The sheet itself runs to the top edge, but its content starts below the status bar: the
        // system's clock and battery are always drawn on top of the app window (only a fullscreen
        // mode hides them, which would cost the user their clock), so the wordmark has to step out
        // from under them rather than share the space.
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(vertical = 12.dp),
        ) {
            // Wordmark on the left, a way out on the right. The corner used to be blank, which
            // left the sheet looking unfinished and gave no visible way to dismiss it.
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "spira",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .semantics { contentDescription = "Close menu" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        SpiraIcons.X,
                        contentDescription = null,
                        tint = MaterialTheme.spiraExtras.mutedForeground,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            // Home is the All-goals page: it is only "where you are" when no goal is open.
            DrawerRow(SpiraIcons.Home, "Home", selected = goalTitle == null, onClick = onHome)
            if (goalTitle != null) {
                Spacer(Modifier.height(8.dp))
                // The rubric is the goal's own name. It can't be the word "Goal" — the first
                // place inside it is already called that, and the same word twice reads as a
                // mistake rather than as a heading over its contents.
                DrawerSection(
                    title = goalTitle,
                    items = GoalTab.entries.map { it.label } + "Resources",
                    icon = SpiraIcons.ChartColumn,
                    selectedItem = currentPlace,
                    onItem = onGoalPlace,
                )
            }
            Spacer(Modifier.height(8.dp))
            DrawerSection("About Spira", listOf("How to use", "What is GROW"), SpiraIcons.CircleQuestion)
            // "Knowledge", not "Resources": these are links and reading worth coming back to,
            // while the footer's Resources are the material attached to one goal. Two places with
            // the same name and the same mark had the user expecting one to be the other.
            DrawerSection("Knowledge", listOf("Useful links", "My resources"), SpiraIcons.BookOpen)

            Spacer(Modifier.weight(1f)) // push the account block to the bottom
            HorizontalDivider()
            Text(
                user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.spiraExtras.mutedForeground,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
            )
            Row(
                Modifier.fillMaxWidth().clickable { onLogout() }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(SpiraIcons.LogOut, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Sign out", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * One top-level drawer entry: its mark, then its name. The two are centred against **each other**
 * — the icon used to be sized on its own and sat visibly low beside the text, because a 19dp glyph
 * next to a line of type centres on the line box, not on the glyph.
 */
private val ROW_GLYPH = 22.dp

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val ink = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        // The mark for "you are here": a teal bar hard against the drawer's left edge, outside the
        // row's own padding so it touches the sheet edge the way the reference does. It is drawn
        // behind the row rather than inside it, so adding it doesn't shift the icon or the label.
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .size(width = 3.dp, height = 24.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Icon and label each sit in a box of the SAME height, both centred: that is what makes
            // the text land on the glyph's optical centre. Centring the two composables directly
            // against each other doesn't — a line of type is taller than its letters, and the extra
            // ascent pushed the label a couple of pixels high.
            Box(Modifier.size(ROW_GLYPH), contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = ink,
                )
            }
            Box(Modifier.height(ROW_GLYPH), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    // A goal's name can outrun the sheet. Say so with an ellipsis rather than
                    // letting the word end mid-air, which reads as a layout fault.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A rubric with a vertical line down its left, and its sub-items sitting inside that line.
 *
 * **The rubric itself is never marked as "you are here"** — only the open sub-item is. A heading
 * that carries a place (the goal's own name) is not a place you can be; lighting it as well as the
 * child said the user was in two places at once, and the eye had nothing to land on.
 */
/** The sub-item rail: a hairline for the places you are not, the full lane in Kale for the one you are. */
private val RAIL_LANE = 3.dp
private val RAIL_HAIRLINE = 1.5.dp

@Composable
private fun DrawerSection(
    title: String,
    items: List<String>,
    icon: ImageVector? = null,
    /** Index of the sub-item the user is on, or -1. Switching screens has to show up here. */
    selectedItem: Int = -1,
    onItem: (Int) -> Unit = {},
) {
    Column(Modifier.padding(top = 8.dp)) {
        if (icon != null) {
            DrawerRow(icon, title)
        } else {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp),
            )
        }
        // The rail is drawn **per item**, not as one line down the side, because the open sub-item
        // marks itself by turning its own stretch of it Kale. One shared line could only be one
        // colour.
        //
        // The lane is a fixed width whichever state an item is in, so lighting one up never nudges
        // the words. IntrinsicSize.Min keeps each segment exactly as tall as its own row — a plain
        // fillMaxHeight here once made the Row swallow the whole screen and pushed the Knowledge
        // section and the account block off the bottom.
        Column(Modifier.padding(start = 26.dp)) {
            items.forEachIndexed { index, item ->
                val here = index == selectedItem
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                        .clickable { onItem(index) },
                ) {
                    Box(Modifier.width(RAIL_LANE), contentAlignment = Alignment.CenterStart) {
                        Box(
                            Modifier
                                .width(if (here) RAIL_LANE else RAIL_HAIRLINE)
                                .fillMaxHeight()
                                .background(
                                    if (here) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.spiraExtras.border
                                    },
                                ),
                        )
                    }
                    Text(
                        item,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (here) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (here) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.spiraExtras.mutedForeground
                        },
                        modifier = Modifier.padding(
                            start = 16.dp,
                            top = 10.dp,
                            bottom = 10.dp,
                            end = 20.dp,
                        ),
                    )
                }
            }
        }
    }
}

/** Full-width search bar that overlays the header (opened from the header search icon). */
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    // Searching keeps the teal header and swaps the wordmark for a white search pill — the same
    // field the goal workspace uses. It used to drop to a white bar, which read as a different
    // screen appearing rather than the header changing mode.
    Row(
        Modifier
            .fillMaxWidth()
            .background(Kale600)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SpiraSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Search for goals",
            modifier = Modifier.weight(1f),
        )
        // The same white-disc-with-a-teal-cross the workspace header uses, so the two teal bars
        // close the same way.
        HeaderCircleClose("Close search", onClose)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GoalCard(goal: GoalSummary, onClick: () -> Unit) {
    // Confidence is hidden by default; a long-press reveals it as a banner at the top of the card.
    var showConfidence by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = { showConfidence = !showConfidence },
        ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.spiraExtras.surfaceRaised),
        border = BorderStroke(1.dp, MaterialTheme.spiraExtras.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            AnimatedVisibility(visible = showConfidence) { ConfidenceBanner(goal.confidence) }
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Progress ring stays vertically centered regardless of card height.
                CircularProgress(goal.progress, Modifier.align(Alignment.CenterVertically))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Title and target count share the top row; the title wraps (max 2 lines,
                    // ellipsis) so it never overlaps the count.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            text = goal.title.ifBlank { "Untitled goal" },
                            style = MaterialTheme.typography.titleMedium,
                            // Goal name on the All-goals cards: GCentra Medium (500), matching web.
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${goal.targetCount} target${if (goal.targetCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.spiraExtras.mutedForeground,
                        )
                    }
                    val bottom = when {
                        goal.achieved -> "Achieved"
                        goal.deadline != null -> deadlineCountdown(goal.deadline)
                        else -> null
                    }
                    if (bottom != null) {
                        Text(bottom, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.spiraExtras.mutedForeground)
                    }
                }
            }
        }
    }
}

/** Full-width banner across the top of a card (long-press) showing the goal's confidence. */
@Composable
private fun ConfidenceBanner(confidence: Int) {
    val bg = confidenceColor(confidence)
    // Pick black or white text by the background's luminance so it's always readable.
    val textColor = if (bg.luminance() > 0.5f) androidx.compose.ui.graphics.Color(0xFF1C1C1C)
    else androidx.compose.ui.graphics.Color.White
    Box(
        Modifier.fillMaxWidth().background(bg).padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Confidence $confidence/10",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}
