package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklySummaryTest {

    private val today = LocalDate.of(2026, 7, 19)

    @Test
    fun compute_countsOnlyDatesWithinTheSevenDayWindow() {
        val exerciseDates = setOf(today, today.minusDays(3), today.minusDays(10)) // 10 days ago is outside the window
        val summary = WeeklySummary.compute(exerciseDates, emptySet(), emptySet(), today)
        assertEquals(2, summary.exerciseDays)
    }

    @Test
    fun compute_windowIsInclusiveOfTodayAndSixDaysBack() {
        val exerciseDates = setOf(today.minusDays(6)) // exactly the oldest day in a 7-day window
        val summary = WeeklySummary.compute(exerciseDates, emptySet(), emptySet(), today)
        assertEquals(1, summary.exerciseDays)
    }

    @Test
    fun compute_eachHabitCountedIndependently() {
        val exerciseDates = setOf(today)
        val readingDates = setOf(today, today.minusDays(1))
        val tanakhDates = emptySet<LocalDate>()
        val summary = WeeklySummary.compute(exerciseDates, readingDates, tanakhDates, today)
        assertEquals(1, summary.exerciseDays)
        assertEquals(2, summary.readingDays)
        assertEquals(0, summary.tanakhDays)
    }

    @Test
    fun compute_comboStreak_countsOnlyDaysAllThreeHabitsWereHit() {
        val exerciseDates = setOf(today, today.minusDays(1))
        val readingDates = setOf(today, today.minusDays(1), today.minusDays(2))
        val tanakhDates = setOf(today) // only today has all three
        val summary = WeeklySummary.compute(exerciseDates, readingDates, tanakhDates, today)
        assertEquals(1, summary.comboStreak)
    }

    @Test
    fun compute_comboStreak_noOverlap_isZero() {
        val summary = WeeklySummary.compute(setOf(today), setOf(today.minusDays(1)), emptySet(), today)
        assertEquals(0, summary.comboStreak)
    }

    @Test
    fun compute_allEmpty_isAllZeroes() {
        val summary = WeeklySummary.compute(emptySet(), emptySet(), emptySet(), today)
        assertEquals(WeeklyHabitCount(0, 0, 0, 0), summary)
    }
}
