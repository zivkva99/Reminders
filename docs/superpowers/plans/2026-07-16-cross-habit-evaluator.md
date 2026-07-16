# Cross-Habit Evaluator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the one cross-habit rule scoped by the design doc: twice daily (8am and
1pm), if Exercise isn't done, Reading isn't done, and Reading has a live streak (>0),
escalate Reading's reminder notification — stronger wording, higher-priority channel —
without ever racing ahead of or duplicating Reading's existing hourly reminder.

**Architecture:** A new Room table, `evaluator_escalation(habitInstanceId, date,
escalated)`, is the single piece of new persisted state — a per-day flag mirroring the
shape of every other daily-progress table in this app. `CrossHabitEvaluator` is a plain
suspend function class that decides (reads Exercise/Reading status via the existing
`HabitEngine`, writes the flag if escalating) — it has no Android/Context dependency,
so it's Robolectric-free to test. `EscalationWorker`, a `CoroutineWorker`, is the only
new Android-integration surface: it calls the evaluator, posts the escalated notification
if warranted, and unconditionally re-enqueues itself for whichever of {8am, 1pm} comes
next — the same self-chaining shape `RolloverReceiver` already uses on `AlarmManager`,
just on `WorkManager` (a genuinely new dependency for this project). `HabitReminderReceiver`
gains one check: skip its normal reminder if today is already escalated, so the two
mechanisms don't fight over the same notification identity. No new habit kind, no new
`HabitStatus` variant, no dashboard changes — this plan is additive to the existing
three-kind engine, not another kind.

**Tech Stack:** Same as Plans 1-3 — Kotlin 2.3.0, Room 2.7.1 (KSP), JUnit4 + Robolectric
4.16.1, `kotlinx-coroutines-test`. New dependency: `androidx.work:work-runtime-ktx` (and
`androidx.work:work-testing` for tests) — this project's first use of WorkManager.
**Verify the exact current stable WorkManager version at implementation time** (this plan
specifies `2.10.0` as a starting point; bump if that exact version doesn't resolve against
this project's AGP/Kotlin toolchain).

## Global Constraints

(Inherited from the main design doc and Plans 1-3 — still binding.)

- Package / application ID: `com.ziv.reminders`. `minSdk = 35`, `targetSdk = 36`.
- Existing per-habit hourly reminders keep using `AlarmManager.setWindow()` — unchanged.
  Only the new evaluator uses `WorkManager`.
- The evaluator must never race ahead of or duplicate a habit's own alarm-driven
  notification — it only ever *updates* Reading's existing notification identity
  (`HabitNotifications.notificationId(instance)`), never posts a separate one.
- No general rule-authoring UI or config — `CrossHabitEvaluator`'s condition is Kotlin
  code, not data, matching the "3 kinds, not a generic engine" instinct applied here too.
- Every Room schema change ships with a real `Migration` object; never
  `fallbackToDestructiveMigration()`. This plan is the schema's v3→v4 migration.
- TDD for all pure logic and repository/dispatch code; Robolectric (`@Config(sdk = [35])`)
  for anything touching Room/`WorkManager`; every commit after a task leaves
  `./gradlew :app:testDebugUnitTest` green.
- If already escalated today, the 1pm run (or any later run) is a no-op — no re-fire, no
  second sound/heads-up alert for the same day.

---

## File Structure

```
Reminders/
  gradle/libs.versions.toml                                     (Modify — Task 1)
  app/build.gradle.kts                                           (Modify — Task 1)
  app/src/main/java/com/ziv/reminders/
    data/
      EvaluatorEscalation.kt                                      (Create — Task 1)
      EvaluatorEscalationDao.kt                                    (Create — Task 1)
      AppDatabase.kt                                               (Modify — Task 1)
      AppContainer.kt                                              (Modify — Tasks 1, 2, 6)
      CrossHabitEvaluator.kt                                       (Create — Task 2)
    notifications/
      HabitNotifications.kt                                        (Modify — Task 3)
    evaluator/
      EscalationSchedule.kt                                        (Create — Task 4)
      EscalationWorker.kt                                          (Create — Task 5)
    scheduling/
      HabitReminderReceiver.kt                                     (Modify — Task 6)
    RemindersApp.kt                                                (Modify — Task 6)
  app/src/test/java/com/ziv/reminders/
    data/
      EvaluatorEscalationDaoTest.kt                                 (Create — Task 1)
      AppDatabaseMigration3To4Test.kt                               (Create — Task 1)
      CrossHabitEvaluatorTest.kt                                    (Create — Task 2)
    evaluator/
      EscalationScheduleTest.kt                                     (Create — Task 4)
      EscalationWorkerTest.kt                                       (Create — Task 5)
    scheduling/
      HabitReminderReceiverTest.kt                                  (Modify — Task 6)
```

---

### Task 1: Room schema v4 — `EvaluatorEscalation`, migration, WorkManager dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/ziv/reminders/data/EvaluatorEscalation.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/EvaluatorEscalationDao.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/EvaluatorEscalationDaoTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration3To4Test.kt`

**Interfaces:**
- Produces: `data class EvaluatorEscalation(habitInstanceId: Long, date: String, escalated:
  Boolean)`; `interface EvaluatorEscalationDao { suspend fun getByDate(habitInstanceId:
  Long, date: String): EvaluatorEscalation?; suspend fun upsert(escalation:
  EvaluatorEscalation) }`; `AppDatabase.MIGRATION_3_4`. Consumed by: `CrossHabitEvaluator`
  (Task 2), `HabitReminderReceiver` (Task 6).

- [ ] **Step 1: Add the WorkManager dependency**

`gradle/libs.versions.toml` — add to `[versions]`:
```toml
work = "2.10.0"
```

Add to `[libraries]`:
```toml
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
```

`app/build.gradle.kts` — add to `dependencies`:
```kotlin
    implementation(libs.work.runtime.ktx)
    testImplementation(libs.work.testing)
```

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If the `2.10.0` version doesn't resolve (dependency not
found), check the current stable `androidx.work:work-runtime-ktx` release and use that
instead — update both the version string and this note is satisfied either way.

- [ ] **Step 2: Write the failing DAO test**

`app/src/test/java/com/ziv/reminders/data/EvaluatorEscalationDaoTest.kt`:
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
class EvaluatorEscalationDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByDate_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.evaluatorEscalationDao().getByDate(2L, "2026-07-16"))
        db.close()
    }

    @Test
    fun upsert_thenGetByDate_returnsTheRow() = runTest {
        val db = newDb()
        val escalation = EvaluatorEscalation(habitInstanceId = 2L, date = "2026-07-16", escalated = true)
        db.evaluatorEscalationDao().upsert(escalation)

        assertEquals(escalation, db.evaluatorEscalationDao().getByDate(2L, "2026-07-16"))
        db.close()
    }

    @Test
    fun upsert_sameKey_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.evaluatorEscalationDao().upsert(EvaluatorEscalation(2L, "2026-07-16", escalated = false))
        db.evaluatorEscalationDao().upsert(EvaluatorEscalation(2L, "2026-07-16", escalated = true))

        assertEquals(true, db.evaluatorEscalationDao().getByDate(2L, "2026-07-16")?.escalated)
        db.close()
    }

    @Test
    fun getByDate_differentInstanceOrDate_returnsNull() = runTest {
        val db = newDb()
        db.evaluatorEscalationDao().upsert(EvaluatorEscalation(2L, "2026-07-16", escalated = true))

        assertNull(db.evaluatorEscalationDao().getByDate(3L, "2026-07-16"))
        assertNull(db.evaluatorEscalationDao().getByDate(2L, "2026-07-17"))
        db.close()
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.EvaluatorEscalationDaoTest"`
Expected: FAIL — `EvaluatorEscalation`, `EvaluatorEscalationDao`, and
`AppDatabase.evaluatorEscalationDao()` don't exist yet (compile error).

- [ ] **Step 4: Write the schema implementation**

`app/src/main/java/com/ziv/reminders/data/EvaluatorEscalation.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity

/**
 * A per-day flag: was this habit instance's reminder escalated by CrossHabitEvaluator
 * today? Mirrors the shape of the other daily-progress tables. HabitReminderReceiver
 * checks this before posting its normal reminder — an already-escalated notification for
 * today must not be silently downgraded back to plain wording an hour later.
 */
@Entity(tableName = "evaluator_escalation", primaryKeys = ["habitInstanceId", "date"])
data class EvaluatorEscalation(
    val habitInstanceId: Long,
    val date: String,
    val escalated: Boolean,
)
```

`app/src/main/java/com/ziv/reminders/data/EvaluatorEscalationDao.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface EvaluatorEscalationDao {
    @Query("SELECT * FROM evaluator_escalation WHERE habitInstanceId = :habitInstanceId AND date = :date")
    suspend fun getByDate(habitInstanceId: Long, date: String): EvaluatorEscalation?

    @Upsert
    suspend fun upsert(escalation: EvaluatorEscalation)
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
        EvaluatorEscalation::class,
    ],
    version = 4,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitInstanceDao(): HabitInstanceDao
    abstract fun counterDailyProgressDao(): CounterDailyProgressDao
    abstract fun timerDailyProgressDao(): TimerDailyProgressDao
    abstract fun scheduleCursorProgressDao(): ScheduleCursorProgressDao
    abstract fun scheduleCursorDailyProgressDao(): ScheduleCursorDailyProgressDao
    abstract fun evaluatorEscalationDao(): EvaluatorEscalationDao

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
    }
}
```

`app/src/main/java/com/ziv/reminders/data/AppContainer.kt` (only the migration wiring and
new DAO getter change here — `crossHabitEvaluator` wiring comes in Task 2):
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
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
    }

    override val habitInstanceDao get() = db.habitInstanceDao()
    val counterDailyProgressDao get() = db.counterDailyProgressDao()
    val timerDailyProgressDao get() = db.timerDailyProgressDao()
    val scheduleCursorProgressDao get() = db.scheduleCursorProgressDao()
    val scheduleCursorDailyProgressDao get() = db.scheduleCursorDailyProgressDao()
    val evaluatorEscalationDao get() = db.evaluatorEscalationDao()
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

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.EvaluatorEscalationDaoTest"`
Expected: PASS (4 tests)

- [ ] **Step 6: Write the failing migration test**

`app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration3To4Test.kt`:
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
class AppDatabaseMigration3To4Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate3To4_preservesExistingRows_andAddsEvaluatorEscalationTable() {
        helper.createDatabase(TEST_DB_NAME, 3).apply {
            execSQL(
                "INSERT INTO habit_instance (id, kind, name, enabledDaysMask, notificationTitle, notificationBody, counterGoal, timerTargetSeconds) " +
                    "VALUES (2, 'TIMER', 'Reading', 31, 't', 'b', NULL, 900)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 4, true, AppDatabase.MIGRATION_3_4)

        migrated.query("SELECT name FROM habit_instance WHERE id = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Reading", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM evaluator_escalation").use { cursor ->
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

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.AppDatabaseMigration3To4Test"`
Expected: PASS (1 test)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests still green)

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/ziv/reminders/data/EvaluatorEscalation.kt app/src/main/java/com/ziv/reminders/data/EvaluatorEscalationDao.kt app/src/main/java/com/ziv/reminders/data/AppDatabase.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/test/java/com/ziv/reminders/data/EvaluatorEscalationDaoTest.kt app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration3To4Test.kt
git commit -m "Add Room schema v4 for cross-habit evaluator + WorkManager dependency"
```

---

### Task 2: `CrossHabitEvaluator`

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/data/CrossHabitEvaluator.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/CrossHabitEvaluatorTest.kt`

**Interfaces:**
- Consumes: `HabitInstanceDao`, `HabitEngine`, `EvaluatorEscalationDao` (existing +
  Task 1); `EXERCISE_HABIT_INSTANCE_ID`, `READING_HABIT_INSTANCE_ID` (existing constants
  in `HabitSeeding.kt`).
- Produces: `class CrossHabitEvaluator(habitInstanceDao, habitEngine, escalationDao) {
  suspend fun evaluate(today: LocalDate): Boolean }` — returns `true` only when this call
  just escalated Reading (writes the flag as a side effect); `false` when the condition
  wasn't met OR today was already escalated by an earlier run (no re-evaluation, no
  re-write in that case). Consumed by `EscalationWorker` (Task 5), which is responsible
  for the actual notification posting (this class has no `Context`/Android dependency,
  so it's plain-JUnit testable).

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ziv/reminders/data/CrossHabitEvaluatorTest.kt`:
```kotlin
package com.ziv.reminders.data

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

private class FakeEvaluatorEscalationDao : EvaluatorEscalationDao {
    val rows = mutableMapOf<Pair<Long, String>, EvaluatorEscalation>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(escalation: EvaluatorEscalation) { rows[escalation.habitInstanceId to escalation.date] = escalation }
}

class CrossHabitEvaluatorTest {

    private val exercise = HabitInstance(
        id = EXERCISE_HABIT_INSTANCE_ID, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )
    private val reading = HabitInstance(
        id = READING_HABIT_INSTANCE_ID, kind = "TIMER", name = "Reading", enabledDaysMask = 0b0011111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null, timerTargetSeconds = 900,
    )
    private val today = LocalDate.of(2026, 7, 16) // a Thursday, enabled for Reading

    private fun newEvaluator(
        instanceDao: FakeHabitInstanceDao,
        counterDao: FakeCounterDailyProgressDao = FakeCounterDailyProgressDao(),
        timerDao: FakeTimerDailyProgressDao = FakeTimerDailyProgressDao(),
        escalationDao: FakeEvaluatorEscalationDao = FakeEvaluatorEscalationDao(),
    ): CrossHabitEvaluator {
        val engine = HabitEngine(
            CounterHabitRepository(counterDao),
            TimerHabitRepository(timerDao, SystemClock),
            ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), emptyList()),
        )
        return CrossHabitEvaluator(instanceDao, engine, escalationDao)
    }

    private fun instanceDaoWithBoth(): FakeHabitInstanceDao {
        val dao = FakeHabitInstanceDao()
        dao.rows[exercise.id] = exercise
        dao.rows[reading.id] = reading
        return dao
    }

    @Test
    fun evaluate_allConditionsMet_escalatesAndWritesFlag() = runTest {
        val timerDao = FakeTimerDailyProgressDao()
        timerDao.rows[reading.id to "2026-07-15"] = TimerDailyProgress(reading.id, "2026-07-15", 900, 0, true, 1L, null) // yesterday completed -> streak 1
        val escalationDao = FakeEvaluatorEscalationDao()
        val evaluator = newEvaluator(instanceDaoWithBoth(), timerDao = timerDao, escalationDao = escalationDao)

        val result = evaluator.evaluate(today)

        assertTrue(result)
        assertEquals(true, escalationDao.rows[reading.id to today.toString()]?.escalated)
    }

    @Test
    fun evaluate_exerciseCompleted_doesNotEscalate() = runTest {
        val counterDao = FakeCounterDailyProgressDao()
        counterDao.rows[exercise.id to today.toString()] = CounterDailyProgress(exercise.id, today.toString(), 5, true)
        val timerDao = FakeTimerDailyProgressDao()
        timerDao.rows[reading.id to "2026-07-15"] = TimerDailyProgress(reading.id, "2026-07-15", 900, 0, true, 1L, null)
        val evaluator = newEvaluator(instanceDaoWithBoth(), counterDao = counterDao, timerDao = timerDao)

        assertFalse(evaluator.evaluate(today))
    }

    @Test
    fun evaluate_readingCompleted_doesNotEscalate() = runTest {
        val timerDao = FakeTimerDailyProgressDao()
        timerDao.rows[reading.id to "2026-07-15"] = TimerDailyProgress(reading.id, "2026-07-15", 900, 0, true, 1L, null)
        timerDao.rows[reading.id to today.toString()] = TimerDailyProgress(reading.id, today.toString(), 900, 0, true, System.currentTimeMillis(), null)
        val evaluator = newEvaluator(instanceDaoWithBoth(), timerDao = timerDao)

        assertFalse(evaluator.evaluate(today))
    }

    @Test
    fun evaluate_readingStreakZero_doesNotEscalate() = runTest {
        // No completed days at all for Reading -> streak is 0.
        val evaluator = newEvaluator(instanceDaoWithBoth())

        assertFalse(evaluator.evaluate(today))
    }

    @Test
    fun evaluate_alreadyEscalatedToday_isANoOp_returnsFalse() = runTest {
        val timerDao = FakeTimerDailyProgressDao()
        timerDao.rows[reading.id to "2026-07-15"] = TimerDailyProgress(reading.id, "2026-07-15", 900, 0, true, 1L, null)
        val escalationDao = FakeEvaluatorEscalationDao()
        escalationDao.rows[reading.id to today.toString()] = EvaluatorEscalation(reading.id, today.toString(), escalated = true)
        val evaluator = newEvaluator(instanceDaoWithBoth(), timerDao = timerDao, escalationDao = escalationDao)

        assertFalse(evaluator.evaluate(today))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.CrossHabitEvaluatorTest"`
Expected: FAIL — `CrossHabitEvaluator` doesn't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/data/CrossHabitEvaluator.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate

/**
 * The one cross-habit rule this app runs: if Exercise isn't done, Reading isn't done,
 * and Reading has a live streak, escalate Reading's reminder. A single hardcoded
 * condition, not a generic rule engine — see the evaluator design doc's Global
 * Constraints for why. Has no Context/Android dependency — EscalationWorker (the only
 * caller) owns the actual notification posting once this returns true.
 */
class CrossHabitEvaluator(
    private val habitInstanceDao: HabitInstanceDao,
    private val habitEngine: HabitEngine,
    private val escalationDao: EvaluatorEscalationDao,
) {

    /** Returns true only if this call just escalated Reading (and wrote the flag).
     * Returns false if the condition wasn't met, or if today was already escalated by
     * an earlier run this day — in the latter case, nothing is re-evaluated or re-written. */
    suspend fun evaluate(today: LocalDate): Boolean {
        val key = today.toString()
        if (escalationDao.getByDate(READING_HABIT_INSTANCE_ID, key)?.escalated == true) return false

        val exercise = habitInstanceDao.getById(EXERCISE_HABIT_INSTANCE_ID) ?: return false
        val reading = habitInstanceDao.getById(READING_HABIT_INSTANCE_ID) ?: return false

        val exerciseStatus = habitEngine.todayStatus(exercise, today) as? HabitStatus.CounterStatus ?: return false
        val readingStatus = habitEngine.todayStatus(reading, today) as? HabitStatus.TimerStatus ?: return false
        val readingStreak = habitEngine.currentStreak(reading, today)

        val shouldEscalate = !exerciseStatus.completed && !readingStatus.completed && readingStreak > 0
        if (shouldEscalate) {
            escalationDao.upsert(EvaluatorEscalation(READING_HABIT_INSTANCE_ID, key, escalated = true))
        }
        return shouldEscalate
    }
}
```

`app/src/main/java/com/ziv/reminders/data/AppContainer.kt` — add one property (rest of
the file unchanged from Task 1's state):
```kotlin
    val crossHabitEvaluator: CrossHabitEvaluator by lazy { CrossHabitEvaluator(habitInstanceDao, habitEngine, evaluatorEscalationDao) }
```
(placed after the `habitEngine` lazy val, before `habitScheduler`)

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.CrossHabitEvaluatorTest"`
Expected: PASS (5 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/CrossHabitEvaluator.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/test/java/com/ziv/reminders/data/CrossHabitEvaluatorTest.kt
git commit -m "Add CrossHabitEvaluator"
```

---

### Task 3: `HabitNotifications` — escalated channel and notification builder

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/notifications/HabitNotifications.kt`

**Interfaces:**
- Produces: `HabitNotifications.escalatedChannelId(habitInstanceId: Long): String;
  createEscalatedChannel(context, habitInstanceId); buildEscalatedReminderNotification(context,
  instance: HabitInstance): Notification`. Consumed by `EscalationWorker` (Task 5). No
  dedicated test file — exercised indirectly through `EscalationWorkerTest` (Task 5),
  matching how the existing reminder/timer notification builders are only tested via
  their respective receivers/services.

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
 * importance, silent per-instance channel. The cross-habit evaluator's escalated notification
 * gets a THIRD per-instance channel, high-importance — Android ties notification importance to
 * the channel (not a per-notification field) on this app's minSdk, so raising priority for one
 * firing without permanently changing the normal reminder channel requires a separate channel. */
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

    fun escalatedChannelId(habitInstanceId: Long): String = "habit_${habitInstanceId}_escalated"

    fun createEscalatedChannel(context: Context, habitInstanceId: Long) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(escalatedChannelId(habitInstanceId), "Escalated reminders", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /** Posted under the SAME notificationId(instance) as the normal reminder — this replaces
     * that notification rather than duplicating it, satisfying the "never race ahead of or
     * duplicate a habit's own alarm" rule even though it uses a different (higher-importance)
     * channel. */
    fun buildEscalatedReminderNotification(context: Context, instance: HabitInstance): Notification {
        val activityIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, notificationId(instance), activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, escalatedChannelId(instance.id))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(instance.notificationTitle)
            .setContentText("Don't lose your reading streak — and Exercise is still waiting too!")
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }
}
```

- [ ] **Step 2: Verify the module still compiles**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (no behavior change to existing call sites; new functions are additive)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/notifications/HabitNotifications.kt
git commit -m "Add escalated per-instance notification channel and builder"
```

---

### Task 4: `EscalationSchedule` — pure next-check-time calculation

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/evaluator/EscalationSchedule.kt`
- Test: `app/src/test/java/com/ziv/reminders/evaluator/EscalationScheduleTest.kt`

**Interfaces:**
- Produces: `fun millisUntilNextCheck(now: ZonedDateTime, checkHours: List<Int> =
  listOf(8, 13)): Long` — pure function, no Android dependency, testable without
  Robolectric. Consumed by `EscalationWorker` (Task 5) to compute its self-reschedule delay.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ziv/reminders/evaluator/EscalationScheduleTest.kt`:
```kotlin
package com.ziv.reminders.evaluator

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class EscalationScheduleTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun millisUntilNextCheck_beforeMorningCheck_returnsDelayToMorning() {
        val now = ZonedDateTime.of(2026, 7, 16, 6, 0, 0, 0, zone) // 6am
        val expected = Duration.ofHours(2).toMillis() // to 8am same day

        assertEquals(expected, millisUntilNextCheck(now))
    }

    @Test
    fun millisUntilNextCheck_betweenMorningAndAfternoon_returnsDelayToAfternoon() {
        val now = ZonedDateTime.of(2026, 7, 16, 10, 0, 0, 0, zone) // 10am
        val expected = Duration.ofHours(3).toMillis() // to 1pm same day

        assertEquals(expected, millisUntilNextCheck(now))
    }

    @Test
    fun millisUntilNextCheck_afterAfternoonCheck_returnsDelayToNextMorningsCheck() {
        val now = ZonedDateTime.of(2026, 7, 16, 15, 0, 0, 0, zone) // 3pm
        val expected = Duration.ofHours(17).toMillis() // to 8am the next day

        assertEquals(expected, millisUntilNextCheck(now))
    }

    @Test
    fun millisUntilNextCheck_exactlyAtACheckHour_rollsForwardToTheNextOne() {
        val now = ZonedDateTime.of(2026, 7, 16, 8, 0, 0, 0, zone) // exactly 8am
        val expected = Duration.ofHours(5).toMillis() // to 1pm same day, not 8am again

        assertEquals(expected, millisUntilNextCheck(now))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.evaluator.EscalationScheduleTest"`
Expected: FAIL — `millisUntilNextCheck` doesn't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/evaluator/EscalationSchedule.kt`:
```kotlin
package com.ziv.reminders.evaluator

import java.time.Duration
import java.time.ZonedDateTime

/**
 * Pure calculation of how long to delay until the next of {8am, 1pm} (or whichever
 * check hours are passed) strictly after [now] — never returns a zero/negative delay,
 * even if [now] lands exactly on a check hour. No Android dependency; EscalationWorker
 * (evaluator/EscalationWorker.kt) uses this to self-reschedule after every run.
 */
fun millisUntilNextCheck(now: ZonedDateTime, checkHours: List<Int> = listOf(8, 13)): Long {
    val today = now.toLocalDate()
    val candidates = checkHours.map { hour -> today.atTime(hour, 0).atZone(now.zone) } +
        checkHours.map { hour -> today.plusDays(1).atTime(hour, 0).atZone(now.zone) }
    val next = candidates.first { it.isAfter(now) }
    return Duration.between(now, next).toMillis()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.evaluator.EscalationScheduleTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/evaluator/EscalationSchedule.kt app/src/test/java/com/ziv/reminders/evaluator/EscalationScheduleTest.kt
git commit -m "Add pure EscalationSchedule next-check-time calculation"
```

---

### Task 5: `EscalationWorker` — the self-rescheduling `WorkManager` job

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/evaluator/EscalationWorker.kt`
- Test: `app/src/test/java/com/ziv/reminders/evaluator/EscalationWorkerTest.kt`

**Interfaces:**
- Consumes: `CrossHabitEvaluator` (Task 2), `HabitNotifications` escalated builders
  (Task 3), `millisUntilNextCheck` (Task 4).
- Produces: `class EscalationWorker(context, params) : CoroutineWorker(context, params)`
  with `companion object { fun ensureScheduled(context: Context); UNIQUE_WORK_NAME }` —
  consumed by `RemindersApp` (Task 6).

This is the project's first use of `WorkManager` and `work-testing` — no existing
precedent to follow for the test setup; follow the code below precisely.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ziv/reminders/evaluator/EscalationWorkerTest.kt`:
```kotlin
package com.ziv.reminders.evaluator

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.CrossHabitEvaluator
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.READING_HABIT_INSTANCE_ID
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TimerDailyProgress
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.notifications.HabitNotifications
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EscalationWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val exercise = HabitInstance(
        id = EXERCISE_HABIT_INSTANCE_ID, kind = "COUNTER", name = "Exercise", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 5,
    )
    private val reading = HabitInstance(
        id = READING_HABIT_INSTANCE_ID, kind = "TIMER", name = "Reading", enabledDaysMask = 0b0011111,
        notificationTitle = "Reminders", notificationBody = "15 minutes of reading today?",
        counterGoal = null, timerTargetSeconds = 900,
    )

    @Before
    fun setUp() {
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    private fun TestScopeDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)

    @Test
    fun doWork_conditionMet_postsEscalatedNotification_andReschedulesNextRun() = runTest {
        val db = TestScopeDb().setQueryCoroutineContext(StandardTestDispatcher(testScheduler)).build()
        db.habitInstanceDao().insertIfAbsent(exercise)
        db.habitInstanceDao().insertIfAbsent(reading)
        db.timerDailyProgressDao().upsert(TimerDailyProgress(reading.id, "2026-07-15", 900, 0, true, 1L, null)) // yesterday completed -> streak 1

        val evaluator = CrossHabitEvaluator(
            db.habitInstanceDao(),
            HabitEngine(
                CounterHabitRepository(db.counterDailyProgressDao()),
                TimerHabitRepository(db.timerDailyProgressDao(), SystemClock),
                ScheduleCursorRepository(db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList()),
            ),
            db.evaluatorEscalationDao(),
        )

        val worker = TestListenableWorkerBuilder<EscalationWorker>(context).build()
        worker.habitInstanceDaoOverride = db.habitInstanceDao()
        worker.crossHabitEvaluatorOverride = evaluator
        worker.today = { LocalDate.of(2026, 7, 16) }

        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.firstOrNull { it.id == HabitNotifications.notificationId(reading) }
        assertNotNull(notification)
        assertEquals(HabitNotifications.escalatedChannelId(reading.id), notification.notification.channelId)

        // Confirm the chain re-enqueued itself.
        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(EscalationWorker.UNIQUE_WORK_NAME).get()
        assertEquals(false, workInfos.isEmpty())

        db.close()
    }

    @Test
    fun doWork_conditionNotMet_postsNothing() = runTest {
        val db = TestScopeDb().setQueryCoroutineContext(StandardTestDispatcher(testScheduler)).build()
        db.habitInstanceDao().insertIfAbsent(exercise)
        db.habitInstanceDao().insertIfAbsent(reading)
        // No completed Reading day at all -> streak 0, condition never met.

        val evaluator = CrossHabitEvaluator(
            db.habitInstanceDao(),
            HabitEngine(
                CounterHabitRepository(db.counterDailyProgressDao()),
                TimerHabitRepository(db.timerDailyProgressDao(), SystemClock),
                ScheduleCursorRepository(db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList()),
            ),
            db.evaluatorEscalationDao(),
        )

        val worker = TestListenableWorkerBuilder<EscalationWorker>(context).build()
        worker.habitInstanceDaoOverride = db.habitInstanceDao()
        worker.crossHabitEvaluatorOverride = evaluator
        worker.today = { LocalDate.of(2026, 7, 16) }

        worker.doWork()

        val manager = context.getSystemService(NotificationManager::class.java)
        assertNull(manager.activeNotifications.firstOrNull { it.id == HabitNotifications.notificationId(reading) })

        db.close()
    }

    @Test
    fun ensureScheduled_enqueuesTheWorkChain() {
        EscalationWorker.ensureScheduled(context)

        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(EscalationWorker.UNIQUE_WORK_NAME).get()
        assertEquals(1, workInfos.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.evaluator.EscalationWorkerTest"`
Expected: FAIL — `EscalationWorker` doesn't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/evaluator/EscalationWorker.kt`:
```kotlin
package com.ziv.reminders.evaluator

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ziv.reminders.RemindersApp
import com.ziv.reminders.data.CrossHabitEvaluator
import com.ziv.reminders.data.HabitInstanceDao
import com.ziv.reminders.data.READING_HABIT_INSTANCE_ID
import com.ziv.reminders.notifications.HabitNotifications
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs CrossHabitEvaluator, posts the escalated notification if it fires, then
 * unconditionally re-enqueues itself for whichever of {8am, 1pm} comes next — the same
 * self-chaining shape RolloverReceiver uses on AlarmManager, just on WorkManager. A
 * failed run must not break the chain, matching BootReceiver/RemindersApp's resilience.
 */
class EscalationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    internal var crossHabitEvaluatorOverride: CrossHabitEvaluator? = null
    internal var habitInstanceDaoOverride: HabitInstanceDao? = null
    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var nowOverride: (() -> ZonedDateTime)? = null

    override suspend fun doWork(): Result {
        // Lazy — same reason as every other receiver/service in this app: Robolectric's test
        // Application isn't a RemindersApp instance, so the cast must only happen if no
        // override was injected.
        val container = (applicationContext as? RemindersApp)?.container
        val evaluator = crossHabitEvaluatorOverride ?: container?.crossHabitEvaluator
        val habitInstanceDao = habitInstanceDaoOverride ?: container?.habitInstanceDao

        try {
            if (evaluator != null && habitInstanceDao != null) {
                val escalated = evaluator.evaluate(today())
                if (escalated) {
                    val reading = habitInstanceDao.getById(READING_HABIT_INSTANCE_ID)
                    if (reading != null) {
                        HabitNotifications.createEscalatedChannel(applicationContext, reading.id)
                        val manager = applicationContext.getSystemService(NotificationManager::class.java)
                        manager.notify(
                            HabitNotifications.notificationId(reading),
                            HabitNotifications.buildEscalatedReminderNotification(applicationContext, reading),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Never let one bad run break the chain — the next line still reschedules.
        } finally {
            scheduleNext(applicationContext, nowOverride?.invoke() ?: ZonedDateTime.now())
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "com.ziv.reminders.evaluator.ESCALATION"

        /** Call from RemindersApp's startup self-heal — idempotent, never resets an
         * already-running chain. */
        fun ensureScheduled(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
            val request = OneTimeWorkRequestBuilder<EscalationWorker>()
                .setInitialDelay(millisUntilNextCheck(now), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        private fun scheduleNext(context: Context, now: ZonedDateTime) {
            val request = OneTimeWorkRequestBuilder<EscalationWorker>()
                .setInitialDelay(millisUntilNextCheck(now), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.evaluator.EscalationWorkerTest"`
Expected: PASS (3 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/evaluator/EscalationWorker.kt app/src/test/java/com/ziv/reminders/evaluator/EscalationWorkerTest.kt
git commit -m "Add self-rescheduling EscalationWorker"
```

---

### Task 6: Wiring — skip escalated reminders, start the evaluator chain

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt`
- Modify: `app/src/main/java/com/ziv/reminders/RemindersApp.kt`
- Modify: `app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt`

**Interfaces:**
- Produces: `HabitReminderReceiver` no longer posts its plain reminder when today is
  already escalated for that instance. `RemindersApp.onCreate()` ensures the
  `EscalationWorker` chain is running, alongside the existing scheduler/reconcile self-heal.

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt` (new
imports and one new test, appended to the existing file):
```kotlin
    @Test
    fun onReceive_todayAlreadyEscalated_postsNothing() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(instance)
        db.evaluatorEscalationDao().upsert(
            com.ziv.reminders.data.EvaluatorEscalation(1L, "2026-07-14", escalated = true)
        )

        val receiver = HabitReminderReceiver()
        receiver.today = { LocalDate.of(2026, 7, 14) }
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.habitEngineOverride = HabitEngine(
            CounterHabitRepository(db.counterDailyProgressDao()),
            com.ziv.reminders.data.TimerHabitRepository(db.timerDailyProgressDao(), com.ziv.reminders.data.SystemClock),
            com.ziv.reminders.data.ScheduleCursorRepository(
                db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList(),
            ),
        )
        receiver.evaluatorEscalationDaoOverride = db.evaluatorEscalationDao()
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        dispatch(receiver, habitInstanceId = 1L)
        testScheduler.advanceUntilIdle()

        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = manager.activeNotifications.firstOrNull { it.id == HabitNotifications.notificationId(instance) }
        assertNull(notification)

        db.close()
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.scheduling.HabitReminderReceiverTest"`
Expected: FAIL — `HabitReminderReceiver` has no `evaluatorEscalationDaoOverride` yet (compile error).

- [ ] **Step 3: Write the implementation**

Full `app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt`:
```kotlin
package com.ziv.reminders.scheduling

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ziv.reminders.RemindersApp
import com.ziv.reminders.data.EvaluatorEscalationDao
import com.ziv.reminders.data.HabitInstanceDao
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.notifications.HabitNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Fired by AlarmManager at each hourly reminder time. A no-op if today is already completed,
 * or if today's reminder was already escalated by the cross-habit evaluator (an escalated
 * notification must not be silently downgraded back to plain wording an hour later). */
class HabitReminderReceiver : BroadcastReceiver() {

    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var habitInstanceDaoOverride: HabitInstanceDao? = null
    internal var habitEngineOverride: HabitEngine? = null
    internal var evaluatorEscalationDaoOverride: EvaluatorEscalationDao? = null
    internal var scopeOverride: CoroutineScope? = null

    override fun onReceive(context: Context, intent: Intent) {
        val habitInstanceId = intent.getLongExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, -1L)
        if (habitInstanceId == -1L) return
        val pendingResult = goAsync()
        // Each override is checked via ?: BEFORE the RemindersApp cast, not a separate eager
        // `val container = (context.applicationContext as RemindersApp)...` line — that would
        // evaluate the cast unconditionally, throwing ClassCastException in every test even
        // when overrides are provided (Robolectric's application context isn't a RemindersApp
        // instance — see robolectric.properties from Task 3). Mirrors ReadBook's NudgeReceiver
        // exactly, which relies on this same short-circuiting for the same reason.
        val dao = habitInstanceDaoOverride
            ?: (context.applicationContext as RemindersApp).container.habitInstanceDao
        val engine = habitEngineOverride
            ?: (context.applicationContext as RemindersApp).container.habitEngine
        val escalationDao = evaluatorEscalationDaoOverride
            ?: (context.applicationContext as RemindersApp).container.evaluatorEscalationDao
        val scope = scopeOverride ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val instance = dao.getById(habitInstanceId) ?: return@launch
                val status = engine.todayStatus(instance, today())
                val completed = when (status) {
                    is HabitStatus.CounterStatus -> status.completed
                    is HabitStatus.TimerStatus -> status.completed
                    is HabitStatus.ScheduleCursorStatus -> status.completed
                }
                val alreadyEscalatedToday = escalationDao.getByDate(habitInstanceId, today().toString())?.escalated == true
                if (!completed && !alreadyEscalatedToday) {
                    HabitNotifications.createChannel(context, instance)
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.notify(HabitNotifications.notificationId(instance), HabitNotifications.buildReminderNotification(context, instance))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

The two existing `receiver.habitEngineOverride = HabitEngine(...)` construction sites in
`HabitReminderReceiverTest.kt` are unaffected (no `HabitEngine` constructor change in this
plan) — only the new test needs the new `evaluatorEscalationDaoOverride` field set.

- [ ] **Step 4: Wire the evaluator chain into startup self-heal**

Full `app/src/main/java/com/ziv/reminders/RemindersApp.kt`:
```kotlin
package com.ziv.reminders

import android.app.Application
import com.ziv.reminders.data.AppContainer
import com.ziv.reminders.data.ensureHabitsSeeded
import com.ziv.reminders.evaluator.EscalationWorker
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
        // out any Timer session left dangling by a process kill, ensures today's reminders and
        // the rollover chain are scheduled even if the midnight/boot jobs never got to run, and
        // ensures the cross-habit evaluator's WorkManager chain is running.
        appScope.launch {
            try {
                ensureHabitsSeeded(container.habitInstanceDao)
                container.timerHabitRepository.reconcileCrashedSessions()
                val today = LocalDate.now()
                for (instance in container.habitInstanceDao.getAll()) {
                    container.habitScheduler.scheduleRemindersForToday(today, instance)
                }
                container.habitScheduler.scheduleRollover(from = today)
                EscalationWorker.ensureScheduled(this@RemindersApp)
            } catch (e: Exception) {
                // Never let a startup self-heal failure crash the app — same resilience as
                // BootReceiver's structurally identical self-heal logic.
            }
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.scheduling.HabitReminderReceiverTest"`
Expected: PASS (3 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt app/src/main/java/com/ziv/reminders/RemindersApp.kt app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt
git commit -m "Wire evaluator escalation skip into HabitReminderReceiver, start the chain at startup"
```

---

### Task 7: On-device manual verification

Not a code task — no commit. Robolectric can't confirm `WorkManager` actually schedules
and survives real app-kill/reboot cycles, and — like the hourly reminder-firing checks in
prior plans — genuinely observing a real 8am/1pm firing isn't forceable without
manipulating the system clock (avoided throughout this project). Install the debug APK
on-device (`./gradlew.bat :app:installDebug`) and confirm:

- [ ] Fresh install/update: no crash. `adb shell dumpsys jobscheduler` (or WorkManager's
  own inspection APIs) shows the `EscalationWorker` unique work enqueued with a delay
  pointing at the next of {8am, 1pm}.
- [ ] Force-stop the app, relaunch: the work chain is still enqueued (idempotent
  `ensureScheduled` via `ExistingWorkPolicy.KEEP` didn't duplicate or reset it).
- [ ] If it's currently possible to contrive the real condition (Exercise undone, Reading
  undone, Reading has a streak) close to a real 8am or 1pm, observe the escalated
  notification firing naturally — stronger wording, audible/heads-up — and confirm the
  next hourly Reading reminder (if any remain that day) does NOT downgrade it.
- [ ] Confirm via logcat that no `WorkManager`/`EscalationWorker` exceptions appear across
  a normal day's usage.

Once all boxes are checked (or the reachable subset, given the timing constraints above),
update `.superpowers/sdd/progress.md` to record this plan's completion, matching Plans
1-3's precedent.
