package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HabitStatsTest {

    @Test
    fun parseDates_emptyList_returnsEmptySet() {
        assertEquals(emptySet(), HabitStats.parseDates(emptyList()))
    }

    @Test
    fun parseDates_malformedEntry_isSkippedAndReported() {
        val malformed = mutableListOf<String>()
        val result = HabitStats.parseDates(listOf("2026-07-01", "not-a-date", "2026-07-02")) { malformed.add(it) }

        assertEquals(setOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2)), result)
        assertEquals(listOf("not-a-date"), malformed)
    }

    @Test
    fun monthCount_countsOnlyCurrentMonthAndYear() {
        val today = LocalDate.of(2026, 7, 19)
        val dates = setOf(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 6, 30), LocalDate.of(2025, 7, 1),
        )
        assertEquals(2, HabitStats.monthCount(dates, today))
    }

    @Test
    fun currentStreak_todayIncluded_countsThroughToday() {
        val today = LocalDate.of(2026, 7, 19)
        val dates = setOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, HabitStats.currentStreak(dates, today))
    }

    @Test
    fun currentStreak_todayNotYetDone_countsThroughYesterday() {
        val today = LocalDate.of(2026, 7, 19)
        val dates = setOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, HabitStats.currentStreak(dates, today))
    }

    @Test
    fun currentStreak_gapBreaksStreak() {
        val today = LocalDate.of(2026, 7, 19)
        val dates = setOf(today, today.minusDays(2))
        assertEquals(1, HabitStats.currentStreak(dates, today))
    }

    @Test
    fun longestStreak_findsLongestConsecutiveRun() {
        val dates = setOf(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3),
            LocalDate.of(2026, 7, 10),
        )
        assertEquals(3, HabitStats.longestStreak(dates))
    }

    @Test
    fun bestMonth_findsMonthWithMostDates() {
        val dates = setOf(
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3),
        )
        assertEquals(3, HabitStats.bestMonth(dates))
    }

    @Test
    fun isNewStreakRecord_currentStreakExceedsPastBaseline_isTrue() {
        val today = LocalDate.of(2026, 7, 19)
        // past streak of 2 (days 10-11), current streak of 3 (days 17-19)
        val dates = setOf(
            LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 11),
            LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 18), today,
        )
        assertTrue(HabitStats.isNewStreakRecord(dates, today))
    }

    @Test
    fun isNewStreakRecord_currentStreakDoesNotExceedPastBaseline_isFalse() {
        val today = LocalDate.of(2026, 7, 19)
        // past streak of 5, current streak of 1
        val dates = setOf(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3),
            LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 5), today,
        )
        assertFalse(HabitStats.isNewStreakRecord(dates, today))
    }

    @Test
    fun isNewMonthRecord_currentMonthExceedsPastBestMonth_isTrue() {
        val today = LocalDate.of(2026, 7, 19)
        val dates = setOf(
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3), today,
        )
        assertTrue(HabitStats.isNewMonthRecord(dates, today))
    }

    @Test
    fun recordSuffix_isRecordTrue_appendsSuffix() {
        assertEquals(" — new record!", HabitStats.recordSuffix(true, "record"))
    }

    @Test
    fun recordSuffix_isRecordFalse_returnsEmptyString() {
        assertEquals("", HabitStats.recordSuffix(false, "record"))
    }

    @Test
    fun totalCount_countsEveryDateRegardlessOfContiguity() {
        val dates = setOf(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5), LocalDate.of(2025, 12, 25),
        )
        assertEquals(3, HabitStats.totalCount(dates))
    }

    @Test
    fun totalCount_emptySet_isZero() {
        assertEquals(0, HabitStats.totalCount(emptySet()))
    }
}
