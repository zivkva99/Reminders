package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class Cpp26ScheduleTest {

    @Test
    fun buildCpp26Schedule_hasNineteenEntriesInTheGivenOrder() {
        val schedule = buildCpp26Schedule(LocalDate.of(2026, 9, 2))

        assertEquals(19, schedule.size)
        assertEquals(CPP26_CHAPTERS, schedule.map { it.chapterHeb.toInt() })
    }

    @Test
    fun buildCpp26Schedule_oneEntryPerConsecutiveCalendarDay_noGaps() {
        // 7 days a week, unlike Tanakh's Sun-Thu CSV — every entry is exactly one day after the
        // previous one, weekends included.
        val schedule = buildCpp26Schedule(LocalDate.of(2026, 9, 2))

        val expectedDates = (0 until 19).map { LocalDate.of(2026, 9, 2).plusDays(it.toLong()) }
        assertEquals(expectedDates, schedule.map { it.date })
    }

    @Test
    fun buildCpp26Schedule_firstAndLastChapters_matchTheGivenList() {
        val schedule = buildCpp26Schedule(LocalDate.of(2026, 9, 2))

        assertEquals("414", schedule.first().chapterHeb)
        assertEquals(LocalDate.of(2026, 9, 2), schedule.first().date)
        assertEquals("548", schedule.last().chapterHeb)
        assertEquals(LocalDate.of(2026, 9, 20), schedule.last().date)
    }
}
