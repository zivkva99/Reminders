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
fun TanakhStatsScreen(viewModel: ActivityViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsState()
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tanakh") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            // Added during /autoplan Design review: same reasoning as ReadingStatsScreen — this
            // screen's isLoaded gate is the only loading signal, so a silent early return would
            // render a blank body under the top bar for the duration of the Room read.
            if (!uiState.isLoaded) {
                Text("Loading…", modifier = Modifier.padding(24.dp))
                return@Column
            }

            HabitStatsSummary("Tanakh", uiState.tanakh)
            SectionCaption("Tap today's cell to undo — past days are view-only")
            if (uiState.tanakh.completedDates.isEmpty()) {
                EmptySectionState()
            } else {
                HeatmapGrid(dates = uiState.tanakh.completedDates, today = today, onDayClick = { selectedDate = it })
            }
        }
    }

    selectedDate?.let { date ->
        TanakhDayDetailDialog(viewModel = viewModel, date = date, today = today, onDismiss = { selectedDate = null })
    }
}
