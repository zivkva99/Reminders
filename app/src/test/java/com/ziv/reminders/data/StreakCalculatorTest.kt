package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakCalculatorTest {

    private val sunThuMask = 0b0011111 // Sun-Thu
    private val allDaysMask = 0b1111111

    @Test
    fun calculate_todayCompleted_countsFromToday() {
        val thursday = LocalDate.of(2026, 7, 16)
        val completed = setOf(thursday, thursday.minusDays(1), thursday.minusDays(2))

        assertEquals(3, StreakCalculator.calculate(completed, sunThuMask, thursday))
    }

    @Test
    fun calculate_todayNotYetCompleted_anchorsAtYesterday() {
        val thursday = LocalDate.of(2026, 7, 16)
        val completed = setOf(thursday.minusDays(1), thursday.minusDays(2)) // yesterday, day before — not today

        assertEquals(2, StreakCalculator.calculate(completed, sunThuMask, thursday))
    }

    @Test
    fun calculate_gapOnAnEnabledDay_breaksTheStreak() {
        val thursday = LocalDate.of(2026, 7, 16)
        // Wednesday (yesterday) missing, Tuesday completed — gap on an enabled day.
        val completed = setOf(thursday.minusDays(2))

        assertEquals(0, StreakCalculator.calculate(completed, sunThuMask, thursday))
    }

    @Test
    fun calculate_gapOnADisabledDay_isSkippedWithoutBreakingTheStreak() {
        // Sunday, with Fri/Sat (disabled under Sun-Thu) having no rows at all.
        val sunday = LocalDate.of(2026, 7, 19)
        val thursdayBefore = sunday.minusDays(3) // the previous Thursday
        val completed = setOf(sunday, thursdayBefore) // Fri/Sat between them intentionally absent

        assertEquals(2, StreakCalculator.calculate(completed, sunThuMask, sunday))
    }

    @Test
    fun calculate_allDaysDisabled_returnsZero() {
        val today = LocalDate.of(2026, 7, 16)
        assertEquals(0, StreakCalculator.calculate(setOf(today), enabledDaysMask = 0, today))
    }

    @Test
    fun calculate_everyDayEnabled_matchesASimpleConsecutiveWalk() {
        val today = LocalDate.of(2026, 7, 16)
        val completed = setOf(today, today.minusDays(1), today.minusDays(2))

        assertEquals(3, StreakCalculator.calculate(completed, allDaysMask, today))
    }
}
