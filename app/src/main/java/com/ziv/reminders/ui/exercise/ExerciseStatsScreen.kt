package com.ziv.reminders.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ziv.reminders.data.HabitStats
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ExerciseStatsScreen(viewModel: ExerciseViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsState()
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Your Progress", style = MaterialTheme.typography.titleMedium)
        }

        // isLoaded distinguishes "hasn't loaded yet" from "genuinely no history" — without
        // it this screen could flash an empty-history message on cold navigation.
        if (!uiState.isLoaded) return@Column

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
        }

        Spacer(Modifier.height(16.dp))

        if (uiState.completedDates.isEmpty()) {
            EmptyState()
        } else {
            HeatmapGrid(dates = uiState.completedDates, today = today, onDayClick = { day -> selectedDate = day })
        }
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
private fun HeatmapGrid(dates: Set<LocalDate>, today: LocalDate, onDayClick: (LocalDate) -> Unit) {
    // Aligned to a fixed 7-column, Sunday-start grid so rows correspond to real calendar
    // weeks regardless of screen width — mirrors Shape's own HeatmapGrid exactly.
    val windowStart = remember(today) { today.minusMonths(12) }
    val alignedStart = remember(windowStart) {
        val daysSinceSunday = windowStart.dayOfWeek.value % 7
        windowStart.minusDays(daysSinceSunday.toLong())
    }
    // Weeks reversed (most recent week first) so today is always near the top with no
    // scrolling — mirrors Shape's own HeatmapGrid exactly.
    val days = remember(alignedStart, today) {
        generateSequence(alignedStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .toList()
            .chunked(7)
            .reversed()
            .flatten()
    }

    LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        items(days) { day ->
            val color = when {
                day == today && day !in dates -> HeatmapPending
                day in dates -> HeatmapHit
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val description = "${day.format(DateTimeFormatter.ISO_LOCAL_DATE)}: " +
                if (day == today && day !in dates) "not yet done"
                else if (day in dates) "goal hit" else "missed"

            Column(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(1.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
                    .clickable { onDayClick(day) }
                    .semantics { contentDescription = description }
            ) {}
        }
    }
}

@Composable
private fun SubCounterDetailDialog(viewModel: ExerciseViewModel, date: LocalDate, onDismiss: () -> Unit) {
    var values by remember(date) { mutableStateOf<Map<String, Int?>?>(null) }
    LaunchedEffect(date) { values = viewModel.subCounterValuesForDate(date) }

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
                        Text("$label: ${current[key] ?: "—"}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
