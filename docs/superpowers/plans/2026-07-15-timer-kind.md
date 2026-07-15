# Timer-with-Duration Kind Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the Timer-with-duration habit kind (a 15-minute "Reading" habit, rebuilt on the
shared engine from Plan 1) — Room schema v2, `TimerHabitRepository`, `HabitEngine` dispatch,
a foreground `TimerService` with a live countdown notification, and a dashboard row that starts
today's read, ticks down live while running, and shows the checkmark once the goal is met —
end-to-end, matching ReadBook's actual proven `ReadingTimerService`/`ReadingTimerRepository`
semantics.

**Architecture:** Room schema bumps to v2 via a real `Migration` (never
`fallbackToDestructiveMigration()`): `habit_instance` gains a nullable `timerTargetSeconds`
column, and a new `timer_daily_progress` table (keyed by `(habitInstanceId, date)`, mirroring
`counter_daily_progress`'s shape) tracks each day's remaining time and any in-progress session
via a timestamp-delta (`activeSessionStartedAt`), exactly like ReadBook's `DailyProgress` —
elapsed time is always computed from that timestamp at read/stop time, never from a periodic
tick-decrement, so there's no drift under Doze and no data-loss window. `TimerHabitRepository`
owns start/stop/status/streak for the kind (streak via a new enabledDaysMask-aware
`StreakCalculator`, since the seeded Reading habit runs Sun–Thu only — unlike Exercise's
every-day mask, a naive consecutive-day walk would wrongly break the streak on Fri/Sat).
A generalized `TimerService` (foreground, keyed by `habitInstanceId` in its Intent extras, same
generalization `HabitScheduler`/`HabitReminderReceiver` already use) drives the running session
and its ongoing per-instance notification. The dashboard is generalized to carry `HabitStatus`
directly per row (replacing Plan 1's pre-formatted `statusText` string) so `DashboardScreen` can
render each kind's own UI — Counter's tap-to-increment is unchanged; Timer's row ticks a local
1Hz countdown via `LaunchedEffect` while running and tapping toggles Start/Stop — the same
Flow-plus-local-ticker mechanism ReadBook's actual `HomeScreen`/`HomeViewModel` use (confirmed
against ReadBook's real code, not the`ServiceConnection` approach floated before this plan was
written), adapted to Reminders' poll-and-refresh (not Flow-reactive) `DashboardViewModel` via an
optimistic local state flip on toggle.

**Tech Stack:** Same as Plan 1 — Kotlin 2.3.0, Jetpack Compose (Material 3), Room 2.7.1 (KSP),
JUnit4 + Robolectric 4.16.1, `kotlinx-coroutines-test`. No new dependencies.

## Global Constraints

(Inherited from Plan 1 — still binding for every task below.)

- Package / application ID: `com.ziv.reminders`. `minSdk = 35`, `targetSdk = 36`.
- Scheduling is **inexact only**: `AlarmManager.setWindow()`. No exact-alarm permission.
- No data migration from Shape or ReadBook — the Reading habit's history starts at zero, same
  as Exercise did in Plan 1.
- No in-app "add habit" UI — the Reading instance is inserted via `ensureHabitsSeeded`, same
  mechanism as Exercise.
- Every Room schema change ships with a real `Migration` object; never
  `fallbackToDestructiveMigration()`. This plan is the schema's v1→v2 migration.
- TDD for all pure logic and repository/dispatch code; Robolectric (`@Config(sdk = [35])`) for
  anything touching Room, `AlarmManager`, or a `Service`; every commit after a task leaves
  `./gradlew :app:testDebugUnitTest` green.
- "Enabled" means the enabled-days bitmask evaluated against today's date (Sun=1 ... Sat=64,
  via the existing `isEnabledDay`) — no separate on/off flag.
- One notification channel per `HabitInstance` for its reminder notification (existing rule from
  Plan 1, unchanged). The Timer's *ongoing foreground* notification additionally gets its own
  low-importance, silent, per-instance channel (`habit_<id>_timer`) — a ReadBook-proven
  necessity (an ongoing ~30s-updated notification on the same channel as the hourly reminder
  would either spam sound/vibration or force the reminder channel itself to go silent). This
  doesn't violate "one channel per instance for reminders" — it adds a second, purpose-specific
  channel for the foreground-service notification only.

---

## File Structure

```
Reminders/
  app/src/main/java/com/ziv/reminders/
    data/
      HabitKind.kt                          (Modify — Task 1)
      HabitInstance.kt                      (Modify — Task 1)
      TimerDailyProgress.kt                 (Create — Task 1)
      TimerDailyProgressDao.kt              (Create — Task 1)
      AppDatabase.kt                        (Modify — Task 1)
      AppContainer.kt                       (Modify — Tasks 1, 4, 8)
      Clock.kt                              (Create — Task 2)
      StreakCalculator.kt                   (Create — Task 2)
      HabitStatus.kt                        (Modify — Task 3)
      TimerHabitRepository.kt               (Create — Task 3)
      HabitSeeding.kt                       (Modify — Task 8)
    engine/
      HabitEngine.kt                        (Modify — Task 4)
    notifications/
      HabitNotifications.kt                 (Modify — Task 6)
    service/
      TimerService.kt                       (Create — Task 7)
    scheduling/
      HabitReminderReceiver.kt              (Modify — Task 3)
    ui/dashboard/
      DashboardUiState.kt                   (Modify — Task 5)
      DashboardViewModel.kt                 (Modify — Tasks 3, 5, 9)
      DashboardScreen.kt                    (Modify — Tasks 5, 9)
    RemindersApp.kt                         (Modify — Task 8)
  app/src/main/AndroidManifest.xml          (Modify — Task 8)
  app/src/test/java/com/ziv/reminders/
    data/
      TimerDailyProgressDaoTest.kt          (Create — Task 1)
      AppDatabaseMigration1To2Test.kt       (Create — Task 1)
      StreakCalculatorTest.kt               (Create — Task 2)
      TimerHabitRepositoryTest.kt           (Create — Task 3)
    engine/
      HabitEngineTest.kt                    (Modify — Task 4)
    scheduling/
      HabitReminderReceiverTest.kt          (Modify — Tasks 3, 4)
    service/
      TimerServiceTest.kt                   (Create — Task 7)
    ui/dashboard/
      DashboardViewModelTest.kt             (Modify — Tasks 4, 5, 9)
      TestAppContainer.kt                   (Modify — Task 4)
```

---

### Task 1: Room schema v2 — `TimerDailyProgress`, `HabitInstance.timerTargetSeconds`, migration

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitKind.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitInstance.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/TimerDailyProgress.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/TimerDailyProgressDao.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/TimerDailyProgressDaoTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration1To2Test.kt`

**Interfaces:**
- Produces: `HabitKind.TIMER`; `HabitInstance.timerTargetSeconds: Int? = null` (trailing,
  defaulted — every existing positional `HabitInstance(...)` call site in the codebase keeps
  compiling unmodified); `data class TimerDailyProgress(habitInstanceId: Long, date: String,
  targetSeconds: Int, remainingSeconds: Int, completed: Boolean, completedAt: Long?,
  activeSessionStartedAt: Long?)`; `interface TimerDailyProgressDao { suspend fun getByDate(...):
  TimerDailyProgress?; suspend fun upsert(...); suspend fun getCompletedDates(habitInstanceId:
  Long): List<String>; suspend fun getActiveSessions(): List<TimerDailyProgress> }`;
  `AppDatabase.MIGRATION_1_2`. Consumed by: `TimerHabitRepository` (Task 3).

- [ ] **Step 1: Write the failing DAO test**

`app/src/test/java/com/ziv/reminders/data/TimerDailyProgressDaoTest.kt`:
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
class TimerDailyProgressDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByDate_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.timerDailyProgressDao().getByDate(1L, "2026-07-15"))
        db.close()
    }

    @Test
    fun upsert_thenGetByDate_returnsTheRow() = runTest {
        val db = newDb()
        val progress = TimerDailyProgress(
            habitInstanceId = 1L, date = "2026-07-15", targetSeconds = 900,
            remainingSeconds = 900, completed = false, completedAt = null,
            activeSessionStartedAt = null,
        )
        db.timerDailyProgressDao().upsert(progress)

        assertEquals(progress, db.timerDailyProgressDao().getByDate(1L, "2026-07-15"))
        db.close()
    }

    @Test
    fun upsert_sameKey_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.timerDailyProgressDao().upsert(
            TimerDailyProgress(1L, "2026-07-15", 900, 900, false, null, null)
        )
        db.timerDailyProgressDao().upsert(
            TimerDailyProgress(1L, "2026-07-15", 900, 0, true, 123L, null)
        )

        val loaded = db.timerDailyProgressDao().getByDate(1L, "2026-07-15")
        assertEquals(0, loaded?.remainingSeconds)
        assertEquals(true, loaded?.completed)
        db.close()
    }

    @Test
    fun getCompletedDates_returnsOnlyCompletedRowsForThatInstance() = runTest {
        val db = newDb()
        db.timerDailyProgressDao().upsert(TimerDailyProgress(1L, "2026-07-13", 900, 0, true, 1L, null))
        db.timerDailyProgressDao().upsert(TimerDailyProgress(1L, "2026-07-14", 900, 300, false, null, null))
        db.timerDailyProgressDao().upsert(TimerDailyProgress(2L, "2026-07-13", 900, 0, true, 1L, null)) // different instance

        assertEquals(listOf("2026-07-13"), db.timerDailyProgressDao().getCompletedDates(1L))
        db.close()
    }

    @Test
    fun getActiveSessions_returnsRowsWithANonNullActiveSessionStartedAt_acrossAllInstances() = runTest {
        val db = newDb()
        db.timerDailyProgressDao().upsert(TimerDailyProgress(1L, "2026-07-15", 900, 600, false, null, 1_000L))
        db.timerDailyProgressDao().upsert(TimerDailyProgress(2L, "2026-07-14", 900, 900, false, null, null)) // not active
        db.timerDailyProgressDao().upsert(TimerDailyProgress(3L, "2026-07-15", 900, 300, false, null, 2_000L))

        val active = db.timerDailyProgressDao().getActiveSessions()
        assertEquals(setOf(1L, 3L), active.map { it.habitInstanceId }.toSet())
        db.close()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.data.TimerDailyProgressDaoTest"`
Expected: FAIL — `TimerDailyProgress`, `TimerDailyProgressDao`, and `AppDatabase.timerDailyProgressDao()` don't exist yet (compile error).

- [ ] **Step 3: Write the schema implementation**

`app/src/main/java/com/ziv/reminders/data/HabitKind.kt`:
```kotlin
package com.ziv.reminders.data

/**
 * The extensibility primitive: adding a new instance of an existing kind needs only a
 * HabitInstance row (see HabitSeeding.kt), zero new Kotlin classes. A genuinely new kind
 * still needs a new enum case, HabitStatus variant, repository, and HabitEngine branch —
 * SCHEDULE_CURSOR is added by a later plan, as a real Room migration.
 */
enum class HabitKind {
    COUNTER,
    TIMER,
}
```

`app/src/main/java/com/ziv/reminders/data/HabitInstance.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * kind is stored as a plain String (HabitKind.name), not a Room-mapped enum column — so a
 * future kind's migration only needs a data INSERT, never a schema change to this column.
 * counterGoal/timerTargetSeconds are nullable per-kind config columns; each new kind adds its
 * own nullable trailing column the same way (a defaulted trailing param, so every existing
 * positional HabitInstance(...) call site keeps compiling unmodified).
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
)
```

`app/src/main/java/com/ziv/reminders/data/TimerDailyProgress.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity

/**
 * Mirrors ReadBook's DailyProgress: a non-null activeSessionStartedAt with no matching Stop by
 * next app launch means the process died mid-session — RemindersApp's startup self-heal calls
 * TimerHabitRepository.reconcileCrashedSessions() to close these out (see Task 8).
 */
@Entity(tableName = "timer_daily_progress", primaryKeys = ["habitInstanceId", "date"])
data class TimerDailyProgress(
    val habitInstanceId: Long,
    val date: String,
    val targetSeconds: Int,
    val remainingSeconds: Int,
    val completed: Boolean,
    val completedAt: Long?,
    val activeSessionStartedAt: Long?,
)
```

`app/src/main/java/com/ziv/reminders/data/TimerDailyProgressDao.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TimerDailyProgressDao {
    @Query("SELECT * FROM timer_daily_progress WHERE habitInstanceId = :habitInstanceId AND date = :date")
    suspend fun getByDate(habitInstanceId: Long, date: String): TimerDailyProgress?

    @Upsert
    suspend fun upsert(progress: TimerDailyProgress)

    @Query("SELECT date FROM timer_daily_progress WHERE habitInstanceId = :habitInstanceId AND completed = 1")
    suspend fun getCompletedDates(habitInstanceId: Long): List<String>

    // No habitInstanceId filter — RemindersApp's startup self-heal reconciles every instance's
    // dangling session in one pass, not one call per instance (see TimerHabitRepository, Task 3).
    @Query("SELECT * FROM timer_daily_progress WHERE activeSessionStartedAt IS NOT NULL")
    suspend fun getActiveSessions(): List<TimerDailyProgress>
}
```

`app/src/main/java/com/ziv/reminders/data/AppDatabase.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HabitInstance::class, CounterDailyProgress::class, TimerDailyProgress::class],
    version = 2,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitInstanceDao(): HabitInstanceDao
    abstract fun counterDailyProgressDao(): CounterDailyProgressDao
    abstract fun timerDailyProgressDao(): TimerDailyProgressDao

    companion object {
        /** Adds Timer-with-duration kind support: a nullable per-instance target-duration
         * column on habit_instance, plus its own daily-progress table (mirrors
         * counter_daily_progress's shape). Never fallbackToDestructiveMigration() — see
         * Global Constraints. */
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
    }
}
```

`app/src/main/java/com/ziv/reminders/data/AppContainer.kt` (only the migration wiring and the
new DAO getter change here — `timerHabitRepository`/`habitEngine` wiring comes in Task 4):
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
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    override val habitInstanceDao get() = db.habitInstanceDao()
    val counterDailyProgressDao get() = db.counterDailyProgressDao()
    val timerDailyProgressDao get() = db.timerDailyProgressDao()
    override val counterHabitRepository: CounterHabitRepository by lazy { CounterHabitRepository(counterDailyProgressDao) }
    override val habitEngine: HabitEngine by lazy { HabitEngine(counterHabitRepository) }
    val habitScheduler: HabitScheduler by lazy { HabitScheduler(appContext) }
}

interface DashboardDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.data.TimerDailyProgressDaoTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Write the failing migration test**

`app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration1To2Test.kt`:
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
class AppDatabaseMigration1To2Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2_preservesExistingHabitInstanceRows_andAddsTimerSupport() {
        // Seed a v1 database with a real row, exactly as an already-installed app would have.
        helper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL(
                "INSERT INTO habit_instance (id, kind, name, enabledDaysMask, notificationTitle, notificationBody, counterGoal) " +
                    "VALUES (1, 'COUNTER', 'Exercise', 127, 't', 'b', 5)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, AppDatabase.MIGRATION_1_2)

        migrated.query("SELECT name, timerTargetSeconds FROM habit_instance WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Exercise", cursor.getString(0))
            assertTrue(cursor.isNull(1))
        }
        migrated.query("SELECT COUNT(*) FROM timer_daily_progress").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test"
    }
}
```

- [ ] **Step 6: Run the migration test, then the full data-package suite**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.data.AppDatabaseMigration1To2Test"`
Expected: PASS (1 test)

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all existing tests still green — `HabitInstance`'s new trailing defaulted param
doesn't break any existing positional-constructor call site)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitKind.kt app/src/main/java/com/ziv/reminders/data/HabitInstance.kt app/src/main/java/com/ziv/reminders/data/TimerDailyProgress.kt app/src/main/java/com/ziv/reminders/data/TimerDailyProgressDao.kt app/src/main/java/com/ziv/reminders/data/AppDatabase.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/test/java/com/ziv/reminders/data/TimerDailyProgressDaoTest.kt app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration1To2Test.kt
git commit -m "Add Room schema v2 for Timer kind (TimerDailyProgress + migration)"
```

---

### Task 2: `Clock` abstraction and `StreakCalculator`

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/data/Clock.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/StreakCalculator.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/StreakCalculatorTest.kt`

**Interfaces:**
- Produces: `interface Clock { fun nowMillis(): Long }`, `object SystemClock : Clock` — consumed
  by `TimerHabitRepository` and `TimerService` (Tasks 3, 7). `object StreakCalculator { fun
  calculate(completedDates: Set<LocalDate>, enabledDaysMask: Int, today: LocalDate): Int }` —
  consumed by `TimerHabitRepository` (Task 3).

`Clock` has no dedicated test (mirrors ReadBook — it's a one-line seam, exercised indirectly
everywhere it's injected as a `FakeClock` in later tests).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakCalculatorTest {

    private val sunThuMask = 0b0011111 // Sun-Thu
    private val allDaysMask = 0b1111111

    @Test
    fun calculate_todayCompleted_countsFromToday() {
        val thursday = LocalDate.of(2026, 7, 16)
        val completed = setOf(thursday, thursday.minusDays(1), thursday.minusDays(2))

        assertEquals(3, StreakCalculator.calculate(completed, sunThuMask, thursday))
    }

    @Test
    fun calculate_todayNotYetCompleted_anchorsAtYesterday() {
        val thursday = LocalDate.of(2026, 7, 16)
        val completed = setOf(thursday.minusDays(1), thursday.minusDays(2)) // yesterday, day before — not today

        assertEquals(2, StreakCalculator.calculate(completed, sunThuMask, thursday))
    }

    @Test
    fun calculate_gapOnAnEnabledDay_breaksTheStreak() {
        val thursday = LocalDate.of(2026, 7, 16)
        // Wednesday (yesterday) missing, Tuesday completed — gap on an enabled day.
        val completed = setOf(thursday.minusDays(2))

        assertEquals(0, StreakCalculator.calculate(completed, sunThuMask, thursday))
    }

    @Test
    fun calculate_gapOnADisabledDay_isSkippedWithoutBreakingTheStreak() {
        // Sunday, with Fri/Sat (disabled under Sun-Thu) having no rows at all.
        val sunday = LocalDate.of(2026, 7, 19)
        val thursdayBefore = sunday.minusDays(3) // the previous Thursday
        val completed = setOf(sunday, thursdayBefore) // Fri/Sat between them intentionally absent

        assertEquals(2, StreakCalculator.calculate(completed, sunThuMask, sunday))
    }

    @Test
    fun calculate_allDaysDisabled_returnsZero() {
        val today = LocalDate.of(2026, 7, 16)
        assertEquals(0, StreakCalculator.calculate(setOf(today), enabledDaysMask = 0, today))
    }

    @Test
    fun calculate_everyDayEnabled_matchesASimpleConsecutiveWalk() {
        val today = LocalDate.of(2026, 7, 16)
        val completed = setOf(today, today.minusDays(1), today.minusDays(2))

        assertEquals(3, StreakCalculator.calculate(completed, allDaysMask, today))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.data.StreakCalculatorTest"`
Expected: FAIL — `StreakCalculator` doesn't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/data/Clock.kt`:
```kotlin
package com.ziv.reminders.data

/** Seam for deterministic time in tests — real code uses SystemClock. */
interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
```

`app/src/main/java/com/ziv/reminders/data/StreakCalculator.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate

/**
 * Combines two rules neither kind's own inline streak logic covers alone: CounterHabitRepository
 * anchors at yesterday when today isn't done yet (the day isn't over), but its every-day
 * enabledDaysMask means it never needed to skip disabled days. The Reading habit runs Sun-Thu
 * only (mirroring ReadBook's actual default), so a naive consecutive-day walk would wrongly
 * break its streak every Friday/Saturday — this calculator skips disabled days entirely (they
 * neither extend nor break the streak), mirroring ReadBook's real StreakCalculator. Deliberately
 * not applied to Counter's existing (already shipped, on-device-verified) currentStreak — its
 * enabledDaysMask is always all-days, so the two are behaviorally identical for it anyway.
 */
object StreakCalculator {

    private const val MAX_LOOKBACK_DAYS = 3650L // ~10 years — safety bound, not a real limit

    fun calculate(completedDates: Set<LocalDate>, enabledDaysMask: Int, today: LocalDate): Int {
        if (enabledDaysMask == 0) return 0

        val anchor = if (today in completedDates) today else today.minusDays(1)
        var streak = 0
        var date = anchor
        var daysChecked = 0L
        while (daysChecked < MAX_LOOKBACK_DAYS) {
            if (isEnabledDay(date, enabledDaysMask)) {
                if (date in completedDates) streak++ else break
            }
            date = date.minusDays(1)
            daysChecked++
        }
        return streak
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.data.StreakCalculatorTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/Clock.kt app/src/main/java/com/ziv/reminders/data/StreakCalculator.kt app/src/test/java/com/ziv/reminders/data/StreakCalculatorTest.kt
git commit -m "Add Clock abstraction and enabledDaysMask-aware StreakCalculator"
```

---

### Task 3: `TimerHabitRepository`, `HabitStatus.TimerStatus`, and the two exhaustive-`when` compile fixes it forces

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitStatus.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/TimerHabitRepository.kt`
- Modify: `app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/TimerHabitRepositoryTest.kt`

**Interfaces:**
- Consumes: `TimerDailyProgress`, `TimerDailyProgressDao` (Task 1); `Clock`, `StreakCalculator`
  (Task 2).
- Produces: `HabitStatus.TimerStatus(remainingSeconds: Int, targetSeconds: Int, isRunning:
  Boolean, completed: Boolean)`; `class TimerHabitRepository(dao: TimerDailyProgressDao, clock:
  Clock) { suspend fun todayStatus(...): HabitStatus.TimerStatus; suspend fun start(instance,
  today): TimerDailyProgress; suspend fun stop(instance, today): TimerDailyProgress?; suspend fun
  reconcileCrashedSessions(): List<TimerDailyProgress>; suspend fun currentStreak(instance,
  today): Int }` — consumed by `HabitEngine` (Task 4), `TimerService` (Task 7), `RemindersApp`
  (Task 8).

`HabitStatus` is a sealed interface — adding `TimerStatus` makes the `when (status)` expressions
in `DashboardViewModel.refresh()` and `HabitReminderReceiver.onReceive()` non-exhaustive, which
is a compile error in `main` sourceSet code, not just a test failure. Both need a branch added in
this same task to keep the build green. `HabitReminderReceiver`'s fix is final (`status.completed`
is correct for every kind, permanently). `DashboardViewModel`'s fix is deliberately minimal — Task
5 replaces `HabitRowUiState`'s shape entirely (carrying `HabitStatus` directly instead of a
pre-formatted string), which supersedes the one-line branch added here.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ziv/reminders/data/TimerHabitRepositoryTest.kt`:
```kotlin
package com.ziv.reminders.data

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeClock(var millis: Long = 0L) : Clock {
    override fun nowMillis(): Long = millis
}

private class FakeTimerDailyProgressDao : TimerDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, TimerDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: TimerDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
    override suspend fun getActiveSessions() = rows.values.filter { it.activeSessionStartedAt != null }
}

class TimerHabitRepositoryTest {

    private val instance = HabitInstance(
        id = 1L, kind = "TIMER", name = "Reading", enabledDaysMask = 0b0011111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null, timerTargetSeconds = 900,
    )
    private val today = LocalDate.of(2026, 7, 12) // a Sunday, enabled under Sun-Thu

    @Test
    fun todayStatus_noRowYet_isFullTargetNotRunningNotCompleted() = runTest {
        val repo = TimerHabitRepository(FakeTimerDailyProgressDao(), FakeClock())

        val status = repo.todayStatus(instance, today)

        assertEquals(HabitStatus.TimerStatus(remainingSeconds = 900, targetSeconds = 900, isRunning = false, completed = false), status)
    }

    @Test
    fun start_onAFreshDay_createsRowWithFullTargetAndActiveSession() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)

        val row = repo.start(instance, today)

        assertEquals(900, row.remainingSeconds)
        assertEquals(1_000_000L, row.activeSessionStartedAt)
        assertFalse(row.completed)
    }

    @Test
    fun start_resumingAPausedDay_keepsRemainingSecondsFromBefore() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 60_000L // 60s elapse
        repo.stop(instance, today) // pauses with remainingSeconds = 840

        clock.millis = 2_000_000L
        val resumed = repo.start(instance, today)

        assertEquals(840, resumed.remainingSeconds)
        assertEquals(2_000_000L, resumed.activeSessionStartedAt)
    }

    @Test
    fun start_whenTodayAlreadyCompleted_isANoOp() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 900_000L // full target elapses
        val completed = repo.stop(instance, today)
        assertTrue(completed?.completed == true)

        clock.millis += 500_000L
        val result = repo.start(instance, today)

        assertEquals(completed, result)
        assertNull(dao.getByDate(1L, today.toString())?.activeSessionStartedAt)
    }

    @Test
    fun stop_reducesRemainingByElapsedTime_andClearsActiveSession() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 120_000L // 120s elapse

        val row = repo.stop(instance, today)

        assertEquals(780, row?.remainingSeconds)
        assertNull(row?.activeSessionStartedAt)
        assertFalse(row?.completed == true)
    }

    @Test
    fun stop_whenElapsedReachesTarget_marksCompleted() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 900_000L // exactly the full target

        val row = repo.stop(instance, today)

        assertEquals(0, row?.remainingSeconds)
        assertTrue(row?.completed == true)
        assertEquals(clock.millis, row?.completedAt)
    }

    @Test
    fun stop_whenNoActiveSession_isANoOp() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 60_000L
        repo.stop(instance, today)
        val before = dao.getByDate(1L, today.toString())

        val result = repo.stop(instance, today) // already stopped — second call

        assertEquals(before, result)
    }

    @Test
    fun todayStatus_whileRunning_computesRemainingFromWallClockAtCallTime() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 200_000L // 200s pass while still running, no stop() called

        val status = repo.todayStatus(instance, today)

        assertEquals(700, status.remainingSeconds)
        assertTrue(status.isRunning)
    }

    @Test
    fun reconcileCrashedSessions_finishesEveryDanglingSessionAndClearsActiveFlag() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        val yesterday = today.minusDays(1)
        repo.start(instance, yesterday) // simulate a session never stopped (crash)
        clock.millis += 60_000L // detected later, on today's app launch

        val reconciled = repo.reconcileCrashedSessions()

        assertEquals(1, reconciled.size)
        assertEquals(yesterday.toString(), reconciled[0].date)
        assertNull(reconciled[0].activeSessionStartedAt)
        assertEquals(840, reconciled[0].remainingSeconds)
    }

    @Test
    fun currentStreak_delegatesToStreakCalculatorWithTheInstancesEnabledDaysMask() = runTest {
        val dao = FakeTimerDailyProgressDao()
        val thursday = today.plusDays(4) // Thursday, still within Sun-Thu
        dao.rows[1L to today.toString()] = TimerDailyProgress(1L, today.toString(), 900, 0, true, 1L, null)
        val repo = TimerHabitRepository(dao, FakeClock())

        // Sunday completed, Mon-Wed have no rows at all (never happened, not just "missed"),
        // so a plain consecutive walk from Thursday would immediately return 0 — proving this
        // delegates to StreakCalculator's enabled-day-aware walk, not a naive one.
        assertEquals(0, repo.currentStreak(instance, thursday))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.data.TimerHabitRepositoryTest"`
Expected: FAIL — `TimerHabitRepository` and `HabitStatus.TimerStatus` don't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/data/HabitStatus.kt`:
```kotlin
package com.ziv.reminders.data

/**
 * The one type unified across every kind — see HabitEngine (engine/HabitEngine.kt) for why
 * only the read path (todayStatus/currentStreak) is generic; each kind's own progress-marking
 * action stays a method on that kind's own repository.
 */
sealed interface HabitStatus {
    data class CounterStatus(val current: Int, val goal: Int, val completed: Boolean) : HabitStatus
    data class TimerStatus(
        val remainingSeconds: Int,
        val targetSeconds: Int,
        val isRunning: Boolean,
        val completed: Boolean,
    ) : HabitStatus
}
```

`app/src/main/java/com/ziv/reminders/data/TimerHabitRepository.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate

/**
 * Timestamp-delta model (mirrors ReadBook's proven ReadingTimerRepository): only
 * TimerDailyProgress.activeSessionStartedAt is written while a session runs, and elapsed time
 * is computed from it at read/stop time — never a periodic tick-decrement — so there's no
 * data-loss window and no drift under Doze. Unlike ReadBook, streaks are computed on demand from
 * completed-date rows (StreakCalculator), not cached in a separate Stats table, matching
 * CounterHabitRepository's on-the-fly approach — and there's no resetToday, since neither
 * Reminders kind has a reset affordance on this dashboard.
 */
class TimerHabitRepository(
    private val dao: TimerDailyProgressDao,
    private val clock: Clock,
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
     * habit instance, and closes each one out — see RemindersApp's startup self-heal (Task 8). */
    suspend fun reconcileCrashedSessions(): List<TimerDailyProgress> =
        dao.getActiveSessions().map { finishSession(it) }

    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int {
        val completedDates = dao.getCompletedDates(instance.id)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
        return StreakCalculator.calculate(completedDates, instance.enabledDaysMask, today)
    }

    private suspend fun finishSession(row: TimerDailyProgress): TimerDailyProgress {
        val startedAt = requireNotNull(row.activeSessionStartedAt)
        val elapsedSeconds = ((clock.nowMillis() - startedAt) / 1000L).toInt()
        val newRemaining = (row.remainingSeconds - elapsedSeconds).coerceAtLeast(0)
        val justCompleted = newRemaining == 0
        val updated = row.copy(
            remainingSeconds = newRemaining,
            completed = row.completed || justCompleted,
            completedAt = if (justCompleted) clock.nowMillis() else row.completedAt,
            activeSessionStartedAt = null,
        )
        dao.upsert(updated)
        return updated
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
                }
```

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt` — change only the
`when` block inside `refresh()` (superseded by Task 5's full `HabitRowUiState` rework):
```kotlin
                val status = dataSource.habitEngine.todayStatus(instance, today)
                val streak = dataSource.habitEngine.currentStreak(instance, today)
                val (statusText, completed) = when (status) {
                    is HabitStatus.CounterStatus -> "${status.current}/${status.goal}" to status.completed
                    is HabitStatus.TimerStatus -> {
                        val minutes = status.remainingSeconds / 60
                        val seconds = status.remainingSeconds % 60
                        "%d:%02d".format(minutes, seconds) to status.completed
                    }
                }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.data.TimerHabitRepositoryTest"`
Expected: PASS (10 tests)

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all existing tests, including `HabitReminderReceiverTest` and
`DashboardViewModelTest`, still green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitStatus.kt app/src/main/java/com/ziv/reminders/data/TimerHabitRepository.kt app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt app/src/test/java/com/ziv/reminders/data/TimerHabitRepositoryTest.kt
git commit -m "Add TimerHabitRepository and HabitStatus.TimerStatus"
```

---

### Task 4: `HabitEngine` dispatch extended for `TIMER`

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/engine/HabitEngine.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`
- Modify: `app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt`
- Modify: `app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt`
- Modify: `app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt`

**Interfaces:**
- Consumes: `TimerHabitRepository` (Task 3).
- Produces: `HabitEngine(counterRepository: CounterHabitRepository, timerRepository:
  TimerHabitRepository)` — the constructor signature change. Every existing call site that
  constructs `HabitEngine(...)` must be updated in this same task for the module to compile.

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt` (new imports and a new
private fake alongside the existing `FakeCounterDailyProgressDao`, plus two new test methods —
full resulting file shown since the existing `HabitEngine(...)` construction calls also change):

```kotlin
package com.ziv.reminders.engine

import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.CounterDailyProgress
import com.ziv.reminders.data.CounterDailyProgressDao
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitStatus
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

class HabitEngineTest {

    private val counterInstance = HabitInstance(
        id = 1L, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )
    private val timerInstance = HabitInstance(
        id = 2L, kind = "TIMER", name = "Reading", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null, timerTargetSeconds = 900,
    )
    private val today = LocalDate.of(2026, 7, 14)

    private fun newEngine(): HabitEngine = HabitEngine(
        CounterHabitRepository(FakeCounterDailyProgressDao()),
        TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock),
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
        val engine = HabitEngine(CounterHabitRepository(counterDao), TimerHabitRepository(FakeTimerDailyProgressDao(), SystemClock))

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
        val engine = HabitEngine(CounterHabitRepository(FakeCounterDailyProgressDao()), TimerHabitRepository(timerDao, SystemClock))

        assertEquals(1, engine.currentStreak(timerInstance, today))
    }

    @Test
    fun todayStatus_unknownKind_throws() = runTest {
        val unknown = counterInstance.copy(kind = "SOMETHING_ELSE")

        assertFailsWith<IllegalArgumentException> { newEngine().todayStatus(unknown, today) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.engine.HabitEngineTest"`
Expected: FAIL — `HabitEngine`'s constructor doesn't accept a `TimerHabitRepository` yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/engine/HabitEngine.kt`:
```kotlin
package com.ziv.reminders.engine

import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitKind
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.TimerHabitRepository
import java.time.LocalDate

/**
 * Dispatches the two calls every kind can answer generically. Write actions (Counter's
 * increment, Timer's start/stop, later ScheduleCursor's markRead) deliberately stay on each
 * kind's own repository, not here — see Plan 1's Architecture section for why.
 */
class HabitEngine(
    private val counterRepository: CounterHabitRepository,
    private val timerRepository: TimerHabitRepository,
) {

    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus =
        when (instance.kind) {
            HabitKind.COUNTER.name -> counterRepository.todayStatus(instance, today)
            HabitKind.TIMER.name -> timerRepository.todayStatus(instance, today)
            else -> throw IllegalArgumentException("Unknown habit kind: ${instance.kind}")
        }

    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int =
        when (instance.kind) {
            HabitKind.COUNTER.name -> counterRepository.currentStreak(instance, today)
            HabitKind.TIMER.name -> timerRepository.currentStreak(instance, today)
            else -> throw IllegalArgumentException("Unknown habit kind: ${instance.kind}")
        }
}
```

`app/src/main/java/com/ziv/reminders/data/AppContainer.kt` (full file — adds `timerHabitRepository`
and updates the `HabitEngine(...)` call):
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
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    override val habitInstanceDao get() = db.habitInstanceDao()
    val counterDailyProgressDao get() = db.counterDailyProgressDao()
    val timerDailyProgressDao get() = db.timerDailyProgressDao()
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

`app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt` (full file):
```kotlin
package com.ziv.reminders.ui.dashboard

import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.engine.HabitEngine

class TestAppContainer(db: AppDatabase) : DashboardDataSource {
    override val habitInstanceDao = db.habitInstanceDao()
    override val counterHabitRepository = CounterHabitRepository(db.counterDailyProgressDao())
    private val timerHabitRepository = TimerHabitRepository(db.timerDailyProgressDao(), SystemClock)
    override val habitEngine = HabitEngine(counterHabitRepository, timerHabitRepository)
}
```

In `app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt`, update both
`receiver.habitEngineOverride = HabitEngine(CounterHabitRepository(db.counterDailyProgressDao()))`
lines to:
```kotlin
        receiver.habitEngineOverride = HabitEngine(
            CounterHabitRepository(db.counterDailyProgressDao()),
            com.ziv.reminders.data.TimerHabitRepository(db.timerDailyProgressDao(), com.ziv.reminders.data.SystemClock),
        )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.engine.HabitEngineTest"`
Expected: PASS (5 tests)

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all tests, including `DashboardViewModelTest` and `HabitReminderReceiverTest`, green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/engine/HabitEngine.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt app/src/test/java/com/ziv/reminders/ui/dashboard/TestAppContainer.kt app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt
git commit -m "Extend HabitEngine dispatch for TIMER kind"
```

---

### Task 5: Dashboard carries `HabitStatus` directly (kind-agnostic data model)

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardUiState.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Produces: `data class HabitRowUiState(instanceId: Long, name: String, status: HabitStatus,
  streak: Int)` — replaces the Task 3-era `statusText`/`completed` fields. Consumed by
  `DashboardScreen`. This task makes the Timer row render (read-only: static remaining time, no
  tap interaction) — Task 9 adds the live ticker and Start/Stop toggle once `TimerService` exists.

- [ ] **Step 1: Update the failing test assertions**

Full `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`:
```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitStatus
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DashboardViewModelTest {

    @Test
    fun refresh_oneHabitNotYetDone_populatesOneRow() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoaded)
        assertEquals(1, state.habits.size)
        assertEquals(HabitStatus.CounterStatus(current = 0, goal = 5, completed = false), state.habits[0].status)

        db.close()
    }

    @Test
    fun onIncrement_updatesStatusAndCompletion() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))
        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        repeat(5) {
            viewModel.onIncrement(1L)
            testScheduler.advanceUntilIdle()
        }

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.CounterStatus
        assertEquals(5, status.current)
        assertTrue(status.completed)

        db.close()
    }

    @Test
    fun refresh_habitDisabledToday_isExcludedFromRows() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(2L, "COUNTER", "Never", 0, "t", "b", 5)
        )
        val viewModel = DashboardViewModel(TestAppContainer(db))

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isLoaded)
        assertEquals(1, state.habits.size)
        assertEquals("Exercise", state.habits[0].name)

        db.close()
    }

    @Test
    fun refresh_timerHabitNotYetStarted_populatesRowAtFullTarget() = runTest {
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

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.TimerStatus
        assertEquals(900, status.remainingSeconds)
        assertEquals(false, status.isRunning)

        db.close()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `HabitRowUiState` has no `status` property yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardUiState.kt`:
```kotlin
package com.ziv.reminders.ui.dashboard

import com.ziv.reminders.data.HabitStatus

data class HabitRowUiState(
    val instanceId: Long,
    val name: String,
    val status: HabitStatus,
    val streak: Int,
)

data class DashboardUiState(
    val habits: List<HabitRowUiState> = emptyList(),
    val isLoaded: Boolean = false,
)
```

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`:
```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.isEnabledDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class DashboardViewModel(private val dataSource: DashboardDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val instances = dataSource.habitInstanceDao.getAll()
                .filter { isEnabledDay(today, it.enabledDaysMask) }
            val rows = instances.map { instance ->
                val status = dataSource.habitEngine.todayStatus(instance, today)
                val streak = dataSource.habitEngine.currentStreak(instance, today)
                HabitRowUiState(instance.id, instance.name, status, streak)
            }
            _uiState.value = DashboardUiState(habits = rows, isLoaded = true)
        }
    }

    fun onIncrement(instanceId: Long) {
        viewModelScope.launch {
            val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return@launch
            dataSource.counterHabitRepository.increment(instance, LocalDate.now())
            refresh()
        }
    }

    companion object {
        fun factory(dataSource: DashboardDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = DashboardViewModel(dataSource) as T
            }
    }
}
```

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`:
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ziv.reminders.data.HabitStatus

@Composable
fun DashboardScreen(viewModel: DashboardViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Re-reads current state on every resume (first composition, backgrounding, notification
    // tap) so the dashboard never shows stale data — see Plan 1's final-review Issue 2/4.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Today", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        if (!uiState.isLoaded) return@Column
        uiState.habits.forEach { habit ->
            HabitRow(habit = habit, onIncrement = { viewModel.onIncrement(habit.instanceId) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HabitRow(habit: HabitRowUiState, onIncrement: () -> Unit) {
    when (habit.status) {
        is HabitStatus.CounterStatus -> CounterHabitRow(habit, habit.status, onIncrement)
        is HabitStatus.TimerStatus -> TimerHabitRow(habit, habit.status)
    }
}

@Composable
private fun CounterHabitRow(habit: HabitRowUiState, status: HabitStatus.CounterStatus, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onIncrement),
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

// Read-only for now — Task 9 adds the live 1Hz countdown and Start/Stop tap once TimerService
// exists to actually run a session.
@Composable
private fun TimerHabitRow(habit: HabitRowUiState, status: HabitStatus.TimerStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
            Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
        }
        val minutes = status.remainingSeconds / 60
        val seconds = status.remainingSeconds % 60
        Text(
            text = if (status.completed) "✓" else "%d:%02d".format(minutes, seconds),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: PASS (4 tests)

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (Compose code compiles)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardUiState.kt app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt
git commit -m "Dashboard rows carry HabitStatus directly, render per-kind"
```

---

### Task 6: `HabitNotifications` — per-instance timer channel and notification builders

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/notifications/HabitNotifications.kt`

**Interfaces:**
- Produces: `HabitNotifications.timerChannelId(habitInstanceId: Long): String;
  timerNotificationId(habitInstanceId: Long): Int; createTimerChannel(context, habitInstanceId);
  buildTimerPlaceholderNotification(context, habitInstanceId): Notification;
  buildTimerNotification(context, instance: HabitInstance, remainingSeconds: Int): Notification`
  — consumed by `TimerService` (Task 7). No dedicated test file — exercised indirectly through
  `TimerServiceTest` (Task 7), matching how the existing reminder-notification builders are only
  tested via `HabitReminderReceiverTest`.

- [ ] **Step 1: Write the implementation**

Full `app/src/main/java/com/ziv/reminders/notifications/HabitNotifications.kt`:
```kotlin
package com.ziv.reminders.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ziv.reminders.MainActivity
import com.ziv.reminders.R
import com.ziv.reminders.data.HabitInstance

/** One channel per HabitInstance for its reminder notification (not per-kind or shared) — see
 * Global Constraints. The ongoing Timer foreground notification gets its own second, low-
 * importance, silent per-instance channel — see this plan's Global Constraints for why. */
object HabitNotifications {
    fun channelId(instance: HabitInstance): String = "habit_${instance.id}"
    fun notificationId(instance: HabitInstance): Int = instance.id.toInt()

    fun createChannel(context: Context, instance: HabitInstance) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId(instance), instance.name, NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun buildReminderNotification(context: Context, instance: HabitInstance): Notification {
        // CLEAR_TOP + SINGLE_TOP so tapping resumes an existing MainActivity instead of
        // stacking a duplicate one, matching ReadBook's precedent for notification-tap intents.
        val activityIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, instance.id.toInt(), activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channelId(instance))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(instance.notificationTitle)
            .setContentText(instance.notificationBody)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }

    fun timerChannelId(habitInstanceId: Long): String = "habit_${habitInstanceId}_timer"

    // Offset from notificationId(instance) (which uses the plain instance id) so the ongoing
    // timer notification and the hourly reminder notification never collide on the same id.
    fun timerNotificationId(habitInstanceId: Long): Int = (habitInstanceId * 1000).toInt()

    fun createTimerChannel(context: Context, habitInstanceId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(timerChannelId(habitInstanceId), "Timer", NotificationManager.IMPORTANCE_LOW)
                .apply { setSound(null, null) }
        )
    }

    private fun timerContentIntent(context: Context, habitInstanceId: Long): PendingIntent {
        val activityIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, timerNotificationId(habitInstanceId), activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // startForeground() must be called synchronously, before the instance can be loaded from
    // Room (a suspend call) — this placeholder needs only the id, corrected via
    // buildTimerNotification() within milliseconds once the instance is known (mirrors
    // ReadBook's ReadingTimerService "0 remaining" placeholder pattern).
    fun buildTimerPlaceholderNotification(context: Context, habitInstanceId: Long): Notification =
        NotificationCompat.Builder(context, timerChannelId(habitInstanceId))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentText("Timer running…")
            .setOngoing(true)
            .setContentIntent(timerContentIntent(context, habitInstanceId))
            .build()

    fun buildTimerNotification(context: Context, instance: HabitInstance, remainingSeconds: Int): Notification {
        val minutes = remainingSeconds / 60
        return NotificationCompat.Builder(context, timerChannelId(instance.id))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(instance.name)
            .setContentText("$minutes min left")
            .setOngoing(true)
            .setContentIntent(timerContentIntent(context, instance.id))
            .build()
    }
}
```

- [ ] **Step 2: Verify the module still compiles**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (no behavior change to existing call sites; new functions are additive)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/notifications/HabitNotifications.kt
git commit -m "Add per-instance timer notification channel and builders"
```

---

### Task 7: `TimerService` — foreground service driving the running session

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/service/TimerService.kt`
- Test: `app/src/test/java/com/ziv/reminders/service/TimerServiceTest.kt`

**Interfaces:**
- Consumes: `TimerHabitRepository` (Task 3), `HabitNotifications` timer builders (Task 6),
  `HabitInstanceDao`, `Clock`.
- Produces: `class TimerService : Service()` with `ACTION_START`/`ACTION_STOP`/
  `EXTRA_HABIT_INSTANCE_ID` — consumed by `DashboardViewModel.onToggleTimer` (Task 9).

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ziv/reminders/service/TimerServiceTest.kt`:
```kotlin
package com.ziv.reminders.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.Clock
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.notifications.HabitNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class FakeClock(var millis: Long = 0L) : Clock {
    override fun nowMillis(): Long = millis
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TimerServiceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instance = HabitInstance(
        id = 2L, kind = "TIMER", name = "Reading", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null, timerTargetSeconds = 900,
    )

    // See CounterHabitRepositoryTest/DashboardViewModelTest precedent — the DB must be built
    // inside the enclosing runTest block, pinned to that block's own testScheduler.
    private fun TestScope.buildTestDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()

    private fun setUpService(db: AppDatabase, clock: Clock, scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        Robolectric.buildService(TimerService::class.java).create().also { controller ->
            val service = controller.get()
            service.habitInstanceDao = db.habitInstanceDao()
            service.timerHabitRepository = TimerHabitRepository(db.timerDailyProgressDao(), clock)
            service.clock = clock
            service.scope = CoroutineScope(StandardTestDispatcher(scheduler))
            service.today = { java.time.LocalDate.of(2026, 7, 15) }
        }

    private fun startIntent() = Intent(TimerService.ACTION_START).putExtra(TimerService.EXTRA_HABIT_INSTANCE_ID, instance.id)
    private fun stopIntent() = Intent(TimerService.ACTION_STOP).putExtra(TimerService.EXTRA_HABIT_INSTANCE_ID, instance.id)

    @Test
    fun actionStart_startsForeground_andStartsARepositorySession() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val db = buildTestDb()
        db.habitInstanceDao().insertIfAbsent(instance)
        val controller = setUpService(db, clock, testScheduler)
        val service = controller.get()

        controller.withIntent(startIntent()).startCommand(0, 1)
        testScheduler.runCurrent()

        val shadowService = shadowOf(service)
        assertTrue(!shadowService.isForegroundStopped())
        val row = db.timerDailyProgressDao().getByDate(2L, "2026-07-15")
        assertNotNull(row)
        assertEquals(1_000_000L, row.activeSessionStartedAt)

        controller.withIntent(stopIntent()).startCommand(0, 2)
        testScheduler.runCurrent()
        db.close()
    }

    @Test
    fun actionStart_updatesNotificationWithTheInstancesNameAndRemainingTime() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val db = buildTestDb()
        db.habitInstanceDao().insertIfAbsent(instance)
        val controller = setUpService(db, clock, testScheduler)

        controller.withIntent(startIntent()).startCommand(0, 1)
        testScheduler.runCurrent()

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(manager).getNotification(HabitNotifications.timerNotificationId(2L))
        assertNotNull(notification)
        assertEquals(HabitNotifications.timerChannelId(2L), notification.channelId)
        assertEquals("15 min left", notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())

        controller.withIntent(stopIntent()).startCommand(0, 2)
        testScheduler.runCurrent()
        db.close()
    }

    @Test
    fun actionStop_stopsForeground_andStopsTheRunningSession() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val db = buildTestDb()
        db.habitInstanceDao().insertIfAbsent(instance)
        val controller = setUpService(db, clock, testScheduler)
        val service = controller.get()

        controller.withIntent(startIntent()).startCommand(0, 1)
        testScheduler.runCurrent()
        clock.millis += 60_000L

        controller.withIntent(stopIntent()).startCommand(0, 2)
        testScheduler.runCurrent()

        val shadowService = shadowOf(service)
        assertTrue(shadowService.isForegroundStopped())
        assertTrue(shadowService.isStoppedBySelf())
        val row = db.timerDailyProgressDao().getByDate(2L, "2026-07-15")
        assertEquals(null, row?.activeSessionStartedAt)
        assertEquals(840, row?.remainingSeconds)

        db.close()
    }

    @Test
    fun runningSession_autoCompletesWhenCountdownNaturallyElapses_withoutAnExplicitStop() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val db = buildTestDb()
        db.habitInstanceDao().insertIfAbsent(instance)
        val controller = setUpService(db, clock, testScheduler)
        val service = controller.get()

        controller.withIntent(startIntent()).startCommand(0, 1)
        testScheduler.runCurrent()

        val elapsedMs = 901 * 1000L
        clock.millis += elapsedMs
        testScheduler.advanceTimeBy(elapsedMs)
        testScheduler.advanceUntilIdle()

        val row = db.timerDailyProgressDao().getByDate(2L, "2026-07-15")
        assertEquals(true, row?.completed)
        assertEquals(null, row?.activeSessionStartedAt)
        val shadowService = shadowOf(service)
        assertTrue(shadowService.isStoppedBySelf())
        assertTrue(shadowService.isForegroundStopped())

        db.close()
    }

    @Test
    fun actionStart_unknownHabitInstanceId_doesNotCrash_andStopsSelf() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val db = buildTestDb()
        // Deliberately not inserted — habitInstanceDao().getById(99L) returns null.
        val controller = setUpService(db, clock, testScheduler)
        val service = controller.get()

        controller.withIntent(
            Intent(TimerService.ACTION_START).putExtra(TimerService.EXTRA_HABIT_INSTANCE_ID, 99L)
        ).startCommand(0, 1)
        testScheduler.runCurrent()

        val shadowService = shadowOf(service)
        assertTrue(shadowService.isStoppedBySelf())

        db.close()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.service.TimerServiceTest"`
Expected: FAIL — `TimerService` doesn't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/service/TimerService.kt`:
```kotlin
package com.ziv.reminders.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.ziv.reminders.RemindersApp
import com.ziv.reminders.data.Clock
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitInstanceDao
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.notifications.HabitNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Foreground service owning the currently-running timer session. Keyed by habitInstanceId (the
 * same generalization HabitScheduler/HabitReminderReceiver already use) so a future second Timer
 * instance needs zero new classes — but state (autoCompleteJob) is a single field, not a map:
 * this app is single-user with one realistic session at a time, matching ReadBook's own
 * ReadingTimerService, which this mirrors almost exactly.
 */
class TimerService : Service() {

    internal lateinit var habitInstanceDao: HabitInstanceDao
    internal lateinit var timerHabitRepository: TimerHabitRepository
    internal lateinit var scope: CoroutineScope
    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var clock: Clock = SystemClock

    private var autoCompleteJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as RemindersApp).container
        habitInstanceDao = container.habitInstanceDao
        timerHabitRepository = container.timerHabitRepository
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val habitInstanceId = intent?.getLongExtra(EXTRA_HABIT_INSTANCE_ID, -1L) ?: -1L
        if (habitInstanceId == -1L) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_START -> handleStart(habitInstanceId)
            ACTION_STOP -> handleStop(habitInstanceId)
        }
        return START_NOT_STICKY
    }

    private fun handleStart(habitInstanceId: Long) {
        HabitNotifications.createTimerChannel(this, habitInstanceId)
        // Placeholder — startForeground() must be called synchronously, before the suspending
        // instance lookup below. Corrected via updateNotification() within milliseconds, once
        // the real instance and remaining time are known (mirrors ReadBook's proven pattern).
        startForeground(
            HabitNotifications.timerNotificationId(habitInstanceId),
            HabitNotifications.buildTimerPlaceholderNotification(this, habitInstanceId),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        autoCompleteJob?.cancel()
        autoCompleteJob = scope.launch {
            val instance = habitInstanceDao.getById(habitInstanceId) ?: return@launch finish()
            val row = timerHabitRepository.start(instance, today())
            val startedAt = row.activeSessionStartedAt ?: return@launch finish() // already completed
            val targetRemaining = row.remainingSeconds
            // Recompute remaining from the wall clock every tick rather than decrementing by the
            // nominal step — delay() can resume much later than requested when backgrounded
            // (Doze/OEM throttling), and a cumulative decrement would understate elapsed time.
            while (true) {
                val elapsedSeconds = ((clock.nowMillis() - startedAt) / 1000L).toInt()
                val remaining = (targetRemaining - elapsedSeconds).coerceAtLeast(0)
                if (remaining <= 0) break
                updateNotification(instance, remaining)
                delay(minOf(NOTIFICATION_UPDATE_INTERVAL_SECONDS, remaining.toLong()) * 1000L)
            }
            timerHabitRepository.stop(instance, today())
            finish()
        }
    }

    private fun updateNotification(instance: HabitInstance, remainingSeconds: Int) {
        getSystemService(NotificationManager::class.java).notify(
            HabitNotifications.timerNotificationId(instance.id),
            HabitNotifications.buildTimerNotification(this, instance, remainingSeconds),
        )
    }

    private fun handleStop(habitInstanceId: Long) {
        autoCompleteJob?.cancel()
        scope.launch {
            val instance = habitInstanceDao.getById(habitInstanceId)
            if (instance != null) timerHabitRepository.stop(instance, today())
            finish()
        }
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.ziv.reminders.action.START_TIMER"
        const val ACTION_STOP = "com.ziv.reminders.action.STOP_TIMER"
        const val EXTRA_HABIT_INSTANCE_ID = "com.ziv.reminders.extra.HABIT_INSTANCE_ID"
        private const val NOTIFICATION_UPDATE_INTERVAL_SECONDS = 30L
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.service.TimerServiceTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/service/TimerService.kt app/src/test/java/com/ziv/reminders/service/TimerServiceTest.kt
git commit -m "Add TimerService foreground service"
```

---

### Task 8: Wiring — manifest, seeding, startup self-heal

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt`
- Modify: `app/src/main/java/com/ziv/reminders/RemindersApp.kt`

**Interfaces:**
- Produces: `READING_HABIT_INSTANCE_ID = 2L`; `ensureHabitsSeeded` now also seeds the Reading
  Timer instance. `RemindersApp.onCreate()` now also reconciles crashed timer sessions at startup.

- [ ] **Step 1: Update the manifest**

Add to `app/src/main/AndroidManifest.xml`, alongside the existing `<uses-permission>` elements:
```xml
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

Add inside `<application>`, alongside the existing `<receiver>` elements:
```xml
        <service
            android:name=".service.TimerService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Ongoing personal habit timer started by the user" />
        </service>
```

- [ ] **Step 2: Seed the Reading habit instance**

Full `app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt`:
```kotlin
package com.ziv.reminders.data

const val EXERCISE_HABIT_INSTANCE_ID = 1L
const val READING_HABIT_INSTANCE_ID = 2L

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
}
```

- [ ] **Step 3: Reconcile crashed timer sessions at startup**

Full `app/src/main/java/com/ziv/reminders/RemindersApp.kt`:
```kotlin
package com.ziv.reminders

import android.app.Application
import com.ziv.reminders.data.AppContainer
import com.ziv.reminders.data.ensureHabitsSeeded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class RemindersApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Self-heal on every app open: seeds the known habit instances on first launch, closes
        // out any Timer session left dangling by a process kill, then ensures today's reminders
        // and the rollover chain are scheduled even if the midnight/boot jobs never got to run.
        appScope.launch {
            try {
                ensureHabitsSeeded(container.habitInstanceDao)
                container.timerHabitRepository.reconcileCrashedSessions()
                val today = LocalDate.now()
                for (instance in container.habitInstanceDao.getAll()) {
                    container.habitScheduler.scheduleRemindersForToday(today, instance)
                }
                container.habitScheduler.scheduleRollover(from = today)
            } catch (e: Exception) {
                // Never let a startup self-heal failure crash the app — same resilience as
                // BootReceiver's structurally identical self-heal logic.
            }
        }
    }
}
```

- [ ] **Step 4: Run the full suite and build**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (all tests green)

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt app/src/main/java/com/ziv/reminders/RemindersApp.kt
git commit -m "Seed Reading timer instance, register TimerService, reconcile crashed sessions"
```

---

### Task 9: Dashboard Timer interactivity — toggle and live countdown

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `TimerService` (Task 7).
- Produces: `DashboardViewModel.onToggleTimer(instanceId: Long, context: Context)` — starts or
  stops `TimerService` for that instance, then optimistically flips the row's `isRunning` state
  locally (the Service's own repository write happens asynchronously in its own process — an
  immediate `refresh()` right after `startService()` would race it; the next `ON_RESUME`
  `refresh()` call reconciles with the real DB state, same safety net every other row already
  relies on). `DashboardScreen`'s Timer row becomes tappable and ticks a local 1Hz countdown via
  `LaunchedEffect` while running — the same mechanism ReadBook's actual `HomeScreen` uses.

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt` (new test,
appended to the existing file — needs a `Context` and `Robolectric.buildService`-free approach
since `onToggleTimer` calls `context.startService`, which Robolectric records without actually
running the service):
```kotlin
    @Test
    fun onToggleTimer_notRunning_startsTheServiceAndOptimisticallyFlipsIsRunning() = runTest {
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

        viewModel.onToggleTimer(2L, context)

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.TimerStatus
        assertTrue(status.isRunning)
        val startedService = org.robolectric.Shadows.shadowOf(context as android.app.Application)
            .nextStartedService
        assertEquals(com.ziv.reminders.service.TimerService.ACTION_START, startedService?.action)
        assertEquals(2L, startedService?.getLongExtra(com.ziv.reminders.service.TimerService.EXTRA_HABIT_INSTANCE_ID, -1L))

        db.close()
    }
```

Add the missing `import kotlin.test.assertTrue` if not already present (it already is, from the
existing tests in this file).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `DashboardViewModel` has no `onToggleTimer` method yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`:
```kotlin
package com.ziv.reminders.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.isEnabledDay
import com.ziv.reminders.service.TimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class DashboardViewModel(private val dataSource: DashboardDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val instances = dataSource.habitInstanceDao.getAll()
                .filter { isEnabledDay(today, it.enabledDaysMask) }
            val rows = instances.map { instance ->
                val status = dataSource.habitEngine.todayStatus(instance, today)
                val streak = dataSource.habitEngine.currentStreak(instance, today)
                HabitRowUiState(instance.id, instance.name, status, streak)
            }
            _uiState.value = DashboardUiState(habits = rows, isLoaded = true)
        }
    }

    fun onIncrement(instanceId: Long) {
        viewModelScope.launch {
            val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return@launch
            dataSource.counterHabitRepository.increment(instance, LocalDate.now())
            refresh()
        }
    }

    /** Starts/stops TimerService (the single source of truth for the DB write) then
     * optimistically flips the row locally — see this task's Interfaces note for why an
     * immediate refresh() would race the service's own async write instead. */
    fun onToggleTimer(instanceId: Long, context: Context) {
        val row = _uiState.value.habits.firstOrNull { it.instanceId == instanceId } ?: return
        val status = row.status as? HabitStatus.TimerStatus ?: return
        val action = if (status.isRunning) TimerService.ACTION_STOP else TimerService.ACTION_START
        context.startService(
            Intent(context, TimerService::class.java)
                .setAction(action)
                .putExtra(TimerService.EXTRA_HABIT_INSTANCE_ID, instanceId)
        )
        val updatedStatus = status.copy(isRunning = !status.isRunning)
        _uiState.value = _uiState.value.copy(
            habits = _uiState.value.habits.map { if (it.instanceId == instanceId) it.copy(status = updatedStatus) else it }
        )
    }

    companion object {
        fun factory(dataSource: DashboardDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = DashboardViewModel(dataSource) as T
            }
    }
}
```

`app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt` — replace the `HabitRow`/
`TimerHabitRow` section (the `CounterHabitRow` function and everything above it is unchanged):
```kotlin
@Composable
private fun HabitRow(habit: HabitRowUiState, onIncrement: () -> Unit, onToggleTimer: () -> Unit) {
    when (habit.status) {
        is HabitStatus.CounterStatus -> CounterHabitRow(habit, habit.status, onIncrement)
        is HabitStatus.TimerStatus -> TimerHabitRow(habit, habit.status, onToggleTimer)
    }
}

@Composable
private fun CounterHabitRow(habit: HabitRowUiState, status: HabitStatus.CounterStatus, onIncrement: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onIncrement),
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
private fun TimerHabitRow(habit: HabitRowUiState, status: HabitStatus.TimerStatus, onToggleTimer: () -> Unit) {
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleTimer),
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
```

Also update `DashboardScreen`'s call site to pass the new callback, and add the new imports at
the top of the file:
```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
```
```kotlin
        uiState.habits.forEach { habit ->
            val context = LocalContext.current
            HabitRow(
                habit = habit,
                onIncrement = { viewModel.onIncrement(habit.instanceId) },
                onToggleTimer = { viewModel.onToggleTimer(habit.instanceId, context) },
            )
            Spacer(Modifier.height(8.dp))
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: PASS (5 tests)

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (full suite green)

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt
git commit -m "Wire Timer Start/Stop and live countdown into the dashboard"
```

---

### Task 10: On-device manual verification

Not a code task — no commit. Robolectric can't exercise real notification firing, real Doze
behavior, or a real foreground-service lifecycle across app kill/reboot. Install the debug APK
on-device (`./gradlew :app:installDebug` or Android Studio) and confirm:

- [ ] Fresh install: dashboard shows both Exercise (0/5) and Reading (15:00) rows (today must be
  Sun-Thu for Reading to appear — Plan 1's Exercise row appears every day).
- [ ] Tap the Reading row: label/countdown starts ticking down from 15:00, a persistent silent
  "Reading — N min left" notification appears.
- [ ] Tap again mid-session: countdown stops, notification disappears, row shows the frozen
  remaining time.
- [ ] Tap to resume: countdown continues from where it paused (not reset to 15:00).
- [ ] Let a short manually-configured session run to completion (or verify the 900s auto-complete
  path via logcat timestamps): row shows ✓, notification is removed, no crash.
- [ ] Force-stop the app mid-session, relaunch: dashboard reflects the session as closed out by
  `reconcileCrashedSessions()` (remaining time reduced by real elapsed time, not left "running"
  forever) — confirm via logcat that `reconcileCrashedSessions` ran without throwing.
- [ ] Reboot the device with a completed Reading day: `BootReceiver`'s self-heal doesn't crash on
  the new instance (already covered structurally by Plan 1's generalized receivers, but confirm
  no new exceptions in logcat mentioning `TIMER` or `timer_daily_progress`).
- [ ] An hourly reminder fires only if Reading is not yet completed that day (same suppression
  logic as Exercise, now exercised for a second kind).
- [ ] `adb shell dumpsys battery` / Doze simulation: starting Reading, backgrounding the app, and
  waiting confirms the notification's remaining time is still correct on foreground return
  (wall-clock recomputation, not a naive decrement).

Once all boxes are checked, update `.superpowers/sdd/progress.md` to record this plan's
completion, matching Plan 1's precedent.
