package com.ziv.reminders.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.DashboardDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class IntervalDueStatsUiState(
    val totalCount: Int = 0,
    val history: List<LocalDate> = emptyList(),
)

/** Deliberately its own small ViewModel over DashboardDataSource, same precedent as
 * ComputedScheduleStatsViewModel — no streak field (design doc Premise 3), so this does not
 * reuse ActivitySectionState (which always carries a streak).
 *
 * Takes habitInstanceId as a constructor param (not hardcoded to Garden) — with a second
 * INTERVAL_DUE instance (מחמצת) added, one reusable ViewModel/Screen pair now serves either,
 * one instance per NavHost route/factory call in MainActivity (see IntervalDueHabitRow's
 * isSourdoughRow for the matching dashboard-row-side split). */
class IntervalDueStatsViewModel(
    private val dataSource: DashboardDataSource,
    private val habitInstanceId: Long,
) : ViewModel() {
    private val _uiState = MutableStateFlow(IntervalDueStatsUiState())
    val uiState: StateFlow<IntervalDueStatsUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val instance = dataSource.habitInstanceDao.getById(habitInstanceId) ?: return@launch
            val history = dataSource.intervalDueRepository.history(instance)
            _uiState.value = IntervalDueStatsUiState(totalCount = history.size, history = history)
        }
    }

    companion object {
        fun factory(dataSource: DashboardDataSource, habitInstanceId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = IntervalDueStatsViewModel(dataSource, habitInstanceId) as T
            }
    }
}
