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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComputedScheduleStatsScreen(viewModel: ComputedScheduleStatsViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.uiState.collectAsState()
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("C++ Weekly") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            HabitStatsSummary("C++ Weekly", state)
            if (state.completedDates.isEmpty()) {
                EmptySectionState()
            } else {
                // onDayClick is a no-op — this kind's watch log has no per-date undo/edit concept
                // (see this step's intro comment), unlike Tanakh's tap-today-to-undo or Reading's
                // tap-to-review-sessions. A future TODOS.md candidate (not proposed here, no user
                // request for it) could show which episode was watched on a given day.
                HeatmapGrid(dates = state.completedDates, today = today, onDayClick = {})
            }
        }
    }
}
