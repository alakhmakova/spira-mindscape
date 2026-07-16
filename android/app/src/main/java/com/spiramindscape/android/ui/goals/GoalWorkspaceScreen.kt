package com.spiramindscape.android.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spiramindscape.android.data.goals.ApolloGoalsRepository
import com.spiramindscape.android.data.goals.GoalDetail
import com.spiramindscape.android.data.goals.TargetItem
import com.spiramindscape.android.data.goals.TextItem
import com.spiramindscape.android.data.net.Network
import kotlin.math.roundToInt

@Composable
fun GoalWorkspaceRoute(goalId: String, onBack: () -> Unit) {
    val viewModel: GoalWorkspaceViewModel = viewModel(
        factory = GoalWorkspaceViewModel.factory(goalId, ApolloGoalsRepository(Network.apollo)),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Re-fetch on resume so target changes from another device show up.
    LifecycleResumeEffect(Unit) {
        if (state is GoalUiState.Content) viewModel.load()
        onPauseOrDispose { }
    }

    GoalWorkspaceScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onSetDone = viewModel::setTargetDone,
        onSetNumeric = viewModel::setNumericCurrent,
        onToggleChecklistItem = viewModel::toggleChecklistItem,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalWorkspaceScreen(
    state: GoalUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSetDone: (targetId: String, done: Boolean) -> Unit,
    onSetNumeric: (targetId: String, current: Double) -> Unit,
    onToggleChecklistItem: (targetId: String, itemId: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goal", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                GoalUiState.Loading -> Centered { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                is GoalUiState.Error -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry) { Text("Try again") }
                    }
                }
                is GoalUiState.Content -> GoalContent(
                    goal = state.goal,
                    onSetDone = onSetDone,
                    onSetNumeric = onSetNumeric,
                    onToggleChecklistItem = onToggleChecklistItem,
                )
            }
        }
    }
}

@Composable
private fun GoalContent(
    goal: GoalDetail,
    onSetDone: (String, Boolean) -> Unit,
    onSetNumeric: (String, Double) -> Unit,
    onToggleChecklistItem: (String, String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(goal.title.ifBlank { "Untitled goal" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { goal.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "${(goal.progress * 100).roundToInt()}%  ·  confidence ${goal.confidence}/10" +
                        (goal.deadline?.take(10)?.let { "  ·  due $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (goal.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(goal.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        item { SectionHeader("Targets") }
        if (goal.targets.isEmpty()) {
            item { EmptyLine("No targets yet.") }
        } else {
            items(goal.targets, key = { "target-${it.id}" }) { target ->
                TargetCard(target, onSetDone, onSetNumeric, onToggleChecklistItem)
            }
        }

        item { SectionHeader("Reality") }
        item { RealityBlock("Actions taken", goal.actions) }
        item { RealityBlock("Obstacles", goal.obstacles) }

        item { SectionHeader("Options") }
        if (goal.options.isEmpty()) item { EmptyLine("No options yet.") }
        else items(goal.options, key = { "option-${it.id}" }) { opt ->
            Text(
                (if (opt.selected) "● " else "○ ") + opt.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (opt.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
        }

        item { SectionHeader("Resources") }
        if (goal.resources.isEmpty()) item { EmptyLine("No resources yet.") }
        else items(goal.resources, key = { "resource-${it.id}" }) { res ->
            Text("• ${res.title ?: res.type} (${res.type})", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TargetCard(
    target: TargetItem,
    onSetDone: (String, Boolean) -> Unit,
    onSetNumeric: (String, Double) -> Unit,
    onToggleChecklistItem: (String, String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            when (target) {
                is TargetItem.Binary -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(checked = target.done, onCheckedChange = { onSetDone(target.id, it) })
                    Text(target.title, style = MaterialTheme.typography.bodyLarge)
                }

                is TargetItem.Numeric -> Column {
                    Text(target.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { onSetNumeric(target.id, target.current - 1) }) { Text("−") }
                        val total = target.total?.let { " / ${trim(it)}" } ?: ""
                        val unit = target.unit?.let { " $it" } ?: ""
                        Text("${trim(target.current)}$total$unit", style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = { onSetNumeric(target.id, target.current + 1) }) { Text("+") }
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { target.progress }, modifier = Modifier.fillMaxWidth())
                }

                is TargetItem.Checklist -> Column {
                    Text(target.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    target.items.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.done, onCheckedChange = { onToggleChecklistItem(target.id, item.id) })
                            Text(item.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                is TargetItem.Other -> Column {
                    Text(target.title, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { target.progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RealityBlock(label: String, items: List<TextItem>) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        if (items.isEmpty()) EmptyLine("None yet.")
        else items.forEach { Text("• ${it.text}", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

/** Show a number without a trailing ".0" for whole values. */
private fun trim(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
