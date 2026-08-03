package com.ziv.reminders.ui.dashboard

import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.LEGO_KIT_HABIT_INSTANCE_ID
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class DashboardRowOrderingTest {

    private val today = LocalDate.of(2026, 7, 27) // a Monday

    @Test
    fun counterStatus_notCompleted_isRed() {
        val status = HabitStatus.CounterStatus(current = 0, goal = 5, completed = false)
        assertEquals(RowUrgency.RED, dashboardRowUrgency(EXERCISE_HABIT_INSTANCE_ID, status, today, 0b1111111))
    }

    @Test
    fun counterStatus_completed_isGreen() {
        val status = HabitStatus.CounterStatus(current = 5, goal = 5, completed = true)
        assertEquals(RowUrgency.GREEN, dashboardRowUrgency(EXERCISE_HABIT_INSTANCE_ID, status, today, 0b1111111))
    }

    @Test
    fun legoKitRow_offDayNotCompleted_isGreenNotRed() {
        // Mirrors LegoKitHabitRow's dot logic — off-day dims to the neutral outline color, not
        // red, so it must not sort with the overdue rows. Mask of 0 means "never enabled",
        // so today is guaranteed to be an off-day regardless of which day of week it is.
        val status = HabitStatus.CounterStatus(current = 0, goal = 1, completed = false)
        assertEquals(RowUrgency.GREEN, dashboardRowUrgency(LEGO_KIT_HABIT_INSTANCE_ID, status, today, enabledDaysMask = 0))
    }

    @Test
    fun legoKitRow_enabledDayNotCompleted_isRed() {
        val status = HabitStatus.CounterStatus(current = 0, goal = 1, completed = false)
        assertEquals(RowUrgency.RED, dashboardRowUrgency(LEGO_KIT_HABIT_INSTANCE_ID, status, today, enabledDaysMask = 0b1111111))
    }

    @Test
    fun timerStatus_notCompleted_isRed() {
        val status = HabitStatus.TimerStatus(remainingSeconds = 900, targetSeconds = 900, isRunning = false, completed = false)
        assertEquals(RowUrgency.RED, dashboardRowUrgency(2L, status, today, 0b1111111))
    }

    @Test
    fun timerStatus_completed_isGreen() {
        val status = HabitStatus.TimerStatus(remainingSeconds = 0, targetSeconds = 900, isRunning = false, completed = true)
        assertEquals(RowUrgency.GREEN, dashboardRowUrgency(2L, status, today, 0b1111111))
    }

    @Test
    fun scheduleCursorStatus_behindWithFewerThanTwoReadToday_isRed() {
        val status = HabitStatus.ScheduleCursorStatus("א", "א׳", dueCount = 3, completed = true, finished = false, isDueToday = false, entriesReadToday = 1)
        assertEquals(RowUrgency.RED, dashboardRowUrgency(3L, status, today, 0b1111111))
    }

    @Test
    fun scheduleCursorStatus_behindButReadTwoOrMoreToday_isOrangeNotRed() {
        val status = HabitStatus.ScheduleCursorStatus("א", "א׳", dueCount = 3, completed = true, finished = false, isDueToday = false, entriesReadToday = 2)
        assertEquals(RowUrgency.ORANGE, dashboardRowUrgency(3L, status, today, 0b1111111))
    }

    @Test
    fun scheduleCursorStatus_dueTodayNotBehind_isOrange() {
        val status = HabitStatus.ScheduleCursorStatus("א", "א׳", dueCount = 0, completed = false, finished = false, isDueToday = true, entriesReadToday = 0)
        assertEquals(RowUrgency.ORANGE, dashboardRowUrgency(3L, status, today, 0b1111111))
    }

    @Test
    fun scheduleCursorStatus_onScheduleNotDue_isGreen() {
        val status = HabitStatus.ScheduleCursorStatus(null, null, dueCount = 0, completed = true, finished = true, isDueToday = false, entriesReadToday = 0)
        assertEquals(RowUrgency.GREEN, dashboardRowUrgency(3L, status, today, 0b1111111))
    }

    @Test
    fun computedScheduleStatus_dueCountOfOne_isOrangeNotRed() {
        // dueCount == 1 means "due today" for this kind — red is reserved for dueCount > 1.
        val status = HabitStatus.ComputedScheduleStatus(nextItemNumber = 542, dueCount = 1, isDueToday = true)
        assertEquals(RowUrgency.ORANGE, dashboardRowUrgency(4L, status, today, 0b1111111))
    }

    @Test
    fun computedScheduleStatus_dueCountAboveOne_isRed() {
        val status = HabitStatus.ComputedScheduleStatus(nextItemNumber = 542, dueCount = 2, isDueToday = false)
        assertEquals(RowUrgency.RED, dashboardRowUrgency(4L, status, today, 0b1111111))
    }

    @Test
    fun intervalDueStatus_overdue_isRed() {
        val status = HabitStatus.IntervalDueStatus(dueDate = today.minusDays(2), isDue = true, completedToday = false)
        assertEquals(RowUrgency.RED, dashboardRowUrgency(6L, status, today, 0b1111111))
    }

    @Test
    fun intervalDueStatus_dueToday_isOrange() {
        val status = HabitStatus.IntervalDueStatus(dueDate = today, isDue = true, completedToday = false)
        assertEquals(RowUrgency.ORANGE, dashboardRowUrgency(6L, status, today, 0b1111111))
    }

    @Test
    fun intervalDueStatus_notDue_isGreen() {
        val status = HabitStatus.IntervalDueStatus(dueDate = today.plusDays(3), isDue = false, completedToday = false)
        assertEquals(RowUrgency.GREEN, dashboardRowUrgency(6L, status, today, 0b1111111))
    }
}
