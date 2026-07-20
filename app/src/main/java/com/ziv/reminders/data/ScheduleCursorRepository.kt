package com.ziv.reminders.data

import java.time.LocalDate

/**
 * Combines the pure schedule-position status (deriveScheduleEntryStatus) with a per-day "did I
 * mark anything today" flag to produce the engine-wide HabitStatus.ScheduleCursorStatus.
 * completed reflects only today's activity (streak-relevant, per the design doc's rule), not
 * whether the whole backlog is cleared — matching Counter/Timer's shared "todayStatus.completed"
 * contract used generically by HabitEngine/HabitReminderReceiver/the dashboard checkmark.
 *
 * undoMarkRead reverses only the most recently marked-read entry — Tanakh's cursor is a single
 * global position, not independent per-day state like Exercise's sub-counters or Reading's
 * session log, so "undo" is meaningful only for the current cursor position, never an
 * arbitrary past day (see this feature's design doc, Recommended Approach). The Activity
 * screen's Tanakh day-edit dialog (Task 6) only offers this action when the tapped day is
 * today.
 */
class ScheduleCursorRepository(
    private val progressDao: ScheduleCursorProgressDao,
    private val dailyProgressDao: ScheduleCursorDailyProgressDao,
    private val schedule: List<ScheduleEntry>,
) {

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus.ScheduleCursorStatus {
        val cursorIndex = progressDao.getByInstance(instance.id)?.cursorIndex ?: 0
        val completedToday = dailyProgressDao.getByDate(instance.id, today.toString())?.completed ?: false
        return when (val status = deriveScheduleEntryStatus(schedule, cursorIndex, today)) {
            is ScheduleEntryStatus.Finished ->
                HabitStatus.ScheduleCursorStatus(book = null, chapterHeb = null, dueCount = 0, completed = completedToday, finished = true)
            is ScheduleEntryStatus.OnSchedule ->
                HabitStatus.ScheduleCursorStatus(status.entry.book, status.entry.chapterHeb, dueCount = 0, completed = completedToday, finished = false)
            is ScheduleEntryStatus.Behind ->
                HabitStatus.ScheduleCursorStatus(status.entry.book, status.entry.chapterHeb, dueCount = status.dueCount, completed = completedToday, finished = false)
            is ScheduleEntryStatus.Waiting ->
                HabitStatus.ScheduleCursorStatus(status.entry.book, status.entry.chapterHeb, dueCount = 0, completed = completedToday, finished = false)
        }
    }

    suspend fun markRead(instance: HabitInstance, today: LocalDate) {
        val cursorIndex = progressDao.getByInstance(instance.id)?.cursorIndex ?: 0
        // No-op once the schedule is exhausted (all entries read, or the bundled CSV failed to
        // load and fell back to an empty list) — otherwise tapping a "Finished" row would still
        // advance the cursor past the end and falsely credit a streak day for nothing read.
        if (deriveScheduleEntryStatus(schedule, cursorIndex, today) is ScheduleEntryStatus.Finished) return
        progressDao.upsert(ScheduleCursorProgress(instance.id, cursorIndex + 1))

        val key = today.toString()
        val newCount = (dailyProgressDao.getByDate(instance.id, key)?.entriesMarkedRead ?: 0) + 1
        dailyProgressDao.upsert(ScheduleCursorDailyProgress(instance.id, key, entriesMarkedRead = newCount, completed = true))
    }

    suspend fun undoMarkRead(instance: HabitInstance, date: LocalDate) {
        val progress = progressDao.getByInstance(instance.id) ?: return
        if (progress.cursorIndex <= 0) return // nothing to undo
        progressDao.upsert(progress.copy(cursorIndex = progress.cursorIndex - 1))

        val key = date.toString()
        val daily = dailyProgressDao.getByDate(instance.id, key) ?: return
        val newCount = (daily.entriesMarkedRead - 1).coerceAtLeast(0)
        dailyProgressDao.upsert(daily.copy(entriesMarkedRead = newCount, completed = newCount > 0))
    }

    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int {
        val completedDates = dailyProgressDao.getCompletedDates(instance.id)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
        return StreakCalculator.calculate(completedDates, instance.enabledDaysMask, today)
    }

    // Feeds the Activity screen's Tanakh heatmap (Task 6) and WeeklySummary's aggregation (Task 4).
    suspend fun completedDates(instance: HabitInstance): List<String> = dailyProgressDao.getCompletedDates(instance.id)
}
