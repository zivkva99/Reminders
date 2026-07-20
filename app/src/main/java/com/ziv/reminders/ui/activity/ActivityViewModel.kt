package com.ziv.reminders.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.ActivityDataSource
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.data.READING_HABIT_INSTANCE_ID
import com.ziv.reminders.data.ReadingSessionLog
import com.ziv.reminders.data.TANAKH_HABIT_INSTANCE_ID
import com.ziv.reminders.data.WeeklySummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ActivitySectionState(
    val streak: Int = 0,
    val totalCount: Int = 0,
    val completedDates: Set<LocalDate> = emptySet(),
)

data class ActivityUiState(
    val exercise: ActivitySectionState = ActivitySectionState(),
    val reading: ActivitySectionState = ActivitySectionState(),
    val tanakh: ActivitySectionState = ActivitySectionState(),
    val comboStreakThisWeek: Int = 0,
    val isLoaded: Boolean = false,
)

class ActivityViewModel(private val dataSource: ActivityDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()

            val exerciseInstance = dataSource.habitInstanceDao.getById(EXERCISE_HABIT_INSTANCE_ID)
            val readingInstance = dataSource.habitInstanceDao.getById(READING_HABIT_INSTANCE_ID)
            val tanakhInstance = dataSource.habitInstanceDao.getById(TANAKH_HABIT_INSTANCE_ID)

            val exerciseDates = exerciseInstance?.let { HabitStats.parseDates(dataSource.counterHabitRepository.completedDates(it)) } ?: emptySet()
            val readingDates = readingInstance?.let { HabitStats.parseDates(dataSource.timerHabitRepository.completedDates(it)) } ?: emptySet()
            val tanakhDates = tanakhInstance?.let { HabitStats.parseDates(dataSource.scheduleCursorRepository.completedDates(it)) } ?: emptySet()

            val exerciseStreak = exerciseInstance?.let { dataSource.habitEngine.currentStreak(it, today) } ?: 0
            val readingStreak = readingInstance?.let { dataSource.habitEngine.currentStreak(it, today) } ?: 0
            val tanakhStreak = tanakhInstance?.let { dataSource.habitEngine.currentStreak(it, today) } ?: 0

            val weekly = WeeklySummary.compute(exerciseDates, readingDates, tanakhDates, today)

            _uiState.value = ActivityUiState(
                exercise = ActivitySectionState(exerciseStreak, HabitStats.totalCount(exerciseDates), exerciseDates),
                reading = ActivitySectionState(readingStreak, HabitStats.totalCount(readingDates), readingDates),
                tanakh = ActivitySectionState(tanakhStreak, HabitStats.totalCount(tanakhDates), tanakhDates),
                comboStreakThisWeek = weekly.comboStreak,
                isLoaded = true,
            )
        }
    }

    suspend fun readingSessionsForDate(date: LocalDate): List<ReadingSessionLog> {
        val instance = dataSource.habitInstanceDao.getById(READING_HABIT_INSTANCE_ID) ?: return emptyList()
        return dataSource.timerHabitRepository.sessionsForDate(instance, date)
    }

    fun deleteReadingSession(session: ReadingSessionLog) {
        viewModelScope.launch {
            dataSource.timerHabitRepository.deleteSession(session)
            refresh()
        }
    }

    fun undoTanakhMarkRead(date: LocalDate) {
        viewModelScope.launch {
            val instance = dataSource.habitInstanceDao.getById(TANAKH_HABIT_INSTANCE_ID) ?: return@launch
            dataSource.scheduleCursorRepository.undoMarkRead(instance, date)
            refresh()
        }
    }

    companion object {
        fun factory(dataSource: ActivityDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = ActivityViewModel(dataSource) as T
            }
    }
}
