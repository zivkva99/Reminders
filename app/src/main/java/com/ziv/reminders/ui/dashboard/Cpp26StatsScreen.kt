package com.ziv.reminders.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziv.reminders.ui.activity.EmptySectionState
import com.ziv.reminders.ui.activity.HabitStatsSummary
import com.ziv.reminders.ui.activity.HeatmapGrid
import java.time.LocalDate

/** Mirrors ComputedScheduleStatsScreen exactly — view-only heatmap, no day-edit/undo dialog
 * (unlike TanakhStatsScreen's tap-today-to-undo, which is wired to ActivityViewModel specifically
 * and not worth duplicating here for a second SCHEDULE_CURSOR instance; the dashboard row's own
 * Snackbar undo already covers the "just tapped, changed my mind" case). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Cpp26StatsScreen(viewModel: Cpp26StatsViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.uiState.collectAsState()
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("C++26") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            HabitStatsSummary("C++26", state)
            if (state.completedDates.isEmpty()) {
                EmptySectionState()
            } else {
                HeatmapGrid(dates = state.completedDates, today = today, onDayClick = {})
            }
        }
    }
}
