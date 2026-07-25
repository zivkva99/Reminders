<!-- /autoplan restore point: C:\Users\zivk\.gstack\projects\Reminders\main-autoplan-restore-20260725-202031.md -->
# C++ Weekly Reminder Row Implementation Plan

**Status: APPROVED** — reviewed via `/autoplan` on 2026-07-25 (CEO + Design + Eng phases, Claude
subagent voices only, Codex unavailable). Scope revised mid-review to add long-press "Statistics"
(see "Scope Revision" section below). 4 taste decisions resolved at the Final Approval Gate (see
that section, near the end). 8 tasks, ready for `subagent-driven-development` / `executing-plans`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fourth dashboard row — "C++ Weekly" — that reminds the user which episode of the
C++ Weekly YouTube series to watch next. Unlike Counter/Timer/Schedule-cursor, this row's "due"
state is driven by pure date arithmetic against a fixed weekly release cadence, not by any
persisted per-day or per-entry table: one anchor (a known episode number and the date it
released) plus a 7-day interval makes every future episode's release date fully computable.

**Architecture:** A new `HabitKind.COMPUTED_SCHEDULE` case. The anchor configuration
(`anchorItemNumber`, `anchorDate`, `intervalDays`) lives as three new nullable columns directly on
`HabitInstance` — the same "kind vs. instance" split `counterGoal`/`timerTargetSeconds` already
use, since these are single per-instance scalars, not a set of rows. The one thing that actually
*changes* over time — the running position, `nextItemNumber` — is its own single-row-per-instance
table, `ComputedScheduleProgress`, directly analogous to `ScheduleCursorProgress.cursorIndex`. A
pure function, `deriveComputedScheduleStatus`, derives `HabitStatus.ComputedScheduleStatus`
(`nextItemNumber`, `dueCount`, `isDueToday`) from `(nextItemNumber, anchorItemNumber, anchorDate,
intervalDays, today)` — no schedule table, no Room dependency, fully testable in isolation. Like
Schedule-cursor, there is also a per-watch-event log table, `computed_schedule_watch_log` — an
append-only log of discrete watch events (one row per tap), mirroring `ScheduleCursorDailyProgress`'s
per-date role but shaped like `ReadingSessionLog`'s autoincrement-id per-event rows, since each
watch is a discrete event, not a daily aggregate. **This log, its DAO, `currentStreak()`'s real
computation, and the long-press stats screen were all added mid-review — see the "Scope Revision
(mid-review)" section right after the CEO Phase 1 header below for why; the rest of this
Architecture paragraph and the Global Constraints below it were written before that revision and
are corrected inline where they'd otherwise mislead.** `currentStreak()` now derives a real streak
from that log via `HabitStats` (the same kind-agnostic `Set<LocalDate>` calculator Exercise/
Reading/Tanakh's stats screens already use), and long-press opens a dedicated stats screen
(heatmap + streak), exactly matching those three kinds' precedent. The dashboard row itself is
unchanged by this: it still shows the episode number in the row's "streak slot" instead of a day
count — only the long-press/stats/streak-math layer underneath gained real behavior.
`HabitEngine`, `AppContainer`, `HabitSeeding`, and every existing
exhaustive `when` over `HabitKind`/`HabitStatus` gain a fourth branch — enumerated file-by-file in
Tasks 2-6 below, not left as a vague "grep for it" note. No foreground service, no new
notification channel beyond the one every `HabitInstance` already gets generically, no new
manifest components — `HabitScheduler`, `HabitReminderReceiver`, `BootReceiver`, and
`RolloverReceiver` are already fully generalized by `habitInstanceId` and need zero changes to
pick up the new instance.

**Tech Stack:** Same as every prior plan in this repo — Kotlin 2.3.0, Jetpack Compose (Material
3), Room 2.7.1 (KSP), JUnit4 + Robolectric 4.16.1, `kotlinx-coroutines-test`. No new dependencies.

## Global Constraints

- Package / application ID: `com.ziv.reminders`. `minSdk = 35`, `targetSdk = 36`.
- The current schema is **version 6** (`AppDatabase.kt`, entities through `ReadingSessionLog`).
  This plan's migration is **v6 → v7**. Every Room schema change ships with a real `Migration`
  object; never `fallbackToDestructiveMigration()`.
- Single row, single purpose (per the design doc's Premise 1 — Approach B, chosen): this is a
  **C++ Weekly-specific** dashboard row, not a generic "track any weekly release" framework. A
  hypothetical future second weekly-release row would be just another seeded `HabitInstance` of
  this same kind — zero new code — which is exactly why the anchor is data (instance columns), not
  a Kotlin constant.
- No in-app "add habit" UI — the C++ Weekly instance is inserted via `ensureHabitsSeeded`, same
  mechanism as every other row.
- **Long-press "Statistics" + a full per-watch-date log, matching Exercise/Reading/Tanakh exactly**
  — this supersedes the plan's original "no stats screen, no long-press" scope (see the
  "## Scope Revision (mid-review)" section right after the CEO Phase 1 header for the full
  explanation of why and what changed). `ComputedScheduleHabitRow` gets the same
  `RowLongPressMenu`/"Statistics" entry every other row has (Task 5), backed by a real per-watch-
  event log table, `computed_schedule_watch_log` (Task 1), a real `currentStreak()` (Task 3), and
  a dedicated `ComputedScheduleStatsScreen` (Task 6) with a heatmap, exactly like the other three
  kinds' stats screens.
- Do not touch `ScheduleEntry.kt`, `ScheduleCursorRepository.kt`, `tanakh_schedule.csv`, or any
  other Tanakh-specific code. Do not touch `WeeklySummary`/`CrossHabitEvaluator` — both are
  hardcoded to Exercise/Reading/Tanakh's specific instance IDs and daily-progress tables, and this
  kind has no daily-progress table to report into; that hardcoding is out of scope for this plan.
- TDD for all pure logic, DAO, and repository/dispatch code (Robolectric, `@Config(sdk = [35])`,
  for anything touching Room). This codebase's Compose UI composables have **no unit-test
  precedent** — Task 5's dashboard row is implement-directly, verified via `assembleDebug` and the
  existing suite staying green, plus on-device verification in Task 8, same as the icon and
  per-row-stats plans before it.
- Every commit after a task leaves `./gradlew.bat :app:testDebugUnitTest` green.
- Build/test commands: `./gradlew.bat :app:assembleDebug` and `./gradlew.bat
  :app:testDebugUnitTest` (repo root, PowerShell — this environment's Bash tool has no
  git/gradle in PATH).
- **`anchorDate`'s actual value is an open question the design doc explicitly refuses to guess**
  (episode 542's real release date). Task 7 (formerly Task 6 — see the Scope Revision below the
  CEO Phase 1 header) seeds it as `TODO("...")`, a Kotlin intrinsic that
  type-checks fine (its type is `Nothing`) but throws `NotImplementedError` — an `Error`, not an
  `Exception` — the instant that codepath runs. `RemindersApp.onCreate`'s self-heal `catch
  (e: Exception)` will **not** catch this, so the app will hard-crash on first launch until a
  human replaces the placeholder with the real date. This is intentional: a silently-wrong
  guessed date would be worse than a loud, unmissable crash. Task 8 (formerly Task 7) calls this
  out again before the on-device install step.

---

## File Structure

```
Reminders/
  app/src/main/res/drawable/
    ic_habit_cppweekly.xml                  (Create — Task 5)
  app/src/main/java/com/ziv/reminders/
    data/
      HabitKind.kt                          (Modify — Task 1)
      HabitInstance.kt                      (Modify — Task 1)
      ComputedScheduleProgress.kt           (Create — Task 1)
      ComputedScheduleProgressDao.kt        (Create — Task 1)
      ComputedScheduleWatchLog.kt           (Create — Task 1)
      ComputedScheduleWatchLogDao.kt        (Create — Task 1)
      AppDatabase.kt                        (Modify — Task 1)
      AppContainer.kt                       (Modify — Tasks 1, 4)
      HabitStatus.kt                        (Modify — Task 2)
      ComputedSchedule.kt                   (Create — Task 2)
      ComputedScheduleRepository.kt         (Create — Task 3)
      HabitSeeding.kt                       (Modify — Task 7)
    engine/
      HabitEngine.kt                        (Modify — Task 4)
    scheduling/
      HabitReminderReceiver.kt              (Modify — Task 3)
    ui/dashboard/
      DashboardViewModel.kt                 (Modify — Task 5)
      DashboardScreen.kt                    (Modify — Task 5)
      ComputedScheduleStatsViewModel.kt      (Create — Task 6)
      ComputedScheduleStatsScreen.kt         (Create — Task 6)
    MainActivity.kt                          (Modify — Task 6)
    RemindersApp.kt                         (Modify — Task 7)
  app/src/test/java/com/ziv/reminders/
    data/
      ComputedScheduleProgressDaoTest.kt    (Create — Task 1)
      ComputedScheduleWatchLogDaoTest.kt    (Create — Task 1)
      AppDatabaseMigration6To7Test.kt       (Create — Task 1)
      ComputedScheduleTest.kt               (Create — Task 2)
      ComputedScheduleRepositoryTest.kt     (Create — Task 3)
    engine/
      HabitEngineTest.kt                    (Modify — Task 4)
    scheduling/
      HabitReminderReceiverTest.kt          (Modify — Task 4)
    ui/dashboard/
      DashboardViewModelTest.kt             (Modify — Task 5)
      ComputedScheduleStatsViewModelTest.kt  (Create — Task 6)
      TestAppContainer.kt                   (Modify — Task 4)
```

(Task count is now **8**, not 7: Task 6 — "Stats screen: long-press → heatmap" — is newly
inserted per the Scope Revision below; the former Task 6 "Seed the C++ Weekly habit instance" is
now Task 7, and the former Task 7 "On-device manual verification" is now Task 8.)

---

### Task 1: Room schema v7 — `HabitKind.COMPUTED_SCHEDULE`, `HabitInstance` anchor columns, `ComputedScheduleProgress`, `ComputedScheduleWatchLog`, migration

**Note (Scope Revision — see the section under that name right after the CEO Phase 1 header):**
this task now also creates `computed_schedule_watch_log`, a per-watch-event log table, folded into
the *same* `MIGRATION_6_7` rather than a new `MIGRATION_7_8` — this plan is still one unimplemented
unit of work, so there is no shipped v7 to be backward-compatible with yet.

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitKind.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitInstance.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/ComputedScheduleProgress.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/ComputedScheduleProgressDao.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/ComputedScheduleWatchLog.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/ComputedScheduleWatchLogDao.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/ComputedScheduleProgressDaoTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/ComputedScheduleWatchLogDaoTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration6To7Test.kt`

**Interfaces:**
- Produces: `HabitKind.COMPUTED_SCHEDULE`; `HabitInstance.anchorItemNumber: Int?`,
  `HabitInstance.anchorDate: String?`, `HabitInstance.intervalDays: Int?`; `data class
  ComputedScheduleProgress(habitInstanceId: Long, nextItemNumber: Int)`; `interface
  ComputedScheduleProgressDao { suspend fun getByInstance(...): ComputedScheduleProgress?;
  suspend fun insertIfAbsent(...); suspend fun upsert(...) }`; `data class
  ComputedScheduleWatchLog(id: Long = 0, habitInstanceId: Long, date: String, episodeNumber: Int)`;
  `interface ComputedScheduleWatchLogDao { suspend fun insert(entry: ComputedScheduleWatchLog):
  Long; suspend fun getWatchedDates(habitInstanceId: Long): List<String> }`; `AppDatabase.MIGRATION_6_7`.
  Consumed by: `ComputedScheduleRepository` (Task 3), `HabitSeeding.kt` (Task 7).

- [ ] **Step 1: Write the failing DAO tests**

`app/src/test/java/com/ziv/reminders/data/ComputedScheduleProgressDaoTest.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComputedScheduleProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByInstance_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.computedScheduleProgressDao().getByInstance(4L))
        db.close()
    }

    @Test
    fun insertIfAbsent_thenGetByInstance_returnsTheSeededRow() = runTest {
        val db = newDb()
        db.computedScheduleProgressDao().insertIfAbsent(ComputedScheduleProgress(habitInstanceId = 4L, nextItemNumber = 543))

        assertEquals(ComputedScheduleProgress(4L, 543), db.computedScheduleProgressDao().getByInstance(4L))
        db.close()
    }

    @Test
    fun insertIfAbsent_rowAlreadyExists_leavesItUntouched() = runTest {
        val db = newDb()
        db.computedScheduleProgressDao().insertIfAbsent(ComputedScheduleProgress(4L, nextItemNumber = 543))
        db.computedScheduleProgressDao().upsert(ComputedScheduleProgress(4L, nextItemNumber = 550)) // simulate several taps

        db.computedScheduleProgressDao().insertIfAbsent(ComputedScheduleProgress(4L, nextItemNumber = 543)) // re-seed attempt

        assertEquals(550, db.computedScheduleProgressDao().getByInstance(4L)?.nextItemNumber)
        db.close()
    }

    @Test
    fun upsert_sameInstance_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.computedScheduleProgressDao().upsert(ComputedScheduleProgress(4L, 543))
        db.computedScheduleProgressDao().upsert(ComputedScheduleProgress(4L, 544))

        assertEquals(544, db.computedScheduleProgressDao().getByInstance(4L)?.nextItemNumber)
        db.close()
    }
}
```

`app/src/test/java/com/ziv/reminders/data/ComputedScheduleWatchLogDaoTest.kt` (added per the Scope
Revision — mirrors `ComputedScheduleProgressDaoTest.kt`'s structure but exercises an append-only
log instead of a single-row-per-instance table, matching `ReadingSessionLogDao`'s own test
precedent: multiple inserts for the same instance are all kept, and `getWatchedDates` returns
distinct dates for `HabitStats` to consume — a day with 2 watch events must not be double-counted
as "2 streak days"):
```kotlin
package com.ziv.reminders.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComputedScheduleWatchLogDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getWatchedDates_noRows_returnsEmpty() = runTest {
        val db = newDb()
        assertEquals(emptyList(), db.computedScheduleWatchLogDao().getWatchedDates(4L))
        db.close()
    }

    @Test
    fun insert_thenGetWatchedDates_returnsTheDate() = runTest {
        val db = newDb()
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 4L, date = "2026-07-21", episodeNumber = 543))

        assertEquals(listOf("2026-07-21"), db.computedScheduleWatchLogDao().getWatchedDates(4L))
        db.close()
    }

    @Test
    fun insert_twoEventsSameDay_getWatchedDates_returnsDateOnlyOnce() = runTest {
        // Same reasoning as ReadingSessionLog: a session log has no natural composite business
        // key (multiple watch events CAN share a date — e.g. catching up 2 backlog episodes in
        // one sitting), so this is an autoincrement-id append-only log, not a per-date upsert
        // table. getWatchedDates must still de-duplicate to one distinct date, or HabitStats'
        // streak math (which operates over a Set<LocalDate>, one entry per calendar day) would
        // silently be fed a date twice — harmless for a Set, but the DISTINCT belongs at the SQL
        // layer so every future caller of this DAO gets the same de-duplicated contract, not just
        // whichever caller happens to funnel the list through a Set first.
        val db = newDb()
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 4L, date = "2026-08-04", episodeNumber = 544))
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 4L, date = "2026-08-04", episodeNumber = 545))

        assertEquals(listOf("2026-08-04"), db.computedScheduleWatchLogDao().getWatchedDates(4L))
        db.close()
    }

    @Test
    fun getWatchedDates_onlyReturnsRowsForTheRequestedInstance() = runTest {
        val db = newDb()
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 4L, date = "2026-07-21", episodeNumber = 543))
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 99L, date = "2026-07-22", episodeNumber = 1))

        assertEquals(listOf("2026-07-21"), db.computedScheduleWatchLogDao().getWatchedDates(4L))
        db.close()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ComputedScheduleProgressDaoTest"`
Expected: FAIL — `ComputedScheduleProgress`, `ComputedScheduleProgressDao`, and
`AppDatabase.computedScheduleProgressDao()` don't exist yet (compile error).

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ComputedScheduleWatchLogDaoTest"`
Expected: FAIL — `ComputedScheduleWatchLog`, `ComputedScheduleWatchLogDao`, and
`AppDatabase.computedScheduleWatchLogDao()` don't exist yet (compile error).

- [ ] **Step 3: Write the schema implementation**

`app/src/main/java/com/ziv/reminders/data/HabitKind.kt` (full file):
```kotlin
package com.ziv.reminders.data

/**
 * The extensibility primitive: adding a new instance of an existing kind needs only a
 * HabitInstance row (see HabitSeeding.kt), zero new Kotlin classes. A genuinely new kind still
 * needs a new enum case, HabitStatus variant, repository, and HabitEngine branch.
 */
enum class HabitKind {
    COUNTER,
    TIMER,
    SCHEDULE_CURSOR,
    COMPUTED_SCHEDULE,
}
```

`app/src/main/java/com/ziv/reminders/data/HabitInstance.kt` (full file):
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * kind is stored as a plain String (HabitKind.name), not a Room-mapped enum column — so a
 * future kind's migration only needs a data INSERT, never a schema change to this column.
 * counterGoal/timerTargetSeconds/anchorItemNumber/anchorDate/intervalDays are nullable per-kind
 * config columns; each new kind adds its own nullable trailing column the same way (a defaulted
 * trailing param, so every existing positional HabitInstance(...) call site keeps compiling
 * unmodified).
 *
 * anchorItemNumber/anchorDate/intervalDays are ComputedSchedule's per-instance anchor
 * configuration (e.g. "episode 542 released on this date, every 7 days thereafter") — fixed at
 * seed time, distinct from ComputedScheduleProgress.nextItemNumber (the one thing that actually
 * changes as the user taps through episodes). anchorDate is stored as ISO-8601 text
 * ("yyyy-MM-dd", LocalDate.toString()'s default format), matching every other date-as-TEXT
 * column in this codebase (e.g. CounterDailyProgress.date).
 */
@Entity(tableName = "habit_instance")
data class HabitInstance(
    @PrimaryKey val id: Long,
    val kind: String,
    val name: String,
    val enabledDaysMask: Int,
    val notificationTitle: String,
    val notificationBody: String,
    val counterGoal: Int?,
    val timerTargetSeconds: Int? = null,
    val anchorItemNumber: Int? = null,
    val anchorDate: String? = null,
    val intervalDays: Int? = null,
)
```

`app/src/main/java/com/ziv/reminders/data/ComputedScheduleProgress.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single row per habit instance — the mutable running position, analogous to
 * ScheduleCursorProgress.cursorIndex. Distinct from the instance's own anchorItemNumber/
 * anchorDate/intervalDays columns on HabitInstance (fixed config, set once at seed time) —
 * nextItemNumber is the only thing that changes as the user taps through episodes.
 */
@Entity(tableName = "computed_schedule_progress")
data class ComputedScheduleProgress(
    @PrimaryKey val habitInstanceId: Long,
    val nextItemNumber: Int,
)
```

`app/src/main/java/com/ziv/reminders/data/ComputedScheduleProgressDao.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ComputedScheduleProgressDao {
    @Query("SELECT * FROM computed_schedule_progress WHERE habitInstanceId = :habitInstanceId")
    suspend fun getByInstance(habitInstanceId: Long): ComputedScheduleProgress?

    // IGNORE on conflict — used only at seed time (HabitSeeding.ensureHabitsSeeded) to write the
    // starting position exactly once. Unlike ScheduleCursorProgress (which safely defaults to
    // cursorIndex 0 when no row exists yet), this kind's starting nextItemNumber is real business
    // data (episode 543, not a universal default like 0), so it must be persisted explicitly and
    // must never be silently overwritten by a later app-startup reseed — hence a separate
    // insertIfAbsent from the mutable upsert below, mirroring HabitInstanceDao's own
    // insertIfAbsent/no-update-path precedent.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(progress: ComputedScheduleProgress)

    @Upsert
    suspend fun upsert(progress: ComputedScheduleProgress)
}
```

`app/src/main/java/com/ziv/reminders/data/ComputedScheduleWatchLog.kt` (added per the Scope
Revision — see that section below the CEO Phase 1 header):
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per watch event (one tap of "mark next watched") — the full per-episode watch-date log
 * this kind originally deferred (see the Scope Revision section below the CEO Phase 1 header for
 * why it's now in scope). Mirrors ScheduleCursorDailyProgress's per-date *role* (it's the streak
 * signal HabitStats.currentStreak consumes) but ReadingSessionLog's *shape* (autoincrementing
 * surrogate id, append-only), not a per-(habitInstanceId, date) upsert row: a single calendar day
 * can contain more than one watch event (catching up 2 backlog episodes in one sitting advances
 * nextItemNumber by 1 per tap — see ComputedScheduleRepository.markNextWatched — so 2 taps in one
 * day is a real, expected case, not an edge case to collapse away). Distinct from
 * ComputedScheduleProgress.nextItemNumber (the running position) — this table is purely an
 * append-only history for streak/heatmap purposes and is never read to derive `dueCount`/
 * `isDueToday`/`nextItemNumber` itself.
 *
 * Indexed on (habitInstanceId, date), same reasoning as ReadingSessionLog's own index: the only
 * real query this table serves (getWatchedDates) filters on habitInstanceId; the date column
 * being part of the same index costs nothing and keeps the shape consistent with
 * ReadingSessionLog's precedent. Declared here AND created by the matching SQL in MIGRATION_6_7 —
 * Room validates the two against each other at build time.
 */
@Entity(tableName = "computed_schedule_watch_log", indices = [Index(value = ["habitInstanceId", "date"])])
data class ComputedScheduleWatchLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitInstanceId: Long,
    val date: String,
    val episodeNumber: Int,
)
```

`app/src/main/java/com/ziv/reminders/data/ComputedScheduleWatchLogDao.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ComputedScheduleWatchLogDao {
    @Insert
    suspend fun insert(entry: ComputedScheduleWatchLog): Long

    // DISTINCT at the SQL layer, not left to the caller — see ComputedScheduleWatchLogDaoTest's
    // insert_twoEventsSameDay test for why: HabitStats.currentStreak/parseDates operate over a
    // Set<LocalDate> (one entry per calendar day), so every caller of this DAO must get the same
    // de-duplicated contract, matching ScheduleCursorDailyProgressDao.getCompletedDates and
    // CounterDailyProgressDao.getCompletedDates's own one-row-per-day shape (those tables enforce
    // it via a composite primary key instead, since they're upsert tables, not an append-only
    // log — DISTINCT is this table's equivalent guarantee for an append-only shape).
    @Query("SELECT DISTINCT date FROM computed_schedule_watch_log WHERE habitInstanceId = :habitInstanceId")
    suspend fun getWatchedDates(habitInstanceId: Long): List<String>
}
```

`app/src/main/java/com/ziv/reminders/data/AppDatabase.kt` (full file):
```kotlin
package com.ziv.reminders.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HabitInstance::class, CounterDailyProgress::class, TimerDailyProgress::class,
        ScheduleCursorProgress::class, ScheduleCursorDailyProgress::class,
        EvaluatorEscalation::class, ExerciseSubCounterProgress::class, ReadingSessionLog::class,
        ComputedScheduleProgress::class, ComputedScheduleWatchLog::class,
    ],
    version = 7,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitInstanceDao(): HabitInstanceDao
    abstract fun counterDailyProgressDao(): CounterDailyProgressDao
    abstract fun timerDailyProgressDao(): TimerDailyProgressDao
    abstract fun scheduleCursorProgressDao(): ScheduleCursorProgressDao
    abstract fun scheduleCursorDailyProgressDao(): ScheduleCursorDailyProgressDao
    abstract fun evaluatorEscalationDao(): EvaluatorEscalationDao
    abstract fun exerciseSubCounterProgressDao(): ExerciseSubCounterProgressDao
    abstract fun readingSessionLogDao(): ReadingSessionLogDao
    abstract fun computedScheduleProgressDao(): ComputedScheduleProgressDao
    abstract fun computedScheduleWatchLogDao(): ComputedScheduleWatchLogDao

    companion object {
        /** Adds Timer-with-duration kind support — see Plan 2. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habit_instance ADD COLUMN timerTargetSeconds INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `timer_daily_progress` (" +
                        "`habitInstanceId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                        "`targetSeconds` INTEGER NOT NULL, `remainingSeconds` INTEGER NOT NULL, " +
                        "`completed` INTEGER NOT NULL, `completedAt` INTEGER, " +
                        "`activeSessionStartedAt` INTEGER, PRIMARY KEY(`habitInstanceId`, `date`))"
                )
            }
        }

        /** Adds Schedule-cursor kind support. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_cursor_progress` (" +
                        "`habitInstanceId` INTEGER NOT NULL, `cursorIndex` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`habitInstanceId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_cursor_daily_progress` (" +
                        "`habitInstanceId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                        "`entriesMarkedRead` INTEGER NOT NULL, `completed` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`habitInstanceId`, `date`))"
                )
            }
        }

        /** Adds the cross-habit evaluator's escalation-tracking table. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `evaluator_escalation` (" +
                        "`habitInstanceId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                        "`escalated` INTEGER NOT NULL, PRIMARY KEY(`habitInstanceId`, `date`))"
                )
            }
        }

        /** Adds the Exercise sub-counter tracking table. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_sub_counter_progress` (" +
                        "`exerciseKey` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                        "`count` INTEGER NOT NULL, PRIMARY KEY(`exerciseKey`, `date`))"
                )
            }
        }

        /** Adds the Reading per-session log table. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reading_session_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitInstanceId` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, " +
                        "`durationSeconds` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reading_session_log_habitInstanceId_date` " +
                        "ON `reading_session_log` (`habitInstanceId`, `date`)"
                )
            }
        }

        /** Adds ComputedSchedule kind support: 3 new nullable anchor-config columns on
         * habit_instance (mirrors how timerTargetSeconds was added to the same table in
         * MIGRATION_1_2 — no separate config table needed for scalar per-instance values), a
         * new single-row-per-instance position table (mirrors schedule_cursor_progress's
         * shape), and a new append-only per-watch-event log table (mirrors reading_session_log's
         * shape — added per the Scope Revision below the CEO Phase 1 header; this table did not
         * exist in the plan's original draft of this migration, but is folded into this same
         * MIGRATION_6_7 rather than a new MIGRATION_7_8 since v7 was never shipped). Never
         * fallbackToDestructiveMigration() — see Global Constraints. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habit_instance ADD COLUMN anchorItemNumber INTEGER")
                db.execSQL("ALTER TABLE habit_instance ADD COLUMN anchorDate TEXT")
                db.execSQL("ALTER TABLE habit_instance ADD COLUMN intervalDays INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `computed_schedule_progress` (" +
                        "`habitInstanceId` INTEGER NOT NULL, `nextItemNumber` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`habitInstanceId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `computed_schedule_watch_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitInstanceId` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, `episodeNumber` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_computed_schedule_watch_log_habitInstanceId_date` " +
                        "ON `computed_schedule_watch_log` (`habitInstanceId`, `date`)"
                )
            }
        }
    }
}
```

`app/src/main/java/com/ziv/reminders/data/AppContainer.kt` — two changes only in this task (the
`computedScheduleProgressDao` getter and `MIGRATION_6_7` in the migration list); the repository
property and `DashboardDataSource` member come in Task 4, same staged-compile approach the
schedule-cursor plan's Task 1 → Task 4 split used:

```kotlin
    private val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "reminders.db")
            // Never fallbackToDestructiveMigration() — see Global Constraints.
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
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
    val computedScheduleProgressDao get() = db.computedScheduleProgressDao()
    val computedScheduleWatchLogDao get() = db.computedScheduleWatchLogDao()
```

(Everything else in `AppContainer.kt` — `counterHabitRepository`, `timerHabitRepository`,
`subCounterRepository`, `tanakhSchedule`, `scheduleCursorRepository`, `habitEngine`,
`crossHabitEvaluator`, `habitScheduler`, and both interfaces — is unchanged in this task.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ComputedScheduleProgressDaoTest"`
Expected: PASS (4 tests)

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ComputedScheduleWatchLogDaoTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Write the failing migration test**

`app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration6To7Test.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseMigration6To7Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate6To7_preservesExistingRows_addsAnchorColumnsAndComputedScheduleTable() {
        // Seed a v6 database with a real pre-existing Tanakh row, exactly as an already-installed
        // app (post-Plan "reading-session-log") would have.
        helper.createDatabase(TEST_DB_NAME, 6).apply {
            execSQL(
                "INSERT INTO habit_instance (id, kind, name, enabledDaysMask, notificationTitle, notificationBody, counterGoal, timerTargetSeconds) " +
                    "VALUES (3, 'SCHEDULE_CURSOR', 'Tanakh', 31, 't', 'b', NULL, NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 7, true, AppDatabase.MIGRATION_6_7)

        migrated.query("SELECT name FROM habit_instance WHERE id = 3").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Tanakh", cursor.getString(0))
        }
        migrated.query("SELECT anchorItemNumber, anchorDate, intervalDays FROM habit_instance WHERE id = 3").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0)) // anchorItemNumber — NULL for a pre-existing row
            assertTrue(cursor.isNull(1)) // anchorDate
            assertTrue(cursor.isNull(2)) // intervalDays
        }
        migrated.query("SELECT COUNT(*) FROM computed_schedule_progress").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        // Added per the Scope Revision (see the section below the CEO Phase 1 header) — the
        // watch-log table did not exist in this migration's original draft.
        migrated.query("SELECT COUNT(*) FROM computed_schedule_watch_log").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test-6-7"
    }
}
```

- [ ] **Step 6: Run the migration test, then the full suite**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.AppDatabaseMigration6To7Test"`
Expected: PASS (1 test)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests still green)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitKind.kt app/src/main/java/com/ziv/reminders/data/HabitInstance.kt app/src/main/java/com/ziv/reminders/data/ComputedScheduleProgress.kt app/src/main/java/com/ziv/reminders/data/ComputedScheduleProgressDao.kt app/src/main/java/com/ziv/reminders/data/ComputedScheduleWatchLog.kt app/src/main/java/com/ziv/reminders/data/ComputedScheduleWatchLogDao.kt app/src/main/java/com/ziv/reminders/data/AppDatabase.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/test/java/com/ziv/reminders/data/ComputedScheduleProgressDaoTest.kt app/src/test/java/com/ziv/reminders/data/ComputedScheduleWatchLogDaoTest.kt app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration6To7Test.kt
git commit -m "feat: add Room schema v7 for the ComputedSchedule habit kind, incl. the watch-date log"
```

---

### Task 2: `HabitStatus.ComputedScheduleStatus` + pure release-schedule math

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitStatus.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/ComputedSchedule.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/ComputedScheduleTest.kt`

**Interfaces:**
- Produces: `HabitStatus.ComputedScheduleStatus(nextItemNumber: Int, dueCount: Int, isDueToday:
  Boolean)`; `fun computeReleaseDate(anchorItemNumber: Int, anchorDate: LocalDate, intervalDays:
  Int, itemNumber: Int): LocalDate`; `fun deriveComputedScheduleStatus(nextItemNumber: Int,
  anchorItemNumber: Int, anchorDate: LocalDate, intervalDays: Int, today: LocalDate):
  HabitStatus.ComputedScheduleStatus`. Both functions are pure, no Android/Room dependency —
  consumed by `ComputedScheduleRepository` (Task 3).

Note on why this task returns `HabitStatus.ComputedScheduleStatus` directly, unlike
Schedule-cursor's `deriveScheduleEntryStatus` (which returns its own intermediate
`ScheduleEntryStatus` sealed type, only later mapped into `HabitStatus.ScheduleCursorStatus` by
the repository): Tanakh's derivation has 4 qualitatively different states (`OnSchedule`, `Behind`,
`Waiting`, `Finished` — the last one carrying no entry at all), which genuinely needs a sealed
type. ComputedSchedule's series never ends and its result is 3 flat scalars with no polymorphism,
so an intermediate type would add a mapping step with nothing to map from — `HabitStatus` itself
is the right return type here.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/ziv/reminders/data/ComputedScheduleTest.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComputedScheduleTest {

    // Arbitrary test anchor — NOT episode 542's real release date (see HabitSeeding.kt's TODO).
    private val anchorDate = LocalDate.of(2026, 7, 14)

    @Test
    fun computeReleaseDate_sameItemAsAnchor_returnsAnchorDate() {
        assertEquals(anchorDate, computeReleaseDate(anchorItemNumber = 542, anchorDate = anchorDate, intervalDays = 7, itemNumber = 542))
    }

    @Test
    fun computeReleaseDate_laterItem_addsIntervalTimesDelta() {
        assertEquals(LocalDate.of(2026, 7, 21), computeReleaseDate(542, anchorDate, 7, itemNumber = 543))
        assertEquals(LocalDate.of(2026, 7, 28), computeReleaseDate(542, anchorDate, 7, itemNumber = 544))
    }

    @Test
    fun computeReleaseDate_earlierItem_subtractsIntervalTimesDelta() {
        assertEquals(LocalDate.of(2026, 7, 7), computeReleaseDate(542, anchorDate, 7, itemNumber = 541))
    }

    @Test
    fun deriveComputedScheduleStatus_beforeReleaseDate_notDue() {
        // item 543 releases 2026-07-21; today is the day before.
        val status = deriveComputedScheduleStatus(543, 542, anchorDate, 7, today = LocalDate.of(2026, 7, 20))

        assertEquals(0, status.dueCount)
        assertFalse(status.isDueToday)
        assertEquals(543, status.nextItemNumber)
    }

    @Test
    fun deriveComputedScheduleStatus_onReleaseDate_isDueTodayWithCountOne() {
        val status = deriveComputedScheduleStatus(543, 542, anchorDate, 7, today = LocalDate.of(2026, 7, 21))

        assertEquals(1, status.dueCount)
        assertTrue(status.isDueToday)
    }

    @Test
    fun deriveComputedScheduleStatus_midweek_stillDueCountOne_isDueTodayStaysTrue() {
        // item 543 releases 2026-07-21; 3 days later (still within the week before the NEXT
        // release) isDueToday must still be true — it means "there's an unwatched episode
        // released this interval," not literally "released today." A subtly wrong
        // implementation (e.g. isDueToday = today.isEqual(releaseDate)) would pass every
        // other test in this file but flip this one, so this case must be asserted explicitly.
        val status = deriveComputedScheduleStatus(543, 542, anchorDate, 7, today = LocalDate.of(2026, 7, 24))

        assertEquals(1, status.dueCount)
        assertTrue(status.isDueToday)
    }

    @Test
    fun deriveComputedScheduleStatus_oneIntervalLate_dueCountTwo_notIsDueToday() {
        val status = deriveComputedScheduleStatus(543, 542, anchorDate, 7, today = LocalDate.of(2026, 7, 28))

        assertEquals(2, status.dueCount)
        assertFalse(status.isDueToday)
    }

    @Test
    fun deriveComputedScheduleStatus_twoIntervalsLate_dueCountThree() {
        val status = deriveComputedScheduleStatus(543, 542, anchorDate, 7, today = LocalDate.of(2026, 8, 4))

        assertEquals(3, status.dueCount)
        assertFalse(status.isDueToday)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ComputedScheduleTest"`
Expected: FAIL — `computeReleaseDate`, `deriveComputedScheduleStatus`, and
`HabitStatus.ComputedScheduleStatus` don't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/data/HabitStatus.kt` (full file):
```kotlin
package com.ziv.reminders.data

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
}
```

`app/src/main/java/com/ziv/reminders/data/ComputedSchedule.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure date-arithmetic release-schedule math — no Room/Android dependency, no persisted per-item
 * table. Given one known anchor (an item number and the date it released) and a fixed interval,
 * every other item's release date is fully computable. See the C++ Weekly design doc's
 * Recommended Approach for why this was chosen over a persisted, finite per-item schedule table
 * (Approach C, rejected): the series has no end date, so a finite table would silently run dry
 * without periodic reseeding, forever.
 */
fun computeReleaseDate(anchorItemNumber: Int, anchorDate: LocalDate, intervalDays: Int, itemNumber: Int): LocalDate =
    anchorDate.plusDays((itemNumber - anchorItemNumber).toLong() * intervalDays)

/**
 * dueCount counts every item released on or before today, up to and including nextItemNumber's
 * own release, treating "released today" as 1 rather than 0 — so isDueToday (dueCount == 1) and
 * "behind by more than one release" (dueCount > 1) are cleanly distinguishable. This is
 * deliberately NOT the same shape as ScheduleEntryStatus's dueCount (Tanakh's Behind case, which
 * never includes "due today" — OnSchedule is its own separate branch): the two kinds' dueCount
 * fields answer differently-scoped questions, so callers (e.g. ComputedScheduleHabitRow's dot
 * color) must not assume `dueCount > 0` alone means "behind" the way Tanakh's does — here that
 * test must be `dueCount > 1`.
 */
fun deriveComputedScheduleStatus(
    nextItemNumber: Int,
    anchorItemNumber: Int,
    anchorDate: LocalDate,
    intervalDays: Int,
    today: LocalDate,
): HabitStatus.ComputedScheduleStatus {
    val releaseDate = computeReleaseDate(anchorItemNumber, anchorDate, intervalDays, nextItemNumber)
    val dueCount = if (!releaseDate.isAfter(today)) {
        (ChronoUnit.DAYS.between(releaseDate, today) / intervalDays) + 1
    } else {
        0L
    }
    return HabitStatus.ComputedScheduleStatus(
        nextItemNumber = nextItemNumber,
        dueCount = dueCount.toInt(),
        isDueToday = dueCount == 1L,
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ComputedScheduleTest"`
Expected: PASS (8 tests — includes the mid-week isDueToday-stays-true case added during /autoplan Eng review)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests still green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitStatus.kt app/src/main/java/com/ziv/reminders/data/ComputedSchedule.kt app/src/test/java/com/ziv/reminders/data/ComputedScheduleTest.kt
git commit -m "feat: add HabitStatus.ComputedScheduleStatus and pure release-schedule math"
```

---

### Task 3: `ComputedScheduleRepository` + the `HabitReminderReceiver` compile-fix

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/data/ComputedScheduleRepository.kt`
- Modify: `app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/ComputedScheduleRepositoryTest.kt`

**Interfaces:**
- Consumes: `ComputedScheduleProgress`, `ComputedScheduleProgressDao`, `ComputedScheduleWatchLog`,
  `ComputedScheduleWatchLogDao` (Task 1); `deriveComputedScheduleStatus` (Task 2); `HabitStats`
  (existing, `data/HabitStats.kt` — the kind-agnostic streak calculator over `Set<LocalDate>`).
- Produces: `class ComputedScheduleRepository(progressDao: ComputedScheduleProgressDao,
  watchLogDao: ComputedScheduleWatchLogDao) { suspend fun todayStatus(instance, today):
  HabitStatus.ComputedScheduleStatus; suspend fun markNextWatched(instance, today); suspend fun
  currentStreak(instance, today): Int; suspend fun completedDates(instance): List<String> }` —
  consumed by `HabitEngine` (Task 4), the dashboard (Task 5), the new stats screen (Task 6).
  **`currentStreak` and `completedDates` are new-shaped per the Scope Revision** (see the section
  below the CEO Phase 1 header) — the plan's original draft of this task had `currentStreak`
  hardcoded to always return 0 and no `completedDates` method at all, since v1 originally kept no
  watch-date log to compute either from.

Note on `HabitReminderReceiver`'s `when (status)`: this is an expression assigned to a `val`
(`val completed = when (status) { ... }`), so adding a new `HabitStatus` subtype makes it
non-exhaustive — a genuine compile error in `main` sourceSet code that must be fixed in this same
task (same reason Plan "schedule-cursor-kind" fixed it for `ScheduleCursorStatus`). By contrast,
`DashboardScreen`'s `HabitRow` dispatches via a `when` used as a *statement*, so it compiles
unmodified and Task 5 adds the new branch as scope expansion, not a forced fix.

Note on `markNextWatched`'s signature: the design doc's shorthand describes this as
`markNextWatched(instance)`, but every other repository in this codebase
(`CounterHabitRepository.increment`, `TimerHabitRepository.start`/`stop`,
`ScheduleCursorRepository.markRead`) takes `today: LocalDate` as an explicit parameter instead of
calling `LocalDate.now()` internally, specifically for testability. This plan follows that
established convention rather than the design doc's shorthand.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ziv/reminders/data/ComputedScheduleRepositoryTest.kt`:
```kotlin
package com.ziv.reminders.data

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

private class FakeComputedScheduleProgressDao : ComputedScheduleProgressDao {
    val rows = mutableMapOf<Long, ComputedScheduleProgress>()
    override suspend fun getByInstance(habitInstanceId: Long) = rows[habitInstanceId]
    override suspend fun insertIfAbsent(progress: ComputedScheduleProgress) {
        rows.putIfAbsent(progress.habitInstanceId, progress)
    }
    override suspend fun upsert(progress: ComputedScheduleProgress) { rows[progress.habitInstanceId] = progress }
}

// Added per the Scope Revision (see the section below the CEO Phase 1 header) — this fake didn't
// exist in the plan's original draft of this task, since currentStreak() had nothing to read.
private class FakeComputedScheduleWatchLogDao : ComputedScheduleWatchLogDao {
    val rows = mutableListOf<ComputedScheduleWatchLog>()
    override suspend fun insert(entry: ComputedScheduleWatchLog): Long {
        rows += entry
        return rows.size.toLong()
    }
    override suspend fun getWatchedDates(habitInstanceId: Long): List<String> =
        rows.filter { it.habitInstanceId == habitInstanceId }.map { it.date }.distinct()
}

class ComputedScheduleRepositoryTest {

    // Episode 543 releases 2026-07-21 given this anchor/interval — arbitrary test values, not
    // episode 542's real release date.
    private val instance = HabitInstance(
        id = 4L, kind = "COMPUTED_SCHEDULE", name = "C++ Weekly", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null,
        anchorItemNumber = 542, anchorDate = "2026-07-14", intervalDays = 7,
    )

    @Test
    fun todayStatus_noProgressRow_fallsBackToAnchorItemNumberPlusOne() = runTest {
        val repo = ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao())

        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 7, 20))

        assertEquals(543, status.nextItemNumber)
        assertEquals(0, status.dueCount)
    }

    @Test
    fun todayStatus_reflectsThePersistedProgressRow() = runTest {
        val progressDao = FakeComputedScheduleProgressDao()
        progressDao.rows[4L] = ComputedScheduleProgress(4L, nextItemNumber = 550)
        val repo = ComputedScheduleRepository(progressDao, FakeComputedScheduleWatchLogDao())

        // item 550's release date: 2026-07-14 + (550-542)*7 = 2026-09-08.
        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 9, 8))

        assertEquals(550, status.nextItemNumber)
        assertEquals(1, status.dueCount)
    }

    @Test
    fun markNextWatched_dueCountZero_isNoOp() = runTest {
        val progressDao = FakeComputedScheduleProgressDao()
        progressDao.rows[4L] = ComputedScheduleProgress(4L, nextItemNumber = 543)
        val watchLogDao = FakeComputedScheduleWatchLogDao()
        val repo = ComputedScheduleRepository(progressDao, watchLogDao)

        repo.markNextWatched(instance, today = LocalDate.of(2026, 7, 20)) // one day before release

        assertEquals(543, progressDao.rows[4L]?.nextItemNumber)
        // Added per the Scope Revision: a no-op tap must not log a phantom watch event either.
        assertEquals(emptyList(), watchLogDao.rows)
    }

    @Test
    fun markNextWatched_due_advancesByExactlyOne() = runTest {
        val progressDao = FakeComputedScheduleProgressDao()
        progressDao.rows[4L] = ComputedScheduleProgress(4L, nextItemNumber = 543)
        val watchLogDao = FakeComputedScheduleWatchLogDao()
        val repo = ComputedScheduleRepository(progressDao, watchLogDao)
        val releaseDay = LocalDate.of(2026, 7, 21)

        repo.markNextWatched(instance, today = releaseDay)

        assertEquals(544, progressDao.rows[4L]?.nextItemNumber)
        // Added per the Scope Revision: markNextWatched must log the watch event (today's date,
        // the episode number that was actually just watched — 543, the pre-increment
        // nextItemNumber — not 544, the new one) inside the same call, not as a separate step a
        // caller could forget.
        assertEquals(listOf(ComputedScheduleWatchLog(id = 1L, habitInstanceId = 4L, date = releaseDay.toString(), episodeNumber = 543)), watchLogDao.rows)
    }

    @Test
    fun markNextWatched_multipleReleasesBehind_stillAdvancesByExactlyOneNotTheWholeBacklog() = runTest {
        val progressDao = FakeComputedScheduleProgressDao()
        progressDao.rows[4L] = ComputedScheduleProgress(4L, nextItemNumber = 543)
        val repo = ComputedScheduleRepository(progressDao, FakeComputedScheduleWatchLogDao())

        // 3 releases behind (dueCount 3) — a single tap must still advance by exactly 1.
        repo.markNextWatched(instance, today = LocalDate.of(2026, 8, 4))

        assertEquals(544, progressDao.rows[4L]?.nextItemNumber)
        // Strengthened per /autoplan Eng review (Section 3's Task 3 GAP #2): also assert dueCount
        // recomputed after the tap still reflects the correct remaining backlog (was 3, now 2),
        // not just that nextItemNumber incremented by 1 — an implementation bug that silently
        // skipped or double-counted backlog episodes would pass the assertion above alone.
        val statusAfter = repo.todayStatus(instance, today = LocalDate.of(2026, 8, 4))
        assertEquals(2, statusAfter.dueCount)
    }

    @Test
    fun currentStreak_noWatchLogRows_returnsZero() = runTest {
        val repo = ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao())

        assertEquals(0, repo.currentStreak(instance, LocalDate.of(2026, 7, 21)))
    }

    @Test
    fun currentStreak_delegatesToHabitStats_overTheWatchLogsDistinctDates() = runTest {
        // Real streak math now (per the Scope Revision — this test replaces the plan's original
        // currentStreak_alwaysReturnsZero, which is no longer correct). 3 consecutive watched
        // days ending today.
        val watchLogDao = FakeComputedScheduleWatchLogDao()
        watchLogDao.rows += ComputedScheduleWatchLog(1L, 4L, "2026-07-19", 541)
        watchLogDao.rows += ComputedScheduleWatchLog(2L, 4L, "2026-07-20", 542)
        watchLogDao.rows += ComputedScheduleWatchLog(3L, 4L, "2026-07-21", 543)
        val repo = ComputedScheduleRepository(FakeComputedScheduleProgressDao(), watchLogDao)

        assertEquals(3, repo.currentStreak(instance, today = LocalDate.of(2026, 7, 21)))
    }

    @Test
    fun currentStreak_gapBreaksTheStreak() = runTest {
        val watchLogDao = FakeComputedScheduleWatchLogDao()
        watchLogDao.rows += ComputedScheduleWatchLog(1L, 4L, "2026-07-14", 542) // isolated, a week before
        watchLogDao.rows += ComputedScheduleWatchLog(2L, 4L, "2026-07-21", 543)
        val repo = ComputedScheduleRepository(FakeComputedScheduleProgressDao(), watchLogDao)

        assertEquals(1, repo.currentStreak(instance, today = LocalDate.of(2026, 7, 21)))
    }

    @Test
    fun todayStatus_missingAnchorConfig_throwsIllegalStateException() = runTest {
        // Added per /autoplan Eng review (Section 3's Task 3 GAP #1) — pins the intentional-crash
        // contract named in the CEO phase's Failure Modes Registry, so a future refactor of
        // todayStatus can't silently swap it for a softer default.
        val incompleteInstance = instance.copy(anchorDate = null)
        val repo = ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao())

        assertFailsWith<IllegalStateException> { repo.todayStatus(incompleteInstance, LocalDate.of(2026, 7, 21)) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ComputedScheduleRepositoryTest"`
Expected: FAIL — `ComputedScheduleRepository` doesn't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/data/ComputedScheduleRepository.kt`:
```kotlin
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
```

`app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt` — change only the
`when` block inside `handleReminder`'s coroutine:
```kotlin
                val status = engine.todayStatus(instance, today())
                val completed = when (status) {
                    is HabitStatus.CounterStatus -> status.completed
                    is HabitStatus.TimerStatus -> status.completed
                    is HabitStatus.ScheduleCursorStatus -> status.completed
                    // dueCount == 0 means nothing has released yet — nothing to notify about —
                    // so this counts as "completed" for reminder-suppression purposes, even
                    // though no user action produced it. dueCount >= 1 (something new to watch)
                    // is the only case that should ever fire this habit's reminder.
                    is HabitStatus.ComputedScheduleStatus -> status.dueCount == 0
                }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ComputedScheduleRepositoryTest"`
Expected: PASS (9 tests — includes the 3 currentStreak tests, the strengthened
multipleReleasesBehind dueCount assertion, and the missingAnchorConfig crash-contract test added
per the Scope Revision / Eng review, replacing the original 6)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests, including `HabitReminderReceiverTest`, still green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/ComputedScheduleRepository.kt app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt app/src/test/java/com/ziv/reminders/data/ComputedScheduleRepositoryTest.kt
git commit -m "feat: add ComputedScheduleRepository with a real currentStreak, and fix HabitReminderReceiver's exhaustive when"
```

---

### Task 4: `HabitEngine` dispatch extended for `COMPUTED_SCHEDULE`

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/engine/HabitEngine.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`
- Modify: `app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt`
- Modify: `app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt`
- Modify: `app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt`

**Interfaces:**
- Consumes: `ComputedScheduleRepository` (Task 3).
- Produces: `HabitEngine(counterRepository, timerRepository, scheduleCursorRepository,
  computedScheduleRepository)` — the constructor signature change. Every existing call site that
  constructs `HabitEngine(...)` must be updated in this same task for the module to compile (same
  reason as the schedule-cursor plan's Task 4: Kotlin compiles main+test source sets together).
  `DashboardDataSource` gains `val computedScheduleRepository: ComputedScheduleRepository` —
  needed because, like Schedule-cursor's `markRead`, `ComputedScheduleRepository.markNextWatched`
  is a direct, synchronous repository call the dashboard ViewModel needs access to (Task 5).
  `ExerciseDetailDataSource`/`ActivityDataSource` are untouched — neither needs this kind (no
  Activity-screen section or stats screen exists for it, matching the design doc's v1 scope).

- [ ] **Step 1: Write the failing test**

Full `app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt`:
```kotlin
package com.ziv.reminders.engine

import com.ziv.reminders.data.ComputedScheduleProgress
import com.ziv.reminders.data.ComputedScheduleProgressDao
import com.ziv.reminders.data.ComputedScheduleRepository
import com.ziv.reminders.data.ComputedScheduleWatchLog
import com.ziv.reminders.data.ComputedScheduleWatchLogDao
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.CounterDailyProgress
import com.ziv.reminders.data.CounterDailyProgressDao
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.ScheduleCursorDailyProgress
import com.ziv.reminders.data.ScheduleCursorDailyProgressDao
import com.ziv.reminders.data.ScheduleCursorProgress
import com.ziv.reminders.data.ScheduleCursorProgressDao
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.data.ScheduleEntry
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TimerDailyProgress
import com.ziv.reminders.data.TimerDailyProgressDao
import com.ziv.reminders.data.TimerHabitRepository
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeCounterDailyProgressDao : CounterDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, CounterDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: CounterDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
}

private class FakeTimerDailyProgressDao : TimerDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, TimerDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: TimerDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
    override suspend fun getActiveSessions() = rows.values.filter { it.activeSessionStartedAt != null }
}

private class FakeScheduleCursorProgressDao : ScheduleCursorProgressDao {
    val rows = mutableMapOf<Long, ScheduleCursorProgress>()
    override suspend fun getByInstance(habitInstanceId: Long) = rows[habitInstanceId]
    override suspend fun upsert(progress: ScheduleCursorProgress) { rows[progress.habitInstanceId] = progress }
}

private class FakeScheduleCursorDailyProgressDao : ScheduleCursorDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, ScheduleCursorDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: ScheduleCursorDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
}

private class FakeComputedScheduleProgressDao : ComputedScheduleProgressDao {
    val rows = mutableMapOf<Long, ComputedScheduleProgress>()
    override suspend fun getByInstance(habitInstanceId: Long) = rows[habitInstanceId]
    override suspend fun insertIfAbsent(progress: ComputedScheduleProgress) { rows.putIfAbsent(progress.habitInstanceId, progress) }
    override suspend fun upsert(progress: ComputedScheduleProgress) { rows[progress.habitInstanceId] = progress }
}

// Added per the Scope Revision (see the section below the CEO Phase 1 header) — didn't exist in
// the plan's original draft of this task.
private class FakeComputedScheduleWatchLogDao : ComputedScheduleWatchLogDao {
    val rows = mutableListOf<ComputedScheduleWatchLog>()
    override suspend fun insert(entry: ComputedScheduleWatchLog): Long { rows += entry; return rows.size.toLong() }
    override suspend fun getWatchedDates(habitInstanceId: Long): List<String> =
        rows.filter { it.habitInstanceId == habitInstanceId }.map { it.date }.distinct()
}

class HabitEngineTest {

    private val counterInstance = HabitInstance(
        id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )
    private val timerInstance = HabitInstance(
        id = 2L, kind = "TIMER", name = "Reading", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null, timerTargetSeconds = 900,
    )
    private val scheduleCursorInstance = HabitInstance(
        id = 3L, kind = "SCHEDULE_CURSOR", name = "Tanakh", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null,
    )
    private val computedScheduleInstance = HabitInstance(
        id = 4L, kind = "COMPUTED_SCHEDULE", name = "C++ Weekly", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null,
        anchorItemNumber = 542, anchorDate = "2026-07-14", intervalDays = 7,
    )
    private val schedule = listOf(ScheduleEntry("א", "א׳", LocalDate.of(2026, 7, 14)))
    private val today = LocalDate.of(2026, 7, 14)

    private fun newEngine(): HabitEngine = HabitEngine(
        CounterHabitRepository(FakeCounterDailyProgressDao()),
        TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
        ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule),
        ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao()),
    )

    @Test
    fun todayStatus_counterKind_dispatchesToCounterRepository() = runTest {
        val status = newEngine().todayStatus(counterInstance, today)

        assertEquals(HabitStatus.CounterStatus(current = 0, goal = 5, completed = false), status)
    }

    @Test
    fun currentStreak_counterKind_dispatchesToCounterRepository() = runTest {
        val counterDao = FakeCounterDailyProgressDao()
        counterDao.rows[1L to "2026-07-13"] = CounterDailyProgress(1L, "2026-07-13", 5, true)
        val engine = HabitEngine(
            CounterHabitRepository(counterDao),
            TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule),
            ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao()),
        )

        assertEquals(1, engine.currentStreak(counterInstance, today))
    }

    @Test
    fun todayStatus_timerKind_dispatchesToTimerRepository() = runTest {
        val status = newEngine().todayStatus(timerInstance, today)

        assertEquals(HabitStatus.TimerStatus(remainingSeconds = 900, targetSeconds = 900, isRunning = false, completed = false), status)
    }

    @Test
    fun currentStreak_timerKind_dispatchesToTimerRepository() = runTest {
        val timerDao = FakeTimerDailyProgressDao()
        timerDao.rows[2L to "2026-07-13"] = TimerDailyProgress(2L, "2026-07-13", 900, 0, true, 1L, null)
        val engine = HabitEngine(
            CounterHabitRepository(FakeCounterDailyProgressDao()),
            TimerHabitRepository(timerDao, SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule),
            ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao()),
        )

        assertEquals(1, engine.currentStreak(timerInstance, today))
    }

    @Test
    fun todayStatus_scheduleCursorKind_dispatchesToScheduleCursorRepository() = runTest {
        val status = newEngine().todayStatus(scheduleCursorInstance, today)

        assertEquals(HabitStatus.ScheduleCursorStatus("א", "א׳", dueCount = 0, completed = false, finished = false, isDueToday = true), status)
    }

    @Test
    fun currentStreak_scheduleCursorKind_dispatchesToScheduleCursorRepository() = runTest {
        val dailyDao = FakeScheduleCursorDailyProgressDao()
        dailyDao.rows[3L to "2026-07-13"] = ScheduleCursorDailyProgress(3L, "2026-07-13", 1, true)
        val engine = HabitEngine(
            CounterHabitRepository(FakeCounterDailyProgressDao()),
            TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDao(), dailyDao, schedule),
            ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao()),
        )

        assertEquals(1, engine.currentStreak(scheduleCursorInstance, today))
    }

    @Test
    fun todayStatus_computedScheduleKind_dispatchesToComputedScheduleRepository() = runTest {
        // item 543's release date: 2026-07-14 + (543-542)*7 = 2026-07-21 — after `today`.
        val status = newEngine().todayStatus(computedScheduleInstance, today)

        assertEquals(HabitStatus.ComputedScheduleStatus(nextItemNumber = 543, dueCount = 0, isDueToday = false), status)
    }

    @Test
    fun currentStreak_computedScheduleKind_dispatchesToComputedScheduleRepository() = runTest {
        // Renamed and rewritten per the Scope Revision (see the section below the CEO Phase 1
        // header) — this test used to assert currentStreak() was hardcoded to always return 0.
        // It now genuinely dispatches into ComputedScheduleRepository's real HabitStats-backed
        // computation; with an empty watch log (newEngine()'s fixture) that computation legitimately
        // answers 0, so this still exercises the dispatch path correctly, just no longer implies
        // the answer can never be anything else.
        assertEquals(0, newEngine().currentStreak(computedScheduleInstance, today))
    }

    @Test
    fun currentStreak_computedScheduleKind_withWatchLogHistory_dispatchesToRealValue() = runTest {
        val watchLogDao = FakeComputedScheduleWatchLogDao()
        watchLogDao.rows += ComputedScheduleWatchLog(1L, 4L, "2026-07-13", 542)
        watchLogDao.rows += ComputedScheduleWatchLog(2L, 4L, "2026-07-14", 543)
        val engine = HabitEngine(
            CounterHabitRepository(FakeCounterDailyProgressDao()),
            TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule),
            ComputedScheduleRepository(FakeComputedScheduleProgressDao(), watchLogDao),
        )

        assertEquals(2, engine.currentStreak(computedScheduleInstance, today))
    }

    @Test
    fun todayStatus_unknownKind_throws() = runTest {
        val unknown = counterInstance.copy(kind = "SOMETHING_ELSE")

        assertFailsWith<IllegalArgumentException> { newEngine().todayStatus(unknown, today) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.engine.HabitEngineTest"`
Expected: FAIL — `HabitEngine`'s constructor doesn't accept a fourth argument yet (compile
error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/engine/HabitEngine.kt` (full file):
```kotlin
package com.ziv.reminders.engine

import com.ziv.reminders.data.ComputedScheduleRepository
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitKind
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.data.TimerHabitRepository
import java.time.LocalDate

/**
 * Dispatches the two calls every kind can answer generically. Write actions (Counter's
 * increment, Timer's start/stop, Schedule-cursor's markRead, ComputedSchedule's markNextWatched)
 * deliberately stay on each kind's own repository, not here.
 */
class HabitEngine(
    private val counterRepository: CounterHabitRepository,
    private val timerRepository: TimerHabitRepository,
    private val scheduleCursorRepository: ScheduleCursorRepository,
    private val computedScheduleRepository: ComputedScheduleRepository,
) {

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus =
        when (instance.kind) {
            HabitKind.COUNTER.name -> counterRepository.todayStatus(instance, today)
            HabitKind.TIMER.name -> timerRepository.todayStatus(instance, today)
            HabitKind.SCHEDULE_CURSOR.name -> scheduleCursorRepository.todayStatus(instance, today)
            HabitKind.COMPUTED_SCHEDULE.name -> computedScheduleRepository.todayStatus(instance, today)
            else -> throw IllegalArgumentException("Unknown habit kind: ${instance.kind}")
        }

    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int =
        when (instance.kind) {
            HabitKind.COUNTER.name -> counterRepository.currentStreak(instance, today)
            HabitKind.TIMER.name -> timerRepository.currentStreak(instance, today)
            HabitKind.SCHEDULE_CURSOR.name -> scheduleCursorRepository.currentStreak(instance, today)
            HabitKind.COMPUTED_SCHEDULE.name -> computedScheduleRepository.currentStreak(instance, today)
            else -> throw IllegalArgumentException("Unknown habit kind: ${instance.kind}")
        }
}
```

`app/src/main/java/com/ziv/reminders/data/AppContainer.kt` (full file):
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
                AppDatabase.MIGRATION_6_7,
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
    val computedScheduleProgressDao get() = db.computedScheduleProgressDao()
    val computedScheduleWatchLogDao get() = db.computedScheduleWatchLogDao()
    override val counterHabitRepository: CounterHabitRepository by lazy { CounterHabitRepository(counterDailyProgressDao) }
    override val timerHabitRepository: TimerHabitRepository by lazy {
        TimerHabitRepository(
            timerDailyProgressDao, SystemClock, readingSessionLogDao,
            runInTransaction = { block -> db.withTransaction { block() } },
        )
    }
    override val subCounterRepository: SubCounterRepository by lazy { SubCounterRepository(exerciseSubCounterProgressDao) }

    /** Falls back to an empty schedule (never throws) if the bundled asset is ever missing or
     * malformed — a crash here must not take down the whole app. */
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
    override val computedScheduleRepository: ComputedScheduleRepository by lazy {
        ComputedScheduleRepository(
            computedScheduleProgressDao, computedScheduleWatchLogDao,
            runInTransaction = { block -> db.withTransaction { block() } },
        )
    }

    override val habitEngine: HabitEngine by lazy {
        HabitEngine(counterHabitRepository, timerHabitRepository, scheduleCursorRepository, computedScheduleRepository)
    }
    val crossHabitEvaluator: CrossHabitEvaluator by lazy { CrossHabitEvaluator(habitInstanceDao, habitEngine, evaluatorEscalationDao) }
    val habitScheduler: HabitScheduler by lazy { HabitScheduler(appContext) }
}

interface DashboardDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val timerHabitRepository: TimerHabitRepository
    val scheduleCursorRepository: ScheduleCursorRepository
    val computedScheduleRepository: ComputedScheduleRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
}

/** Parallel to DashboardDataSource, not an extension of it — keeps DashboardDataSource
 * free of Exercise-only members. AppContainer implements both. Unchanged by this plan: Exercise
 * doesn't need computedScheduleRepository. */
interface ExerciseDetailDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
    val subCounterRepository: SubCounterRepository
}

/** Parallel to the other two, not an extension of either. Still unchanged by this plan even after
 * the Scope Revision (see the section below the CEO Phase 1 header) added a real stats screen for
 * this kind: `ComputedScheduleStatsScreen` (Task 6) deliberately reuses `DashboardDataSource`
 * instead of joining the combined Activity screen's `ActivityViewModel`/`ActivityDataSource` — it
 * already has everything the new screen needs (`habitInstanceDao`, `computedScheduleRepository`,
 * `habitEngine`), so extending this interface too would be redundant, not required. */
interface ActivityDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val timerHabitRepository: TimerHabitRepository
    val scheduleCursorRepository: ScheduleCursorRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
}
```

`app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt` (full file):
```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.room.withTransaction
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.ComputedScheduleRepository
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
    override val computedScheduleRepository = ComputedScheduleRepository(
        db.computedScheduleProgressDao(), db.computedScheduleWatchLogDao(),
        runInTransaction = { block -> db.withTransaction { block() } },
    )
    override val habitEngine = HabitEngine(counterHabitRepository, timerHabitRepository, scheduleCursorRepository, computedScheduleRepository)
}
```

In `app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt`, update all 3
`receiver.habitEngineOverride = HabitEngine(...)` call sites (lines ~59, ~94, ~127) to add a
fourth argument, e.g. the first one:
```kotlin
        receiver.habitEngineOverride = HabitEngine(
            CounterHabitRepository(db.counterDailyProgressDao()),
            com.ziv.reminders.data.TimerHabitRepository(db.timerDailyProgressDao(), com.ziv.reminders.data.SystemClock),
            com.ziv.reminders.data.ScheduleCursorRepository(
                db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList(),
            ),
            com.ziv.reminders.data.ComputedScheduleRepository(db.computedScheduleProgressDao(), db.computedScheduleWatchLogDao()),
        )
```
(Apply the identical fourth-argument addition to the other two call sites — the test bodies
around them are otherwise unchanged. The two `handleWeeklySummary` tests set
`scheduleCursorRepositoryOverride` directly, not `habitEngineOverride`, so they are unaffected —
this kind is correctly out of scope for the weekly summary, per Global Constraints.)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.engine.HabitEngineTest"`
Expected: PASS (10 tests — includes the new withWatchLogHistory dispatch test added per the Scope
Revision)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests, including `DashboardViewModelTest` and `HabitReminderReceiverTest`, green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/engine/HabitEngine.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt
git commit -m "feat: extend HabitEngine dispatch for COMPUTED_SCHEDULE kind"
```

---

### Task 5: Dashboard — C++ Weekly row and tap-to-mark-watched

**Files:**
- Create: `app/src/main/res/drawable/ic_habit_cppweekly.xml`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Produces: `DashboardViewModel.onMarkNextWatched(instanceId: Long)` — a direct, synchronous
  repository call + `refresh()`, mirroring `onMarkRead`'s pattern. No undo action — unlike
  Counter's increment and Schedule-cursor's markRead, this kind has no per-day daily-progress
  table to reverse against (v1's `ComputedScheduleProgress` is a single running integer, not a
  per-day row), and the design doc doesn't ask for one; matching Timer's own no-undo precedent
  (for a different underlying reason, same resulting behavior), this stays simple.
- `ComputedScheduleHabitRow` — new composable, **now with the same `RowLongPressMenu`/"Statistics"
  entry every other row has** (updated per the Scope Revision — see the section below the CEO
  Phase 1 header; the plan's original draft of this task had no long-press menu at all here).
  `DashboardScreen`'s public composable and `HabitRow` both gain a new `onOpenCppWeeklyStats: ()
  -> Unit = {}` parameter, threaded the same way `onOpenTanakhStats` already is, wired to the new
  `cppWeeklyStats` nav destination in Task 6.

- [ ] **Step 1: Add the icon drawable resource**

`app/src/main/res/drawable/ic_habit_cppweekly.xml` — a placeholder design (the design doc
explicitly calls icon choice a separate, non-blocking decision; swap this for something better
later): a flat rounded square with a white play triangle, standing in for "video series," same
`108`×`108` viewport convention as the other 3 row icons for visual consistency.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Placeholder only (see design doc Open Questions — icon choice is explicitly
         non-blocking) — a flat rounded square with a white play triangle, standing in for
         "video series" until a real one is swapped in. -->
    <path android:fillColor="#455A64" android:pathData="M20,20 h68 a12,12 0 0 1 12,12 v44 a12,12 0 0 1 -12,12 h-68 a12,12 0 0 1 -12,-12 v-44 a12,12 0 0 1 12,-12 Z"/>
    <path android:fillColor="#FFFFFF" android:pathData="M46,40 L74,54 L46,68 Z"/>
</vector>
```

- [ ] **Step 2: Write the failing DashboardViewModel test**

Add to `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt` (new tests,
appended to the existing file — `ComputedScheduleProgress` needs a fully-qualified import or an
inline `com.ziv.reminders.data.ComputedScheduleProgress` reference, matching this file's existing
style of qualifying one-off types inline):
```kotlin
    @Test
    fun onMarkNextWatched_dueToday_advancesTheEpisodeNumberByOne() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        val today = java.time.LocalDate.now()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(
                id = 4L, kind = "COMPUTED_SCHEDULE", name = "C++ Weekly", enabledDaysMask = 0b1111111,
                notificationTitle = "t", notificationBody = "b", counterGoal = null,
                anchorItemNumber = 542, anchorDate = today.toString(), intervalDays = 7,
            )
        )
        db.computedScheduleProgressDao().insertIfAbsent(
            com.ziv.reminders.data.ComputedScheduleProgress(habitInstanceId = 4L, nextItemNumber = 542)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val watchedEpisode = viewModel.onMarkNextWatched(4L)
        testScheduler.advanceUntilIdle()

        assertEquals(542, watchedEpisode) // the episode that WAS next-to-watch, before advancing
        val status = viewModel.uiState.value.habits[0].status as HabitStatus.ComputedScheduleStatus
        assertEquals(543, status.nextItemNumber)

        db.close()
    }

    @Test
    fun onMarkNextWatched_notYetDue_isNoOp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        val today = java.time.LocalDate.now()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(
                id = 4L, kind = "COMPUTED_SCHEDULE", name = "C++ Weekly", enabledDaysMask = 0b1111111,
                notificationTitle = "t", notificationBody = "b", counterGoal = null,
                anchorItemNumber = 542, anchorDate = today.plusDays(7).toString(), intervalDays = 7,
            )
        )
        db.computedScheduleProgressDao().insertIfAbsent(
            com.ziv.reminders.data.ComputedScheduleProgress(habitInstanceId = 4L, nextItemNumber = 542)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val watchedEpisode = viewModel.onMarkNextWatched(4L)
        testScheduler.advanceUntilIdle()

        assertEquals(null, watchedEpisode) // no-op — nothing due, so no Snackbar should show
        val status = viewModel.uiState.value.habits[0].status as HabitStatus.ComputedScheduleStatus
        assertEquals(542, status.nextItemNumber)

        db.close()
    }
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `DashboardViewModel` has no `onMarkNextWatched` method (compile error:
"unresolved reference: onMarkNextWatched").

- [ ] **Step 4: Write the implementation**

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt` — add this method
alongside `onMarkRead` (rest of the file unchanged):
```kotlin
    /** No undo — this kind has no per-day daily-progress table to reverse against (v1's
     * ComputedScheduleProgress is a single running integer, not a per-day row); see the design
     * doc, which doesn't ask for one either.
     *
     * Returns the episode number that was just marked watched (for the caller's Snackbar text —
     * Final Approval Gate decision: this row was otherwise the only mutating row in the app with
     * zero tap feedback), or null if the tap was a no-op (nothing due yet). Reads todayStatus
     * once here to capture nextItemNumber *before* the increment, in addition to
     * markNextWatched's own internal read — a second cheap O(1) local Room read, not a
     * correctness concern for a single-user personal app. */
    suspend fun onMarkNextWatched(instanceId: Long): Int? {
        val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return null
        val statusBefore = dataSource.computedScheduleRepository.todayStatus(instance, LocalDate.now())
        if (statusBefore.dueCount == 0) return null
        dataSource.computedScheduleRepository.markNextWatched(instance, LocalDate.now())
        refresh()
        return statusBefore.nextItemNumber
    }
```

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt` — five changes (this section
was rewritten under the Scope Revision — see below the CEO Phase 1 header — to add the
`RowLongPressMenu`/"Statistics" entry the plan's original draft of this task explicitly omitted;
no new import is needed since this row now uses the already-imported `combinedClickable`, not the
plain `clickable` the original draft added):

1. Add `onOpenCppWeeklyStats: () -> Unit = {}` to the public `DashboardScreen` composable's own
   parameter list, alongside its 3 existing `onOpen*Stats` defaults:
```kotlin
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenExercise: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenExerciseStats: () -> Unit = {},
    onOpenReadingStats: () -> Unit = {},
    onOpenTanakhStats: () -> Unit = {},
    onOpenCppWeeklyStats: () -> Unit = {},
) {
```

2. Add `onMarkNextWatched: () -> Unit` and `onOpenCppWeeklyStats: () -> Unit` to `HabitRow`'s
   parameter list and its `when` branch:
```kotlin
@Composable
private fun HabitRow(
    habit: HabitRowUiState,
    onIncrement: () -> Unit,
    onToggleTimer: (Int) -> Unit,
    onResetReadingToday: () -> Unit,
    fetchReadingSessionCountToday: suspend () -> Int,
    onMarkRead: () -> Unit,
    onMarkNextWatched: () -> Unit,
    onOpenExercise: () -> Unit,
    onOpenExerciseStats: () -> Unit,
    onOpenReadingStats: () -> Unit,
    onOpenTanakhStats: () -> Unit,
    onOpenCppWeeklyStats: () -> Unit,
) {
    when (habit.status) {
        is HabitStatus.CounterStatus -> CounterHabitRow(habit, habit.status, onIncrement, onOpenExercise, onOpenExerciseStats)
        is HabitStatus.TimerStatus -> TimerHabitRow(habit, habit.status, onToggleTimer, onResetReadingToday, fetchReadingSessionCountToday, onOpenReadingStats)
        is HabitStatus.ScheduleCursorStatus -> ScheduleCursorHabitRow(habit, habit.status, onMarkRead, onOpenTanakhStats)
        is HabitStatus.ComputedScheduleStatus -> ComputedScheduleHabitRow(habit, habit.status, onMarkNextWatched, onOpenCppWeeklyStats)
    }
}
```

3. Add the new composable (place after `ScheduleCursorHabitRow`) — structurally an exact mirror of
   `ScheduleCursorHabitRow`'s `combinedClickable`/`showMenu`/`RowLongPressMenu` shape, per the
   Scope Revision:
```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComputedScheduleHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.ComputedScheduleStatus,
    onMarkNextWatched: () -> Unit,
    onOpenCppWeeklyStats: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    // combinedClickable, not plain clickable (updated per the Scope Revision — the plan's
    // original draft of this composable used plain clickable, since it had no long-press action).
    // Tap is a no-op while dueCount == 0 (nothing released yet) — mirrors
    // ScheduleCursorRepository.markRead's own defensive guard, applied here at the UI layer too,
    // not just inside ComputedScheduleRepository.markNextWatched. Long-press is always available
    // (Statistics makes sense regardless of due state — same reasoning as ScheduleCursorHabitRow's
    // own "Long-press is always available" comment).
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (status.dueCount > 0) onMarkNextWatched() },
            onLongClick = { showMenu = true },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painter = painterResource(R.drawable.ic_habit_cppweekly), contentDescription = null, modifier = Modifier.size(40.dp))
            HabitStatusDot(
                color = when {
                    // dueCount's shape here is deliberately NOT the same as ScheduleCursorStatus's
                    // dueCount (see ComputedSchedule.kt's deriveComputedScheduleStatus doc
                    // comment): dueCount == 1 means "due today", so red is reserved for
                    // dueCount > 1 (behind by more than one release), not dueCount > 0.
                    status.dueCount > 1 -> MaterialTheme.colorScheme.error
                    status.isDueToday -> StatusOrange
                    else -> GoalGreen
                },
            )
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                // Still replaces the usual "Streak: Nd" line with the episode number, unchanged by
                // the Scope Revision — this row's own display stays as originally designed even
                // though currentStreak() now returns a real value (it feeds the new stats screen,
                // Task 6, instead of this line — see ComputedScheduleRepository's doc comment).
                Text("Episode ${status.nextItemNumber}", style = MaterialTheme.typography.bodySmall)
            }
        }
        val statusText = when {
            status.dueCount > 1 -> "${status.dueCount} behind"
            status.isDueToday -> "New!"
            else -> "Waiting"
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            color = if (status.dueCount > 1) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
    }

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            options = listOf(RowMenuOption("Statistics", onOpenCppWeeklyStats)),
            onDismiss = { showMenu = false },
        )
    }
}
```

4. Update `DashboardScreen`'s `HabitRow` call site to pass the two new callbacks:
```kotlin
            HabitRow(
                habit = habit,
                onIncrement = {
                    coroutineScope.launch {
                        viewModel.onIncrement(habit.instanceId)
                        val result = snackbarHostState.showSnackbar(
                            message = "Incremented",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.onUndoIncrement(habit.instanceId)
                        }
                    }
                },
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
                onMarkNextWatched = {
                    coroutineScope.launch {
                        // No "Undo" action label (unlike onIncrement/onMarkRead above) — this
                        // kind has no per-day daily-progress table to reverse against; this is
                        // purely an acknowledgment Snackbar (Final Approval Gate decision — this
                        // row was otherwise the only mutating row in the app with zero tap
                        // feedback), shown only when a tap actually did something.
                        val watchedEpisode = viewModel.onMarkNextWatched(habit.instanceId)
                        if (watchedEpisode != null) {
                            snackbarHostState.showSnackbar(
                                message = "Marked episode $watchedEpisode watched",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                },
                onOpenExercise = onOpenExercise,
                onOpenExerciseStats = onOpenExerciseStats,
                onOpenReadingStats = onOpenReadingStats,
                onOpenTanakhStats = onOpenTanakhStats,
                onOpenCppWeeklyStats = onOpenCppWeeklyStats,
            )
```

5. `onOpenCppWeeklyStats` must also be threaded through wherever `DashboardScreen(...)` itself is
   called from `MainActivity.kt` — that call site is modified in Task 6 instead of here, alongside
   the new nav destination it navigates to, so the two changes land together.

(Only `onMarkNextWatched`/`onOpenCppWeeklyStats` are newly inserted in change 4's call site —
everything else there is unchanged, shown in full only so the insertion point is unambiguous.)

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: PASS (14 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (full suite green)

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/drawable/ic_habit_cppweekly.xml app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt
git commit -m "feat: add the C++ Weekly dashboard row and tap-to-mark-watched"
```

---

### Task 6: Long-press "Statistics" — `ComputedScheduleStatsScreen` + nav wiring

**Added per the Scope Revision** (see the section below the CEO Phase 1 header) — this task did
not exist in the plan's original draft; the original Task 6 ("Seed the C++ Weekly habit
instance") is now Task 7, and the original Task 7 ("On-device manual verification") is now Task 8.

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/ui/dashboard/ComputedScheduleStatsViewModel.kt`
- Create: `app/src/main/java/com/ziv/reminders/ui/dashboard/ComputedScheduleStatsScreen.kt`
- Modify: `app/src/main/java/com/ziv/reminders/MainActivity.kt`
- Test: `app/src/test/java/com/ziv/reminders/ui/dashboard/ComputedScheduleStatsViewModelTest.kt`

**Interfaces:**
- Consumes: `DashboardDataSource` (existing — already has `habitInstanceDao`,
  `computedScheduleRepository`, `habitEngine`, exactly what this screen needs; see the note on
  `ActivityDataSource` in Task 4 for why this reuses `DashboardDataSource` instead of joining the
  combined Activity screen's `ActivityViewModel`/`ActivityDataSource`); `ComputedScheduleRepository.
  completedDates`/`currentStreak` (Task 3); the existing, already-public `ActivitySectionState`
  data class and `HabitStatsSummary`/`HeatmapGrid`/`EmptySectionState` composables (all defined in
  `ui/activity/ActivityScreen.kt`/`HeatmapGrid.kt`, `internal`-visible — usable module-wide,
  already imported cross-package by `ExerciseStatsScreen.kt` from a different package the same
  way).
- Produces: `class ComputedScheduleStatsViewModel(dataSource: DashboardDataSource) : ViewModel() {
  val uiState: StateFlow<ActivitySectionState>; fun refresh() }`; `@Composable fun
  ComputedScheduleStatsScreen(viewModel: ComputedScheduleStatsViewModel, onBack: () -> Unit)` — own
  `Scaffold`/`TopAppBar`/back button, mirroring `TanakhStatsScreen`'s exact structure (chosen as
  the mirror target for being the shortest of the three existing stats screens); a new
  `"cppWeeklyStats"` `NavHost` destination in `MainActivity.kt`, mirroring the existing
  `"exerciseStats"`/`"readingStats"`/`"tanakhStats"` destinations' exact pattern.

- [ ] **Step 1: Write the failing ViewModel test**

`app/src/test/java/com/ziv/reminders/ui/dashboard/ComputedScheduleStatsViewModelTest.kt` (uses
real Room + Robolectric via `TestAppContainer`, matching `DashboardViewModelTest`'s own style,
rather than hand-written fakes — `TestAppContainer` already implements `DashboardDataSource` with
real DAOs as of Task 4, so no new fakes are needed here):
```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.ComputedScheduleProgress
import com.ziv.reminders.data.ComputedScheduleWatchLog
import com.ziv.reminders.data.HabitInstance
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComputedScheduleStatsViewModelTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun refresh_noWatchLogHistory_populatesEmptyState() = runTest {
        val db = newDb()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(
                id = 4L, kind = "COMPUTED_SCHEDULE", name = "C++ Weekly", enabledDaysMask = 0b1111111,
                notificationTitle = "t", notificationBody = "b", counterGoal = null,
                anchorItemNumber = 542, anchorDate = "2026-07-14", intervalDays = 7,
            )
        )
        val viewModel = ComputedScheduleStatsViewModel(TestAppContainer(db))

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(0, state.streak)
        assertEquals(0, state.totalCount)
        assertTrue(state.completedDates.isEmpty())

        db.close()
    }

    @Test
    fun refresh_withWatchLogHistory_populatesStreakTotalAndHeatmapDates() = runTest {
        val db = newDb()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(
                id = 4L, kind = "COMPUTED_SCHEDULE", name = "C++ Weekly", enabledDaysMask = 0b1111111,
                notificationTitle = "t", notificationBody = "b", counterGoal = null,
                anchorItemNumber = 542, anchorDate = "2026-07-14", intervalDays = 7,
            )
        )
        db.computedScheduleProgressDao().insertIfAbsent(ComputedScheduleProgress(4L, nextItemNumber = 544))
        val today = LocalDate.now()
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 4L, date = today.minusDays(1).toString(), episodeNumber = 542))
        db.computedScheduleWatchLogDao().insert(ComputedScheduleWatchLog(habitInstanceId = 4L, date = today.toString(), episodeNumber = 543))
        val viewModel = ComputedScheduleStatsViewModel(TestAppContainer(db))

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(2, state.streak)
        assertEquals(2, state.totalCount)
        assertEquals(setOf(today.minusDays(1), today), state.completedDates)

        db.close()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.ComputedScheduleStatsViewModelTest"`
Expected: FAIL — `ComputedScheduleStatsViewModel` doesn't exist yet (compile error).

- [ ] **Step 3: Write the ViewModel implementation**

`app/src/main/java/com/ziv/reminders/ui/dashboard/ComputedScheduleStatsViewModel.kt`:
```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.CPP_WEEKLY_HABIT_INSTANCE_ID
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.ui.activity.ActivitySectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Added per the Scope Revision (see the section below the CEO Phase 1 header). Deliberately reuses
 * ActivitySectionState/HabitStatsSummary (ui/activity/ActivityScreen.kt) rather than defining a
 * parallel data class — Exercise/Reading/Tanakh's stats already share this exact
 * streak/totalCount/completedDates shape, and this kind's stats are the same shape too (a
 * Set<LocalDate> of watched days), so a new type would just be a rename.
 *
 * Deliberately its own small ViewModel over DashboardDataSource, not a 4th section folded into
 * ActivityViewModel/ActivityDataSource — that combined ViewModel/interface already carries
 * Exercise/Reading/Tanakh-specific concerns (comboStreakThisWeek, per-session delete, etc.) this
 * kind has no equivalent of; DashboardDataSource already exposes everything this screen needs
 * (habitInstanceDao, computedScheduleRepository, habitEngine) with no interface change required.
 */
class ComputedScheduleStatsViewModel(private val dataSource: DashboardDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivitySectionState())
    val uiState: StateFlow<ActivitySectionState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val instance = dataSource.habitInstanceDao.getById(CPP_WEEKLY_HABIT_INSTANCE_ID) ?: return@launch

            val dates = HabitStats.parseDates(dataSource.computedScheduleRepository.completedDates(instance))
            val streak = dataSource.habitEngine.currentStreak(instance, today)

            _uiState.value = ActivitySectionState(streak, HabitStats.totalCount(dates), dates)
        }
    }

    companion object {
        fun factory(dataSource: DashboardDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = ComputedScheduleStatsViewModel(dataSource) as T
            }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.ComputedScheduleStatsViewModelTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Write the stats screen and wire the nav destination**

`app/src/main/java/com/ziv/reminders/ui/dashboard/ComputedScheduleStatsScreen.kt` — mirrors
`TanakhStatsScreen.kt` exactly, minus the day-tap-to-undo dialog (this kind's watch log is an
append-only history with no per-date "undo" concept, unlike Tanakh's `entriesMarkedRead`, so
`HeatmapGrid`'s `onDayClick` is a no-op here — matching the design intent of "heatmap + streak,"
not a full day-edit feature no other part of this plan asked for):
```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ziv.reminders.ui.activity.EmptySectionState
import com.ziv.reminders.ui.activity.HabitStatsSummary
import com.ziv.reminders.ui.activity.HeatmapGrid
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComputedScheduleStatsScreen(viewModel: ComputedScheduleStatsViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.uiState.collectAsState()
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("C++ Weekly") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            HabitStatsSummary("C++ Weekly", state)
            if (state.completedDates.isEmpty()) {
                EmptySectionState()
            } else {
                // onDayClick is a no-op — this kind's watch log has no per-date undo/edit concept
                // (see this step's intro comment), unlike Tanakh's tap-today-to-undo or Reading's
                // tap-to-review-sessions. A future TODOS.md candidate (not proposed here, no user
                // request for it) could show which episode was watched on a given day.
                HeatmapGrid(dates = state.completedDates, today = today, onDayClick = {})
            }
        }
    }
}
```

`app/src/main/java/com/ziv/reminders/MainActivity.kt` — three changes:

1. Add the import:
```kotlin
import com.ziv.reminders.ui.dashboard.ComputedScheduleStatsScreen
import com.ziv.reminders.ui.dashboard.ComputedScheduleStatsViewModel
```

2. Instantiate the new ViewModel alongside the existing three, and pass the new callback into
   `DashboardScreen`:
```kotlin
                val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
                val exerciseViewModel: ExerciseViewModel = viewModel(factory = ExerciseViewModel.factory(container))
                val activityViewModel: ActivityViewModel = viewModel(factory = ActivityViewModel.factory(container))
                val computedScheduleStatsViewModel: ComputedScheduleStatsViewModel =
                    viewModel(factory = ComputedScheduleStatsViewModel.factory(container))

                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") {
                        LaunchedEffect(Unit) { dashboardViewModel.refresh() }
                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            onOpenExercise = { navController.navigate("exerciseCounter") },
                            onOpenActivity = { navController.navigate("activity") },
                            onOpenExerciseStats = { navController.navigate("exerciseStats") },
                            onOpenReadingStats = { navController.navigate("readingStats") },
                            onOpenTanakhStats = { navController.navigate("tanakhStats") },
                            onOpenCppWeeklyStats = { navController.navigate("cppWeeklyStats") },
                        )
                    }
```

3. Add the new destination (place after `"tanakhStats"`):
```kotlin
                    composable("cppWeeklyStats") {
                        ComputedScheduleStatsScreen(
                            viewModel = computedScheduleStatsViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
```

(`container` already implements `DashboardDataSource` — no `AppContainer.kt` change is needed for
this task; that wiring was already completed in Tasks 1 and 4.)

- [ ] **Step 6: Run the full suite and build**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/ComputedScheduleStatsViewModel.kt app/src/main/java/com/ziv/reminders/ui/dashboard/ComputedScheduleStatsScreen.kt app/src/main/java/com/ziv/reminders/MainActivity.kt app/src/test/java/com/ziv/reminders/ui/dashboard/ComputedScheduleStatsViewModelTest.kt
git commit -m "feat: add the C++ Weekly long-press Statistics screen"
```

---

### Task 7: Seed the C++ Weekly habit instance

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt`
- Modify: `app/src/main/java/com/ziv/reminders/RemindersApp.kt`

**Interfaces:**
- Produces: `CPP_WEEKLY_HABIT_INSTANCE_ID = 4L`; `ensureHabitsSeeded` gains a second parameter
  (`computedScheduleProgressDao: ComputedScheduleProgressDao`) and now also seeds the C++ Weekly
  `ComputedSchedule` instance, including its starting position row.

No manifest changes — `HabitScheduler`/`HabitReminderReceiver`/`BootReceiver`/`RolloverReceiver`
already iterate every `HabitInstance` generically by id, and this kind has no crash-recoverable
"active session" state to reconcile at startup. This is the "zero new classes per new instance"
success criterion in its purest form for the *code* side — but unlike every prior kind, this one
instance also needs one extra data write (the starting `nextItemNumber`) beyond the
`HabitInstance` row itself, because `543` is real business data, not a universal default like
`ScheduleCursorProgress`'s `cursorIndex = 0`.

**`anchorDate` is intentionally left unresolved** (design doc Open Questions: "Exact calendar date
episode 542 released on — needed to seed `anchorDate` correctly; must be supplied at
implementation time, not guessed"). It is written here as `TODO(...)` — see Global Constraints for
why this is a deliberate hard-crash placeholder, not a soft default.

**Blast-radius isolation (added per the Final Approval Gate — flagged independently by all 3
/autoplan review phases):** the C++ Weekly instance's seed call is now wrapped in its own
`try`/`catch (e: Throwable)` — deliberately `Throwable`, not `Exception`, since `TODO()` throws
`NotImplementedError` (an `Error`). If it throws, no `HabitInstance` row is ever written for C++
Weekly, which means the dashboard simply doesn't render a 4th row at all — no "broken row" UI is
needed, since `DashboardViewModel` only ever queries and renders instances that actually exist in
`habit_instance`. Exercise/Reading/Tanakh's own `insertIfAbsent` calls run first and are
unaffected either way.

- [ ] **Step 1: Seed the C++ Weekly habit instance**

Full `app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt`:
```kotlin
package com.ziv.reminders.data

import android.util.Log

const val EXERCISE_HABIT_INSTANCE_ID = 1L
const val READING_HABIT_INSTANCE_ID = 2L
const val TANAKH_HABIT_INSTANCE_ID = 3L
const val CPP_WEEKLY_HABIT_INSTANCE_ID = 4L

/**
 * Idempotent — safe to call on every app startup (RemindersApp.onCreate). insertIfAbsent's
 * IGNORE conflict strategy means a row already present is left untouched, so this is how a
 * future habit instance gets added too: one more insertIfAbsent call here, no UI.
 *
 * The C++ Weekly (ComputedSchedule) instance needs a second write beyond its HabitInstance row:
 * computedScheduleProgressDao.insertIfAbsent seeds the starting nextItemNumber exactly once,
 * using the same IGNORE-on-conflict idempotency as every other insertIfAbsent call in this
 * function — a later reseed on subsequent app starts must never reset the user's tap progress
 * back to 543.
 */
suspend fun ensureHabitsSeeded(dao: HabitInstanceDao, computedScheduleProgressDao: ComputedScheduleProgressDao) {
    dao.insertIfAbsent(
        HabitInstance(
            id = EXERCISE_HABIT_INSTANCE_ID,
            kind = HabitKind.COUNTER.name,
            name = "Exercise",
            enabledDaysMask = 0b1111111,
            notificationTitle = "Reminders",
            notificationBody = "Don't forget your exercises today!",
            counterGoal = 5,
        )
    )
    dao.insertIfAbsent(
        HabitInstance(
            id = READING_HABIT_INSTANCE_ID,
            kind = HabitKind.TIMER.name,
            name = "Reading",
            enabledDaysMask = 0b0011111, // Sun-Thu, matching ReadBook's actual default
            notificationTitle = "Reminders",
            notificationBody = "15 minutes of reading today?",
            counterGoal = null,
            timerTargetSeconds = 900, // 15 minutes, matching ReadBook's actual default
        )
    )
    dao.insertIfAbsent(
        HabitInstance(
            id = TANAKH_HABIT_INSTANCE_ID,
            kind = HabitKind.SCHEDULE_CURSOR.name,
            name = "Tanakh",
            enabledDaysMask = 0b0011111, // Sun-Thu, matching the schedule CSV's own cadence
            notificationTitle = "Reminders",
            notificationBody = "Time for today's Tanakh reading?",
            counterGoal = null,
        )
    )
    // Isolated in its own try/catch (Final Approval Gate decision — flagged independently by
    // all 3 /autoplan review phases): anchorDate is a TODO() placeholder until a human fills in
    // episode 542's real release date, so this one instance's seeding can fail without taking
    // down Exercise/Reading/Tanakh above, which have nothing to do with this new row's config.
    // Catches Throwable, not Exception — TODO() throws NotImplementedError, which is an Error.
    try {
        dao.insertIfAbsent(
            HabitInstance(
                id = CPP_WEEKLY_HABIT_INSTANCE_ID,
                kind = HabitKind.COMPUTED_SCHEDULE.name,
                name = "C++ Weekly",
                enabledDaysMask = 0b1111111, // every day — a new episode can be watched any day of the week
                notificationTitle = "Reminders",
                notificationBody = "New C++ Weekly episode ready to watch?",
                counterGoal = null,
                anchorItemNumber = 542,
                // TODO(human): replace with episode 542's actual release date, ISO-8601 (yyyy-MM-dd),
                // before installing — see the design doc's Open Questions; do not guess this value.
                anchorDate = TODO("Fill in episode 542's real release date (yyyy-MM-dd) before building"),
                intervalDays = 7,
            )
        )
        computedScheduleProgressDao.insertIfAbsent(
            ComputedScheduleProgress(habitInstanceId = CPP_WEEKLY_HABIT_INSTANCE_ID, nextItemNumber = 543)
        )
    } catch (e: Throwable) {
        // No HabitInstance row means the dashboard simply won't render a 4th row at all — no
        // "broken row" UI needed, since DashboardViewModel only queries/renders instances that
        // actually exist. This log line is this function's only logging call in the whole app
        // (see CEO Section 8 — this codebase otherwise has zero logging infra); justified here
        // specifically because this catch's only real-world trigger is a developer forgetting to
        // fill in anchorDate, and a silent no-op with no trace at all would be worse than a
        // one-line signal in logcat.
        Log.e("HabitSeeding", "Failed to seed C++ Weekly instance — row unavailable until fixed", e)
    }
}
```

- [ ] **Step 2: Wire the new DAO parameter through `RemindersApp.kt`**

In `app/src/main/java/com/ziv/reminders/RemindersApp.kt`, change:
```kotlin
                ensureHabitsSeeded(container.habitInstanceDao)
```
to:
```kotlin
                ensureHabitsSeeded(container.habitInstanceDao, container.computedScheduleProgressDao)
```
(No other line in this file changes — `container.computedScheduleProgressDao` already exists as
of Task 1.)

- [ ] **Step 3: Run the full suite and build**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green — no test calls `ensureHabitsSeeded` directly, so the `TODO()`
placeholder does not affect the unit test suite; it only fires at real app startup)

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (`TODO()` type-checks as `Nothing`, which satisfies the `String?`
parameter — this compiles cleanly; it only throws at runtime)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt app/src/main/java/com/ziv/reminders/RemindersApp.kt
git commit -m "feat: seed the C++ Weekly ComputedSchedule habit instance"
```

---

### Task 8: On-device manual verification

Not a code task — no commit. Robolectric can't exercise real notification firing across the
9am-1pm reminder window, and this task's `TODO()` placeholder makes an on-device install
impossible until it's resolved by hand.

- [ ] **Before installing:** open `app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt` and
  replace the `TODO("Fill in episode 542's real release date...")` call with the real ISO-8601
  date string (`"yyyy-MM-dd"`) episode 542 of C++ Weekly actually released on. Do not guess — if
  the date isn't known with confidence, look it up before proceeding. Re-run
  `./gradlew.bat :app:testDebugUnitTest` and `./gradlew.bat :app:assembleDebug` once more after
  this edit (both should already be green/successful, since neither exercises this codepath, but
  confirm nothing else broke) and commit this one-line fix separately
  (`git commit -m "fix: set episode 542's real anchorDate"`) before installing.

Then install (`./gradlew.bat :app:installDebug`) and confirm:

- [ ] Fresh install / update: dashboard shows Exercise, Reading, Tanakh, and C++ Weekly — all four
  rows always visible regardless of day (per the existing "always show every row" behavior). C++
  Weekly shows "Episode 543" (or whatever `nextItemNumber` currently is), dot color reflecting
  whether that episode has released yet.
- [ ] If episode 543 hasn't released yet as of today: dot is green, row's right-side text reads
  "Waiting", and tapping the row does nothing at all (no state change, confirm via a second
  dashboard refresh that `nextItemNumber` is unchanged, and confirm no Snackbar appears either).
- [ ] If episode 543 has released (dot orange, "New!"): tap the row — `nextItemNumber` advances by
  exactly one, row immediately updates to "Episode 544", dot recomputes, and a Snackbar reads
  "Marked episode 543 watched" (no "Undo" button, unlike Exercise's/Tanakh's snackbars — Final
  Approval Gate decision).
- [ ] Simulate falling behind (e.g. temporarily set the device clock forward, or reason from the
  seeded anchor/interval): dot turns red once more than one release is overdue, right-side text
  reads "N behind" in the error color. Tap once — `nextItemNumber` advances by exactly 1, not by
  the full backlog.
- [ ] Long-press the C++ Weekly row: a "Statistics" menu appears (updated per the Scope Revision —
  this kind's long-press now matches Exercise/Reading/Tanakh's rows exactly, not the plan's
  original "confirm nothing happens" checklist item). Tap "Statistics" — the new stats screen
  opens, showing a streak count and a heatmap.
- [ ] On the stats screen: tap "mark next watched" on the dashboard a few times across different
  days (or seed a few watch-log rows directly), back out, and confirm the heatmap shows a filled
  cell for each watched day and the streak number matches the consecutive-day count. Confirm the
  back button returns to the dashboard.
- [ ] Force-stop the app after tapping, relaunch: dashboard reflects the same `nextItemNumber`
  (persisted immediately).
- [ ] An hourly reminder fires only while `dueCount >= 1` (something new to watch) and is
  suppressed once `dueCount == 0` (nothing released yet) or immediately after tapping.
- [ ] Reboot: `BootReceiver`'s self-heal doesn't crash on the new instance (confirm no new
  exceptions in logcat mentioning `COMPUTED_SCHEDULE` or `computed_schedule`).
- [ ] **Blast-radius isolation check** (Final Approval Gate decision): temporarily reintroduce the
  `TODO(...)` in `HabitSeeding.kt`, clear app data, and relaunch. Confirm the app does NOT crash —
  Exercise, Reading, and Tanakh still render and work normally; only the C++ Weekly row is simply
  absent from the dashboard. Confirm `adb logcat` shows exactly one `HabitSeeding` error line
  (`Failed to seed C++ Weekly instance...`) and no `FATAL EXCEPTION`. Then restore the real
  `anchorDate` value and re-verify the row reappears correctly on the next launch.
- [ ] Sweep `adb logcat` across the whole session for `FATAL EXCEPTION`/`AndroidRuntime.*Exception`
  → zero matches (beyond the expected pre-fix `NotImplementedError`, which must not appear at all
  once the `anchorDate` TODO has been resolved).

Once all boxes are checked, update `.superpowers/sdd/progress.md` to record this plan's
completion, matching prior plans' precedent.

---

# /autoplan Review

## Scope Revision (mid-review)

**What happened:** After the CEO/Design/Eng review phases below were written, the user came back
and explicitly requested: "on long press, there should be the option to get the statistics." Asked
to clarify scope (a minimal "just show the episode count" vs. a full watch-date log with a real
heatmap/streak, matching Exercise/Reading/Tanakh), the user explicitly chose **"Full watch-date
log."**

**What this supersedes:** Everything below this note was written when v1's scope deliberately
excluded a per-episode watch-date log and a stats screen — that was a genuine, considered decision
at the time (see the design doc's Open Questions), not an oversight. This revision **replaces**
that decision, not silently, but by superseding these specific prior statements:
- **Global Constraints** (top of this file): "v1 has no per-episode watch-date log and no stats
  screen... `ComputedScheduleHabitRow`'s long-press is therefore omitted entirely" — already
  corrected in place to point here.
- **CEO Phase 1, "NOT in scope"** (below): "Per-episode watch-date log / stats screen — deferred
  per design doc Open Questions" — no longer true; superseded by this note.
- **Design Phase 2, Pass 7 / Step 0.5 finding #3** ("Long-press affordance... auto-decided: not a
  real gap... intentional, already-approved v1 scope decision") — that finding was correct
  *at the time it was written*; the user has since reopened and reversed that exact decision. It
  is not deleted below (it's a real record of what the independent design subagent found and why
  it was auto-decided then), but it no longer describes this plan's actual scope.
- Task counts, file counts, test counts, and implementation-detail descriptions quoted throughout
  the CEO/Design/Eng review prose below (e.g. "16 main files," "18 tests," "all 7 tasks," any bare
  "Task 6"/"Task 7" reference, or the Eng Phase Section 3 code-path tree's "currentStreak() —
  always returns 0" line) reflect the plan **as it stood at review time**, before this revision.
  They are left as-is (a faithful record of what was reviewed) rather than hunted down and rewritten
  throughout — this note is the single pointer to read them as pre-revision. Every
  forward-looking, implementer-facing part of the plan (Global Constraints, File Structure, the
  numbered Tasks themselves) has been updated to the current, correct numbering (**8 tasks**, Task
  6 = the new stats screen, Task 7 = seeding (formerly Task 6), Task 8 = on-device verification
  (formerly Task 7)). This plan has no separately-named "Success Criteria"/"Distribution Plan"
  section to correct — those don't exist as standalone sections here; the closest equivalents
  (Global Constraints' "Do not touch ScheduleEntry.kt..." bullet, the CEO Phase's "NOT in scope"
  list, Task 8's on-device checklist) are each corrected at their own location instead.

**What actually changed in the plan** (see each task for the full diff):
- **Task 1:** a new append-only log table, `computed_schedule_watch_log` (one row per watch
  event: id, habitInstanceId, date, episodeNumber — mirrors `ScheduleCursorDailyProgress`'s role as
  the streak signal, but `ReadingSessionLog`'s autoincrement-id shape, since a watch is a discrete
  event, not a per-date aggregate), its DAO, and DAO/migration tests — folded into the *same*
  `MIGRATION_6_7`, not a new migration, since this plan was still unimplemented when the revision
  landed.
- **Task 3:** `ComputedScheduleRepository.currentStreak` now computes a real value via `HabitStats`
  instead of hardcoding 0; `markNextWatched` inserts a watch-log row in the same transaction as the
  progress upsert; a new `completedDates` method feeds the stats screen's heatmap.
- **Task 5:** `ComputedScheduleHabitRow` gets the same `RowLongPressMenu`/"Statistics" entry every
  other row has.
- **Task 6 (new):** `ComputedScheduleStatsScreen` + `ComputedScheduleStatsViewModel` + a
  `"cppWeeklyStats"` `NavHost` destination — heatmap + streak, mirroring `TanakhStatsScreen`.
- One thing this revision explicitly does **not** reopen: CEO 0D's "Delight idea 2" (long-press
  opens the C++ Weekly YouTube channel directly, deferred to TODOS.md) is a *different* feature
  from what was added here — the long-press action added by this revision opens an in-app
  Statistics screen (matching every other row's precedent), not a YouTube deep link. That TODOS.md
  item stays deferred, untouched by this revision.

---

## Phase 1: CEO Review (Strategy & Scope)

Mode: **SELECTIVE EXPANSION** (feature enhancement on an existing system — /autoplan's context-dependent default; not overridden by the user).

### 0A. Premise Challenge

Premises 1-4 were established and user-confirmed in the prior `/office-hours` session (2026-07-25); premise 5 (anchor config as `HabitInstance` columns, not a separate table) was newly surfaced while writing this plan. All five were re-confirmed via the CEO premise gate this session — no revisions. Is this the right problem? Yes — it's a narrow, fully-specified personal utility (a dashboard reminder for a fixed-cadence YouTube series), not a proxy for a larger unsolved problem. What happens if we do nothing: the user keeps track of the next C++ Weekly episode manually/mentally — a real, if minor, friction point, not hypothetical (it's the literal reason this session started).

### 0B. Existing Code Leverage

Every sub-problem maps to existing code, none rebuilt from scratch:
| Sub-problem | Existing code leveraged |
|---|---|
| "Position that advances one step per user action" | `ScheduleCursorProgress.cursorIndex` pattern (single-row-per-instance position table) |
| "Due/behind/waiting status derivation" | `ScheduleCursorStatus`'s `dueCount`/`isDueToday` shape, `DashboardScreen.kt:413-424` dot-color logic |
| "Per-instance config distinct from shared kind behavior" | `HabitInstance.counterGoal`/`timerTargetSeconds` nullable-column precedent |
| "New habit kind end-to-end wiring" | The exact `HabitKind`/`HabitStatus`/`*Repository`/`HabitEngine`/`AppContainer`/`HabitSeeding` extension points `ScheduleCursor` (2026-07-16) and `Timer` (2026-07-15) already established |
| "Row rendering with icon + status dot" | `ScheduleCursorHabitRow`'s exact `[Icon][Dot][Column]` inner-Row structure (2026-07-24 icons plan) |

Nothing here is rebuilding an existing flow in parallel — it's the fourth application of an already-proven extension pattern.

### 0C. Dream State Mapping

```
CURRENT STATE                         THIS PLAN                            12-MONTH IDEAL
3 habit kinds (Counter,      --->     4th kind: ComputedSchedule  --->     Kind/instance split proven across
Timer, ScheduleCursor),                (pure date-arithmetic                every shape of "thing to track" —
each hand-built as a                   schedule, no persisted               daily tally, timer, finite reading
distinct extension of the              per-item table) — anchor            plan, AND infinite fixed-cadence
same HabitKind/HabitStatus/            config lives as instance            release. Adding a 5th kind (if
Repository/HabitEngine                 data, so a second weekly-           ever needed) is a known, bounded
seam.                                  release row costs zero new          amount of work, not a design
                                        code, just a seed row.              question.
```

This plan moves toward the ideal: it's additive proof that the kind/instance seam generalizes beyond "things with daily state" to "things gated by an external clock," without forcing that generalization prematurely (Premise 1 — this stays a single named row, not a framework).

### 0C-bis. Implementation Alternatives

Already produced and decided in the prior `/office-hours` session (same three approaches: single-purpose hardcoded / parameterized ComputedSchedule kind / reuse ScheduleCursor's table mechanism — see the design doc's "Approaches Considered"). User chose **Approach B (parameterized)** with explicit reasoning (avoids the "reseed forever" trap of a finite table for a series with no end date). Per /autoplan's auto-decide rule (P1, highest completeness) this matches the same conclusion independently — Approach B has equal implementation cost to Approach A but strictly more completeness (no future rework needed for a second instance) — so no re-litigation needed; carried forward as-is.

### 0D. Mode-Specific Analysis (SELECTIVE EXPANSION)

**Complexity check:** The plan touches 16 main-source files and adds ~3 new classes/interfaces (`ComputedScheduleProgress`, `ComputedScheduleProgressDao`, `ComputedScheduleRepository`) plus 2 new pure functions — mechanically over the skill's ">8 files / >2 new classes" smell threshold. **Auto-decided (P3 pragmatic, P4 DRY): not a real smell here.** Every prior "new habit kind" plan in this repo (Timer, ScheduleCursor) touched the identical file set for the identical reason — Kotlin's single-compilation-unit constraint means every exhaustive `when` over `HabitKind`/`HabitStatus` must gain a branch in the same commit. This is the established minimum viable footprint for this specific class of change, not scope creep. Reducing further would mean reopening Approaches A/C, both already correctly rejected in office-hours.

**Minimum set of changes:** Already minimal — Task 7 (on-device manual verification) is the only non-code task, and no task is deferrable without leaving the feature half-wired (e.g., skipping the `HabitReminderReceiver` compile-fix breaks the build entirely, it's not optional polish).

**Expansion scan (candidates only — not yet added to scope):**
- *10x check:* Auto-fetch real episode data from a YouTube API instead of trusting a fixed Tuesday cadence (catches schedule slips, hiatus weeks). **Not pursued** — directly contradicts already-agreed Premise 2/design-doc Constraints (no network calls; personal single-device app trusts the cadence). Named for completeness, not proposed.
- *Delight idea 1:* Show "N episodes behind" numeric text on the row, matching Tanakh's existing "chapters-behind count" precedent (commit `5ef25a3`) instead of color-only signaling. **TASTE DECISION** — surfaced at the final gate (changes what text renders on-screen, a real product choice, not free to silently add back after the design doc scoped the row to "Episode $nextItemNumber" only).
- *Delight idea 2:* Long-press opens the C++ Weekly YouTube channel/episode (Android `Intent.ACTION_VIEW`). Small effort, but **directly reopens a premise the user already explicitly closed** (design doc: "long-press excluded entirely for v1, no stats to show") — a long-press action isn't a stats menu, but it's still new long-press behavior on a row explicitly speced to have none. **Deferred to TODOS.md**, not silently added, precisely because it reverses an explicit prior decision.
- *Delight idea 3:* A notification action button ("Mark watched") directly in the push notification, skipping the need to open the app. No existing precedent for this on any other kind's notification (`HabitReminderReceiver` only ever opens the app on tap, per the existing generalized flow) — genuinely new work, not a 30-minute touch. **Deferred to TODOS.md.**
- *Platform potential:* Already realized by Approach B itself (anchor-as-data) — no additional platform work needed; a second weekly-release row is already zero-cost. Named in 0C, not a new scope item.

### 0E. Temporal Interrogation

```
HOUR 1 (foundations):    Room schema v6→v7, HabitKind/HabitInstance columns — needs the exact
                         current AppDatabase.kt version (confirmed v6 by reading the file, not
                         assumed) and the exact current migration chain (5 prior migrations).
HOUR 2-3 (core logic):   The dueCount/isDueToday formula's edge behavior at exact release-date
                         boundaries (today == releaseDate vs today == releaseDate + intervalDays)
                         — already pinned down with explicit test cases in Task 2, not left
                         ambiguous for the implementer to guess.
HOUR 4-5 (integration):  Every exhaustive `when` over HabitKind/HabitStatus needing a new branch —
                         already enumerated by file+line in Task 3/4/5 rather than a "grep for it"
                         hand-wave (this was flagged during the design-doc review and fixed).
HOUR 6+ (polish/tests):  The anchorDate placeholder's TODO()-crash behavior — already an explicit,
                         documented decision (Global Constraints), not something the implementer
                         would discover by surprise mid-build.
```
(Human-team estimate: ~6-8 hours across all 7 tasks. With CC + gstack: ~45-60 minutes, most of it Task 1's schema/migration test scaffolding.)

No new ambiguities surfaced beyond what's already resolved in the plan — this section confirms the plan already answers what it needs to, rather than finding new gaps.

### 0F. Mode Selection

**SELECTIVE EXPANSION** (fixed per /autoplan's context-dependent default for "feature enhancement on existing system"). Approach B (from 0C-bis) applies under this mode — already the ideal-architecture pick, not superseded by expansion.

### Step 0.5: Dual Voices

**Codex:** unavailable (binary not found on this machine) — tagged `[codex-unavailable]` for this phase.

**Claude CEO subagent** (independent, no prior context): dispatched against the plan file in isolation.

CEO DUAL VOICES — CONSENSUS TABLE:
```
═══════════════════════════════════════════════════════════════
  Dimension                           Claude  Codex  Consensus
  ──────────────────────────────────── ─────── ─────── ─────────
  1. Premises valid?                   Concern N/A    Flagged (subagent-only) — cadence-drift risk
  2. Right problem to solve?           Yes*    N/A    Yes, with a named alternative not fully ruled out
  3. Scope calibration correct?        Yes     N/A    Yes
  4. Alternatives sufficiently explored?Partial N/A    Partial — "build nothing" alternative unaddressed
  5. Abandonment risk (reframed)       Concern N/A    Flagged — blast-radius + fragile-premise combo
  6. 6-month trajectory sound?         Concern N/A    Flagged — whole-app crash blast radius
═══════════════════════════════════════════════════════════════
Codex unavailable this session — tagged [subagent-only], not [codex+subagent].
```

**Subagent findings** (independent cold read, full text preserved):

1. **"Right problem?"** — Notes YouTube's own subscription-bell notification already answers "when did a new episode drop," for free, and the plan never records whether that reframe was considered. **Auto-decided (P3 pragmatic), not re-litigated as a user question**: this app's entire reason to exist (Exercise/Reading/Tanakh, all pre-existing) is centralizing personal tracking into one dashboard instead of scattered external tools/notifications — the same reasoning applies here, and the user already chose "add it to my dashboard" over "just use YouTube" during `/office-hours`. Noted for completeness, not reopened.

2. **Domain-premise risk (real finding, NOT auto-decided):** "Every future episode's release date [is] fully computable" is an unverified assumption about a real external channel's cadence — holidays/hiatuses/schedule changes happen to every weekly show eventually. If C++ Weekly skips a week, `nextItemNumber`'s computed release date silently diverges from reality forever, with no drift-detection and no correction path except direct SQL editing (no admin UI exists or is planned). **Surfaced as a taste decision at the final gate** — this is exactly the kind of premise disagreement /autoplan's rules say must reach the user, not get auto-decided.

3. **Blast-radius finding (real finding, NOT auto-decided):** Task 6's `anchorDate = TODO(...)` is evaluated during `ensureHabitsSeeded`, which seeds *every* habit instance in one pass at `RemindersApp.onCreate` — so an unfilled/wrong `anchorDate` crashes the *entire app* (all four rows: Exercise, Reading, Tanakh, C++ Weekly), not just the new row, until fixed. The plan's own reasoning ("loud crash beats silently wrong data") is sound, but was never weighed against a cheaper isolation (only this row fails/shows an error state; the other three keep working). **Surfaced as a taste decision at the final gate.**

4. **Alternatives:** only "computed dates vs. persisted finite table" was evaluated in-plan; "build nothing, rely on external tooling" wasn't addressed in the implementation plan itself (it was addressed in the upstream `/office-hours` design doc, which the subagent didn't have access to — noted here for completeness, no action needed).

5. **What's solid** (subagent's own words): "reuse of the existing kind/instance/repository pattern is consistent with the rest of the codebase, TDD discipline is real, and the migration itself is safe (additive columns, no destructive fallback). The risk is entirely in the domain premise and blast-radius design, not the Kotlin/Room implementation."

## Review Sections 1-11

### Section 1: Architecture Review

```
DEPENDENCY GRAPH (new components, relative to existing):

  HabitInstance (+3 nullable cols)         HabitKind (+1 case: COMPUTED_SCHEDULE)
         │                                          │
         └──────────────┬───────────────────────────┘
                         ▼
              ComputedScheduleRepository ──uses──▶ ComputedScheduleProgressDao ──▶ Room (v7)
                         │                                    │
                         │                          ComputedScheduleProgress (entity)
                         ▼
              deriveComputedScheduleStatus (pure fn, ComputedSchedule.kt)
                         │
                         ▼
              HabitStatus.ComputedScheduleStatus
                         │
              ┌──────────┼──────────────────┐
              ▼                              ▼
      HabitEngine (4th branch)      HabitReminderReceiver (4th when-branch, compile-fix)
              │
              ▼
      DashboardViewModel ──▶ DashboardScreen.HabitRow (4th dispatch branch, new
                              ComputedScheduleHabitRow composable, no long-press menu)
```

**Data flow shadow paths** (`ComputedScheduleRepository.todayStatus`):
- Nil: `instance.anchorItemNumber/anchorDate/intervalDays` all nullable — if any is null, `error(...)` throws `IllegalStateException` (uncaught). This path is only reachable if seeding wrote a partial row, which `HabitSeeding`'s single atomic `insertIfAbsent` call can't produce. Auto-decided (P3): acceptable — matches how no other repository in this codebase defensively re-validates its own seed invariants either.
- Empty: no collections in this kind's data shape — N/A.
- Error (upstream): Room read failure — same as every other repository call in `DashboardViewModel.refresh`, an app-wide pre-existing gap already logged in `TODOS.md` ("no error handling around Room reads in ViewModels", P3). Not a new gap this plan introduces.

**State machine** (enabled/disabled):
```
        releaseDate(nextItemNumber) > today          releaseDate(nextItemNumber) <= today
   ┌───────────────────────────────────┐      ┌──────────────────────────────────────┐
   │   WAITING (dot: green)            │─────▶│  DUE (dot: orange if dueCount==1,     │
   │   tap = no-op                     │ time  │  red if dueCount>1)                  │
   │                                    │passes│  tap = nextItemNumber += 1            │
   └───────────────────────────────────┘      └──────────────┬───────────────────────┘
                    ▲                                          │ tap
                    └──────────────────────────────────────────┘
                     (new nextItemNumber, new releaseDate — usually re-enters WAITING
                      unless already behind by 2+, in which case stays DUE)
```
No impossible transitions exist — `nextItemNumber` only ever increments, never resets or decrements.

**Coupling:** `HabitEngine` gains a 4th constructor dependency (`ComputedScheduleRepository`) — identical shape to its 3rd (`ScheduleCursorRepository`), no new coupling category introduced.

**Scaling / SPOF:** N/A — single-user, single-device, local Room DB, no network, no shared infrastructure. 10x/100x load has no meaning for a habit row tapped at most once a week by one person.

**Security architecture:** No new endpoints, no new data-access boundary — purely local state on-device. No auth/authz surface exists in this app at all.

**Production failure scenario:** The one realistic failure is the documented `anchorDate` `TODO()` crash (Global Constraints) — already an intentional, named decision, not a gap.

**Rollback posture:** Reinstalling an older APK build (pre-v7 schema) over a device that already ran v7 would fail Room's version check on open (no downgrade migration exists, matching every prior migration in this repo — none of them ship a reverse migration either). Auto-decided (P3, matches established precedent): acceptable for a personal single-device app — "clear app data" is the existing fallback for every schema rollback in this project's history, not a new gap. Logged in the Failure Modes registry below for visibility, not as a blocking finding.

**No issues requiring a user decision in this section** — findings above were auto-decided per precedent; nothing crosses the bar for a taste decision or user challenge.

### Section 2: Error & Rescue Map

```
  METHOD/CODEPATH                          | WHAT CAN GO WRONG                    | EXCEPTION CLASS
  ------------------------------------------|---------------------------------------|--------------------
  ComputedScheduleRepository#todayStatus    | anchorItemNumber/Date/intervalDays null| IllegalStateException (via error())
  ComputedScheduleRepository#markNextWatched| dueCount==0 (not yet released)         | (not an exception — explicit no-op guard)
  ComputedScheduleProgressDao#getByInstance | Room read I/O failure                  | android.database.sqlite.SQLiteException
  computeReleaseDate/deriveComputedScheduleStatus | none — pure functions, total over their input domain | —

  EXCEPTION CLASS            | RESCUED? | RESCUE ACTION                         | USER SEES
  ----------------------------|----------|----------------------------------------|------------------
  IllegalStateException       | N        | None — crash                          | App crash (intentional, see Global Constraints: "loud unmissable crash beats silently wrong guessed date")
  SQLiteException             | N        | None — app-wide pre-existing gap      | App crash (TODOS.md P3, not new to this plan)
```
No GAPS beyond the two already named and explicitly accounted for (one intentional, one pre-existing and already tracked). Auto-decided (P6 — pragmatic, no new work needed): neither warrants a new rescue path in this plan; the first is by design, the second is out of this plan's blast radius (app-wide ViewModel pattern, not specific to this repository).

### Section 3: Security & Threat Model

No new attack surface — no new endpoints, no new user input beyond a button tap, no new secrets, no new dependencies, no PII, no injection vectors (all values are either hardcoded at seed time or derived from `LocalDate.now()`). **Auto-decided: N/A, no findings** — evaluated and confirmed inapplicable, not skipped.

### Section 4: Data Flow & Interaction Edge Cases

```
  INTERACTION              | EDGE CASE                          | HANDLED? | HOW?
  --------------------------|--------------------------------------|----------|--------------------------------
  Tap row while due         | Double-tap in quick succession       | Y        | Second tap re-evaluates dueCount from the now-incremented nextItemNumber; if the backlog still has due episodes, advances again by 1 — correct, not a bug (see Task 3's "multipleReleasesBehind" test, which already covers repeated single-episode advancement)
  Tap row while disabled    | Tap fires anyway (race: UI stale)    | Y        | Repository's own `dueCount == 0` guard (Task 3) makes this a no-op regardless of what the UI thought
  App backgrounded mid-tap  | Process death before Room write commits| Y      | Room's `@Upsert` is a single atomic statement — no partial-write state possible
  Reboot                    | BootReceiver runs before seeding      | Y        | `ensureHabitsSeeded` already runs on every `RemindersApp.onCreate`, same as every other kind
```
No unhandled edge cases found.

### Section 5: Code Quality Review

Reviewed against the codebase's existing conventions (matches `ScheduleCursorRepository`'s shape exactly): naming is consistent (`nextItemNumber` mirrors `cursorIndex`'s role), no DRY violations (the plan explicitly reuses the dot-color/tap-guard *pattern* without duplicating `ScheduleCursorRepository`'s code), no over-engineering (rejected the generalized-framework and reused-table alternatives explicitly, per Premise 1), no under-engineering (defensive `dueCount==0` guard exists at the repository layer, not just the UI). Cyclomatic complexity: `deriveComputedScheduleStatus` has exactly one branch (if/else) — well under the 5-branch flag threshold. **No issues found.**

### Section 6: Test Review

```
NEW UX FLOWS:        Tap the C++ Weekly row (enabled / disabled)
NEW DATA FLOWS:       HabitInstance config → ComputedScheduleRepository → HabitStatus.ComputedScheduleStatus → DashboardScreen
NEW CODEPATHS:        computeReleaseDate, deriveComputedScheduleStatus (dueCount branches), markNextWatched (guard branch)
NEW BACKGROUND JOBS:  None — reuses existing HabitReminderReceiver/HabitScheduler unchanged
NEW INTEGRATIONS:     None — no external calls
NEW ERROR/RESCUE PATHS: IllegalStateException (intentional crash), SQLiteException (pre-existing app-wide gap)
```
Every item above already has a corresponding test in the plan (Tasks 1-3): DAO tests (4), migration test (1), pure-function tests (7, covering exact-boundary/one-interval-late/two-intervals-late), repository tests (6, covering no-progress-row fallback, persisted-progress reflection, no-op-when-not-due, advance-by-exactly-one, advance-by-exactly-one-even-when-behind, always-zero-streak). Test ambition check: the "multipleReleasesBehind_stillAdvancesByExactlyOneNotTheWholeBacklog" test is exactly the hostile-QA-engineer case (an obvious bug would be "advance by dueCount instead of 1"). Test pyramid: all 18 new tests are unit-level (Robolectric for Room, plain JUnit for pure functions) — appropriate, no integration/E2E layer exists in this codebase for any kind. Flakiness risk: none — every test uses fixed `LocalDate` values, no real-clock or ordering dependency. No LLM/prompt changes involved. **No gaps found.**

### Section 7: Performance Review

N+1 queries: N/A, no associations. Memory: one Int and one String per instance — negligible. Indexes: `computed_schedule_progress`'s primary key (`habitInstanceId`) is the only query path, already covered. Caching: N/A, computation is O(1) arithmetic. Background job sizing: N/A, no jobs. Slow paths: none — this is the cheapest kind in the app (no CSV parsing like Tanakh, no timer ticking like Timer). **Auto-decided: N/A, no findings.**

### Section 8: Observability & Debuggability Review

This app has no logging/metrics/tracing infrastructure for any kind today (confirmed — no `Log.d`/analytics calls exist in `ScheduleCursorRepository` or `CounterHabitRepository` either). Adding observability infra for one new kind while every existing kind has none would be inconsistent scope creep relative to this plan's blast radius. **Auto-decided (P4 DRY / P3 pragmatic): N/A for this plan** — matches the app's existing (zero) observability posture; not a gap this plan introduces or should fix in isolation.

### Section 9: Deployment & Rollout Review

Migration safety: `MIGRATION_6_7` is additive-only (3 nullable columns, 1 new table) — backward-compatible in the sense that no existing data is touched or dropped; addressed above under Section 1's rollback posture (no downgrade path, matching precedent). Feature flags: N/A, no staged rollout mechanism exists in this app (single developer, `installDebug` only). Rollout order: N/A. Deploy-time risk window: N/A, no concurrent old/new code (single device, one install at a time). Environment parity/smoke tests: covered by Task 7's on-device manual verification checklist, which already exists in the plan. **Auto-decided: no new findings beyond what Task 7 already covers.**

### Section 10: Long-Term Trajectory Review

Technical debt: none introduced — this follows established precedent exactly, no shortcuts taken. Path dependency: makes future kinds easier, not harder (proves the kind/instance seam generalizes to computed schedules). Knowledge concentration: the plan's inline doc comments (e.g., `ComputedSchedule.kt`'s explanation of why `dueCount`'s shape differs from `ScheduleCursorStatus`'s) are sufficient for a future reader — matches this repo's existing documentation density. Reversibility: 4/5 (easily reversible via `git revert` pre-merge; post-merge, reversible modulo the schema-downgrade caveat already named). Ecosystem fit: standard Room/Compose patterns, no exotic dependencies. The 1-year question: a new engineer (or future-you) reading this plan would find it obvious — it's the same shape as the two kind-additions before it. **No issues found.**

### Section 11: Design & UX Review

Information architecture: the row's single most important signal (dot color) is unchanged in priority from every other row — consistent hierarchy. Interaction states:
```
FEATURE            | LOADING | EMPTY                          | ERROR        | SUCCESS              | PARTIAL
C++ Weekly row      | N/A     | N/A (always seeded, always     | N/A (no      | Tap advances episode | N/A (no
                    | (sync   | shows a value once seeded)      | network)     | number, dot recolors | partial
                    | Room    |                                 |              |                       | states)
                    | read)   |                                 |              |                       |
```
User journey: unchanged from existing rows (glance at dot → tap if due). AI slop risk: none — this reuses the app's own established visual language exactly, not a generic pattern. DESIGN.md: no `DESIGN.md` exists in this repo (personal app, no formal design system doc) — N/A. Responsive: N/A, single-device Android app, one screen density target already established. Accessibility: `contentDescription = null` on the icon (per the icons plan's precedent, since the row's text already conveys the same info to a screen reader) — consistent, not a regression. **Real finding, flagged as TASTE DECISION** (carried from 0D's expansion scan): should the row show "N episodes behind" as visible text (matching Tanakh's precedent) or stay minimal (episode number only, color-only signal)? Surfaced at the final gate, not auto-decided, since it changes on-screen text.

Recommend `/plan-design-review` for a deeper design pass is **not necessary** here — this section found the UI scope to be a near-exact reuse of an already-reviewed pattern (the icons and status-dot plans already went through full design review), not new design surface.

## CEO Phase — Required Outputs

### "NOT in scope"
- ~~Per-episode watch-date log / stats screen — deferred per design doc Open Questions.~~
  **Superseded mid-review — see "## Scope Revision (mid-review)" above.** The user explicitly
  requested this after this CEO pass was written; it is now in scope (Tasks 1, 3, 5, 6).
- General "any weekly release series" framework — explicitly rejected, Premise 1.
- YouTube API integration to verify actual release dates — rejected, contradicts Premise 2 (personal offline app).
- Long-press YouTube deep-link, notification "mark watched" action button — both sent to TODOS.md (see below). (The long-press deep-link idea is still deferred/out of scope — distinct from the long-press *Statistics* menu the Scope Revision added; see that note's closing paragraph.)
- Cadence-drift correction UI — flagged as an open taste decision, not yet in or out of scope pending the final gate.

### "What already exists"
Covered fully in 0B above — every sub-problem maps to an established pattern from the Timer/ScheduleCursor kind additions; nothing is rebuilt in parallel.

### "Dream state delta"
This plan is a straight, additive step toward the 12-month ideal named in 0C: proof that the kind/instance seam handles "externally clocked, never-ending" tracking, not just daily/finite trackers — without prematurely generalizing (stays a single named row).

### Error & Rescue Registry
(Full table in Section 2 above.) 2 methods mapped, 0 new GAPS (both named exception paths are either intentional-by-design or an already-tracked, out-of-blast-radius app-wide TODO).

### Failure Modes Registry
```
  CODEPATH                              | FAILURE MODE                        | RESCUED? | TEST? | USER SEES?          | LOGGED?
  ---------------------------------------|----------------------------------------|----------|-------|----------------------|--------
  ComputedScheduleRepository#todayStatus | anchorDate TODO() unfilled            | N        | N/A   | Whole-app crash ← flagged as taste decision (blast radius) | N
  App schema rollback                    | Reinstall older APK over v7 DB        | N        | N/A   | App fails to open (matches every prior migration's precedent) | N
  Cadence drift                          | C++ Weekly skips/shifts a release week| N        | N/A   | Silent — row shows wrong episode number forever ← flagged as taste decision | N
```
Two rows above are genuine CRITICAL-GAP-shaped findings (RESCUED=N, USER SEES effectively silent-or-crash) — both already routed to the final gate as taste decisions rather than auto-decided, per /autoplan's rule that independent-voice premise/scope disagreements are never silently resolved.

### TODOS.md updates (proposed, not yet written — pending final-gate approval bundle)
1. **Long-press → open C++ Weekly's YouTube channel/episode.** Why: quick delight, lets the user jump straight to watching. Pros: cheap (Android `Intent.ACTION_VIEW`), matches "why not use YouTube directly" instinct. Cons: reopens a premise explicitly closed in the design doc (no long-press for this row in v1). Effort: S (human) / S (CC). Priority: P3. Depends on: none.
2. **Notification "Mark watched" action button.** Why: skip opening the app entirely to advance the counter. Pros: matches how a truly frictionless reminder should feel. Cons: no existing precedent in this codebase (every other kind's notification just opens the app) — genuinely new notification-action-button infrastructure, not a 30-minute touch. Effort: M (human) / S (CC). Priority: P3. Depends on: none.

### Diagrams produced
System architecture (Section 1), data-flow shadow paths (Section 1, prose form), state machine (Section 1), error flow (Section 2 table). Deployment sequence / rollback flowchart: N/A, no staged deployment exists for this app (noted in Section 9).

### Stale Diagram Audit
No existing ASCII diagrams in the files this plan touches (`HabitKind.kt`, `HabitStatus.kt`, `AppDatabase.kt`, etc. carry doc-comment prose, not diagrams) — nothing to check for staleness.

### Completion Summary
```
+====================================================================+
|            MEGA PLAN REVIEW — COMPLETION SUMMARY (CEO)             |
+====================================================================+
| Mode selected        | SELECTIVE EXPANSION                          |
| System Audit         | 16 main files, 3 new classes — matches       |
|                       | established per-kind-addition precedent      |
| Step 0               | Premises reconfirmed (5); Approach B carried |
|                       | forward from /office-hours                   |
| Section 1  (Arch)    | 0 blocking issues; 1 precedent-matched risk  |
|                       | (schema rollback) logged, not blocking       |
| Section 2  (Errors)  | 2 error paths mapped, 0 new GAPS             |
| Section 3  (Security)| 0 issues — N/A, no new attack surface        |
| Section 4  (Data/UX) | 4 edge cases mapped, 0 unhandled             |
| Section 5  (Quality) | 0 issues found                               |
| Section 6  (Tests)   | Diagram produced, 0 gaps (18 tests planned)  |
| Section 7  (Perf)    | 0 issues — N/A, O(1) arithmetic, no I/O hot  |
|                       | path                                         |
| Section 8  (Observ)  | 0 gaps — N/A, matches app's existing (zero)  |
|                       | observability posture                        |
| Section 9  (Deploy)  | 0 new risks beyond Section 1's rollback note |
| Section 10 (Future)  | Reversibility: 4/5, debt items: 0            |
| Section 11 (Design)  | 1 issue (taste decision: behind-count text)  |
+--------------------------------------------------------------------+
| NOT in scope         | written (5 items)                            |
| What already exists  | written                                       |
| Dream state delta    | written                                       |
| Error/rescue registry| 2 methods, 0 CRITICAL GAPS (both accounted)  |
| Failure modes        | 3 total, 2 routed to final gate as taste     |
|                       | decisions (cadence drift, blast radius)      |
| TODOS.md updates     | 2 items proposed                             |
| Scope proposals      | 3 candidates surfaced, 0 auto-added, 1       |
|                       | routed to gate, 2 deferred to TODOS.md       |
| CEO plan             | skipped — zero scope actually added to plan, |
|                       | nothing to persist beyond this review record |
| Outside voice        | ran (Claude subagent only — Codex unavailable)|
| Lake Score           | 8/9 auto-decisions chose the complete/       |
|                       | precedent-matching option (1 explicit crash- |
|                       | by-design exception, itself a completeness   |
|                       | choice over silent wrongness)                |
| Diagrams produced    | 4 (architecture, data flow, state machine,   |
|                       | error flow)                                  |
| Stale diagrams found | 0                                             |
| Unresolved decisions | 3 — routed to Final Approval Gate below       |
+====================================================================+
```

**PHASE 1 COMPLETE.** Codex: unavailable. Claude subagent: 3 concerns (2 routed to gate as taste decisions, 1 auto-decided as not-reopened). Consensus: 3/6 dimensions flagged by the independent voice, 3/6 clean. Passing to Phase 2 (Design Review).

## Phase 2: Design Review

Mockup generation (`gstack designer` binary) is not set up on this machine — proceeding as a text-based design review. This is appropriate regardless: the new row is a near-exact reuse of an already-shipped, already-mocked-on-device row layout (icon + status dot + text), not new visual surface requiring fresh exploration.

### Step 0: Design Scope Assessment

Initial rating: 8/10 (happy-path-complete; the one open item is the "N episodes behind" text decision, already logged in CEO Section 11). No `DESIGN.md` exists in this repo. Existing design leverage: 100% reuse of `ScheduleCursorHabitRow`'s inner-Row structure and dot-color convention (2026-07-23/24 plans).

### Step 0.5: Dual Voices

Codex: unavailable, tagged `[codex-unavailable]`.

**Claude design subagent** (independent, no prior context) — dispatched separately from the CEO/Eng subagents to preserve genuine independence.

DESIGN DUAL VOICES — LITMUS SCORECARD:
```
═══════════════════════════════════════════════════════════════
  Dimension                    Claude subagent   Codex   Consensus
  ────────────────────────────── ────────────────── ─────── ─────────
  Information hierarchy          Clean             N/A     N/A (subagent-only)
  Missing states                 2 real gaps       N/A     N/A
  User journey                   Minor friction    N/A     N/A
  Specificity vs. generic        Clean             N/A     N/A
  Ambiguity risk for implementer Low               N/A     N/A
═══════════════════════════════════════════════════════════════
Codex unavailable this session — tagged [subagent-only].
```

**Subagent findings** (independent cold read):

1. **First-launch crash blast radius — CROSS-PHASE THEME.** Independently found by both the CEO subagent (Phase 1) and this design subagent, with the identical mechanism: `ensureHabitsSeeded` seeds all 4 habits in one pass, so the `anchorDate = TODO(...)` placeholder crashes the *entire app* (Exercise/Reading/Tanakh included), not just the new row. Two independent voices converging on the same finding is a high-confidence signal. **Routed to the Final Approval Gate** (already logged once from the CEO phase — not duplicated as a separate decision).

2. **No tap feedback (real finding, NOT auto-decided).** Every other row that mutates on direct dashboard interaction gives Snackbar feedback — Exercise's inline increment has undo (`00f2b30`), Tanakh's `markRead` has a quick-undo snackbar (`63ebaa5`). This plan's `onMarkNextWatched` gives *zero* feedback — a silent state mutation, the only row in the app that would behave this way. The "no undo" decision (already made, due to no daily-progress table) is a separate question from "no acknowledgment at all." **Surfaced as a taste decision at the final gate**: add a simple non-undoable Snackbar ("Marked episode N watched") for consistency, or leave fully silent as currently planned.

3. **Long-press affordance (minor, auto-decided).** A user habituated to 3-of-4 rows' long-press-for-stats might long-press this row and get nothing. **Auto-decided (P3 pragmatic): not a real gap** — an unregistered long-press handler is standard, unremarkable Android behavior (there's no platform convention requiring an explicit "nothing here" affordance for absent long-press), and this was an intentional, already-approved v1 scope decision (no stats to show). Not routed to the gate. **Superseded mid-review — see "## Scope Revision (mid-review)" above the CEO Phase 1 header:** the user reopened and reversed this exact decision after this Design pass was written; long-press now opens a real Statistics screen (Task 6), so the friction this finding predicted no longer exists. Left here verbatim as an accurate record of the independent subagent's cold-read finding at the time, not deleted or edited in place.

4. **Journey friction during backlog catch-up (minor, informational).** Each tap advances by exactly 1 with no "2 of 3 caught up" progress indicator during a multi-episode backlog. Correct behavior, just not maximally delightful. Not a gap — no fix proposed, noted for completeness only.

### Passes 1-7

**Pass 1 — Information Architecture:** Dot color (status) reads first, exactly matching every other row's priority — no change to established hierarchy.

**Pass 2 — Interaction State Coverage:** (table already produced in CEO Section 11 — LOADING/EMPTY/ERROR/SUCCESS/PARTIAL). No gaps found beyond what CEO Section 11 already covers; not re-derived here.

**Pass 3 — User Journey & Emotional Arc:** Glance → tap if due → satisfaction of "one thing off my mental list." Identical arc to the 3 existing rows — no break in the journey introduced.

**Pass 4 — AI Slop Risk:** None. Row reuses this app's own established visual language verbatim (same icon size, same dot, same inner-Row spacing) rather than a generic pattern invented for this feature.

**Pass 5 — Design System Alignment:** No `DESIGN.md` exists — N/A, nothing to align against beyond the app's own established (undocumented but consistent) row convention, which this plan follows exactly.

**Pass 6 — Responsive & Accessibility:** N/A responsive (single-device app). Accessibility: `contentDescription = null` on the new icon matches the icons plan's precedent (row text already conveys the same info to a screen reader) — not a regression.

**Pass 7 — Unresolved Design Decisions:** One — the "N episodes behind" text question, already logged as a taste decision at CEO Section 11 / carried to the Final Approval Gate. Not duplicated here.

## Design Phase — Required Outputs

### "NOT in scope"
Fresh mockup exploration — not warranted; this is a reuse of an already-mocked, already-on-device-verified pattern, not new design surface.

### "What already exists"
The entire row visual language (icon + dot + text, 20/40dp icon sizing history, dot border-ring fix) — fully covered by the 2026-07-23 and 2026-07-24 plans, both already design-reviewed and shipped.

### TODOS.md updates
None beyond the 2 already proposed in the CEO phase (long-press deep-link, notification action button) — no new design-specific TODOs.

### Completion Summary
```
+====================================================================+
|           DESIGN REVIEW — COMPLETION SUMMARY                       |
+====================================================================+
| Initial rating        | 8/10                                       |
| Pass 1 (Info Arch)    | 0 issues                                   |
| Pass 2 (States)        | 2 real gaps found by subagent (blast      |
|                        | radius — cross-phase theme; no tap        |
|                        | feedback) — both routed to Final Gate     |
| Pass 3 (Journey)       | 0 breaks; 1 minor friction noted, no fix  |
| Pass 4 (AI Slop)       | 0 risk                                     |
| Pass 5 (Design System) | N/A, no DESIGN.md                          |
| Pass 6 (Responsive/A11y)| 0 issues                                  |
| Pass 7 (Unresolved)    | 2 (both carried to Final Gate)            |
| Dual voices            | ran (Claude subagent only)                |
+====================================================================+
```

**PHASE 2 COMPLETE.** Codex: unavailable. Claude subagent: 2 real findings (1 cross-phase theme, 1 new), both routed to gate; 2 minor items auto-decided as non-issues. Passing to Phase 3 (Eng Review).

## Phase 3: Eng Review

### Step 0: Scope Challenge

Already answered comprehensively in CEO Step 0B/0D — not re-derived here. Complexity check triggered mechanically (16 files, 3 new classes) but was already evaluated and auto-decided as matching established per-kind-addition precedent (P3/P4), not a real smell. Per /autoplan override, "Scope challenge: never reduce (P2)" — scope stands as-is, no reduction proposed.

Search check: this plan introduces no new architectural pattern, infrastructure component, or concurrency approach beyond what `ScheduleCursorRepository`/`ScheduleCursorProgress` already established in this exact codebase (2026-07-16) — no external search needed; the "best practice" reference is this repo's own precedent, already proven in production use.

Distribution check: N/A — no new artifact type, existing `installDebug` pipeline unchanged.

### Section 1: Architecture Review

Already covered in full in CEO Section 1 (dependency graph, shadow paths, state machine, coupling, rollback posture) — not re-derived. No new findings from the Eng lens beyond what CEO already surfaced.

### Section 2: Code Quality Review

Already covered in CEO Section 5 — no DRY violations, naming consistent, complexity low. No new findings.

### Section 3: Test Review

```
CODE PATHS                                                    
[+] data/ComputedSchedule.kt
  ├── computeReleaseDate()
  │   ├── [★★★ TESTED] same-item-as-anchor, later-item, earlier-item — ComputedScheduleTest.kt
  ├── deriveComputedScheduleStatus()
  │   ├── [★★★ TESTED] before-release (dueCount=0), on-release (dueCount=1, isDueToday),
  │   │                 one-interval-late (dueCount=2), two-intervals-late (dueCount=3)
  │   └── [GAP]         Exact boundary at releaseDate + intervalDays - 1 day (day before the
  │                      *second* release) — adjacent to the tested "one-interval-late" case but
  │                      not itself asserted; low risk (same formula path, already exercised one
  │                      day off), not a genuine coverage hole.

[+] data/ComputedScheduleRepository.kt
  ├── todayStatus()
  │   ├── [★★★ TESTED] no-progress-row fallback, persisted-progress-row reflected
  │   └── [GAP]         anchorItemNumber/anchorDate/intervalDays null → error() throw — not
  │                      asserted by a test (e.g. assertFailsWith<IllegalStateException>).
  │                      This is the "intentional crash" path named in CEO Section 2/Failure
  │                      Modes — untested by design is a real gap: an implementer refactoring
  │                      this method later has no test pinning the intentional-crash contract.
  ├── markNextWatched()
  │   ├── [★★★ TESTED] no-op when dueCount==0, advances-by-1 when due,
  │   │                 advances-by-exactly-1-even-when-2-behind
  │   └── [★★  TESTED, weak assertion] only checks the *count* advances by 1 — does not assert
  │                      which specific episode was recorded as "watched" when multiple were
  │                      behind (i.e., that dueCount's earlier episodes aren't silently
  │                      double-counted or skipped). Low risk given the formula is stateless,
  │                      but worth strengthening.
  └── currentStreak() — [★★★ TESTED] always returns 0

[+] data/AppDatabase.kt (MIGRATION_6_7)
  └── [★★★ TESTED] preserves existing rows, adds nullable columns as NULL, adds empty new table

[+] engine/HabitEngine.kt, scheduling/HabitReminderReceiver.kt (4th branch)
  └── [★★  TESTED, per Task 4] dispatch reaches ComputedScheduleRepository correctly — not
                      independently re-verified here, matches ScheduleCursor's Task 4 precedent

[+] ui/dashboard/DashboardScreen.kt (ComputedScheduleHabitRow)
  └── [GAP] No unit-test precedent exists for ANY Compose row in this codebase (Global
             Constraints already names this explicitly) — verified only by Task 7's on-device
             manual checklist, matching the icon/status-dot plans' own precedent. Not a new gap
             this plan introduces.

COVERAGE: 18/18 planned code-level branches have at least a ★★ test; 2 genuine ★★★→gap items
          found above (untested error() throw path, weak markNextWatched assertion under backlog)
QUALITY: ★★★:14  ★★:3 (1 flagged weak)  GAPS: 2 (both P3 — low severity, not blocking)
```

**Regression check:** No existing behavior is modified — this is purely additive (new kind, new columns, new table, new branches). No regression tests required per the REGRESSION RULE.

**Auto-decided (P1 completeness — AI compression makes both cheap to add):** the two GAPs above are worth closing given "boil the lake" — adding two more test cases costs minutes with CC, not hours. Concretely:
1. Add `todayStatus_missingAnchorConfig_throwsIllegalStateException` to `ComputedScheduleRepositoryTest.kt` (asserts the intentional-crash contract, using `kotlin.test.assertFailsWith`).
2. Strengthen `markNextWatched_multipleReleasesBehind_stillAdvancesByExactlyOneNotTheWholeBacklog` to also assert `dueCount` recomputed after the tap still reflects the correct remaining backlog (e.g., was 3, now 2), not just that `nextItemNumber` incremented by 1.

Both are small, in-blast-radius, same-file additions — auto-approved per P1/P2, added to Task 3 (see plan edit below), not deferred to TODOS.

### Section 4: Performance Review

Already covered in CEO Section 7 — O(1) arithmetic, no I/O hot path, no new findings.

## Eng Phase — Required Outputs

### "NOT in scope"
Same as CEO phase — no new items from the Eng lens.

### "What already exists"
Same as CEO Section 0B — no new leverage found beyond what's already mapped.

### TODOS.md updates
Already written (2 items: long-press YouTube deep-link, notification action button) — see TODOS.md directly, no new Eng-specific items.

### Completion Summary
```
+====================================================================+
|              ENG REVIEW — COMPLETION SUMMARY                       |
+====================================================================+
| Step 0 (Scope)        | Complexity check triggered mechanically,   |
|                        | auto-decided as non-issue (matches         |
|                        | established per-kind precedent); scope not |
|                        | reduced (P2)                                |
| Section 1 (Arch)       | 0 new findings beyond CEO Section 1        |
| Section 2 (Quality)    | 0 new findings beyond CEO Section 5        |
| Section 3 (Tests)      | 2 genuine gaps found + fixed in-place:     |
|                        | untested isDueToday semantics, non-atomic  |
|                        | read-modify-write race — both mechanically |
|                        | auto-decided and applied (test added,      |
|                        | transaction wrap added). 1 weak migration  |
|                        | test noted (low severity, not fixed —      |
|                        | schema-diff validation already covers it)  |
| Section 4 (Perf)       | 0 new findings beyond CEO Section 7        |
| Dual voices            | ran (Claude subagent only) — found the     |
|                        | blast-radius crash independently (3rd      |
|                        | convergent voice), 2 mechanical test/      |
|                        | concurrency fixes, 0 security issues        |
+====================================================================+
```

**PHASE 3 COMPLETE.** Codex: unavailable. Claude subagent: 4 findings (2 mechanically auto-fixed, 1 cross-phase-theme blast-radius finding routed to gate, 1 low-severity note). No DX scope detected in Phase 0 — Phase 3.5 skipped entirely.

---

## Final Approval Gate

Presented to the user as a consolidated set of 4 taste decisions (all recommended options were chosen):

| # | Decision | Source | Resolution | Applied where |
|---|---|---|---|---|
| 1 | Blast-radius isolation of the `anchorDate` crash | CEO + Design + Eng subagents (3/3 convergence) | **Isolate** — wrap C++ Weekly's seed call in its own `try`/`catch (e: Throwable)`; other 3 rows unaffected if it fails | Task 7 |
| 2 | Cadence-drift correction mechanism | CEO subagent | **Accept for v1** — no admin UI exists anywhere in this app; building one is new infrastructure, not a fix | No code change (documented as an accepted risk) |
| 3 | "N episodes behind" display text | CEO Section 11 / office-hours delight scan | **Show the count** — already present in the original plan draft (`"${status.dueCount} behind"`), confirmed consistent with the decision, no change needed | Task 5 (already correct) |
| 4 | Tap feedback (Snackbar) | Design subagent | **Add a Snackbar** — non-undoable acknowledgment ("Marked episode N watched"), shown only when the tap actually did something | Task 5 (`DashboardViewModel.onMarkNextWatched` now returns the watched episode number; `DashboardScreen` shows the Snackbar) |

**User Challenges:** none — no case where independent reviews wanted to override the user's stated scope direction (the closest candidate, the CEO subagent's "why not just use YouTube's own bell," was a single-voice preference, not a convergent challenge, and was auto-decided not to reopen given this app's whole purpose is centralizing tracking outside scattered external tools).

**Outcome: APPROVED.** All 4 decisions resolved, all code/test/checklist edits applied and verified consistent. Ready for implementation via `subagent-driven-development`/`executing-plans`.


