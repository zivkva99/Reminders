package com.ziv.reminders.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Re-reads current state on every resume (first composition, backgrounding, notification
    // tap) so the dashboard never shows stale data — see final-review Issue 2/4.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Today", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        if (!uiState.isLoaded) return@Column
        uiState.habits.forEach { habit ->
            HabitRow(habit = habit, onIncrement = { viewModel.onIncrement(habit.instanceId) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HabitRow(habit: HabitRowUiState, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onIncrement),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
            Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = if (habit.completed) "✓ ${habit.statusText}" else habit.statusText,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
