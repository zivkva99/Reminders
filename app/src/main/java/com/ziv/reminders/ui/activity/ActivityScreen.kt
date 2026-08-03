package com.ziv.reminders.ui.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziv.reminders.data.ReadingSessionLog
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun HabitStatsSummary(title: String, state: ActivitySectionState) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text("Streak: ${state.streak} day${if (state.streak == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge)
        Text("Total: ${state.totalCount} day${if (state.totalCount == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun EmptySectionState() {
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
internal fun SectionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}

@Composable
internal fun ReadingDayDetailDialog(viewModel: ActivityViewModel, date: LocalDate, onDismiss: () -> Unit) {
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
internal fun TanakhDayDetailDialog(viewModel: ActivityViewModel, date: LocalDate, today: LocalDate, onDismiss: () -> Unit) {
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
