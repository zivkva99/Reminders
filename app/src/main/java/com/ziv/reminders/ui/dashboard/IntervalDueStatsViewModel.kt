package com.ziv.reminders.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.GARDEN_HABIT_INSTANCE_ID
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
 * reuse ActivitySectionState (which always carries a streak). */
class IntervalDueStatsViewModel(private val dataSource: DashboardDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(IntervalDueStatsUiState())
    val uiState: StateFlow<IntervalDueStatsUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val instance = dataSource.habitInstanceDao.getById(GARDEN_HABIT_INSTANCE_ID) ?: return@launch
            val history = dataSource.intervalDueRepository.history(instance)
            _uiState.value = IntervalDueStatsUiState(totalCount = history.size, history = history)
        }
    }

    companion object {
        fun factory(dataSource: DashboardDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = IntervalDueStatsViewModel(dataSource) as T
            }
    }
}
