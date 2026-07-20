package com.ziv.reminders.ui.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziv.reminders.data.ReadingSessionLog
import com.ziv.reminders.ui.exercise.ExerciseActivitySection
import com.ziv.reminders.ui.exercise.ExerciseViewModel
import com.ziv.reminders.ui.exercise.GoalGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ActivityScreen(activityViewModel: ActivityViewModel, exerciseViewModel: ExerciseViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { activityViewModel.refresh() }
    val uiState by activityViewModel.uiState.collectAsState()
    // Corrected during /autoplan design review: ExerciseActivitySection independently triggers
    // and gates on exerciseViewModel's own isLoaded (via its own LaunchedEffect(Unit)), so
    // whichever of the two ViewModels' Room reads finishes first pops its section in while the
    // other stays blank — visible layout jank on every screen open. Reading exerciseUiState
    // here too and folding it into this screen's single top-level gate means the whole screen
    // appears atomically once both are ready, instead of section-by-section.
    val exerciseUiState by exerciseViewModel.uiState.collectAsState()
    val today = remember { LocalDate.now() }
    var selectedReadingDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedTanakhDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Activity", style = MaterialTheme.typography.titleMedium)
        }

        if (!uiState.isLoaded || !exerciseUiState.isLoaded) return@Column

        // Combo-streak surfaced here AND in the weekly-summary notification (Task 7) — this
        // resolves the design doc's "Reviewer Concern" about ambiguous placement by putting it
        // in both places rather than picking one.
        //
        // Corrected during /autoplan design review: always shown (not hidden at 0), with a
        // "0/7" fallback — the original conditional made this the headline feature of the whole
        // plan invisible on most weeks (any week without a same-day triple-hit), which is the
        // majority case for a realistic mixed-consistency user, making the feature undiscoverable.
        // Corrected during /autoplan design review: "/7" implied 7/7 was the achievable ceiling,
        // but Reading/Tanakh run on a 5-day (Sun-Thu) mask while Exercise runs all 7 — for any
        // user with that real seeded config, all-three-in-one-day can only happen on at most 5
        // of the 7 days, so the banner's own headline metric would structurally always look like
        // a shortfall. Dropped the "/N" denominator entirely rather than compute an
        // achievable-ceiling number (which would need each habit's enabledDaysMask threaded into
        // WeeklySummary — more invasive, and still wrong the moment a mask ever changes).
        Text(
            text = "Hit all 3 habits on ${uiState.comboStreakThisWeek} day${if (uiState.comboStreakThisWeek == 1) "" else "s"} this week!",
            style = MaterialTheme.typography.bodyLarge,
            color = GoalGreen,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )

        // Section order matches the dashboard's own row order (Exercise/Reading/Tanakh) —
        // this was already true but unstated; noted here per /autoplan design review so a
        // future editor doesn't read it as arbitrary implementation order.
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text("Exercise", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SectionCaption("Tap a day to review this day's reps")
            ExerciseActivitySection(viewModel = exerciseViewModel)

            HabitStatsSummary("Reading", uiState.reading)
            SectionCaption("Tap a day to review or delete a session")
            if (uiState.reading.completedDates.isEmpty()) {
                EmptySectionState()
            } else {
                HeatmapGrid(dates = uiState.reading.completedDates, today = today, onDayClick = { selectedReadingDate = it })
            }

            HabitStatsSummary("Tanakh", uiState.tanakh)
            SectionCaption("Tap today's cell to undo — past days are view-only")
            if (uiState.tanakh.completedDates.isEmpty()) {
                EmptySectionState()
            } else {
                HeatmapGrid(dates = uiState.tanakh.completedDates, today = today, onDayClick = { selectedTanakhDate = it })
            }
        }
    }

    selectedReadingDate?.let { date ->
        ReadingDayDetailDialog(viewModel = activityViewModel, date = date, onDismiss = { selectedReadingDate = null })
    }
    selectedTanakhDate?.let { date ->
        TanakhDayDetailDialog(viewModel = activityViewModel, date = date, today = today, onDismiss = { selectedTanakhDate = null })
    }
}

@Composable
private fun HabitStatsSummary(title: String, state: ActivitySectionState) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text("Streak: ${state.streak} day${if (state.streak == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge)
        Text("Total: ${state.totalCount} day${if (state.totalCount == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptySectionState() {
    Text(
        text = "No history yet",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}

// Added per /autoplan design review (High finding: three genuinely different day-tap
// behaviors — view-only, session-delete, conditional-undo — on visually identical heatmap
// grids, with no cue distinguishing them before the user taps). One line per section states
// what a tap does before the user commits to it.
@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}

@Composable
private fun ReadingDayDetailDialog(viewModel: ActivityViewModel, date: LocalDate, onDismiss: () -> Unit) {
    var sessions by remember(date) { mutableStateOf<List<ReadingSessionLog>?>(null) }
    // Tracks which session (if any) the user has tapped Delete on, pending confirmation —
    // added per /autoplan design review (Critical finding: this delete previously fired
    // immediately with no confirm step, while Tanakh's equally-destructive undo action
    // already required a confirm tap — two visually identical grids with different risk
    // profiles for a mis-tap). Now both require a confirm.
    var pendingDelete by remember(date) { mutableStateOf<ReadingSessionLog?>(null) }
    LaunchedEffect(date) { sessions = viewModel.readingSessionsForDate(date) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DateTimeFormatter.ISO_LOCAL_DATE)) },
        text = {
            val current = sessions
            when {
                current == null -> Text("Loading…")
                current.isEmpty() -> Text("No sessions logged this day")
                else -> Column {
                    current.forEach { session ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${session.durationSeconds / 60} min", modifier = Modifier.weight(1f))
                            TextButton(onClick = { pendingDelete = session }) { Text("Delete") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this session?") },
            text = { Text("${session.durationSeconds / 60} min logged on ${date.format(DateTimeFormatter.ISO_LOCAL_DATE)} — this can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReadingSession(session)
                    sessions = sessions?.minus(session)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TanakhDayDetailDialog(viewModel: ActivityViewModel, date: LocalDate, today: LocalDate, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DateTimeFormatter.ISO_LOCAL_DATE)) },
        text = {
            // Tanakh has a single global cursor, not independent per-day state — undo only
            // makes sense for today's most recent mark, never an arbitrary past day (see
            // ScheduleCursorRepository.undoMarkRead's doc comment).
            if (date == today) {
                Text("Undo today's Tanakh reading?")
            } else {
                Text("Past days can't be edited — Tanakh tracks one running position, not independent daily entries.")
            }
        },
        confirmButton = {
            if (date == today) {
                TextButton(onClick = {
                    viewModel.undoTanakhMarkRead(date)
                    onDismiss()
                }) { Text("Undo") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = if (date == today) {
            { TextButton(onClick = onDismiss) { Text("Cancel") } }
        } else {
            null
        },
    )
}
