package com.ziv.reminders.data

import java.time.LocalDate

/**
 * Timestamp-delta model (mirrors ReadBook's proven ReadingTimerRepository): only
 * TimerDailyProgress.activeSessionStartedAt is written while a session runs, and elapsed time
 * is computed from it at read/stop time — never a periodic tick-decrement — so there's no
 * data-loss window and no drift under Doze. Unlike ReadBook, streaks are computed on demand from
 * completed-date rows (StreakCalculator), not cached in a separate Stats table, matching
 * CounterHabitRepository's on-the-fly approach.
 *
 * sessionLogDao is nullable, defaulting to null, deliberately — 8 test files across this
 * codebase already construct this class with the 2-arg (dao, clock) signature; making session
 * logging a required 3rd parameter would force mechanical edits to every one of them for a
 * feature they don't test. When present, every finishSession() call logs one row; when absent
 * (existing tests), session logging is silently skipped and nothing else changes.
 *
 * runInTransaction follows the same nullable/no-op-default escape hatch, for the same reason:
 * it wraps resetToday()'s upsert-then-delete pair atomically in production (AppContainer passes
 * AppDatabase.withTransaction), but defaults to a plain passthrough so every fake-DAO-based test
 * — including this class's own test file — needs no real Room database.
 */
class TimerHabitRepository(
    private val dao: TimerDailyProgressDao,
    private val clock: Clock,
    private val sessionLogDao: ReadingSessionLogDao? = null,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() },
) {

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus.TimerStatus {
        val target = requireNotNull(instance.timerTargetSeconds) { "Timer habit ${instance.id} has no timerTargetSeconds" }
        val row = dao.getByDate(instance.id, today.toString())
            ?: return HabitStatus.TimerStatus(remainingSeconds = target, targetSeconds = target, isRunning = false, completed = false)
        val startedAt = row.activeSessionStartedAt
        val remaining = if (startedAt != null) {
            val elapsedSeconds = ((clock.nowMillis() - startedAt) / 1000L).toInt()
            (row.remainingSeconds - elapsedSeconds).coerceAtLeast(0)
        } else {
            row.remainingSeconds
        }
        return HabitStatus.TimerStatus(
            remainingSeconds = remaining, targetSeconds = row.targetSeconds,
            isRunning = startedAt != null, completed = row.completed,
        )
    }

    suspend fun start(instance: HabitInstance, today: LocalDate): TimerDailyProgress {
        val target = requireNotNull(instance.timerTargetSeconds) { "Timer habit ${instance.id} has no timerTargetSeconds" }
        val key = today.toString()
        val existing = dao.getByDate(instance.id, key)
        if (existing?.completed == true) return existing // guard: don't restart an already-completed day
        if (existing?.activeSessionStartedAt != null) return existing // idempotent — already running, don't reset the clock
        val row = existing?.copy(activeSessionStartedAt = clock.nowMillis())
            ?: TimerDailyProgress(
                habitInstanceId = instance.id, date = key, targetSeconds = target, remainingSeconds = target,
                completed = false, completedAt = null, activeSessionStartedAt = clock.nowMillis(),
            )
        dao.upsert(row)
        return row
    }

    suspend fun stop(instance: HabitInstance, today: LocalDate): TimerDailyProgress? {
        val row = dao.getByDate(instance.id, today.toString()) ?: return null
        if (row.activeSessionStartedAt == null) return row // idempotent — nothing running
        return finishSession(row)
    }

    /** Call on app launch: finds every session left dangling by a process kill, across every
     * habit instance, and closes each one out — see RemindersApp's startup self-heal.
     * allowCompletion = false: a crash-reconciled session must never itself mark a day completed
     * or extend the streak — the elapsed gap it credits may span hours of the app simply being
     * closed, not real usage. Only a genuine in-app stop() may mark a day completed. */
    suspend fun reconcileCrashedSessions(): List<TimerDailyProgress> =
        dao.getActiveSessions().map { finishSession(it, allowCompletion = false) }

    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int {
        val completedDates = dao.getCompletedDates(instance.id)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
        return StreakCalculator.calculate(completedDates, instance.enabledDaysMask, today)
    }

    // Feeds HabitStats' totalCount and the Activity screen's Reading heatmap (Task 5/6), and
    // WeeklySummary's aggregation (Task 4) — mirrors CounterHabitRepository.completedDates.
    suspend fun completedDates(instance: HabitInstance): List<String> = dao.getCompletedDates(instance.id)

    // Feeds the Activity screen's Reading day-edit dialog (Task 6).
    suspend fun sessionsForDate(instance: HabitInstance, date: LocalDate): List<ReadingSessionLog> =
        sessionLogDao?.getForDate(instance.id, date.toString()) ?: emptyList()

    suspend fun deleteSession(session: ReadingSessionLog) {
        sessionLogDao?.delete(session)
    }

    /** Full reset: discards any active session by calling stop() itself, awaited, as its first
     * internal step — never routed through TimerService/an Intent — so any active session is
     * correctly closed out (and logged) before this function's own writes run. Then overwrites
     * today's row back to defaults and deletes every session log for today, including whichever
     * one stop() may have just inserted. The upsert-then-delete pair runs inside
     * [runInTransaction] so those two writes land atomically together. A caller that also needs
     * to stop TimerService's foreground notification must send that Intent only after this
     * suspend function returns, never before or concurrently — see this class's doc comment and
     * the "Reading Reset" design doc's corrected sequencing for why.
     *
     * Known pre-existing limitation (noted during /autoplan Eng review, not introduced by this
     * function): stop()'s own read-then-write in finishSession() has no lock, so a truly
     * concurrent caller (e.g. TimerService's autoCompleteJob) reading the row between this
     * function's internal stop() call and its own read could theoretically race — narrower than
     * the race this function was written to close (see above), and, like that one, an accepted
     * edge case for a single-user app rather than something worth adding locking for. */
    suspend fun resetToday(instance: HabitInstance, today: LocalDate) {
        stop(instance, today)
        val target = requireNotNull(instance.timerTargetSeconds) { "Timer habit ${instance.id} has no timerTargetSeconds" }
        val key = today.toString()
        runInTransaction {
            dao.upsert(
                TimerDailyProgress(
                    habitInstanceId = instance.id, date = key, targetSeconds = target, remainingSeconds = target,
                    completed = false, completedAt = null, activeSessionStartedAt = null,
                )
            )
            sessionLogDao?.deleteForDate(instance.id, key)
        }
    }

    private suspend fun finishSession(row: TimerDailyProgress, allowCompletion: Boolean = true): TimerDailyProgress {
        val startedAt = requireNotNull(row.activeSessionStartedAt)
        val endedAt = clock.nowMillis()
        val elapsedSeconds = ((endedAt - startedAt) / 1000L).toInt()
        val newRemaining = (row.remainingSeconds - elapsedSeconds).coerceAtLeast(0)
        val justCompleted = allowCompletion && newRemaining == 0
        val updated = row.copy(
            remainingSeconds = newRemaining,
            completed = row.completed || justCompleted,
            completedAt = if (justCompleted) endedAt else row.completedAt,
            activeSessionStartedAt = null,
        )
        dao.upsert(updated)
        sessionLogDao?.insert(
            ReadingSessionLog(
                habitInstanceId = row.habitInstanceId, date = row.date,
                startedAt = startedAt, endedAt = endedAt, durationSeconds = elapsedSeconds,
            )
        )
        return updated
    }
}
