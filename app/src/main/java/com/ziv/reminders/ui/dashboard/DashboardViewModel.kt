package com.ziv.reminders.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.isEnabledDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class DashboardViewModel(private val dataSource: DashboardDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val instances = dataSource.habitInstanceDao.getAll()
                .filter { isEnabledDay(today, it.enabledDaysMask) }
            val rows = instances.map { instance ->
                val status = dataSource.habitEngine.todayStatus(instance, today)
                val streak = dataSource.habitEngine.currentStreak(instance, today)
                val (statusText, completed) = when (status) {
                    is HabitStatus.CounterStatus -> "${status.current}/${status.goal}" to status.completed
                    is HabitStatus.TimerStatus -> {
                        val minutes = status.remainingSeconds / 60
                        val seconds = status.remainingSeconds % 60
                        "%d:%02d".format(minutes, seconds) to status.completed
                    }
                }
                HabitRowUiState(instance.id, instance.name, statusText, completed, streak)
            }
            _uiState.value = DashboardUiState(habits = rows, isLoaded = true)
        }
    }

    fun onIncrement(instanceId: Long) {
        viewModelScope.launch {
            val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return@launch
            dataSource.counterHabitRepository.increment(instance, LocalDate.now())
            refresh()
        }
    }

    companion object {
        fun factory(dataSource: DashboardDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = DashboardViewModel(dataSource) as T
            }
    }
}
