package com.ziv.reminders.data

import java.time.LocalDate

/**
 * Combines the instance's own anchor config (anchorItemNumber/anchorDate/intervalDays — plain
 * nullable columns on HabitInstance, not a separate config table, mirroring how Counter's
 * counterGoal and Timer's timerTargetSeconds already work) with the persisted running position
 * (ComputedScheduleProgress.nextItemNumber) to produce HabitStatus.ComputedScheduleStatus via
 * the pure deriveComputedScheduleStatus function (ComputedSchedule.kt).
 *
 * **Updated per the Scope Revision (see the section below the CEO Phase 1 header):** this class's
 * doc comment previously said "there is no separate daily-progress table for this kind... so
 * currentStreak() always returns 0; there is nothing to count." That is no longer true. A
 * per-watch-event log now exists (`ComputedScheduleWatchLog`/`ComputedScheduleWatchLogDao`, Task
 * 1), and `currentStreak()` below computes a real value from it via `HabitStats` — the same
 * kind-agnostic `Set<LocalDate>` streak calculator Exercise/Reading/Tanakh's stats already use
 * (`data/HabitStats.kt`). The dashboard row's own display is unchanged: it still shows the episode
 * number in place of a streak count (see ComputedScheduleHabitRow, Task 5) — `currentStreak()`'s
 * real value now feeds the new stats screen instead (Task 6).
 *
 * runInTransaction follows TimerHabitRepository's exact nullable/no-op-default escape hatch
 * (TimerHabitRepository.kt's own doc comment): it wraps markNextWatched's read-then-upsert(-then-
 * insert, as of the Scope Revision) atomically in production (AppContainer passes
 * AppDatabase.withTransaction, same as every other repository that needs this), but defaults to a
 * plain passthrough so this class's own fake-DAO-based tests need no real Room database. Added
 * during /autoplan Eng review — the original draft called todayStatus (a read) then
 * progressDao.upsert (a write) as two separate statements, so two coroutines from a rapid
 * double-tap could both read the same stale nextItemNumber before either write commits, silently
 * losing one tap. The Scope Revision's new watchLogDao.insert call joins the same transaction, not
 * a separate one — a crash between the progress upsert and the watch-log insert must not leave
 * `nextItemNumber` advanced with no matching log entry (which would silently corrupt the streak).
 */
class ComputedScheduleRepository(
    private val progressDao: ComputedScheduleProgressDao,
    private val watchLogDao: ComputedScheduleWatchLogDao,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() },
) {

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus.ComputedScheduleStatus {
        val anchorItemNumber = instance.anchorItemNumber
            ?: error("ComputedSchedule instance ${instance.id} is missing anchorItemNumber")
        val anchorDate = instance.anchorDate?.let { LocalDate.parse(it) }
            ?: error("ComputedSchedule instance ${instance.id} is missing anchorDate")
        val intervalDays = instance.intervalDays
            ?: error("ComputedSchedule instance ${instance.id} is missing intervalDays")
        val nextItemNumber = progressDao.getByInstance(instance.id)?.nextItemNumber ?: (anchorItemNumber + 1)
        return deriveComputedScheduleStatus(nextItemNumber, anchorItemNumber, anchorDate, intervalDays, today)
    }

    suspend fun markNextWatched(instance: HabitInstance, today: LocalDate) {
        runInTransaction {
            val status = todayStatus(instance, today)
            // Defensive no-op if dueCount == 0 — ComputedScheduleHabitRow's onClick (Task 5)
            // already refuses to call this while disabled, but this guard makes the repository
            // itself safe to call unconditionally too (see the design doc's explicit tap rule),
            // matching ScheduleCursorRepository.markRead's own defensive Finished/Waiting guard
            // precedent. A no-op tap logs nothing either — see
            // markNextWatched_dueCountZero_isNoOp's watch-log assertion.
            if (status.dueCount == 0) return@runInTransaction
            // Always +1 per tap, even if dueCount > 1 (multiple episodes behind) — one tap means
            // "I watched the next one," not "I'm caught up." Catching up from a backlog takes
            // one tap per episode (see the design doc's Recommended Approach).
            progressDao.upsert(ComputedScheduleProgress(instance.id, status.nextItemNumber + 1))
            // Added per the Scope Revision — logs the episode that was actually just watched
            // (status.nextItemNumber, the pre-increment value), not the new nextItemNumber above,
            // and today's date, not the episode's release date (a backlog catch-up tap logs
            // "watched today," even though the episode itself released earlier).
            watchLogDao.insert(ComputedScheduleWatchLog(habitInstanceId = instance.id, date = today.toString(), episodeNumber = status.nextItemNumber))
        }
    }

    /**
     * Added per the Scope Revision (see the section below the CEO Phase 1 header) — this method
     * previously always returned 0 ("there is nothing to count"). Mirrors
     * ExerciseViewModel/ActivityViewModel's own existing `HabitStats.parseDates(...)` +
     * `HabitStats.currentStreak(...)` two-step pattern, not `ScheduleCursorRepository.currentStreak`'s
     * `StreakCalculator` (which additionally weighs `enabledDaysMask` to let disabled days pass
     * through without breaking a streak) — this kind's `enabledDaysMask` is always "every day"
     * (see HabitSeeding.kt, Task 7), so `HabitStats`'s plain consecutive-calendar-day definition is
     * the correct, simpler fit, and matches this repo's other kind-agnostic `Set<LocalDate>`
     * consumer exactly, per the user's explicit "matching established precedent fully" request.
     */
    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int {
        val dates = HabitStats.parseDates(watchLogDao.getWatchedDates(instance.id))
        return HabitStats.currentStreak(dates, today)
    }

    // Feeds the new stats screen's heatmap (Task 6) — mirrors CounterHabitRepository/
    // TimerHabitRepository/ScheduleCursorRepository's own completedDates(instance): List<String>
    // method, all four now sharing the identical shape.
    suspend fun completedDates(instance: HabitInstance): List<String> = watchLogDao.getWatchedDates(instance.id)
}
