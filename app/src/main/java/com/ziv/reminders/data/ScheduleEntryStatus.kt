package com.ziv.reminders.data

import java.time.LocalDate

/**
 * Pure schedule-position status, independent of any per-day "did I mark something today" flag —
 * ScheduleCursorRepository (Task 3) combines this with that flag to produce the engine-wide
 * HabitStatus.ScheduleCursorStatus. Mirrors ReadBook's BibleReadingStatus derivation exactly.
 */
sealed interface ScheduleEntryStatus {
    data class OnSchedule(val entry: ScheduleEntry) : ScheduleEntryStatus
    data class Behind(val entry: ScheduleEntry, val dueCount: Int) : ScheduleEntryStatus
    data class Waiting(val entry: ScheduleEntry) : ScheduleEntryStatus
    data object Finished : ScheduleEntryStatus
}

/**
 * cursorIndex is the index of the next unread entry. The schedule's dates never move (no reflow
 * on a missed day) — falling behind means catching up one entry at a time, never skipping ahead
 * to "today's" entry.
 */
fun deriveScheduleEntryStatus(
    schedule: List<ScheduleEntry>,
    cursorIndex: Int,
    today: LocalDate,
): ScheduleEntryStatus {
    val entry = schedule.getOrNull(cursorIndex) ?: return ScheduleEntryStatus.Finished
    return when {
        entry.date.isEqual(today) -> ScheduleEntryStatus.OnSchedule(entry)
        entry.date.isBefore(today) -> {
            val lastDueIndex = schedule.indexOfLast { !it.date.isAfter(today) }
            val dueCount = lastDueIndex - cursorIndex + 1
            ScheduleEntryStatus.Behind(entry, dueCount)
        }
        else -> ScheduleEntryStatus.Waiting(entry)
    }
}
