package com.ziv.reminders.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.HabitStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onOpenExercise: () -> Unit = {}, onOpenActivity: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Re-reads current state on every resume (first composition, backgrounding, notification
    // tap) so the dashboard never shows stale data — see Plan 1's final-review Issue 2/4.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today") },
                actions = {
                    IconButton(onClick = onOpenActivity) {
                        Icon(imageVector = Icons.Default.List, contentDescription = "Activity")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            if (!uiState.isLoaded) return@Column
            uiState.habits.forEach { habit ->
                val context = LocalContext.current
                HabitRow(
                    habit = habit,
                    onIncrement = { viewModel.onIncrement(habit.instanceId) },
                    onToggleTimer = { displayedRemainingSeconds ->
                        viewModel.onToggleTimer(habit.instanceId, context, displayedRemainingSeconds)
                    },
                    onResetReadingToday = {
                        coroutineScope.launch { viewModel.onResetReadingToday(habit.instanceId, context) }
                    },
                    fetchReadingSessionCountToday = { viewModel.readingSessionCountToday(habit.instanceId) },
                    onMarkRead = {
                        coroutineScope.launch {
                            viewModel.onMarkRead(habit.instanceId)
                            val result = snackbarHostState.showSnackbar(
                                message = "Marked as read",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.onUndoMarkRead(habit.instanceId)
                            }
                        }
                    },
                    onOpenExercise = onOpenExercise,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HabitRow(
    habit: HabitRowUiState,
    onIncrement: () -> Unit,
    onToggleTimer: (Int) -> Unit,
    onResetReadingToday: () -> Unit,
    fetchReadingSessionCountToday: suspend () -> Int,
    onMarkRead: () -> Unit,
    onOpenExercise: () -> Unit,
) {
    when (habit.status) {
        is HabitStatus.CounterStatus -> CounterHabitRow(habit, habit.status, onIncrement, onOpenExercise)
        is HabitStatus.TimerStatus -> TimerHabitRow(habit, habit.status, onToggleTimer, onResetReadingToday, fetchReadingSessionCountToday)
        is HabitStatus.ScheduleCursorStatus -> ScheduleCursorHabitRow(habit, habit.status, onMarkRead)
    }
}

// Dispatch is by instance ID, not by HabitKind — a hypothetical future second
// COUNTER-kind habit must not be silently redirected into the Exercise flow just because
// it shares HabitKind.COUNTER (see DashboardDispatchTest).
fun shouldNavigateToExerciseDetail(instanceId: Long): Boolean = instanceId == EXERCISE_HABIT_INSTANCE_ID

@Composable
private fun CounterHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.CounterStatus,
    onIncrement: () -> Unit,
    onOpenExercise: () -> Unit,
) {
    val onClick = if (shouldNavigateToExerciseDetail(habit.instanceId)) onOpenExercise else onIncrement
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.TimerStatus,
    onToggleTimer: (Int) -> Unit,
    onResetToday: () -> Unit,
    fetchSessionCountToday: suspend () -> Int,
) {
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
    var showResetConfirm by remember { mutableStateOf(false) }
    var sessionCountToday by remember { mutableStateOf<Int?>(null) }
    val rowScope = rememberCoroutineScope()

    Row(
        // Pass the currently-displayed (ticked-down) value, not status.remainingSeconds — the
        // ViewModel's optimistic flip uses this to avoid visually resetting to the stale
        // pre-session baseline the instant Stop is tapped. Long-press triggers the destructive
        // reset confirm dialog instead of the row's normal tap-to-toggle behavior — the session
        // count is fetched first (added during /autoplan review: a destructive, irreversible
        // action shouldn't be confirmed blind) so the dialog can show what's about to be lost.
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { onToggleTimer(displaySeconds) },
            onLongClick = {
                rowScope.launch {
                    sessionCountToday = fetchSessionCountToday()
                    showResetConfirm = true
                }
            },
        ),
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

    if (showResetConfirm) {
        val count = sessionCountToday ?: 0
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset today?") },
            text = {
                Text(
                    if (count > 0) {
                        "This deletes $count session${if (count == 1) "" else "s"} logged today and clears today's progress. This can't be undone."
                    } else {
                        "This clears today's progress. This can't be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showResetConfirm = false; onResetToday() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ScheduleCursorHabitRow(habit: HabitRowUiState, status: HabitStatus.ScheduleCursorStatus, onMarkRead: () -> Unit) {
    // Once the schedule is exhausted there's nothing left to mark read — the row stops being
    // tappable so a stray tap can't advance the cursor past the end or credit a phantom streak
    // day (see ScheduleCursorRepository.markRead's matching finished-state no-op guard).
    val rowModifier = Modifier.fillMaxWidth().let { if (!status.finished) it.clickable(onClick = onMarkRead) else it }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
            Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
        }
        val chapterText = if (status.finished) "Finished" else "${status.book} ${status.chapterHeb}"
        Text(
            text = if (status.completed) "✓ $chapterText" else chapterText,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
