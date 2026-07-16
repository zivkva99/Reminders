# Schedule-Cursor Kind Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Schedule-cursor habit kind — a "Tanakh" chapter-a-day tracker — rebuilt on
the shared engine from Plans 1-2, reusing ReadBook's proven ordered-catch-up cursor model and
its bundled 724-chapter schedule CSV. This is the third and final habit kind named in the
design doc; once it ships, all three kinds (Counter, Timer, Schedule-cursor) work end-to-end on
one shared dashboard.

**Architecture:** A single persisted `cursorIndex` per habit instance (`ScheduleCursorProgress`,
mirrors ReadBook's `BibleReadingProgress`) tracks the index of the next unread schedule entry in
a bundled, static CSV asset — the schedule's dates never move on a missed day; falling behind
means catching up one entry at a time, never skipping ahead to "today's" entry. A pure function,
`deriveScheduleEntryStatus`, derives OnSchedule/Behind/Waiting/Finished from `(schedule,
cursorIndex, today)` — a direct, faithful port of ReadBook's `deriveBibleReadingStatus`. Unlike
ReadBook (which never had a streak concept for Tanakh), Reminders needs one: a new
`ScheduleCursorDailyProgress` table (mirrors `counter_daily_progress`'s per-day shape) tracks
whether *at least one* entry was marked read on a given calendar day — this is the streak signal
the design doc specifies ("falling behind doesn't itself break the streak, only a day with zero
chapters marked read does"), decoupled from the schedule-position status above. `currentStreak`
reuses the `StreakCalculator` built in Plan 2 unmodified. No foreground service, no new
notification channel, no new manifest components are needed — `HabitScheduler`,
`HabitReminderReceiver`, `BootReceiver`, and `RolloverReceiver` are already fully generalized by
`habitInstanceId` and need zero changes to pick up the new instance. There is no undo action for
`markRead` — Reminders' dashboard has no snackbar/undo affordance for any kind (ReadBook's Home
screen has one; Reminders' Counter/Timer kinds don't, and this kind won't either, for
consistency and because YAGNI applies equally here).

**Tech Stack:** Same as Plans 1-2 — Kotlin 2.3.0, Jetpack Compose (Material 3), Room 2.7.1 (KSP),
JUnit4 + Robolectric 4.16.1, `kotlinx-coroutines-test`. No new dependencies.

## Global Constraints

(Inherited from Plans 1-2 — still binding for every task below.)

- Package / application ID: `com.ziv.reminders`. `minSdk = 35`, `targetSdk = 36`.
- No data migration from Shape or ReadBook — the Tanakh cursor starts at 0, same as Exercise and
  Reading started at zero. (The bundled schedule CSV is static reference content, not user
  history — copying it is the same "generic asset reuse" precedent Plan 1's Task 1 already
  established for launcher icons/`gradlew`, not the kind of migration this rule forbids.)
- No in-app "add habit" UI — the Tanakh instance is inserted via `ensureHabitsSeeded`, same
  mechanism as Exercise and Reading.
- Every Room schema change ships with a real `Migration` object; never
  `fallbackToDestructiveMigration()`. This plan is the schema's v2→v3 migration.
- TDD for all pure logic and repository/dispatch code; Robolectric (`@Config(sdk = [35])`) for
  anything touching Room; every commit after a task leaves `./gradlew :app:testDebugUnitTest`
  green.
- "Enabled" means the enabled-days bitmask evaluated against today's date. The Tanakh instance is
  seeded Sun–Thu (`0b0011111`), matching Reading's mask and ReadBook's own design doc, which
  states this feature "deliberately shares that day-of-week configuration" with the reading nudge
  — and matches the schedule CSV's own cadence (it has no entries on Fri/Sat at all).
- `HabitInstance.kind` stays a plain String column — `SCHEDULE_CURSOR` is a new enum case with no
  schema implication for that column. This kind needs no new nullable config column on
  `habit_instance` (unlike `counterGoal`/`timerTargetSeconds`) — its only "config" is the shared
  bundled schedule asset, not per-instance data.

---

## File Structure

```
Reminders/
  app/src/main/assets/
    tanakh_schedule.csv                     (Create — Task 1, copied verbatim from ReadBook)
  app/src/main/java/com/ziv/reminders/
    data/
      HabitKind.kt                          (Modify — Task 1)
      ScheduleCursorProgress.kt              (Create — Task 1)
      ScheduleCursorProgressDao.kt            (Create — Task 1)
      ScheduleCursorDailyProgress.kt          (Create — Task 1)
      ScheduleCursorDailyProgressDao.kt       (Create — Task 1)
      AppDatabase.kt                          (Modify — Task 1)
      AppContainer.kt                         (Modify — Tasks 1, 4)
      ScheduleEntry.kt                        (Create — Task 2)
      ScheduleEntryStatus.kt                  (Create — Task 2)
      HabitStatus.kt                          (Modify — Task 3)
      ScheduleCursorRepository.kt             (Create — Task 3)
      HabitSeeding.kt                          (Modify — Task 6)
    engine/
      HabitEngine.kt                          (Modify — Task 4)
    scheduling/
      HabitReminderReceiver.kt                (Modify — Task 3)
    ui/dashboard/
      DashboardViewModel.kt                   (Modify — Task 5)
      DashboardScreen.kt                      (Modify — Task 5)
  app/src/test/java/com/ziv/reminders/
    data/
      ScheduleCursorProgressDaoTest.kt         (Create — Task 1)
      ScheduleCursorDailyProgressDaoTest.kt    (Create — Task 1)
      AppDatabaseMigration2To3Test.kt          (Create — Task 1)
      ScheduleEntryTest.kt                     (Create — Task 2)
      ScheduleEntryStatusTest.kt               (Create — Task 2)
      ScheduleCursorRepositoryTest.kt          (Create — Task 3)
    engine/
      HabitEngineTest.kt                       (Modify — Task 4)
    scheduling/
      HabitReminderReceiverTest.kt             (Modify — Task 4)
    ui/dashboard/
      DashboardViewModelTest.kt                (Modify — Task 5)
      TestAppContainer.kt                      (Modify — Task 4)
```

---

### Task 1: Room schema v3 — `ScheduleCursorProgress`, `ScheduleCursorDailyProgress`, migration, CSV asset

**Files:**
- Create: `app/src/main/assets/tanakh_schedule.csv`
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitKind.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/ScheduleCursorProgress.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/ScheduleCursorProgressDao.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/ScheduleCursorDailyProgress.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/ScheduleCursorDailyProgressDao.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/ScheduleCursorProgressDaoTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/ScheduleCursorDailyProgressDaoTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration2To3Test.kt`

**Interfaces:**
- Produces: `HabitKind.SCHEDULE_CURSOR`; `data class ScheduleCursorProgress(habitInstanceId: Long,
  cursorIndex: Int)`; `interface ScheduleCursorProgressDao { suspend fun getByInstance(...):
  ScheduleCursorProgress?; suspend fun upsert(...) }`; `data class ScheduleCursorDailyProgress(
  habitInstanceId: Long, date: String, entriesMarkedRead: Int, completed: Boolean)`; `interface
  ScheduleCursorDailyProgressDao { suspend fun getByDate(...): ScheduleCursorDailyProgress?;
  suspend fun upsert(...); suspend fun getCompletedDates(habitInstanceId: Long): List<String> }`;
  `AppDatabase.MIGRATION_2_3`. Consumed by: `ScheduleCursorRepository` (Task 3).

- [ ] **Step 1: Copy the schedule asset**

```bash
mkdir -p "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\assets"
cp "D:\Users\zivk\Documents\GitHub\ReadBook\app\src\main\assets\tanakh_schedule.csv" "D:\Users\zivk\Documents\GitHub\Reminders\app\src\main\assets\tanakh_schedule.csv"
```

Expected: 725 lines (1 header + 724 chapters), UTF-8 with a leading BOM, columns
`Book,ChapterNum,ChapterHeb,Date` (date format `d.M.yyyy`), spanning 2026-06-14 through
2029-03-21. Not parsed until Task 2/4 — this step only stages the file.

- [ ] **Step 2: Write the failing DAO tests**

`app/src/test/java/com/ziv/reminders/data/ScheduleCursorProgressDaoTest.kt`:
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
class ScheduleCursorProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByInstance_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.scheduleCursorProgressDao().getByInstance(3L))
        db.close()
    }

    @Test
    fun upsert_thenGetByInstance_returnsTheRow() = runTest {
        val db = newDb()
        db.scheduleCursorProgressDao().upsert(ScheduleCursorProgress(habitInstanceId = 3L, cursorIndex = 5))

        assertEquals(ScheduleCursorProgress(3L, 5), db.scheduleCursorProgressDao().getByInstance(3L))
        db.close()
    }

    @Test
    fun upsert_sameInstance_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.scheduleCursorProgressDao().upsert(ScheduleCursorProgress(3L, cursorIndex = 5))
        db.scheduleCursorProgressDao().upsert(ScheduleCursorProgress(3L, cursorIndex = 6))

        assertEquals(6, db.scheduleCursorProgressDao().getByInstance(3L)?.cursorIndex)
        db.close()
    }
}
```

`app/src/test/java/com/ziv/reminders/data/ScheduleCursorDailyProgressDaoTest.kt`:
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
class ScheduleCursorDailyProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByDate_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.scheduleCursorDailyProgressDao().getByDate(3L, "2026-07-16"))
        db.close()
    }

    @Test
    fun upsert_thenGetByDate_returnsTheRow() = runTest {
        val db = newDb()
        val progress = ScheduleCursorDailyProgress(habitInstanceId = 3L, date = "2026-07-16", entriesMarkedRead = 1, completed = true)
        db.scheduleCursorDailyProgressDao().upsert(progress)

        assertEquals(progress, db.scheduleCursorDailyProgressDao().getByDate(3L, "2026-07-16"))
        db.close()
    }

    @Test
    fun upsert_sameKey_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.scheduleCursorDailyProgressDao().upsert(ScheduleCursorDailyProgress(3L, "2026-07-16", 1, true))
        db.scheduleCursorDailyProgressDao().upsert(ScheduleCursorDailyProgress(3L, "2026-07-16", 2, true))

        assertEquals(2, db.scheduleCursorDailyProgressDao().getByDate(3L, "2026-07-16")?.entriesMarkedRead)
        db.close()
    }

    @Test
    fun getCompletedDates_returnsOnlyCompletedRowsForThatInstance() = runTest {
        val db = newDb()
        db.scheduleCursorDailyProgressDao().upsert(ScheduleCursorDailyProgress(3L, "2026-07-14", 1, true))
        db.scheduleCursorDailyProgressDao().upsert(ScheduleCursorDailyProgress(3L, "2026-07-15", 0, false))
        db.scheduleCursorDailyProgressDao().upsert(ScheduleCursorDailyProgress(9L, "2026-07-14", 1, true)) // different instance

        assertEquals(listOf("2026-07-14"), db.scheduleCursorDailyProgressDao().getCompletedDates(3L))
        db.close()
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ScheduleCursorProgressDaoTest" --tests "com.ziv.reminders.data.ScheduleCursorDailyProgressDaoTest"`
Expected: FAIL — the entities/DAOs/`AppDatabase` accessors don't exist yet (compile error).

- [ ] **Step 4: Write the schema implementation**

`app/src/main/java/com/ziv/reminders/data/HabitKind.kt`:
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
}
```

`app/src/main/java/com/ziv/reminders/data/ScheduleCursorProgress.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single row per habit instance. cursorIndex is the index of the next unread entry in the
 * bundled schedule asset (see ScheduleEntry.kt) — mirrors ReadBook's BibleReadingProgress. The
 * schedule's dates never move; falling behind means catching up one entry at a time, never
 * skipping ahead to "today's" entry.
 */
@Entity(tableName = "schedule_cursor_progress")
data class ScheduleCursorProgress(
    @PrimaryKey val habitInstanceId: Long,
    val cursorIndex: Int,
)
```

`app/src/main/java/com/ziv/reminders/data/ScheduleCursorProgressDao.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ScheduleCursorProgressDao {
    @Query("SELECT * FROM schedule_cursor_progress WHERE habitInstanceId = :habitInstanceId")
    suspend fun getByInstance(habitInstanceId: Long): ScheduleCursorProgress?

    @Upsert
    suspend fun upsert(progress: ScheduleCursorProgress)
}
```

`app/src/main/java/com/ziv/reminders/data/ScheduleCursorDailyProgress.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity

/**
 * Tracks whether at least one schedule entry was marked read on a given calendar day — this is
 * the streak signal (see the design doc: "counts consecutive calendar days with at least one
 * chapter marked read... falling behind doesn't itself break the streak"), distinct from
 * cursorIndex's running position through the whole schedule. Mirrors counter_daily_progress's
 * per-day shape.
 */
@Entity(tableName = "schedule_cursor_daily_progress", primaryKeys = ["habitInstanceId", "date"])
data class ScheduleCursorDailyProgress(
    val habitInstanceId: Long,
    val date: String,
    val entriesMarkedRead: Int,
    val completed: Boolean,
)
```

`app/src/main/java/com/ziv/reminders/data/ScheduleCursorDailyProgressDao.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ScheduleCursorDailyProgressDao {
    @Query("SELECT * FROM schedule_cursor_daily_progress WHERE habitInstanceId = :habitInstanceId AND date = :date")
    suspend fun getByDate(habitInstanceId: Long, date: String): ScheduleCursorDailyProgress?

    @Upsert
    suspend fun upsert(progress: ScheduleCursorDailyProgress)

    @Query("SELECT date FROM schedule_cursor_daily_progress WHERE habitInstanceId = :habitInstanceId AND completed = 1")
    suspend fun getCompletedDates(habitInstanceId: Long): List<String>
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
    ],
    version = 3,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitInstanceDao(): HabitInstanceDao
    abstract fun counterDailyProgressDao(): CounterDailyProgressDao
    abstract fun timerDailyProgressDao(): TimerDailyProgressDao
    abstract fun scheduleCursorProgressDao(): ScheduleCursorProgressDao
    abstract fun scheduleCursorDailyProgressDao(): ScheduleCursorDailyProgressDao

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

        /** Adds Schedule-cursor kind support: a per-instance running cursor position table, plus
         * its own daily-progress table for streak tracking (mirrors timer_daily_progress's
         * shape). No new habit_instance column — unlike Counter/Timer, this kind's only "config"
         * is the shared bundled schedule asset, not per-instance data. Never
         * fallbackToDestructiveMigration() — see Global Constraints. */
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
    }
}
```

`app/src/main/java/com/ziv/reminders/data/AppContainer.kt` (only the migration wiring and new
DAO getters change here — `scheduleCursorRepository`/`habitEngine` wiring comes in Task 4):
```kotlin
package com.ziv.reminders.data

import android.content.Context
import androidx.room.Room
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.scheduling.HabitScheduler

/** Manual DI — no framework needed at this app's size. One instance, owned by RemindersApp. */
class AppContainer(context: Context) : DashboardDataSource {
    private val appContext = context.applicationContext

    private val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "reminders.db")
            // Never fallbackToDestructiveMigration() — see Global Constraints.
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    override val habitInstanceDao get() = db.habitInstanceDao()
    val counterDailyProgressDao get() = db.counterDailyProgressDao()
    val timerDailyProgressDao get() = db.timerDailyProgressDao()
    val scheduleCursorProgressDao get() = db.scheduleCursorProgressDao()
    val scheduleCursorDailyProgressDao get() = db.scheduleCursorDailyProgressDao()
    override val counterHabitRepository: CounterHabitRepository by lazy { CounterHabitRepository(counterDailyProgressDao) }
    val timerHabitRepository: TimerHabitRepository by lazy { TimerHabitRepository(timerDailyProgressDao, SystemClock) }
    override val habitEngine: HabitEngine by lazy { HabitEngine(counterHabitRepository, timerHabitRepository) }
    val habitScheduler: HabitScheduler by lazy { HabitScheduler(appContext) }
}

interface DashboardDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
}
```

Note: this is an intentionally incomplete intermediate state — `HabitEngine(counterHabitRepository,
timerHabitRepository)` still has its Plan-2 two-argument shape here; Task 4 changes it to three
arguments once `ScheduleCursorRepository` exists (Task 3). This file compiles and all existing
tests stay green after this task alone.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ScheduleCursorProgressDaoTest" --tests "com.ziv.reminders.data.ScheduleCursorDailyProgressDaoTest"`
Expected: PASS (7 tests)

- [ ] **Step 6: Write the failing migration test**

`app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration2To3Test.kt`:
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
class AppDatabaseMigration2To3Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate2To3_preservesExistingRows_andAddsScheduleCursorTables() {
        // Seed a v2 database with real Exercise + Reading data, exactly as an already-installed
        // app (post-Plan-2) would have.
        helper.createDatabase(TEST_DB_NAME, 2).apply {
            execSQL(
                "INSERT INTO habit_instance (id, kind, name, enabledDaysMask, notificationTitle, notificationBody, counterGoal, timerTargetSeconds) " +
                    "VALUES (1, 'COUNTER', 'Exercise', 127, 't', 'b', 5, NULL)"
            )
            execSQL(
                "INSERT INTO timer_daily_progress (habitInstanceId, date, targetSeconds, remainingSeconds, completed, completedAt, activeSessionStartedAt) " +
                    "VALUES (2, '2026-07-15', 900, 0, 1, 123, NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 3, true, AppDatabase.MIGRATION_2_3)

        migrated.query("SELECT name FROM habit_instance WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Exercise", cursor.getString(0))
        }
        migrated.query("SELECT completed FROM timer_daily_progress WHERE habitInstanceId = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM schedule_cursor_progress").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM schedule_cursor_daily_progress").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }
}
```

- [ ] **Step 7: Run the migration test, then the full suite**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.AppDatabaseMigration2To3Test"`
Expected: PASS (1 test)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests still green)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/assets/tanakh_schedule.csv app/src/main/java/com/ziv/reminders/data/HabitKind.kt app/src/main/java/com/ziv/reminders/data/ScheduleCursorProgress.kt app/src/main/java/com/ziv/reminders/data/ScheduleCursorProgressDao.kt app/src/main/java/com/ziv/reminders/data/ScheduleCursorDailyProgress.kt app/src/main/java/com/ziv/reminders/data/ScheduleCursorDailyProgressDao.kt app/src/main/java/com/ziv/reminders/data/AppDatabase.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/test/java/com/ziv/reminders/data/ScheduleCursorProgressDaoTest.kt app/src/test/java/com/ziv/reminders/data/ScheduleCursorDailyProgressDaoTest.kt app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration2To3Test.kt
git commit -m "Add Room schema v3 for Schedule-cursor kind + Tanakh schedule asset"
```

---

### Task 2: `ScheduleEntry`, `parseTanakhSchedule`, `ScheduleEntryStatus`

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/data/ScheduleEntry.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/ScheduleEntryStatus.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/ScheduleEntryTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/ScheduleEntryStatusTest.kt`

**Interfaces:**
- Produces: `data class ScheduleEntry(book: String, chapterHeb: String, date: LocalDate)`; `fun
  parseTanakhSchedule(csvText: String): List<ScheduleEntry>`; `sealed interface
  ScheduleEntryStatus { OnSchedule(entry); Behind(entry, dueCount); Waiting(entry); Finished }`;
  `fun deriveScheduleEntryStatus(schedule: List<ScheduleEntry>, cursorIndex: Int, today:
  LocalDate): ScheduleEntryStatus`. Both pure functions, no Android/Room dependency — consumed by
  `ScheduleCursorRepository` (Task 3) and `AppContainer` (Task 4, for the real bundled asset).

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/ziv/reminders/data/ScheduleEntryTest.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleEntryTest {

    @Test
    fun parseTanakhSchedule_parsesEveryFieldFromAQuotedCsvRow() {
        val csv = "\"Book\",\"ChapterNum\",\"ChapterHeb\",\"Date\"\n" +
            "\"יהושע\",\"19\",\"י״ט\",\"14.6.2026\"\n"

        val entries = parseTanakhSchedule(csv)

        assertEquals(1, entries.size)
        assertEquals(ScheduleEntry(book = "יהושע", chapterHeb = "י״ט", date = LocalDate.of(2026, 6, 14)), entries[0])
    }

    @Test
    fun parseTanakhSchedule_parsesMultipleRowsInOrder() {
        val csv = "\"Book\",\"ChapterNum\",\"ChapterHeb\",\"Date\"\n" +
            "\"יהושע\",\"19\",\"י״ט\",\"14.6.2026\"\n" +
            "\"יהושע\",\"20\",\"כ׳\",\"15.6.2026\"\n"

        val entries = parseTanakhSchedule(csv)

        assertEquals(2, entries.size)
        assertEquals(LocalDate.of(2026, 6, 14), entries[0].date)
        assertEquals(LocalDate.of(2026, 6, 15), entries[1].date)
    }

    @Test
    fun parseTanakhSchedule_skipsBlankLines() {
        val csv = "\"Book\",\"ChapterNum\",\"ChapterHeb\",\"Date\"\n" +
            "\"יהושע\",\"19\",\"י״ט\",\"14.6.2026\"\n" +
            "\n"

        assertEquals(1, parseTanakhSchedule(csv).size)
    }

    @Test
    fun parseTanakhSchedule_emptyBody_returnsEmptyList() {
        val csv = "\"Book\",\"ChapterNum\",\"ChapterHeb\",\"Date\"\n"

        assertEquals(emptyList(), parseTanakhSchedule(csv))
    }
}
```

`app/src/test/java/com/ziv/reminders/data/ScheduleEntryStatusTest.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ScheduleEntryStatusTest {

    private val schedule = listOf(
        ScheduleEntry("א", "א׳", LocalDate.of(2026, 7, 12)), // Sunday
        ScheduleEntry("א", "ב׳", LocalDate.of(2026, 7, 13)), // Monday
        ScheduleEntry("א", "ג׳", LocalDate.of(2026, 7, 14)), // Tuesday
    )

    @Test
    fun deriveScheduleEntryStatus_cursorAtTodaysEntry_isOnSchedule() {
        val status = deriveScheduleEntryStatus(schedule, cursorIndex = 0, today = LocalDate.of(2026, 7, 12))

        assertEquals(ScheduleEntryStatus.OnSchedule(schedule[0]), status)
    }

    @Test
    fun deriveScheduleEntryStatus_cursorEntryInThePast_isBehindWithCorrectDueCount() {
        // Cursor still at the Sunday entry, but it's now Tuesday — 3 entries due (Sun/Mon/Tue).
        val status = deriveScheduleEntryStatus(schedule, cursorIndex = 0, today = LocalDate.of(2026, 7, 14))

        assertIs<ScheduleEntryStatus.Behind>(status)
        assertEquals(schedule[0], (status as ScheduleEntryStatus.Behind).entry)
        assertEquals(3, status.dueCount)
    }

    @Test
    fun deriveScheduleEntryStatus_cursorEntryInTheFuture_isWaiting() {
        val status = deriveScheduleEntryStatus(schedule, cursorIndex = 1, today = LocalDate.of(2026, 7, 12))

        assertEquals(ScheduleEntryStatus.Waiting(schedule[1]), status)
    }

    @Test
    fun deriveScheduleEntryStatus_cursorPastTheEndOfSchedule_isFinished() {
        val status = deriveScheduleEntryStatus(schedule, cursorIndex = 3, today = LocalDate.of(2026, 7, 20))

        assertEquals(ScheduleEntryStatus.Finished, status)
    }

    @Test
    fun deriveScheduleEntryStatus_neverSkipsAheadToTodaysEntry_evenWhileBehind() {
        // Cursor at index 0 (Sunday, unread), today is Tuesday — the next entry to read is still
        // Sunday's, never Tuesday's, regardless of how far behind.
        val status = deriveScheduleEntryStatus(schedule, cursorIndex = 0, today = LocalDate.of(2026, 7, 14))

        assertEquals(schedule[0], (status as ScheduleEntryStatus.Behind).entry)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ScheduleEntryTest" --tests "com.ziv.reminders.data.ScheduleEntryStatusTest"`
Expected: FAIL — `ScheduleEntry`, `parseTanakhSchedule`, `ScheduleEntryStatus`,
`deriveScheduleEntryStatus` don't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/data/ScheduleEntry.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ScheduleEntry(val book: String, val chapterHeb: String, val date: LocalDate)

private val SCHEDULE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d.M.yyyy")

/**
 * Parses the bundled schedule CSV: header row `Book,ChapterNum,ChapterHeb,Date`, every field
 * double-quoted, no embedded commas/quotes in any value — the same format ReadBook's Tanakh
 * schedule export uses (tanakh_schedule.csv, copied verbatim in Task 1). Pure function, no
 * Android dependency, so it's testable without Robolectric.
 */
fun parseTanakhSchedule(csvText: String): List<ScheduleEntry> =
    csvText.lineSequence()
        .drop(1) // header
        .filter { it.isNotBlank() }
        .map { line ->
            val fields = line.split(",").map { it.trim().removeSurrounding("\"") }
            ScheduleEntry(
                book = fields[0],
                chapterHeb = fields[2],
                date = LocalDate.parse(fields[3], SCHEDULE_DATE_FORMATTER),
            )
        }
        .toList()
```

`app/src/main/java/com/ziv/reminders/data/ScheduleEntryStatus.kt`:
```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ScheduleEntryTest" --tests "com.ziv.reminders.data.ScheduleEntryStatusTest"`
Expected: PASS (9 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/ScheduleEntry.kt app/src/main/java/com/ziv/reminders/data/ScheduleEntryStatus.kt app/src/test/java/com/ziv/reminders/data/ScheduleEntryTest.kt app/src/test/java/com/ziv/reminders/data/ScheduleEntryStatusTest.kt
git commit -m "Add ScheduleEntry parsing and pure schedule-position status derivation"
```

---

### Task 3: `ScheduleCursorRepository`, `HabitStatus.ScheduleCursorStatus`, and the receiver compile-fix

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitStatus.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/ScheduleCursorRepository.kt`
- Modify: `app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/ScheduleCursorRepositoryTest.kt`

**Interfaces:**
- Consumes: `ScheduleCursorProgress`, `ScheduleCursorDailyProgress`, their DAOs (Task 1);
  `ScheduleEntry`, `ScheduleEntryStatus`, `deriveScheduleEntryStatus` (Task 2).
- Produces: `HabitStatus.ScheduleCursorStatus(book: String?, chapterHeb: String?, dueCount: Int,
  completed: Boolean, finished: Boolean)`; `class ScheduleCursorRepository(progressDao:
  ScheduleCursorProgressDao, dailyProgressDao: ScheduleCursorDailyProgressDao, schedule:
  List<ScheduleEntry>) { suspend fun todayStatus(...): HabitStatus.ScheduleCursorStatus; suspend
  fun markRead(instance, today); suspend fun currentStreak(instance, today): Int }` — consumed by
  `HabitEngine` (Task 4), the dashboard (Task 5).

Note on `HabitReminderReceiver`'s `when (status)`: this is an expression assigned to a `val`
(`val completed = when (status) { ... }`), so adding a new `HabitStatus` subtype makes it
non-exhaustive — a genuine compile error in `main` sourceSet code that must be fixed in this same
task. (By contrast, `DashboardScreen`'s `HabitRow` dispatches via a `when` used as a *statement*,
not an expression — Kotlin doesn't require that one to be exhaustive, so it compiles unmodified
and Task 5 adds the new branch as pure, non-urgent scope expansion, not a forced fix.)

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ziv/reminders/data/ScheduleCursorRepositoryTest.kt`:
```kotlin
package com.ziv.reminders.data

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

class ScheduleCursorRepositoryTest {

    private val schedule = listOf(
        ScheduleEntry("א", "א׳", LocalDate.of(2026, 7, 12)), // Sunday
        ScheduleEntry("א", "ב׳", LocalDate.of(2026, 7, 13)), // Monday
    )
    private val instance = HabitInstance(
        id = 3L, kind = "SCHEDULE_CURSOR", name = "Tanakh", enabledDaysMask = 0b0011111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null,
    )

    @Test
    fun todayStatus_noProgressYet_reportsFirstEntryNotCompleted() = runTest {
        val repo = ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule)

        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 7, 12))

        assertEquals(HabitStatus.ScheduleCursorStatus("א", "א׳", dueCount = 0, completed = false, finished = false), status)
    }

    @Test
    fun markRead_advancesTheCursorByExactlyOne() = runTest {
        val progressDao = FakeScheduleCursorProgressDao()
        val repo = ScheduleCursorRepository(progressDao, FakeScheduleCursorDailyProgressDao(), schedule)

        repo.markRead(instance, today = LocalDate.of(2026, 7, 12))

        assertEquals(1, progressDao.rows[3L]?.cursorIndex)
    }

    @Test
    fun markRead_marksTodayCompletedForStreakPurposes() = runTest {
        val dailyDao = FakeScheduleCursorDailyProgressDao()
        val repo = ScheduleCursorRepository(FakeScheduleCursorProgressDao(), dailyDao, schedule)

        repo.markRead(instance, today = LocalDate.of(2026, 7, 12))

        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 7, 13))
        assertTrue(status.completed.let { true }) // sanity: repository call succeeds
        assertEquals(true, dailyDao.rows[3L to "2026-07-12"]?.completed)
    }

    @Test
    fun todayStatus_afterMarkingReadToday_reflectsCompletedTrue() = runTest {
        val repo = ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule)
        val today = LocalDate.of(2026, 7, 12)

        repo.markRead(instance, today)
        val status = repo.todayStatus(instance, today)

        assertTrue(status.completed)
    }

    @Test
    fun todayStatus_fallingBehind_reportsDueCount() = runTest {
        val repo = ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule)

        // Cursor still at index 0 (Sunday), but today is Monday — 2 entries due.
        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 7, 13))

        assertEquals(2, status.dueCount)
        assertFalse(status.finished)
    }

    @Test
    fun todayStatus_scheduleExhausted_isFinished() = runTest {
        val progressDao = FakeScheduleCursorProgressDao()
        progressDao.rows[3L] = ScheduleCursorProgress(3L, cursorIndex = schedule.size)
        val repo = ScheduleCursorRepository(progressDao, FakeScheduleCursorDailyProgressDao(), schedule)

        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 7, 20))

        assertTrue(status.finished)
        assertEquals(null, status.book)
    }

    @Test
    fun currentStreak_delegatesToStreakCalculatorWithTheInstancesEnabledDaysMask() = runTest {
        val dailyDao = FakeScheduleCursorDailyProgressDao()
        val sunday = LocalDate.of(2026, 7, 12)
        dailyDao.rows[3L to sunday.toString()] = ScheduleCursorDailyProgress(3L, sunday.toString(), 1, true)
        val repo = ScheduleCursorRepository(FakeScheduleCursorProgressDao(), dailyDao, schedule)

        assertEquals(1, repo.currentStreak(instance, sunday))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ScheduleCursorRepositoryTest"`
Expected: FAIL — `ScheduleCursorRepository` and `HabitStatus.ScheduleCursorStatus` don't exist
yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/data/HabitStatus.kt`:
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
    ) : HabitStatus
}
```

`app/src/main/java/com/ziv/reminders/data/ScheduleCursorRepository.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate

/**
 * Combines the pure schedule-position status (deriveScheduleEntryStatus) with a per-day "did I
 * mark anything today" flag to produce the engine-wide HabitStatus.ScheduleCursorStatus.
 * completed reflects only today's activity (streak-relevant, per the design doc's rule), not
 * whether the whole backlog is cleared — matching Counter/Timer's shared "todayStatus.completed"
 * contract used generically by HabitEngine/HabitReminderReceiver/the dashboard checkmark. No
 * undo — Reminders' dashboard has no snackbar/undo affordance for any kind, so markRead is a
 * one-way action here, consistent with Counter's increment and Timer's start/stop.
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
        progressDao.upsert(ScheduleCursorProgress(instance.id, cursorIndex + 1))

        val key = today.toString()
        val newCount = (dailyProgressDao.getByDate(instance.id, key)?.entriesMarkedRead ?: 0) + 1
        dailyProgressDao.upsert(ScheduleCursorDailyProgress(instance.id, key, entriesMarkedRead = newCount, completed = true))
    }

    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int {
        val completedDates = dailyProgressDao.getCompletedDates(instance.id)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
        return StreakCalculator.calculate(completedDates, instance.enabledDaysMask, today)
    }
}
```

`app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt` — change only the
`when` block inside `onReceive`'s coroutine:
```kotlin
                val status = engine.todayStatus(instance, today())
                val completed = when (status) {
                    is HabitStatus.CounterStatus -> status.completed
                    is HabitStatus.TimerStatus -> status.completed
                    is HabitStatus.ScheduleCursorStatus -> status.completed
                }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ScheduleCursorRepositoryTest"`
Expected: PASS (7 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests, including `HabitReminderReceiverTest`, still green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitStatus.kt app/src/main/java/com/ziv/reminders/data/ScheduleCursorRepository.kt app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt app/src/test/java/com/ziv/reminders/data/ScheduleCursorRepositoryTest.kt
git commit -m "Add ScheduleCursorRepository and HabitStatus.ScheduleCursorStatus"
```

---

### Task 4: `HabitEngine` dispatch extended for `SCHEDULE_CURSOR`

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/engine/HabitEngine.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`
- Modify: `app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt`
- Modify: `app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt`
- Modify: `app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt`

**Interfaces:**
- Consumes: `ScheduleCursorRepository` (Task 3).
- Produces: `HabitEngine(counterRepository, timerRepository, scheduleCursorRepository)` — the
  constructor signature change. Every existing call site that constructs `HabitEngine(...)` must
  be updated in this same task for the module to compile (same reason as Plan 2's Task 4:
  Kotlin compiles main+test source sets together). `DashboardDataSource` gains `val
  scheduleCursorRepository: ScheduleCursorRepository` — needed because, unlike Timer's
  `onToggleTimer` (which routes through a Service), Schedule-cursor's `markRead` is a direct,
  synchronous repository call the dashboard ViewModel needs access to (Task 5).

- [ ] **Step 1: Write the failing test**

Full `app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt`:
```kotlin
package com.ziv.reminders.engine

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
    private val schedule = listOf(ScheduleEntry("א", "א׳", LocalDate.of(2026, 7, 14)))
    private val today = LocalDate.of(2026, 7, 14)

    private fun newEngine(): HabitEngine = HabitEngine(
        CounterHabitRepository(FakeCounterDailyProgressDao()),
        TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
        ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule),
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
        )

        assertEquals(1, engine.currentStreak(timerInstance, today))
    }

    @Test
    fun todayStatus_scheduleCursorKind_dispatchesToScheduleCursorRepository() = runTest {
        val status = newEngine().todayStatus(scheduleCursorInstance, today)

        assertEquals(HabitStatus.ScheduleCursorStatus("א", "א׳", dueCount = 0, completed = false, finished = false), status)
    }

    @Test
    fun currentStreak_scheduleCursorKind_dispatchesToScheduleCursorRepository() = runTest {
        val dailyDao = FakeScheduleCursorDailyProgressDao()
        dailyDao.rows[3L to "2026-07-13"] = ScheduleCursorDailyProgress(3L, "2026-07-13", 1, true)
        val engine = HabitEngine(
            CounterHabitRepository(FakeCounterDailyProgressDao()),
            TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDao(), dailyDao, schedule),
        )

        assertEquals(1, engine.currentStreak(scheduleCursorInstance, today))
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
Expected: FAIL — `HabitEngine`'s constructor doesn't accept a third argument yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/engine/HabitEngine.kt`:
```kotlin
package com.ziv.reminders.engine

import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitKind
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.data.TimerHabitRepository
import java.time.LocalDate

/**
 * Dispatches the two calls every kind can answer generically. Write actions (Counter's
 * increment, Timer's start/stop, Schedule-cursor's markRead) deliberately stay on each kind's
 * own repository, not here — see Plan 1's Architecture section for why.
 */
class HabitEngine(
    private val counterRepository: CounterHabitRepository,
    private val timerRepository: TimerHabitRepository,
    private val scheduleCursorRepository: ScheduleCursorRepository,
) {

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus =
        when (instance.kind) {
            HabitKind.COUNTER.name -> counterRepository.todayStatus(instance, today)
            HabitKind.TIMER.name -> timerRepository.todayStatus(instance, today)
            HabitKind.SCHEDULE_CURSOR.name -> scheduleCursorRepository.todayStatus(instance, today)
            else -> throw IllegalArgumentException("Unknown habit kind: ${instance.kind}")
        }

    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int =
        when (instance.kind) {
            HabitKind.COUNTER.name -> counterRepository.currentStreak(instance, today)
            HabitKind.TIMER.name -> timerRepository.currentStreak(instance, today)
            HabitKind.SCHEDULE_CURSOR.name -> scheduleCursorRepository.currentStreak(instance, today)
            else -> throw IllegalArgumentException("Unknown habit kind: ${instance.kind}")
        }
}
```

`app/src/main/java/com/ziv/reminders/data/AppContainer.kt` (full file):
```kotlin
package com.ziv.reminders.data

import android.content.Context
import androidx.room.Room
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.scheduling.HabitScheduler

/** Manual DI — no framework needed at this app's size. One instance, owned by RemindersApp. */
class AppContainer(context: Context) : DashboardDataSource {
    private val appContext = context.applicationContext

    private val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "reminders.db")
            // Never fallbackToDestructiveMigration() — see Global Constraints.
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    override val habitInstanceDao get() = db.habitInstanceDao()
    val counterDailyProgressDao get() = db.counterDailyProgressDao()
    val timerDailyProgressDao get() = db.timerDailyProgressDao()
    val scheduleCursorProgressDao get() = db.scheduleCursorProgressDao()
    val scheduleCursorDailyProgressDao get() = db.scheduleCursorDailyProgressDao()
    override val counterHabitRepository: CounterHabitRepository by lazy { CounterHabitRepository(counterDailyProgressDao) }
    val timerHabitRepository: TimerHabitRepository by lazy { TimerHabitRepository(timerDailyProgressDao, SystemClock) }

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
    val habitScheduler: HabitScheduler by lazy { HabitScheduler(appContext) }
}

interface DashboardDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val scheduleCursorRepository: ScheduleCursorRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
}
```

`app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt` (full file — `schedule`
defaults to `emptyList()` so every existing `TestAppContainer(db)` call site keeps compiling
unmodified):
```kotlin
package com.ziv.reminders.ui.dashboard

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
    private val timerHabitRepository = TimerHabitRepository(db.timerDailyProgressDao(), SystemClock)
    override val scheduleCursorRepository = ScheduleCursorRepository(db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), schedule)
    override val habitEngine = HabitEngine(counterHabitRepository, timerHabitRepository, scheduleCursorRepository)
}
```

In `app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt`, update both
`receiver.habitEngineOverride = HabitEngine(...)` call sites to add a third argument:
```kotlin
        receiver.habitEngineOverride = HabitEngine(
            CounterHabitRepository(db.counterDailyProgressDao()),
            com.ziv.reminders.data.TimerHabitRepository(db.timerDailyProgressDao(), com.ziv.reminders.data.SystemClock),
            com.ziv.reminders.data.ScheduleCursorRepository(
                db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList(),
            ),
        )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.engine.HabitEngineTest"`
Expected: PASS (7 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests, including `DashboardViewModelTest` and `HabitReminderReceiverTest`, green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/engine/HabitEngine.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt
git commit -m "Extend HabitEngine dispatch for SCHEDULE_CURSOR kind"
```

---

### Task 5: Dashboard — Schedule-cursor row and tap-to-mark-read

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Produces: `DashboardViewModel.onMarkRead(instanceId: Long)` — a direct, synchronous repository
  call + `refresh()`, mirroring `onIncrement`'s exact pattern (unlike Timer's `onToggleTimer`,
  there's no foreground service or async race to avoid here — `ScheduleCursorRepository.markRead`
  is a plain suspend call the ViewModel's own coroutine can await directly, so no optimistic
  local state flip is needed).

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt` (new imports
and one new test, appended to the existing file):
```kotlin
    @Test
    fun onMarkRead_advancesTheCursorAndMarksTodayCompleted() = runTest {
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

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.ScheduleCursorStatus
        assertTrue(status.completed)

        db.close()
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `DashboardViewModel` has no `onMarkRead` method and `TestAppContainer` doesn't
accept a `schedule` argument yet in this test's usage (compile error) — actually `TestAppContainer`
already gained the defaulted `schedule` parameter in Task 4, so only `onMarkRead` is missing;
confirm the failure is specifically "unresolved reference: onMarkRead".

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt` — add this method
alongside `onIncrement` (rest of the file unchanged from Plan 2's final state):
```kotlin
    fun onMarkRead(instanceId: Long) {
        viewModelScope.launch {
            val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return@launch
            dataSource.scheduleCursorRepository.markRead(instance, LocalDate.now())
            refresh()
        }
    }
```

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt` — three changes:

1. Add the new branch to `HabitRow` and thread a new callback through:
```kotlin
@Composable
private fun HabitRow(
    habit: HabitRowUiState,
    onIncrement: () -> Unit,
    onToggleTimer: (Int) -> Unit,
    onMarkRead: () -> Unit,
) {
    when (habit.status) {
        is HabitStatus.CounterStatus -> CounterHabitRow(habit, habit.status, onIncrement)
        is HabitStatus.TimerStatus -> TimerHabitRow(habit, habit.status, onToggleTimer)
        is HabitStatus.ScheduleCursorStatus -> ScheduleCursorHabitRow(habit, habit.status, onMarkRead)
    }
}
```

2. Add the new composable (place after `TimerHabitRow`):
```kotlin
@Composable
private fun ScheduleCursorHabitRow(habit: HabitRowUiState, status: HabitStatus.ScheduleCursorStatus, onMarkRead: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onMarkRead),
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

3. Update `DashboardScreen`'s call site to pass the new callback:
```kotlin
            HabitRow(
                habit = habit,
                onIncrement = { viewModel.onIncrement(habit.instanceId) },
                onToggleTimer = { displayedRemainingSeconds ->
                    viewModel.onToggleTimer(habit.instanceId, context, displayedRemainingSeconds)
                },
                onMarkRead = { viewModel.onMarkRead(habit.instanceId) },
            )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: PASS (6 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (full suite green)

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt
git commit -m "Wire Schedule-cursor tap-to-mark-read into the dashboard"
```

---

### Task 6: Seed the Tanakh habit instance

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt`

**Interfaces:**
- Produces: `TANAKH_HABIT_INSTANCE_ID = 3L`; `ensureHabitsSeeded` now also seeds the Tanakh
  Schedule-cursor instance.

No manifest changes, no `RemindersApp` changes — `HabitScheduler`/`HabitReminderReceiver`/
`BootReceiver`/`RolloverReceiver` already iterate every `HabitInstance` generically by id, and
this kind has no crash-recoverable "active session" state (unlike Timer's `reconcileCrashedSessions`)
to reconcile at startup. This is the "zero new classes per new instance" success criterion in
its purest form: one data row, and the entire scheduling/reminder/dashboard-filter pipeline picks
it up automatically.

- [ ] **Step 1: Seed the Tanakh habit instance**

Full `app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt`:
```kotlin
package com.ziv.reminders.data

const val EXERCISE_HABIT_INSTANCE_ID = 1L
const val READING_HABIT_INSTANCE_ID = 2L
const val TANAKH_HABIT_INSTANCE_ID = 3L

/**
 * Idempotent — safe to call on every app startup (RemindersApp.onCreate). insertIfAbsent's
 * IGNORE conflict strategy means a row already present is left untouched, so this is how a
 * future habit instance gets added too: one more insertIfAbsent call here, no UI.
 */
suspend fun ensureHabitsSeeded(dao: HabitInstanceDao) {
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
}
```

- [ ] **Step 2: Run the full suite and build**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt
git commit -m "Seed Tanakh Schedule-cursor habit instance"
```

---

### Task 7: On-device manual verification

Not a code task — no commit. Robolectric can't exercise real notification firing across the
9am-1pm reminder window, or confirm the bundled CSV asset actually loads from a real APK's
assets folder (vs. the test classpath). Install the debug APK on-device
(`./gradlew.bat :app:installDebug`) and confirm:

- [ ] Fresh install / update: dashboard shows Exercise, and on Sun-Thu, both Reading and Tanakh
  rows. Tanakh shows the very first schedule entry (`יהושע י״ט`), unmarked (no ✓).
- [ ] Tap the Tanakh row: cursor advances by one, row updates to the next entry
  (`יהושע כ׳`), shows ✓ for today, streak becomes 1d.
- [ ] Tap again (same day): cursor advances by one more entry; today still shows ✓ (already
  marked once today) — confirm via logcat/dumpsys that this doesn't double-count today's streak
  day (it's a boolean flag, not a running total).
- [ ] Force-stop the app after marking a chapter, relaunch: dashboard reflects the same
  cursor position (persisted immediately, no crash-recovery concept needed for this kind since
  there's no in-flight "session" to reconcile).
- [ ] Skip a day deliberately (or reason from logcat timestamps): confirm the next day's status
  is `Behind` with the correct due count, and the row still shows the *skipped* day's chapter,
  not today's — never skipping ahead.
- [ ] An hourly reminder fires only if today's Tanakh entry is not yet marked read.
- [ ] Reboot: `BootReceiver`'s self-heal doesn't crash on the new instance (already covered
  structurally by the fully generalized receivers — confirm no new exceptions in logcat
  mentioning `SCHEDULE_CURSOR` or `schedule_cursor`).
- [ ] Confirm via logcat that the bundled `tanakh_schedule.csv` asset loaded successfully (724
  entries) rather than silently falling back to `emptyList()` — e.g. temporarily log
  `tanakhSchedule.size` in `AppContainer` during this check, or verify indirectly by confirming
  the Tanakh row shows real chapter text instead of blank/"Finished" on first launch.

Once all boxes are checked, update `.superpowers/sdd/progress.md` to record this plan's
completion, matching Plans 1-2's precedent.
