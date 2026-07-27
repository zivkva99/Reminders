package com.ziv.reminders.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ziv.reminders.R
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.LEGO_KIT_HABIT_INSTANCE_ID
import com.ziv.reminders.data.isEnabledDay
import com.ziv.reminders.ui.exercise.GoalGreen
import com.ziv.reminders.ui.exercise.StatusOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenExercise: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenExerciseStats: () -> Unit = {},
    onOpenReadingStats: () -> Unit = {},
    onOpenTanakhStats: () -> Unit = {},
    onOpenCppWeeklyStats: () -> Unit = {},
    onOpenLegoKitStats: () -> Unit = {},
    onOpenGardenStats: () -> Unit = {},
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
                    onMarkNextWatched = {
                        coroutineScope.launch {
                            // No "Undo" action label (unlike onIncrement/onMarkRead above) — this
                            // kind has no per-day daily-progress table to reverse against; this is
                            // purely an acknowledgment Snackbar (Final Approval Gate decision — this
                            // row was otherwise the only mutating row in the app with zero tap
                            // feedback), shown only when a tap actually did something.
                            val watchedEpisode = viewModel.onMarkNextWatched(habit.instanceId)
                            if (watchedEpisode != null) {
                                snackbarHostState.showSnackbar(
                                    message = "Marked episode $watchedEpisode watched",
                                    duration = SnackbarDuration.Short,
                                )
                            }
                        }
                    },
                    onIncrementLegoKit = {
                        coroutineScope.launch { viewModel.onIncrement(habit.instanceId) }
                    },
                    onUndoLegoKit = {
                        coroutineScope.launch { viewModel.onUndoIncrement(habit.instanceId) }
                    },
                    onMarkDone = { intervalDays ->
                        coroutineScope.launch {
                            viewModel.onMarkDone(habit.instanceId, intervalDays)
                            snackbarHostState.showSnackbar(message = "Watered!", duration = SnackbarDuration.Short)
                        }
                    },
                    onRescheduleOnly = { intervalDays ->
                        coroutineScope.launch {
                            viewModel.onRescheduleOnly(habit.instanceId, intervalDays)
                            snackbarHostState.showSnackbar(message = "Rescheduled", duration = SnackbarDuration.Short)
                        }
                    },
                    onOpenExercise = onOpenExercise,
                    onOpenExerciseStats = onOpenExerciseStats,
                    onOpenReadingStats = onOpenReadingStats,
                    onOpenTanakhStats = onOpenTanakhStats,
                    onOpenCppWeeklyStats = onOpenCppWeeklyStats,
                    onOpenLegoKitStats = onOpenLegoKitStats,
                    onOpenGardenStats = onOpenGardenStats,
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
    onMarkNextWatched: () -> Unit,
    onIncrementLegoKit: () -> Unit,
    onUndoLegoKit: () -> Unit,
    onOpenExercise: () -> Unit,
    onOpenExerciseStats: () -> Unit,
    onOpenReadingStats: () -> Unit,
    onOpenTanakhStats: () -> Unit,
    onOpenCppWeeklyStats: () -> Unit,
    onOpenLegoKitStats: () -> Unit,
    onMarkDone: (Int) -> Unit,
    onRescheduleOnly: (Int) -> Unit,
    onOpenGardenStats: () -> Unit,
) {
    when (habit.status) {
        is HabitStatus.CounterStatus -> if (isLegoKitRow(habit.instanceId)) {
            LegoKitHabitRow(habit, habit.status, onIncrementLegoKit, onUndoLegoKit, onOpenLegoKitStats)
        } else {
            CounterHabitRow(habit, habit.status, onIncrement, onOpenExercise, onOpenExerciseStats)
        }
        is HabitStatus.TimerStatus -> TimerHabitRow(habit, habit.status, onToggleTimer, onResetReadingToday, fetchReadingSessionCountToday, onOpenReadingStats)
        is HabitStatus.ScheduleCursorStatus -> ScheduleCursorHabitRow(habit, habit.status, onMarkRead, onOpenTanakhStats)
        is HabitStatus.ComputedScheduleStatus -> ComputedScheduleHabitRow(habit, habit.status, onMarkNextWatched, onOpenCppWeeklyStats)
        is HabitStatus.IntervalDueStatus -> IntervalDueHabitRow(habit, habit.status, onMarkDone, onRescheduleOnly, onOpenGardenStats)
    }
}

// Dispatch is by instance ID, not by HabitKind — a hypothetical future second
// COUNTER-kind habit must not be silently offered the Exercise long-press menu just because
// it shares HabitKind.COUNTER (see DashboardDispatchTest). Renamed from
// shouldNavigateToExerciseDetail: it no longer gates tap-navigation (short-tap is now a pure
// increment for every Counter-kind habit) — it gates long-press-menu eligibility instead.
fun hasExerciseDetailMenu(instanceId: Long): Boolean = instanceId == EXERCISE_HABIT_INSTANCE_ID

// Same "dispatch by ID, not by shared HabitKind" rule as hasExerciseDetailMenu above — Lego Kit
// is also a COUNTER-kind habit (shares HabitStatus.CounterStatus), so it must not fall through
// to CounterHabitRow's always-tappable, never-dimmed rendering just because the status type
// matches (see DashboardDispatchTest).
fun isLegoKitRow(instanceId: Long): Boolean = instanceId == LEGO_KIT_HABIT_INSTANCE_ID

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

@Composable
private fun HabitStatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.background, CircleShape)
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painter = painterResource(R.drawable.ic_habit_exercise), contentDescription = null, modifier = Modifier.size(40.dp))
            HabitStatusDot(color = if (status.completed) GoalGreen else MaterialTheme.colorScheme.error)
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
            }
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
private fun LegoKitHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.CounterStatus,
    onIncrement: () -> Unit,
    onUndo: () -> Unit,
    onOpenStats: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val isEnabledToday = isEnabledDay(today, habit.enabledDaysMask)
    val isDimmed = status.completed || !isEnabledToday

    // Tap is a no-op once already completed today or on a day this habit isn't enabled — the
    // "disables until the next day" behavior the design doc committed to. No transient Snackbar
    // here (unlike CounterHabitRow's onIncrement) — Undo lives in the long-press menu instead,
    // so there's exactly one Undo affordance, not two competing ones.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDimmed) 0.5f else 1f)
            .combinedClickable(
                onClick = { if (!status.completed && isEnabledToday) onIncrement() },
                onLongClick = { showMenu = true },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painter = painterResource(R.drawable.ic_habit_legokit), contentDescription = null, modifier = Modifier.size(40.dp))
            // Design review finding: red is reserved everywhere else in this codebase for
            // "behind schedule, needs attention" (ScheduleCursorHabitRow/ComputedScheduleHabitRow
            // both only use error-red for a real dueCount). Lego Kit is the first row that dims
            // for a SCHEDULE reason (off-day), not just a completion reason — reusing the plain
            // completed/error binary here would show a red dot on a dimmed, non-actionable
            // Friday row, reading as "overdue" rather than "not today."
            HabitStatusDot(
                color = when {
                    status.completed -> GoalGreen
                    !isEnabledToday -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.error
                },
            )
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            text = when {
                status.completed -> "✓ Added"
                !isEnabledToday -> "Not today"
                else -> "Add one kit"
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            options = buildList {
                add(RowMenuOption("Statistics", onOpenStats))
                if (status.completed) add(RowMenuOption("Undo", onUndo))
            },
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painter = painterResource(R.drawable.ic_habit_reading), contentDescription = null, modifier = Modifier.size(40.dp))
            HabitStatusDot(color = if (status.completed) GoalGreen else MaterialTheme.colorScheme.error)
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
            }
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painter = painterResource(R.drawable.ic_habit_tanakh), contentDescription = null, modifier = Modifier.size(40.dp))
            HabitStatusDot(
                color = when {
                    // dueCount is only ever nonzero when status is Behind (see
                    // ScheduleCursorRepository's deriveScheduleEntryStatus branches) —
                    // OnSchedule/Waiting/Finished always carry 0. Behind wins regardless of
                    // whether something was separately marked read today — see this plan's
                    // design doc for why the generic `completed` flag can't be used here.
                    status.dueCount > 0 -> MaterialTheme.colorScheme.error
                    status.isDueToday -> StatusOrange
                    else -> GoalGreen
                },
            )
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComputedScheduleHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.ComputedScheduleStatus,
    onMarkNextWatched: () -> Unit,
    onOpenCppWeeklyStats: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    // combinedClickable, not plain clickable (updated per the Scope Revision — the plan's
    // original draft of this composable used plain clickable, since it had no long-press action).
    // Tap is a no-op while dueCount == 0 (nothing released yet) — mirrors
    // ScheduleCursorRepository.markRead's own defensive guard, applied here at the UI layer too,
    // not just inside ComputedScheduleRepository.markNextWatched. Long-press is always available
    // (Statistics makes sense regardless of due state — same reasoning as ScheduleCursorHabitRow's
    // own "Long-press is always available" comment).
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (status.dueCount > 0) onMarkNextWatched() },
            onLongClick = { showMenu = true },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painter = painterResource(R.drawable.ic_habit_cppweekly), contentDescription = null, modifier = Modifier.size(40.dp))
            HabitStatusDot(
                color = when {
                    // dueCount's shape here is deliberately NOT the same as ScheduleCursorStatus's
                    // dueCount (see ComputedSchedule.kt's deriveComputedScheduleStatus doc
                    // comment): dueCount == 1 means "due today", so red is reserved for
                    // dueCount > 1 (behind by more than one release), not dueCount > 0.
                    status.dueCount > 1 -> MaterialTheme.colorScheme.error
                    status.isDueToday -> StatusOrange
                    else -> GoalGreen
                },
            )
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                // Still replaces the usual "Streak: Nd" line with the episode number, unchanged by
                // the Scope Revision — this row's own display stays as originally designed even
                // though currentStreak() now returns a real value (it feeds the new stats screen,
                // Task 6, instead of this line — see ComputedScheduleRepository's doc comment).
                Text("Episode ${status.nextItemNumber}", style = MaterialTheme.typography.bodySmall)
            }
        }
        val statusText = when {
            status.dueCount > 1 -> "${status.dueCount} behind"
            status.isDueToday -> "New!"
            else -> "Waiting"
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            color = if (status.dueCount > 1) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            options = listOf(RowMenuOption("Statistics", onOpenCppWeeklyStats)),
            onDismiss = { showMenu = false },
        )
    }
}

@Composable
private fun IntervalDuePickerDialog(
    title: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Preset chips for the common cases, plus free numeric entry for anything else — chips set
    // the text field's value directly, so Confirm's enabled/disabled logic and the >= 1 guard
    // apply uniformly regardless of how the value was entered. Confirm is disabled below 1,
    // mirroring IntervalDueRepository's own require(intervalDays >= 1) guard — belt-and-suspenders,
    // not a substitute for it (see Task 2).
    var text by remember { mutableStateOf("") }
    val parsed = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                // horizontalScroll — on-device testing found 5 chips don't fit the dialog's
                // width, which compressed the last chip and wrapped "14" onto two lines instead
                // of shrinking the Row itself.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    listOf(3, 5, 7, 10, 14).forEach { preset ->
                        AssistChip(onClick = { text = preset.toString() }, label = { Text("$preset") })
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Days") },
                    // Explains why Confirm is disabled (design review Pass 2 finding — a
                    // greyed-out button with no reason is a dead end, not a state).
                    supportingText = { if (parsed == null || parsed < 1) Text("Enter at least 1 day") },
                    isError = text.isNotEmpty() && (parsed == null || parsed < 1),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null && parsed >= 1,
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IntervalDueHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.IntervalDueStatus,
    onMarkDone: (Int) -> Unit,
    onRescheduleOnly: (Int) -> Unit,
    onOpenGardenStats: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf<PickerMode?>(null) }
    val today = LocalDate.now()

    // Design review finding (accepted): the first draft used a binary due/not-due dot, "matching
    // Counter's convention" — the wrong analog. Counter's completed/not-completed is a same-day
    // fact with no concept of accumulating lateness; this kind's due-ness accumulates exactly
    // like Schedule-cursor/Computed-schedule's does (a plant overdue by 1 day and one overdue by
    // 3 weeks must not render identically), so this row now follows THEIR 3-state convention
    // instead: not-due (green) / due today (orange) / overdue (red, plus magnitude text) — not
    // Counter's binary one. The math itself is delegated to a pure, unit-tested function (Eng
    // review finding: this logic previously lived inline in the Composable with zero test
    // coverage — an argument-order swap or off-by-one would have silently flipped "3 days
    // overdue" into "in 3 days" with nothing to catch it).
    val display = deriveIntervalDueRowDisplay(status.dueDate, today)
    val dotColor = when (display.urgency) {
        IntervalDueUrgency.OVERDUE -> MaterialTheme.colorScheme.error
        IntervalDueUrgency.DUE_TODAY -> StatusOrange
        IntervalDueUrgency.NOT_DUE -> GoalGreen
    }
    val statusText = display.statusText

    // Short-press only when due (mirrors the "already completed today, tap disabled" precedent
    // from other kinds, applied here to "not currently due"). Long-press's two reschedule options
    // are deliberately NOT gated on due state, unlike short-press — a long-press is already an
    // explicit "I want to do X" choice (the user opened a menu and picked an option), so it
    // doesn't need the same "only when it makes sense" guard a single ambient tap does; someone
    // may legitimately want to water early or push the date out before it's even due (design
    // review Pass 4 finding — this asymmetry was previously present but unstated).
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (status.isDue) showPicker = PickerMode.MARK_DONE },
            onLongClick = { showMenu = true },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painter = painterResource(R.drawable.ic_habit_garden), contentDescription = null, modifier = Modifier.size(40.dp))
            HabitStatusDot(color = dotColor)
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                // Design review finding (accepted): the first draft had no subtitle at all — every
                // other row's left block is icon+dot+Column(name, subtitle), and dropping the
                // second line was an unconsidered gap, not a deliberate departure like the
                // Statistics screen's no-streak choice is. habit.streak carries total-times-
                // watered for this kind (see HabitEngine.currentStreak's INTERVAL_DUE branch,
                // Task 2) — NOT a real streak, just the same generic per-row summary-count slot
                // repurposed, at zero extra repository calls in the dashboard-refresh hot path.
                Text("Watered ${habit.streak} time${if (habit.streak == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(text = statusText, style = MaterialTheme.typography.titleMedium)
    }

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            // Ordering rationale (design review Pass 4 finding — previously undocumented):
            // "Mark done + reschedule" stays first, matching Exercise's own [Counter, Statistics]
            // ordering (primary action first) rather than Timer's [Statistics, Reset today]
            // ordering (safe-action-first, destructive-last) — Timer's ordering exists specifically
            // to protect against a habit-driven reflex tap landing on a DESTRUCTIVE action (data
            // loss). None of this row's 3 options destroy data (mark-done/reschedule-only both
            // just write a new due date; at most the log gets one extra row), so there's no
            // destructive option to protect against here, and no option is marked isDestructive.
            options = listOf(
                RowMenuOption("Mark done + reschedule", onSelect = { showPicker = PickerMode.MARK_DONE }),
                RowMenuOption("Reschedule only", onSelect = { showPicker = PickerMode.RESCHEDULE_ONLY }),
                RowMenuOption("Statistics", onOpenGardenStats),
            ),
            onDismiss = { showMenu = false },
        )
    }

    showPicker?.let { mode ->
        IntervalDuePickerDialog(
            title = if (mode == PickerMode.MARK_DONE) "Water again in how many days?" else "Reschedule to how many days from now?",
            onConfirm = { days ->
                showPicker = null
                if (mode == PickerMode.MARK_DONE) onMarkDone(days) else onRescheduleOnly(days)
            },
            onDismiss = { showPicker = null },
        )
    }
}

private enum class PickerMode { MARK_DONE, RESCHEDULE_ONLY }
