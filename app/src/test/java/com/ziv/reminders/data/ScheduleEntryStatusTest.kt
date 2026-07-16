package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScheduleEntryStatusTest {

    private val schedule = listOf(
        ScheduleEntry("א", "א׳", LocalDate.of(2026, 7, 12)), // Sunday
        ScheduleEntry("א", "ב׳", LocalDate.of(2026, 7, 13)), // Monday
        ScheduleEntry("א", "ג׳", LocalDate.of(2026, 7, 14)), // Tuesday
    )

    @Test
    fun deriveScheduleEntryStatus_cursorAtTodaysEntry_isOnSchedule() {
        val status = deriveScheduleEntryStatus(schedule, cursorIndex = 0, today = LocalDate.of(2026, 7, 12))

        assertEquals(ScheduleEntryStatus.OnSchedule(schedule[0]), status)
    }

    @Test
    fun deriveScheduleEntryStatus_cursorEntryInThePast_isBehindWithCorrectDueCount() {
        // Cursor still at the Sunday entry, but it's now Tuesday — 3 entries due (Sun/Mon/Tue).
        val status = deriveScheduleEntryStatus(schedule, cursorIndex = 0, today = LocalDate.of(2026, 7, 14))

        assertIs<ScheduleEntryStatus.Behind>(status)
        assertEquals(schedule[0], (status as ScheduleEntryStatus.Behind).entry)
        assertEquals(3, status.dueCount)
    }

    @Test
    fun deriveScheduleEntryStatus_cursorEntryInTheFuture_isWaiting() {
        val status = deriveScheduleEntryStatus(schedule, cursorIndex = 1, today = LocalDate.of(2026, 7, 12))

        assertEquals(ScheduleEntryStatus.Waiting(schedule[1]), status)
    }

    @Test
    fun deriveScheduleEntryStatus_cursorPastTheEndOfSchedule_isFinished() {
        val status = deriveScheduleEntryStatus(schedule, cursorIndex = 3, today = LocalDate.of(2026, 7, 20))

        assertEquals(ScheduleEntryStatus.Finished, status)
    }

    @Test
    fun deriveScheduleEntryStatus_neverSkipsAheadToTodaysEntry_evenWhileBehind() {
        // Cursor at index 0 (Sunday, unread), today is Tuesday — the next entry to read is still
        // Sunday's, never Tuesday's, regardless of how far behind.
        val status = deriveScheduleEntryStatus(schedule, cursorIndex = 0, today = LocalDate.of(2026, 7, 14))

        assertEquals(schedule[0], (status as ScheduleEntryStatus.Behind).entry)
    }
}
