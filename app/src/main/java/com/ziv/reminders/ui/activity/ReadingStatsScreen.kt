package com.ziv.reminders.ui.activity

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatsScreen(viewModel: ActivityViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsState()
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            // Added during /autoplan Design review: this screen's own isLoaded gate is the ONLY
            // loading signal (unlike the shared ActivityScreen, there's no outer multi-section
            // gate) — a silent early return would render a blank body under the top bar for the
            // duration of the Room read, reading as broken rather than loading.
            if (!uiState.isLoaded) {
                Text("Loading…", modifier = Modifier.padding(24.dp))
                return@Column
            }

            HabitStatsSummary("Reading", uiState.reading)
            SectionCaption("Tap a day to review or delete a session")
            if (uiState.reading.completedDates.isEmpty()) {
                EmptySectionState()
            } else {
                HeatmapGrid(dates = uiState.reading.completedDates, today = today, onDayClick = { selectedDate = it })
            }
        }
    }

    selectedDate?.let { date ->
        ReadingDayDetailDialog(viewModel = viewModel, date = date, onDismiss = { selectedDate = null })
    }
}
