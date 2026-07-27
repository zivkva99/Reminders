package com.ziv.reminders.data

import java.time.LocalDate

/**
 * markDone/rescheduleOnly both require intervalDays >= 1 (repository-level guard, not just the
 * picker dialog's UI constraint — see design doc's fix for the reviewer-found gap where an
 * unguarded 0/negative value could land nextDueDate in the past, reproducing the exact bug
 * Premise 2 ("count from today, not the old due date") was written to prevent). Both always
 * compute nextDueDate = today + intervalDays, never (old due date) + intervalDays — that's the
 * whole point of counting from today. No upper bound — intentionally unbounded, matching this
 * app's existing risk tolerance for a personal, single-user app (see design doc's Testing Plan).
 */
class IntervalDueRepository(
    private val progressDao: IntervalDueProgressDao,
    private val logDao: IntervalDueLogDao,
) {

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus.IntervalDueStatus {
        val dueDate = progressDao.getByInstance(instance.id)?.nextDueDate?.let(LocalDate::parse) ?: today
        val completedToday = logDao.getByDate(instance.id, today.toString()) != null
        return HabitStatus.IntervalDueStatus(
            dueDate = dueDate,
            isDue = !dueDate.isAfter(today),
            completedToday = completedToday,
        )
    }

    suspend fun markDone(instance: HabitInstance, intervalDays: Int, today: LocalDate) {
        require(intervalDays >= 1) { "intervalDays must be >= 1, was $intervalDays" }
        progressDao.upsert(IntervalDueProgress(instance.id, nextDueDate = today.plusDays(intervalDays.toLong()).toString()))
        logDao.insert(IntervalDueLog(habitInstanceId = instance.id, date = today.toString()))
    }

    suspend fun rescheduleOnly(instance: HabitInstance, intervalDays: Int, today: LocalDate) {
        require(intervalDays >= 1) { "intervalDays must be >= 1, was $intervalDays" }
        progressDao.upsert(IntervalDueProgress(instance.id, nextDueDate = today.plusDays(intervalDays.toLong()).toString()))
    }

    suspend fun history(instance: HabitInstance): List<LocalDate> =
        logDao.getAllForInstance(instance.id).map { LocalDate.parse(it.date) }

    /** Matches every other kind's completedDates(instance): List<String> shape (e.g.
     * ComputedScheduleRepository.completedDates), for symmetry — not currently consumed by
     * WeeklySummary (this kind isn't part of that cross-habit aggregate), but kept consistent in
     * case a future stats/summary screen wants it. */
    suspend fun completedDates(instance: HabitInstance): List<String> =
        logDao.getAllForInstance(instance.id).map { it.date }
}
