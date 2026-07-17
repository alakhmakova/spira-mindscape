package com.spiramindscape.android.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spiramindscape.android.data.auth.AuthUser
import com.spiramindscape.android.data.goals.ApolloGoalsRepository
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
import com.spiramindscape.android.ui.components.DeadlineField
import com.spiramindscape.android.ui.components.EmptyLine
import com.spiramindscape.android.ui.components.FieldLabel
import com.spiramindscape.android.ui.components.InlineEditText
import com.spiramindscape.android.ui.components.InlineItemRow
import com.spiramindscape.android.ui.components.SectionLabel
import com.spiramindscape.android.ui.components.SpiraCard
import com.spiramindscape.android.ui.components.SpiraDropdownMenu
import com.spiramindscape.android.ui.components.SpiraLinearProgress
import com.spiramindscape.android.ui.icons.SpiraIcons
import com.spiramindscape.android.ui.theme.spiraExtras
import kotlin.math.roundToInt

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
    val onRemoveOption: (optionId: String) -> Unit = {},
    val onAddResource: (
        type: String, title: String?, body: String?, url: String?,
        name: String?, email: String?, role: String?, phone: String?,
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    val onUpdateResource: (
        id: String, title: String?, body: String?, url: String?,
        name: String?, email: String?, role: String?, phone: String?,
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
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
            onRemoveOption = viewModel::removeOption,
            onAddResource = viewModel::addResource,
            onUpdateResource = { id, title, body, url, name, email, role, phone ->
                viewModel.updateResource(id, title, body, url, name, email, role, phone)
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
    var tab by remember { mutableStateOf(GoalTab.Goal) }
    // Sort/filter in this header apply to the TARGETS tab.
    var targetSort by remember { mutableStateOf(TargetSort.Name) }
    var targetSortAscending by remember { mutableStateOf(true) }
    var targetFilter by remember { mutableStateOf(TargetFilter.All) }
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
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(SpiraIcons.Menu, contentDescription = "Menu", modifier = Modifier.width(24.dp).height(24.dp))
                        }
                    },
                    title = {
                        Text(
                            "spira",
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    actions = {
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(SpiraIcons.Search, contentDescription = "Search goals", modifier = Modifier.width(24.dp).height(24.dp))
                        }
                        TargetSortMenu(targetSort, targetSortAscending, { targetSort = it }, { targetSortAscending = !targetSortAscending })
                        TargetFilterMenu(targetFilter) { targetFilter = it }
                        if (state is GoalUiState.Content) {
                            IconButton(onClick = { confirmDeleteGoal = true }) {
                                Icon(
                                    SpiraIcons.Trash,
                                    contentDescription = "Delete goal",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.width(24.dp).height(24.dp),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.spiraExtras.surfaceRaised) {
                    GoalTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon(), contentDescription = null, modifier = Modifier.width(22.dp).height(22.dp)) },
                            label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.spiraExtras.primarySoft,
                            ),
                        )
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = actions.onBack,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(SpiraIcons.ArrowLeft, contentDescription = "Back to all goals") }
            },
            floatingActionButtonPosition = FabPosition.Start,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (state) {
                    GoalUiState.Loading -> Centered { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                    is GoalUiState.Error -> Centered {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.message, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = actions.onRetry) { Text("Try again") }
                        }
                    }
                    is GoalUiState.Content -> GoalTabContent(
                        goal = state.goal,
                        tab = tab,
                        actions = actions,
                        targetSort = targetSort,
                        targetSortAscending = targetSortAscending,
                        targetFilter = targetFilter,
                    )
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
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(key.label) },
                    onClick = { onSortChange(key); expanded = false },
                    trailingIcon = if (key == sort) {
                        { Icon(SpiraIcons.Check, contentDescription = "Selected", modifier = Modifier.width(16.dp).height(16.dp)) }
                    } else null,
                )
            }
            androidx.compose.material3.HorizontalDivider()
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(if (ascending) "Ascending" else "Descending") },
                onClick = { onToggleDir(); expanded = false },
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
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(f.label) },
                    onClick = { onChange(f); expanded = false },
                    trailingIcon = if (f == filter) {
                        { Icon(SpiraIcons.Check, contentDescription = "Selected", modifier = Modifier.width(16.dp).height(16.dp)) }
                    } else null,
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
) {
    var showNewTarget by remember { mutableStateOf(false) }
    var showNewResource by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (tab) {
            GoalTab.Goal -> {
                item { GoalHeader(goal, actions) }
            }
            GoalTab.Reality -> {
                item { SectionLabel("Reality") }
                item { RealityBlock("Actions taken", "actions", goal.actions, actions) }
                item { RealityBlock("Obstacles", "obstacles", goal.obstacles, actions) }
            }
            GoalTab.Resources -> {
                item { SectionHeaderRow("Resources") { TextButton(onClick = { showNewResource = true }) { Text("Add") } } }
                if (goal.resources.isEmpty()) item { EmptyLine("No resources yet.") }
                else items(goal.resources, key = { "resource-${it.id}" }) { res ->
                    ResourceRow(res, actions)
                }
            }
            GoalTab.Options -> {
                item { SectionLabel("Options") }
                item { OptionsBlock(goal, actions) }
            }
            GoalTab.Targets -> {
                item { SectionHeaderRow("Targets") { TextButton(onClick = { showNewTarget = true }) { Text("Add") } } }
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
    if (showNewResource) {
        NewResourceSheet(
            onDismiss = { showNewResource = false },
            onSubmit = { type, title, body, url, name, email, role, phone ->
                actions.onAddResource(type, title, body, url, name, email, role, phone)
                showNewResource = false
            },
        )
    }
}

@Composable
private fun GoalHeader(goal: GoalDetail, actions: GoalWorkspaceActions) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InlineEditText(
            value = goal.title,
            onCommit = actions.onSetGoalTitle,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Goal title",
            textStyle = MaterialTheme.typography.headlineSmall,
            // Multiline so a long title wraps and is fully visible (no horizontal scrolling).
            singleLine = false,
            required = true,
        )
        InlineEditText(
            value = goal.description,
            onCommit = actions.onSetGoalDescription,
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Add a description",
            singleLine = false,
            minLines = 2,
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        SpiraLinearProgress(goal.progress, Modifier.fillMaxWidth())
        Text(
            "${(goal.progress * 100).roundToInt()}%" + if (goal.achieved) "  ·  Achieved" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Confidence: ${goal.confidence}/10")
            ConfidenceStepper(goal.confidence, actions.onSetConfidence)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Deadline")
            DeadlineField(goal.deadline, actions.onSetDeadline)
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
                    textStyle = MaterialTheme.typography.bodyLarge,
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

@Composable
private fun RealityBlock(label: String, kind: String, items: List<com.spiramindscape.android.data.goals.TextItem>, actions: GoalWorkspaceActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        items.forEach { item ->
            InlineItemRow(
                text = item.text,
                onCommit = { actions.onUpdateReality(kind, item.id, it) },
                onRemove = { actions.onRemoveReality(kind, item.id) },
            )
        }
        AddItemRow("Add ${label.lowercase()}", onAdd = { actions.onAddReality(kind, it) })
    }
}

@Composable
private fun OptionsBlock(goal: GoalDetail, actions: GoalWorkspaceActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        goal.options.forEach { opt ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = opt.selected, onClick = { actions.onSelectOption(opt.id) })
                InlineEditText(
                    value = opt.text,
                    onCommit = { actions.onSetOptionText(opt.id, it) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    required = true,
                )
                IconButton(onClick = { actions.onRemoveOption(opt.id) }) {
                    Icon(SpiraIcons.X, contentDescription = "Remove option")
                }
            }
        }
        AddItemRow("Add an option", onAdd = { actions.onAddOption(it) })
    }
}

@Composable
private fun ResourceRow(res: ResourceItem, actions: GoalWorkspaceActions) {
    var editing by remember { mutableStateOf(false) }
    SpiraCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(res.title ?: res.name ?: res.type, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                val subtitle = when (res.type) {
                    "link" -> res.url
                    "email" -> res.email
                    "note" -> res.body?.take(60)
                    else -> res.type
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = { editing = true }) { Text("Edit") }
            IconButton(onClick = { actions.onRemoveResource(res.id) }) {
                Icon(SpiraIcons.X, contentDescription = "Remove resource")
            }
        }
    }
    if (editing) {
        NewResourceSheet(
            onDismiss = { editing = false },
            initial = res,
            onSubmit = { _, title, body, url, name, email, role, phone ->
                actions.onUpdateResource(res.id, title, body, url, name, email, role, phone)
                editing = false
            },
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
private fun trim(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
