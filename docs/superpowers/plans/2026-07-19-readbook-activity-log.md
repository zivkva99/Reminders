<!-- /autoplan restore point: /c/Users/zivk/.gstack/projects/Reminders/main-autoplan-restore-20260720-014521.md -->
# Unified Activity Log Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port ReadBook's six remaining features (Reading stats/history screen, per-session log, actionable Start/Snooze notifications, weekly summary, undo/day-edit, lifetime total-days stat) into Reminders, unified into one new cross-habit **Activity** screen showing Exercise, Reading, and Tanakh side by side — plus a genuine cross-habit "combo streak" no single-habit ReadBook feature could ever have had.

**Architecture:** A new `"activity"` `NavHost` destination replaces the standalone `"exerciseStats"` route. `ExerciseCounterScreen`'s existing stats button is repointed there instead of being left as a duplicate entry point. `ExerciseStatsScreen`'s content becomes an embeddable `ExerciseActivitySection` composable, unchanged in behavior. `HeatmapGrid`'s date-alignment/status logic is extracted into a pure, unit-tested function (`buildHeatmapDays`) before the composable itself is shared across all three habit sections — this pure extraction is the actual safety net for refactoring already-shipped code, since no Compose UI test coverage exists for it today. Reading gains a real session-log Room table (`reading_session_log`, schema v5→v6) wired into `TimerHabitRepository` via a nullable, default-`null` constructor parameter — chosen specifically so the 8 existing test files that construct `TimerHabitRepository` with its current 2-arg signature keep compiling unchanged. Tanakh gains an "undo most recent mark-read" repository method (`ScheduleCursorRepository.undoMarkRead`) — meaningful only for the cursor's current position, not arbitrary past days, since Tanakh's cursor is a single global position, not independent per-day state. A new pure `WeeklySummary` object computes a 7-day cross-habit aggregation (per-habit weekly counts + a combo-streak count), written alongside — not through — the existing single-condition `CrossHabitEvaluator`. `HabitReminderReceiver` gains real `intent.action` branching (it has none today) for Reading's Start action (routes through `ContextCompat.startForegroundService` + `TimerService.ACTION_START`, converging with the in-app start path rather than bypassing the foreground service), a Snooze action (schedules a one-shot alarm reusing the existing `ACTION_REMINDER` path), and a new weekly-summary alarm (self-healed the same way as the existing hourly/rollover alarms).

**Tech Stack:** Kotlin 2.3.0, Room 2.7.1 (KSP), Jetpack Compose (existing), Navigation-Compose 2.9.6 (existing, added in the prior Exercise-detail-screen plan), JUnit4 + `kotlin-test`, Robolectric 4.16.1 for DB/migration/receiver/scheduler tests.

## Global Constraints

- Package `com.ziv.reminders`. `minSdk = 35`, `targetSdk = 36`.
- Every Room schema change ships with a real `Migration` object; never `fallbackToDestructiveMigration()`. This plan is the schema's v5→v6 migration (adds `reading_session_log` only — `totalCount` needs no schema change at all, it's computed on-the-fly as `completedDates.size`).
- `AlarmManager.setWindow()` only — never exact alarms — matching `HabitScheduler`'s existing convention. New alarms (snooze, weekly summary) follow the same pattern.
- Manual DI via parallel `AppContainer` interfaces (`DashboardDataSource`, `ExerciseDetailDataSource`, and this plan's new `ActivityDataSource`) — never one bloated interface.
- `HeatmapGrid`'s extraction must not change its existing rendering behavior or the call site in `ExerciseStatsScreen.kt`/its successor — the pure `buildHeatmapDays` function is unit-tested precisely so this is verifiable, not assumed.
- `TimerHabitRepository`'s new `sessionLogDao` constructor parameter is nullable with a default of `null` — **never** make it a required parameter; 8 existing test files (`ExerciseViewModelTest`, `TestAppContainer`, `HabitEngineTest`, `TimerServiceTest`, `EscalationWorkerTest`, `HabitReminderReceiverTest`, `CrossHabitEvaluatorTest`, `TimerHabitRepositoryTest` — verified count during /autoplan Eng review) construct this class with the current 2-arg signature and must keep compiling without modification.
- TDD for all pure logic, repository, DAO, migration, receiver, and scheduler code (Robolectric `@Config(sdk = [35])` for anything touching Room/Android framework classes). Compose UI composables in this project have no existing test precedent (established in the prior Exercise-detail-screen plan) — this plan follows that same precedent: UI composables are implemented directly and verified on-device (Task 8), while every ViewModel/repository/DAO/pure-function layer underneath them is fully unit tested.
- **Corrected during /autoplan CEO review — self-contradiction found and resolved.** This line originally said Exercise's `SubCounterDetailDialog` "stays view-only, unchanged" and that the `TODOS.md` P3 retroactive-edit item "remains separately deferred, not solved here" — but Task 6 Step 0 (added in an earlier CEO-cherry-pick pass, for edit-parity with Reading's delete and Tanakh's undo) fully implements `ExerciseViewModel.adjustSubCounterForDate` with TDD coverage, directly contradicting this constraint. The richer version (Task 6 Step 0) is correct and stays — it closes a real gap (all three habit kinds now support day-edit, not just two) and was already fully implemented with tests, not a stub. **Corrected constraint:** Exercise's `SubCounterDetailDialog` gains +/- edit controls for past days via `adjustSubCounterForDate` (Task 6 Step 0). Once Task 6 ships, the `TODOS.md` P3 item "Retroactive edit of a past day's rep counts" is resolved, not deferred — move it to TODOS.md's `## Completed` section (matching the pattern already used for the Exercise-detail-screen plan's 3 post-ship fixes) as part of Task 6's commit, referencing this task's commit hash.
- Every commit after a task leaves `./gradlew.bat :app:testDebugUnitTest` green.

---

## File Structure

```
Reminders/
  app/src/main/java/com/ziv/reminders/
    ui/activity/
      HeatmapDay.kt                                                    (Create — Task 1)
      HeatmapGrid.kt                                                   (Create — Task 1)
      ActivityViewModel.kt                                             (Create — Task 5)
      ActivityScreen.kt                                                (Create — Task 6)
    ui/exercise/
      ExerciseStatsScreen.kt                                           (Modify — Tasks 1, 6)
      ExerciseViewModel.kt                                             (Modify — Task 6)
    ui/dashboard/DashboardScreen.kt                                    (Modify — Task 6)
    data/
      HabitStats.kt                                                    (Modify — Task 1)
      ReadingSessionLog.kt                                              (Create — Task 2)
      ReadingSessionLogDao.kt                                           (Create — Task 2)
      AppDatabase.kt                                                   (Modify — Task 2)
      AppContainer.kt                                                   (Modify — Tasks 2, 5)
      TimerHabitRepository.kt                                          (Modify — Task 2)
      ScheduleCursorRepository.kt                                       (Modify — Task 3)
      WeeklySummary.kt                                                  (Create — Task 4)
    notifications/HabitNotifications.kt                                (Modify — Task 7)
    scheduling/HabitScheduler.kt                                        (Modify — Task 7)
    scheduling/HabitReminderReceiver.kt                                 (Modify — Task 7)
    RemindersApp.kt                                                     (Modify — Task 7)
    scheduling/BootReceiver.kt                                          (Modify — Task 7)
    scheduling/RolloverReceiver.kt                                      (Modify — Task 7)
    MainActivity.kt                                                     (Modify — Task 6)
  app/src/test/java/com/ziv/reminders/
    ui/activity/
      HeatmapDayTest.kt                                                (Create — Task 1)
    data/
      HabitStatsTest.kt                                                (Modify — Task 1)
      ReadingSessionLogDaoTest.kt                                       (Create — Task 2)
      AppDatabaseMigration5To6Test.kt                                   (Create — Task 2)
      TimerHabitRepositoryTest.kt                                       (Modify — Task 2)
      ScheduleCursorRepositoryTest.kt                                   (Modify — Task 3)
      WeeklySummaryTest.kt                                              (Create — Task 4)
    ui/activity/
      ActivityViewModelTest.kt                                         (Create — Task 5)
    ui/exercise/
      ActivityTestFakes.kt                                              (Create — Task 5)
      ExerciseViewModelTest.kt                                         (Modify — Task 6)
    scheduling/
      HabitSchedulerTest.kt                                             (Modify — Task 7)
      HabitReminderReceiverTest.kt                                      (Modify — Task 7)
      BootReceiverTest.kt                                               (Modify — Task 7, count update only)
      RolloverReceiverTest.kt                                           (Modify — Task 7, count update only)
```

---

### Task 1: `HeatmapGrid` extraction + `HabitStats.totalCount`

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/ui/activity/HeatmapDay.kt`
- Create: `app/src/main/java/com/ziv/reminders/ui/activity/HeatmapGrid.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseStatsScreen.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitStats.kt`
- Test: `app/src/test/java/com/ziv/reminders/ui/activity/HeatmapDayTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/HabitStatsTest.kt`

**Interfaces:**
- Produces: `enum class HeatmapDayStatus { HIT, PENDING, MISS }`; `data class HeatmapDay(val date: LocalDate, val status: HeatmapDayStatus)`; `fun buildHeatmapDays(dates: Set<LocalDate>, today: LocalDate): List<HeatmapDay>` (package `com.ziv.reminders.ui.activity`); `@Composable fun HeatmapGrid(dates: Set<LocalDate>, today: LocalDate, onDayClick: (LocalDate) -> Unit)` (same package, same signature as the composable it replaces); `HabitStats.totalCount(dates: Set<LocalDate>): Int`. Consumed by Task 6 (`ActivityScreen`, `ExerciseActivitySection`) and Task 5 (`ActivityViewModel`).

- [ ] **Step 1: Write the failing characterization tests for `buildHeatmapDays`**

`app/src/test/java/com/ziv/reminders/ui/activity/HeatmapDayTest.kt`:
```kotlin
package com.ziv.reminders.ui.activity

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeatmapDayTest {

    private val today = LocalDate.of(2026, 7, 19)

    @Test
    fun buildHeatmapDays_todayInDates_todayEntryIsHit() {
        val days = buildHeatmapDays(setOf(today), today)
        val todayEntry = days.first { it.date == today }
        assertEquals(HeatmapDayStatus.HIT, todayEntry.status)
    }

    @Test
    fun buildHeatmapDays_todayNotInDates_todayEntryIsPending() {
        val days = buildHeatmapDays(emptySet(), today)
        val todayEntry = days.first { it.date == today }
        assertEquals(HeatmapDayStatus.PENDING, todayEntry.status)
    }

    @Test
    fun buildHeatmapDays_pastDayNotInDates_isMiss() {
        val yesterday = today.minusDays(1)
        val days = buildHeatmapDays(emptySet(), today)
        val entry = days.first { it.date == yesterday }
        assertEquals(HeatmapDayStatus.MISS, entry.status)
    }

    @Test
    fun buildHeatmapDays_pastDayInDates_isHit() {
        val yesterday = today.minusDays(1)
        val days = buildHeatmapDays(setOf(yesterday), today)
        val entry = days.first { it.date == yesterday }
        assertEquals(HeatmapDayStatus.HIT, entry.status)
    }

    @Test
    fun buildHeatmapDays_firstEntry_isSundayAligned() {
        // Every 7-day chunk boundary aligns to Sunday by construction, regardless of what
        // weekday `today` falls on — the alignment math shifts windowStart backward to the
        // nearest Sunday before chunking.
        val days = buildHeatmapDays(emptySet(), today)
        assertEquals(DayOfWeek.SUNDAY, days.first().date.dayOfWeek)
    }

    @Test
    fun buildHeatmapDays_todayAppearsWithinFirstWeek_nearTheTop() {
        // Weeks are reversed (most recent week first) so today is always near the top of the
        // list with no scrolling — mirrors the original composable's documented intent.
        val days = buildHeatmapDays(emptySet(), today)
        assertTrue(days.take(7).any { it.date == today })
    }

    @Test
    fun buildHeatmapDays_noDuplicateDates() {
        val days = buildHeatmapDays(emptySet(), today)
        assertEquals(days.size, days.map { it.date }.toSet().size)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.activity.HeatmapDayTest"`
Expected: FAIL — `buildHeatmapDays`/`HeatmapDayStatus`/`HeatmapDay` don't exist yet (compile error).

- [ ] **Step 3: Write the pure implementation**

`app/src/main/java/com/ziv/reminders/ui/activity/HeatmapDay.kt`:
```kotlin
package com.ziv.reminders.ui.activity

import java.time.LocalDate

enum class HeatmapDayStatus { HIT, PENDING, MISS }

data class HeatmapDay(val date: LocalDate, val status: HeatmapDayStatus)

/**
 * Pure computation behind HeatmapGrid's rendering, extracted from the original private
 * HeatmapGrid function in ExerciseStatsScreen.kt with zero logic changes — this is the real
 * safety net for sharing the composable across habit kinds, since no Compose UI test coverage
 * exists for this app's screens (see Global Constraints).
 */
fun buildHeatmapDays(dates: Set<LocalDate>, today: LocalDate): List<HeatmapDay> {
    // Aligned to a fixed 7-column, Sunday-start grid so rows correspond to real calendar
    // weeks regardless of screen width — mirrors Shape's own HeatmapGrid exactly.
    val windowStart = today.minusMonths(12)
    val daysSinceSunday = windowStart.dayOfWeek.value % 7
    val alignedStart = windowStart.minusDays(daysSinceSunday.toLong())

    // Weeks reversed (most recent week first) so today is always near the top with no
    // scrolling — mirrors Shape's own HeatmapGrid exactly.
    val orderedDays = generateSequence(alignedStart) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .toList()
        .chunked(7)
        .reversed()
        .flatten()

    return orderedDays.map { day ->
        val status = when {
            day == today && day !in dates -> HeatmapDayStatus.PENDING
            day in dates -> HeatmapDayStatus.HIT
            else -> HeatmapDayStatus.MISS
        }
        HeatmapDay(day, status)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.activity.HeatmapDayTest"`
Expected: PASS (7 tests)

- [ ] **Step 5: Write the shared `HeatmapGrid` composable**

`app/src/main/java/com/ziv/reminders/ui/activity/HeatmapGrid.kt`:
```kotlin
package com.ziv.reminders.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ziv.reminders.ui.exercise.HeatmapHit
import com.ziv.reminders.ui.exercise.HeatmapPending
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Shared across Exercise/Reading/Tanakh's Activity sections (Task 6) — extracted from
 * ExerciseStatsScreen.kt's original private HeatmapGrid, with the day-alignment/status math
 * moved into the pure, unit-tested buildHeatmapDays.
 *
 * **Corrected during /autoplan design review — deliberately NOT a `LazyVerticalGrid`.** The
 * original extraction kept the source composable's `LazyVerticalGrid`, but Task 6 nests this
 * composable (three times — once per habit section) inside `ActivityScreen`'s outer
 * `Column(Modifier.verticalScroll(...))`. A `LazyVerticalGrid` given no explicit height,
 * placed inside a `verticalScroll` parent, crashes at runtime ("Vertically scrollable
 * component was measured with an infinite height constraints") — a well-known Compose nested-
 * scroll failure mode. `ExerciseStatsScreen.kt`'s original standalone screen never hit this
 * because its outer `Column` had no `verticalScroll` of its own (the single grid was the only
 * scrollable element). Fix: render as a plain, eager `Column` of `Row`s (7 cells per row) —
 * the heatmap's dataset is small and fixed-size (~53 weeks × 7 days year-round), so eager
 * rendering costs nothing meaningful, and a plain Column/Row nests inside any parent scroll
 * container with zero risk, unlike any `Lazy*` composable. This also avoids the awkward
 * scroll-within-scroll UX a fixed-height workaround would have introduced.
 */
@Composable
fun HeatmapGrid(dates: Set<LocalDate>, today: LocalDate, onDayClick: (LocalDate) -> Unit) {
    val days = remember(dates, today) { buildHeatmapDays(dates, today) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val color = when (day.status) {
                        HeatmapDayStatus.PENDING -> HeatmapPending
                        HeatmapDayStatus.HIT -> HeatmapHit
                        HeatmapDayStatus.MISS -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val description = "${day.date.format(DateTimeFormatter.ISO_LOCAL_DATE)}: " + when (day.status) {
                        HeatmapDayStatus.PENDING -> "not yet done"
                        HeatmapDayStatus.HIT -> "goal hit"
                        HeatmapDayStatus.MISS -> "missed"
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                            .clickable { onDayClick(day.date) }
                            .semantics { contentDescription = description }
                    ) {}
                }
            }
        }
    }
}
```

- [ ] **Step 6: Update `ExerciseStatsScreen.kt` to use the shared composable**

Modify `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseStatsScreen.kt` — remove the private `HeatmapGrid` function (lines 99-141 of the current file) and its now-unused imports (`androidx.compose.foundation.background`, `androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.aspectRatio`, `androidx.compose.foundation.lazy.grid.GridCells`, `androidx.compose.foundation.lazy.grid.LazyVerticalGrid`, `androidx.compose.foundation.lazy.grid.items`, `androidx.compose.foundation.shape.RoundedCornerShape`, `androidx.compose.ui.draw.clip`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`), and add:
```kotlin
import com.ziv.reminders.ui.activity.HeatmapGrid
```
The call site `HeatmapGrid(dates = uiState.completedDates, today = today, onDayClick = { day -> selectedDate = day })` is unchanged — only its resolution moves from the local private function to the new shared import.

- [ ] **Step 7: Run the exercise UI file's dependent tests and the full suite**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.exercise.ExerciseViewModelTest"`
Expected: PASS (unaffected — this test doesn't touch the Compose layer)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests green — confirms the extraction didn't break compilation anywhere)

- [ ] **Step 8: Write the failing `totalCount` test**

Add to `app/src/test/java/com/ziv/reminders/data/HabitStatsTest.kt`, inside the existing `HabitStatsTest` class (e.g. after `recordSuffix_isRecordFalse_returnsEmptyString`):
```kotlin
    @Test
    fun totalCount_countsEveryDateRegardlessOfContiguity() {
        val dates = setOf(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 5), LocalDate.of(2025, 12, 25),
        )
        assertEquals(3, HabitStats.totalCount(dates))
    }

    @Test
    fun totalCount_emptySet_isZero() {
        assertEquals(0, HabitStats.totalCount(emptySet()))
    }
```

- [ ] **Step 9: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.HabitStatsTest"`
Expected: FAIL — `HabitStats.totalCount` doesn't exist yet (compile error).

- [ ] **Step 10: Add `totalCount` to `HabitStats`**

Add to `app/src/main/java/com/ziv/reminders/data/HabitStats.kt`, inside the `HabitStats` object (e.g. after `recordSuffix`):
```kotlin
    // Lifetime total — mask-independent by nature (a running total doesn't care which days
    // were "enabled"), computed on-the-fly like every other stat in this object rather than
    // cached in a new Room column (see this feature's design doc, "totalCount is computed
    // on-the-fly, not stored").
    fun totalCount(dates: Set<LocalDate>): Int = dates.size
```

- [ ] **Step 11: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.HabitStatsTest"`
Expected: PASS (16 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/activity/HeatmapDay.kt app/src/main/java/com/ziv/reminders/ui/activity/HeatmapGrid.kt app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseStatsScreen.kt app/src/main/java/com/ziv/reminders/data/HabitStats.kt app/src/test/java/com/ziv/reminders/ui/activity/HeatmapDayTest.kt app/src/test/java/com/ziv/reminders/data/HabitStatsTest.kt
git commit -m "Extract HeatmapGrid into a shared composable and add HabitStats.totalCount"
```

---

### Task 2: Reading session log (Room v5→v6)

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/data/ReadingSessionLog.kt`
- Create: `app/src/main/java/com/ziv/reminders/data/ReadingSessionLogDao.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/TimerHabitRepository.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/ReadingSessionLogDaoTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration5To6Test.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/TimerHabitRepositoryTest.kt`

**Interfaces:**
- Produces: `data class ReadingSessionLog(id: Long = 0, habitInstanceId: Long, date: String, startedAt: Long, endedAt: Long, durationSeconds: Int)`; `interface ReadingSessionLogDao { suspend fun insert(session: ReadingSessionLog): Long; suspend fun getForDate(habitInstanceId: Long, date: String): List<ReadingSessionLog>; suspend fun delete(session: ReadingSessionLog) }`; `AppDatabase.MIGRATION_5_6`; `TimerHabitRepository(dao, clock, sessionLogDao: ReadingSessionLogDao? = null)` (new nullable 3rd param); `TimerHabitRepository.completedDates(instance: HabitInstance): List<String>`; `TimerHabitRepository.sessionsForDate(instance: HabitInstance, date: LocalDate): List<ReadingSessionLog>`; `TimerHabitRepository.deleteSession(session: ReadingSessionLog)`. Consumed by Task 5 (`ActivityViewModel`), Task 4 (`WeeklySummary` via Reading's completed dates).

- [ ] **Step 1: Write the failing DAO test**

`app/src/test/java/com/ziv/reminders/data/ReadingSessionLogDaoTest.kt`:
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadingSessionLogDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getForDate_noRows_returnsEmptyList() = runTest {
        val db = newDb()
        assertTrue(db.readingSessionLogDao().getForDate(2L, "2026-07-19").isEmpty())
        db.close()
    }

    @Test
    fun insert_thenGetForDate_returnsIt() = runTest {
        val db = newDb()
        val id = db.readingSessionLogDao().insert(
            ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-19", startedAt = 1000L, endedAt = 1900L, durationSeconds = 900)
        )

        val rows = db.readingSessionLogDao().getForDate(2L, "2026-07-19")
        assertEquals(1, rows.size)
        assertEquals(id, rows[0].id)
        assertEquals(900, rows[0].durationSeconds)
        db.close()
    }

    @Test
    fun insert_twoSessionsSameDate_returnsBothOrderedByStartTime() = runTest {
        val db = newDb()
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-19", startedAt = 5000L, endedAt = 5500L, durationSeconds = 500))
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-19", startedAt = 1000L, endedAt = 1300L, durationSeconds = 300))

        val rows = db.readingSessionLogDao().getForDate(2L, "2026-07-19")
        assertEquals(listOf(1000L, 5000L), rows.map { it.startedAt })
        db.close()
    }

    @Test
    fun getForDate_differentDate_isExcluded() = runTest {
        val db = newDb()
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-18", startedAt = 1000L, endedAt = 1300L, durationSeconds = 300))

        assertTrue(db.readingSessionLogDao().getForDate(2L, "2026-07-19").isEmpty())
        db.close()
    }

    @Test
    fun delete_removesTheRow() = runTest {
        val db = newDb()
        db.readingSessionLogDao().insert(ReadingSessionLog(habitInstanceId = 2L, date = "2026-07-19", startedAt = 1000L, endedAt = 1300L, durationSeconds = 300))
        val row = db.readingSessionLogDao().getForDate(2L, "2026-07-19").single()

        db.readingSessionLogDao().delete(row)

        assertTrue(db.readingSessionLogDao().getForDate(2L, "2026-07-19").isEmpty())
        db.close()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ReadingSessionLogDaoTest"`
Expected: FAIL — `ReadingSessionLog`, `ReadingSessionLogDao`, `AppDatabase.readingSessionLogDao()` don't exist yet (compile error).

- [ ] **Step 3: Write the entity and DAO**

`app/src/main/java/com/ziv/reminders/data/ReadingSessionLog.kt`:
```kotlin
package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per Reading start/stop segment — the session log ReadBook's own ReadingSession table
 * kept and Reminders' timestamp-delta TimerDailyProgress never needed for daily state, but
 * which the Activity screen's Reading section requires. Written once per completed segment
 * (TimerHabitRepository.finishSession), read/deleted only via an explicit user action from the
 * Activity screen's day-edit dialog (Task 6). Keyed by an autoincrementing surrogate id (the
 * first entity in this codebase to use one) since, unlike every other table here, a session
 * log has no natural composite business key — multiple sessions can share the same
 * (habitInstanceId, date).
 *
 * Indexed on (habitInstanceId, date) — every real query against this table (getForDate) filters
 * on exactly that pair, never the surrogate id alone; without this index each lookup is a full
 * table scan. The index must be declared here AND created by the matching SQL in MIGRATION_5_6
 * below — Room validates the migrated schema against this annotation at build time and throws
 * if the two ever drift apart.
 */
@Entity(tableName = "reading_session_log", indices = [Index(value = ["habitInstanceId", "date"])])
data class ReadingSessionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitInstanceId: Long,
    val date: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Int,
)
```

`app/src/main/java/com/ziv/reminders/data/ReadingSessionLogDao.kt`:
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
}
```

Modify `app/src/main/java/com/ziv/reminders/data/AppDatabase.kt` (full file):
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
    ],
    version = 6,
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

        /** Adds the Reading per-session log table — one row per start/stop segment, the first
         * table in this codebase with an autoincrement surrogate key instead of a composite
         * business key (see ReadingSessionLog's doc comment for why). Never
         * fallbackToDestructiveMigration() — see Global Constraints. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reading_session_log` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitInstanceId` INTEGER NOT NULL, " +
                        "`date` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, " +
                        "`durationSeconds` INTEGER NOT NULL)"
                )
                // Must match ReadingSessionLog's `indices = [Index(value = ["habitInstanceId", "date"])]`
                // exactly (including Room's default index-name convention) or schema validation
                // fails at app startup with a migration-mismatch exception.
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reading_session_log_habitInstanceId_date` " +
                        "ON `reading_session_log` (`habitInstanceId`, `date`)"
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ReadingSessionLogDaoTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Write the failing migration test**

`app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration5To6Test.kt`:
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
class AppDatabaseMigration5To6Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate5To6_preservesExistingRows_andAddsReadingSessionLogTable() {
        helper.createDatabase(TEST_DB_NAME, 5).apply {
            execSQL(
                "INSERT INTO habit_instance (id, kind, name, enabledDaysMask, notificationTitle, notificationBody, counterGoal, timerTargetSeconds) " +
                    "VALUES (2, 'TIMER', 'Reading', 31, 't', 'b', NULL, 900)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 6, true, AppDatabase.MIGRATION_5_6)

        migrated.query("SELECT name FROM habit_instance WHERE id = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Reading", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM reading_session_log").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_reading_session_log_habitInstanceId_date'").use { cursor ->
            assertTrue(cursor.moveToFirst(), "expected index_reading_session_log_habitInstanceId_date to exist after migration")
        }
    }

    companion object {
        private const val TEST_DB_NAME = "migration-test-5-6"
    }
}
```

- [ ] **Step 6: Run the migration test, then the full suite**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.AppDatabaseMigration5To6Test"`
Expected: PASS (1 test)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests still green)

- [ ] **Step 7: Write the failing `TimerHabitRepository` tests**

Add to `app/src/test/java/com/ziv/reminders/data/TimerHabitRepositoryTest.kt` — first add a new fake near the top (after `FakeTimerDailyProgressDao`):
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
}
```

Then add these test methods inside the existing `TimerHabitRepositoryTest` class (e.g. after `currentStreak_delegatesToStreakCalculatorWithTheInstancesEnabledDaysMask`):
```kotlin
    @Test
    fun stop_logsACompletedSessionWithStartAndEndTimestamps() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val sessionLogDao = FakeReadingSessionLogDao()
        val repo = TimerHabitRepository(dao, clock, sessionLogDao)
        repo.start(instance, today)
        clock.millis += 120_000L // 120s elapse

        repo.stop(instance, today)

        val sessions = repo.sessionsForDate(instance, today)
        assertEquals(1, sessions.size)
        assertEquals(1_000_000L, sessions[0].startedAt)
        assertEquals(1_120_000L, sessions[0].endedAt)
        assertEquals(120, sessions[0].durationSeconds)
    }

    @Test
    fun stop_withNoSessionLogDaoProvided_stillCompletesNormally_neverCrashes() = runTest {
        // Regression guard: TimerHabitRepository's 8 pre-existing call sites (test files
        // predating this feature) construct it with only (dao, clock) — sessionLogDao must
        // default to null and finishSession must tolerate that, never crash for lack of
        // session logging.
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val repo = TimerHabitRepository(dao, clock)
        repo.start(instance, today)
        clock.millis += 60_000L

        val result = repo.stop(instance, today)

        assertEquals(840, result?.remainingSeconds)
    }

    @Test
    fun deleteSession_removesItFromSessionsForDate() = runTest {
        val clock = FakeClock(millis = 1_000_000L)
        val dao = FakeTimerDailyProgressDao()
        val sessionLogDao = FakeReadingSessionLogDao()
        val repo = TimerHabitRepository(dao, clock, sessionLogDao)
        repo.start(instance, today)
        clock.millis += 60_000L
        repo.stop(instance, today)
        val logged = repo.sessionsForDate(instance, today).single()

        repo.deleteSession(logged)

        assertEquals(emptyList(), repo.sessionsForDate(instance, today))
    }

    @Test
    fun completedDates_returnsOnlyDatesWithCompletedTrue() = runTest {
        val dao = FakeTimerDailyProgressDao()
        dao.rows[1L to "2026-07-17"] = TimerDailyProgress(1L, "2026-07-17", 900, 0, true, 1L, null)
        dao.rows[1L to "2026-07-18"] = TimerDailyProgress(1L, "2026-07-18", 900, 400, false, null, null)
        val repo = TimerHabitRepository(dao, FakeClock())

        assertEquals(listOf("2026-07-17"), repo.completedDates(instance))
    }
```

- [ ] **Step 8: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.TimerHabitRepositoryTest"`
Expected: FAIL — `sessionsForDate`, `deleteSession`, `completedDates`, and the 3-arg constructor don't exist yet (compile error).

- [ ] **Step 9: Modify `TimerHabitRepository`**

Full `app/src/main/java/com/ziv/reminders/data/TimerHabitRepository.kt`:
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
 */
class TimerHabitRepository(
    private val dao: TimerDailyProgressDao,
    private val clock: Clock,
    private val sessionLogDao: ReadingSessionLogDao? = null,
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

- [ ] **Step 10: Wire `AppContainer`**

Modify `app/src/main/java/com/ziv/reminders/data/AppContainer.kt` — add the new DAO getter, add `MIGRATION_5_6` to the migrations list, and pass `readingSessionLogDao` as the 3rd argument to `TimerHabitRepository`:
```kotlin
    private val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "reminders.db")
            // Never fallbackToDestructiveMigration() — see Global Constraints.
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6,
            )
            .build()
    }
```
And:
```kotlin
    val readingSessionLogDao get() = db.readingSessionLogDao()
    ...
    val timerHabitRepository: TimerHabitRepository by lazy { TimerHabitRepository(timerDailyProgressDao, SystemClock, readingSessionLogDao) }
```
(replacing the existing `val timerHabitRepository: TimerHabitRepository by lazy { TimerHabitRepository(timerDailyProgressDao, SystemClock) }` line — everything else in the file is unchanged.)

- [ ] **Step 11: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.TimerHabitRepositoryTest"`
Expected: PASS (13 tests — 9 existing + 4 new)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green — confirms the other 8 pre-existing `TimerHabitRepository(dao, clock)` call sites across `ExerciseViewModelTest`, `TestAppContainer`, `TimerServiceTest`, `HabitReminderReceiverTest`, `CrossHabitEvaluatorTest`, `EscalationWorkerTest`, `HabitEngineTest` still compile unchanged)

- [ ] **Step 12: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/ReadingSessionLog.kt app/src/main/java/com/ziv/reminders/data/ReadingSessionLogDao.kt app/src/main/java/com/ziv/reminders/data/AppDatabase.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/main/java/com/ziv/reminders/data/TimerHabitRepository.kt app/src/test/java/com/ziv/reminders/data/ReadingSessionLogDaoTest.kt app/src/test/java/com/ziv/reminders/data/AppDatabaseMigration5To6Test.kt app/src/test/java/com/ziv/reminders/data/TimerHabitRepositoryTest.kt
git commit -m "Add Room schema v6 for Reading per-session history log"
```

---

### Task 3: Tanakh undo + `completedDates`

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/ScheduleCursorRepository.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/ScheduleCursorRepositoryTest.kt`

**Interfaces:**
- Produces: `ScheduleCursorRepository.undoMarkRead(instance: HabitInstance, date: LocalDate)`; `ScheduleCursorRepository.completedDates(instance: HabitInstance): List<String>`. Consumed by Task 5 (`ActivityViewModel`), Task 4 (`WeeklySummary`).

- [ ] **Step 1: Write the failing tests**

Add to `app/src/test/java/com/ziv/reminders/data/ScheduleCursorRepositoryTest.kt`, inside the existing `ScheduleCursorRepositoryTest` class (e.g. after `markRead_whenFinished_isANoOp`):
```kotlin
    @Test
    fun undoMarkRead_decrementsCursorByOne() = runTest {
        val progressDao = FakeScheduleCursorProgressDao()
        val dailyDao = FakeScheduleCursorDailyProgressDao()
        val repo = ScheduleCursorRepository(progressDao, dailyDao, schedule)
        val today = LocalDate.of(2026, 7, 12)
        repo.markRead(instance, today)

        repo.undoMarkRead(instance, today)

        assertEquals(0, progressDao.rows[3L]?.cursorIndex)
    }

    @Test
    fun undoMarkRead_clearsTodaysCompletedFlagWhenCountReachesZero() = runTest {
        val progressDao = FakeScheduleCursorProgressDao()
        val dailyDao = FakeScheduleCursorDailyProgressDao()
        val repo = ScheduleCursorRepository(progressDao, dailyDao, schedule)
        val today = LocalDate.of(2026, 7, 12)
        repo.markRead(instance, today)

        repo.undoMarkRead(instance, today)

        assertEquals(false, dailyDao.rows[3L to today.toString()]?.completed)
    }

    @Test
    fun undoMarkRead_cursorAtZero_isANoOp_neverGoesNegative() = runTest {
        val progressDao = FakeScheduleCursorProgressDao()
        val dailyDao = FakeScheduleCursorDailyProgressDao()
        val repo = ScheduleCursorRepository(progressDao, dailyDao, schedule)

        repo.undoMarkRead(instance, LocalDate.of(2026, 7, 12))

        assertEquals(null, progressDao.rows[3L])
    }

    @Test
    fun completedDates_returnsOnlyDatesWithCompletedTrue() = runTest {
        val dailyDao = FakeScheduleCursorDailyProgressDao()
        dailyDao.rows[3L to "2026-07-12"] = ScheduleCursorDailyProgress(3L, "2026-07-12", 1, true)
        dailyDao.rows[3L to "2026-07-13"] = ScheduleCursorDailyProgress(3L, "2026-07-13", 0, false)
        val repo = ScheduleCursorRepository(FakeScheduleCursorProgressDao(), dailyDao, schedule)

        assertEquals(listOf("2026-07-12"), repo.completedDates(instance))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ScheduleCursorRepositoryTest"`
Expected: FAIL — `undoMarkRead` and `completedDates` don't exist yet (compile error).

- [ ] **Step 3: Modify `ScheduleCursorRepository`**

Full `app/src/main/java/com/ziv/reminders/data/ScheduleCursorRepository.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate

/**
 * Combines the pure schedule-position status (deriveScheduleEntryStatus) with a per-day "did I
 * mark anything today" flag to produce the engine-wide HabitStatus.ScheduleCursorStatus.
 * completed reflects only today's activity (streak-relevant, per the design doc's rule), not
 * whether the whole backlog is cleared — matching Counter/Timer's shared "todayStatus.completed"
 * contract used generically by HabitEngine/HabitReminderReceiver/the dashboard checkmark.
 *
 * undoMarkRead reverses only the most recently marked-read entry — Tanakh's cursor is a single
 * global position, not independent per-day state like Exercise's sub-counters or Reading's
 * session log, so "undo" is meaningful only for the current cursor position, never an
 * arbitrary past day (see this feature's design doc, Recommended Approach). The Activity
 * screen's Tanakh day-edit dialog (Task 6) only offers this action when the tapped day is
 * today.
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
        // No-op once the schedule is exhausted (all entries read, or the bundled CSV failed to
        // load and fell back to an empty list) — otherwise tapping a "Finished" row would still
        // advance the cursor past the end and falsely credit a streak day for nothing read.
        if (deriveScheduleEntryStatus(schedule, cursorIndex, today) is ScheduleEntryStatus.Finished) return
        progressDao.upsert(ScheduleCursorProgress(instance.id, cursorIndex + 1))

        val key = today.toString()
        val newCount = (dailyProgressDao.getByDate(instance.id, key)?.entriesMarkedRead ?: 0) + 1
        dailyProgressDao.upsert(ScheduleCursorDailyProgress(instance.id, key, entriesMarkedRead = newCount, completed = true))
    }

    suspend fun undoMarkRead(instance: HabitInstance, date: LocalDate) {
        val progress = progressDao.getByInstance(instance.id) ?: return
        if (progress.cursorIndex <= 0) return // nothing to undo
        progressDao.upsert(progress.copy(cursorIndex = progress.cursorIndex - 1))

        val key = date.toString()
        val daily = dailyProgressDao.getByDate(instance.id, key) ?: return
        val newCount = (daily.entriesMarkedRead - 1).coerceAtLeast(0)
        dailyProgressDao.upsert(daily.copy(entriesMarkedRead = newCount, completed = newCount > 0))
    }

    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int {
        val completedDates = dailyProgressDao.getCompletedDates(instance.id)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()
        return StreakCalculator.calculate(completedDates, instance.enabledDaysMask, today)
    }

    // Feeds the Activity screen's Tanakh heatmap (Task 6) and WeeklySummary's aggregation (Task 4).
    suspend fun completedDates(instance: HabitInstance): List<String> = dailyProgressDao.getCompletedDates(instance.id)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ScheduleCursorRepositoryTest"`
Expected: PASS (14 tests — 10 existing + 4 new)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/ScheduleCursorRepository.kt app/src/test/java/com/ziv/reminders/data/ScheduleCursorRepositoryTest.kt
git commit -m "Add ScheduleCursorRepository.undoMarkRead and completedDates"
```

---

### Task 4: Cross-habit weekly aggregation (`WeeklySummary`)

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/data/WeeklySummary.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/WeeklySummaryTest.kt`

**Interfaces:**
- Produces: `data class WeeklyHabitCount(val exerciseDays: Int, val readingDays: Int, val tanakhDays: Int, val comboStreak: Int)`; `object WeeklySummary { fun compute(exerciseDates: Set<LocalDate>, readingDates: Set<LocalDate>, tanakhDates: Set<LocalDate>, today: LocalDate): WeeklyHabitCount }`. Consumed by Task 5 (`ActivityViewModel`) and Task 7 (weekly-summary notification text).

- [ ] **Step 1: Write the failing tests**

`app/src/test/java/com/ziv/reminders/data/WeeklySummaryTest.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklySummaryTest {

    private val today = LocalDate.of(2026, 7, 19)

    @Test
    fun compute_countsOnlyDatesWithinTheSevenDayWindow() {
        val exerciseDates = setOf(today, today.minusDays(3), today.minusDays(10)) // 10 days ago is outside the window
        val summary = WeeklySummary.compute(exerciseDates, emptySet(), emptySet(), today)
        assertEquals(2, summary.exerciseDays)
    }

    @Test
    fun compute_windowIsInclusiveOfTodayAndSixDaysBack() {
        val exerciseDates = setOf(today.minusDays(6)) // exactly the oldest day in a 7-day window
        val summary = WeeklySummary.compute(exerciseDates, emptySet(), emptySet(), today)
        assertEquals(1, summary.exerciseDays)
    }

    @Test
    fun compute_eachHabitCountedIndependently() {
        val exerciseDates = setOf(today)
        val readingDates = setOf(today, today.minusDays(1))
        val tanakhDates = emptySet<LocalDate>()
        val summary = WeeklySummary.compute(exerciseDates, readingDates, tanakhDates, today)
        assertEquals(1, summary.exerciseDays)
        assertEquals(2, summary.readingDays)
        assertEquals(0, summary.tanakhDays)
    }

    @Test
    fun compute_comboStreak_countsOnlyDaysAllThreeHabitsWereHit() {
        val exerciseDates = setOf(today, today.minusDays(1))
        val readingDates = setOf(today, today.minusDays(1), today.minusDays(2))
        val tanakhDates = setOf(today) // only today has all three
        val summary = WeeklySummary.compute(exerciseDates, readingDates, tanakhDates, today)
        assertEquals(1, summary.comboStreak)
    }

    @Test
    fun compute_comboStreak_noOverlap_isZero() {
        val summary = WeeklySummary.compute(setOf(today), setOf(today.minusDays(1)), emptySet(), today)
        assertEquals(0, summary.comboStreak)
    }

    @Test
    fun compute_allEmpty_isAllZeroes() {
        val summary = WeeklySummary.compute(emptySet(), emptySet(), emptySet(), today)
        assertEquals(WeeklyHabitCount(0, 0, 0, 0), summary)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.WeeklySummaryTest"`
Expected: FAIL — `WeeklySummary`/`WeeklyHabitCount` don't exist yet (compile error).

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/ziv/reminders/data/WeeklySummary.kt`:
```kotlin
package com.ziv.reminders.data

import java.time.LocalDate

data class WeeklyHabitCount(
    val exerciseDays: Int,
    val readingDays: Int,
    val tanakhDays: Int,
    // Corrected during /autoplan CEO review — "Streak" here does NOT mean what it means
    // everywhere else in this codebase. HabitStats.currentStreak/StreakCalculator both anchor
    // on today-or-yesterday and require unbroken consecutive days; comboStreak just counts how
    // many of the last 7 days (independently, no contiguity required) all three habits were
    // hit. Kept as-is rather than renamed (would touch ~8 call sites across Task 4/5/6/7 for a
    // naming-only fix) — this doc comment is the disambiguation.
    val comboStreak: Int,
)

/**
 * New aggregation logic written alongside CrossHabitEvaluator, not through it —
 * CrossHabitEvaluator is a single hardcoded Exercise+Reading, single-day escalation condition
 * with no Tanakh wiring and no historical window (see its own doc comment: "a single hardcoded
 * condition, not a generic rule engine"). This is a genuinely different computation: a 7-day
 * window across all three habits, feeding both the Activity screen's combo-streak display and
 * the weekly-summary notification (Task 5/7).
 */
object WeeklySummary {

    fun compute(
        exerciseDates: Set<LocalDate>,
        readingDates: Set<LocalDate>,
        tanakhDates: Set<LocalDate>,
        today: LocalDate,
    ): WeeklyHabitCount {
        val window = (0..6).map { today.minusDays(it.toLong()) }.toSet()
        val comboStreak = window.count { it in exerciseDates && it in readingDates && it in tanakhDates }
        return WeeklyHabitCount(
            exerciseDays = (exerciseDates intersect window).size,
            readingDays = (readingDates intersect window).size,
            tanakhDays = (tanakhDates intersect window).size,
            comboStreak = comboStreak,
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.WeeklySummaryTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/WeeklySummary.kt app/src/test/java/com/ziv/reminders/data/WeeklySummaryTest.kt
git commit -m "Add WeeklySummary cross-habit aggregation"
```

---

### Task 5: `ActivityViewModel` + `ActivityDataSource`

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/ui/activity/ActivityViewModel.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/AppContainer.kt`
- Test: `app/src/test/java/com/ziv/reminders/ui/activity/ActivityViewModelTest.kt`

**Interfaces:**
- Produces: `interface ActivityDataSource { val habitInstanceDao: HabitInstanceDao; val counterHabitRepository: CounterHabitRepository; val timerHabitRepository: TimerHabitRepository; val scheduleCursorRepository: ScheduleCursorRepository; val habitEngine: HabitEngine }`; `data class ActivitySectionState(val streak: Int, val totalCount: Int, val completedDates: Set<LocalDate>)`; `data class ActivityUiState(val exercise: ActivitySectionState, val reading: ActivitySectionState, val tanakh: ActivitySectionState, val comboStreakThisWeek: Int, val isLoaded: Boolean)`; `class ActivityViewModel(dataSource: ActivityDataSource) : ViewModel() { fun refresh(); suspend fun readingSessionsForDate(date: LocalDate): List<ReadingSessionLog>; fun deleteReadingSession(session: ReadingSessionLog); fun undoTanakhMarkRead(date: LocalDate) }`. Consumed by Task 6 (`ActivityScreen`), Task 6 (`MainActivity`'s `NavHost`).

**Note:** `ActivityDataSource` deliberately omits `subCounterRepository` — the Exercise section reuses the existing `ExerciseViewModel`/`ExerciseDetailDataSource` directly (see Task 6), not a duplicate path through `ActivityDataSource`.

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/ziv/reminders/ui/activity/ActivityViewModelTest.kt`:
```kotlin
package com.ziv.reminders.ui.activity

import com.ziv.reminders.data.ActivityDataSource
import com.ziv.reminders.data.CounterDailyProgress
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitInstanceDao
import com.ziv.reminders.data.READING_HABIT_INSTANCE_ID
import com.ziv.reminders.data.ReadingSessionLog
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TANAKH_HABIT_INSTANCE_ID
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.data.ScheduleCursorProgress
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.engine.HabitEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

private class FakeHabitInstanceDao(private val instances: Map<Long, HabitInstance>) : HabitInstanceDao {
    override suspend fun getById(id: Long) = instances[id]
    override suspend fun getAll() = instances.values.toList()
    override suspend fun insertIfAbsent(instance: HabitInstance) { /* unused in this test */ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModelTest {

    private val today = LocalDate.of(2026, 7, 19)
    private val exercise = HabitInstance(EXERCISE_HABIT_INSTANCE_ID, "COUNTER", "Exercise", 0b1111111, "t", "b", counterGoal = 5)
    private val reading = HabitInstance(READING_HABIT_INSTANCE_ID, "TIMER", "Reading", 0b0011111, "t", "b", counterGoal = null, timerTargetSeconds = 900)
    private val tanakh = HabitInstance(TANAKH_HABIT_INSTANCE_ID, "SCHEDULE_CURSOR", "Tanakh", 0b0011111, "t", "b", counterGoal = null)

    @Before
    fun setMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After
    fun resetMainDispatcher() { Dispatchers.resetMain() }

    private fun dataSource(
        counterDao: com.ziv.reminders.data.CounterDailyProgressDao,
        timerDao: com.ziv.reminders.data.TimerDailyProgressDao,
        cursorProgressDao: com.ziv.reminders.data.ScheduleCursorProgressDao,
        cursorDailyDao: com.ziv.reminders.data.ScheduleCursorDailyProgressDao,
    ): ActivityDataSource {
        val counterRepo = CounterHabitRepository(counterDao)
        val timerRepo = TimerHabitRepository(timerDao, SystemClock)
        val cursorRepo = ScheduleCursorRepository(cursorProgressDao, cursorDailyDao, emptyList())
        val engine = HabitEngine(counterRepo, timerRepo, cursorRepo)
        val dao = FakeHabitInstanceDao(mapOf(EXERCISE_HABIT_INSTANCE_ID to exercise, READING_HABIT_INSTANCE_ID to reading, TANAKH_HABIT_INSTANCE_ID to tanakh))
        return object : ActivityDataSource {
            override val habitInstanceDao = dao
            override val counterHabitRepository = counterRepo
            override val timerHabitRepository = timerRepo
            override val scheduleCursorRepository = cursorRepo
            override val habitEngine = engine
        }
    }

    @Test
    fun refresh_populatesAllThreeSectionsAndComboStreak() = runTest {
        val counterDao = com.ziv.reminders.ui.exercise.FakeCounterDailyProgressDaoForActivityTest()
        counterDao.rows[EXERCISE_HABIT_INSTANCE_ID to today.toString()] = CounterDailyProgress(EXERCISE_HABIT_INSTANCE_ID, today.toString(), 5, true)
        val timerDao = com.ziv.reminders.ui.exercise.FakeTimerDailyProgressDaoForActivityTest()
        timerDao.rows[READING_HABIT_INSTANCE_ID to today.toString()] = com.ziv.reminders.data.TimerDailyProgress(READING_HABIT_INSTANCE_ID, today.toString(), 900, 0, true, 1L, null)
        val cursorProgressDao = com.ziv.reminders.ui.exercise.FakeScheduleCursorProgressDaoForActivityTest()
        val cursorDailyDao = com.ziv.reminders.ui.exercise.FakeScheduleCursorDailyProgressDaoForActivityTest()
        cursorDailyDao.rows[TANAKH_HABIT_INSTANCE_ID to today.toString()] = com.ziv.reminders.data.ScheduleCursorDailyProgress(TANAKH_HABIT_INSTANCE_ID, today.toString(), 1, true)

        val viewModel = ActivityViewModel(dataSource(counterDao, timerDao, cursorProgressDao, cursorDailyDao))
        viewModel.refresh()

        val state = viewModel.uiState.value
        assertEquals(true, state.isLoaded)
        assertEquals(1, state.exercise.totalCount)
        assertEquals(1, state.reading.totalCount)
        assertEquals(1, state.tanakh.totalCount)
        assertEquals(1, state.comboStreakThisWeek)
    }
}
```

Given `HabitInstanceDao`, `CounterDailyProgressDao`, `TimerDailyProgressDao`, `ScheduleCursorProgressDao`, `ScheduleCursorDailyProgressDao` are Room `@Dao` interfaces (not directly fakeable without matching every method), add these small test-only fakes to a new shared test file `app/src/test/java/com/ziv/reminders/ui/exercise/ActivityTestFakes.kt` (package chosen only so the fakes sit next to this codebase's other test-fake precedents; referenced by the test above via fully-qualified names to keep the main test file's imports short):
```kotlin
package com.ziv.reminders.ui.exercise

import com.ziv.reminders.data.CounterDailyProgress
import com.ziv.reminders.data.CounterDailyProgressDao
import com.ziv.reminders.data.ScheduleCursorDailyProgress
import com.ziv.reminders.data.ScheduleCursorDailyProgressDao
import com.ziv.reminders.data.ScheduleCursorProgress
import com.ziv.reminders.data.ScheduleCursorProgressDao
import com.ziv.reminders.data.TimerDailyProgress
import com.ziv.reminders.data.TimerDailyProgressDao

class FakeCounterDailyProgressDaoForActivityTest : CounterDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, CounterDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: CounterDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
}

class FakeTimerDailyProgressDaoForActivityTest : TimerDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, TimerDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: TimerDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
    override suspend fun getActiveSessions() = rows.values.filter { it.activeSessionStartedAt != null }
}

class FakeScheduleCursorProgressDaoForActivityTest : ScheduleCursorProgressDao {
    val rows = mutableMapOf<Long, ScheduleCursorProgress>()
    override suspend fun getByInstance(habitInstanceId: Long) = rows[habitInstanceId]
    override suspend fun upsert(progress: ScheduleCursorProgress) { rows[progress.habitInstanceId] = progress }
}

class FakeScheduleCursorDailyProgressDaoForActivityTest : ScheduleCursorDailyProgressDao {
    val rows = mutableMapOf<Pair<Long, String>, ScheduleCursorDailyProgress>()
    override suspend fun getByDate(habitInstanceId: Long, date: String) = rows[habitInstanceId to date]
    override suspend fun upsert(progress: ScheduleCursorDailyProgress) { rows[progress.habitInstanceId to progress.date] = progress }
    override suspend fun getCompletedDates(habitInstanceId: Long) =
        rows.values.filter { it.habitInstanceId == habitInstanceId && it.completed }.map { it.date }
}
```

Also add a minimal `insertIfAbsent`-free stub check: confirm `HabitInstanceDao`'s actual interface shape (it may have more methods than `getById`/`getAll`/`insertIfAbsent`) by reading `app/src/main/java/com/ziv/reminders/data/HabitInstanceDao.kt` before finalizing `FakeHabitInstanceDao` above — implement every method the real interface declares, matching this codebase's existing fakes' style (they always implement the full interface, never a subset).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.activity.ActivityViewModelTest"`
Expected: FAIL — `ActivityDataSource`, `ActivityViewModel` don't exist yet (compile error).

- [ ] **Step 3: Write `ActivityViewModel` and `ActivityDataSource`**

`app/src/main/java/com/ziv/reminders/ui/activity/ActivityViewModel.kt`:
```kotlin
package com.ziv.reminders.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.ActivityDataSource
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.data.READING_HABIT_INSTANCE_ID
import com.ziv.reminders.data.ReadingSessionLog
import com.ziv.reminders.data.TANAKH_HABIT_INSTANCE_ID
import com.ziv.reminders.data.WeeklySummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ActivitySectionState(
    val streak: Int = 0,
    val totalCount: Int = 0,
    val completedDates: Set<LocalDate> = emptySet(),
)

data class ActivityUiState(
    val exercise: ActivitySectionState = ActivitySectionState(),
    val reading: ActivitySectionState = ActivitySectionState(),
    val tanakh: ActivitySectionState = ActivitySectionState(),
    val comboStreakThisWeek: Int = 0,
    val isLoaded: Boolean = false,
)

class ActivityViewModel(private val dataSource: ActivityDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()

            val exerciseInstance = dataSource.habitInstanceDao.getById(EXERCISE_HABIT_INSTANCE_ID)
            val readingInstance = dataSource.habitInstanceDao.getById(READING_HABIT_INSTANCE_ID)
            val tanakhInstance = dataSource.habitInstanceDao.getById(TANAKH_HABIT_INSTANCE_ID)

            val exerciseDates = exerciseInstance?.let { HabitStats.parseDates(dataSource.counterHabitRepository.completedDates(it)) } ?: emptySet()
            val readingDates = readingInstance?.let { HabitStats.parseDates(dataSource.timerHabitRepository.completedDates(it)) } ?: emptySet()
            val tanakhDates = tanakhInstance?.let { HabitStats.parseDates(dataSource.scheduleCursorRepository.completedDates(it)) } ?: emptySet()

            val exerciseStreak = exerciseInstance?.let { dataSource.habitEngine.currentStreak(it, today) } ?: 0
            val readingStreak = readingInstance?.let { dataSource.habitEngine.currentStreak(it, today) } ?: 0
            val tanakhStreak = tanakhInstance?.let { dataSource.habitEngine.currentStreak(it, today) } ?: 0

            val weekly = WeeklySummary.compute(exerciseDates, readingDates, tanakhDates, today)

            _uiState.value = ActivityUiState(
                exercise = ActivitySectionState(exerciseStreak, HabitStats.totalCount(exerciseDates), exerciseDates),
                reading = ActivitySectionState(readingStreak, HabitStats.totalCount(readingDates), readingDates),
                tanakh = ActivitySectionState(tanakhStreak, HabitStats.totalCount(tanakhDates), tanakhDates),
                comboStreakThisWeek = weekly.comboStreak,
                isLoaded = true,
            )
        }
    }

    suspend fun readingSessionsForDate(date: LocalDate): List<ReadingSessionLog> {
        val instance = dataSource.habitInstanceDao.getById(READING_HABIT_INSTANCE_ID) ?: return emptyList()
        return dataSource.timerHabitRepository.sessionsForDate(instance, date)
    }

    fun deleteReadingSession(session: ReadingSessionLog) {
        viewModelScope.launch {
            dataSource.timerHabitRepository.deleteSession(session)
            refresh()
        }
    }

    fun undoTanakhMarkRead(date: LocalDate) {
        viewModelScope.launch {
            val instance = dataSource.habitInstanceDao.getById(TANAKH_HABIT_INSTANCE_ID) ?: return@launch
            dataSource.scheduleCursorRepository.undoMarkRead(instance, date)
            refresh()
        }
    }

    companion object {
        fun factory(dataSource: ActivityDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = ActivityViewModel(dataSource) as T
            }
    }
}
```

Modify `app/src/main/java/com/ziv/reminders/data/AppContainer.kt` (full file):
```kotlin
package com.ziv.reminders.data

import android.content.Context
import androidx.room.Room
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
    override val timerHabitRepository: TimerHabitRepository by lazy { TimerHabitRepository(timerDailyProgressDao, SystemClock, readingSessionLogDao) }
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
 * directly instead of duplicating that path (see Task 6). */
interface ActivityDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val timerHabitRepository: TimerHabitRepository
    val scheduleCursorRepository: ScheduleCursorRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.activity.ActivityViewModelTest"`
Expected: PASS (1 test)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/activity/ActivityViewModel.kt app/src/main/java/com/ziv/reminders/data/AppContainer.kt app/src/test/java/com/ziv/reminders/ui/activity/ActivityViewModelTest.kt app/src/test/java/com/ziv/reminders/ui/exercise/ActivityTestFakes.kt
git commit -m "Add ActivityViewModel and ActivityDataSource"
```

---

### Task 6: `ActivityScreen` UI + dashboard entry point + route removal

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/ui/activity/ActivityScreen.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseStatsScreen.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseViewModel.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/java/com/ziv/reminders/MainActivity.kt`
- Modify: `TODOS.md` (close the resolved retroactive-edit item, see Step 5.5)
- Test: `app/src/test/java/com/ziv/reminders/ui/exercise/ExerciseViewModelTest.kt`

**Interfaces:**
- Produces: `@Composable fun ActivityScreen(activityViewModel: ActivityViewModel, exerciseViewModel: ExerciseViewModel, onBack: () -> Unit)`; `@Composable fun ExerciseActivitySection(viewModel: ExerciseViewModel)` (renamed from the old top-level `ExerciseStatsScreen` composable); `DashboardScreen(viewModel, onOpenExercise, onOpenActivity: () -> Unit = {})`; `ExerciseViewModel.adjustSubCounterForDate(exerciseKey: String, date: LocalDate, delta: Int)`; `ExerciseUiState.totalCount: Int`. Consumed by `MainActivity`'s `NavHost`.

**Incorporates /autoplan review findings** (CEO cherry-pick + Design review, both applied before implementation rather than after):
1. **CEO cherry-pick: Exercise edit parity.** `SubCounterRepository.adjust(exerciseKey, date, delta)` already takes an arbitrary `LocalDate` (its "today" parameter name is misleading — it works for any date already), so no repository change is needed. `ExerciseViewModel` gains `adjustSubCounterForDate` (TDD'd below) so `SubCounterDetailDialog` can offer +/- edit controls for a past day, not just view them. **Corrected in a later /autoplan CEO pass:** this cherry-pick directly contradicted the Global Constraints line inherited from the original design ("stays view-only, unchanged") — that constraint has been corrected (see Global Constraints), and Step 5.5 below closes the `TODOS.md` P3 item this work resolves.
2. **Design finding (Medium/High): inconsistent stat semantics.** Exercise showed "This month" while Reading/Tanakh showed "Total" — same visual slot, different metric, easy to misread. Fix: `ExerciseUiState` gains a `totalCount` field (via `HabitStats.totalCount`, already built in Task 1) shown *alongside* the existing streak/month/record text, not replacing it — Exercise keeps its richer callouts AND gains the same "Total" line the other two sections show.
3. **Design finding (Critical): Reading's delete has no confirm, unlike Tanakh's undo confirm, on visually identical grids.** Fixed in `ReadingDayDetailDialog` below — delete now requires a confirm tap, matching Tanakh's caution level.
4. **Design finding (High): three different day-tap behaviors with no visual cue.** Fixed via a one-line caption under each section header in `ActivityScreen` below.
5. **Design finding (Medium): combo-streak banner is invisible most weeks, making the plan's headline feature undiscoverable.** Fixed — always shown, with a "0/7" fallback instead of hiding at zero.
6. **Design finding (Low): section order unmotivated.** It already matches the dashboard's own habit row order (Exercise/Reading/Tanakh) — this was implicit, not stated; a one-line code comment now says so.
7. **Design finding (High): no error handling in `ActivityViewModel.refresh()`.** Not fixed here — this matches the exact same gap already present in `DashboardViewModel.refresh()` and `ExerciseViewModel.refresh()` (100% of this codebase's ViewModels today), tracked as a single app-wide item in `TODOS.md` ("App-wide: no error handling around Room reads in ViewModels"). Fixing it only for this one new ViewModel while leaving the other two as-is would be *less* consistent than the current uniform gap — this plan updates that TODOS.md item to note `ActivityViewModel` now shares it, rather than patching one ViewModel in isolation.

Everything below except the new `ExerciseViewModel` step is unverified by unit tests — matches this codebase's established precedent (no Compose UI test tooling anywhere in this project); verified on-device in Task 8.

- [ ] **Step 0: Add `ExerciseViewModel.adjustSubCounterForDate` and `totalCount` (TDD)**

Add to `app/src/test/java/com/ziv/reminders/ui/exercise/ExerciseViewModelTest.kt`, inside the existing `ExerciseViewModelTest` class (read the file first to match its exact fake-dataSource setup style):
```kotlin
    @Test
    fun adjustSubCounterForDate_pastDate_adjustsThatDatesValue_notToday() = runTest {
        val viewModel = ExerciseViewModel(testDataSource) // use this file's existing fake-construction helper
        val pastDate = LocalDate.now().minusDays(3)

        viewModel.adjustSubCounterForDate(EXERCISE_KEY_PUSHUP, pastDate, +2)

        val values = viewModel.subCounterValuesForDate(pastDate)
        assertEquals(EXERCISE_SUB_COUNTER_DEFAULT + 2, values[EXERCISE_KEY_PUSHUP])
    }

    @Test
    fun refresh_populatesTotalCount() = runTest {
        // Reuses this file's existing fake completed-dates setup for the streak/month tests —
        // totalCount should equal HabitStats.totalCount(completedDates), the same value already
        // exercised by HabitStatsTest.
        val viewModel = ExerciseViewModel(testDataSource)
        viewModel.refresh()
        assertEquals(viewModel.uiState.value.completedDates.size, viewModel.uiState.value.totalCount)
    }
```

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.exercise.ExerciseViewModelTest"`
Expected: FAIL — `adjustSubCounterForDate` and `ExerciseUiState.totalCount` don't exist yet (compile error).

Modify `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseViewModel.kt` — add `totalCount` to `ExerciseUiState`, populate it in `refresh()`, and add the new method:
```kotlin
data class ExerciseUiState(
    val current: Int = 0,
    val goal: Int = 5,
    val completed: Boolean = false,
    val streak: Int = 0,
    val isNewStreakRecord: Boolean = false,
    val monthCount: Int = 0,
    val isNewMonthRecord: Boolean = false,
    val totalCount: Int = 0,
    val subCounters: Map<String, Int> = ALL_EXERCISE_KEYS.associateWith { EXERCISE_SUB_COUNTER_DEFAULT },
    val completedDates: Set<LocalDate> = emptySet(),
    val isLoaded: Boolean = false,
)
```
In `refresh()`, add `totalCount = HabitStats.totalCount(dates),` to the `ExerciseUiState(...)` constructor call (alongside the existing `isNewStreakRecord`/`monthCount`/etc. fields — `dates` is already computed there).

Add this method alongside the existing `adjustSubCounter`:
```kotlin
    // Unlike adjustSubCounter (always today, called from the live counter screen), this takes
    // an explicit date — used by SubCounterDetailDialog to edit a past day's value. No new
    // repository method needed: SubCounterRepository.adjust already takes an arbitrary
    // LocalDate despite its parameter being named "today".
    fun adjustSubCounterForDate(exerciseKey: String, date: LocalDate, delta: Int) {
        viewModelScope.launch {
            dataSource.subCounterRepository.adjust(exerciseKey, date, delta)
            refresh()
        }
    }
```

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.exercise.ExerciseViewModelTest"`
Expected: PASS (existing tests + 2 new)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

Commit:
```bash
git add app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseViewModel.kt app/src/test/java/com/ziv/reminders/ui/exercise/ExerciseViewModelTest.kt
git commit -m "Add ExerciseViewModel.adjustSubCounterForDate and totalCount"
```

- [ ] **Step 1: Rename `ExerciseStatsScreen` into an embeddable section, with edit controls and totalCount**

Full `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseStatsScreen.kt` (after this task; Task 1 already replaced the local `HeatmapGrid` with the shared import):
```kotlin
package com.ziv.reminders.ui.exercise

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.ui.activity.HeatmapGrid
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Embeddable Exercise section for the unified Activity screen — this used to be the standalone
 * "exerciseStats" NavHost destination, just reached via Activity instead of its own route. The
 * back button/title row that used to live here now belongs to ActivityScreen's shared top bar.
 *
 * Corrected during /autoplan review: SubCounterDetailDialog now supports +/- editing a past
 * day's value (not view-only), and this section now shows a "Total" line alongside its existing
 * streak/month/record callouts — matching Reading/Tanakh's stat vocabulary instead of showing a
 * differently-labeled number in the same visual slot.
 */
@Composable
fun ExerciseActivitySection(viewModel: ExerciseViewModel) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsState()
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    if (!uiState.isLoaded) return

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
        Text(
            text = "Total: ${uiState.totalCount} day${if (uiState.totalCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyLarge,
        )
    }

    Spacer(Modifier.height(16.dp))

    if (uiState.completedDates.isEmpty()) {
        EmptyState()
    } else {
        HeatmapGrid(dates = uiState.completedDates, today = today, onDayClick = { day -> selectedDate = day })
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
private fun SubCounterDetailDialog(viewModel: ExerciseViewModel, date: LocalDate, onDismiss: () -> Unit) {
    var values by remember(date) { mutableStateOf<Map<String, Int?>?>(null) }
    // Keyed on (date, uiState.subCounters) not just (date) — the new +/- edit buttons below call
    // adjustSubCounterForDate, which triggers refresh() and updates uiState, but without this
    // extra key the dialog's own `values` (fetched once on open) would go stale after an edit
    // within the same dialog session instead of reflecting the just-applied change.
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(date, uiState.subCounters) { values = viewModel.subCounterValuesForDate(date) }

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
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("$label: ${current[key] ?: "—"}", modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.adjustSubCounterForDate(key, date, -1) }) { Text("−") }
                            TextButton(onClick = { viewModel.adjustSubCounterForDate(key, date, +1) }) { Text("+") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
```

- [ ] **Step 2: Write `ActivityScreen`**

`app/src/main/java/com/ziv/reminders/ui/activity/ActivityScreen.kt`:
```kotlin
package com.ziv.reminders.ui.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.ziv.reminders.data.ReadingSessionLog
import com.ziv.reminders.ui.exercise.ExerciseActivitySection
import com.ziv.reminders.ui.exercise.ExerciseViewModel
import com.ziv.reminders.ui.exercise.GoalGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ActivityScreen(activityViewModel: ActivityViewModel, exerciseViewModel: ExerciseViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { activityViewModel.refresh() }
    val uiState by activityViewModel.uiState.collectAsState()
    // Corrected during /autoplan design review: ExerciseActivitySection independently triggers
    // and gates on exerciseViewModel's own isLoaded (via its own LaunchedEffect(Unit)), so
    // whichever of the two ViewModels' Room reads finishes first pops its section in while the
    // other stays blank — visible layout jank on every screen open. Reading exerciseUiState
    // here too and folding it into this screen's single top-level gate means the whole screen
    // appears atomically once both are ready, instead of section-by-section.
    val exerciseUiState by exerciseViewModel.uiState.collectAsState()
    val today = remember { LocalDate.now() }
    var selectedReadingDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedTanakhDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Activity", style = MaterialTheme.typography.titleMedium)
        }

        if (!uiState.isLoaded || !exerciseUiState.isLoaded) return@Column

        // Combo-streak surfaced here AND in the weekly-summary notification (Task 7) — this
        // resolves the design doc's "Reviewer Concern" about ambiguous placement by putting it
        // in both places rather than picking one.
        //
        // Corrected during /autoplan design review: always shown (not hidden at 0), with a
        // "0/7" fallback — the original conditional made this the headline feature of the whole
        // plan invisible on most weeks (any week without a same-day triple-hit), which is the
        // majority case for a realistic mixed-consistency user, making the feature undiscoverable.
        // Corrected during /autoplan design review: "/7" implied 7/7 was the achievable ceiling,
        // but Reading/Tanakh run on a 5-day (Sun-Thu) mask while Exercise runs all 7 — for any
        // user with that real seeded config, all-three-in-one-day can only happen on at most 5
        // of the 7 days, so the banner's own headline metric would structurally always look like
        // a shortfall. Dropped the "/N" denominator entirely rather than compute an
        // achievable-ceiling number (which would need each habit's enabledDaysMask threaded into
        // WeeklySummary — more invasive, and still wrong the moment a mask ever changes).
        Text(
            text = "Hit all 3 habits on ${uiState.comboStreakThisWeek} day${if (uiState.comboStreakThisWeek == 1) "" else "s"} this week!",
            style = MaterialTheme.typography.bodyLarge,
            color = GoalGreen,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )

        // Section order matches the dashboard's own row order (Exercise/Reading/Tanakh) —
        // this was already true but unstated; noted here per /autoplan design review so a
        // future editor doesn't read it as arbitrary implementation order.
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text("Exercise", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SectionCaption("Tap a day to review this day's reps")
            ExerciseActivitySection(viewModel = exerciseViewModel)

            HabitStatsSummary("Reading", uiState.reading)
            SectionCaption("Tap a day to review or delete a session")
            if (uiState.reading.completedDates.isEmpty()) {
                EmptySectionState()
            } else {
                HeatmapGrid(dates = uiState.reading.completedDates, today = today, onDayClick = { selectedReadingDate = it })
            }

            HabitStatsSummary("Tanakh", uiState.tanakh)
            SectionCaption("Tap today's cell to undo — past days are view-only")
            if (uiState.tanakh.completedDates.isEmpty()) {
                EmptySectionState()
            } else {
                HeatmapGrid(dates = uiState.tanakh.completedDates, today = today, onDayClick = { selectedTanakhDate = it })
            }
        }
    }

    selectedReadingDate?.let { date ->
        ReadingDayDetailDialog(viewModel = activityViewModel, date = date, onDismiss = { selectedReadingDate = null })
    }
    selectedTanakhDate?.let { date ->
        TanakhDayDetailDialog(viewModel = activityViewModel, date = date, today = today, onDismiss = { selectedTanakhDate = null })
    }
}

@Composable
private fun HabitStatsSummary(title: String, state: ActivitySectionState) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text("Streak: ${state.streak} day${if (state.streak == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge)
        Text("Total: ${state.totalCount} day${if (state.totalCount == 1) "" else "s"}", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EmptySectionState() {
    Text(
        text = "No history yet",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}

// Added per /autoplan design review (High finding: three genuinely different day-tap
// behaviors — view-only, session-delete, conditional-undo — on visually identical heatmap
// grids, with no cue distinguishing them before the user taps). One line per section states
// what a tap does before the user commits to it.
@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    )
}

@Composable
private fun ReadingDayDetailDialog(viewModel: ActivityViewModel, date: LocalDate, onDismiss: () -> Unit) {
    var sessions by remember(date) { mutableStateOf<List<ReadingSessionLog>?>(null) }
    // Tracks which session (if any) the user has tapped Delete on, pending confirmation —
    // added per /autoplan design review (Critical finding: this delete previously fired
    // immediately with no confirm step, while Tanakh's equally-destructive undo action
    // already required a confirm tap — two visually identical grids with different risk
    // profiles for a mis-tap). Now both require a confirm.
    var pendingDelete by remember(date) { mutableStateOf<ReadingSessionLog?>(null) }
    LaunchedEffect(date) { sessions = viewModel.readingSessionsForDate(date) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DateTimeFormatter.ISO_LOCAL_DATE)) },
        text = {
            val current = sessions
            when {
                current == null -> Text("Loading…")
                current.isEmpty() -> Text("No sessions logged this day")
                else -> Column {
                    current.forEach { session ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${session.durationSeconds / 60} min", modifier = Modifier.weight(1f))
                            TextButton(onClick = { pendingDelete = session }) { Text("Delete") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this session?") },
            text = { Text("${session.durationSeconds / 60} min logged on ${date.format(DateTimeFormatter.ISO_LOCAL_DATE)} — this can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReadingSession(session)
                    sessions = sessions?.minus(session)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TanakhDayDetailDialog(viewModel: ActivityViewModel, date: LocalDate, today: LocalDate, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(DateTimeFormatter.ISO_LOCAL_DATE)) },
        text = {
            // Tanakh has a single global cursor, not independent per-day state — undo only
            // makes sense for today's most recent mark, never an arbitrary past day (see
            // ScheduleCursorRepository.undoMarkRead's doc comment).
            if (date == today) {
                Text("Undo today's Tanakh reading?")
            } else {
                Text("Past days can't be edited — Tanakh tracks one running position, not independent daily entries.")
            }
        },
        confirmButton = {
            if (date == today) {
                TextButton(onClick = {
                    viewModel.undoTanakhMarkRead(date)
                    onDismiss()
                }) { Text("Undo") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = if (date == today) {
            { TextButton(onClick = onDismiss) { Text("Cancel") } }
        } else {
            null
        },
    )
}
```

- [ ] **Step 3: Add the dashboard's top bar entry point**

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
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
fun DashboardScreen(viewModel: DashboardViewModel, onOpenExercise: () -> Unit = {}, onOpenActivity: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()

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

- [ ] **Step 4: Wire `MainActivity`'s `NavHost`, remove the standalone `exerciseStats` route**

Full `app/src/main/java/com/ziv/reminders/MainActivity.kt`:
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
import com.ziv.reminders.ui.activity.ActivityScreen
import com.ziv.reminders.ui.activity.ActivityViewModel
import com.ziv.reminders.ui.dashboard.DashboardScreen
import com.ziv.reminders.ui.dashboard.DashboardViewModel
import com.ziv.reminders.ui.exercise.ExerciseCounterScreen
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
                val activityViewModel: ActivityViewModel = viewModel(factory = ActivityViewModel.factory(container))

                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") {
                        // Fires every time this destination re-enters composition —
                        // including after popping back from the Exercise/Activity flows, not
                        // just on cold start — so the dashboard's rows never show stale data.
                        LaunchedEffect(Unit) { dashboardViewModel.refresh() }
                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            onOpenExercise = { navController.navigate("exerciseCounter") },
                            onOpenActivity = { navController.navigate("activity") },
                        )
                    }
                    composable("exerciseCounter") {
                        ExerciseCounterScreen(
                            viewModel = exerciseViewModel,
                            onOpenStats = { navController.navigate("activity") },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("activity") {
                        ActivityScreen(
                            activityViewModel = activityViewModel,
                            exerciseViewModel = exerciseViewModel,
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

- [ ] **Step 5: Build and run the full test suite**

Run: `./gradlew.bat :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green — this task touches no logic layer, only Compose UI wiring, so no test count should change from Task 5's total)

- [ ] **Step 5.5: Close the resolved `TODOS.md` item**

Step 0 above (`ExerciseViewModel.adjustSubCounterForDate`) resolves the pre-existing `TODOS.md` item "Retroactive edit of a past day's rep counts" (P3) — it should no longer be listed as deferred. Read `TODOS.md`, find that item under `## Review` (or wherever it currently lives), and move it into the `## Completed` section, following the exact format of the 3 items already there from the Exercise-detail-screen plan (**Completed:** commit `<short-hash>` (date) — use the actual commit hash from Step 6 below once known, or come back and fill it in immediately after committing).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/activity/ActivityScreen.kt app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseStatsScreen.kt app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt app/src/main/java/com/ziv/reminders/MainActivity.kt TODOS.md
git commit -m "Add ActivityScreen, dashboard entry point, and remove standalone exerciseStats route"
```

---

### Task 7: Notification actions (Start/Snooze) + cross-habit weekly summary

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/scheduling/HabitScheduler.kt`
- Modify: `app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt`
- Modify: `app/src/main/java/com/ziv/reminders/notifications/HabitNotifications.kt`
- Modify: `app/src/main/java/com/ziv/reminders/RemindersApp.kt`
- Modify: `app/src/main/java/com/ziv/reminders/scheduling/BootReceiver.kt`
- Modify: `app/src/main/java/com/ziv/reminders/scheduling/RolloverReceiver.kt`
- Modify: `TODOS.md` (close the resolved weekly-summary item, see Step 10.5)
- Test: `app/src/test/java/com/ziv/reminders/scheduling/HabitSchedulerTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/scheduling/BootReceiverTest.kt` (count update only, see Step 10)
- Test: `app/src/test/java/com/ziv/reminders/scheduling/RolloverReceiverTest.kt` (count update only, see Step 10)

**Interfaces:**
- Produces: `HabitScheduler.ACTION_START_READING`, `HabitScheduler.ACTION_SNOOZE_READING`, `HabitScheduler.ACTION_WEEKLY_SUMMARY` (String constants); `HabitScheduler.scheduleSnooze(habitInstanceId: Long)`; `HabitScheduler.scheduleWeeklySummary(from: LocalDate)`; `HabitNotifications.buildReminderNotification` gains Reading-only Start/Snooze action buttons; `HabitNotifications.createWeeklySummaryChannel(context)`, `HabitNotifications.buildWeeklySummaryNotification(context, summary: WeeklyHabitCount)`.

No manifest changes needed — `HabitReminderReceiver` is already declared (`android:exported="false"`, no intent-filter) and is only ever reached via explicit `PendingIntent`s, exactly like its existing `ACTION_REMINDER`/rollover siblings.

- [ ] **Step 1: Write the failing `HabitScheduler` tests**

Add to `app/src/test/java/com/ziv/reminders/scheduling/HabitSchedulerTest.kt`, inside the existing `HabitSchedulerTest` class (e.g. after `scheduleRollover_schedulesOneAlarm`):
```kotlin
    @Test
    fun scheduleSnooze_schedulesExactlyOneAlarm() {
        val scheduler = HabitScheduler(context)

        scheduler.scheduleSnooze(habitInstanceId = 2L)

        assertEquals(1, shadowOf(alarmManager).getScheduledAlarms().size)
    }

    @Test
    fun scheduleWeeklySummary_schedulesExactlyOneAlarm() {
        val scheduler = HabitScheduler(context)

        scheduler.scheduleWeeklySummary(from = LocalDate.of(2026, 7, 19)) // a Sunday

        assertEquals(1, shadowOf(alarmManager).getScheduledAlarms().size)
    }

    @Test
    fun scheduleWeeklySummary_calledTwice_updatesRatherThanDuplicates() {
        val scheduler = HabitScheduler(context)

        scheduler.scheduleWeeklySummary(from = LocalDate.of(2026, 7, 19))
        scheduler.scheduleWeeklySummary(from = LocalDate.of(2026, 7, 20)) // called again the next day, matching daily rollover re-invocation

        // FLAG_UPDATE_CURRENT + a fixed request code means the second call replaces the first
        // alarm's trigger time rather than adding a duplicate — mirrors scheduleRollover's own
        // idempotent daily re-scheduling.
        assertEquals(1, shadowOf(alarmManager).getScheduledAlarms().size)
    }

    @Test
    fun scheduleWeeklySummary_fromIsAlreadyASundayInTheFuture_schedulesThatSameSunday_notAWeekLater() {
        val scheduler = HabitScheduler(context)
        // Constructed relative to LocalDate.now() rather than hardcoded, so this test doesn't
        // depend on which real date it happens to run on (HabitScheduler has no injectable
        // clock — same limitation scheduleRemindersForToday's own test documents). Landing a
        // full extra week past the nearest Sunday guarantees that Sunday's 9am is unambiguously
        // in the future no matter what hour "now" actually is.
        val today = LocalDate.now()
        val daysUntilSunday = (7 - today.dayOfWeek.value % 7) % 7
        val futureSunday = today.plusDays((daysUntilSunday + 7).toLong())

        // Regression guard for the bug this task fixed: calling scheduleWeeklySummary with a
        // Sunday as `from` (exactly what happens on Sunday's own midnight rollover self-heal)
        // must schedule THAT Sunday, not silently jump a week ahead and never fire on the
        // Sunday it was actually supposed to.
        scheduler.scheduleWeeklySummary(from = futureSunday)

        val scheduled = shadowOf(alarmManager).getScheduledAlarms().single()
        val expectedTriggerAt = futureSunday.atTime(9, 0)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expectedTriggerAt, scheduled.triggerAtTime)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.scheduling.HabitSchedulerTest"`
Expected: FAIL — `scheduleSnooze`/`scheduleWeeklySummary` don't exist yet (compile error).

- [ ] **Step 3: Modify `HabitScheduler`**

Full `app/src/main/java/com/ziv/reminders/scheduling/HabitScheduler.kt`:
```kotlin
package com.ziv.reminders.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.isEnabledDay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Wraps AlarmManager, generalized to take a habitInstanceId so one receiver serves every
 * habit instance regardless of kind. Always inexact (setWindow, never setExact...) — see
 * Global Constraints.
 */
class HabitScheduler(private val context: Context) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    fun scheduleRemindersForToday(date: LocalDate, instance: HabitInstance) {
        if (!isEnabledDay(date, instance.enabledDaysMask)) return
        for (hour in REMINDER_HOURS) {
            val triggerAt = epochMillisAt(date, hour)
            if (triggerAt <= System.currentTimeMillis()) continue
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_LENGTH_MS, reminderPendingIntent(instance.id, hour)
            )
        }
    }

    fun scheduleRollover(from: LocalDate) {
        val nextMidnight = epochMillisAt(from.plusDays(1), hour = 0, minute = 1)
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, nextMidnight, WINDOW_LENGTH_MS, rolloverPendingIntent())
    }

    /** Fires the normal reminder path (ACTION_REMINDER) 15 minutes from now — reuses
     * reminderPendingIntent with a request-code-only "hour" slot (SNOOZE_REQUEST_HOUR_SLOT,
     * outside the real 9-13 range) purely to keep this alarm's request code distinct from the
     * hourly ones for the same instance; the receiver's handling doesn't depend on which hour
     * value was used to build the request code. */
    fun scheduleSnooze(habitInstanceId: Long) {
        val triggerAt = System.currentTimeMillis() + SNOOZE_DELAY_MS
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_LENGTH_MS,
            reminderPendingIntent(habitInstanceId, hour = SNOOZE_REQUEST_HOUR_SLOT),
        )
    }

    /** Schedules the upcoming Sunday 09:00 alarm — called daily from the same self-heal sites as
     * scheduleRollover (RemindersApp.onCreate, BootReceiver, RolloverReceiver), so it recomputes
     * the target Sunday every day and idempotently re-arms the same alarm (FLAG_UPDATE_CURRENT +
     * fixed request code) until that Sunday passes, then automatically advances — same pattern
     * scheduleRemindersForToday/scheduleRollover already use.
     *
     * candidateSunday is `from` itself when `from` is already a Sunday — critical, because this
     * runs on Sunday's own 00:01 rollover too. An earlier version always jumped to `from + 7`
     * whenever `from` was a Sunday (treating "0 days until Sunday" as "no, next week"), which
     * meant Sunday's own midnight rollover self-heal would silently overwrite Saturday
     * rollover's correctly-scheduled *today* 9am alarm with next week's date every single week —
     * the notification would never actually fire. The `<=` now-check below is what actually
     * decides "today" vs. "next week", not the day-of-week arithmetic alone. */
    fun scheduleWeeklySummary(from: LocalDate) {
        val daysUntilSunday = (7 - from.dayOfWeek.value % 7) % 7
        val candidateSunday = from.plusDays(daysUntilSunday.toLong())
        val candidateTriggerAt = epochMillisAt(candidateSunday, hour = 9)
        val triggerAt = if (candidateTriggerAt <= System.currentTimeMillis()) {
            epochMillisAt(candidateSunday.plusDays(7), hour = 9)
        } else {
            candidateTriggerAt
        }
        alarmManager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, WINDOW_LENGTH_MS, weeklySummaryPendingIntent())
    }

    private fun epochMillisAt(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun reminderPendingIntent(habitInstanceId: Long, hour: Int): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .putExtra(EXTRA_HABIT_INSTANCE_ID, habitInstanceId)
        // Unique request code per (instance, hour) so different instances/hours never collide.
        val requestCode = (habitInstanceId * 100 + hour).toInt()
        return PendingIntent.getBroadcast(
            context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun rolloverPendingIntent(): PendingIntent {
        val intent = Intent(context, RolloverReceiver::class.java).setAction(ACTION_ROLLOVER)
        return PendingIntent.getBroadcast(
            context, ROLLOVER_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun weeklySummaryPendingIntent(): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java).setAction(ACTION_WEEKLY_SUMMARY)
        return PendingIntent.getBroadcast(
            context, WEEKLY_SUMMARY_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        val REMINDER_HOURS = listOf(9, 10, 11, 12, 13)
        const val WINDOW_LENGTH_MS = 15 * 60 * 1000L
        const val SNOOZE_DELAY_MS = 15 * 60 * 1000L
        // Outside the real 9-13 reminder-hour range — used only for request-code uniqueness.
        const val SNOOZE_REQUEST_HOUR_SLOT = 99
        // Negative, guaranteed never to collide with a (habitInstanceId * 100 + hour) request code.
        const val ROLLOVER_REQUEST_CODE = -1
        const val WEEKLY_SUMMARY_REQUEST_CODE = -2
        const val ACTION_REMINDER = "com.ziv.reminders.action.REMINDER"
        const val ACTION_ROLLOVER = "com.ziv.reminders.action.ROLLOVER"
        const val ACTION_START_READING = "com.ziv.reminders.action.START_READING"
        const val ACTION_SNOOZE_READING = "com.ziv.reminders.action.SNOOZE_READING"
        const val ACTION_WEEKLY_SUMMARY = "com.ziv.reminders.action.WEEKLY_SUMMARY"
        const val EXTRA_HABIT_INSTANCE_ID = "com.ziv.reminders.extra.HABIT_INSTANCE_ID"
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.scheduling.HabitSchedulerTest"`
Expected: PASS (7 tests — 3 existing + 4 new)

- [ ] **Step 5: Write the failing `HabitReminderReceiver` tests**

Add to `app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt`, inside the existing `HabitReminderReceiverTest` class (read the file first to match its exact `dispatch`/setup helper style before adding — the two new tests below assume the same Robolectric `Room.inMemoryDatabaseBuilder` + `registerReceiver`/`sendBroadcast`/`shadowOf(Looper...).idle()` pattern the existing tests already use):
```kotlin
    @Test
    fun onReceive_actionStartReading_startsTimerServiceViaForegroundServiceStart() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(id = 2L, kind = "TIMER", name = "Reading", enabledDaysMask = 0b0011111, notificationTitle = "t", notificationBody = "b", counterGoal = null, timerTargetSeconds = 900)
        )

        val receiver = HabitReminderReceiver()
        context.registerReceiver(receiver, IntentFilter(HabitScheduler.ACTION_START_READING), Context.RECEIVER_NOT_EXPORTED)
        context.sendBroadcast(
            Intent(HabitScheduler.ACTION_START_READING).putExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, 2L)
        )
        shadowOf(Looper.getMainLooper()).idle()

        val nextService = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).nextStartedService
        assertEquals(com.ziv.reminders.service.TimerService.ACTION_START, nextService?.action)
        assertEquals(2L, nextService?.getLongExtra(com.ziv.reminders.service.TimerService.EXTRA_HABIT_INSTANCE_ID, -1L))
    }

    @Test
    fun onReceive_actionWeeklySummary_someActivityThisWeek_postsAWeeklySummaryNotification() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        // At least one completed day this week — otherwise the all-zero suppression guard
        // (added per /autoplan CEO review) would make this test indistinguishable from the
        // suppression test below.
        db.counterDailyProgressDao().upsert(
            com.ziv.reminders.data.CounterDailyProgress(EXERCISE_HABIT_INSTANCE_ID, LocalDate.now().toString(), 5, true)
        )

        val receiver = HabitReminderReceiver()
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.counterHabitRepositoryOverride = CounterHabitRepository(db.counterDailyProgressDao())
        receiver.timerHabitRepositoryOverride = com.ziv.reminders.data.TimerHabitRepository(db.timerDailyProgressDao(), com.ziv.reminders.data.SystemClock)
        receiver.scheduleCursorRepositoryOverride = com.ziv.reminders.data.ScheduleCursorRepository(db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList())
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        context.registerReceiver(receiver, IntentFilter(HabitScheduler.ACTION_WEEKLY_SUMMARY), Context.RECEIVER_NOT_EXPORTED)
        context.sendBroadcast(Intent(HabitScheduler.ACTION_WEEKLY_SUMMARY))
        shadowOf(Looper.getMainLooper()).idle()
        testScheduler.advanceUntilIdle()

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, shadowOf(manager).size())
    }

    @Test
    fun onReceive_actionWeeklySummary_noActivityAtAll_suppressesTheNotification() = runTest {
        // Regression test for the /autoplan CEO cherry-pick: a fresh install (empty DB) must
        // never post "Exercise 0/7 · Reading 0/7 · Tanakh 0/7" — a useless nag.
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()

        val receiver = HabitReminderReceiver()
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.counterHabitRepositoryOverride = CounterHabitRepository(db.counterDailyProgressDao())
        receiver.timerHabitRepositoryOverride = com.ziv.reminders.data.TimerHabitRepository(db.timerDailyProgressDao(), com.ziv.reminders.data.SystemClock)
        receiver.scheduleCursorRepositoryOverride = com.ziv.reminders.data.ScheduleCursorRepository(db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), emptyList())
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        context.registerReceiver(receiver, IntentFilter(HabitScheduler.ACTION_WEEKLY_SUMMARY), Context.RECEIVER_NOT_EXPORTED)
        context.sendBroadcast(Intent(HabitScheduler.ACTION_WEEKLY_SUMMARY))
        shadowOf(Looper.getMainLooper()).idle()
        testScheduler.advanceUntilIdle()

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(0, shadowOf(manager).size())
    }
```

- [ ] **Step 6: Run test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.scheduling.HabitReminderReceiverTest"`
Expected: FAIL — `ACTION_START_READING`, `ACTION_WEEKLY_SUMMARY`, and the new override fields don't exist yet (compile error).

- [ ] **Step 7: Modify `HabitReminderReceiver`**

Full `app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt`:
```kotlin
package com.ziv.reminders.scheduling

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ziv.reminders.RemindersApp
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.EvaluatorEscalationDao
import com.ziv.reminders.data.HabitInstanceDao
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.READING_HABIT_INSTANCE_ID
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.data.TANAKH_HABIT_INSTANCE_ID
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.data.WeeklySummary
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.notifications.HabitNotifications
import com.ziv.reminders.service.TimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Fired by AlarmManager for four purposes, dispatched by intent.action: the hourly reminder
 * (ACTION_REMINDER, including snooze-rescheduled re-firings — a no-op if today is already
 * completed or already escalated), Reading's notification Start button (ACTION_START_READING),
 * Reading's notification Snooze button (ACTION_SNOOZE_READING), and the weekly cross-habit
 * summary (ACTION_WEEKLY_SUMMARY). */
class HabitReminderReceiver : BroadcastReceiver() {

    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var habitInstanceDaoOverride: HabitInstanceDao? = null
    internal var habitEngineOverride: HabitEngine? = null
    internal var evaluatorEscalationDaoOverride: EvaluatorEscalationDao? = null
    internal var habitSchedulerOverride: HabitScheduler? = null
    internal var counterHabitRepositoryOverride: CounterHabitRepository? = null
    internal var timerHabitRepositoryOverride: TimerHabitRepository? = null
    internal var scheduleCursorRepositoryOverride: ScheduleCursorRepository? = null
    internal var scopeOverride: CoroutineScope? = null

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            HabitScheduler.ACTION_START_READING -> handleStartReading(context, intent)
            HabitScheduler.ACTION_SNOOZE_READING -> handleSnoozeReading(context, intent)
            HabitScheduler.ACTION_WEEKLY_SUMMARY -> handleWeeklySummary(context)
            else -> handleReminder(context, intent)
        }
    }

    private fun handleReminder(context: Context, intent: Intent) {
        val habitInstanceId = intent.getLongExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, -1L)
        if (habitInstanceId == -1L) return
        val pendingResult = goAsync()
        // Each override is checked via ?: BEFORE the RemindersApp cast, not a separate eager
        // `val container = (context.applicationContext as RemindersApp)...` line — that would
        // evaluate the cast unconditionally, throwing ClassCastException in every test even
        // when overrides are provided (Robolectric's application context isn't a RemindersApp
        // instance). Mirrors ReadBook's NudgeReceiver exactly, which relies on this same
        // short-circuiting for the same reason.
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

    private fun handleStartReading(context: Context, intent: Intent) {
        val habitInstanceId = intent.getLongExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, -1L)
        if (habitInstanceId == -1L) return
        // Converges with the in-app start path (DashboardViewModel.onToggleTimer) instead of
        // calling TimerHabitRepository directly — a direct repository call would leave a
        // running DB session with no foreground service and no ongoing ticking notification.
        ContextCompat.startForegroundService(
            context,
            Intent(context, TimerService::class.java)
                .setAction(TimerService.ACTION_START)
                .putExtra(TimerService.EXTRA_HABIT_INSTANCE_ID, habitInstanceId),
        )
    }

    private fun handleSnoozeReading(context: Context, intent: Intent) {
        val habitInstanceId = intent.getLongExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, -1L)
        if (habitInstanceId == -1L) return
        // Dismiss the current reminder — otherwise it sits stale for the next 15 minutes even
        // though the user just acted on it.
        context.getSystemService(NotificationManager::class.java).cancel(habitInstanceId.toInt())
        val scheduler = habitSchedulerOverride
            ?: (context.applicationContext as RemindersApp).container.habitScheduler
        scheduler.scheduleSnooze(habitInstanceId)
    }

    private fun handleWeeklySummary(context: Context) {
        val pendingResult = goAsync()
        val dao = habitInstanceDaoOverride
            ?: (context.applicationContext as RemindersApp).container.habitInstanceDao
        val counterRepo = counterHabitRepositoryOverride
            ?: (context.applicationContext as RemindersApp).container.counterHabitRepository
        val timerRepo = timerHabitRepositoryOverride
            ?: (context.applicationContext as RemindersApp).container.timerHabitRepository
        val cursorRepo = scheduleCursorRepositoryOverride
            ?: (context.applicationContext as RemindersApp).container.scheduleCursorRepository
        val scope = scopeOverride ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val date = today()
                val exercise = dao.getById(EXERCISE_HABIT_INSTANCE_ID)
                val reading = dao.getById(READING_HABIT_INSTANCE_ID)
                val tanakh = dao.getById(TANAKH_HABIT_INSTANCE_ID)
                val exerciseDates = exercise?.let { HabitStats.parseDates(counterRepo.completedDates(it)) } ?: emptySet()
                val readingDates = reading?.let { HabitStats.parseDates(timerRepo.completedDates(it)) } ?: emptySet()
                val tanakhDates = tanakh?.let { HabitStats.parseDates(cursorRepo.completedDates(it)) } ?: emptySet()
                val summary = WeeklySummary.compute(exerciseDates, readingDates, tanakhDates, date)

                // Added per /autoplan CEO review: a fresh install (or any week with no habit
                // activity at all) would otherwise post "Exercise 0/7 · Reading 0/7 · Tanakh
                // 0/7" every Sunday — a useless nag with no suppression.
                if (summary.exerciseDays == 0 && summary.readingDays == 0 && summary.tanakhDays == 0) return@launch

                HabitNotifications.createWeeklySummaryChannel(context)
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.notify(HabitNotifications.WEEKLY_SUMMARY_NOTIFICATION_ID, HabitNotifications.buildWeeklySummaryNotification(context, summary))
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

- [ ] **Step 8: Modify `HabitNotifications`**

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
import com.ziv.reminders.data.HabitKind
import com.ziv.reminders.data.WeeklyHabitCount
import com.ziv.reminders.scheduling.HabitReminderReceiver
import com.ziv.reminders.scheduling.HabitScheduler

/** One channel per HabitInstance for its reminder notification (not per-kind or shared) — see
 * Global Constraints. The ongoing Timer foreground notification gets its own second, low-
 * importance, silent per-instance channel. The cross-habit evaluator's escalated notification
 * gets a THIRD per-instance channel, high-importance. The weekly cross-habit summary gets one
 * more, app-wide (not per-instance, since it covers all three habits at once).
 *
 * This file references HabitReminderReceiver/HabitScheduler (the scheduling package) to build
 * the Start/Snooze action PendingIntents — a deliberate mutual reference, mirroring
 * HabitReminderReceiver's own existing import of this file the other direction; this app's
 * "no framework needed at this size" philosophy accepts this over introducing a third
 * mediating class for two small, already-coupled packages.
 */
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
        val builder = NotificationCompat.Builder(context, channelId(instance))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(instance.notificationTitle)
            .setContentText(instance.notificationBody)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        // Actionable Start/Snooze buttons are Reading (Timer-kind)-specific — ReadBook's own
        // nudge notification never had these for its Bible-reading habit either.
        if (instance.kind == HabitKind.TIMER.name) {
            builder
                .addAction(0, "Start", startReadingPendingIntent(context, instance.id))
                .addAction(0, "Snooze 15m", snoozeReadingPendingIntent(context, instance.id))
        }
        return builder.build()
    }

    private fun startReadingPendingIntent(context: Context, habitInstanceId: Long): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java)
            .setAction(HabitScheduler.ACTION_START_READING)
            .putExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, habitInstanceId)
        return PendingIntent.getBroadcast(
            context, (habitInstanceId * 10 + 1).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun snoozeReadingPendingIntent(context: Context, habitInstanceId: Long): PendingIntent {
        val intent = Intent(context, HabitReminderReceiver::class.java)
            .setAction(HabitScheduler.ACTION_SNOOZE_READING)
            .putExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, habitInstanceId)
        return PendingIntent.getBroadcast(
            context, (habitInstanceId * 10 + 2).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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

    const val WEEKLY_SUMMARY_CHANNEL_ID = "weekly_summary"
    // Far outside any per-instance id (1,2,3) or timer id (1000,2000,3000) range.
    const val WEEKLY_SUMMARY_NOTIFICATION_ID = -100

    fun createWeeklySummaryChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(WEEKLY_SUMMARY_CHANNEL_ID, "Weekly summary", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun buildWeeklySummaryNotification(context: Context, summary: WeeklyHabitCount): Notification {
        // Plain tap-to-open — no action buttons, unlike the Reading reminder above (see this
        // feature's design doc, "the weekly summary is a plain notification, not an
        // action-dispatch case").
        val activityIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, WEEKLY_SUMMARY_NOTIFICATION_ID, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // "/7" per-habit is a literal 7-calendar-day window count and stays accurate regardless
        // of enabledDaysMask. The combo-streak clause deliberately drops any "/N" denominator —
        // see ActivityScreen's matching correction for why 7 is not an achievable ceiling here.
        val text = "Exercise ${summary.exerciseDays}/7 · Reading ${summary.readingDays}/7 · Tanakh ${summary.tanakhDays}/7" +
            if (summary.comboStreak > 0) " · All three ${summary.comboStreak} day${if (summary.comboStreak == 1) "" else "s"}!" else ""
        return NotificationCompat.Builder(context, WEEKLY_SUMMARY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Your week in Reminders")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }
}
```

- [ ] **Step 9: Wire the weekly-summary alarm into the existing self-heal sites**

Modify `app/src/main/java/com/ziv/reminders/RemindersApp.kt` — add one line after the existing `container.habitScheduler.scheduleRollover(from = today)` call:
```kotlin
                container.habitScheduler.scheduleRollover(from = today)
                container.habitScheduler.scheduleWeeklySummary(from = today)
                EscalationWorker.ensureScheduled(this@RemindersApp)
```

Modify `app/src/main/java/com/ziv/reminders/scheduling/BootReceiver.kt` — add one line after the existing `scheduler.scheduleRollover(from = date)` call:
```kotlin
                    scheduler.scheduleRollover(from = date)
                    scheduler.scheduleWeeklySummary(from = date)
```

Modify `app/src/main/java/com/ziv/reminders/scheduling/RolloverReceiver.kt` — add one line after the existing `scheduler.scheduleRollover(from = today)` call:
```kotlin
                scheduler.scheduleRollover(from = today)
                scheduler.scheduleWeeklySummary(from = today)
```

- [ ] **Step 10: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.scheduling.HabitReminderReceiverTest"`
Expected: PASS (existing tests + 3 new)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: FAIL at this point — verified in advance (read both files): `BootReceiverTest.onReceive_bootCompleted_schedulesTodaysRemindersForEveryInstance_andRollover` (line 59) and `RolloverReceiverTest`'s equivalent test (line 57) both assert `assertEquals(6, shadowOf(alarmManager).getScheduledAlarms().size)` — 5 hourly reminders + 1 rollover alarm. `scheduleWeeklySummary` always schedules exactly one alarm unconditionally (no enabled-day-style guard, unlike `scheduleRemindersForToday`), so both counts become 7 regardless of which real-world day the test happens to run on. Update both — **corrected during /autoplan Eng review: assert on the added alarm's actual target/action, not just the raw count**, since a bare count bump is a proxy that would also pass if the new alarm silently pointed at the wrong receiver or action:

```bash
# app/src/test/java/com/ziv/reminders/scheduling/BootReceiverTest.kt:59
# app/src/test/java/com/ziv/reminders/scheduling/RolloverReceiverTest.kt:57
```
Replace the single `assertEquals(6, ...)` line in each file with:
```kotlin
        val scheduled = shadowOf(alarmManager).getScheduledAlarms()
        assertEquals(7, scheduled.size)
        val weeklySummaryAlarms = scheduled.filter {
            shadowOf(it.operation).savedIntent.action == HabitScheduler.ACTION_WEEKLY_SUMMARY
        }
        assertEquals(1, weeklySummaryAlarms.size)
```

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 10.5: Close the resolved `TODOS.md` item**

This task resolves the pre-existing `TODOS.md` item "Weekly aggregate summary notification" (P2). Read `TODOS.md`, find that item, and move it into the `## Completed` section (same format as the other resolved items there), noting the commit hash once known.

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/scheduling/HabitScheduler.kt app/src/main/java/com/ziv/reminders/scheduling/HabitReminderReceiver.kt app/src/main/java/com/ziv/reminders/notifications/HabitNotifications.kt app/src/main/java/com/ziv/reminders/RemindersApp.kt app/src/main/java/com/ziv/reminders/scheduling/BootReceiver.kt app/src/main/java/com/ziv/reminders/scheduling/RolloverReceiver.kt app/src/test/java/com/ziv/reminders/scheduling/HabitSchedulerTest.kt app/src/test/java/com/ziv/reminders/scheduling/HabitReminderReceiverTest.kt app/src/test/java/com/ziv/reminders/scheduling/BootReceiverTest.kt app/src/test/java/com/ziv/reminders/scheduling/RolloverReceiverTest.kt TODOS.md
git commit -m "Add Reading Start/Snooze notification actions and cross-habit weekly summary"
```

---

### Task 8: On-device verification

**Files:** none (manual verification only)

- [ ] **Step 1: Install the debug build**

Run: `./gradlew.bat :app:installDebug`
Expected: `BUILD SUCCESSFUL`, app installed on the connected device.

- [ ] **Step 2: Verify the Activity entry point and all three sections render**

Open the app, tap the new Activity icon on the dashboard's top bar. Confirm:
- Exercise, Reading, and Tanakh sections all render with streak/total/heatmap (or empty state if no history yet).
- The combo-streak line ("Hit all 3 habits on N days this week!") is always visible, showing "0 days" on a fresh install rather than being hidden — this was a design-review fix (the original conditional made this feature undiscoverable most weeks; a later fix also dropped the original "/7" suffix since 7/7 is unreachable given Reading/Tanakh's 5-day mask).
- A one-line caption appears under each section's heading ("Tap a day to review...", etc.) — the design-review fix for undifferentiated day-tap behavior across the 3 sections.

- [ ] **Step 3: Verify Exercise's entry point repoint, route removal, and edit-parity dialog**

From the dashboard, tap Exercise → tap the stats icon inside `ExerciseCounterScreen`. Confirm it opens the same unified Activity screen (not a separate standalone stats screen), landing with the Exercise section visible, now showing a "Total" line alongside the existing streak/month text. Tap a heatmap day — confirm the dialog now shows +/- buttons next to each sub-counter value (the CEO-review Exercise-edit-parity addition) and that adjusting a past day's value updates it without affecting today's live counter on `ExerciseCounterScreen`.

- [ ] **Step 4: Verify Reading's session log and day-edit-with-confirm**

Start and stop a Reading session from the dashboard (tap the Reading row twice, with a few seconds between). Open Activity → tap today's cell in the Reading heatmap. Confirm the session (its duration) appears in the dialog, tapping "Delete" now opens a confirm step ("Delete this session? ... this can't be undone") rather than deleting immediately (the Critical design-review fix), and confirming removes it and updates the heatmap after closing/reopening.

- [ ] **Step 5: Verify Tanakh's undo**

Mark today's Tanakh chapter read from the dashboard. Open Activity → tap today's cell in the Tanakh heatmap. Confirm the dialog offers "Undo," and tapping it reverts the day (re-check the dashboard's Tanakh row shows the same chapter as before marking). Confirm tapping a past day's cell shows the "can't be edited" message with no Undo button.

- [ ] **Step 5b: Verify the weekly-summary suppression guard**

With a fresh install (or after clearing app data), manually trigger `adb shell am broadcast -a com.ziv.reminders.action.WEEKLY_SUMMARY -n com.ziv.reminders/.scheduling.HabitReminderReceiver` (requires the receiver to be reachable via adb broadcast — if `exported="false"` blocks this, verify instead via `adb logcat` that `handleWeeklySummary` was entered and no notification was posted) and confirm no weekly-summary notification appears when nothing has been completed this week. Then complete at least one habit today and repeat — confirm the notification now appears.

- [ ] **Step 6: Verify Reading's notification Start/Snooze actions**

```bash
adb shell dumpsys alarm | grep -i reminders
```
Confirm hourly reminder alarms are scheduled. To force a reminder notification for manual testing, use `adb shell cmd notification post` is not viable for actionable notifications with custom PendingIntents — instead, temporarily verify via logcat that tapping "Start" and "Snooze 15m" on an actual fired reminder notification (or by manipulating device time forward to the next reminder hour, if acceptable) triggers `TimerService.ACTION_START` (check `adb shell dumpsys activity services | grep TimerService`) and, for Snooze, that the notification is dismissed and a new one-shot alarm appears in `adb shell dumpsys alarm | grep -i reminders`.

- [ ] **Step 7: Verify no regressions in Exercise's existing behavior**

Increment Exercise's sub-counters and main counter from `ExerciseCounterScreen`; confirm values persist correctly and match what the Activity screen's Exercise section shows.

- [ ] **Step 8: Check logs for crashes across the whole session**

```bash
adb logcat -d | grep -E "FATAL|AndroidRuntime|com.ziv.reminders.*Exception"
```
Expected: no matches from this session's testing.

- [ ] **Step 9: Run the full test suite one final time**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 10: Report results**

Summarize what was verified on-device (Steps 2-7) and confirm no crashes (Step 8) before considering this plan complete. If any step reveals a regression or bug, stop and fix it (with a covering test) before proceeding to `finishing-a-development-branch`.

---

## Decision Audit Trail

Mode: SELECTIVE EXPANSION (feature enhancement on an existing system — context-dependent default, not asked since it was already the established mode for this repo's prior plan). DX Review (Phase 3.5) skipped — no developer-facing scope (personal consumer app, no API/CLI/SDK). Codex unavailable on this machine — all three phases below ran single-voice (fresh Claude subagent per phase) rather than dual-voice; degradation noted per phase.

| # | Phase | Decision | Classification | Principle | Rationale | Rejected |
|---|-------|----------|-----------------|-----------|-----------|----------|
| 1 | CEO | Keep Task 6's Exercise edit-parity work; fix the stale Global Constraints line instead of reverting the code | Mechanical | P1 (completeness) | Task 6 Step 0 was already fully implemented and TDD'd, not a stub; reverting working, tested code to match a stale constraint line is strictly worse than fixing the line | Reverting Task 6 Step 0 |
| 2 | CEO | Close the `TODOS.md` P3 (Exercise retroactive-edit) and P2 (weekly summary) items as part of Tasks 6/7's commits | Mechanical | P2 (boil lakes) | Both items become genuinely resolved once these tasks ship; leaving them listed as deferred would be stale-doc drift in a single-maintainer project | Leaving TODOS.md unchanged |
| 3 | CEO | Add a disambiguating doc comment on `comboStreak` rather than renaming it across ~8 call sites | Taste | P3 (pragmatic) / P5 (explicit) | A doc comment fixes the confusion at near-zero diff; a full rename touches Tasks 4/5/6/7 for a naming-only change | Renaming `comboStreak` everywhere |
| 4 | CEO | Accept the self-heal plumbing changes to `RemindersApp`/`BootReceiver`/`RolloverReceiver` for the weekly-summary alarm as proportionate, not excessive blast radius | Taste | P2 (boil lakes) | This extends the exact same self-heal pattern every other alarm in this app already uses — not a new risk category, just one more call at 3 already-existing sites | Building a separate, smaller-blast-radius scheduling mechanism |
| 5 | Design | Rewrite `HeatmapGrid` from `LazyVerticalGrid` to a plain `Column`/`Row` grid | Mechanical | P5 (explicit over clever) | `LazyVerticalGrid` with no height nested inside `Column.verticalScroll` is a guaranteed Compose runtime crash; a plain eager grid has zero nested-scroll risk and the dataset (~53 weeks × 7 cells) is small enough that eager rendering costs nothing | A fixed-height workaround (would create scroll-within-scroll UX) |
| 6 | Design | Unify `ActivityScreen`'s loading gate across both `activityViewModel` and `exerciseViewModel` | Mechanical | P5 (explicit) | Two independently-gated ViewModels pop their sections in at different times on every screen open — a real, avoidable jank with a 3-line fix | Leaving the two independent gates as-is |
| 7 | Design | Drop the "/7" denominator from the combo-streak copy (screen banner + notification), keep per-habit "/7" counts as-is | Mechanical | P5 (explicit) | Reading/Tanakh run a 5-day mask, so 7/7 is structurally unreachable for the combo metric specifically — the per-habit counts are legitimate literal 7-day-window counts and aren't misleading the same way | Computing an achievable-ceiling denominator from each habit's mask (more invasive, still wrong if a mask ever changes) |
| 8 | Design | Bake the `SubCounterDetailDialog` stale-value fix directly into the code block instead of leaving it as trailing prose | Mechanical | P5 (explicit) | Trailing "note: change X to Y" instructions are easy for an implementer to skim past; the plan's own convention (per writing-plans) is complete code in every step | Leaving it as a prose note |
| 9 | Eng | Correct "9 existing test files" → "8" (verified by grep) in 3 locations | Mechanical | P5 (explicit) | Factual accuracy; harmless either way for the nullable-default-null design itself, but a wrong count is a small trust-eroding error worth fixing when caught | — |
| 10 | Eng | Strengthen `BootReceiverTest`/`RolloverReceiverTest`'s count-only assertion to also check the added alarm's actual action/target | Taste | P1 (completeness) | A bare count bump is a weak proxy — it would also pass if the new alarm silently pointed at the wrong receiver; asserting the actual `PendingIntent`'s action closes that gap for near-zero extra cost | Leaving the count-only assertion (adequate but weaker) |

No User Challenges — nothing in this plan required overriding a direction you'd explicitly stated. No premise re-litigation — the design doc's premises were already confirmed during `/office-hours`'s 3-round review; this plan-level review found implementation-level issues, not scope/premise issues.

## GSTACK REVIEW REPORT

- **CEO phase:** single-voice (Claude subagent; Codex unavailable). 1 self-contradiction found and fixed (Exercise edit-parity vs. stale "view-only" constraint), 2 stale-`TODOS.md` closures added, 1 naming clarity fix, 1 blast-radius concern reviewed and accepted as proportionate.
- **Design phase:** single-voice (Claude subagent; Codex unavailable). 5 prior design-review fixes verified genuine. 1 CRITICAL runtime-crash bug found and fixed (`LazyVerticalGrid` nested in `verticalScroll`). 1 HIGH misleading-copy bug fixed (unreachable "/7" combo-streak denominator). 1 MEDIUM UX-jank bug fixed (unsynchronized dual-ViewModel loading gate). 1 minor process fix (baked-in vs. trailing-prose code fix).
- **Eng phase:** single-voice (Claude subagent; Codex unavailable). Room migration/schema verified consistent (index declaration matches migration SQL exactly). AlarmManager request-code space verified collision-free. Receiver goAsync()/lifecycle correctness verified. 1 factual miscount fixed (test-file count). 1 test-assertion strengthened (alarm target, not just count).
- **DX phase:** skipped — no developer-facing scope detected (personal consumer app).
- **Status:** APPROVED, pending your sign-off below.

Ready to merge: **Yes**, pending sign-off. All 10 decisions above were auto-decided per the 6 principles; none rise to a User Challenge or premise re-litigation. Two personal-project-appropriate residual notes carried into Task 8's on-device verification, not blocking: the app-wide "no error handling in ViewModels" gap (pre-existing, already tracked in `TODOS.md`) now also applies to `ActivityViewModel`, consistent with the other two ViewModels rather than a new gap; and the plan's blast radius (8 tasks, ~24 files, a schema migration, and self-heal plumbing changes) is larger than a typical single-feature plan but was reviewed and judged proportionate to what it delivers (6 ReadBook features + 2 scope extensions), not scope creep.
