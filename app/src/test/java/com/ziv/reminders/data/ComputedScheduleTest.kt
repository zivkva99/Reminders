package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComputedScheduleTest {

    // Arbitrary test anchor — NOT episode 542's real release date (see HabitSeeding.kt's TODO).
    private val anchorDate = LocalDate.of(2026, 7, 14)

    @Test
    fun computeReleaseDate_sameItemAsAnchor_returnsAnchorDate() {
        assertEquals(anchorDate, computeReleaseDate(anchorItemNumber = 542, anchorDate = anchorDate, intervalDays = 7, itemNumber = 542))
    }

    @Test
    fun computeReleaseDate_laterItem_addsIntervalTimesDelta() {
        assertEquals(LocalDate.of(2026, 7, 21), computeReleaseDate(542, anchorDate, 7, itemNumber = 543))
        assertEquals(LocalDate.of(2026, 7, 28), computeReleaseDate(542, anchorDate, 7, itemNumber = 544))
    }

    @Test
    fun computeReleaseDate_earlierItem_subtractsIntervalTimesDelta() {
        assertEquals(LocalDate.of(2026, 7, 7), computeReleaseDate(542, anchorDate, 7, itemNumber = 541))
    }

    @Test
    fun deriveComputedScheduleStatus_beforeReleaseDate_notDue() {
        // item 543 releases 2026-07-21; today is the day before.
        val status = deriveComputedScheduleStatus(543, 542, anchorDate, 7, today = LocalDate.of(2026, 7, 20))

        assertEquals(0, status.dueCount)
        assertFalse(status.isDueToday)
        assertEquals(543, status.nextItemNumber)
    }

    @Test
    fun deriveComputedScheduleStatus_onReleaseDate_isDueTodayWithCountOne() {
        val status = deriveComputedScheduleStatus(543, 542, anchorDate, 7, today = LocalDate.of(2026, 7, 21))

        assertEquals(1, status.dueCount)
        assertTrue(status.isDueToday)
    }

    @Test
    fun deriveComputedScheduleStatus_midweek_stillDueCountOne_isDueTodayStaysTrue() {
        // item 543 releases 2026-07-21; 3 days later (still within the week before the NEXT
        // release) isDueToday must still be true — it means "there's an unwatched episode
        // released this interval," not literally "released today." A subtly wrong
        // implementation (e.g. isDueToday = today.isEqual(releaseDate)) would pass every
        // other test in this file but flip this one, so this case must be asserted explicitly.
        val status = deriveComputedScheduleStatus(543, 542, anchorDate, 7, today = LocalDate.of(2026, 7, 24))

        assertEquals(1, status.dueCount)
        assertTrue(status.isDueToday)
    }

    @Test
    fun deriveComputedScheduleStatus_oneIntervalLate_dueCountTwo_notIsDueToday() {
        val status = deriveComputedScheduleStatus(543, 542, anchorDate, 7, today = LocalDate.of(2026, 7, 28))

        assertEquals(2, status.dueCount)
        assertFalse(status.isDueToday)
    }

    @Test
    fun deriveComputedScheduleStatus_twoIntervalsLate_dueCountThree() {
        val status = deriveComputedScheduleStatus(543, 542, anchorDate, 7, today = LocalDate.of(2026, 8, 4))

        assertEquals(3, status.dueCount)
        assertFalse(status.isDueToday)
    }
}
