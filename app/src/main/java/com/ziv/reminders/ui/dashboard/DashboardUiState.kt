package com.ziv.reminders.ui.dashboard

data class HabitRowUiState(
    val instanceId: Long,
    val name: String,
    val statusText: String,
    val completed: Boolean,
    val streak: Int,
)

data class DashboardUiState(
    val habits: List<HabitRowUiState> = emptyList(),
    val isLoaded: Boolean = false,
)
