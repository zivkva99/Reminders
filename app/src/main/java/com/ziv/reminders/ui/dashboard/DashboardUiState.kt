package com.ziv.reminders.ui.dashboard

import com.ziv.reminders.data.HabitStatus

data class HabitRowUiState(
    val instanceId: Long,
    val name: String,
    val status: HabitStatus,
    val streak: Int,
)

data class DashboardUiState(
    val habits: List<HabitRowUiState> = emptyList(),
    val isLoaded: Boolean = false,
)
