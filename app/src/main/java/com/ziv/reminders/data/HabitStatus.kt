package com.ziv.reminders.data

import java.time.LocalDate

/**
 * The one type unified across every kind — see HabitEngine (engine/HabitEngine.kt) for why only
 * the read path (todayStatus/currentStreak) is generic; each kind's own progress-marking action
 * stays a method on that kind's own repository.
 */
sealed interface HabitStatus {
    data class CounterStatus(val current: Int, val goal: Int, val completed: Boolean) : HabitStatus
    data class TimerStatus(
        val remainingSeconds: Int,
        val targetSeconds: Int,
        val isRunning: Boolean,
        val completed: Boolean,
    ) : HabitStatus
    data class ScheduleCursorStatus(
        val book: String?,
        val chapterHeb: String?,
        val dueCount: Int,
        val completed: Boolean,
        val finished: Boolean,
        val isDueToday: Boolean,
        // How many entries were marked read *today* specifically (not the running cursor
        // position) — lets the dashboard distinguish "behind, untouched today" from "behind, but
        // already read 2+ chapters today" even though dueCount itself doesn't change until
        // tomorrow's date rolls the schedule forward. See ScheduleCursorRepository.todayStatus.
        val entriesReadToday: Int,
    ) : HabitStatus
    /**
     * dueCount's shape here is deliberately NOT the same as ScheduleCursorStatus's dueCount — see
     * ComputedSchedule.kt's deriveComputedScheduleStatus doc comment for exactly why. There is no
     * `completed`/`finished` field: this status is derived purely from the anchor/interval math
     * and the running `nextItemNumber`, and the series never ends. A per-watch-event log
     * (`ComputedScheduleWatchLog`, added per the Scope Revision below the CEO Phase 1 header) does
     * now exist, but it feeds `currentStreak()`/the stats screen only — it is never read here, so
     * this status type's shape is unaffected by that addition.
     */
    data class ComputedScheduleStatus(
        val nextItemNumber: Int,
        val dueCount: Int,
        val isDueToday: Boolean,
    ) : HabitStatus
    data class IntervalDueStatus(
        val dueDate: LocalDate,
        val isDue: Boolean,
        val completedToday: Boolean,
    ) : HabitStatus
}
