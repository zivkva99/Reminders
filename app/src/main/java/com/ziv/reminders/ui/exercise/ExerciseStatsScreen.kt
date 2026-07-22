package com.ziv.reminders.ui.exercise

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.ui.activity.HeatmapGrid
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseStatsScreen(viewModel: ExerciseViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            ExerciseActivitySection(viewModel = viewModel)
        }
    }
}

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

    if (!uiState.isLoaded) {
        Text("Loading…", modifier = Modifier.padding(24.dp))
        return
    }

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
    val scope = rememberCoroutineScope()
    LaunchedEffect(date) { values = viewModel.subCounterValuesForDate(date) }

    // Found on-device during Task 8 verification: the previous approach keyed the fetch on
    // (date, uiState.subCounters), assuming an edit would change that map — but uiState.subCounters
    // only ever reflects TODAY's values (see ExerciseViewModel.refresh()), so editing any OTHER
    // date left that key unchanged, silently freezing this dialog on its pre-edit snapshot even
    // though the write succeeded (confirmed via close/reopen — the DB was correct, only this
    // dialog's display was stale). Fix: sequence the write and the re-fetch explicitly in the
    // same coroutine, in the dialog's own scope, instead of relying on an indirect uiState signal.
    fun adjust(exerciseKey: String, delta: Int) {
        scope.launch {
            viewModel.adjustSubCounterForDate(exerciseKey, date, delta)
            values = viewModel.subCounterValuesForDate(date)
        }
    }

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
                            TextButton(onClick = { adjust(key, -1) }) { Text("−") }
                            TextButton(onClick = { adjust(key, +1) }) { Text("+") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
