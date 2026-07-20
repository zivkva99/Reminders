# Reading Reset, Tanakh Quick-Undo, and Behind-Count Indicator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the last 3 ReadBook-parity gaps: a "Reset today" action for Reading, a fast quick-undo for Tanakh's mark-read, and a "chapters behind" indicator on Tanakh's dashboard row.

**Architecture:** `TimerHabitRepository` gains `resetToday()`, which awaits its own existing `stop()` as an internal first step (never routed through `TimerService`/an Intent) so any active session is closed out and logged before the reset's own writes run, then atomically overwrites today's progress row and deletes all of today's session-log rows. `DashboardViewModel.onMarkRead` is converted from fire-and-forget to a plain `suspend fun` (mirroring the `ExerciseViewModel.adjustSubCounterForDate` fix from the prior plan) so `DashboardScreen` can await it before showing a quick-undo `Snackbar` on the existing `Scaffold`. Tanakh's "N behind" indicator is a pure text-format change reading an already-computed field.

**Tech Stack:** Kotlin 2.3.0, Room 2.7.1 (KSP, `room-ktx` for `withTransaction`), Jetpack Compose (existing), JUnit4 + `kotlin-test`, Robolectric 4.16.1.

## Global Constraints

- Package `com.ziv.reminders`. `minSdk = 35`, `targetSdk = 36`.
- No new Room schema — this plan adds one DAO method (`ReadingSessionLogDao.deleteForDate`) only; no new tables, columns, or migrations.
- Manual DI via parallel `AppContainer` interfaces (`DashboardDataSource`, `ExerciseDetailDataSource`, `ActivityDataSource`) — never one bloated interface. `DashboardDataSource` gains one new member this plan (`timerHabitRepository`).
- `TimerHabitRepository`'s new `runInTransaction` constructor parameter is a default-no-op lambda — **never** make it required; every existing test file that constructs this class with its current signature must keep compiling without modification.
- `resetToday()` must call `stop()` itself, awaited, as its first internal step — never send `TimerService.ACTION_STOP` before or concurrently with `resetToday()`'s own writes. Callers send `ACTION_STOP` only after `resetToday()` (a suspend function) returns.
- TDD for all pure logic, repository, DAO, and ViewModel code (Robolectric `@Config(sdk = [35])` for anything touching Room/Android framework classes). Compose UI composables have no test precedent in this codebase (established in the prior two plans) — implemented directly, verified on-device.
- Every commit after a task leaves `./gradlew.bat :app:testDebugUnitTest` green.

---

## File Structure

```
Reminders/
  app/src/main/java/com/ziv/reminders/
    data/
      ReadingSessionLogDao.kt                                          (Modify — Task 1)
      TimerHabitRepository.kt                                          (Modify — Task 2)
      AppContainer.kt                                                  (Modify — Task 2)
    ui/dashboard/
      DashboardViewModel.kt                                            (Modify — Tasks 3, 4)
      DashboardScreen.kt                                               (Modify — Tasks 3, 4, 5)
  app/src/test/java/com/ziv/reminders/
    data/
      ReadingSessionLogDaoTest.kt                                      (Modify — Task 1)
      TimerHabitRepositoryTest.kt                                      (Modify — Task 2)
    ui/dashboard/
      TestAppContainer.kt                                              (Modify — Task 2)
      DashboardViewModelTest.kt                                        (Modify — Tasks 3, 4)
```

---

### Task 1: `ReadingSessionLogDao.deleteForDate`

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/ReadingSessionLogDao.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/ReadingSessionLogDaoTest.kt`

**Interfaces:**
- Produces: `suspend fun deleteForDate(habitInstanceId: Long, date: String)` on `ReadingSessionLogDao`. Consumed by Task 2's `TimerHabitRepository.resetToday`.

- [ ] **Step 1: Write the failing tests**

Append to `app/src/test/java/com/ziv/reminders/data/ReadingSessionLogDaoTest.kt` (inside the existing `ReadingSessionLogDaoTest` class, after `delete_removesTheRow`):

```kotlin
    @Test
    fun deleteForDate_removesAllRowsForThatInstanceAndDate() = runTest {
        val db = newDb()
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-19", startedAt = 1000L, endedAt = 1300L, durationSeconds = 300))
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-19", startedAt = 5000L, endedAt = 5500L, durationSeconds = 500))

        db.readingSessionLogDao().deleteForDate(2L, "2026-07-19")

        assertTrue(db.readingSessionLogDao().getForDate(2L, "2026-07-19").isEmpty())
        db.close()
    }

    @Test
    fun deleteForDate_doesNotAffectOtherDatesOrInstances() = runTest {
        val db = newDb()
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-18", startedAt = 1000L, endedAt = 1300L, durationSeconds = 300))
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 5L, date = "2026-07-19", startedAt = 1000L, endedAt = 1300L, durationSeconds = 300))

        db.readingSessionLogDao().deleteForDate(2L, "2026-07-19")

        assertEquals(1, db.readingSessionLogDao().getForDate(2L, "2026-07-18").size)
        assertEquals(1, db.readingSessionLogDao().getForDate(5L, "2026-07-19").size)
        db.close()
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ReadingSessionLogDaoTest"`
Expected: FAIL — `deleteForDate` doesn't exist yet (compile error).

- [ ] **Step 3: Add the DAO method**

`app/src/main/java/com/ziv/reminders/data/ReadingSessionLogDao.kt` — full file:

```kotlin
package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ReadingSessionLogDao {
    @Insert
    suspend fun insert(session: ReadingSessionLog): Long

    @Query("SELECT * FROM reading_session_log WHERE habitInstanceId = :habitInstanceId AND date = :date ORDER BY startedAt ASC")
    suspend fun getForDate(habitInstanceId: Long, date: String): List<ReadingSessionLog>

    @Delete
    suspend fun delete(session: ReadingSessionLog)

    // Feeds TimerHabitRepository.resetToday (Task 2) — deletes every session logged for a
    // given instance/date in one statement, rather than requiring the caller to fetch-then-
    // delete each row individually via the single-row delete() above.
    @Query("DELETE FROM reading_session_log WHERE habitInstanceId = :habitInstanceId AND date = :date")
    suspend fun deleteForDate(habitInstanceId: Long, date: String)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ReadingSessionLogDaoTest"`
Expected: PASS (7 tests — 5 existing + 2 new)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/ReadingSessionLogDao.kt app/src/test/java/com/ziv/reminders/data/ReadingSessionLogDaoTest.kt
git commit -m "feat: add ReadingSessionLogDao.deleteForDate for the upcoming Reading reset"
```

---

### Task 2: `TimerHabitRepository.resetToday` + transactional wiring

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/TimerHabitRepository.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/TimerHabitRepositoryTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt`

**Interfaces:**
- Consumes: `ReadingSessionLogDao.deleteForDate` (Task 1).
- Produces: `suspend fun resetToday(instance: HabitInstance, today: LocalDate)` on `TimerHabitRepository`; a new 4th constructor parameter `runInTransaction: suspend (suspend () -> Unit) -> Unit = { it() }` (default no-op, mirrors the existing nullable-default `sessionLogDao` pattern already in this class); `DashboardDataSource` gains `val timerHabitRepository: TimerHabitRepository`. Consumed by Task 3's `DashboardViewModel.onResetReadingToday`.

- [ ] **Step 1: Write the failing tests**

Add `deleteForDate` to the existing `FakeReadingSessionLogDao` in `app/src/test/java/com/ziv/reminders/data/TimerHabitRepositoryTest.kt` (it must implement the interface's new method to keep compiling):

```kotlin
private class FakeReadingSessionLogDao : ReadingSessionLogDao {
    val logged = mutableListOf<ReadingSessionLog>()
    private var nextId = 1L
    override suspend fun insert(session: ReadingSessionLog): Long {
        val withId = session.copy(id = nextId++)
        logged += withId
        return withId.id
    }
    override suspend fun getForDate(habitInstanceId: Long, date: String) =
        logged.filter { it.habitInstanceId == habitInstanceId && it.date == date }.sortedBy { it.startedAt }
    override suspend fun delete(session: ReadingSessionLog) { logged.removeAll { it.id == session.id } }
    override suspend fun deleteForDate(habitInstanceId: Long, date: String) {
        logged.removeAll { it.habitInstanceId == habitInstanceId && it.date == date }
    }
}
```

Append these tests to the `TimerHabitRepositoryTest` class, after `completedDates_returnsOnlyDatesWithCompletedTrue`:

```kotlin
    @Test
    fun resetToday_noPriorRow_createsFreshDefaultRow() = runTest {
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, FakeClock())

        repo.resetToday(instance, today)

        val row = dao.getByDate(1L, today.toString())
        assertEquals(900, row?.remainingSeconds)
        assertFalse(row?.completed == true)
        assertNull(row?.activeSessionStartedAt)
    }

    @Test
    fun resetToday_idleCompletedDay_resetsBackToFullTargetAndNotCompleted() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 900_000L // full target elapses
        repo.stop(instance, today) // marks completed

        repo.resetToday(instance, today)

        val status = repo.todayStatus(instance, today)
        assertEquals(900, status.remainingSeconds)
        assertFalse(status.completed)
    }

    @Test
    fun resetToday_activeSession_stopsItFirstThenResetsToFullTarget_notThePartialRemaining() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 60_000L // 60s elapse, session still active

        repo.resetToday(instance, today)

        val status = repo.todayStatus(instance, today)
        assertEquals(900, status.remainingSeconds) // full target, not 840 (stop()'s own partial result)
        assertFalse(status.isRunning)
        assertFalse(status.completed)
    }

    @Test
    fun resetToday_deletesAllOfTodaysLoggedSessions_includingThePriorOneAndTheActiveOneItCloses() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val sessionLogDao = FakeReadingSessionLogDao()
        val repo = TimerHabitRepository(dao, clock, sessionLogDao)
        repo.start(instance, today)
        clock.millis += 60_000L
        repo.stop(instance, today) // first completed segment, logged
        repo.start(instance, today) // second session, still active when reset happens
        clock.millis += 30_000L

        repo.resetToday(instance, today)

        assertEquals(emptyList(), repo.sessionsForDate(instance, today))
    }

    @Test
    fun resetToday_sessionLogFromADifferentDay_isNotDeleted() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val sessionLogDao = FakeReadingSessionLogDao()
        val repo = TimerHabitRepository(dao, clock, sessionLogDao)
        val yesterday = today.minusDays(1)
        repo.start(instance, yesterday)
        clock.millis += 60_000L
        repo.stop(instance, yesterday) // logged under yesterday's date

        repo.resetToday(instance, today)

        assertEquals(1, repo.sessionsForDate(instance, yesterday).size)
    }

    @Test
    fun resetToday_runsItsWritesInsideTheProvidedTransactionRunner() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        var transactionRan = false
        val repo = TimerHabitRepository(dao, clock, runInTransaction = { block -> transactionRan = true; block() })
        repo.start(instance, today)

        repo.resetToday(instance, today)

        assertTrue(transactionRan)
        assertEquals(900, dao.getByDate(1L, today.toString())?.remainingSeconds)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.TimerHabitRepositoryTest"`
Expected: FAIL — `resetToday`/`runInTransaction` don't exist yet (compile error).

- [ ] **Step 3: Add `resetToday` and the `runInTransaction` parameter**

`app/src/main/java/com/ziv/reminders/data/TimerHabitRepository.kt` — full file:

```kotlin
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

    // Feeds HabitStats' totalCount and the Activity screen's Reading heatmap, and
    // WeeklySummary's aggregation — mirrors CounterHabitRepository.completedDates.
    suspend fun completedDates(instance: HabitInstance): List<String> = dao.getCompletedDates(instance.id)

    // Feeds the Activity screen's Reading day-edit dialog.
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.TimerHabitRepositoryTest"`
Expected: PASS (22 tests — 16 existing + 6 new)

- [ ] **Step 5: Wire `AppContainer` for real transactional atomicity**

In `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`, add the import and change `timerHabitRepository`'s construction and the `DashboardDataSource` interface:

```kotlin
package com.ziv.reminders.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.scheduling.HabitScheduler

/** Manual DI — no framework needed at this app's size. One instance, owned by RemindersApp. */
class AppContainer(context: Context) : DashboardDataSource, ExerciseDetailDataSource, ActivityDataSource {
    private val appContext = context.applicationContext

    private val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "reminders.db")
            // Never fallbackToDestructiveMigration() — see Global Constraints.
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6,
            )
            .build()
    }

    override val habitInstanceDao get() = db.habitInstanceDao()
    val counterDailyProgressDao get() = db.counterDailyProgressDao()
    val timerDailyProgressDao get() = db.timerDailyProgressDao()
    val scheduleCursorProgressDao get() = db.scheduleCursorProgressDao()
    val scheduleCursorDailyProgressDao get() = db.scheduleCursorDailyProgressDao()
    val evaluatorEscalationDao get() = db.evaluatorEscalationDao()
    val exerciseSubCounterProgressDao get() = db.exerciseSubCounterProgressDao()
    val readingSessionLogDao get() = db.readingSessionLogDao()
    override val counterHabitRepository: CounterHabitRepository by lazy { CounterHabitRepository(counterDailyProgressDao) }
    override val timerHabitRepository: TimerHabitRepository by lazy {
        TimerHabitRepository(
            timerDailyProgressDao, SystemClock, readingSessionLogDao,
            runInTransaction = { block -> db.withTransaction { block() } },
        )
    }
    override val subCounterRepository: SubCounterRepository by lazy { SubCounterRepository(exerciseSubCounterProgressDao) }

    /** Falls back to an empty schedule (never throws) if the bundled asset is ever missing or
     * malformed — mirrors ReadBook's own tanakhSchedule loader; a crash here must not take down
     * the whole app. */
    val tanakhSchedule: List<ScheduleEntry> by lazy {
        try {
            val csvText = appContext.assets.open("tanakh_schedule.csv").bufferedReader().use { it.readText() }
            parseTanakhSchedule(csvText)
        } catch (e: Exception) {
            emptyList()
        }
    }
    override val scheduleCursorRepository: ScheduleCursorRepository by lazy {
        ScheduleCursorRepository(scheduleCursorProgressDao, scheduleCursorDailyProgressDao, tanakhSchedule)
    }

    override val habitEngine: HabitEngine by lazy { HabitEngine(counterHabitRepository, timerHabitRepository, scheduleCursorRepository) }
    val crossHabitEvaluator: CrossHabitEvaluator by lazy { CrossHabitEvaluator(habitInstanceDao, habitEngine, evaluatorEscalationDao) }
    val habitScheduler: HabitScheduler by lazy { HabitScheduler(appContext) }
}

interface DashboardDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val timerHabitRepository: TimerHabitRepository
    val scheduleCursorRepository: ScheduleCursorRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
}

/** Parallel to DashboardDataSource, not an extension of it — keeps DashboardDataSource
 * free of Exercise-only members. AppContainer implements both. */
interface ExerciseDetailDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
    val subCounterRepository: SubCounterRepository
}

/** Parallel to the other two, not an extension of either — the Activity screen needs
 * timerHabitRepository/scheduleCursorRepository (for Reading/Tanakh's completedDates,
 * session log, and undo) that DashboardDataSource doesn't expose, but deliberately excludes
 * subCounterRepository: the Activity screen's Exercise section reuses ExerciseViewModel
 * directly instead of duplicating that path. */
interface ActivityDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val timerHabitRepository: TimerHabitRepository
    val scheduleCursorRepository: ScheduleCursorRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
}
```

- [ ] **Step 6: Wire `TestAppContainer` the same way, so integration tests exercise the real transactional path**

`app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt` — full file:

```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.room.withTransaction
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.data.ScheduleEntry
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.engine.HabitEngine

class TestAppContainer(db: AppDatabase, schedule: List<ScheduleEntry> = emptyList()) : DashboardDataSource {
    override val habitInstanceDao = db.habitInstanceDao()
    override val counterHabitRepository = CounterHabitRepository(db.counterDailyProgressDao())
    override val timerHabitRepository = TimerHabitRepository(
        db.timerDailyProgressDao(), SystemClock, db.readingSessionLogDao(),
        runInTransaction = { block -> db.withTransaction { block() } },
    )
    override val scheduleCursorRepository = ScheduleCursorRepository(db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), schedule)
    override val habitEngine = HabitEngine(counterHabitRepository, timerHabitRepository, scheduleCursorRepository)
}
```

- [ ] **Step 7: Run tests to verify everything still passes**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green — confirms `DashboardDataSource`'s new member didn't break any other implementer, and every other pre-existing `TimerHabitRepository(dao, clock)` call site across the test suite still compiles unchanged)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/TimerHabitRepository.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/test/java/com/ziv/reminders/data/TimerHabitRepositoryTest.kt app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt
git commit -m "feat: add TimerHabitRepository.resetToday with race-safe sequencing and transactional wiring"
```

---

### Task 3: Reading "Reset today" — dashboard long-press + confirm dialog

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`
- Test: `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerHabitRepository.resetToday` (Task 2), `DashboardDataSource.timerHabitRepository` (Task 2).
- Produces: `suspend fun onResetReadingToday(instanceId: Long, context: Context)` and `suspend fun readingSessionCountToday(instanceId: Long): Int` on `DashboardViewModel`. Consumed by `DashboardScreen`'s `TimerHabitRow` in this same task. Bundled together (ViewModel + UI in one task) not because it's strictly required for compilation — `onResetReadingToday` is a new method with no pre-existing call site, so it alone wouldn't break the build — but because a ViewModel method with zero callers isn't independently reviewable or meaningfully "done"; Task 4 bundles for the stricter reason (converting `onMarkRead` to suspend genuinely cannot compile split from its call-site fix, since a suspend function cannot be called from a plain `() -> Unit` lambda).

- [ ] **Step 1: Write the failing `DashboardViewModel` tests**

Add `kotlin.test.assertFalse` to the imports in `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`, then append these tests to the `DashboardViewModelTest` class, after `onMarkRead_advancesTheCursorAndMarksTodayCompleted`:

```kotlin
    @Test
    fun onResetReadingToday_idleCompletedDay_resetsProgressBackToFullTarget() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(2L, "TIMER", "Reading", 0b1111111, "t", "b", null, timerTargetSeconds = 900)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        viewModel.onResetReadingToday(2L, context)
        testScheduler.advanceUntilIdle()

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.TimerStatus
        assertEquals(900, status.remainingSeconds)
        assertEquals(false, status.completed)

        db.close()
    }

    @Test
    fun onResetReadingToday_activeSession_stopsItFirstThenResetsToFullTarget() = runTest {
        // Starts the session directly via the repository, not via onToggleTimer — Robolectric's
        // startService() only records the Intent, it never actually invokes
        // TimerService.onStartCommand, so onToggleTimer alone would never create a real active
        // session row here. This is the only way to exercise resetToday's active-session path
        // at this (ViewModel/integration) layer; the repository layer's own equivalent test
        // (TimerHabitRepositoryTest.resetToday_activeSession_...) already covers it via fakes.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(2L, "TIMER", "Reading", 0b1111111, "t", "b", null, timerTargetSeconds = 900)
        )
        val container = TestAppContainer(db)
        val instance = db.habitInstanceDao().getById(2L)!!
        container.timerHabitRepository.start(instance, java.time.LocalDate.now())
        val viewModel = DashboardViewModel(container)
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        viewModel.onResetReadingToday(2L, context)
        testScheduler.advanceUntilIdle()

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.TimerStatus
        assertEquals(900, status.remainingSeconds)
        assertEquals(false, status.completed)
        assertEquals(false, status.isRunning)

        db.close()
    }

    @Test
    fun onResetReadingToday_sendsStopActionToTimerServiceAfterResetting() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(2L, "TIMER", "Reading", 0b1111111, "t", "b", null, timerTargetSeconds = 900)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        viewModel.onResetReadingToday(2L, context)
        testScheduler.advanceUntilIdle()

        val startedService = org.robolectric.Shadows.shadowOf(context as android.app.Application).nextStartedService
        assertEquals(com.ziv.reminders.service.TimerService.ACTION_STOP, startedService?.action)
        assertEquals(2L, startedService?.getLongExtra(com.ziv.reminders.service.TimerService.EXTRA_HABIT_INSTANCE_ID, -1L))

        db.close()
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `onResetReadingToday` doesn't exist yet (compile error).

- [ ] **Step 3: Add `onResetReadingToday` and `readingSessionCountToday` to `DashboardViewModel`**

Add these methods to `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`, after `onToggleTimer`:

```kotlin
    /** Suspend — the caller (DashboardScreen) sends TimerService.ACTION_STOP only after this
     * returns, never concurrently, so the service's own async stop-path can't race this
     * function's writes. resetToday() itself awaits TimerHabitRepository.stop() as its first
     * internal step, so any active session is already closed out (and logged) before this
     * suspend call returns — the Intent sent here only tears down the foreground notification
     * and cancels the service's ticking job, it does no DB work of its own that could clobber
     * the reset. See the "Reading Reset" design doc's corrected sequencing for why the two must
     * never fire concurrently. */
    suspend fun onResetReadingToday(instanceId: Long, context: Context) {
        val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return
        dataSource.timerHabitRepository.resetToday(instance, LocalDate.now())
        context.startService(
            Intent(context, TimerService::class.java)
                .setAction(TimerService.ACTION_STOP)
                .putExtra(TimerService.EXTRA_HABIT_INSTANCE_ID, instanceId)
        )
        refresh()
    }

    // Feeds the reset confirm dialog's preview text (added during /autoplan review — a
    // destructive, irreversible action shouldn't be confirmed blind) so the user sees how many
    // sessions they're about to lose before confirming.
    suspend fun readingSessionCountToday(instanceId: Long): Int {
        val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return 0
        return dataSource.timerHabitRepository.sessionsForDate(instance, LocalDate.now()).size
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: PASS (10 tests — 7 existing + 3 new)

- [ ] **Step 5: Wire the long-press + confirm dialog into `DashboardScreen`**

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt` — full file:

```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.HabitStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onOpenExercise: () -> Unit = {}, onOpenActivity: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Re-reads current state on every resume (first composition, backgrounding, notification
    // tap) so the dashboard never shows stale data — see Plan 1's final-review Issue 2/4.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today") },
                actions = {
                    IconButton(onClick = onOpenActivity) {
                        Icon(imageVector = Icons.Default.List, contentDescription = "Activity")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            if (!uiState.isLoaded) return@Column
            uiState.habits.forEach { habit ->
                val context = LocalContext.current
                HabitRow(
                    habit = habit,
                    onIncrement = { viewModel.onIncrement(habit.instanceId) },
                    onToggleTimer = { displayedRemainingSeconds ->
                        viewModel.onToggleTimer(habit.instanceId, context, displayedRemainingSeconds)
                    },
                    onResetReadingToday = {
                        coroutineScope.launch { viewModel.onResetReadingToday(habit.instanceId, context) }
                    },
                    fetchReadingSessionCountToday = { viewModel.readingSessionCountToday(habit.instanceId) },
                    onMarkRead = { viewModel.onMarkRead(habit.instanceId) },
                    onOpenExercise = onOpenExercise,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HabitRow(
    habit: HabitRowUiState,
    onIncrement: () -> Unit,
    onToggleTimer: (Int) -> Unit,
    onResetReadingToday: () -> Unit,
    fetchReadingSessionCountToday: suspend () -> Int,
    onMarkRead: () -> Unit,
    onOpenExercise: () -> Unit,
) {
    when (habit.status) {
        is HabitStatus.CounterStatus -> CounterHabitRow(habit, habit.status, onIncrement, onOpenExercise)
        is HabitStatus.TimerStatus -> TimerHabitRow(habit, habit.status, onToggleTimer, onResetReadingToday, fetchReadingSessionCountToday)
        is HabitStatus.ScheduleCursorStatus -> ScheduleCursorHabitRow(habit, habit.status, onMarkRead)
    }
}

// Dispatch is by instance ID, not by HabitKind — a hypothetical future second
// COUNTER-kind habit must not be silently redirected into the Exercise flow just because
// it shares HabitKind.COUNTER (see DashboardDispatchTest).
fun shouldNavigateToExerciseDetail(instanceId: Long): Boolean = instanceId == EXERCISE_HABIT_INSTANCE_ID

@Composable
private fun CounterHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.CounterStatus,
    onIncrement: () -> Unit,
    onOpenExercise: () -> Unit,
) {
    val onClick = if (shouldNavigateToExerciseDetail(habit.instanceId)) onOpenExercise else onIncrement
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
            Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = if (status.completed) "✓ ${status.current}/${status.goal}" else "${status.current}/${status.goal}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.TimerStatus,
    onToggleTimer: (Int) -> Unit,
    onResetToday: () -> Unit,
    fetchSessionCountToday: suspend () -> Int,
) {
    // Live 1Hz countdown while running — the ViewModel/DB only update on Start/Stop/Completion,
    // not every second; the visual tick lives here and resets whenever the underlying status
    // (a new baseline remainingSeconds, or isRunning flipping) actually changes. Mirrors
    // ReadBook's real HomeScreen InProgressContent mechanism.
    var displaySeconds by remember(status) { mutableIntStateOf(status.remainingSeconds) }
    LaunchedEffect(status) {
        while (status.isRunning && displaySeconds > 0) {
            delay(1000)
            displaySeconds -= 1
        }
    }
    var showResetConfirm by remember { mutableStateOf(false) }
    var sessionCountToday by remember { mutableStateOf<Int?>(null) }
    val rowScope = rememberCoroutineScope()

    Row(
        // Pass the currently-displayed (ticked-down) value, not status.remainingSeconds — the
        // ViewModel's optimistic flip uses this to avoid visually resetting to the stale
        // pre-session baseline the instant Stop is tapped. Long-press triggers the destructive
        // reset confirm dialog instead of the row's normal tap-to-toggle behavior — the session
        // count is fetched first (added during /autoplan review: a destructive, irreversible
        // action shouldn't be confirmed blind) so the dialog can show what's about to be lost.
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { onToggleTimer(displaySeconds) },
            onLongClick = {
                rowScope.launch {
                    sessionCountToday = fetchSessionCountToday()
                    showResetConfirm = true
                }
            },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
            Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
        }
        val minutes = displaySeconds / 60
        val seconds = displaySeconds % 60
        Text(
            text = if (status.completed) "✓" else "%d:%02d".format(minutes, seconds),
            style = MaterialTheme.typography.titleMedium,
        )
    }

    if (showResetConfirm) {
        val count = sessionCountToday ?: 0
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset today?") },
            text = {
                Text(
                    if (count > 0) {
                        "This deletes $count session${if (count == 1) "" else "s"} logged today and clears today's progress. This can't be undone."
                    } else {
                        "This clears today's progress. This can't be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showResetConfirm = false; onResetToday() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ScheduleCursorHabitRow(habit: HabitRowUiState, status: HabitStatus.ScheduleCursorStatus, onMarkRead: () -> Unit) {
    // Once the schedule is exhausted there's nothing left to mark read — the row stops being
    // tappable so a stray tap can't advance the cursor past the end or credit a phantom streak
    // day (see ScheduleCursorRepository.markRead's matching finished-state no-op guard).
    val rowModifier = Modifier.fillMaxWidth().let { if (!status.finished) it.clickable(onClick = onMarkRead) else it }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
            Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
        }
        val chapterText = if (status.finished) "Finished" else "${status.book} ${status.chapterHeb}"
        Text(
            text = if (status.completed) "✓ $chapterText" else chapterText,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
```

Note: `onMarkRead`'s lambda body here is still the pre-Task-4 fire-and-forget call (`viewModel.onMarkRead(habit.instanceId)`) — Task 4 converts `DashboardViewModel.onMarkRead` to a suspend function and updates this call site together with that change, since the two cannot land in separate commits without an intermediate non-compiling state (see Task 4's Interfaces note).

- [ ] **Step 6: Verify it builds and all tests still pass**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green — this step touches no logic layer beyond Step 3's `onResetReadingToday`, already covered above)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt
git commit -m "feat: add Reading dashboard long-press reset with confirm dialog"
```

---

### Task 4: Tanakh quick-undo — dashboard snackbar

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`
- Test: `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `ScheduleCursorRepository.undoMarkRead` (already exists, shipped by the prior plan).
- Produces: `DashboardViewModel.onMarkRead` converted from a fire-and-forget `fun` into a plain `suspend fun` (same signature otherwise); new `suspend fun onUndoMarkRead(instanceId: Long)`. Both are consumed by `DashboardScreen`'s Tanakh row callback in this same task — bundled together because converting `onMarkRead`'s signature and updating its one call site cannot land in separate commits without an intermediate non-compiling state (a suspend function cannot be called from a non-suspend lambda).

- [ ] **Step 1: Write the failing `DashboardViewModel` test**

Append this test to the `DashboardViewModelTest` class, after `onResetReadingToday_sendsStopActionToTimerServiceAfterResetting` (the last test added in Task 3):

```kotlin
    @Test
    fun onUndoMarkRead_reversesTheMostRecentMarkRead() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(3L, "SCHEDULE_CURSOR", "Tanakh", 0b1111111, "t", "b", null)
        )
        val schedule = listOf(
            com.ziv.reminders.data.ScheduleEntry("א", "א׳", java.time.LocalDate.now()),
        )
        val viewModel = DashboardViewModel(TestAppContainer(db, schedule))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()
        viewModel.onMarkRead(3L)
        testScheduler.advanceUntilIdle()

        viewModel.onUndoMarkRead(3L)
        testScheduler.advanceUntilIdle()

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.ScheduleCursorStatus
        assertFalse(status.completed)

        db.close()
    }
```

(The existing `onMarkRead_advancesTheCursorAndMarksTodayCompleted` test's call site, `viewModel.onMarkRead(3L)`, keeps compiling unchanged after Step 2 below — calling a suspend function directly from within a `runTest` body is syntactically identical to calling a non-suspend one.)

- [ ] **Step 2: Run tests to verify the new one fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `onUndoMarkRead` doesn't exist yet (compile error).

- [ ] **Step 3: Convert `onMarkRead` to suspend and add `onUndoMarkRead`**

In `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`, replace the existing `onMarkRead` function:

```kotlin
    fun onMarkRead(instanceId: Long) {
        viewModelScope.launch {
            val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return@launch
            dataSource.scheduleCursorRepository.markRead(instance, LocalDate.now())
            refresh()
        }
    }
```

with:

```kotlin
    /** Suspend, not fire-and-forget on viewModelScope (mirrors the fix applied to
     * ExerciseViewModel.adjustSubCounterForDate in the prior plan) — DashboardScreen's Tanakh
     * row needs to await this completing before showing the quick-undo snackbar; a
     * fire-and-forget launch gives the caller no signal that the write has happened. */
    suspend fun onMarkRead(instanceId: Long) {
        val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return
        dataSource.scheduleCursorRepository.markRead(instance, LocalDate.now())
        refresh()
    }

    suspend fun onUndoMarkRead(instanceId: Long) {
        val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return
        dataSource.scheduleCursorRepository.undoMarkRead(instance, LocalDate.now())
        refresh()
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: PASS (11 tests — 10 from Task 3 + 1 new). This will still fail to *compile* at this point because `DashboardScreen.kt`'s `onMarkRead = { viewModel.onMarkRead(habit.instanceId) }` call site now references a suspend function from a non-suspend lambda — proceed to Step 5 before re-running.

- [ ] **Step 5: Wire the snackbar into `DashboardScreen`**

In `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`:

Add these imports:

```kotlin
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
```

Add a `SnackbarHostState` and wire it into `Scaffold`, and change the Tanakh row's `onMarkRead` callback — the top of `DashboardScreen` becomes:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onOpenExercise: () -> Unit = {}, onOpenActivity: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Re-reads current state on every resume (first composition, backgrounding, notification
    // tap) so the dashboard never shows stale data — see Plan 1's final-review Issue 2/4.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today") },
                actions = {
                    IconButton(onClick = onOpenActivity) {
                        Icon(imageVector = Icons.Default.List, contentDescription = "Activity")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
            if (!uiState.isLoaded) return@Column
            uiState.habits.forEach { habit ->
                val context = LocalContext.current
                HabitRow(
                    habit = habit,
                    onIncrement = { viewModel.onIncrement(habit.instanceId) },
                    onToggleTimer = { displayedRemainingSeconds ->
                        viewModel.onToggleTimer(habit.instanceId, context, displayedRemainingSeconds)
                    },
                    onResetReadingToday = {
                        coroutineScope.launch { viewModel.onResetReadingToday(habit.instanceId, context) }
                    },
                    fetchReadingSessionCountToday = { viewModel.readingSessionCountToday(habit.instanceId) },
                    onMarkRead = {
                        coroutineScope.launch {
                            viewModel.onMarkRead(habit.instanceId)
                            val result = snackbarHostState.showSnackbar(
                                message = "Marked as read",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.onUndoMarkRead(habit.instanceId)
                            }
                        }
                    },
                    onOpenExercise = onOpenExercise,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
```

The rest of the file (`HabitRow`, `CounterHabitRow`, `TimerHabitRow`, `ScheduleCursorHabitRow`) is unchanged from Task 3.

- [ ] **Step 6: Run tests to verify everything passes**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: PASS (11 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt
git commit -m "feat: add Tanakh quick-undo snackbar on the dashboard"
```

---

### Task 5: Tanakh "N behind" indicator

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`

**Interfaces:**
- Consumes: `HabitStatus.ScheduleCursorStatus.dueCount` (already exists, computed by `ScheduleCursorRepository.todayStatus`).

- [ ] **Step 1: Extend the row's text**

In `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`, replace `ScheduleCursorHabitRow`'s body:

```kotlin
@Composable
private fun ScheduleCursorHabitRow(habit: HabitRowUiState, status: HabitStatus.ScheduleCursorStatus, onMarkRead: () -> Unit) {
    // Once the schedule is exhausted there's nothing left to mark read — the row stops being
    // tappable so a stray tap can't advance the cursor past the end or credit a phantom streak
    // day (see ScheduleCursorRepository.markRead's matching finished-state no-op guard).
    val rowModifier = Modifier.fillMaxWidth().let { if (!status.finished) it.clickable(onClick = onMarkRead) else it }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
            Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
        }
        // dueCount is only ever nonzero when status is Behind (see ScheduleCursorRepository's
        // deriveScheduleEntryStatus branches) — OnSchedule/Waiting/Finished always carry 0.
        Column(horizontalAlignment = Alignment.End) {
            val chapterText = if (status.finished) "Finished" else "${status.book} ${status.chapterHeb}"
            Text(
                text = if (status.completed) "✓ $chapterText" else chapterText,
                style = MaterialTheme.typography.titleMedium,
            )
            // On its own line, styled distinctly (not interpolated into the line above) — found
            // during /autoplan design review: completed and dueCount>0 aren't mutually
            // exclusive (today's entry can be done while still behind on the overall schedule),
            // so a single shared string like "✓ ... · 3 behind" read as self-contradictory.
            if (status.dueCount > 0) {
                Text(
                    text = "${status.dueCount} behind",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify it builds and all tests still pass**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green — this task touches only display text, no logic layer)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt
git commit -m "feat: show chapters-behind count on Tanakh's dashboard row"
```

---

## Final Verification

After all 5 tasks:

```bash
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug
```

Both must succeed. Then on-device verification (per this session's established convention — Compose UI has no test coverage, so this is the real check):
1. Long-press the Reading row → confirm dialog appears, showing a session-count preview when sessions exist today → Reset → row shows full target, not completed.
2. Start a Reading session, let a few seconds elapse, long-press → dialog shows "1 session" → Reset → confirm the foreground notification disappears and the row shows full target (not the partial elapsed time).
3. Mark Tanakh read from the dashboard → snackbar appears with "Undo" → tap it → cursor/streak reverts.
4. Get behind on Tanakh's schedule (or seed test data) → dashboard row shows a red "N behind" line beneath the chapter reference. Mark today read while still behind → confirm the "✓ chapter" line and the "N behind" line both render, clearly separated, not as one contradictory sentence.

<!-- AUTONOMOUS DECISION LOG -->
## Decision Audit Trail

| # | Phase | Decision | Classification | Principle | Rationale | Rejected |
|---|-------|----------|-----------------|-----------|-----------|----------|
| 1 | CEO | Add a dynamic session-count/duration preview to the Reading reset confirm dialog | Mechanical (converged: both CEO and Design review flagged it independently) | P2 (boil lakes) | A destructive, irreversible action was being confirmed with no visibility into what's lost; `TimerHabitRepository.sessionsForDate` already exists to compute the count cheaply | Leaving the dialog static (original plan draft) |
| 2 | CEO | Defer reconsidering "full reset (destructive) vs. soft reset (non-destructive)" to the user, not auto-decided | User Challenge (single-voice — Codex unavailable, so not "both models agree," but revisits an explicit prior user decision) | N/A — explicitly not auto-decided per policy | The user already chose full reset over a lighter option during `/office-hours`, against my own recommendation at the time; a new consideration (inconsistency with Tanakh's reversible undo in the same plan) surfaced during this review and deserves to be put back in front of the user, not silently overridden | Auto-approving either direction myself |
| 3 | Design | Split the Tanakh row's "✓ chapter" and "N behind" onto two visually distinct lines (bodySmall, error color) instead of one interpolated string | Mechanical | P5 (explicit over clever) | `completed` and `dueCount > 0` aren't mutually exclusive; a single shared string reads as self-contradictory ("✓ ... behind") when both are true on the same day | Leaving them in one string (original plan draft) |
| 4 | Design | Style the reset dialog's "Reset" confirm button with `MaterialTheme.colorScheme.error` | Mechanical | P5 (explicit over clever) | Standard Material guidance for destructive confirm actions; was visually identical to "Cancel" in the original draft | Leaving both buttons unstyled |
| 5 | Eng | Fix `onResetReadingToday_activeSession_...` test to start the session directly via `TestAppContainer.timerHabitRepository.start(...)` instead of via `onToggleTimer` | Mechanical (real bug, not a taste call) | P1 (completeness) | Robolectric's `startService()` only records the Intent — it never runs `TimerService.onStartCommand` — so the original test never actually created an active session row and silently degraded into a duplicate of the idle-day case | Leaving the misleading test as-is |
| 6 | Eng | Reword Task 3's bundling rationale from "cannot compile if split" to the accurate reason (reviewability, not a hard compile error) | Mechanical | P5 (explicit over clever) | `onResetReadingToday` is a new method with no pre-existing call site — Task 3 alone wouldn't actually break the build if split, unlike Task 4's `onMarkRead` conversion, which genuinely would | Leaving the imprecise claim uncorrected |
| 7 | Eng | Document the pre-existing `stop()`/`finishSession()` check-then-act race as an accepted, out-of-scope limitation; no code change | Taste (documented, not silently dropped) | P3 (pragmatic) | Pre-existing in the codebase, not introduced by this plan; adding locking for a single-user personal app's narrow race window isn't worth the complexity | Adding a lock/mutex around `finishSession()` |
| 8 | Eng | No test added for `runInTransaction`'s rollback behavior | Taste (documented, not silently dropped) | P5 (explicit over clever) | Would test Room's own `withTransaction` implementation, not application code — our own lambda is a one-line passthrough to a well-tested library function | Writing a rollback test against a fake that can't actually roll back anything |

## GSTACK REVIEW REPORT

**Phases run:** CEO (Phase 1), Design (Phase 2 — UI scope detected: Compose dialogs, snackbar, long-press), Eng (Phase 3). DX (Phase 3.5) skipped — no developer-facing scope.

**Dual voices:** Codex CLI unavailable in this environment (not installed) — all three phases ran as `[subagent-only]` (one independent Claude subagent per phase, no prior-phase context, dispatched fresh). No Codex-vs-Claude disagreements were possible to surface as a result; all findings below are single-voice, evaluated and auto-decided (or escalated) by the orchestrating session directly.

**CEO findings:** 1 mechanical fix (dialog preview, decision #1), 1 item escalated as a User Challenge (decision #2 — full reset vs. soft reset), rest of the plan's scope/premises confirmed sound for a personal-project "boil the lake" pass (near-total reuse of existing repository/DAO/ViewModel code, no premature abstraction found).

**Design findings:** 2 mechanical fixes (decisions #3, #4). Long-press discoverability and copy specificity both confirmed acceptable as originally drafted — no changes needed there.

**Eng findings:** 1 real test bug found and fixed (decision #5), 1 documentation-accuracy fix (decision #6), 2 items explicitly accepted and documented rather than fixed (decisions #7, #8). Race-safety design (the `resetToday()` sequencing) confirmed sound against the actual repository/service code — no gaps beyond the already-documented, already-accepted narrow edge case.

**Outcome:** 6 of 8 findings auto-fixed directly in this plan. 1 documented as an accepted limitation. 1 (the full-reset-vs-soft-reset question) was put to the user directly.

**Final Approval Gate decision:** Approved as-is — Reading's reset stays a full, irreversible delete (no snackbar undo). Rationale, confirmed by the user: Reading's reset is already double-gated (long-press + confirm dialog) against accidental triggers, unlike Tanakh's single unguarded tap, which is why Tanakh needs a cheap undo and Reading doesn't. The asymmetry between the two features in this same plan is deliberate, not an oversight.

**PLAN STATUS: APPROVED — ready for subagent-driven-development.**
