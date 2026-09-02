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
 *
 * [scheduleFor] resolves which entry list an instance's cursor walks — added once C++26 became a
 * second SCHEDULE_CURSOR-kind instance (2026-09-02): unlike every other kind, this one's "config"
 * is an externally supplied entry list rather than plain columns on HabitInstance
 * (counterGoal/timerTargetSeconds/anchorItemNumber etc.), so a single shared list — fine when
 * only Tanakh existed — would have made a second instance's cursor walk Tanakh's own book/chapter
 * text. AppContainer's production wiring routes by instance.id (Tanakh → tanakhSchedule, C++26 →
 * cpp26Schedule); the secondary constructor below keeps every pre-existing single-schedule call
 * site (this class's own tests included) compiling unchanged.
 */
class ScheduleCursorRepository(
    private val progressDao: ScheduleCursorProgressDao,
    private val dailyProgressDao: ScheduleCursorDailyProgressDao,
    private val scheduleFor: (HabitInstance) -> List<ScheduleEntry>,
) {
    constructor(
        progressDao: ScheduleCursorProgressDao,
        dailyProgressDao: ScheduleCursorDailyProgressDao,
        schedule: List<ScheduleEntry>,
    ) : this(progressDao, dailyProgressDao, { _: HabitInstance -> schedule })

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus.ScheduleCursorStatus {
        val cursorIndex = progressDao.getByInstance(instance.id)?.cursorIndex ?: 0
        val todayProgress = dailyProgressDao.getByDate(instance.id, today.toString())
        val completedToday = todayProgress?.completed ?: false
        val entriesReadToday = todayProgress?.entriesMarkedRead ?: 0
        return when (val status = deriveScheduleEntryStatus(scheduleFor(instance), cursorIndex, today)) {
            is ScheduleEntryStatus.Finished ->
                HabitStatus.ScheduleCursorStatus(book = null, chapterHeb = null, dueCount = 0, completed = completedToday, finished = true, isDueToday = false, entriesReadToday = entriesReadToday)
            is ScheduleEntryStatus.OnSchedule ->
                HabitStatus.ScheduleCursorStatus(status.entry.book, status.entry.chapterHeb, dueCount = 0, completed = completedToday, finished = false, isDueToday = true, entriesReadToday = entriesReadToday)
            is ScheduleEntryStatus.Behind ->
                HabitStatus.ScheduleCursorStatus(status.entry.book, status.entry.chapterHeb, dueCount = status.dueCount, completed = completedToday, finished = false, isDueToday = false, entriesReadToday = entriesReadToday)
            is ScheduleEntryStatus.Waiting ->
                HabitStatus.ScheduleCursorStatus(status.entry.book, status.entry.chapterHeb, dueCount = 0, completed = completedToday, finished = false, isDueToday = false, entriesReadToday = entriesReadToday)
        }
    }

    suspend fun markRead(instance: HabitInstance, today: LocalDate) {
        val cursorIndex = progressDao.getByInstance(instance.id)?.cursorIndex ?: 0
        // No-op once the schedule is exhausted (Finished), or once the cursor has caught up to —
        // or gotten ahead of — today's date (Waiting): the schedule's dates never move, so
        // nothing new is due until tomorrow. Without the Waiting guard (bug found live
        // 2026-07-21), repeatedly tapping an already-caught-up row silently read ahead through
        // future chapters, defeating the whole catch-up/pacing model this kind exists for.
        val status = deriveScheduleEntryStatus(scheduleFor(instance), cursorIndex, today)
        if (status is ScheduleEntryStatus.Finished || status is ScheduleEntryStatus.Waiting) return
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
