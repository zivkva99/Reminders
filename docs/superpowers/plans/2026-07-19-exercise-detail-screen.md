# Exercise Detail Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port Shape's exercise-tracking UI (big counter button, 4 rep sub-counters, streak/heatmap stats) into Reminders as a new navigation flow reached by tapping the dashboard's Exercise row, reusing Reminders' existing Room-backed goal/streak engine, with real per-day persistence for the 4 rep sub-counters (a genuine improvement over Shape's own decorative version).

**Architecture:** Navigation-Compose (`NavHost`/`NavController`) with 3 destinations — `dashboard` (existing, modified), `exerciseCounter` and `exerciseStats` (new, mirroring Shape's own two-screen Counter+Stats structure). A new pure `HabitStats` object (ported from Shape's `TrainingStats.kt`) supplies month/best-month/record detection Reminders doesn't have today. A new Room table, `exercise_sub_counter_progress`, is keyed by **(exerciseKey, date)** — one independent row per sub-counter per day, NOT one row per day with 4 columns — so a single `@Upsert` can never clobber another sub-counter's value. `AppContainer` gains a second DI interface, `ExerciseDetailDataSource`, alongside the existing `DashboardDataSource`, rather than polluting it with Exercise-only members.

**Tech Stack:** Kotlin 2.3.0, Room 2.7.1 (KSP), Jetpack Compose (existing), Navigation-Compose (new dependency — this project's first use), JUnit4 + `kotlin-test`, Robolectric 4.16.1 for DB/migration tests.

## Global Constraints

(Inherited from the main Reminders design doc and this feature's own design doc — still binding.)

- Package `com.ziv.reminders`. `minSdk = 35`, `targetSdk = 36`.
- Every Room schema change ships with a real `Migration` object; never `fallbackToDestructiveMigration()`. This plan is the schema's v4→v5 migration.
- The new `exercise_sub_counter_progress` table is keyed by **(exerciseKey, date)** — never collapse this to one row per date with multiple columns; that shape allows a partial `@Upsert` to silently clobber other sub-counters (see this feature's design doc, "Review Findings").
- `AppContainer`'s existing `DashboardDataSource` interface is not extended with Exercise-only members — a new parallel `ExerciseDetailDataSource` interface is added instead, and `AppContainer` implements both.
- Interaction model is **full navigate** — tapping the Exercise dashboard row always opens the new screen; it does not increment inline. This was explicitly confirmed by the user after review (not a bug to route around).
- Navigation uses **Navigation-Compose**, not a hand-rolled `enum` + `BackHandler` — an explicit user override of the plan's initial engineering recommendation.
- TDD for all pure logic, repository, and DAO/migration code (Robolectric `@Config(sdk = [35])` for anything touching Room). Compose UI composables in this project have no existing test precedent (neither Shape nor Reminders has Compose UI tests) — this plan follows that same precedent: UI composables are implemented directly and verified on-device, while every ViewModel/repository/DAO/pure-function layer underneath them is fully unit tested.
- Every commit after a task leaves `./gradlew.bat :app:testDebugUnitTest` green.

---

## File Structure

```
Reminders/
  gradle/libs.versions.toml                                        (Modify — Task 4)
  app/build.gradle.kts                                              (Modify — Task 4)
  app/src/main/java/com/ziv/reminders/
    data/
      HabitStats.kt                                                  (Create — Task 1)
      ExerciseSubCounterProgress.kt                                  (Create — Task 2)
      ExerciseSubCounterProgressDao.kt                                (Create — Task 2)
      AppDatabase.kt                                                 (Modify — Task 2)
      AppContainer.kt                                                 (Modify — Tasks 2, 3)
      CounterHabitRepository.kt                                       (Modify — Task 3)
      SubCounterRepository.kt                                         (Create — Task 3)
    MainActivity.kt                                                   (Modify — Task 4)
    ui/dashboard/DashboardScreen.kt                                   (Modify — Task 4)
    ui/exercise/
      ExerciseColors.kt                                               (Create — Task 5)
      ExerciseViewModel.kt                                            (Create — Task 5)
      ExerciseCounterScreen.kt                                        (Create — Task 5)
      ExerciseStatsScreen.kt                                          (Create — Task 6)
    res/drawable/
      exercise_weight_side.png, exercise_weight_front.png,
      exercise_situp.png, exercise_pushup.png                         (Copy from Shape — Task 5)
  app/src/test/java/com/ziv/reminders/
    data/
      HabitStatsTest.kt                                               (Create — Task 1)
      ExerciseSubCounterProgressDaoTest.kt                            (Create — Task 2)
      AppDatabaseMigration4To5Test.kt                                 (Create — Task 2)
      SubCounterRepositoryTest.kt                                     (Create — Task 3)
    ui/dashboard/
      DashboardDispatchTest.kt                                        (Create — Task 4)
    ui/exercise/
      ExerciseViewModelTest.kt                                        (Create — Task 5)
```

---

### Task 1: `HabitStats` pure functions

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/data/HabitStats.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/HabitStatsTest.kt`

**Interfaces:**
- Produces: `object HabitStats { fun parseDates(raw: List<String>, onMalformedEntry: (String) -> Unit = {}): Set<LocalDate>; fun recordSuffix(isRecord: Boolean, label: String): String; fun monthCount(dates: Set<LocalDate>, today: LocalDate): Int; fun currentStreak(dates: Set<LocalDate>, today: LocalDate): Int; fun longestStreak(dates: Set<LocalDate>): Int; fun bestMonth(dates: Set<LocalDate>): Int; fun isNewStreakRecord(dates: Set<LocalDate>, today: LocalDate): Boolean; fun isNewMonthRecord(dates: Set<LocalDate>, today: LocalDate): Boolean }`. Consumed by `ExerciseViewModel` (Task 5).
- Note: adapted from Shape's `TrainingStats.kt` — `parseDates` takes a `List<String>` (one row per completed day, matching `CounterDailyProgressDao.getCompletedDates`'s return shape) rather than Shape's single comma-joined CSV string, since Reminders stores one Room row per completed day, not one CSV cell.

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/ziv/reminders/data/HabitStatsTest.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HabitStatsTest {

    @Test
    fun parseDates_emptyList_returnsEmptySet() {
        assertEquals(emptySet(), HabitStats.parseDates(emptyList()))
    }

    @Test
    fun parseDates_malformedEntry_isSkippedAndReported() {
        val malformed = mutableListOf<String>()
        val result = HabitStats.parseDates(listOf("2026-07-01", "not-a-date", "2026-07-02")) { malformed.add(it) }

        assertEquals(setOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2)), result)
        assertEquals(listOf("not-a-date"), malformed)
    }

    @Test
    fun monthCount_countsOnlyCurrentMonthAndYear() {
        val today = LocalDate.of(2026, 7, 19)
        val dates = setOf(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15),
            LocalDate.of(2026, 6, 30), LocalDate.of(2025, 7, 1),
        )
        assertEquals(2, HabitStats.monthCount(dates, today))
    }

    @Test
    fun currentStreak_todayIncluded_countsThroughToday() {
        val today = LocalDate.of(2026, 7, 19)
        val dates = setOf(today, today.minusDays(1), today.minusDays(2))
        assertEquals(3, HabitStats.currentStreak(dates, today))
    }

    @Test
    fun currentStreak_todayNotYetDone_countsThroughYesterday() {
        val today = LocalDate.of(2026, 7, 19)
        val dates = setOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, HabitStats.currentStreak(dates, today))
    }

    @Test
    fun currentStreak_gapBreaksStreak() {
        val today = LocalDate.of(2026, 7, 19)
        val dates = setOf(today, today.minusDays(2))
        assertEquals(1, HabitStats.currentStreak(dates, today))
    }

    @Test
    fun longestStreak_findsLongestConsecutiveRun() {
        val dates = setOf(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3),
            LocalDate.of(2026, 7, 10),
        )
        assertEquals(3, HabitStats.longestStreak(dates))
    }

    @Test
    fun bestMonth_findsMonthWithMostDates() {
        val dates = setOf(
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3),
        )
        assertEquals(3, HabitStats.bestMonth(dates))
    }

    @Test
    fun isNewStreakRecord_currentStreakExceedsPastBaseline_isTrue() {
        val today = LocalDate.of(2026, 7, 19)
        // past streak of 2 (days 10-11), current streak of 3 (days 17-19)
        val dates = setOf(
            LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 11),
            LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 18), today,
        )
        assertTrue(HabitStats.isNewStreakRecord(dates, today))
    }

    @Test
    fun isNewStreakRecord_currentStreakDoesNotExceedPastBaseline_isFalse() {
        val today = LocalDate.of(2026, 7, 19)
        // past streak of 5, current streak of 1
        val dates = setOf(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3),
            LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 5), today,
        )
        assertFalse(HabitStats.isNewStreakRecord(dates, today))
    }

    @Test
    fun isNewMonthRecord_currentMonthExceedsPastBestMonth_isTrue() {
        val today = LocalDate.of(2026, 7, 19)
        val dates = setOf(
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 3), today,
        )
        assertTrue(HabitStats.isNewMonthRecord(dates, today))
    }

    @Test
    fun recordSuffix_isRecordTrue_appendsSuffix() {
        assertEquals(" — new record!", HabitStats.recordSuffix(true, "record"))
    }

    @Test
    fun recordSuffix_isRecordFalse_returnsEmptyString() {
        assertEquals("", HabitStats.recordSuffix(false, "record"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.HabitStatsTest"`
Expected: FAIL — `HabitStats` doesn't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/data/HabitStats.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Pure, Android-free port of Shape's TrainingStats (Shape/app/src/main/java/com/shape/
 * data/TrainingStats.kt), providing the month/best-month/"new record" detection Reminders
 * has none of today (only currentStreak exists, in CounterHabitRepository).
 *
 * parseDates takes a List<String> — one row per completed day, matching
 * CounterDailyProgressDao.getCompletedDates's return shape — rather than Shape's single
 * comma-joined CSV string, since Reminders stores one Room row per completed day.
 */
object HabitStats {

    fun parseDates(raw: List<String>, onMalformedEntry: (String) -> Unit = {}): Set<LocalDate> =
        raw.mapNotNull { entry ->
            try {
                LocalDate.parse(entry.trim())
            } catch (e: DateTimeParseException) {
                onMalformedEntry(entry)
                null
            }
        }.toSet()

    fun recordSuffix(isRecord: Boolean, label: String): String =
        if (isRecord) " — new $label!" else ""

    fun monthCount(dates: Set<LocalDate>, today: LocalDate): Int =
        dates.count { it.year == today.year && it.month == today.month }

    // If today hasn't been hit yet, the day isn't over — the streak counts through
    // yesterday and isn't broken until midnight passes without today being hit.
    fun currentStreak(dates: Set<LocalDate>, today: LocalDate): Int =
        currentStreakDates(dates, today).size

    private fun currentStreakDates(dates: Set<LocalDate>, today: LocalDate): Set<LocalDate> {
        val anchor = if (today in dates) today else today.minusDays(1)
        val result = mutableSetOf<LocalDate>()
        var cursor = anchor
        while (cursor in dates) {
            result += cursor
            cursor = cursor.minusDays(1)
        }
        return result
    }

    fun longestStreak(dates: Set<LocalDate>): Int {
        var longest = 0
        var current = 0
        var previous: LocalDate? = null
        for (date in dates.sorted()) {
            current = if (previous != null && date == previous.plusDays(1)) current + 1 else 1
            longest = maxOf(longest, current)
            previous = date
        }
        return longest
    }

    fun bestMonth(dates: Set<LocalDate>): Int =
        dates.groupingBy { it.year to it.month }.eachCount().values.maxOrNull() ?: 0

    // "New record" (strictly greater) must be judged against a baseline that EXCLUDES the
    // currently in-progress streak/month — comparing against a baseline that includes the
    // in-progress run is self-referential and can never be true on the exact day the
    // record is actually set.
    fun isNewStreakRecord(dates: Set<LocalDate>, today: LocalDate): Boolean {
        val inProgress = currentStreakDates(dates, today)
        val baseline = longestStreak(dates - inProgress)
        return currentStreak(dates, today) > baseline
    }

    fun isNewMonthRecord(dates: Set<LocalDate>, today: LocalDate): Boolean {
        val currentMonthDates = dates.filter { it.year == today.year && it.month == today.month }.toSet()
        val baseline = bestMonth(dates - currentMonthDates)
        return monthCount(dates, today) > baseline
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.HabitStatsTest"`
Expected: PASS (14 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitStats.kt app/src/test/java/com/ziv/reminders/data/HabitStatsTest.kt
git commit -m "Add HabitStats pure functions, ported from Shape's TrainingStats"
```

---

### Task 2: Room schema v4→v5 — `ExerciseSubCounterProgress`

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/data/ExerciseSubCounterProgress.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/ExerciseSubCounterProgressDao.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/ExerciseSubCounterProgressDaoTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration4To5Test.kt`

**Interfaces:**
- Produces: `data class ExerciseSubCounterProgress(exerciseKey: String, date: String, count: Int)`; `interface ExerciseSubCounterProgressDao { suspend fun getByDate(exerciseKey: String, date: String): ExerciseSubCounterProgress?; suspend fun upsert(progress: ExerciseSubCounterProgress); suspend fun getAllForDate(date: String): List<ExerciseSubCounterProgress> }`; `EXERCISE_KEY_LATERAL_RAISE`, `EXERCISE_KEY_ARM_ROTATION`, `EXERCISE_KEY_SITUP`, `EXERCISE_KEY_PUSHUP` (String constants); `ALL_EXERCISE_KEYS: List<String>`; `EXERCISE_SUB_COUNTER_DEFAULT = 5`; `AppDatabase.MIGRATION_4_5`. Consumed by `SubCounterRepository` (Task 3).

- [ ] **Step 1: Write the failing DAO test**

`app/src/test/java/com/ziv/reminders/data/ExerciseSubCounterProgressDaoTest.kt`:
```kotlin
package com.ziv.reminders.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExerciseSubCounterProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByDate_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.exerciseSubCounterProgressDao().getByDate(EXERCISE_KEY_PUSHUP, "2026-07-19"))
        db.close()
    }

    @Test
    fun upsert_thenGetByDate_returnsTheRow() = runTest {
        val db = newDb()
        val row = ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, "2026-07-19", 8)
        db.exerciseSubCounterProgressDao().upsert(row)

        assertEquals(row, db.exerciseSubCounterProgressDao().getByDate(EXERCISE_KEY_PUSHUP, "2026-07-19"))
        db.close()
    }

    @Test
    fun upsert_sameKey_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, "2026-07-19", 5))
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, "2026-07-19", 9))

        assertEquals(9, db.exerciseSubCounterProgressDao().getByDate(EXERCISE_KEY_PUSHUP, "2026-07-19")?.count)
        db.close()
    }

    @Test
    fun upsert_differentExerciseKey_doesNotAffectAnotherKeysRowForTheSameDate() = runTest {
        val db = newDb()
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, "2026-07-19", 5))
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_SITUP, "2026-07-19", 12))

        // This is the regression test for the clobber bug the design doc identifies:
        // upserting one exerciseKey's row must never touch another exerciseKey's row for
        // the same date. One row per (exerciseKey, date) — not one row per date with
        // multiple columns — makes this true by construction.
        assertEquals(5, db.exerciseSubCounterProgressDao().getByDate(EXERCISE_KEY_PUSHUP, "2026-07-19")?.count)
        assertEquals(12, db.exerciseSubCounterProgressDao().getByDate(EXERCISE_KEY_SITUP, "2026-07-19")?.count)
        db.close()
    }

    @Test
    fun getAllForDate_returnsOnlyRowsForThatDate() = runTest {
        val db = newDb()
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, "2026-07-19", 5))
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_SITUP, "2026-07-19", 12))
        db.exerciseSubCounterProgressDao().upsert(ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, "2026-07-18", 3))

        val rows = db.exerciseSubCounterProgressDao().getAllForDate("2026-07-19")
        assertEquals(2, rows.size)
        db.close()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ExerciseSubCounterProgressDaoTest"`
Expected: FAIL — `ExerciseSubCounterProgress`, `EXERCISE_KEY_*`, and `AppDatabase.exerciseSubCounterProgressDao()` don't exist yet (compile error).

- [ ] **Step 3: Write the entity, keys, and DAO**

`app/src/main/java/com/ziv/reminders/data/ExerciseSubCounterProgress.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity

/**
 * One row per (exerciseKey, date) — NOT one row per date with 4 columns. A single row
 * covering all 4 sub-counters would let a partial @Upsert on one counter silently clobber
 * the other 3 (Room replaces the whole row on a primary-key conflict); keying by
 * exerciseKey instead mirrors Shape's own independent-key model (KEY_EX1..KEY_EX4 in
 * Shape's CounterDataStore.kt) so each +/- tap upserts exactly one independent row.
 */
@Entity(tableName = "exercise_sub_counter_progress", primaryKeys = ["exerciseKey", "date"])
data class ExerciseSubCounterProgress(
    val exerciseKey: String,
    val date: String,
    val count: Int,
)

const val EXERCISE_KEY_LATERAL_RAISE = "lateral_raise"
const val EXERCISE_KEY_ARM_ROTATION = "arm_rotation"
const val EXERCISE_KEY_SITUP = "situp"
const val EXERCISE_KEY_PUSHUP = "pushup"

val ALL_EXERCISE_KEYS = listOf(
    EXERCISE_KEY_LATERAL_RAISE,
    EXERCISE_KEY_ARM_ROTATION,
    EXERCISE_KEY_SITUP,
    EXERCISE_KEY_PUSHUP,
)

// Matches Shape's own sub-counter default (CounterViewModel.kt: `prefs[it] ?: 5`).
const val EXERCISE_SUB_COUNTER_DEFAULT = 5
```

`app/src/main/java/com/ziv/reminders/data/ExerciseSubCounterProgressDao.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ExerciseSubCounterProgressDao {
    @Query("SELECT * FROM exercise_sub_counter_progress WHERE exerciseKey = :exerciseKey AND date = :date")
    suspend fun getByDate(exerciseKey: String, date: String): ExerciseSubCounterProgress?

    @Upsert
    suspend fun upsert(progress: ExerciseSubCounterProgress)

    @Query("SELECT * FROM exercise_sub_counter_progress WHERE date = :date")
    suspend fun getAllForDate(date: String): List<ExerciseSubCounterProgress>
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
        EvaluatorEscalation::class, ExerciseSubCounterProgress::class,
    ],
    version = 5,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitInstanceDao(): HabitInstanceDao
    abstract fun counterDailyProgressDao(): CounterDailyProgressDao
    abstract fun timerDailyProgressDao(): TimerDailyProgressDao
    abstract fun scheduleCursorProgressDao(): ScheduleCursorProgressDao
    abstract fun scheduleCursorDailyProgressDao(): ScheduleCursorDailyProgressDao
    abstract fun evaluatorEscalationDao(): EvaluatorEscalationDao
    abstract fun exerciseSubCounterProgressDao(): ExerciseSubCounterProgressDao

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

        /** Adds Schedule-cursor kind support — see Plan 3. */
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

        /** Adds the cross-habit evaluator's escalation-tracking table — a per-day flag,
         * no new habit_instance column. Never fallbackToDestructiveMigration() — see
         * Global Constraints. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `evaluator_escalation` (" +
                        "`habitInstanceId` INTEGER NOT NULL, `date` TEXT NOT NULL, " +
                        "`escalated` INTEGER NOT NULL, PRIMARY KEY(`habitInstanceId`, `date`))"
                )
            }
        }

        /** Adds the Exercise sub-counter tracking table — one row per (exerciseKey,
         * date), never one row per date with multiple columns (see this file's
         * ExerciseSubCounterProgress doc comment for why). Never
         * fallbackToDestructiveMigration() — see Global Constraints. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_sub_counter_progress` (" +
                        "`exerciseKey` TEXT NOT NULL, `date` TEXT NOT NULL, " +
                        "`count` INTEGER NOT NULL, PRIMARY KEY(`exerciseKey`, `date`))"
                )
            }
        }
    }
}
```

`app/src/main/java/com/ziv/reminders/data/AppContainer.kt` (only the migration wiring and new DAO getter change here — `ExerciseDetailDataSource`/`subCounterRepository` wiring comes in Task 3):
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
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5,
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

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ExerciseSubCounterProgressDaoTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Write the failing migration test**

`app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration4To5Test.kt`:
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
class AppDatabaseMigration4To5Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate4To5_preservesExistingRows_andAddsExerciseSubCounterProgressTable() {
        helper.createDatabase(TEST_DB_NAME, 4).apply {
            execSQL(
                "INSERT INTO habit_instance (id, kind, name, enabledDaysMask, notificationTitle, notificationBody, counterGoal, timerTargetSeconds) " +
                    "VALUES (1, 'COUNTER', 'Exercise', 127, 't', 'b', 5, NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 5, true, AppDatabase.MIGRATION_4_5)

        migrated.query("SELECT name FROM habit_instance WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Exercise", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM exercise_sub_counter_progress").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test-4-5"
    }
}
```

- [ ] **Step 6: Run the migration test, then the full suite**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.AppDatabaseMigration4To5Test"`
Expected: PASS (1 test)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests still green)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/ExerciseSubCounterProgress.kt app/src/main/java/com/ziv/reminders/data/ExerciseSubCounterProgressDao.kt app/src/main/java/com/ziv/reminders/data/AppDatabase.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/test/java/com/ziv/reminders/data/ExerciseSubCounterProgressDaoTest.kt app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration4To5Test.kt
git commit -m "Add Room schema v5 for per-day exercise sub-counter tracking"
```

---

### Task 3: `SubCounterRepository` + `ExerciseDetailDataSource`

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/data/SubCounterRepository.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/CounterHabitRepository.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/SubCounterRepositoryTest.kt`

**Interfaces:**
- Produces: `class SubCounterRepository(dao: ExerciseSubCounterProgressDao) { suspend fun todayValue(exerciseKey: String, today: LocalDate): Int; suspend fun valueForDate(exerciseKey: String, date: LocalDate): Int?; suspend fun adjust(exerciseKey: String, today: LocalDate, delta: Int); suspend fun valuesForDate(date: LocalDate): Map<String, Int> }`; `interface ExerciseDetailDataSource { val habitInstanceDao: HabitInstanceDao; val counterHabitRepository: CounterHabitRepository; val habitEngine: HabitEngine; val subCounterRepository: SubCounterRepository }`; `CounterHabitRepository.completedDates(instance: HabitInstance): List<String>`. Consumed by `ExerciseViewModel` (Task 5).

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ziv/reminders/data/SubCounterRepositoryTest.kt`:
```kotlin
package com.ziv.reminders.data

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeExerciseSubCounterProgressDao : ExerciseSubCounterProgressDao {
    val rows = mutableMapOf<Pair<String, String>, ExerciseSubCounterProgress>()
    override suspend fun getByDate(exerciseKey: String, date: String) = rows[exerciseKey to date]
    override suspend fun upsert(progress: ExerciseSubCounterProgress) { rows[progress.exerciseKey to progress.date] = progress }
    override suspend fun getAllForDate(date: String) = rows.values.filter { it.date == date }
}

class SubCounterRepositoryTest {

    private val today = LocalDate.of(2026, 7, 19)

    @Test
    fun todayValue_noRow_defaultsToFive() = runTest {
        val repo = SubCounterRepository(FakeExerciseSubCounterProgressDao())
        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT, repo.todayValue(EXERCISE_KEY_PUSHUP, today))
    }

    @Test
    fun todayValue_existingRow_returnsStoredValue() = runTest {
        val dao = FakeExerciseSubCounterProgressDao()
        dao.rows[EXERCISE_KEY_PUSHUP to today.toString()] = ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, today.toString(), 12)
        val repo = SubCounterRepository(dao)

        assertEquals(12, repo.todayValue(EXERCISE_KEY_PUSHUP, today))
    }

    @Test
    fun valueForDate_pastDateNoRow_returnsNull_neverDefaultsToFive() = runTest {
        val repo = SubCounterRepository(FakeExerciseSubCounterProgressDao())
        assertNull(repo.valueForDate(EXERCISE_KEY_PUSHUP, today.minusDays(30)))
    }

    @Test
    fun valueForDate_pastDateWithRow_returnsStoredValue() = runTest {
        val dao = FakeExerciseSubCounterProgressDao()
        val pastDate = today.minusDays(5)
        dao.rows[EXERCISE_KEY_SITUP to pastDate.toString()] = ExerciseSubCounterProgress(EXERCISE_KEY_SITUP, pastDate.toString(), 20)
        val repo = SubCounterRepository(dao)

        assertEquals(20, repo.valueForDate(EXERCISE_KEY_SITUP, pastDate))
    }

    @Test
    fun adjust_incrementsFromDefault_andClampsAtNinetyNine() = runTest {
        val repo = SubCounterRepository(FakeExerciseSubCounterProgressDao())
        repo.adjust(EXERCISE_KEY_PUSHUP, today, +1)
        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT + 1, repo.todayValue(EXERCISE_KEY_PUSHUP, today))
    }

    @Test
    fun adjust_clampsAtZero_neverGoesNegative() = runTest {
        val dao = FakeExerciseSubCounterProgressDao()
        dao.rows[EXERCISE_KEY_PUSHUP to today.toString()] = ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, today.toString(), 0)
        val repo = SubCounterRepository(dao)

        repo.adjust(EXERCISE_KEY_PUSHUP, today, -1)
        assertEquals(0, repo.todayValue(EXERCISE_KEY_PUSHUP, today))
    }

    @Test
    fun adjust_oneExerciseKey_doesNotAffectAnother() = runTest {
        val repo = SubCounterRepository(FakeExerciseSubCounterProgressDao())
        repo.adjust(EXERCISE_KEY_PUSHUP, today, +3)

        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT + 3, repo.todayValue(EXERCISE_KEY_PUSHUP, today))
        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT, repo.todayValue(EXERCISE_KEY_SITUP, today))
    }

    @Test
    fun valuesForDate_returnsMapKeyedByExerciseKey() = runTest {
        val dao = FakeExerciseSubCounterProgressDao()
        dao.rows[EXERCISE_KEY_PUSHUP to today.toString()] = ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, today.toString(), 8)
        dao.rows[EXERCISE_KEY_SITUP to today.toString()] = ExerciseSubCounterProgress(EXERCISE_KEY_SITUP, today.toString(), 15)
        val repo = SubCounterRepository(dao)

        val values = repo.valuesForDate(today)
        assertEquals(mapOf(EXERCISE_KEY_PUSHUP to 8, EXERCISE_KEY_SITUP to 15), values)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.SubCounterRepositoryTest"`
Expected: FAIL — `SubCounterRepository` doesn't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/data/SubCounterRepository.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate

/**
 * todayValue defaults a missing row to EXERCISE_SUB_COUNTER_DEFAULT (matches Shape's
 * live-session default of 5). valueForDate (used for past dates — e.g. the
 * heatmap-day-tap detail view) returns null for a missing row instead: a past day with no
 * logged value means "no data," never a fabricated default. Two distinct methods make
 * this split explicit in the code rather than one overloaded default.
 */
class SubCounterRepository(private val dao: ExerciseSubCounterProgressDao) {

    suspend fun todayValue(exerciseKey: String, today: LocalDate): Int =
        dao.getByDate(exerciseKey, today.toString())?.count ?: EXERCISE_SUB_COUNTER_DEFAULT

    suspend fun valueForDate(exerciseKey: String, date: LocalDate): Int? =
        dao.getByDate(exerciseKey, date.toString())?.count

    suspend fun adjust(exerciseKey: String, today: LocalDate, delta: Int) {
        val current = todayValue(exerciseKey, today)
        val newValue = (current + delta).coerceIn(0, 99)
        dao.upsert(ExerciseSubCounterProgress(exerciseKey, today.toString(), newValue))
    }

    suspend fun valuesForDate(date: LocalDate): Map<String, Int> =
        dao.getAllForDate(date.toString()).associate { it.exerciseKey to it.count }
}
```

Modify `app/src/main/java/com/ziv/reminders/data/CounterHabitRepository.kt` — add one method (rest of the file unchanged):
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate

class CounterHabitRepository(private val dao: CounterDailyProgressDao) {

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus.CounterStatus {
        val goal = requireNotNull(instance.counterGoal) { "Counter habit ${instance.id} has no counterGoal" }
        val current = dao.getByDate(instance.id, today.toString())?.count ?: 0
        return HabitStatus.CounterStatus(current = current, goal = goal, completed = current >= goal)
    }

    suspend fun increment(instance: HabitInstance, today: LocalDate) {
        val goal = requireNotNull(instance.counterGoal) { "Counter habit ${instance.id} has no counterGoal" }
        val current = dao.getByDate(instance.id, today.toString())?.count ?: 0
        val newCount = current + 1
        dao.upsert(
            CounterDailyProgress(
                habitInstanceId = instance.id,
                date = today.toString(),
                count = newCount,
                completed = newCount >= goal,
            )
        )
    }

    // Mirrors Shape's TrainingStats.currentStreak anchor logic: if today isn't done yet, the
    // day isn't over — the streak counts through yesterday and isn't broken until midnight
    // passes without today being hit.
    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int {
        val completedDates = dao.getCompletedDates(instance.id)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
        val anchor = if (today in completedDates) today else today.minusDays(1)
        var streak = 0
        var cursor = anchor
        while (cursor in completedDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    // Feeds HabitStats' month/best-month/record functions (ExerciseViewModel, Task 5),
    // which need the raw completed-date rows, not just the derived streak count.
    suspend fun completedDates(instance: HabitInstance): List<String> = dao.getCompletedDates(instance.id)
}
```

Modify `app/src/main/java/com/ziv/reminders/data/AppContainer.kt` — add the new interface, implement it, and wire the repository (full file):
```kotlin
package com.ziv.reminders.data

import android.content.Context
import androidx.room.Room
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.scheduling.HabitScheduler

/** Manual DI — no framework needed at this app's size. One instance, owned by RemindersApp. */
class AppContainer(context: Context) : DashboardDataSource, ExerciseDetailDataSource {
    private val appContext = context.applicationContext

    private val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "reminders.db")
            // Never fallbackToDestructiveMigration() — see Global Constraints.
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5,
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
    override val counterHabitRepository: CounterHabitRepository by lazy { CounterHabitRepository(counterDailyProgressDao) }
    val timerHabitRepository: TimerHabitRepository by lazy { TimerHabitRepository(timerDailyProgressDao, SystemClock) }
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
    val habitScheduler: HabitScheduler by lazy { HabitScheduler(appContext) }
}

interface DashboardDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.SubCounterRepositoryTest"`
Expected: PASS (8 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/SubCounterRepository.kt app/src/main/java/com/ziv/reminders/data/CounterHabitRepository.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/test/java/com/ziv/reminders/data/SubCounterRepositoryTest.kt
git commit -m "Add SubCounterRepository and ExerciseDetailDataSource"
```

---

### Task 4: Navigation-Compose setup + dashboard dispatch

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`
- Test: `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardDispatchTest.kt`

**Interfaces:**
- Produces: `fun shouldNavigateToExerciseDetail(instanceId: Long): Boolean` in `DashboardScreen.kt`; `DashboardScreen(viewModel: DashboardViewModel, onOpenExercise: () -> Unit = {})`. Consumed by `MainActivity.kt`'s `NavHost` (Task 5, since `MainActivity.kt` also needs `ExerciseViewModel` which doesn't exist until Task 5 — the `NavHost` wiring is deferred to Task 5's Step 5 below so `MainActivity.kt` is only edited once).

- [ ] **Step 1: Add the Navigation-Compose dependency**

`gradle/libs.versions.toml` — add to `[versions]`:
```toml
navigationCompose = "2.9.6"
```

Add to `[libraries]`:
```toml
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
```

`app/build.gradle.kts` — add to `dependencies`:
```kotlin
    implementation(libs.navigation.compose)
```

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If `2.9.6` doesn't resolve (dependency not found), check the current stable `androidx.navigation:navigation-compose` release compatible with this project's Compose BOM (`2026.06.01`) and use that instead — update both the version string and this note is satisfied either way.

- [ ] **Step 2: Write the failing dispatch test**

`app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardDispatchTest.kt`:
```kotlin
package com.ziv.reminders.ui.dashboard

import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.READING_HABIT_INSTANCE_ID
import com.ziv.reminders.data.TANAKH_HABIT_INSTANCE_ID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardDispatchTest {

    @Test
    fun shouldNavigateToExerciseDetail_exerciseInstanceId_isTrue() {
        assertTrue(shouldNavigateToExerciseDetail(EXERCISE_HABIT_INSTANCE_ID))
    }

    @Test
    fun shouldNavigateToExerciseDetail_otherInstanceIds_isFalse() {
        // Regression guard: a hypothetical future second COUNTER-kind habit must not be
        // silently redirected into the Exercise navigation flow just because it shares
        // HabitKind.COUNTER — dispatch is by instance ID, not by kind.
        assertFalse(shouldNavigateToExerciseDetail(READING_HABIT_INSTANCE_ID))
        assertFalse(shouldNavigateToExerciseDetail(TANAKH_HABIT_INSTANCE_ID))
        assertFalse(shouldNavigateToExerciseDetail(999L))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardDispatchTest"`
Expected: FAIL — `shouldNavigateToExerciseDetail` doesn't exist yet (compile error).

- [ ] **Step 4: Modify `DashboardScreen.kt`**

Full `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`:
```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onOpenExercise: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()

    // Re-reads current state on every resume (first composition, backgrounding, notification
    // tap) so the dashboard never shows stale data — see Plan 1's final-review Issue 2/4.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Today", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        if (!uiState.isLoaded) return@Column
        uiState.habits.forEach { habit ->
            val context = LocalContext.current
            HabitRow(
                habit = habit,
                onIncrement = { viewModel.onIncrement(habit.instanceId) },
                onToggleTimer = { displayedRemainingSeconds ->
                    viewModel.onToggleTimer(habit.instanceId, context, displayedRemainingSeconds)
                },
                onMarkRead = { viewModel.onMarkRead(habit.instanceId) },
                onOpenExercise = onOpenExercise,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HabitRow(
    habit: HabitRowUiState,
    onIncrement: () -> Unit,
    onToggleTimer: (Int) -> Unit,
    onMarkRead: () -> Unit,
    onOpenExercise: () -> Unit,
) {
    when (habit.status) {
        is HabitStatus.CounterStatus -> CounterHabitRow(habit, habit.status, onIncrement, onOpenExercise)
        is HabitStatus.TimerStatus -> TimerHabitRow(habit, habit.status, onToggleTimer)
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

@Composable
private fun TimerHabitRow(habit: HabitRowUiState, status: HabitStatus.TimerStatus, onToggleTimer: (Int) -> Unit) {
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

    Row(
        // Pass the currently-displayed (ticked-down) value, not status.remainingSeconds — the
        // ViewModel's optimistic flip uses this to avoid visually resetting to the stale
        // pre-session baseline the instant Stop is tapped.
        modifier = Modifier.fillMaxWidth().clickable(onClick = { onToggleTimer(displaySeconds) }),
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

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardDispatchTest"`
Expected: PASS (2 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green — `DashboardScreen`'s existing behavior for Reading/Tanakh rows is unchanged; only `CounterHabitRow`'s dispatch gained a branch)

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardDispatchTest.kt
git commit -m "Add Navigation-Compose dependency and Exercise-row dispatch"
```

---

### Task 5: `ExerciseViewModel` + `ExerciseCounterScreen`

**Files:**
- Copy: 4 drawables from `Shape/app/src/main/res/drawable/` (`exercise_weight_side.png`, `exercise_weight_front.png`, `exercise_situp.png`, `exercise_pushup.png`) into `app/src/main/res/drawable/`
- Create: `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseColors.kt`
- Create: `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseViewModel.kt`
- Create: `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseCounterScreen.kt`
- Modify: `app/src/main/java/com/ziv/reminders/MainActivity.kt`
- Test: `app/src/test/java/com/ziv/reminders/ui/exercise/ExerciseViewModelTest.kt`

**Interfaces:**
- Produces: `data class ExerciseUiState(current, goal, completed, streak, isNewStreakRecord, monthCount, isNewMonthRecord, subCounters: Map<String, Int>, completedDates: Set<LocalDate>, isLoaded: Boolean)`; `class ExerciseViewModel(dataSource: ExerciseDetailDataSource) { val uiState: StateFlow<ExerciseUiState>; fun refresh(); fun increment(); fun adjustSubCounter(exerciseKey: String, delta: Int); suspend fun subCounterValuesForDate(date: LocalDate): Map<String, Int?> }`. Consumed by `ExerciseCounterScreen`, `ExerciseStatsScreen` (Task 6), `MainActivity.kt`.

- [ ] **Step 1: Copy the exercise icon drawables**

```bash
cp "../Shape/app/src/main/res/drawable/exercise_weight_side.png" app/src/main/res/drawable/
cp "../Shape/app/src/main/res/drawable/exercise_weight_front.png" app/src/main/res/drawable/
cp "../Shape/app/src/main/res/drawable/exercise_situp.png" app/src/main/res/drawable/
cp "../Shape/app/src/main/res/drawable/exercise_pushup.png" app/src/main/res/drawable/
```
(Adjust the relative path to wherever the Shape repo is checked out locally if not a sibling directory.)

- [ ] **Step 2: Write the failing ViewModel test**

`app/src/test/java/com/ziv/reminders/ui/exercise/ExerciseViewModelTest.kt`:
```kotlin
package com.ziv.reminders.ui.exercise

import com.ziv.reminders.data.CounterDailyProgress
import com.ziv.reminders.data.CounterDailyProgressDao
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.EXERCISE_KEY_PUSHUP
import com.ziv.reminders.data.EXERCISE_KEY_SITUP
import com.ziv.reminders.data.EXERCISE_SUB_COUNTER_DEFAULT
import com.ziv.reminders.data.ExerciseDetailDataSource
import com.ziv.reminders.data.ExerciseSubCounterProgress
import com.ziv.reminders.data.ExerciseSubCounterProgressDao
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitInstanceDao
import com.ziv.reminders.data.ScheduleCursorDailyProgress
import com.ziv.reminders.data.ScheduleCursorDailyProgressDao
import com.ziv.reminders.data.ScheduleCursorProgress
import com.ziv.reminders.data.ScheduleCursorProgressDao
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.data.SubCounterRepository
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TimerDailyProgress
import com.ziv.reminders.data.TimerDailyProgressDao
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.engine.HabitEngine
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeHabitInstanceDao : HabitInstanceDao {
    val rows = mutableMapOf<Long, HabitInstance>()
    override suspend fun getAll() = rows.values.toList()
    override suspend fun getById(id: Long) = rows[id]
    override suspend fun insertIfAbsent(instance: HabitInstance) { rows.putIfAbsent(instance.id, instance) }
}

private class FakeCounterDailyProgressDao : CounterDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, CounterDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: CounterDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
}

private class FakeExerciseSubCounterProgressDao : ExerciseSubCounterProgressDao {
    val rows = mutableMapOf<Pair<String, String>, ExerciseSubCounterProgress>()
    override suspend fun getByDate(exerciseKey: String, date: String) = rows[exerciseKey to date]
    override suspend fun upsert(progress: ExerciseSubCounterProgress) { rows[progress.exerciseKey to progress.date] = progress }
    override suspend fun getAllForDate(date: String) = rows.values.filter { it.date == date }
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
    override suspend fun getByInstance(habitInstanceId: Long): ScheduleCursorProgress? = null
    override suspend fun upsert(progress: ScheduleCursorProgress) {}
}

private class FakeScheduleCursorDailyProgressDao : ScheduleCursorDailyProgressDao {
    override suspend fun getByDate(habitInstanceId: Long, date: String): ScheduleCursorDailyProgress? = null
    override suspend fun upsert(progress: ScheduleCursorDailyProgress) {}
    override suspend fun getCompletedDates(habitInstanceId: Long) = emptyList<String>()
}

private class FakeExerciseDetailDataSource(
    private val instanceDao: FakeHabitInstanceDao,
    counterDao: FakeCounterDailyProgressDao,
    private val subCounterDao: FakeExerciseSubCounterProgressDao,
) : ExerciseDetailDataSource {
    override val habitInstanceDao = instanceDao
    override val counterHabitRepository = CounterHabitRepository(counterDao)
    override val habitEngine = HabitEngine(
        counterHabitRepository,
        TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
        ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), emptyList()),
    )
    override val subCounterRepository = SubCounterRepository(subCounterDao)
}

class ExerciseViewModelTest {

    private val exercise = HabitInstance(
        id = EXERCISE_HABIT_INSTANCE_ID, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )
    private val today = LocalDate.of(2026, 7, 19)

    private fun newViewModel(
        instanceDao: FakeHabitInstanceDao = FakeHabitInstanceDao().apply { rows[exercise.id] = exercise },
        counterDao: FakeCounterDailyProgressDao = FakeCounterDailyProgressDao(),
        subCounterDao: FakeExerciseSubCounterProgressDao = FakeExerciseSubCounterProgressDao(),
    ): ExerciseViewModel = ExerciseViewModel(FakeExerciseDetailDataSource(instanceDao, counterDao, subCounterDao))

    @Test
    fun refresh_populatesStateFromCurrentData() = runTest {
        val counterDao = FakeCounterDailyProgressDao()
        counterDao.rows[exercise.id to today.toString()] = CounterDailyProgress(exercise.id, today.toString(), 3, false)
        val viewModel = newViewModel(counterDao = counterDao)

        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(3, state.current)
        assertEquals(5, state.goal)
        assertTrue(!state.completed)
        assertTrue(state.isLoaded)
    }

    @Test
    fun increment_incrementsCountAndReloads() = runTest {
        val viewModel = newViewModel()
        viewModel.refresh()

        viewModel.increment()

        assertEquals(1, viewModel.uiState.value.current)
    }

    @Test
    fun increment_reachingGoal_setsCompletedTrue() = runTest {
        val counterDao = FakeCounterDailyProgressDao()
        counterDao.rows[exercise.id to today.toString()] = CounterDailyProgress(exercise.id, today.toString(), 4, false)
        val viewModel = newViewModel(counterDao = counterDao)
        viewModel.refresh()

        viewModel.increment()

        assertTrue(viewModel.uiState.value.completed)
    }

    @Test
    fun adjustSubCounter_oneKey_doesNotAffectAnother() = runTest {
        val viewModel = newViewModel()
        viewModel.refresh()

        viewModel.adjustSubCounter(EXERCISE_KEY_PUSHUP, +2)

        val state = viewModel.uiState.value
        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT + 2, state.subCounters[EXERCISE_KEY_PUSHUP])
        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT, state.subCounters[EXERCISE_KEY_SITUP])
    }

    @Test
    fun subCounterValuesForDate_pastDateNoData_returnsAllNull() = runTest {
        val viewModel = newViewModel()
        viewModel.refresh()

        val values = viewModel.subCounterValuesForDate(today.minusDays(30))

        assertTrue(values.values.all { it == null })
    }

    @Test
    fun subCounterValuesForDate_pastDateWithData_returnsStoredValues() = runTest {
        val subCounterDao = FakeExerciseSubCounterProgressDao()
        val pastDate = today.minusDays(3)
        subCounterDao.rows[EXERCISE_KEY_PUSHUP to pastDate.toString()] = ExerciseSubCounterProgress(EXERCISE_KEY_PUSHUP, pastDate.toString(), 10)
        val viewModel = newViewModel(subCounterDao = subCounterDao)
        viewModel.refresh()

        val values = viewModel.subCounterValuesForDate(pastDate)

        assertEquals(10, values[EXERCISE_KEY_PUSHUP])
        assertNull(values[EXERCISE_KEY_SITUP])
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.exercise.ExerciseViewModelTest"`
Expected: FAIL — `ExerciseViewModel` doesn't exist yet (compile error).

- [ ] **Step 4: Write `ExerciseColors.kt` and `ExerciseViewModel.kt`**

`app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseColors.kt`:
```kotlin
package com.ziv.reminders.ui.exercise

import androidx.compose.ui.graphics.Color

/**
 * Small, targeted semantic colors only — NOT a full theme override. Reminders uses
 * Material You dynamic color everywhere else (RemindersTheme.kt); these 4 values carry
 * real status meaning (goal reached, heatmap hit/miss/pending) that a per-device dynamic
 * palette can't express, so they're layered on top of the dynamic MaterialTheme only
 * within the Exercise screens, mirroring Shape's own GoalGreen/Heatmap* constants without
 * importing Shape's full lightColorScheme()/darkColorScheme() override.
 */
val GoalGreen = Color(0xFF2E7D32)
val HeatmapHit = GoalGreen
val HeatmapMiss = Color(0xFFE0E0E0)
val HeatmapPending = Color(0xFFFFD54F)
```

`app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseViewModel.kt`:
```kotlin
package com.ziv.reminders.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.ALL_EXERCISE_KEYS
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.EXERCISE_SUB_COUNTER_DEFAULT
import com.ziv.reminders.data.ExerciseDetailDataSource
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.data.HabitStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExerciseUiState(
    val current: Int = 0,
    val goal: Int = 5,
    val completed: Boolean = false,
    val streak: Int = 0,
    val isNewStreakRecord: Boolean = false,
    val monthCount: Int = 0,
    val isNewMonthRecord: Boolean = false,
    val subCounters: Map<String, Int> = ALL_EXERCISE_KEYS.associateWith { EXERCISE_SUB_COUNTER_DEFAULT },
    val completedDates: Set<LocalDate> = emptySet(),
    val isLoaded: Boolean = false,
)

class ExerciseViewModel(private val dataSource: ExerciseDetailDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(ExerciseUiState())
    val uiState: StateFlow<ExerciseUiState> = _uiState.asStateFlow()

    private var instance: HabitInstance? = null

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val exerciseInstance = dataSource.habitInstanceDao.getById(EXERCISE_HABIT_INSTANCE_ID) ?: return@launch
            instance = exerciseInstance

            val status = dataSource.habitEngine.todayStatus(exerciseInstance, today) as HabitStatus.CounterStatus
            val rawDates = dataSource.counterHabitRepository.completedDates(exerciseInstance)
            val dates = HabitStats.parseDates(rawDates)
            val streak = dataSource.habitEngine.currentStreak(exerciseInstance, today)
            val subCounters = ALL_EXERCISE_KEYS.associateWith { key -> dataSource.subCounterRepository.todayValue(key, today) }

            _uiState.value = ExerciseUiState(
                current = status.current,
                goal = status.goal,
                completed = status.completed,
                streak = streak,
                isNewStreakRecord = HabitStats.isNewStreakRecord(dates, today),
                monthCount = HabitStats.monthCount(dates, today),
                isNewMonthRecord = HabitStats.isNewMonthRecord(dates, today),
                subCounters = subCounters,
                completedDates = dates,
                isLoaded = true,
            )
        }
    }

    fun increment() {
        viewModelScope.launch {
            val exerciseInstance = instance ?: dataSource.habitInstanceDao.getById(EXERCISE_HABIT_INSTANCE_ID) ?: return@launch
            dataSource.counterHabitRepository.increment(exerciseInstance, LocalDate.now())
            refresh()
        }
    }

    fun adjustSubCounter(exerciseKey: String, delta: Int) {
        viewModelScope.launch {
            dataSource.subCounterRepository.adjust(exerciseKey, LocalDate.now(), delta)
            refresh()
        }
    }

    suspend fun subCounterValuesForDate(date: LocalDate): Map<String, Int?> {
        val values = dataSource.subCounterRepository.valuesForDate(date)
        return ALL_EXERCISE_KEYS.associateWith { key -> values[key] }
    }

    companion object {
        fun factory(dataSource: ExerciseDetailDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = ExerciseViewModel(dataSource) as T
            }
    }
}
```

Note: `refresh()` is called from the composable side (Step 6 below and Task 6), not from an `init {}` block — this mirrors `MainActivity.kt`'s existing pattern of triggering the first load from a `LaunchedEffect(Unit)` at the call site, and is what makes the "refresh fires again on Navigation-Compose re-entry" fix in Step 6 actually work (an `init {}`-only load would only run once, the first time the ViewModel is constructed, not on subsequent navigations back to the same destination while the ViewModel instance is retained).

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.exercise.ExerciseViewModelTest"`
Expected: PASS (6 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 6: Write `ExerciseCounterScreen.kt` and wire the `NavHost` in `MainActivity.kt`**

This project has no Compose UI test precedent (neither Shape nor Reminders has one) — this composable is implemented directly and verified on-device in Task 7's final check, matching that established precedent. Every piece of logic underneath it (`ExerciseViewModel`, `SubCounterRepository`, `HabitStats`) is already fully unit tested above.

`app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseCounterScreen.kt`:
```kotlin
package com.ziv.reminders.ui.exercise

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ziv.reminders.R
import com.ziv.reminders.data.ALL_EXERCISE_KEYS
import com.ziv.reminders.data.EXERCISE_KEY_ARM_ROTATION
import com.ziv.reminders.data.EXERCISE_KEY_LATERAL_RAISE
import com.ziv.reminders.data.EXERCISE_KEY_PUSHUP
import com.ziv.reminders.data.EXERCISE_KEY_SITUP
import com.ziv.reminders.data.EXERCISE_SUB_COUNTER_DEFAULT

private val exerciseIcons = mapOf(
    EXERCISE_KEY_LATERAL_RAISE to R.drawable.exercise_weight_side,
    EXERCISE_KEY_ARM_ROTATION to R.drawable.exercise_weight_front,
    EXERCISE_KEY_SITUP to R.drawable.exercise_situp,
    EXERCISE_KEY_PUSHUP to R.drawable.exercise_pushup,
)

internal val exerciseLabels = mapOf(
    EXERCISE_KEY_LATERAL_RAISE to "Lateral Raise",
    EXERCISE_KEY_ARM_ROTATION to "Arm Rotation",
    EXERCISE_KEY_SITUP to "Sit-up",
    EXERCISE_KEY_PUSHUP to "Push-up",
)

@Composable
fun ExerciseCounterScreen(viewModel: ExerciseViewModel, onOpenStats: () -> Unit, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBack)

    val goalReached = uiState.completed
    val countColor = if (goalReached) GoalGreen else MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 80.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onOpenStats) {
                Icon(imageVector = Icons.Default.DateRange, contentDescription = "Your Progress", tint = GoalGreen)
            }
        }

        Text("Today's Exercises", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("${uiState.current}", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = countColor)
        Text(
            text = if (goalReached) "Goal reached!" else "goal: ${uiState.goal}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (goalReached) GoalGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))

        FloatingActionButton(
            onClick = { viewModel.increment() },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.width(320.dp).height(80.dp),
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Increment", modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(32.dp))

        ALL_EXERCISE_KEYS.forEach { key ->
            ExerciseSubCounterRow(
                iconRes = exerciseIcons.getValue(key),
                label = exerciseLabels.getValue(key),
                count = uiState.subCounters[key] ?: EXERCISE_SUB_COUNTER_DEFAULT,
                onDecrement = { viewModel.adjustSubCounter(key, -1) },
                onIncrement = { viewModel.adjustSubCounter(key, 1) },
            )
        }
    }
}

@Composable
private fun ExerciseSubCounterRow(iconRes: Int, label: String, count: Int, onDecrement: () -> Unit, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painter = painterResource(iconRes), contentDescription = label, modifier = Modifier.size(52.dp))
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onDecrement, modifier = Modifier.size(40.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp)) {
            Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(text = count.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp), textAlign = TextAlign.Center)
        OutlinedButton(onClick = onIncrement, modifier = Modifier.size(40.dp), shape = CircleShape, contentPadding = PaddingValues(0.dp)) {
            Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
```

Full `app/src/main/java/com/ziv/reminders/MainActivity.kt` (adds the `NavHost` with all 3 destinations — `ExerciseStatsScreen` is created in Task 6, so this file compiles only after Task 6; if executing tasks sequentially, expect a compile error here that resolves once Task 6 lands, or implement Tasks 5 and 6 together before the next test run):
```kotlin
package com.ziv.reminders

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ziv.reminders.ui.dashboard.DashboardScreen
import com.ziv.reminders.ui.dashboard.DashboardViewModel
import com.ziv.reminders.ui.exercise.ExerciseCounterScreen
import com.ziv.reminders.ui.exercise.ExerciseStatsScreen
import com.ziv.reminders.ui.exercise.ExerciseViewModel
import com.ziv.reminders.ui.theme.RemindersTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        requestBatteryOptimizationExemptionIfNeeded()
        val container = (application as RemindersApp).container
        setContent {
            RemindersTheme {
                val navController = rememberNavController()
                val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
                val exerciseViewModel: ExerciseViewModel = viewModel(factory = ExerciseViewModel.factory(container))

                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") {
                        // Fires every time this destination re-enters composition —
                        // including after popping back from the Exercise flow, not just
                        // on cold start — so the Exercise row's count never shows stale
                        // data after logging a workout. See ExerciseViewModel.kt's note
                        // on why refresh() lives at the call site, not in init{}.
                        LaunchedEffect(Unit) { dashboardViewModel.refresh() }
                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            onOpenExercise = { navController.navigate("exerciseCounter") },
                        )
                    }
                    composable("exerciseCounter") {
                        ExerciseCounterScreen(
                            viewModel = exerciseViewModel,
                            onOpenStats = { navController.navigate("exerciseStats") },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("exerciseStats") {
                        ExerciseStatsScreen(
                            viewModel = exerciseViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // No persisted "already asked" state, no denied-state banner — re-checks and
    // re-prompts every launch until actually granted, same shape as the notification
    // permission check above. Some OEMs (Samsung, Xiaomi) aggressively kill background
    // alarms/WorkManager without this exemption.
    private fun requestBatteryOptimizationExemptionIfNeeded() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/res/drawable/exercise_weight_side.png app/src/main/res/drawable/exercise_weight_front.png app/src/main/res/drawable/exercise_situp.png app/src/main/res/drawable/exercise_pushup.png app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseColors.kt app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseViewModel.kt app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseCounterScreen.kt app/src/main/java/com/ziv/reminders/MainActivity.kt app/src/test/java/com/ziv/reminders/ui/exercise/ExerciseViewModelTest.kt
git commit -m "Add ExerciseViewModel and ExerciseCounterScreen, wire NavHost"
```
(This commit will not compile standalone since `ExerciseStatsScreen` doesn't exist until Task 6 — either commit Tasks 5+6 together, or stage this commit locally without pushing until Task 6 lands, matching whichever of this repo's two commit-per-task vs. commit-per-working-build conventions the implementer prefers; prior plans in this repo always commit a working build, so combining Tasks 5 and 6 into one commit is the safer choice if working sequentially.)

---

### Task 6: `ExerciseStatsScreen` + heatmap-day-tap dialog

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseStatsScreen.kt`

**Interfaces:**
- Consumes: `ExerciseViewModel` (Task 5), `HabitStats.recordSuffix` (Task 1).
- No new test file — this composable's underlying logic (`subCounterValuesForDate`) is already covered by `ExerciseViewModelTest` (Task 5); the composable itself follows this project's no-Compose-UI-test precedent (see Task 5, Step 6).

- [ ] **Step 1: Write `ExerciseStatsScreen.kt`**

```kotlin
package com.ziv.reminders.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ziv.reminders.data.HabitStats
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ExerciseStatsScreen(viewModel: ExerciseViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsState()
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Your Progress", style = MaterialTheme.typography.titleMedium)
        }

        // isLoaded distinguishes "hasn't loaded yet" from "genuinely no history" — without
        // it this screen could flash an empty-history message on cold navigation.
        if (!uiState.isLoaded) return@Column

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(
                text = "Streak: ${uiState.streak} day${if (uiState.streak == 1) "" else "s"}" +
                    HabitStats.recordSuffix(uiState.isNewStreakRecord, "record"),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "This month: ${uiState.monthCount} day${if (uiState.monthCount == 1) "" else "s"}" +
                    HabitStats.recordSuffix(uiState.isNewMonthRecord, "best month"),
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (uiState.completedDates.isEmpty()) {
            EmptyState()
        } else {
            HeatmapGrid(dates = uiState.completedDates, today = today, onDayClick = { day -> selectedDate = day })
        }
    }

    selectedDate?.let { date ->
        SubCounterDetailDialog(viewModel = viewModel, date = date, onDismiss = { selectedDate = null })
    }
}

@Composable
private fun EmptyState() {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Hit your goal to start your streak",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeatmapGrid(dates: Set<LocalDate>, today: LocalDate, onDayClick: (LocalDate) -> Unit) {
    // Aligned to a fixed 7-column, Sunday-start grid so rows correspond to real calendar
    // weeks regardless of screen width — mirrors Shape's own HeatmapGrid exactly.
    val windowStart = remember(today) { today.minusMonths(12) }
    val alignedStart = remember(windowStart) {
        val daysSinceSunday = windowStart.dayOfWeek.value % 7
        windowStart.minusDays(daysSinceSunday.toLong())
    }
    // Weeks reversed (most recent week first) so today is always near the top with no
    // scrolling — mirrors Shape's own HeatmapGrid exactly.
    val days = remember(alignedStart, today) {
        generateSequence(alignedStart) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .toList()
            .chunked(7)
            .reversed()
            .flatten()
    }

    LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        items(days) { day ->
            val color = when {
                day == today && day !in dates -> HeatmapPending
                day in dates -> HeatmapHit
                else -> HeatmapMiss
            }
            val description = "${day.format(DateTimeFormatter.ISO_LOCAL_DATE)}: " +
                if (day == today && day !in dates) "not yet done"
                else if (day in dates) "goal hit" else "missed"

            Column(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(1.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
                    .clickable { onDayClick(day) }
                    .semantics { contentDescription = description }
            ) {}
        }
    }
}

@Composable
private fun SubCounterDetailDialog(viewModel: ExerciseViewModel, date: LocalDate, onDismiss: () -> Unit) {
    var values by remember(date) { mutableStateOf<Map<String, Int?>?>(null) }
    LaunchedEffect(date) { values = viewModel.subCounterValuesForDate(date) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DateTimeFormatter.ISO_LOCAL_DATE)) },
        text = {
            val current = values
            when {
                current == null -> Text("Loading…")
                current.values.all { it == null } -> Text("No data for this day")
                else -> Column {
                    exerciseLabels.forEach { (key, label) ->
                        Text("$label: ${current[key] ?: "—"}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
```

- [ ] **Step 2: Verify the module compiles**

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (Task 5's `MainActivity.kt` reference to `ExerciseStatsScreen` now resolves).

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green — no new unit tests in this task; `HeatmapGrid`'s day-click wiring and `SubCounterDetailDialog`'s three render branches are exercised on-device in Task 7).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseStatsScreen.kt
git commit -m "Add ExerciseStatsScreen with heatmap-day-tap detail dialog"
```

---

### Task 7: End-to-End Verification on Device

No Compose UI test framework exists in this project (matching Shape's own precedent) — verify manually on a physical device or emulator.

- [ ] **Step 1: Install and launch**

Run via Android Studio or `./gradlew.bat installDebug`.

- [ ] **Step 2: Verify the full flow**

1. Tap "Exercise" on the dashboard → `ExerciseCounterScreen` opens (big button, 4 sub-counter rows with the 4 exercise icons).
2. Tap the big `+` button 5 times → count turns green, "Goal reached!" shown.
3. Adjust one sub-counter (e.g. tap `+` on Push-up 3 times) → only that counter changes; the other 3 stay at their previous values (regression check for the clobber bug this plan's schema design avoids).
4. Tap the top-right stats icon → `ExerciseStatsScreen` opens, showing streak/month text and a 12-month heatmap with today highlighted amber (goal met today → green).
5. Tap a past heatmap day with no data → dialog shows "No data for this day."
6. Tap today's heatmap cell (after hitting goal) → dialog shows today's date + the 4 sub-counter values just entered.
7. Tap "Close" → dialog dismisses.
8. Press back → returns to `ExerciseCounterScreen`.
9. Press back again → returns to the dashboard; the Exercise row's count reflects the new total (**this is the regression check for the stale-refresh bug this plan's `LaunchedEffect(Unit)` fixes** — confirm it is NOT stale).
10. Verify Reading and Tanakh rows behave exactly as before (unaffected).

- [ ] **Step 3: Verify Room migration on an existing install**

If a debug build from before this feature was previously installed on the test device: install this build over it (not a clean install) and confirm the app launches without crashing, existing habit data (Exercise/Reading/Tanakh progress, streaks) is intact, and the new sub-counter table starts empty.

- [ ] **Step 4: Final commit if all checks pass**

```bash
git add .
git commit -m "Verify Exercise detail screen end-to-end on device"
```
