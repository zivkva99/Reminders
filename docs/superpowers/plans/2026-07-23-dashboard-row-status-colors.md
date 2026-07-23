# Dashboard Row Status Colors Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each dashboard row a small colored status dot — red/orange/green for Tanakh (behind / today's chapter due / caught up), red/green for Exercise and Reading (not done today / done today).

**Architecture:** One new derived (non-persisted) boolean field on `HabitStatus.ScheduleCursorStatus` (`isDueToday`), computed in `ScheduleCursorRepository.todayStatus()` from the already-existing `ScheduleEntryStatus` branches. A small `HabitStatusDot` composable renders the color, wired into all 3 existing row composables in `DashboardScreen.kt` by wrapping the dot and the existing name/streak `Column` in a new inner `Row`.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Room (unaffected — no schema change).

## Global Constraints

- No new Room schema — `isDueToday` is derived fresh on every `todayStatus()` call, never persisted.
- No new DI wiring — no new repository/ViewModel methods, no new `AppContainer` members.
- `HabitStatus.ScheduleCursorStatus`'s new `isDueToday: Boolean` field has **no default value** — every existing construction site is updated explicitly in this plan, not silently defaulted.
- Task 1 (repository/data logic) is pure Kotlin with an established test precedent in this codebase — follow TDD (failing test first).
- Task 3 (Compose UI) has **no unit-test precedent** in this codebase — implement directly, verify via `assembleDebug` and on-device at the end, no new Compose UI tests.
- Personal side-project (builder mode) — build exactly what's specified below, no speculative generalization (e.g. no shared cross-kind color enum — confirmed not needed, Exercise/Reading already expose everything via `completed`).
- Build/test commands: `./gradlew.bat :app:assembleDebug` and `./gradlew.bat :app:testDebugUnitTest` (run from the repo root, via the PowerShell tool — this environment's Bash tool has no git/gradle in PATH).
- **Noted during `/autoplan` Design review, not fixed:** red vs. green at these saturations is a known-risky pairing for deuteranopia/protanopia color blindness (orange partially disambiguates the 3-state Tanakh case via a luminance progression, but Exercise/Reading are pure 2-color with nothing else to lean on). Accepted for a single-user personal app — the dot is always paired with unambiguous status text ("✓ 5/5", "N behind"), so it's a redundant accelerant, not the sole signal. Also noted: Tanakh's red dot and the existing error-colored "N behind" text both fire for the same Behind state on opposite ends of the row — intentional reinforcement, not a conflict.

---

### Task 1: Add `isDueToday` to `HabitStatus.ScheduleCursorStatus`

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitStatus.kt`
- Modify: `app/src/main/java/com/ziv/reminders/data/ScheduleCursorRepository.kt:25-38`
- Test: `app/src/test/java/com/ziv/reminders/data/ScheduleCursorRepositoryTest.kt`
- Test: `app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt`

**Interfaces:**
- Consumes: existing `ScheduleEntryStatus` sealed interface (`Finished`, `OnSchedule`, `Behind`, `Waiting`) — unchanged, already defined in `ScheduleEntryStatus.kt`.
- Produces: `HabitStatus.ScheduleCursorStatus` gains `val isDueToday: Boolean` (`true` only when the underlying status is `OnSchedule`) — Task 3 reads this field to color the Tanakh dot.

- [ ] **Step 1: Update the 2 existing full-constructor test assertions to include the new field**

In `app/src/test/java/com/ziv/reminders/data/ScheduleCursorRepositoryTest.kt`, change line 41 from:

```kotlin
        assertEquals(HabitStatus.ScheduleCursorStatus("א", "א׳", dueCount = 0, completed = false, finished = false), status)
```

to:

```kotlin
        assertEquals(HabitStatus.ScheduleCursorStatus("א", "א׳", dueCount = 0, completed = false, finished = false, isDueToday = true), status)
```

(`today = LocalDate.of(2026, 7, 12)` in this test equals the schedule's first entry date exactly — this is the `OnSchedule` case, so `isDueToday = true` is correct.)

In `app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt`, change line 122 from:

```kotlin
        assertEquals(HabitStatus.ScheduleCursorStatus("א", "א׳", dueCount = 0, completed = false, finished = false), status)
```

to:

```kotlin
        assertEquals(HabitStatus.ScheduleCursorStatus("א", "א׳", dueCount = 0, completed = false, finished = false, isDueToday = true), status)
```

(This file's `today = LocalDate.of(2026, 7, 14)` equals its single schedule entry's date exactly — also `OnSchedule`, so `isDueToday = true` is correct.)

- [ ] **Step 2: Extend the existing Behind-case test to assert `isDueToday` is false**

In `app/src/test/java/com/ziv/reminders/data/ScheduleCursorRepositoryTest.kt`, change:

```kotlin
    @Test
    fun todayStatus_fallingBehind_reportsDueCount() = runTest {
        val repo = ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule)

        // Cursor still at index 0 (Sunday), but today is Monday — 2 entries due.
        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 7, 13))

        assertEquals(2, status.dueCount)
        assertFalse(status.finished)
    }
```

to:

```kotlin
    @Test
    fun todayStatus_fallingBehind_reportsDueCount() = runTest {
        val repo = ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule)

        // Cursor still at index 0 (Sunday), but today is Monday — 2 entries due.
        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 7, 13))

        assertEquals(2, status.dueCount)
        assertFalse(status.finished)
        assertFalse(status.isDueToday)
    }
```

- [ ] **Step 3: Extend the existing Finished-case test to assert `isDueToday` is false**

In the same file, change:

```kotlin
    @Test
    fun todayStatus_scheduleExhausted_isFinished() = runTest {
        val progressDao = FakeScheduleCursorProgressDao()
        progressDao.rows[3L] = ScheduleCursorProgress(3L, cursorIndex = schedule.size)
        val repo = ScheduleCursorRepository(progressDao, FakeScheduleCursorDailyProgressDao(), schedule)

        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 7, 20))

        assertTrue(status.finished)
        assertEquals(null, status.book)
    }
```

to:

```kotlin
    @Test
    fun todayStatus_scheduleExhausted_isFinished() = runTest {
        val progressDao = FakeScheduleCursorProgressDao()
        progressDao.rows[3L] = ScheduleCursorProgress(3L, cursorIndex = schedule.size)
        val repo = ScheduleCursorRepository(progressDao, FakeScheduleCursorDailyProgressDao(), schedule)

        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 7, 20))

        assertTrue(status.finished)
        assertEquals(null, status.book)
        assertFalse(status.isDueToday)
    }
```

- [ ] **Step 4: Add a new test for the Waiting case**

The existing test file has no direct `todayStatus` assertion for `Waiting` (a next entry dated in the future — nothing due yet). Add this new test immediately after `todayStatus_fallingBehind_reportsDueCount` (from Step 2):

```kotlin
    @Test
    fun todayStatus_nextEntryNotYetDue_isWaiting_reportsIsDueTodayFalse() = runTest {
        val repo = ScheduleCursorRepository(FakeScheduleCursorProgressDao(), FakeScheduleCursorDailyProgressDao(), schedule)

        // Cursor at index 0 (Sunday, dated 2026-07-12), but today is Saturday — the next entry
        // isn't due yet.
        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 7, 11))

        assertEquals(0, status.dueCount)
        assertFalse(status.finished)
        assertFalse(status.isDueToday)
    }
```

- [ ] **Step 5: Run the tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ScheduleCursorRepositoryTest" --tests "com.ziv.reminders.engine.HabitEngineTest"`
Expected: FAIL — compile error, `HabitStatus.ScheduleCursorStatus` has no parameter/property named `isDueToday` yet.

- [ ] **Step 6: Add the `isDueToday` field to `HabitStatus.ScheduleCursorStatus`**

In `app/src/main/java/com/ziv/reminders/data/HabitStatus.kt`, change:

```kotlin
    data class ScheduleCursorStatus(
        val book: String?,
        val chapterHeb: String?,
        val dueCount: Int,
        val completed: Boolean,
        val finished: Boolean,
    ) : HabitStatus
```

to:

```kotlin
    data class ScheduleCursorStatus(
        val book: String?,
        val chapterHeb: String?,
        val dueCount: Int,
        val completed: Boolean,
        val finished: Boolean,
        val isDueToday: Boolean,
    ) : HabitStatus
```

- [ ] **Step 7: Compute `isDueToday` in `ScheduleCursorRepository.todayStatus()`**

In `app/src/main/java/com/ziv/reminders/data/ScheduleCursorRepository.kt`, change:

```kotlin
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
```

to:

```kotlin
    suspend fun todayStatus(instance: HabitInstance, today: LocalDate): HabitStatus.ScheduleCursorStatus {
        val cursorIndex = progressDao.getByInstance(instance.id)?.cursorIndex ?: 0
        val completedToday = dailyProgressDao.getByDate(instance.id, today.toString())?.completed ?: false
        return when (val status = deriveScheduleEntryStatus(schedule, cursorIndex, today)) {
            is ScheduleEntryStatus.Finished ->
                HabitStatus.ScheduleCursorStatus(book = null, chapterHeb = null, dueCount = 0, completed = completedToday, finished = true, isDueToday = false)
            is ScheduleEntryStatus.OnSchedule ->
                HabitStatus.ScheduleCursorStatus(status.entry.book, status.entry.chapterHeb, dueCount = 0, completed = completedToday, finished = false, isDueToday = true)
            is ScheduleEntryStatus.Behind ->
                HabitStatus.ScheduleCursorStatus(status.entry.book, status.entry.chapterHeb, dueCount = status.dueCount, completed = completedToday, finished = false, isDueToday = false)
            is ScheduleEntryStatus.Waiting ->
                HabitStatus.ScheduleCursorStatus(status.entry.book, status.entry.chapterHeb, dueCount = 0, completed = completedToday, finished = false, isDueToday = false)
        }
    }
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.ScheduleCursorRepositoryTest" --tests "com.ziv.reminders.engine.HabitEngineTest"`
Expected: BUILD SUCCESSFUL

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (full suite — confirms no other file constructs `HabitStatus.ScheduleCursorStatus` and broke silently)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitStatus.kt app/src/main/java/com/ziv/reminders/data/ScheduleCursorRepository.kt app/src/test/java/com/ziv/reminders/data/ScheduleCursorRepositoryTest.kt app/src/test/java/com/ziv/reminders/engine/HabitEngineTest.kt
git commit -m "feat: add isDueToday to ScheduleCursorStatus, distinguishing OnSchedule from Waiting"
```

---

### Task 2: Add the `StatusOrange` color constant

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseColors.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `val StatusOrange: Color` — Task 3 imports this into `DashboardScreen.kt`.

- [ ] **Step 1: Add the constant**

In `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseColors.kt`, change:

```kotlin
val GoalGreen = Color(0xFF2E7D32)
val HeatmapHit = GoalGreen
val HeatmapPending = Color(0xFFFFD54F)
```

to:

```kotlin
val GoalGreen = Color(0xFF2E7D32)
val HeatmapHit = GoalGreen
val HeatmapPending = Color(0xFFFFD54F)

// True orange (distinct from HeatmapPending's amber above) — the dashboard's Tanakh row uses
// this for "today's chapter is due and unread, no backlog" (see DashboardScreen.kt's
// HabitStatusDot wiring).
val StatusOrange = Color(0xFFF57C00)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseColors.kt
git commit -m "feat: add StatusOrange color constant for the dashboard's Tanakh status dot"
```

---

### Task 3: Render the status dot on all 3 dashboard rows

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`

**Interfaces:**
- Consumes: `HabitStatus.CounterStatus.completed`, `HabitStatus.TimerStatus.completed` (unchanged, already existing), `HabitStatus.ScheduleCursorStatus.dueCount`/`.isDueToday` (Task 1), `GoalGreen`/`StatusOrange` (Task 2).
- Produces: nothing consumed by later tasks — this is the final task.

- [ ] **Step 1: Add the 2 new imports**

In `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`, add alongside the existing imports (this file currently has zero imports from `com.ziv.reminders.ui.exercise`, and no `Box` import yet either):

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import com.ziv.reminders.ui.exercise.GoalGreen
import com.ziv.reminders.ui.exercise.StatusOrange
```

- [ ] **Step 2: Add the `HabitStatusDot` composable**

Insert this immediately after `RowLongPressMenu` (i.e., right before `CounterHabitRow`):

```kotlin
@Composable
private fun HabitStatusDot(color: Color) {
    Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
}
```

- [ ] **Step 3: Wire the dot into `CounterHabitRow` (Exercise)**

Change the Row's body from:

```kotlin
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
```

to:

```kotlin
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitStatusDot(color = if (status.completed) GoalGreen else MaterialTheme.colorScheme.error)
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            text = if (status.completed) "✓ ${status.current}/${status.goal}" else "${status.current}/${status.goal}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
```

(This is inside `CounterHabitRow`'s outer `Row` — its `modifier`/`verticalAlignment`/`horizontalArrangement = Arrangement.SpaceBetween` parameters above this block are unchanged; only the first child, the bare `Column { ... }`, is replaced with the new inner `Row` wrapping the dot and that same `Column`.)

- [ ] **Step 4: Wire the dot into `TimerHabitRow` (Reading)**

Change the Row's body from:

```kotlin
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
```

to:

```kotlin
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitStatusDot(color = if (status.completed) GoalGreen else MaterialTheme.colorScheme.error)
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
            }
        }
        val minutes = displaySeconds / 60
        val seconds = displaySeconds % 60
        Text(
            text = if (status.completed) "✓" else "%d:%02d".format(minutes, seconds),
            style = MaterialTheme.typography.titleMedium,
        )
    }
```

(Same pattern — `TimerHabitRow`'s outer `Row`'s `modifier`/alignment/arrangement parameters above this block are unchanged; only the first child is replaced.)

- [ ] **Step 5: Wire the dot into `ScheduleCursorHabitRow` (Tanakh)**

Change the Row's body from:

```kotlin
    ) {
        Column {
            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
            Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
        }
        // dueCount is only ever nonzero when status is Behind (see ScheduleCursorRepository's
        // deriveScheduleEntryStatus branches) — OnSchedule/Waiting/Finished always carry 0.
        Column(horizontalAlignment = Alignment.End) {
```

to:

```kotlin
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitStatusDot(
                color = when {
                    // dueCount is only ever nonzero when status is Behind (see
                    // ScheduleCursorRepository's deriveScheduleEntryStatus branches) —
                    // OnSchedule/Waiting/Finished always carry 0. Behind wins regardless of
                    // whether something was separately marked read today — see this plan's
                    // design doc for why the generic `completed` flag can't be used here.
                    status.dueCount > 0 -> MaterialTheme.colorScheme.error
                    status.isDueToday -> StatusOrange
                    else -> GoalGreen
                },
            )
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
            }
        }
        // dueCount is only ever nonzero when status is Behind (see ScheduleCursorRepository's
        // deriveScheduleEntryStatus branches) — OnSchedule/Waiting/Finished always carry 0.
        Column(horizontalAlignment = Alignment.End) {
```

(Same pattern — the outer `Row`'s `modifier`/alignment/arrangement parameters above this block, and everything from the `Column(horizontalAlignment = Alignment.End) {` line onward, are unchanged; only the first child is replaced.)

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Run the full test suite**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt
git commit -m "feat: render a red/orange/green status dot on each dashboard row"
```

- [ ] **Step 9: On-device verification (manual, end of plan)**

Install and manually verify on the connected device:
1. `./gradlew.bat :app:installDebug`
2. Confirm the Exercise row's name/streak are still flush-left and the dot doesn't push them out of position; dot is red if today's 5/5 isn't met, green if it is.
3. Confirm the Reading row's dot is red if today's 15 min isn't logged, green if it is.
4. Confirm the Tanakh row's dot matches its real current state: red if the "N behind" text is showing, orange if today's chapter is due and unread with no backlog, green if caught up.
5. Complete Exercise's goal (or use the existing short-tap increment) and confirm the dot flips from red to green live, without navigating away.
6. Sweep `adb logcat` across the whole session for `FATAL EXCEPTION`/`AndroidRuntime.*Exception` → zero matches.

---

## Self-Review Notes

- **Spec coverage:** all 6 Confirmed Decisions map to tasks — (1)(2) Exercise/Reading 2-color logic → Task 3 Steps 3-4 (no backend change, as decided); (3) Tanakh 3-color logic → Task 3 Step 5; (4) new `isDueToday` field → Task 1; (5) dot rendering (not stripe/background) → Task 3 Step 2; (6) color reuse (error/GoalGreen) + new StatusOrange → Tasks 2-3.
- **Placeholder scan:** no TBDs — every step has complete code or an exact command.
- **Type consistency:** `HabitStatusDot(color: Color)`, `GoalGreen`, `StatusOrange`, `status.isDueToday`, `status.dueCount` used identically across Tasks 1-3 — verified against the actual current file contents (not the design doc's transcription alone) before writing this plan.
