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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ziv.reminders.data.HabitStatus
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Re-reads current state on every resume (first composition, backgrounding, notification
    // tap) so the dashboard never shows stale data — see Plan 1's final-review Issue 2/4.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Today", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        if (!uiState.isLoaded) return@Column
        uiState.habits.forEach { habit ->
            val context = LocalContext.current
            HabitRow(
                habit = habit,
                onIncrement = { viewModel.onIncrement(habit.instanceId) },
                onToggleTimer = { displayedRemainingSeconds ->
                    viewModel.onToggleTimer(habit.instanceId, context, displayedRemainingSeconds)
                },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HabitRow(habit: HabitRowUiState, onIncrement: () -> Unit, onToggleTimer: (Int) -> Unit) {
    when (habit.status) {
        is HabitStatus.CounterStatus -> CounterHabitRow(habit, habit.status, onIncrement)
        is HabitStatus.TimerStatus -> TimerHabitRow(habit, habit.status, onToggleTimer)
        // Real row rendering is Task 5's scope expansion; this task only needs the module to
        // compile again now that HabitStatus has a third subtype (Kotlin requires this `when`
        // to be exhaustive even though it's used as a statement, not an expression).
        is HabitStatus.ScheduleCursorStatus -> Unit
    }
}

@Composable
private fun CounterHabitRow(habit: HabitRowUiState, status: HabitStatus.CounterStatus, onIncrement: () -> Unit) {
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
            text = if (status.completed) "✓ ${status.current}/${status.goal}" else "${status.current}/${status.goal}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun TimerHabitRow(habit: HabitRowUiState, status: HabitStatus.TimerStatus, onToggleTimer: (Int) -> Unit) {
    // Live 1Hz countdown while running — the ViewModel/DB only update on Start/Stop/Completion,
    // not every second; the visual tick lives here and resets whenever the underlying status
    // (a new baseline remainingSeconds, or isRunning flipping) actually changes. Mirrors
    // ReadBook's real HomeScreen InProgressContent mechanism.
    var displaySeconds by remember(status) { mutableIntStateOf(status.remainingSeconds) }
    LaunchedEffect(status) {
        while (status.isRunning && displaySeconds > 0) {
            delay(1000)
            displaySeconds -= 1
        }
    }

    Row(
        // Pass the currently-displayed (ticked-down) value, not status.remainingSeconds — the
        // ViewModel's optimistic flip uses this to avoid visually resetting to the stale
        // pre-session baseline the instant Stop is tapped.
        modifier = Modifier.fillMaxWidth().clickable(onClick = { onToggleTimer(displaySeconds) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
            Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
        }
        val minutes = displaySeconds / 60
        val seconds = displaySeconds % 60
        Text(
            text = if (status.completed) "✓" else "%d:%02d".format(minutes, seconds),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
