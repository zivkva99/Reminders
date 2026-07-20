package com.ziv.reminders.ui.exercise

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.ui.activity.HeatmapGrid
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Embeddable Exercise section for the unified Activity screen — this used to be the standalone
 * "exerciseStats" NavHost destination, just reached via Activity instead of its own route. The
 * back button/title row that used to live here now belongs to ActivityScreen's shared top bar.
 *
 * Corrected during /autoplan review: SubCounterDetailDialog now supports +/- editing a past
 * day's value (not view-only), and this section now shows a "Total" line alongside its existing
 * streak/month/record callouts — matching Reading/Tanakh's stat vocabulary instead of showing a
 * differently-labeled number in the same visual slot.
 */
@Composable
fun ExerciseActivitySection(viewModel: ExerciseViewModel) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsState()
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    if (!uiState.isLoaded) return

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            text = "Streak: ${uiState.streak} day${if (uiState.streak == 1) "" else "s"}" +
                HabitStats.recordSuffix(uiState.isNewStreakRecord, "record"),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "This month: ${uiState.monthCount} day${if (uiState.monthCount == 1) "" else "s"}" +
                HabitStats.recordSuffix(uiState.isNewMonthRecord, "best month"),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Total: ${uiState.totalCount} day${if (uiState.totalCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyLarge,
        )
    }

    Spacer(Modifier.height(16.dp))

    if (uiState.completedDates.isEmpty()) {
        EmptyState()
    } else {
        HeatmapGrid(dates = uiState.completedDates, today = today, onDayClick = { day -> selectedDate = day })
    }

    selectedDate?.let { date ->
        SubCounterDetailDialog(viewModel = viewModel, date = date, onDismiss = { selectedDate = null })
    }
}

@Composable
private fun EmptyState() {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Hit your goal to start your streak",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubCounterDetailDialog(viewModel: ExerciseViewModel, date: LocalDate, onDismiss: () -> Unit) {
    var values by remember(date) { mutableStateOf<Map<String, Int?>?>(null) }
    // Keyed on (date, uiState.subCounters) not just (date) — the new +/- edit buttons below call
    // adjustSubCounterForDate, which triggers refresh() and updates uiState, but without this
    // extra key the dialog's own `values` (fetched once on open) would go stale after an edit
    // within the same dialog session instead of reflecting the just-applied change.
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(date, uiState.subCounters) { values = viewModel.subCounterValuesForDate(date) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DateTimeFormatter.ISO_LOCAL_DATE)) },
        text = {
            val current = values
            when {
                current == null -> Text("Loading…")
                current.values.all { it == null } -> Text("No data for this day")
                else -> Column {
                    exerciseLabels.forEach { (key, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("$label: ${current[key] ?: "—"}", modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.adjustSubCounterForDate(key, date, -1) }) { Text("−") }
                            TextButton(onClick = { viewModel.adjustSubCounterForDate(key, date, +1) }) { Text("+") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
