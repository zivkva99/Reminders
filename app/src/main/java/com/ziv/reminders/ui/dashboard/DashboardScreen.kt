package com.ziv.reminders.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
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
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenExercise: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenExerciseStats: () -> Unit = {},
    onOpenReadingStats: () -> Unit = {},
    onOpenTanakhStats: () -> Unit = {},
) {
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
                    onIncrement = {
                        coroutineScope.launch {
                            viewModel.onIncrement(habit.instanceId)
                            val result = snackbarHostState.showSnackbar(
                                message = "Incremented",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.onUndoIncrement(habit.instanceId)
                            }
                        }
                    },
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
                    onOpenExerciseStats = onOpenExerciseStats,
                    onOpenReadingStats = onOpenReadingStats,
                    onOpenTanakhStats = onOpenTanakhStats,
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
    onOpenExerciseStats: () -> Unit,
    onOpenReadingStats: () -> Unit,
    onOpenTanakhStats: () -> Unit,
) {
    when (habit.status) {
        is HabitStatus.CounterStatus -> CounterHabitRow(habit, habit.status, onIncrement, onOpenExercise, onOpenExerciseStats)
        is HabitStatus.TimerStatus -> TimerHabitRow(habit, habit.status, onToggleTimer, onResetReadingToday, fetchReadingSessionCountToday, onOpenReadingStats)
        is HabitStatus.ScheduleCursorStatus -> ScheduleCursorHabitRow(habit, habit.status, onMarkRead, onOpenTanakhStats)
    }
}

// Dispatch is by instance ID, not by HabitKind — a hypothetical future second
// COUNTER-kind habit must not be silently offered the Exercise long-press menu just because
// it shares HabitKind.COUNTER (see DashboardDispatchTest). Renamed from
// shouldNavigateToExerciseDetail: it no longer gates tap-navigation (short-tap is now a pure
// increment for every Counter-kind habit) — it gates long-press-menu eligibility instead.
fun hasExerciseDetailMenu(instanceId: Long): Boolean = instanceId == EXERCISE_HABIT_INSTANCE_ID

// Small, deliberately generic long-press menu mechanism — a row supplies a title and a list of
// labeled actions, this renders them as an AlertDialog with one button per option plus Cancel.
// Chosen over a one-off dialog hardcoded in CounterHabitRow so any future row that needs a
// "pick where to go" long-press can reuse this without new bespoke dialog code — the one
// deliberate exception to this codebase's usual anti-premature-generalization stance (no second
// use case exists yet; kept intentionally small — one data class, one composable, no config
// knobs beyond title/options/onDismiss).
private data class RowMenuOption(val label: String, val onSelect: () -> Unit, val isDestructive: Boolean = false)

@Composable
private fun RowLongPressMenu(title: String, options: List<RowMenuOption>, onDismiss: () -> Unit) {
    // Cancel lives in the body alongside the real options (not confirmButton) — corrected
    // during /autoplan design review: AlertDialog's confirmButton slot renders with more visual
    // emphasis than plain body TextButtons, so putting Cancel there (the original draft) made
    // "do nothing" look like the recommended choice instead of the N real options. confirmButton
    // is a required parameter but doesn't have to render anything, so it's left empty.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    // isDestructive options (added during /autoplan Design review) render in the
                    // error color — matching the reset-confirm dialog's own destructive-button
                    // convention — so a habit-driven "tap the top option" reflex from another
                    // row's menu doesn't land on a destructive action unstyled.
                    TextButton(
                        onClick = { onDismiss(); option.onSelect() },
                        colors = if (option.isDestructive) {
                            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.textButtonColors()
                        },
                    ) { Text(option.label) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        confirmButton = {},
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CounterHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.CounterStatus,
    onIncrement: () -> Unit,
    onOpenExercise: () -> Unit,
    onOpenExerciseStats: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val isExercise = hasExerciseDetailMenu(habit.instanceId)

    // Noted during /autoplan design review, not fixed: if the user long-presses and navigates
    // away while a prior tap's undo-snackbar coroutine is still pending (within its ~4s window),
    // leaving composition cancels that coroutine scope — the Undo action is silently lost, no
    // data corruption, just a missed correction window. Acceptable for a personal app; a stray
    // extra increment is a one-tap fix on the next visit.
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onIncrement,
            onLongClick = if (isExercise) { { showMenu = true } } else null,
        ),
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

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            options = listOf(
                RowMenuOption("Counter", onOpenExercise),
                RowMenuOption("Statistics", onOpenExerciseStats),
            ),
            onDismiss = { showMenu = false },
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
    onOpenReadingStats: () -> Unit,
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
    var showMenu by remember { mutableStateOf(false) }
    val rowScope = rememberCoroutineScope()

    Row(
        // Pass the currently-displayed (ticked-down) value, not status.remainingSeconds — the
        // ViewModel's optimistic flip uses this to avoid visually resetting to the stale
        // pre-session baseline the instant Stop is tapped. Long-press now opens a menu
        // (Reset today / Statistics) instead of jumping straight to the reset confirm dialog.
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { onToggleTimer(displaySeconds) },
            onLongClick = { showMenu = true },
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

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            // Added during /autoplan Design review: Statistics listed first (matches Exercise's
            // safe-option-first "Counter" and Tanakh's single-entry menu), destructive "Reset
            // today" listed second and marked isDestructive so it renders in the error color —
            // the prior draft put the destructive action in the same top slot every other row
            // uses for a benign action, risking a habit-driven mis-tap.
            options = listOf(
                RowMenuOption("Statistics", onOpenReadingStats),
                // Fetches the session count first (same as the row's previous direct
                // long-click behavior) so the confirm dialog below can show what's about to
                // be lost — a destructive, irreversible action shouldn't be confirmed blind.
                // isDestructive is named (not trailing-lambda) because it's no longer the last
                // parameter once isDestructive follows it — see Step 5's RowMenuOption reorder.
                RowMenuOption(
                    "Reset today",
                    onSelect = {
                        rowScope.launch {
                            sessionCountToday = fetchSessionCountToday()
                            showResetConfirm = true
                        }
                    },
                    isDestructive = true,
                ),
            ),
            onDismiss = { showMenu = false },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduleCursorHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.ScheduleCursorStatus,
    onMarkRead: () -> Unit,
    onOpenTanakhStats: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    // Once the schedule is exhausted there's nothing left to mark read — tap is a no-op so a
    // stray tap can't advance the cursor past the end or credit a phantom streak day (see
    // ScheduleCursorRepository.markRead's matching finished-state no-op guard). combinedClickable's
    // onClick is non-nullable (unlike onLongClick), so this gating now lives inside the lambda
    // instead of being expressed by omitting a click modifier entirely — one accepted, minor UX
    // change: a finished row now shows a tap ripple, even though tapping still does nothing.
    // Noted during /autoplan Eng review, not fixed: a finished row previously had no click
    // modifier at all, so TalkBack didn't announce it as interactive; combinedClickable is now
    // always applied, so TalkBack will announce "double tap to activate" even though tapping is
    // a no-op. Accepted for a personal app — not worth a conditional modifier branch to avoid.
    // Long-press is always available (Statistics makes sense regardless of finished state).
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (!status.finished) onMarkRead() },
            onLongClick = { showMenu = true },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
            Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
        }
        // dueCount is only ever nonzero when status is Behind (see ScheduleCursorRepository's
        // deriveScheduleEntryStatus branches) — OnSchedule/Waiting/Finished always carry 0.
        Column(horizontalAlignment = Alignment.End) {
            val chapterText = if (status.finished) "Finished" else "${status.book} ${status.chapterHeb}"
            Text(
                text = if (status.completed) "✓ $chapterText" else chapterText,
                style = MaterialTheme.typography.titleMedium,
            )
            // On its own line, styled distinctly (not interpolated into the line above) — found
            // during /autoplan design review: completed and dueCount>0 aren't mutually
            // exclusive (today's entry can be done while still behind on the overall schedule),
            // so a single shared string like "✓ ... · 3 behind" read as self-contradictory.
            if (status.dueCount > 0) {
                Text(
                    text = "${status.dueCount} behind",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            options = listOf(RowMenuOption("Statistics", onOpenTanakhStats)),
            onDismiss = { showMenu = false },
        )
    }
}
