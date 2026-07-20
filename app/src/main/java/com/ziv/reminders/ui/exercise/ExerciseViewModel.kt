package com.ziv.reminders.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.ALL_EXERCISE_KEYS
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.EXERCISE_SUB_COUNTER_DEFAULT
import com.ziv.reminders.data.ExerciseDetailDataSource
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.data.HabitStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExerciseUiState(
    val current: Int = 0,
    val goal: Int = 5,
    val completed: Boolean = false,
    val streak: Int = 0,
    val isNewStreakRecord: Boolean = false,
    val monthCount: Int = 0,
    val isNewMonthRecord: Boolean = false,
    val totalCount: Int = 0,
    val subCounters: Map<String, Int> = ALL_EXERCISE_KEYS.associateWith { EXERCISE_SUB_COUNTER_DEFAULT },
    val completedDates: Set<LocalDate> = emptySet(),
    val isLoaded: Boolean = false,
)

class ExerciseViewModel(private val dataSource: ExerciseDetailDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(ExerciseUiState())
    val uiState: StateFlow<ExerciseUiState> = _uiState.asStateFlow()

    private var instance: HabitInstance? = null

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val exerciseInstance = dataSource.habitInstanceDao.getById(EXERCISE_HABIT_INSTANCE_ID) ?: return@launch
            instance = exerciseInstance

            val status = dataSource.habitEngine.todayStatus(exerciseInstance, today) as HabitStatus.CounterStatus
            val rawDates = dataSource.counterHabitRepository.completedDates(exerciseInstance)
            val dates = HabitStats.parseDates(rawDates)
            val streak = dataSource.habitEngine.currentStreak(exerciseInstance, today)
            val subCounters = ALL_EXERCISE_KEYS.associateWith { key -> dataSource.subCounterRepository.todayValue(key, today) }

            _uiState.value = ExerciseUiState(
                current = status.current,
                goal = status.goal,
                completed = status.completed,
                streak = streak,
                isNewStreakRecord = HabitStats.isNewStreakRecord(dates, today),
                monthCount = HabitStats.monthCount(dates, today),
                isNewMonthRecord = HabitStats.isNewMonthRecord(dates, today),
                totalCount = HabitStats.totalCount(dates),
                subCounters = subCounters,
                completedDates = dates,
                isLoaded = true,
            )
        }
    }

    fun increment() {
        viewModelScope.launch {
            val exerciseInstance = instance ?: dataSource.habitInstanceDao.getById(EXERCISE_HABIT_INSTANCE_ID) ?: return@launch
            dataSource.counterHabitRepository.increment(exerciseInstance, LocalDate.now())
            refresh()
        }
    }

    fun adjustSubCounter(exerciseKey: String, delta: Int) {
        viewModelScope.launch {
            dataSource.subCounterRepository.adjust(exerciseKey, LocalDate.now(), delta)
            refresh()
        }
    }

    // Unlike adjustSubCounter (always today, called from the live counter screen), this takes
    // an explicit date — used by SubCounterDetailDialog to edit a past day's value. No new
    // repository method needed: SubCounterRepository.adjust already takes an arbitrary
    // LocalDate despite its parameter being named "today".
    fun adjustSubCounterForDate(exerciseKey: String, date: LocalDate, delta: Int) {
        viewModelScope.launch {
            dataSource.subCounterRepository.adjust(exerciseKey, date, delta)
            refresh()
        }
    }

    suspend fun subCounterValuesForDate(date: LocalDate): Map<String, Int?> {
        val values = dataSource.subCounterRepository.valuesForDate(date)
        return ALL_EXERCISE_KEYS.associateWith { key -> values[key] }
    }

    companion object {
        fun factory(dataSource: ExerciseDetailDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = ExerciseViewModel(dataSource) as T
            }
    }
}
