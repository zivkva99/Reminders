package com.ziv.reminders.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.isEnabledDay
import com.ziv.reminders.service.TimerService
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
                HabitRowUiState(instance.id, instance.name, status, streak)
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

    fun onMarkRead(instanceId: Long) {
        viewModelScope.launch {
            val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return@launch
            dataSource.scheduleCursorRepository.markRead(instance, LocalDate.now())
            refresh()
        }
    }

    /** Starts/stops TimerService (the single source of truth for the DB write) then
     * optimistically flips the row locally — see this task's Interfaces note for why an
     * immediate refresh() would race the service's own async write instead.
     *
     * [displayedRemainingSeconds] is the value the Composable is currently showing (ticked down
     * locally, 1Hz, while running) — the ViewModel's own `status.remainingSeconds` is a stale
     * baseline that only changes on Start/Stop/refresh(), never every second. Carrying the
     * displayed value into the optimistic update (instead of just flipping isRunning) is what
     * stops the countdown visually jumping back to that stale baseline the instant Stop is
     * tapped. */
    fun onToggleTimer(instanceId: Long, context: Context, displayedRemainingSeconds: Int) {
        val row = _uiState.value.habits.firstOrNull { it.instanceId == instanceId } ?: return
        val status = row.status as? HabitStatus.TimerStatus ?: return
        val action = if (status.isRunning) TimerService.ACTION_STOP else TimerService.ACTION_START
        context.startService(
            Intent(context, TimerService::class.java)
                .setAction(action)
                .putExtra(TimerService.EXTRA_HABIT_INSTANCE_ID, instanceId)
        )
        val updatedStatus = status.copy(isRunning = !status.isRunning, remainingSeconds = displayedRemainingSeconds)
        _uiState.value = _uiState.value.copy(
            habits = _uiState.value.habits.map { if (it.instanceId == instanceId) it.copy(status = updatedStatus) else it }
        )
    }

    companion object {
        fun factory(dataSource: DashboardDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = DashboardViewModel(dataSource) as T
            }
    }
}
