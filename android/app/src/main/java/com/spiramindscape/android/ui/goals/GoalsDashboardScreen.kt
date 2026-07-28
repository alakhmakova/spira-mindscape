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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.data.goals.GoalSummary
import com.spiramindscape.android.data.goals.GoalsStore
import com.spiramindscape.android.ui.components.CircularProgress
import com.spiramindscape.android.ui.components.EmptyState
import com.spiramindscape.android.ui.components.SpiraDropdownMenu
import com.spiramindscape.android.ui.components.SpiraMenuDivider
import com.spiramindscape.android.ui.components.SpiraMenuItem
import com.spiramindscape.android.ui.components.SpiraTopBar
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.confidenceColor
import com.spiramindscape.android.ui.theme.spiraExtras
import com.spiramindscape.android.ui.util.deadlineCountdown
import kotlinx.coroutines.launch

@Composable
fun GoalsRoute(user: AuthUser, onGoalClick: (String) -> Unit, onLogout: () -> Unit) {
    val viewModel: GoalsViewModel = viewModel(factory = GoalsViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val allGoals by GoalsStore.goals.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sortKey by viewModel.sortKey.collectAsStateWithLifecycle()
    val sortAscending by viewModel.sortAscending.collectAsStateWithLifecycle()
    val status by viewModel.statusFilter.collectAsStateWithLifecycle()
    val deadlineFilter by viewModel.deadlineFilter.collectAsStateWithLifecycle()

    val visibleGoals = remember(allGoals, query, sortKey, sortAscending, status, deadlineFilter) {
        applyGoalView(allGoals, query, sortKey, sortAscending, status, deadlineFilter)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    GoalsDashboardScreen(
        state = state,
        visibleGoals = visibleGoals,
        hasAnyGoals = allGoals.isNotEmpty(),
        user = user,
        query = query,
        onQueryChange = { viewModel.query.value = it },
        sortKey = sortKey,
        sortAscending = sortAscending,
        onSortChange = { viewModel.sortKey.value = it },
        onToggleSortDir = { viewModel.sortAscending.value = !viewModel.sortAscending.value },
        status = status,
        onStatusChange = { viewModel.statusFilter.value = it },
        deadlineFilter = deadlineFilter,
        onDeadlineFilterChange = { viewModel.deadlineFilter.value = it },
        creating = creating,
        onCreateGoal = { title, description, confidence, deadline ->
            viewModel.createGoal(title, description, confidence, deadline)
        },
        onGoalClick = onGoalClick,
        onRetry = viewModel::load,
        onLogout = onLogout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
    creating: Boolean = false,
    onCreateGoal: (title: String, description: String?, confidence: Int, deadline: String?) -> Unit =
        { _, _, _, _ -> },
    onGoalClick: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showNewGoal by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { SpiraDrawer(user, onLogout) },
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
                        onAssistant = { /* AI assistant — no screen yet */ },
                        onProfile = { scope.launch { drawerState.open() } },
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
                Text(
                    "All goals",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                )

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
                        visibleGoals.isEmpty() -> Centered {
                            EmptyState(title = "No goals match", subtitle = "Try a different search or filter.")
                        }
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
@Composable
fun SpiraDrawer(user: AuthUser, onLogout: () -> Unit) {
    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerShape = androidx.compose.ui.graphics.RectangleShape, // no rounded corners
        drawerContainerColor = MaterialTheme.spiraExtras.surfaceRaised,
    ) {
        Column(Modifier.fillMaxHeight().padding(vertical = 12.dp)) {
            Text(
                "spira",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 16.dp),
            )
            // Home — house icon + label, no background.
            Row(
                Modifier.fillMaxWidth().clickable { }.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(SpiraIcons.Home, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                Text("Home", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(8.dp))
            DrawerSection("About Spira", listOf("How to use", "What is GROW"))
            DrawerSection("Resources", listOf("Useful links", "My resources"))

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

/** A rubric with a vertical line down its left, and its sub-items sitting inside that line. */
@Composable
private fun DrawerSection(title: String, items: List<String>) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 6.dp),
        )
        // IntrinsicSize.Min: the line is only as tall as the items next to it. (A plain
        // fillMaxHeight here once made the Row swallow the whole screen and pushed the
        // Resources section + account block off-screen.)
        Row(Modifier.padding(start = 26.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.spiraExtras.border),
            )
            Column {
                items.forEach { item ->
                    Text(
                        item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.spiraExtras.mutedForeground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { }
                            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 20.dp),
                    )
                }
            }
        }
    }
}

/** Full-width search bar that overlays the header (opened from the header search icon). */
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search for goals") },
                leadingIcon = { Icon(SpiraIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
            )
            IconButton(onClick = onClose) { Icon(SpiraIcons.X, contentDescription = "Close search") }
        }
    }
}

@Composable
private fun SortMenu(
    sortKey: SortKey,
    sortAscending: Boolean,
    onSortChange: (SortKey) -> Unit,
    onToggleSortDir: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(SpiraIcons.ArrowUpDown, contentDescription = "Sort", modifier = Modifier.size(24.dp))
        }
        SpiraDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortKey.entries.forEach { key ->
                SpiraMenuItem(
                    label = key.label,
                    onClick = { onSortChange(key); expanded = false },
                    selected = key == sortKey,
                )
            }
            SpiraMenuDivider()
            SpiraMenuItem(
                label = if (sortAscending) "Ascending" else "Descending",
                onClick = { onToggleSortDir(); expanded = false },
                icon = if (sortAscending) SpiraIcons.ArrowUp else SpiraIcons.ArrowDown,
            )
        }
    }
}

@Composable
private fun FilterMenu(
    status: StatusFilter,
    onStatusChange: (StatusFilter) -> Unit,
    deadlineFilter: DeadlineFilter,
    onDeadlineFilterChange: (DeadlineFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = status != StatusFilter.All || deadlineFilter != DeadlineFilter.Any
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                SpiraIcons.SlidersHorizontal,
                contentDescription = "Filter",
                modifier = Modifier.size(24.dp),
                tint = if (active) MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
            )
        }
        SpiraDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            StatusFilter.entries.forEach { s ->
                SpiraMenuItem(
                    label = s.label,
                    onClick = { onStatusChange(s); expanded = false },
                    selected = s == status,
                )
            }
            SpiraMenuDivider()
            DeadlineFilter.entries.forEach { d ->
                SpiraMenuItem(
                    label = d.label,
                    onClick = { onDeadlineFilterChange(d); expanded = false },
                    selected = d == deadlineFilter,
                )
            }
        }
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
    val textColor = if (bg.luminance() > 0.5f) androidx.compose.ui.graphics.Color(0xFF1A1A1A)
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
