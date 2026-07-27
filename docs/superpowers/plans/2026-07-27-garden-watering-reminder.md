<!-- /autoplan restore point: /c/Users/zivk/.gstack/projects/Reminders/main-autoplan-restore-20260727-183809.md -->
# Garden Watering Reminder (INTERVAL_DUE Habit Kind) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Water the garden" dashboard row on a genuinely new habit kind,
`HabitKind.INTERVAL_DUE` — the 5th kind in this engine, alongside Counter, Timer,
Schedule-cursor, and Computed-schedule. Unlike every existing kind, the user picks the
reschedule interval themselves at completion time (not a fixed daily goal, not a fixed
external schedule, not a fixed anchor+cadence). Full requirements and rationale:
`docs/superpowers/specs/2026-07-27-garden-watering-reminder-design.md` (condensed) /
`C:\Users\zivk\.gstack\projects\Reminders\zivk-main-design-20260727-154006.md` (full).

**Architecture:** A single persisted `nextDueDate` per habit instance (`IntervalDueProgress`,
mirrors `ScheduleCursorProgress`/`ComputedScheduleProgress`'s single-row-per-instance shape) plus
an append-only completion log (`IntervalDueLog`, mirrors `ReadingSessionLog`/
`ComputedScheduleWatchLog`'s autoincrement-id shape). `isDue = !nextDueDate.isAfter(today)` —
today or earlier. `markDone`/`rescheduleOnly` both require `intervalDays >= 1` and both set
`nextDueDate = today + intervalDays` (never the old due date + intervalDays — see design doc
Premise 2 for why). `markDone` additionally inserts a log row dated `today` — always today, even
if the row was overdue when pressed (design doc's overdue-logging requirement). No
`enabledDaysMask` gating (this kind has no day-of-week concept), no streak (design doc Premise
3 — a self-chosen variable interval has no honest streak definition), so the Statistics screen
is a plain history list, not the usual streak+heatmap `HabitStatsSummary`/`HeatmapGrid` pair
every other kind's stats screen reuses — this is a deliberate, one-off departure, not an
oversight (flagged explicitly for the /autoplan Design review to weigh).

**Tech Stack:** Same as every prior plan — Kotlin 2.3.0, Jetpack Compose (Material 3), Room
2.7.1 (KSP), JUnit4 + Robolectric 4.16.1, `kotlinx-coroutines-test`. No new dependencies.

## Global Constraints

(Inherited from the master design doc and every prior plan — still binding.)

- Package / application ID: `com.ziv.reminders`. `minSdk = 35`, `targetSdk = 36`.
- No in-app "add habit" UI — the garden-watering instance is inserted via
  `ensureHabitsSeeded`, same mechanism as Exercise/Reading/Tanakh/C++ Weekly/Lego Kit.
- Every Room schema change ships with a real `Migration` object; never
  `fallbackToDestructiveMigration()`. This plan is the schema's v7→v8 migration.
- TDD for all pure logic and repository/dispatch code; Robolectric (`@Config(sdk = [35])`)
  for anything touching Room; every commit after a task leaves
  `./gradlew.bat :app:testDebugUnitTest` green.
- No new `HabitInstance` column: unlike Timer/ComputedSchedule (which each added nullable
  per-instance config columns), this kind's only "config" is the seeded initial due date,
  written directly into `IntervalDueProgress` at seed time — matching Schedule-cursor's
  precedent of adding zero new `habit_instance` columns. **Do not reuse the existing
  `HabitInstance.intervalDays` column** — that column is ComputedSchedule's fixed per-instance
  release cadence (set once at seed time, e.g. "every 7 days"); this kind's interval is chosen
  fresh by the user at every single completion and lives nowhere on `HabitInstance`.
- Dashboard rows are always shown regardless of day-of-week (existing app-wide behavior) —
  this kind has no day-of-week concept at all, so this is automatic, not something to implement.
- `GARDEN_HABIT_INSTANCE_ID = 6L` — the next available instance ID (Exercise=1, Reading=2,
  Tanakh=3, C++ Weekly=4, Lego Kit=5).

---

## File Structure

```
Reminders/
  app/src/main/res/drawable/
    ic_habit_garden.xml                        (Create — Task 5)
  app/src/main/java/com/ziv/reminders/
    data/
      HabitKind.kt                             (Modify — Task 1)
      IntervalDueProgress.kt                    (Create — Task 1)
      IntervalDueProgressDao.kt                  (Create — Task 1)
      IntervalDueLog.kt                          (Create — Task 1)
      IntervalDueLogDao.kt                       (Create — Task 1)
      AppDatabase.kt                             (Modify — Task 1)
      AppContainer.kt                            (Modify — Tasks 1, 2)
      HabitStatus.kt                             (Modify — Task 2)
      IntervalDueRepository.kt                   (Create — Task 2)
      HabitSeeding.kt                            (Modify — Tasks 1, 5)
    engine/
      HabitEngine.kt                             (Modify — Task 2)
    scheduling/
      HabitReminderReceiver.kt                   (Modify — Task 2)
    ui/dashboard/
      DashboardViewModel.kt                      (Modify — Task 3)
      DashboardScreen.kt                         (Modify — Task 3)
      IntervalDueStatsViewModel.kt                (Create — Task 4)
      IntervalDueStatsScreen.kt                   (Create — Task 4)
    MainActivity.kt                              (Modify — Task 4)
    RemindersApp.kt                              (Modify — Task 5)
  app/src/test/java/com/ziv/reminders/
    data/
      IntervalDueProgressDaoTest.kt               (Create — Task 1)
      IntervalDueLogDaoTest.kt                    (Create — Task 1)
      AppDatabaseMigration7To8Test.kt             (Create — Task 1)
      IntervalDueRepositoryTest.kt                (Create — Task 2)
      HabitSeedingTest.kt                         (Modify — Task 5, or Create if it doesn't exist yet)
    engine/
      HabitEngineTest.kt                          (Modify — Task 2)
    scheduling/
      HabitReminderReceiverTest.kt                 (Modify — Task 2)
    ui/dashboard/
      DashboardViewModelTest.kt                    (Modify — Task 3)
      DashboardDispatchTest.kt                     (Modify — Task 3, if it exists — verify during Task 3)
      TestAppContainer.kt                          (Modify — Task 2)
```

---

### Task 1: Room schema v8 — `IntervalDueProgress`, `IntervalDueLog`, migration

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitKind.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/IntervalDueProgress.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/IntervalDueProgressDao.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/IntervalDueLog.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/IntervalDueLogDao.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt` (DAO getters only — repository wiring is Task 2, matching the Schedule-cursor plan's Task 1/Task 4 split)
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt` (constant only — see
  Sequencing note in Step 3 below; the actual seeding insert is Task 5)
- Test: `app/src/test/java/com/ziv/reminders/data/IntervalDueProgressDaoTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/IntervalDueLogDaoTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration7To8Test.kt`

**Interfaces:**
- Produces: `HabitKind.INTERVAL_DUE`; `data class IntervalDueProgress(habitInstanceId: Long,
  nextDueDate: String)`; `interface IntervalDueProgressDao { suspend fun getByInstance(...):
  IntervalDueProgress?; suspend fun upsert(...); suspend fun insertIfAbsent(...) }` — the DAO
  gets `insertIfAbsent` here in Task 1, not bolted on in Task 5, mirroring
  `ComputedScheduleProgressDao`'s identical `insertIfAbsent`/`upsert` pair (Eng review finding:
  the plan's first draft introduced `insertIfAbsent` only as an afterthought in Task 5's seeding
  step, with no DAO declaration, no file-list entry, and no test — the exact "reseed resets your
  real due date back to today" regression `ComputedScheduleProgressDao`'s own
  `insertIfAbsent_rowAlreadyExists_leavesItUntouched` test already guards against for that kind).
  `data class IntervalDueLog(id: Long = 0,
  habitInstanceId: Long, date: String)`; `interface IntervalDueLogDao { suspend fun insert(...);
  suspend fun getByDate(...): IntervalDueLog?; suspend fun getAllForInstance(...): List<IntervalDueLog>
  }`; `AppDatabase.MIGRATION_7_8`. Consumed by: `IntervalDueRepository` (Task 2).

- [ ] **Step 1: Write the failing DAO tests**

`app/src/test/java/com/ziv/reminders/data/IntervalDueProgressDaoTest.kt`:
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
class IntervalDueProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByInstance_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.intervalDueProgressDao().getByInstance(6L))
        db.close()
    }

    @Test
    fun upsert_thenGetByInstance_returnsTheRow() = runTest {
        val db = newDb()
        db.intervalDueProgressDao().upsert(IntervalDueProgress(habitInstanceId = 6L, nextDueDate = "2026-07-27"))

        assertEquals(IntervalDueProgress(6L, "2026-07-27"), db.intervalDueProgressDao().getByInstance(6L))
        db.close()
    }

    @Test
    fun upsert_sameInstance_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.intervalDueProgressDao().upsert(IntervalDueProgress(6L, nextDueDate = "2026-07-27"))
        db.intervalDueProgressDao().upsert(IntervalDueProgress(6L, nextDueDate = "2026-08-01"))

        assertEquals("2026-08-01", db.intervalDueProgressDao().getByInstance(6L)?.nextDueDate)
        db.close()
    }

    @Test
    fun insertIfAbsent_noRow_insertsIt() = runTest {
        val db = newDb()
        db.intervalDueProgressDao().insertIfAbsent(IntervalDueProgress(6L, nextDueDate = "2026-07-27"))

        assertEquals("2026-07-27", db.intervalDueProgressDao().getByInstance(6L)?.nextDueDate)
        db.close()
    }

    @Test
    fun insertIfAbsent_rowAlreadyExists_leavesItUntouched() = runTest {
        // Eng review finding: this is the regression insertIfAbsent exists to prevent — re-running
        // seeding (every app restart, via RemindersApp.onCreate) must never reset a real,
        // already-advanced due date back to "today." Mirrors
        // ComputedScheduleProgressDaoTest.insertIfAbsent_rowAlreadyExists_leavesItUntouched exactly.
        val db = newDb()
        db.intervalDueProgressDao().upsert(IntervalDueProgress(6L, nextDueDate = "2026-08-15"))

        db.intervalDueProgressDao().insertIfAbsent(IntervalDueProgress(6L, nextDueDate = "2026-07-27"))

        assertEquals("2026-08-15", db.intervalDueProgressDao().getByInstance(6L)?.nextDueDate)
        db.close()
    }
}
```

`app/src/test/java/com/ziv/reminders/data/IntervalDueLogDaoTest.kt`:
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
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IntervalDueLogDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByDate_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.intervalDueLogDao().getByDate(6L, "2026-07-27"))
        db.close()
    }

    @Test
    fun insert_thenGetByDate_returnsARow() = runTest {
        val db = newDb()
        db.intervalDueLogDao().insert(IntervalDueLog(habitInstanceId = 6L, date = "2026-07-27"))

        assertNotNull(db.intervalDueLogDao().getByDate(6L, "2026-07-27"))
        db.close()
    }

    @Test
    fun insert_sameInstanceAndDateTwice_keepsBothRows() = runTest {
        // Append-only log, not an upsert table — watering twice in one day isn't precluded by
        // the spec (see design doc Architecture), so both inserts must survive as separate rows.
        val db = newDb()
        db.intervalDueLogDao().insert(IntervalDueLog(habitInstanceId = 6L, date = "2026-07-27"))
        db.intervalDueLogDao().insert(IntervalDueLog(habitInstanceId = 6L, date = "2026-07-27"))

        assertEquals(2, db.intervalDueLogDao().getAllForInstance(6L).size)
        db.close()
    }

    @Test
    fun getAllForInstance_returnsOnlyThatInstancesRows_mostRecentDateFirst() = runTest {
        val db = newDb()
        db.intervalDueLogDao().insert(IntervalDueLog(habitInstanceId = 6L, date = "2026-07-20"))
        db.intervalDueLogDao().insert(IntervalDueLog(habitInstanceId = 6L, date = "2026-07-27"))
        db.intervalDueLogDao().insert(IntervalDueLog(habitInstanceId = 9L, date = "2026-07-27")) // different instance

        val dates = db.intervalDueLogDao().getAllForInstance(6L).map { it.date }
        assertEquals(listOf("2026-07-27", "2026-07-20"), dates)
        db.close()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.IntervalDueProgressDaoTest" --tests "com.ziv.reminders.data.IntervalDueLogDaoTest"`
Expected: FAIL — the entities/DAOs/`AppDatabase` accessors don't exist yet (compile error).

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
    INTERVAL_DUE,
}
```

**Sequencing note:** `GARDEN_HABIT_INSTANCE_ID` is declared in this task (added to
`HabitSeeding.kt` as a standalone top-level `const val`, see below), even though the actual
`ensureHabitsSeeded` insert for this instance isn't written until Task 5 — Tasks 2-4's tests and
production code (`IntervalDueStatsViewModel`) reference this constant, and every prior kind's
plan could take the constant's prior existence for granted (Tanakh/C++ Weekly/Lego Kit were all
seeded well after their own IDs were established); this is the first genuinely new kind+ID pair
introduced in the same plan, so the constant must be declared before it's used, not deferred to
the same task that populates the row.

Add to `app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt` in this task (just the
constant — leave the rest of that file, including `ensureHabitsSeeded`'s body, untouched until
Task 5):
```kotlin
const val GARDEN_HABIT_INSTANCE_ID = 6L
```

`app/src/main/java/com/ziv/reminders/data/IntervalDueProgress.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single row per habit instance. nextDueDate is ISO-8601 text (LocalDate.toString()'s default
 * format), matching every other date-as-TEXT column in this codebase. Mirrors
 * ScheduleCursorProgress/ComputedScheduleProgress's single-row-per-instance shape, but stores a
 * due date instead of a cursor index/item number.
 */
@Entity(tableName = "interval_due_progress")
data class IntervalDueProgress(
    @PrimaryKey val habitInstanceId: Long,
    val nextDueDate: String,
)
```

`app/src/main/java/com/ziv/reminders/data/IntervalDueProgressDao.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface IntervalDueProgressDao {
    @Query("SELECT * FROM interval_due_progress WHERE habitInstanceId = :habitInstanceId")
    suspend fun getByInstance(habitInstanceId: Long): IntervalDueProgress?

    @Upsert
    suspend fun upsert(progress: IntervalDueProgress)

    // Used only at seed time (Task 5's ensureHabitsSeeded) to write the initial due-today row —
    // IGNORE on conflict means re-running seeding on every app restart never resets a real,
    // already-advanced due date back to "today." Mirrors ComputedScheduleProgressDao's identical
    // insertIfAbsent/upsert pair exactly (Eng review finding — see Interfaces note above).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(progress: IntervalDueProgress)
}
```
(Add `import androidx.room.Insert` and `import androidx.room.OnConflictStrategy`.)

`app/src/main/java/com/ziv/reminders/data/IntervalDueLog.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only completion log — one row per watering event, autoincrement surrogate key (mirrors
 * ReadingSessionLog/ComputedScheduleWatchLog's shape, not a per-day upsert table like
 * CounterDailyProgress/TimerDailyProgress). date is always the date markDone actually ran, never
 * the due date that triggered it (design doc's overdue-logging requirement) — always today's
 * date at the moment of the write, since markDone is only ever called with today() (see
 * IntervalDueRepository). Multiple rows for the same (habitInstanceId, date) are legal — this is
 * a log, not a uniqueness-enforcing table.
 *
 * indices must match MIGRATION_7_8's manually-created index (name and columns) exactly, or
 * Room's schema validation fails at runtime with a migration-mismatch exception — same lesson
 * ReadingSessionLog's own migration plan already documented (caught by actually running the
 * migration test during implementation, not caught by any of the 3 /autoplan review phases).
 */
@Entity(tableName = "interval_due_log", indices = [Index(value = ["habitInstanceId", "date"])])
data class IntervalDueLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitInstanceId: Long,
    val date: String,
)
```

`app/src/main/java/com/ziv/reminders/data/IntervalDueLogDao.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface IntervalDueLogDao {
    @Insert
    suspend fun insert(log: IntervalDueLog)

    @Query("SELECT * FROM interval_due_log WHERE habitInstanceId = :habitInstanceId AND date = :date LIMIT 1")
    suspend fun getByDate(habitInstanceId: Long, date: String): IntervalDueLog?

    @Query("SELECT * FROM interval_due_log WHERE habitInstanceId = :habitInstanceId ORDER BY date DESC, id DESC")
    suspend fun getAllForInstance(habitInstanceId: Long): List<IntervalDueLog>
}
```

`app/src/main/java/com/ziv/reminders/data/AppDatabase.kt` — add the two entities, bump to
version 8, add the two DAO accessors, add `MIGRATION_7_8`:
```kotlin
@Database(
    entities = [
        HabitInstance::class, CounterDailyProgress::class, TimerDailyProgress::class,
        ScheduleCursorProgress::class, ScheduleCursorDailyProgress::class,
        EvaluatorEscalation::class, ExerciseSubCounterProgress::class, ReadingSessionLog::class,
        ComputedScheduleProgress::class, ComputedScheduleWatchLog::class,
        IntervalDueProgress::class, IntervalDueLog::class,
    ],
    version = 8,
)
abstract class AppDatabase : RoomDatabase() {
    // ... existing DAO accessors unchanged ...
    abstract fun intervalDueProgressDao(): IntervalDueProgressDao
    abstract fun intervalDueLogDao(): IntervalDueLogDao

    companion object {
        // ... MIGRATION_1_2 through MIGRATION_6_7 unchanged ...

        /** Adds INTERVAL_DUE kind support: a per-instance running due-date table (mirrors
         * schedule_cursor_progress/computed_schedule_progress's single-row shape) plus an
         * append-only completion log (mirrors reading_session_log/computed_schedule_watch_log's
         * autoincrement shape). No new habit_instance column — this kind's only "config" is the
         * seeded initial due date, written directly into interval_due_progress at seed time (see
         * Global Constraints — do not confuse with the existing intervalDays column, which is
         * ComputedSchedule's unrelated fixed cadence). Never fallbackToDestructiveMigration(). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `interval_due_progress` (" +
                        "`habitInstanceId` INTEGER NOT NULL, `nextDueDate` TEXT NOT NULL, " +
                        "PRIMARY KEY(`habitInstanceId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `interval_due_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitInstanceId` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL)"
                )
                // Matches IntervalDueLogDao's WHERE habitInstanceId = ... AND date = ... access
                // pattern — same index shape as reading_session_log/computed_schedule_watch_log.
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_interval_due_log_habitInstanceId_date` " +
                        "ON `interval_due_log` (`habitInstanceId`, `date`)"
                )
            }
        }
    }
}
```

`app/src/main/java/com/ziv/reminders/data/AppContainer.kt` — add the migration and the two DAO
getters only (repository/HabitEngine wiring is Task 2, matching the Schedule-cursor plan's
intentionally-incomplete-intermediate-state precedent):
```kotlin
    private val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "reminders.db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8,
            )
            .build()
    }

    // ... existing DAO getters unchanged ...
    val intervalDueProgressDao get() = db.intervalDueProgressDao()
    val intervalDueLogDao get() = db.intervalDueLogDao()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.IntervalDueProgressDaoTest" --tests "com.ziv.reminders.data.IntervalDueLogDaoTest"`
Expected: PASS (9 tests)

- [ ] **Step 5: Write the failing migration test**

`app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration7To8Test.kt`:
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
class AppDatabaseMigration7To8Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate7To8_preservesExistingRows_andAddsIntervalDueTables() {
        helper.createDatabase(TEST_DB_NAME, 7).apply {
            execSQL(
                "INSERT INTO habit_instance (id, kind, name, enabledDaysMask, notificationTitle, notificationBody, counterGoal) " +
                    "VALUES (1, 'COUNTER', 'Exercise', 127, 't', 'b', 5)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 8, true, AppDatabase.MIGRATION_7_8)

        migrated.query("SELECT name FROM habit_instance WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Exercise", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM interval_due_progress").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM interval_due_log").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }
}
```

- [ ] **Step 6: Run the migration test, then the full suite**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.AppDatabaseMigration7To8Test"`
Expected: PASS (1 test)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests still green)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitKind.kt app/src/main/java/com/ziv/reminders/data/IntervalDueProgress.kt app/src/main/java/com/ziv/reminders/data/IntervalDueProgressDao.kt app/src/main/java/com/ziv/reminders/data/IntervalDueLog.kt app/src/main/java/com/ziv/reminders/data/IntervalDueLogDao.kt app/src/main/java/com/ziv/reminders/data/AppDatabase.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt app/src/test/java/com/ziv/reminders/data/IntervalDueProgressDaoTest.kt app/src/test/java/com/ziv/reminders/data/IntervalDueLogDaoTest.kt app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration7To8Test.kt
git commit -m "Add Room schema v8 for INTERVAL_DUE kind"
```

---

### Task 2: `IntervalDueRepository`, `HabitStatus.IntervalDueStatus`, `HabitEngine` dispatch

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitStatus.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/IntervalDueRepository.kt`
- Modify: `app/src/main/java/com/ziv/reminders/engine/HabitEngine.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt` (repository + HabitEngine
  wiring, completing Task 1's intentionally-incomplete intermediate state)
- Modify: `app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt`
- Modify: `app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt`
- Modify: `app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt` (or wherever the
  fake `DashboardDataSource` for tests lives — verify exact file during this task)
- Modify: `app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/IntervalDueRepositoryTest.kt`

**Interfaces:**
- Consumes: `IntervalDueProgress`, `IntervalDueLog`, their DAOs (Task 1).
- Produces: `HabitStatus.IntervalDueStatus(dueDate: LocalDate, isDue: Boolean, completedToday:
  Boolean)`; `class IntervalDueRepository(progressDao, logDao) { suspend fun todayStatus(...):
  HabitStatus.IntervalDueStatus; suspend fun markDone(instance, intervalDays, today); suspend fun
  rescheduleOnly(instance, intervalDays, today); suspend fun history(instance): List<LocalDate>;
  suspend fun completedDates(instance): List<String> }`; `HabitEngine(counterRepository,
  timerRepository, scheduleCursorRepository, computedScheduleRepository, intervalDueRepository)`
  — the constructor signature change (4 args → 5). Every existing call site that constructs
  `HabitEngine(...)` must be updated in this same task (same reason as every prior kind's Task 4:
  Kotlin compiles main+test source sets together).

Note on `intervalDays >= 1` validation: this is enforced in the repository (not just the UI
dialog in Task 3) — belt-and-suspenders, per the design doc's explicit fix for the reviewer-found
gap where an unguarded 0/negative value could reproduce the exact "reschedule doesn't actually
move forward" bug Premise 2 was written to prevent. Both `markDone` and `rescheduleOnly` throw
`IllegalArgumentException` on `intervalDays < 1` — callers (the ViewModel, Task 3) never pass an
invalid value in practice, since the dialog's confirm button is itself disabled below 1, but the
repository must not silently accept bad input from any future caller either.

Note on `HabitReminderReceiver`'s `when (status)` completed-check: this is an expression `val`
(non-exhaustive is a compile error), so this task must add the `IntervalDueStatus` branch. Per
the design doc's notification-tap decision, `completedToday` (not `isDue`) is what suppresses
re-firing the reminder — matches how `CounterStatus.completed`/`TimerStatus.completed` are used
in this same `when` today.

- [ ] **Step 1: Write the failing repository test**

`app/src/test/java/com/ziv/reminders/data/IntervalDueRepositoryTest.kt`:
```kotlin
package com.ziv.reminders.data

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeIntervalDueProgressDao : IntervalDueProgressDao {
    val rows = mutableMapOf<Long, IntervalDueProgress>()
    override suspend fun getByInstance(habitInstanceId: Long) = rows[habitInstanceId]
    override suspend fun upsert(progress: IntervalDueProgress) { rows[progress.habitInstanceId] = progress }
}

private class FakeIntervalDueLogDao : IntervalDueLogDao {
    val rows = mutableListOf<IntervalDueLog>()
    override suspend fun insert(log: IntervalDueLog) { rows.add(log.copy(id = rows.size + 1L)) }
    override suspend fun getByDate(habitInstanceId: Long, date: String) =
        rows.firstOrNull { it.habitInstanceId == habitInstanceId && it.date == date }
    override suspend fun getAllForInstance(habitInstanceId: Long) =
        rows.filter { it.habitInstanceId == habitInstanceId }.sortedWith(compareByDescending<IntervalDueLog> { it.date }.thenByDescending { it.id })
}

class IntervalDueRepositoryTest {

    private val instance = HabitInstance(
        id = 6L, kind = "INTERVAL_DUE", name = "Water the garden", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null,
    )

    @Test
    fun todayStatus_dueDateIsToday_isDueTrue() = runTest {
        val progressDao = FakeIntervalDueProgressDao()
        progressDao.rows[6L] = IntervalDueProgress(6L, nextDueDate = "2026-07-27")
        val repo = IntervalDueRepository(progressDao, FakeIntervalDueLogDao())

        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 7, 27))

        assertTrue(status.isDue)
        assertEquals(LocalDate.of(2026, 7, 27), status.dueDate)
    }

    @Test
    fun todayStatus_dueDateInThePast_isDueTrue() = runTest {
        val progressDao = FakeIntervalDueProgressDao()
        progressDao.rows[6L] = IntervalDueProgress(6L, nextDueDate = "2026-07-20")
        val repo = IntervalDueRepository(progressDao, FakeIntervalDueLogDao())

        assertTrue(repo.todayStatus(instance, today = LocalDate.of(2026, 7, 27)).isDue)
    }

    @Test
    fun todayStatus_dueDateTomorrow_isDueFalse() = runTest {
        val progressDao = FakeIntervalDueProgressDao()
        progressDao.rows[6L] = IntervalDueProgress(6L, nextDueDate = "2026-07-28")
        val repo = IntervalDueRepository(progressDao, FakeIntervalDueLogDao())

        assertFalse(repo.todayStatus(instance, today = LocalDate.of(2026, 7, 27)).isDue)
    }

    @Test
    fun markDone_setsNextDueDateToTodayPlusIntervalDays_regardlessOfHowOverdue() = runTest {
        // The overdue-logging edge case: due date is 10 days in the past, intervalDays=3 — the
        // new due date must be today+3, never (old due date)+3 (which would still be in the past).
        val progressDao = FakeIntervalDueProgressDao()
        progressDao.rows[6L] = IntervalDueProgress(6L, nextDueDate = "2026-07-17")
        val repo = IntervalDueRepository(progressDao, FakeIntervalDueLogDao())
        val today = LocalDate.of(2026, 7, 27)

        repo.markDone(instance, intervalDays = 3, today)

        assertEquals("2026-07-30", progressDao.rows[6L]?.nextDueDate)
    }

    @Test
    fun markDone_logsTodaysDate_neverTheOriginalDueDate() = runTest {
        val logDao = FakeIntervalDueLogDao()
        val repo = IntervalDueRepository(FakeIntervalDueProgressDao(), logDao)
        val today = LocalDate.of(2026, 7, 27)

        repo.markDone(instance, intervalDays = 5, today)

        assertEquals(listOf("2026-07-27"), logDao.rows.map { it.date })
    }

    @Test
    fun markDone_rejectsIntervalDaysLessThanOne() = runTest {
        val repo = IntervalDueRepository(FakeIntervalDueProgressDao(), FakeIntervalDueLogDao())

        assertFailsWith<IllegalArgumentException> {
            repo.markDone(instance, intervalDays = 0, today = LocalDate.of(2026, 7, 27))
        }
    }

    @Test
    fun rescheduleOnly_updatesDueDate_withoutLoggingACompletion() = runTest {
        val logDao = FakeIntervalDueLogDao()
        val repo = IntervalDueRepository(FakeIntervalDueProgressDao(), logDao)
        val today = LocalDate.of(2026, 7, 27)

        repo.rescheduleOnly(instance, intervalDays = 4, today)

        assertTrue(logDao.rows.isEmpty())
    }

    @Test
    fun rescheduleOnly_worksEvenWhenNotCurrentlyDue() = runTest {
        // Long-press reschedule-only is available regardless of due state (design doc Architecture).
        val progressDao = FakeIntervalDueProgressDao()
        progressDao.rows[6L] = IntervalDueProgress(6L, nextDueDate = "2026-08-15") // far future, not due
        val repo = IntervalDueRepository(progressDao, FakeIntervalDueLogDao())

        repo.rescheduleOnly(instance, intervalDays = 2, today = LocalDate.of(2026, 7, 27))

        assertEquals("2026-07-29", progressDao.rows[6L]?.nextDueDate)
    }

    @Test
    fun rescheduleOnly_rejectsIntervalDaysLessThanOne() = runTest {
        val repo = IntervalDueRepository(FakeIntervalDueProgressDao(), FakeIntervalDueLogDao())

        assertFailsWith<IllegalArgumentException> {
            repo.rescheduleOnly(instance, intervalDays = -1, today = LocalDate.of(2026, 7, 27))
        }
    }

    @Test
    fun todayStatus_completedTodayReflectsWhetherALogRowExistsForToday() = runTest {
        val repo = IntervalDueRepository(FakeIntervalDueProgressDao(), FakeIntervalDueLogDao())
        val today = LocalDate.of(2026, 7, 27)

        repo.markDone(instance, intervalDays = 5, today)
        val status = repo.todayStatus(instance, today)

        assertTrue(status.completedToday)
        // markDone always requires intervalDays >= 1, so nextDueDate is always in the future
        // immediately after — isDue and completedToday never disagree same-day.
        assertFalse(status.isDue)
    }

    @Test
    fun history_returnsLoggedDatesMostRecentFirst() = runTest {
        val repo = IntervalDueRepository(FakeIntervalDueProgressDao(), FakeIntervalDueLogDao())

        repo.markDone(instance, intervalDays = 5, today = LocalDate.of(2026, 7, 10))
        repo.markDone(instance, intervalDays = 5, today = LocalDate.of(2026, 7, 20))

        assertEquals(listOf(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 10)), repo.history(instance))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.IntervalDueRepositoryTest"`
Expected: FAIL — `IntervalDueRepository` and `HabitStatus.IntervalDueStatus` don't exist yet.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/data/HabitStatus.kt` — add the new case to the existing
sealed interface:
```kotlin
    data class IntervalDueStatus(
        val dueDate: LocalDate,
        val isDue: Boolean,
        val completedToday: Boolean,
    ) : HabitStatus
```
(Add `import java.time.LocalDate` at the top of the file if not already present.)

`app/src/main/java/com/ziv/reminders/data/IntervalDueRepository.kt`:
```kotlin
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
```

`app/src/main/java/com/ziv/reminders/engine/HabitEngine.kt` (full file):
```kotlin
package com.ziv.reminders.engine

import com.ziv.reminders.data.ComputedScheduleRepository
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitKind
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.IntervalDueRepository
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.data.TimerHabitRepository
import java.time.LocalDate

class HabitEngine(
    private val counterRepository: CounterHabitRepository,
    private val timerRepository: TimerHabitRepository,
    private val scheduleCursorRepository: ScheduleCursorRepository,
    private val computedScheduleRepository: ComputedScheduleRepository,
    private val intervalDueRepository: IntervalDueRepository,
) {

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus =
        when (instance.kind) {
            HabitKind.COUNTER.name -> counterRepository.todayStatus(instance, today)
            HabitKind.TIMER.name -> timerRepository.todayStatus(instance, today)
            HabitKind.SCHEDULE_CURSOR.name -> scheduleCursorRepository.todayStatus(instance, today)
            HabitKind.COMPUTED_SCHEDULE.name -> computedScheduleRepository.todayStatus(instance, today)
            HabitKind.INTERVAL_DUE.name -> intervalDueRepository.todayStatus(instance, today)
            else -> throw IllegalArgumentException("Unknown habit kind: ${instance.kind}")
        }

    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int =
        when (instance.kind) {
            HabitKind.COUNTER.name -> counterRepository.currentStreak(instance, today)
            HabitKind.TIMER.name -> timerRepository.currentStreak(instance, today)
            HabitKind.SCHEDULE_CURSOR.name -> scheduleCursorRepository.currentStreak(instance, today)
            HabitKind.COMPUTED_SCHEDULE.name -> computedScheduleRepository.currentStreak(instance, today)
            // Repurposes the generic per-row "streak" summary slot to carry total-times-watered
            // instead — NOT a real streak (design doc Premise 3 still holds: no consecutive-day
            // math for this kind). Added during /autoplan Design review: reuses the exact
            // dashboard-refresh plumbing that already calls currentStreak() for every instance
            // (DashboardViewModel.refresh), so the row's subtitle line (Task 3) gets a genuinely
            // useful number at zero extra repository calls in the hot path, instead of the earlier
            // draft's hardcoded 0 (which the row would've had nothing meaningful to show anyway).
            HabitKind.INTERVAL_DUE.name -> intervalDueRepository.history(instance).size
            else -> throw IllegalArgumentException("Unknown habit kind: ${instance.kind}")
        }
}
```

`app/src/main/java/com/ziv/reminders/data/AppContainer.kt` — complete the wiring:
```kotlin
    val intervalDueRepository: IntervalDueRepository by lazy {
        IntervalDueRepository(intervalDueProgressDao, intervalDueLogDao)
    }

    override val habitEngine: HabitEngine by lazy {
        HabitEngine(counterHabitRepository, timerHabitRepository, scheduleCursorRepository, computedScheduleRepository, intervalDueRepository)
    }
```
Add `val intervalDueRepository: IntervalDueRepository` to `DashboardDataSource` (needed because,
like `ScheduleCursorRepository.markRead`/`ComputedScheduleRepository.markNextWatched`,
`IntervalDueRepository.markDone`/`rescheduleOnly` are direct, synchronous repository calls the
dashboard ViewModel needs access to — Task 3). `ExerciseDetailDataSource`/`ActivityDataSource`
are unaffected (neither needs this repository, same as they don't need `computedScheduleRepository`).

`app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt` — add the branch:
```kotlin
                val completed = when (status) {
                    is HabitStatus.CounterStatus -> status.completed
                    is HabitStatus.TimerStatus -> status.completed
                    is HabitStatus.ScheduleCursorStatus -> status.completed
                    is HabitStatus.ComputedScheduleStatus -> status.dueCount == 0
                    is HabitStatus.IntervalDueStatus -> status.completedToday
                }
```

- [ ] **Step 4: Update every other `HabitEngine(...)` construction site**

Search for every remaining call site (test fakes/containers) and add a fifth
`IntervalDueRepository` argument — same mechanical fix every prior kind's Task 4 needed. Verify
with:
```bash
grep -rn "HabitEngine(" app/src/main app/src/test
```
Update `HabitEngineTest.kt` and `TestAppContainer.kt` (or wherever the test-side fake
`DashboardDataSource` lives) to supply a fake `IntervalDueRepository` or the real one backed by
fakes, matching how `ComputedScheduleRepository` was threaded through in the prior plan.

**Eng review finding (accepted):** the plan's first draft only tested the `currentStreak`
dispatch branch, leaving the new `todayStatus` dispatch branch (`HabitKind.INTERVAL_DUE.name ->
intervalDueRepository.todayStatus(instance, today)`) completely untested. This dispatch is a
runtime string match, not compiler-enforced — and `DashboardViewModel.refresh()` calls
`habitEngine.todayStatus(...)` inside a `.map { }` over *every* habit instance, with no
try/catch, before assigning `_uiState.value`. A bug in this one new branch (wrong repository
wired, branch typo, branch accidentally omitted) would throw inside that `map` and take down
**the entire dashboard's refresh** — all 5 rows, not just this one — with nothing in the test
suite catching it first. Add both:
```kotlin
    @Test
    fun todayStatus_intervalDueKind_dispatchesToIntervalDueRepository() = runTest {
        // Guards the whole dashboard refresh path, not just this row — see this task's Interfaces
        // note. A regression here throws inside DashboardViewModel.refresh()'s per-instance map,
        // before _uiState.value is ever assigned, silently breaking every habit row's display.
        val instance = HabitInstance(
            id = 6L, kind = "INTERVAL_DUE", name = "Water the garden", enabledDaysMask = 0b1111111,
            notificationTitle = "t", notificationBody = "b", counterGoal = null,
        )
        val today = LocalDate.of(2026, 7, 27)
        intervalDueRepositoryFake.progress[6L] = IntervalDueProgress(6L, nextDueDate = "2026-07-27")

        val status = engine.todayStatus(instance, today)

        assertIs<HabitStatus.IntervalDueStatus>(status)
        assertTrue((status as HabitStatus.IntervalDueStatus).isDue)
    }

    @Test
    fun currentStreak_intervalDueKind_dispatchesToIntervalDueRepositoryHistorySize() = runTest {
        val instance = HabitInstance(
            id = 6L, kind = "INTERVAL_DUE", name = "Water the garden", enabledDaysMask = 0b1111111,
            notificationTitle = "t", notificationBody = "b", counterGoal = null,
        )
        intervalDueRepositoryFake.logs[6L] = listOf("2026-07-10", "2026-07-20")

        assertEquals(2, engine.currentStreak(instance, LocalDate.of(2026, 7, 27)))
    }
```
(Fill in `intervalDueRepositoryFake`/`engine` construction to match this test file's existing
fake-repository and `HabitEngine(...)` setup pattern — the two behaviors under test, dispatch
correctness for both `todayStatus` and `currentStreak`, are what matter.)

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.IntervalDueRepositoryTest"`
Expected: PASS (11 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests, including `HabitEngineTest`/`HabitReminderReceiverTest`, still green)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitStatus.kt app/src/main/java/com/ziv/reminders/data/IntervalDueRepository.kt app/src/main/java/com/ziv/reminders/engine/HabitEngine.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt app/src/test/java/com/ziv/reminders/data/IntervalDueRepositoryTest.kt app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt
git commit -m "Add IntervalDueRepository and HabitStatus.IntervalDueStatus, wire HabitEngine dispatch"
```

(Adjust the file list above to include whatever the actual `TestAppContainer`/fake-DataSource
file is named once located in Step 4.)

---

### Task 3: Dashboard row, day-count picker dialog, long-press menu

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `IntervalDueRepository` (Task 2, via `DashboardDataSource`).
- Produces: `DashboardViewModel.onMarkDone(instanceId, intervalDays): Unit` (suspend),
  `DashboardViewModel.onRescheduleOnly(instanceId, intervalDays): Unit` (suspend);
  `IntervalDuePickerDialog` composable; `IntervalDueHabitRow` composable, wired into
  `HabitRow`'s dispatch `when` as a new, purely additive branch (existing branches unchanged).

- [ ] **Step 1: Write the failing ViewModel tests**

Add to `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt` (exact fake
`DashboardDataSource` setup should mirror however `onMarkNextWatched`/`onMarkRead` are already
tested in this file):
```kotlin
    @Test
    fun onMarkDone_advancesDueDateAndLogsToday() = runTest {
        // seed instance 6 as INTERVAL_DUE with an overdue nextDueDate, matching this test file's
        // existing fake-instance setup convention
        viewModel.onMarkDone(GARDEN_HABIT_INSTANCE_ID, intervalDays = 4)

        val status = fakeDataSource.intervalDueRepository.todayStatus(gardenInstance, today = LocalDate.now())
        assertFalse(status.isDue)
    }

    @Test
    fun onRescheduleOnly_updatesDueDate_withoutLoggingACompletion() = runTest {
        viewModel.onRescheduleOnly(GARDEN_HABIT_INSTANCE_ID, intervalDays = 3)

        assertTrue(fakeDataSource.intervalDueRepository.history(gardenInstance).isEmpty())
    }
```
(Fill in the exact fake setup boilerplate to match this file's established pattern once read —
the two behaviors under test are what matter: `onMarkDone` calls through to
`intervalDueRepository.markDone` and refreshes; `onRescheduleOnly` calls through to
`rescheduleOnly` and refreshes, with no log row created.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `onMarkDone`/`onRescheduleOnly` don't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt` — add:
```kotlin
    suspend fun onMarkDone(instanceId: Long, intervalDays: Int) {
        val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return
        dataSource.intervalDueRepository.markDone(instance, intervalDays, LocalDate.now())
        refresh()
    }

    suspend fun onRescheduleOnly(instanceId: Long, intervalDays: Int) {
        val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return
        dataSource.intervalDueRepository.rescheduleOnly(instance, intervalDays, LocalDate.now())
        refresh()
    }
```

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`:

1. Add `onOpenGardenStats: () -> Unit = {}` to `DashboardScreen`'s parameter list and pass it
   through to `HabitRow`, same shape as every other `onOpenXStats` parameter.

2. Add `onMarkDone`/`onRescheduleOnly` callback wiring in `DashboardScreen`'s row-building loop.

**Design review finding (accepted):** the plan's first draft wired these as bare
`coroutineScope.launch { ... }` calls with no user-visible acknowledgment — the *only* mutating
row in the app with zero tap feedback if left that way (every other mutating row shows a
Snackbar: `onIncrement` → "Incremented", `onMarkRead` → "Marked as read",
`onMarkNextWatched` → "Marked episode N watched"). Fixed to match that established rule — an
acknowledgment Snackbar, no "Undo" action (this kind has no undo, same as
`onMarkNextWatched`/`ComputedScheduleWatchLog` — an append-only log with nothing to reverse):
```kotlin
                    onMarkDone = { intervalDays ->
                        coroutineScope.launch {
                            viewModel.onMarkDone(habit.instanceId, intervalDays)
                            snackbarHostState.showSnackbar(message = "Watered!", duration = SnackbarDuration.Short)
                        }
                    },
                    onRescheduleOnly = { intervalDays ->
                        coroutineScope.launch {
                            viewModel.onRescheduleOnly(habit.instanceId, intervalDays)
                            snackbarHostState.showSnackbar(message = "Rescheduled", duration = SnackbarDuration.Short)
                        }
                    },
                    onOpenGardenStats = onOpenGardenStats,
```

3. Extend the private `HabitRow` composable's parameter list to accept the three new callbacks,
   then add the new branch to its dispatch `when` — purely additive, existing branches
   unchanged.

**Eng review finding (accepted):** the plan's first draft showed the `when` branch and the
`DashboardScreen` call site passing these three parameters, but never showed `HabitRow`'s own
`private fun HabitRow(...)` signature actually being extended to receive them — an implementer
following the snippets literally would hit a compile error with no step telling them to expect
it. `HabitRow`'s current signature (verified against `DashboardScreen.kt`) has 14 parameters
(`habit`, `onIncrement`, `onToggleTimer`, ... `onOpenLegoKitStats`); add three more at the end:
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
    onIncrementLegoKit: () -> Unit,
    onUndoLegoKit: () -> Unit,
    onOpenExercise: () -> Unit,
    onOpenExerciseStats: () -> Unit,
    onOpenReadingStats: () -> Unit,
    onOpenTanakhStats: () -> Unit,
    onOpenCppWeeklyStats: () -> Unit,
    onOpenLegoKitStats: () -> Unit,
    onMarkDone: (Int) -> Unit,
    onRescheduleOnly: (Int) -> Unit,
    onOpenGardenStats: () -> Unit,
) {
    when (habit.status) {
        // ... existing branches (CounterStatus/TimerStatus/ScheduleCursorStatus/
        // ComputedScheduleStatus) unchanged ...
        is HabitStatus.IntervalDueStatus -> IntervalDueHabitRow(habit, habit.status, onMarkDone, onRescheduleOnly, onOpenGardenStats)
    }
}
```
And update `DashboardScreen`'s call site to `HabitRow(...)` to pass the three new arguments
(already shown in step 2 above as part of the row-building loop's parameter list).

4. Add the day-count picker dialog composable:

**Cherry-pick accepted during `/autoplan` CEO review (SELECTIVE EXPANSION):** preset day-count
chips (3/5/7/10/14) alongside the free-entry field, not free-entry alone — a tap-once path for
the common case, still backed by the same free-text field for anything else. In blast radius
(touches only this new dialog), well under an hour of effort, so auto-approved per the "boil the
lake" principle rather than deferred to TODOS.md.
```kotlin
@Composable
private fun IntervalDuePickerDialog(
    title: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // Preset chips for the common cases, plus free numeric entry for anything else — chips set
    // the text field's value directly, so Confirm's enabled/disabled logic and the >= 1 guard
    // apply uniformly regardless of how the value was entered. Confirm is disabled below 1,
    // mirroring IntervalDueRepository's own require(intervalDays >= 1) guard — belt-and-suspenders,
    // not a substitute for it (see Task 2).
    var text by remember { mutableStateOf("") }
    val parsed = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 7, 10, 14).forEach { preset ->
                        AssistChip(onClick = { text = preset.toString() }, label = { Text("$preset") })
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Days") },
                    // Explains why Confirm is disabled (design review Pass 2 finding — a
                    // greyed-out button with no reason is a dead end, not a state).
                    supportingText = { if (parsed == null || parsed < 1) Text("Enter at least 1 day") },
                    isError = text.isNotEmpty() && (parsed == null || parsed < 1),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null && parsed >= 1,
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```
(Add `androidx.compose.material3.OutlinedTextField`, `androidx.compose.material3.AssistChip`,
`androidx.compose.ui.text.input.KeyboardType`, `androidx.compose.foundation.text.KeyboardOptions`
imports.)

5. Add the pure due/overdue derivation function, in a new plain-Kotlin file (no Compose
   dependency, so it's unit-testable with plain JUnit — no Robolectric needed):

**Eng review finding (accepted):** this math previously lived inline inside the row Composable
with zero test coverage. Extracted here so a TDD cycle actually covers it.

`app/src/main/java/com/ziv/reminders/ui/dashboard/IntervalDueRowDisplay.kt`:
```kotlin
package com.ziv.reminders.ui.dashboard

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal enum class IntervalDueUrgency { OVERDUE, DUE_TODAY, NOT_DUE }

internal data class IntervalDueRowDisplay(val urgency: IntervalDueUrgency, val statusText: String)

/** Pure function — no Android/Compose dependency. daysOverdue > 0 means dueDate is in the past
 * (overdue by that many days); == 0 means due today; < 0 means dueDate is still in the future. */
internal fun deriveIntervalDueRowDisplay(dueDate: LocalDate, today: LocalDate): IntervalDueRowDisplay {
    val daysOverdue = ChronoUnit.DAYS.between(dueDate, today).toInt()
    return when {
        daysOverdue > 0 -> IntervalDueRowDisplay(
            IntervalDueUrgency.OVERDUE,
            "$daysOverdue day${if (daysOverdue == 1) "" else "s"} overdue",
        )
        daysOverdue == 0 -> IntervalDueRowDisplay(IntervalDueUrgency.DUE_TODAY, "Due today")
        else -> IntervalDueRowDisplay(
            IntervalDueUrgency.NOT_DUE,
            "In ${-daysOverdue} day${if (daysOverdue == -1) "" else "s"}",
        )
    }
}
```

`app/src/test/java/com/ziv/reminders/ui/dashboard/IntervalDueRowDisplayTest.kt`:
```kotlin
package com.ziv.reminders.ui.dashboard

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class IntervalDueRowDisplayTest {

    @Test
    fun dueDateInPast_isOverdue_pluralText() {
        val display = deriveIntervalDueRowDisplay(
            dueDate = LocalDate.of(2026, 7, 24), today = LocalDate.of(2026, 7, 27),
        )
        assertEquals(IntervalDueUrgency.OVERDUE, display.urgency)
        assertEquals("3 days overdue", display.statusText)
    }

    @Test
    fun dueDateOneDayInPast_isOverdue_singularText() {
        val display = deriveIntervalDueRowDisplay(
            dueDate = LocalDate.of(2026, 7, 26), today = LocalDate.of(2026, 7, 27),
        )
        assertEquals("1 day overdue", display.statusText)
    }

    @Test
    fun dueDateIsToday_isDueToday() {
        val display = deriveIntervalDueRowDisplay(
            dueDate = LocalDate.of(2026, 7, 27), today = LocalDate.of(2026, 7, 27),
        )
        assertEquals(IntervalDueUrgency.DUE_TODAY, display.urgency)
        assertEquals("Due today", display.statusText)
    }

    @Test
    fun dueDateInFuture_isNotDue_pluralText() {
        val display = deriveIntervalDueRowDisplay(
            dueDate = LocalDate.of(2026, 7, 30), today = LocalDate.of(2026, 7, 27),
        )
        assertEquals(IntervalDueUrgency.NOT_DUE, display.urgency)
        assertEquals("In 3 days", display.statusText)
    }

    @Test
    fun dueDateOneDayInFuture_isNotDue_singularText() {
        val display = deriveIntervalDueRowDisplay(
            dueDate = LocalDate.of(2026, 7, 28), today = LocalDate.of(2026, 7, 27),
        )
        assertEquals("In 1 day", display.statusText)
    }
}
```

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.IntervalDueRowDisplayTest"`
Expected: PASS (5 tests)

6. Add the row composable:
```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IntervalDueHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.IntervalDueStatus,
    onMarkDone: (Int) -> Unit,
    onRescheduleOnly: (Int) -> Unit,
    onOpenGardenStats: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf<PickerMode?>(null) }
    val today = LocalDate.now()

    // Design review finding (accepted): the first draft used a binary due/not-due dot, "matching
    // Counter's convention" — the wrong analog. Counter's completed/not-completed is a same-day
    // fact with no concept of accumulating lateness; this kind's due-ness accumulates exactly
    // like Schedule-cursor/Computed-schedule's does (a plant overdue by 1 day and one overdue by
    // 3 weeks must not render identically), so this row now follows THEIR 3-state convention
    // instead: not-due (green) / due today (orange) / overdue (red, plus magnitude text) — not
    // Counter's binary one. The math itself is delegated to a pure, unit-tested function (Eng
    // review finding: this logic previously lived inline in the Composable with zero test
    // coverage — an argument-order swap or off-by-one would have silently flipped "3 days
    // overdue" into "in 3 days" with nothing to catch it).
    val display = deriveIntervalDueRowDisplay(status.dueDate, today)
    val dotColor = when (display.urgency) {
        IntervalDueUrgency.OVERDUE -> MaterialTheme.colorScheme.error
        IntervalDueUrgency.DUE_TODAY -> StatusOrange
        IntervalDueUrgency.NOT_DUE -> GoalGreen
    }
    val statusText = display.statusText

    // Short-press only when due (mirrors the "already completed today, tap disabled" precedent
    // from other kinds, applied here to "not currently due"). Long-press's two reschedule options
    // are deliberately NOT gated on due state, unlike short-press — a long-press is already an
    // explicit "I want to do X" choice (the user opened a menu and picked an option), so it
    // doesn't need the same "only when it makes sense" guard a single ambient tap does; someone
    // may legitimately want to water early or push the date out before it's even due (design
    // review Pass 4 finding — this asymmetry was previously present but unstated).
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (status.isDue) showPicker = PickerMode.MARK_DONE },
            onLongClick = { showMenu = true },
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painter = painterResource(R.drawable.ic_habit_garden), contentDescription = null, modifier = Modifier.size(40.dp))
            HabitStatusDot(color = dotColor)
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                // Design review finding (accepted): the first draft had no subtitle at all — every
                // other row's left block is icon+dot+Column(name, subtitle), and dropping the
                // second line was an unconsidered gap, not a deliberate departure like the
                // Statistics screen's no-streak choice is. habit.streak carries total-times-
                // watered for this kind (see HabitEngine.currentStreak's INTERVAL_DUE branch,
                // Task 2) — NOT a real streak, just the same generic per-row summary-count slot
                // repurposed, at zero extra repository calls in the dashboard-refresh hot path.
                Text("Watered ${habit.streak} time${if (habit.streak == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(text = statusText, style = MaterialTheme.typography.titleMedium)
    }

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            // Ordering rationale (design review Pass 4 finding — previously undocumented):
            // "Mark done + reschedule" stays first, matching Exercise's own [Counter, Statistics]
            // ordering (primary action first) rather than Timer's [Statistics, Reset today]
            // ordering (safe-action-first, destructive-last) — Timer's ordering exists specifically
            // to protect against a habit-driven reflex tap landing on a DESTRUCTIVE action (data
            // loss). None of this row's 3 options destroy data (mark-done/reschedule-only both
            // just write a new due date; at most the log gets one extra row), so there's no
            // destructive option to protect against here, and no option is marked isDestructive.
            options = listOf(
                RowMenuOption("Mark done + reschedule", onSelect = { showPicker = PickerMode.MARK_DONE }),
                RowMenuOption("Reschedule only", onSelect = { showPicker = PickerMode.RESCHEDULE_ONLY }),
                RowMenuOption("Statistics", onOpenGardenStats),
            ),
            onDismiss = { showMenu = false },
        )
    }

    showPicker?.let { mode ->
        IntervalDuePickerDialog(
            title = if (mode == PickerMode.MARK_DONE) "Water again in how many days?" else "Reschedule to how many days from now?",
            onConfirm = { days ->
                showPicker = null
                if (mode == PickerMode.MARK_DONE) onMarkDone(days) else onRescheduleOnly(days)
            },
            onDismiss = { showPicker = null },
        )
    }
}
```
(No new import needed for `deriveIntervalDueRowDisplay`/`IntervalDueUrgency` — `IntervalDueRowDisplay.kt`
is in the same `com.ziv.reminders.ui.dashboard` package as `DashboardScreen.kt`. `StatusOrange`/
`GoalGreen`/`Column` are already imported in this file for the other rows' composables.)
```kotlin

private enum class PickerMode { MARK_DONE, RESCHEDULE_ONLY }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: PASS

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests still green, including `DashboardDispatchTest` if it exists —
verify no dispatch-by-instance-id special case is needed here, since `IntervalDueStatus` is a
brand-new type, not a reused `CounterStatus`/etc. like Lego Kit's situation).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt
git commit -m "Add the garden-watering dashboard row, day-count picker dialog, and long-press menu"
```

---

### Task 4: `IntervalDueStatsScreen` + NavHost destination

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/ui/dashboard/IntervalDueStatsViewModel.kt`
- Create: `app/src/main/java/com/ziv/reminders/ui/dashboard/IntervalDueStatsScreen.kt`
- Modify: `app/src/main/java/com/ziv/reminders/MainActivity.kt`

**Interfaces:**
- Consumes: `IntervalDueRepository.history()`/`completedDates()` (Task 2).
- Produces: `IntervalDueStatsViewModel` (own small ViewModel over `DashboardDataSource`, same
  precedent as `ComputedScheduleStatsViewModel`); `IntervalDueStatsScreen` composable; new
  `"gardenStats"` NavHost destination, fixed single-instance (matches
  `exerciseStats`/`readingStats`/`tanakhStats`/`cppWeeklyStats`/`legoKitStats` — none of these are
  generically `habitInstanceId`-parameterized, so this one isn't either, per the design doc).

**Deliberate departure, called out for Design review:** every existing stats screen (including
`ComputedScheduleStatsScreen`, the closest log-based precedent) reuses
`HabitStatsSummary`/`ActivitySectionState` (which always renders a "Streak: N days" line) and
`HeatmapGrid`. This kind has no streak concept (design doc Premise 3), so this screen does
**not** reuse either — it needs its own minimal summary (title + total-count line only) and a
plain date list instead of a heatmap grid. This is a one-off UI component, not a generalization
of the existing stats-screen shape.

- [ ] **Step 1: Write the implementation** (no meaningful pure logic to TDD here beyond what
  Task 2's repository tests already cover — this task is UI wiring, verified by the existing
  `./gradlew.bat :app:testDebugUnitTest` suite staying green plus on-device QA, matching how
  Task 5/6 of prior kind-addition plans treated their own stats-screen wiring.)

`app/src/main/java/com/ziv/reminders/ui/dashboard/IntervalDueStatsViewModel.kt`:
```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.GARDEN_HABIT_INSTANCE_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class IntervalDueStatsUiState(
    val totalCount: Int = 0,
    val history: List<LocalDate> = emptyList(),
)

/** Deliberately its own small ViewModel over DashboardDataSource, same precedent as
 * ComputedScheduleStatsViewModel — no streak field (design doc Premise 3), so this does not
 * reuse ActivitySectionState (which always carries a streak). */
class IntervalDueStatsViewModel(private val dataSource: DashboardDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(IntervalDueStatsUiState())
    val uiState: StateFlow<IntervalDueStatsUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val instance = dataSource.habitInstanceDao.getById(GARDEN_HABIT_INSTANCE_ID) ?: return@launch
            val history = dataSource.intervalDueRepository.history(instance)
            _uiState.value = IntervalDueStatsUiState(totalCount = history.size, history = history)
        }
    }

    companion object {
        fun factory(dataSource: DashboardDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = IntervalDueStatsViewModel(dataSource) as T
            }
    }
}
```

`app/src/main/java/com/ziv/reminders/ui/dashboard/IntervalDueStatsScreen.kt`:
```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntervalDueStatsScreen(viewModel: IntervalDueStatsViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Water the garden") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // No streak line (design doc Premise 3) — total count only, unlike every other stats
            // screen's HabitStatsSummary.
            Text(
                "Total: ${state.totalCount} time${if (state.totalCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (state.history.isEmpty()) {
                EmptySectionState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    items(state.history) { date ->
                        Text(
                            date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
```

`app/src/main/java/com/ziv/reminders/MainActivity.kt`:
```kotlin
                val gardenStatsViewModel: IntervalDueStatsViewModel =
                    viewModel(factory = IntervalDueStatsViewModel.factory(container))
                // ... add alongside the other ViewModel declarations ...

                    composable("dashboard") {
                        // ... existing DashboardScreen call, add: ...
                            onOpenGardenStats = { navController.navigate("gardenStats") },
                        )
                    }
                    // ... add alongside the other composable("...Stats") destinations ...
                    composable("gardenStats") {
                        IntervalDueStatsScreen(
                            viewModel = gardenStatsViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
```

- [ ] **Step 2: Build and run the full suite**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green — this task adds no new pure-logic tests, only wiring already
covered by Task 2's repository tests and Task 3's ViewModel tests)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/IntervalDueStatsViewModel.kt app/src/main/java/com/ziv/reminders/ui/dashboard/IntervalDueStatsScreen.kt app/src/main/java/com/ziv/reminders/MainActivity.kt
git commit -m "Add the garden-watering Statistics screen (history list, no heatmap/streak)"
```

---

### Task 5: Seeding, icon, notification copy

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt`
- Modify: `app/src/main/java/com/ziv/reminders/RemindersApp.kt`
- Create: `app/src/main/res/drawable/ic_habit_garden.xml`
- Modify/Create: `app/src/test/java/com/ziv/reminders/data/HabitSeedingTest.kt` (verify whether
  this file already exists before choosing Modify vs. Create)

**Interfaces:**
- Produces: `GARDEN_HABIT_INSTANCE_ID = 6L`; seeds the `HabitInstance` row plus its
  `IntervalDueProgress(nextDueDate = today)` row (design doc Premise 4 — due immediately on
  first install).

Note: unlike the C++ Weekly instance's isolated `try/catch` (needed because its `anchorDate` was
a real `TODO()` placeholder pending a human-supplied value), this instance has no placeholder
config to fill in — `nextDueDate = today` is fully computable at seed time, so this insert can
sit alongside Exercise/Reading/Tanakh/Lego Kit's plain `insertIfAbsent` calls, no try/catch needed.

- [ ] **Step 1: Write the failing seeding test**

`app/src/test/java/com/ziv/reminders/data/HabitSeedingTest.kt` (add to existing file, or create
if none exists yet — verify first):
```kotlin
    @Test
    fun ensureHabitsSeeded_seedsGardenInstance_dueToday() = runTest {
        val db = newDb() // however this file's existing tests construct an in-memory AppDatabase
        val today = LocalDate.now()

        ensureHabitsSeeded(db.habitInstanceDao(), db.computedScheduleProgressDao())
        // If ensureHabitsSeeded's signature needs an intervalDueProgressDao param too, add it
        // here and to the production signature in Step 3 below — mirrors how
        // computedScheduleProgressDao was threaded through as an explicit parameter rather than
        // reached via a container.

        val instance = db.habitInstanceDao().getById(GARDEN_HABIT_INSTANCE_ID)
        assertEquals("Water the garden", instance?.name)
        assertEquals(HabitKind.INTERVAL_DUE.name, instance?.kind)

        val progress = db.intervalDueProgressDao().getByInstance(GARDEN_HABIT_INSTANCE_ID)
        assertEquals(today.toString(), progress?.nextDueDate)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.HabitSeedingTest"`
Expected: FAIL — `GARDEN_HABIT_INSTANCE_ID` doesn't exist, no garden seeding yet.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt` (`GARDEN_HABIT_INSTANCE_ID` already
declared in Task 1 — only `ensureHabitsSeeded`'s body changes here):
```kotlin
suspend fun ensureHabitsSeeded(
    dao: HabitInstanceDao,
    computedScheduleProgressDao: ComputedScheduleProgressDao,
    intervalDueProgressDao: IntervalDueProgressDao,
) {
    // ... existing Exercise/Reading/Tanakh/C++ Weekly/Lego Kit seeding unchanged ...

    dao.insertIfAbsent(
        HabitInstance(
            id = GARDEN_HABIT_INSTANCE_ID,
            kind = HabitKind.INTERVAL_DUE.name,
            name = "Water the garden",
            enabledDaysMask = 0b1111111, // unused by this kind (no day-of-week concept), all-days for consistency with other always-visible rows
            notificationTitle = "Reminders",
            notificationBody = "🌱 Time to water the garden!",
            counterGoal = null,
        )
    )
    intervalDueProgressDao.insertIfAbsent(
        IntervalDueProgress(habitInstanceId = GARDEN_HABIT_INSTANCE_ID, nextDueDate = LocalDate.now().toString())
    )
}
```
(`IntervalDueProgressDao.insertIfAbsent` was already declared in Task 1, with its own DAO tests
— no new DAO work needed here, just calling the method that already exists.)

`app/src/main/java/com/ziv/reminders/RemindersApp.kt` — update the call site:
```kotlin
                ensureHabitsSeeded(container.habitInstanceDao, container.computedScheduleProgressDao, container.intervalDueProgressDao)
```

`app/src/main/res/drawable/ic_habit_garden.xml` — placeholder, matching `ic_habit_legokit.xml`'s
own "placeholder only, icon artwork non-blocking" precedent:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Placeholder only (design doc Premise 6 — icon artwork explicitly non-blocking) — a green
         watering-can silhouette, standing in until a real icon is swapped in. Swapping this file
         later has zero code impact. -->
    <path android:fillColor="#2E7D32" android:pathData="M20,50 h40 a10,10 0 0 1 10,10 v10 a10,10 0 0 1 -10,10 h-40 a10,10 0 0 1 -10,-10 v-10 a10,10 0 0 1 10,-10 Z"/>
    <path android:fillColor="#2E7D32" android:pathData="M70,55 l24,-14 a6,6 0 0 1 9,5.2 v17.6 a6,6 0 0 1 -9,5.2 l-24,-14 Z"/>
    <path android:fillColor="#2E7D32" android:pathData="M30,50 v-14 a6,6 0 0 1 6,-6 h8 a6,6 0 0 1 6,6 v14 Z"/>
</vector>
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.HabitSeedingTest"`
Expected: PASS

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 5: On-device verification**

Install and run on-device (`./gradlew.bat installDebug`):
- The garden row appears on first launch, showing "Due" immediately (Premise 4) — if you install
  this after already having watered recently, use long-press "Reschedule only" right away rather
  than "Mark done + reschedule," so the first Statistics entry doesn't record a watering that
  didn't actually happen today (flagged by the `/autoplan` CEO review's independent voice).
- Short-press opens the day-count picker; confirming a value sets the row to "Not due" and the
  Statistics screen shows one logged date (today).
- Long-press shows all 3 options (Mark done + reschedule / Reschedule only / Statistics) plus
  Cancel.
- Manually set the row overdue (wait a day or adjust device clock only if this codebase's
  existing convention allows it — otherwise verify via the repository unit tests' overdue
  coverage instead, consistent with this app's stated avoidance of system-clock manipulation for
  on-device QA) and confirm pressing it logs *today's* date in Statistics, not the stale due date.
- Reschedule-only does not add a Statistics entry.
- Full regression pass on Exercise/Reading/Tanakh/C++ Weekly/Lego Kit rows — no regressions from
  the `HabitEngine`/`AppContainer`/`ensureHabitsSeeded` signature changes.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt app/src/main/java/com/ziv/reminders/RemindersApp.kt app/src/main/res/drawable/ic_habit_garden.xml app/src/test/java/com/ziv/reminders/data/HabitSeedingTest.kt
git commit -m "Seed the garden-watering INTERVAL_DUE habit instance, add icon and notification copy"
```

---

## /autoplan Phase 1 — CEO Review

Mode: **SELECTIVE EXPANSION** (default for a feature added to an established, working engine).
Premises (Step 0A) confirmed by user: new kind is the right unit of work; the 5-task sequencing
is correct; no in-app "add habit" UI needed. Alternatives (0C-bis) reconfirmed at plan level —
Approach B (new `INTERVAL_DUE` kind), already decided with real alternatives during
`/office-hours`, not re-litigated.

**Complexity check:** triggers by raw count (6+ new classes, 15+ files) but matches the exact
shape of all 4 prior kind-addition plans in this repo — not scope creep, logged as mechanical,
not reduced.

**Cherry-pick accepted (SELECTIVE EXPANSION):** preset day-count chips (3/5/7/10/14) alongside
free entry in the picker dialog — in blast radius, <30min effort. Applied to Task 3.

**Dual Voices:**
- Codex: `[codex-unavailable]` — binary not installed in this environment.
- Claude subagent (independent, no prior context): found the plan solid and internally
  consistent, matching this codebase's established conventions. Its one substantive finding: this
  is the 5th consecutive full bespoke "add a habit kind" buildout, and the per-instance dashboard/
  stats-screen UI wiring (`onOpenXStats` params, one NavHost route + ViewModel per instance, the
  `isLegoKitRow`-style instance-ID branch) still hasn't been generalized — the "no second use case
  yet" YAGNI call is wearing thin at the 5th repetition. Its own recommended fix was modest:
  document the recurring cost, don't refactor mid-flight on this plan. **Accepted** —
  added a new TODOS.md entry ("Parameterize per-instance stats screens/dashboard wiring") rather
  than changing this plan's scope. Also flagged a minor Premise-4 edge case (first-launch false
  "Due" nag if watered recently before install) — **accepted**, added a one-line note to Task 5's
  on-device verification checklist.

```
CEO DUAL VOICES — CONSENSUS TABLE:
═══════════════════════════════════════════════════════════════
  Dimension                           Claude  Codex  Consensus
  ──────────────────────────────────── ─────── ─────── ─────────
  1. Premises valid?                   Yes     N/A    N/A (single voice)
  2. Right problem to solve?           Yes     N/A    N/A (single voice)
  3. Scope calibration correct?        Yes*    N/A    N/A (single voice)
  4. Alternatives sufficiently explored?Partial N/A    N/A (single voice)
  5. Competitive/market risks covered? N/A (personal app) N/A  N/A
  6. 6-month trajectory sound?         Flagged N/A    N/A (single voice)
═══════════════════════════════════════════════════════════════
*Scope is right for THIS plan; flags a cross-plan recurring-cost pattern (see TODOS.md), not a
scope error in this plan itself. Codex unavailable this session — single-voice review, no
cross-model consensus possible; tagged [subagent-only].
```

### Review Sections 1-10

**Section 1 (Architecture):** Additive only — new dispatch branches in `HabitEngine`/
`HabitReminderReceiver`/`DashboardScreen`, zero changes to existing kinds' logic. No new coupling,
no new single point of failure beyond the existing app-wide "no error handling around Room reads"
gap (already tracked in TODOS.md, not duplicated here — this new code inherits that same
pre-existing, accepted gap, not a new one). No auth/security boundaries (local single-user app).
Rollback: git revert / uninstall APK, same as every prior plan.

**Section 2 (Error & Rescue Map):** `require(intervalDays >= 1)` is a deliberate crash-early
guard against programmer/caller error, not a rescuable condition — consistent with this
codebase's convention of not catching self-inflicted errors. No new rescuable failure modes
beyond the pre-existing, already-tracked Room-read gap.

**Section 3 (Security):** N/A — no server, no auth boundaries, no secrets, no new dependencies.
Input validation (`intervalDays >= 1`) enforced at both dialog and repository layer.

**Section 4 (Data Flow & Edge Cases):** Overdue-logging, same-day/0-day, and reschedule-while-
not-due are already covered by the repository test suite. Rapid double-tap on the picker's
Confirm button is the same accepted, low-severity race already tolerated for `onIncrement`
elsewhere in this app (Lego Kit plan's audit trail item #5) — not a new gap.

**Section 5 (Code Quality):** No DRY violations; naming matches established `IntervalDue*`-style
prefix convention; scope matches the office-hours alternatives analysis. No issues.

**Section 6 (Tests):** Full TDD breakdown already in the plan (DAO, migration, repository,
ViewModel tests) — 11 repository tests alone. Test Plan Artifact is produced during Phase 3 (Eng
review), per that phase's own required output.

**Section 7 (Performance):** N/A — single-user local SQLite, trivial data volume, no N+1/caching
concerns.

**Section 8 (Observability):** This app has no logging/metrics/alerting infrastructure anywhere
(one `Log.e` call app-wide, per `HabitSeeding.kt`'s own comment) — consistent absence, not a gap
this plan introduces.

**Section 9 (Deployment):** Personal sideload (`installDebug`/`assembleDebug`), no CI/CD, no
feature flags — matches every prior plan. Migration is additive-only (`CREATE TABLE IF NOT
EXISTS`), safe for a single-device app.

**Section 10 (Long-Term Trajectory):** Reversibility 5/5 (fully additive, nothing deleted). One
debt item identified and logged (see TODOS.md addition above) — the recurring per-instance UI-
wiring cost.

**Section 11 (Design):** Deferred to Phase 2 (dedicated Design review, next) rather than
duplicated here.

### NOT in scope
- Parameterizing per-instance stats-screen/dashboard wiring — deferred to TODOS.md (see above),
  not blocking this plan.
- Notification "mark done" action button — already 2x deferred for Lego Kit/C++ Weekly for the
  same reason (new PendingIntent/BroadcastReceiver infra); this kind's completion additionally
  requires a day-count first, making a single-tap notification action a worse fit than for those
  kinds.
- Undo for logged waterings — no per-day upsert table to reverse against (append-only log,
  matching `ComputedScheduleWatchLog`'s same no-undo precedent), consistent with this kind's
  closest analog.

### What already exists
Room schema/DAO pattern, `HabitEngine` dispatch, `RowLongPressMenu`, per-row stats-screen shape,
`ensureHabitsSeeded` — all reused verbatim, per the plan's own Architecture section.

### Dream state delta
Moves from 4 shipped kinds to 5, consistent with the master design doc's "new instance = one
data row, occasionally one new kind" trajectory. Surfaces (via the independent CEO voice) that
the UI-wiring half of that trajectory hasn't kept pace — tracked in TODOS.md for a future plan.

### Error & Rescue Registry
```
METHOD/CODEPATH                  | WHAT CAN GO WRONG        | EXCEPTION CLASS         | RESCUED? | USER SEES
----------------------------------|--------------------------|-------------------------|----------|----------
IntervalDueRepository.markDone    | intervalDays < 1         | IllegalArgumentException| N (by design — programmer/caller guard, dialog prevents in practice) | N/A (never reachable via UI)
IntervalDueRepository.* (any)     | Room I/O failure          | (app-wide, untracked)    | N — pre-existing gap | Crash (pre-existing app-wide TODOS.md item, not new)
```

### Failure Modes Registry
```
CODEPATH                        | FAILURE MODE                | RESCUED? | TEST? | USER SEES     | LOGGED?
---------------------------------|------------------------------|----------|-------|---------------|--------
markDone/rescheduleOnly          | intervalDays < 1 passed      | N (guard)| Y     | N/A (UI blocks)| N
Any Room read/write in this kind | I/O failure                  | N (pre-existing) | N | Crash (pre-existing) | N
```
No new CRITICAL GAPS beyond the pre-existing, already-tracked app-wide Room error-handling gap.

### TODOS.md updates
One entry added (see above): "Parameterize per-instance stats screens/dashboard wiring instead of
one bespoke set per instance" — P3, effort M.

### Diagrams
System architecture and data flow are covered by the plan's own Architecture section (prose form,
consistent with every prior kind-addition plan's style — none of the 4 prior plans use ASCII
diagrams for this app's simple additive dispatch pattern either).

### Stale Diagram Audit
No existing ASCII diagrams in any file this plan touches.

## Implementation Tasks
_No new tasks beyond what Task 1-5 already specify — this review found no gaps requiring
additional build-actionable work on top of the plan as written._

(JSONL task artifact skipped — `jq` is not installed in this environment; per the skill's own
fallback rule, aggregation will show "no per-phase task lists found" at the final gate.)

### Completion Summary
```
+====================================================================+
|            MEGA PLAN REVIEW — COMPLETION SUMMARY (Phase 1)         |
+====================================================================+
| Mode selected        | SELECTIVE EXPANSION                          |
| Section 1  (Arch)    | 0 issues found                              |
| Section 2  (Errors)  | 0 new gaps (1 pre-existing, already tracked)|
| Section 3  (Security)| N/A — no server/auth boundaries              |
| Section 4  (Data/UX) | 0 unhandled edge cases                       |
| Section 5  (Quality) | 0 issues found                              |
| Section 6  (Tests)   | Full TDD coverage already in plan            |
| Section 7  (Perf)    | N/A — single-user local SQLite               |
| Section 8  (Observ)  | N/A — no logging infra anywhere in this app  |
| Section 9  (Deploy)  | 0 risks — personal sideload, additive migration|
| Section 10 (Future)  | Reversibility: 5/5, debt items: 1 (logged)  |
| Section 11 (Design)  | Deferred to Phase 2                          |
+--------------------------------------------------------------------+
| NOT in scope         | written (3 items)                            |
| What already exists  | written                                       |
| Dream state delta    | written                                       |
| Error/rescue registry| 2 codepaths, 0 new CRITICAL GAPS              |
| Failure modes        | 2 total, 0 new CRITICAL GAPS                 |
| TODOS.md updates     | 1 item added                                 |
| Cherry-picks         | 1 proposed, 1 accepted (preset day-count chips)|
| Outside voice        | Claude subagent only (codex unavailable)     |
| Diagrams produced     | 0 (prose-form architecture, matches precedent)|
| Unresolved decisions | 0                                            |
+====================================================================+
```

---

## /autoplan Phase 2 — Design Review

**Step 0:** Initial design rating 6/10 pre-review (good interaction-state and dialog specificity,
but the dashboard row itself — the one part of this plan touched every day — was under-designed
relative to sibling rows). No `DESIGN.md` in this repo; proceeding on the app's own strong,
consistent de facto Material3 conventions across the 5 existing rows instead (recommending
`/design-consultation` would be low-value for a single-screen personal utility with an already-
coherent style). Visual mockups skipped — `DESIGN_NOT_AVAILABLE` (the gstack design binary isn't
set up in this environment, and this is a native Android Compose app, not a web/HTML surface the
mockup tool targets anyway). Focus: all 7 dimensions (no narrowing requested).

**Dual Voices:**
- Codex: `[codex-unavailable]`.
- Claude subagent (independent, no prior-phase context): found the plan's backend rigor strong
  but the row's own design under-specified relative to this codebase's own established, hard-won
  conventions, verified directly against the real `DashboardScreen.kt`. Design completeness
  score: 5/10 pre-fix. Findings, all **accepted and applied**:
  1. **(High)** No acknowledgment feedback after `onMarkDone`/`onRescheduleOnly` — the only
     mutating row in the app with zero tap feedback, contradicting this codebase's own documented
     rule (established by a prior plan's own design review, see `onMarkNextWatched`'s comment).
     Fixed: acknowledgment Snackbar added, no undo (matches the append-only-log precedent).
  2. **(High)** Binary due/not-due dot wrongly modeled on Counter's same-day convention instead of
     Schedule-cursor/Computed-schedule's 3-state accumulating-lateness convention — a plant
     overdue 1 day and one overdue 3 weeks rendered identically. Fixed: 3-state dot (not
     due/due today/overdue) with magnitude text ("N days overdue" / "Due today" / "In N days").
  3. **(Medium-high)** No subtitle line (every sibling row has icon+dot+name+subtitle; this row
     had name alone). Fixed: subtitle now shows total-times-watered, reusing the existing generic
     per-instance "streak" plumbing slot at zero extra repository calls (see Phase 1's Task 2
     `HabitEngine.currentStreak` change).
  4. **(Medium)** Long-press menu action ordering was unexplained, unlike Timer's explicitly
     documented "safe-first, destructive-last" rule. Fixed: documented rationale — this row's menu
     keeps the primary action first (matching Exercise's convention) because none of its 3 options
     are destructive, unlike Timer's, so there's no reflex-tap risk to protect against.
  5. **(Medium)** Tap/long-press due-gating asymmetry (short-tap gated on `isDue`, long-press
     reschedule options are not) was present but unstated. Fixed: documented as intentional — a
     long-press is already a deliberate "I want to do X" choice, unlike an ambient tap.
  6. **(Low)** Picker doesn't remember the last-used interval. **Considered, not applied** —
     the preset chips (3/5/7/10/14, from Phase 1's cherry-pick) already cover the common-case
     friction this would address; marginal value doesn't clear the bar for more state/complexity
     in this dialog. Logged as considered-and-declined, not silently dropped.
  7. **(Medium)** First-launch false-due mitigation (Phase 1's QA-checklist note) called "fragile."
     **Considered, kept as-is** — the mitigation guards a single-user, single-developer personal
     app where the one user already has the context; over-engineering a first-run dialog to guard
     against the app's only user forgetting their own quirk doesn't clear the bar for this app's
     scale (P3 pragmatic).

```
DESIGN LITMUS SCORECARD:
═══════════════════════════════════════════════════════════════
  Dimension                              Score (post-fix)
  ──────────────────────────────────────  ─────────────────
  1. Information architecture             9/10
  2. Interaction state coverage            9/10
  3. User journey & emotional arc          9/10 (was 5/10 pre-fix — feedback loop was the gap)
  4. AI slop risk                          10/10 — 0 blacklist violations (App UI classifier)
  5. Design system alignment                9/10 (no DESIGN.md, but strong de facto reuse)
  6. Responsive & accessibility            8/10 (single-device app, standard Material3 targets)
  7. Unresolved design decisions            0 remaining (all 7 findings resolved above)
═══════════════════════════════════════════════════════════════
Codex unavailable this session — single-voice review, tagged [subagent-only].
```

### Pass-by-pass detail

**Pass 1 (Information Architecture):** Left-side hierarchy (icon → dot → name → subtitle) now
matches every sibling row exactly. Right-side status text now carries distinct information from
the dot color (magnitude, not just a restatement of due/not-due).

**Pass 2 (Interaction States):** Loading/empty/error states match the app-wide accepted pattern
(no loading spinner anywhere, momentary stale-state until `refresh()` completes — same as every
row). Success state now has explicit Snackbar feedback (fix #1 above). Error state on the picker
dialog now shows supporting text (fixed during this same phase, see Task 3's
`IntervalDuePickerDialog` — caught during my own pre-subagent pass before dispatching the
independent voice).

**Pass 3 (User Journey):** Fixed feedback-loop gap (#1) directly repairs the emotional arc — the
user now gets the same "did it land?" reassurance every other row's action gives.

**Pass 4 (AI Slop Risk):** Classifier = App UI (data-dense personal utility, not
marketing/consumer). 0/11 blacklist patterns present. Material3's default Roboto typography is
the platform-native choice for Android, not a lazy web `system-ui` default — not flagged.

**Pass 5 (Design System Alignment):** No `DESIGN.md`, but near-total reuse of the existing de
facto vocabulary (`HabitStatusDot`, `RowLongPressMenu`, row layout shape) — now including the
*correct* 3-state dot convention, not the wrong 2-state one.

**Pass 6 (Responsive & Accessibility):** Single-device app, no multi-viewport concern (matches
every prior plan). Touch targets: `AssistChip`/`combinedClickable` meet standard Material3
defaults, same as every existing row.

**Pass 7 (Unresolved Design Decisions):** All 7 findings above resolved during this phase — 0
remaining.

### NOT in scope
- Picker remembering the last-used interval — considered, declined (see finding #6 above).
- A dedicated first-run onboarding dialog to prevent the false-due nag — considered, declined as
  disproportionate for a single-user app (see finding #7 above).

### What already exists
`HabitStatusDot`, `RowLongPressMenu`, the row layout shape, the Snackbar-acknowledgment pattern
(`onIncrement`/`onMarkRead`/`onMarkNextWatched`) — all now genuinely reused, not just partially.

### TODOS.md updates
None from this phase — all findings were fixed inline (in blast radius, no deferred design debt).

### Completion Summary
```
+====================================================================+
|         DESIGN REVIEW — COMPLETION SUMMARY (Phase 2)               |
+====================================================================+
| Initial rating        | 6/10 pre-review                            |
| Pass 1 (Info arch)     | 2 issues found, 2 fixed                    |
| Pass 2 (Interaction)   | 2 issues found, 2 fixed                    |
| Pass 3 (User journey)  | 1 issue found (feedback loop), fixed       |
| Pass 4 (AI slop)       | 0 issues — 0/11 blacklist patterns         |
| Pass 5 (Design system) | 0 issues — strong de facto reuse           |
| Pass 6 (Responsive/a11y)| 0 issues — matches app-wide precedent     |
| Pass 7 (Unresolved)    | 0 remaining after this phase                |
| NOT in scope           | written (2 items, both considered+declined)|
| What already exists    | written                                    |
| TODOS.md updates       | 0 (all fixed inline)                       |
| Outside voice          | Claude subagent only (codex unavailable)   |
| Design completeness    | 5/10 pre-fix -> 9/10 post-fix (avg of 7 passes)|
| Unresolved decisions   | 0                                           |
+====================================================================+
```

---

## /autoplan Phase 3 — Eng Review

**Step 0 (Scope Challenge):** Already addressed at plan level during Phase 1's CEO review
(complexity check triggers by raw file/class count but matches all 4 prior kind-addition plans —
not scope creep, not re-litigated here). Test-framework detection: JUnit4 + Robolectric,
confirmed against every existing test file this plan's precedents use.

**Dual Voices:**
- Codex: `[codex-unavailable]`.
- Claude subagent (independent, no prior-phase context): cross-checked the plan against the
  actual current production files (`AppDatabase.kt`, `AppContainer.kt`, `HabitStatus.kt`,
  `HabitSeeding.kt`, `HabitEngine.kt`, `HabitInstance.kt`, `DashboardScreen.kt`,
  `DashboardViewModel.kt`) and precedent test files. Found the migration safe and additive, the
  repository logic well-tested, and no security surface — but surfaced two concrete, verifiable
  gaps the plan's own earlier self-assessment ("0 issues found") missed. All four substantive
  findings **accepted and applied**:
  1. **(High)** `IntervalDueProgressDao.insertIfAbsent` was introduced only as an afterthought in
     Task 5's seeding step, never declared in Task 1's DAO interface, absent from Task 5's own
     file list, and completely untested — the exact "reseed resets your real due date back to
     today" regression `ComputedScheduleProgressDao`'s own
     `insertIfAbsent_rowAlreadyExists_leavesItUntouched` test already guards against for that
     kind. Fixed: `insertIfAbsent` moved into Task 1's DAO declaration with 2 new DAO tests
     mirroring that exact precedent test.
  2. **(High)** The new `HabitEngine.todayStatus` dispatch branch for `INTERVAL_DUE` had no
     dedicated test — only `currentStreak`'s dispatch was covered. Since this dispatch is a
     runtime string match (not compiler-enforced) inside `DashboardViewModel.refresh()`'s
     per-instance `map` with no try/catch, a regression here would crash the *entire* dashboard's
     refresh, not just this row. Fixed: added `todayStatus_intervalDueKind_dispatchesTo...` and
     `currentStreak_intervalDueKind_dispatchesTo...` tests to Task 2.
  3. **(Medium)** The row's due/overdue derivation math (`ChronoUnit.DAYS.between`, 3-way branch,
     singular/plural text) lived inline in a private Composable with zero test coverage — an
     argument-order swap or off-by-one would silently flip meaning with nothing to catch it.
     Fixed: extracted to a pure `deriveIntervalDueRowDisplay` function in a new plain-Kotlin file,
     with 5 dedicated unit tests (Task 3).
  4. **(Medium)** The plan's own snippets were internally inconsistent: the `HabitRow` dispatch
     branch and `DashboardScreen` call site both assumed 3 new parameters on `HabitRow`'s
     signature, but no step ever showed that signature actually being extended — an implementer
     following the snippets literally would hit an unexpected compile error. Fixed: added an
     explicit signature-extension step (Task 3, Step 3) showing the full 17-parameter `HabitRow`
     signature.
  **Considered, not applied** (Low severity, correctly judged not worth the added complexity at
  this app's personal, single-user scale): `currentStreak`'s `history(instance).size` does a full
  `interval_due_log` table read on every dashboard refresh rather than a `COUNT(*)` query — no
  `count()` DAO method exists as an alternative, and table sizes here are trivially small (a
  personal app watering a garden, not a high-frequency log). The picker's "Enter at least 1 day"
  text is technically misleading for a too-large (`Int` overflow) input rather than too-small —
  not worth a second error message for a scenario no real user hits entering a garden-watering
  interval.

```
ENG DUAL VOICES — CONSENSUS TABLE:
═══════════════════════════════════════════════════════════════
  Dimension                           Claude  Codex  Consensus
  ──────────────────────────────────── ─────── ─────── ─────────
  1. Architecture sound?               Yes     N/A    N/A (single voice)
  2. Test coverage sufficient?         Was No, now Yes (2 gaps fixed) N/A  N/A
  3. Performance risks addressed?      Yes (1 low-severity, declined) N/A  N/A
  4. Security threats covered?         Yes — N/A, no server/auth     N/A  N/A
  5. Error paths handled?              Yes (matches app-wide accepted gap) N/A N/A
  6. Deployment risk manageable?       Yes — additive migration      N/A  N/A
═══════════════════════════════════════════════════════════════
Codex unavailable this session — single-voice review, tagged [subagent-only].
```

### Section 1 (Architecture)
No structural problems. Migration verified additive-only (`CREATE TABLE IF NOT EXISTS`/
`CREATE INDEX IF NOT EXISTS`, zero `ALTER TABLE` on `habit_instance`) — matches `MIGRATION_6_7`'s
idiom exactly. `CrossHabitEvaluator`/`WeeklySummary` hardcode Exercise/Reading/Tanakh instance IDs
and never touch `INTERVAL_DUE` — the `currentStreak` repurposing (Phase 2) can't leak into
escalation logic. Dispatch changes are purely additive branches.

### Section 2 (Code Quality)
No DRY violations. `HabitRow`'s dispatch-branch inconsistency (finding #4) is now fixed. Naming
matches established convention throughout.

### Section 3 (Test Review)
```
CODE PATHS                                              GAPS FOUND -> FIXED
[+] IntervalDueRepository                                 0 gaps (11 tests, already thorough)
[+] IntervalDueProgressDao.insertIfAbsent                  1 gap -> fixed (2 new tests)
[+] HabitEngine.todayStatus (INTERVAL_DUE branch)          1 gap -> fixed (1 new test)
[+] HabitEngine.currentStreak (INTERVAL_DUE branch)        1 gap -> fixed (1 new test)
[+] deriveIntervalDueRowDisplay (row urgency/text math)    1 gap -> fixed (5 new tests, new file)
[+] HabitReminderReceiver (IntervalDueStatus branch)       0 gaps (already planned)
[+] IntervalDuePickerDialog (validation UI)                0 gaps — manual/on-device (no Compose UI test precedent in this app)

USER FLOWS                                                COVERAGE
[+] Mark done + reschedule (short-press, due)              Repository-level tested; Snackbar/UI manual
[+] Mark done + reschedule (long-press)                     Same repository path, manual UI verification
[+] Reschedule only (long-press, any due state)             Repository-level tested; manual UI verification
[+] Statistics / history view                               Manual (no Compose UI test precedent)
[+] Overdue-logging edge case                               Repository-tested (markDone_setsNextDueDate...)
[+] App-restart reseed regression                           Now repository-tested (finding #1 fix)

COVERAGE: all identified code-path gaps closed. UI-flow verification remains manual/on-device,
matching this app's established testing posture (no Compose UI test infrastructure exists
anywhere in this codebase to extend).
```
Test Plan Artifact written to
`C:\Users\zivk\.gstack\projects\Reminders\zivk-main-eng-review-test-plan-20260727-190512.md` for
`/qa`/`/qa-only` consumption.

### Section 4 (Performance)
N/A beyond the one Low-severity, declined finding above (full-table read for a count, trivial at
this app's scale). No N+1 queries, no caching needed, single-user local SQLite.

### NOT in scope
- A `count()`-based DAO alternative to `history(instance).size` — considered, declined (Low
  severity, trivial scale).
- A second error-message variant distinguishing "too large" from "too small" input — considered,
  declined (no realistic user input triggers it).

### What already exists
`ComputedScheduleProgressDao`'s `insertIfAbsent`/`upsert` pair (now mirrored exactly), the DAO/
migration/repository/dispatch test patterns from all 4 prior kind additions.

### TODOS.md updates
None from this phase — both High findings were fixed inline (correctness bugs in the plan itself,
not deferred work).

### Completion Summary
```
+====================================================================+
|            ENG REVIEW — COMPLETION SUMMARY (Phase 3)               |
+====================================================================+
| Section 1 (Architecture) | 0 issues found                          |
| Section 2 (Code Quality) | 1 issue found, 1 fixed (dispatch-branch inconsistency)|
| Section 3 (Tests)        | 4 gaps found, 4 fixed (9 new tests added)|
| Section 4 (Performance)  | 1 low-severity issue, declined (proportionate)|
| NOT in scope             | written (2 items, both considered+declined)|
| What already exists      | written                                  |
| TODOS.md updates         | 0 (fixed inline)                         |
| Test Plan Artifact       | written to ~/.gstack/projects/Reminders/ |
| Outside voice            | Claude subagent only (codex unavailable) |
| Unresolved decisions     | 0                                        |
+====================================================================+
```



