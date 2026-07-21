# Exercise Dashboard Row — Inline Increment + Long-Press Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Exercise's dashboard row increment inline on short-tap (with undo), matching every other habit row, and add a long-press menu offering a choice between the Counter screen and Statistics.

**Corrected during /autoplan CEO review — why this isn't a reversal of the 2026-07-19 decision.** That earlier plan made the whole row navigate-away-on-tap specifically to stop a real sub-counter data-loss bug (a 4-column upsert silently clobbering the other 3 sub-drills). This plan narrows that fix to exactly what needed protecting: sub-counters stay screen-only, untouched by the dashboard, while the aggregate count — which was never the source of that bug — moves back to inline tap. This is a scope correction discovered by living with the broader fix's UX cost (an extra screen hop for the common "just log a rep" case), not indecision.

**Architecture:** `CounterHabitRepository` gains `undoIncrement` (mirrors `ScheduleCursorRepository.undoMarkRead`'s floor-guard shape). `DashboardViewModel.onIncrement` converts from fire-and-forget to `suspend fun` so `DashboardScreen` can await it before showing an undo snackbar (mirrors the `onMarkRead` conversion from the prior plan). A small reusable `RowMenuOption`/`RowLongPressMenu` mechanism renders a long-press menu for any row that supplies options; `CounterHabitRow` adopts `combinedClickable` and offers it for Exercise specifically, replacing the row's previous tap-to-navigate special-case entirely.

**Tech Stack:** Kotlin 2.3.0, Jetpack Compose (Foundation `combinedClickable`, Material3 `AlertDialog`/`Snackbar`), JUnit4 + `kotlin-test`, Robolectric 4.16.1.

## Global Constraints

- No new Room schema — this plan adds one repository method only.
- Manual DI via parallel `AppContainer` interfaces — no new interface member needed (`DashboardDataSource` already exposes `counterHabitRepository`).
- TDD for repository and ViewModel code (Robolectric `@Config(sdk = [35])` for anything touching Room/Android framework classes). Compose UI composables have no test precedent in this codebase (established convention) — implemented directly, verified on-device in a later step, not part of this plan's own tasks.
- Long-press replaces short-tap's current navigation entirely for Exercise — short-tap becomes a pure increment action after this plan, identical to any other Counter-kind habit.
- The `RowLongPressMenu` mechanism stays exactly as small as the design specifies (one data class, one composable, no config knobs) — don't add anything beyond what's shown below.
- Every commit after a task leaves `./gradlew.bat :app:testDebugUnitTest` green.

---

## File Structure

```
Reminders/
  app/src/main/java/com/ziv/reminders/
    data/
      CounterHabitRepository.kt                                        (Modify — Task 1)
    ui/dashboard/
      DashboardViewModel.kt                                            (Modify — Task 2)
      DashboardScreen.kt                                               (Modify — Tasks 2, 3)
    ui/exercise/
      ExerciseCounterScreen.kt                                         (Modify — Task 4)
    MainActivity.kt                                                    (Modify — Task 4)
  app/src/test/java/com/ziv/reminders/
    data/
      CounterHabitRepositoryTest.kt                                    (Modify — Task 1)
    ui/dashboard/
      DashboardViewModelTest.kt                                        (Modify — Task 2)
      DashboardDispatchTest.kt                                         (Modify — Task 3)
```

---

### Task 1: `CounterHabitRepository.undoIncrement`

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/CounterHabitRepository.kt`
- Test: `app/src/test/java/com/ziv/reminders/data/CounterHabitRepositoryTest.kt`

**Interfaces:**
- Produces: `suspend fun undoIncrement(instance: HabitInstance, today: LocalDate)` on `CounterHabitRepository`. Consumed by Task 2's `DashboardViewModel.onUndoIncrement`.

- [ ] **Step 1: Write the failing tests**

Append to the `CounterHabitRepositoryTest` class in `app/src/test/java/com/ziv/reminders/data/CounterHabitRepositoryTest.kt`, after `increment_reachingGoal_marksCompleted`:

```kotlin
    @Test
    fun undoIncrement_decrementsByOne() = runTest {
        val dao = FakeCounterDailyProgressDao()
        val repo = CounterHabitRepository(dao)
        repeat(3) { repo.increment(instance, today) }

        repo.undoIncrement(instance, today)

        val status = repo.todayStatus(instance, today)
        assertEquals(2, status.current)
    }

    @Test
    fun undoIncrement_atZero_isANoOp_neverGoesNegative() = runTest {
        val dao = FakeCounterDailyProgressDao()
        val repo = CounterHabitRepository(dao)

        repo.undoIncrement(instance, today)

        val status = repo.todayStatus(instance, today)
        assertEquals(0, status.current)
    }

    @Test
    fun undoIncrement_droppingBelowGoal_unsetsCompleted() = runTest {
        val dao = FakeCounterDailyProgressDao()
        val repo = CounterHabitRepository(dao)
        repeat(5) { repo.increment(instance, today) } // reaches goal (5), completed = true

        repo.undoIncrement(instance, today)

        val status = repo.todayStatus(instance, today)
        assertEquals(4, status.current)
        assertFalse(status.completed)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.CounterHabitRepositoryTest"`
Expected: FAIL — `undoIncrement` doesn't exist yet (compile error).

- [ ] **Step 3: Add `undoIncrement`**

`app/src/main/java/com/ziv/reminders/data/CounterHabitRepository.kt` — full file:

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

    // Floored at 0 (never negative), same guard shape as ScheduleCursorRepository.undoMarkRead —
    // reverses exactly one increment, recomputing completed from the new count so undoing below
    // goal correctly un-sets it.
    suspend fun undoIncrement(instance: HabitInstance, today: LocalDate) {
        val goal = requireNotNull(instance.counterGoal) { "Counter habit ${instance.id} has no counterGoal" }
        val current = dao.getByDate(instance.id, today.toString())?.count ?: 0
        val newCount = (current - 1).coerceAtLeast(0)
        dao.upsert(
            CounterDailyProgress(
                habitInstanceId = instance.id,
                date = today.toString(),
                count = newCount,
                completed = newCount >= goal,
            )
        )
    }

    // Delegates to HabitStats.currentStreak (same anchor logic: if today isn't done yet,
    // the day isn't over — the streak counts through yesterday and isn't broken until
    // midnight passes without today being hit) so this app has exactly one streak-anchor
    // implementation, not two independently maintained copies that could silently diverge.
    suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int =
        HabitStats.currentStreak(HabitStats.parseDates(dao.getCompletedDates(instance.id)), today)

    // Feeds HabitStats' month/best-month/record functions (ExerciseViewModel, Task 5),
    // which need the raw completed-date rows, not just the derived streak count.
    suspend fun completedDates(instance: HabitInstance): List<String> = dao.getCompletedDates(instance.id)
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.CounterHabitRepositoryTest"`
Expected: PASS (8 tests — 5 existing + 3 new)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/CounterHabitRepository.kt app/src/test/java/com/ziv/reminders/data/CounterHabitRepositoryTest.kt
git commit -m "feat: add CounterHabitRepository.undoIncrement for the Exercise dashboard undo"
```

---

### Task 2: `DashboardViewModel.onIncrement` suspend conversion + `onUndoIncrement` + snackbar wiring

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`
- Test: `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`

**Interfaces:**
- Consumes: `CounterHabitRepository.undoIncrement` (Task 1).
- Produces: `DashboardViewModel.onIncrement` converted from a fire-and-forget `fun` into a plain `suspend fun` (same signature otherwise); new `suspend fun onUndoIncrement(instanceId: Long)`. Both consumed by `DashboardScreen`'s `CounterHabitRow` callback in this same task — bundled together because converting `onIncrement`'s signature and updating its one call site cannot land in separate commits without an intermediate non-compiling state (a suspend function cannot be called from a non-suspend lambda), exactly mirroring the `onMarkRead` conversion from the prior plan.

- [ ] **Step 1: Write the failing test**

Append to the `DashboardViewModelTest` class in `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`, after `onIncrement_updatesStatusAndCompletion`:

```kotlin
    @Test
    fun onUndoIncrement_reversesTheMostRecentIncrement() = runTest {
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
        viewModel.onIncrement(1L)
        testScheduler.advanceUntilIdle()

        viewModel.onUndoIncrement(1L)
        testScheduler.advanceUntilIdle()

        val status = viewModel.uiState.value.habits[0].status as HabitStatus.CounterStatus
        assertEquals(0, status.current)

        db.close()
    }
```

Note: the existing test `onIncrement_updatesStatusAndCompletion` (which calls `viewModel.onIncrement(1L)` inside a `repeat(5) { ... }` block) needs **no changes** — `repeat` is a Kotlin stdlib `inline` function with no `crossinline`/`noinline` modifier on its lambda, so calling a suspend function inside it from a suspend caller (this test's `runTest` body) compiles unchanged, exactly like the prior plan's equivalent `onMarkRead` conversion required zero test changes.

- [ ] **Step 2: Run tests to verify the new one fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `onUndoIncrement` doesn't exist yet (compile error).

- [ ] **Step 3: Convert `onIncrement` to suspend and add `onUndoIncrement`**

In `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`, replace the existing `onIncrement` function:

```kotlin
    fun onIncrement(instanceId: Long) {
        viewModelScope.launch {
            val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return@launch
            dataSource.counterHabitRepository.increment(instance, LocalDate.now())
            refresh()
        }
    }
```

with:

```kotlin
    /** Suspend, not fire-and-forget on viewModelScope (mirrors the fix applied to onMarkRead
     * in the prior plan) — DashboardScreen's Exercise row needs to await this completing before
     * showing the quick-undo snackbar; a fire-and-forget launch gives the caller no signal that
     * the write has happened. */
    suspend fun onIncrement(instanceId: Long) {
        val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return
        dataSource.counterHabitRepository.increment(instance, LocalDate.now())
        refresh()
    }

    suspend fun onUndoIncrement(instanceId: Long) {
        val instance = dataSource.habitInstanceDao.getById(instanceId) ?: return
        dataSource.counterHabitRepository.undoIncrement(instance, LocalDate.now())
        refresh()
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Do not run yet — `DashboardScreen.kt`'s `onIncrement = { viewModel.onIncrement(habit.instanceId) }` call site now references a suspend function from a non-suspend lambda, so `compileTestKotlin` fails at this point (not an individual test failure, a build failure). Proceed to Step 5 first, then run tests.

- [ ] **Step 5: Wire the snackbar into `DashboardScreen`**

In `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`, inside `DashboardScreen`'s `uiState.habits.forEach { habit -> ... }` loop, replace the `onIncrement` argument passed into `HabitRow`:

```kotlin
                    onIncrement = { viewModel.onIncrement(habit.instanceId) },
```

with:

```kotlin
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
```

Everything else in `DashboardScreen.kt` (imports, `CounterHabitRow`'s own body, all other rows) is unchanged in this task — Task 3 adds the long-press menu on top of this.

- [ ] **Step 6: Run tests to verify everything passes**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: PASS (12 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green)

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt
git commit -m "feat: increment Exercise inline from the dashboard with an undo snackbar"
```

---

### Task 3: `RowLongPressMenu` + `CounterHabitRow` long-press wiring + `hasExerciseDetailMenu` rename

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardDispatchTest.kt`

**Interfaces:**
- Produces: `private data class RowMenuOption(val label: String, val onSelect: () -> Unit)`; `private fun RowLongPressMenu(title: String, options: List<RowMenuOption>, onDismiss: () -> Unit)` (both in `DashboardScreen.kt`); `fun hasExerciseDetailMenu(instanceId: Long): Boolean` (renamed from `shouldNavigateToExerciseDetail`, same implementation — dispatch is still by instance ID, not by kind); `HabitRow` gains a new `onOpenExerciseStats: () -> Unit` parameter. Consumed by Task 4's `MainActivity.kt` call site (unaffected by this task — `MainActivity` doesn't call `HabitRow`/`CounterHabitRow` directly, only `DashboardScreen`, whose own public signature is unchanged).

- [ ] **Step 1: Rename the dispatch function and update its test**

`shouldNavigateToExerciseDetail` no longer gates tap-navigation (Task 2 already made short-tap a pure increment for every Counter-kind habit) — it now gates long-press-menu eligibility instead. Rename it to `hasExerciseDetailMenu` for clarity, keeping the exact same implementation and the same dispatch-by-instance-ID principle.

`app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardDispatchTest.kt` — full file:

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
    fun hasExerciseDetailMenu_exerciseInstanceId_isTrue() {
        assertTrue(hasExerciseDetailMenu(EXERCISE_HABIT_INSTANCE_ID))
    }

    @Test
    fun hasExerciseDetailMenu_otherInstanceIds_isFalse() {
        // Regression guard: a hypothetical future second COUNTER-kind habit must not be
        // silently offered the Exercise long-press menu just because it shares
        // HabitKind.COUNTER — dispatch is by instance ID, not by kind.
        assertFalse(hasExerciseDetailMenu(READING_HABIT_INSTANCE_ID))
        assertFalse(hasExerciseDetailMenu(TANAKH_HABIT_INSTANCE_ID))
        assertFalse(hasExerciseDetailMenu(999L))
    }
}
```

- [ ] **Step 2: Run the renamed test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardDispatchTest"`
Expected: FAIL — `hasExerciseDetailMenu` doesn't exist yet (compile error; `shouldNavigateToExerciseDetail` still exists in `DashboardScreen.kt` but is no longer called by the test).

- [ ] **Step 3: Rewrite `DashboardScreen.kt` — rename, add the menu mechanism, wire long-press**

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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
                    onOpenExercise = onOpenExercise,
                    onOpenExerciseStats = onOpenActivity,
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
    onOpenExerciseStats: () -> Unit,
) {
    when (habit.status) {
        is HabitStatus.CounterStatus -> CounterHabitRow(habit, habit.status, onIncrement, onOpenExercise, onOpenExerciseStats)
        is HabitStatus.TimerStatus -> TimerHabitRow(habit, habit.status, onToggleTimer, onResetReadingToday, fetchReadingSessionCountToday)
        is HabitStatus.ScheduleCursorStatus -> ScheduleCursorHabitRow(habit, habit.status, onMarkRead)
    }
}

// Dispatch is by instance ID, not by HabitKind — a hypothetical future second
// COUNTER-kind habit must not be silently offered the Exercise long-press menu just because
// it shares HabitKind.COUNTER (see DashboardDispatchTest). Renamed from
// shouldNavigateToExerciseDetail: it no longer gates tap-navigation (short-tap is now a pure
// increment for every Counter-kind habit) — it gates long-press-menu eligibility instead.
fun hasExerciseDetailMenu(instanceId: Long): Boolean = instanceId == EXERCISE_HABIT_INSTANCE_ID

// Small, deliberately generic long-press menu mechanism — a row supplies a title and a list of
// labeled actions, this renders them as an AlertDialog with one button per option plus Cancel.
// Chosen over a one-off dialog hardcoded in CounterHabitRow so any future row that needs a
// "pick where to go" long-press can reuse this without new bespoke dialog code — the one
// deliberate exception to this codebase's usual anti-premature-generalization stance (no second
// use case exists yet; kept intentionally small — one data class, one composable, no config
// knobs beyond title/options/onDismiss).
private data class RowMenuOption(val label: String, val onSelect: () -> Unit)

@Composable
private fun RowLongPressMenu(title: String, options: List<RowMenuOption>, onDismiss: () -> Unit) {
    // Cancel lives in the body alongside the real options (not confirmButton) — corrected
    // during /autoplan design review: AlertDialog's confirmButton slot renders with more visual
    // emphasis than plain body TextButtons, so putting Cancel there (the original draft) made
    // "do nothing" look like the recommended choice instead of the N real options. confirmButton
    // is a required parameter but doesn't have to render anything, so it's left empty.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    TextButton(onClick = { onDismiss(); option.onSelect() }) { Text(option.label) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        confirmButton = {},
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CounterHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.CounterStatus,
    onIncrement: () -> Unit,
    onOpenExercise: () -> Unit,
    onOpenExerciseStats: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val isExercise = hasExerciseDetailMenu(habit.instanceId)

    // Noted during /autoplan design review, not fixed: if the user long-presses and navigates
    // away while a prior tap's undo-snackbar coroutine is still pending (within its ~4s window),
    // leaving composition cancels that coroutine scope — the Undo action is silently lost, no
    // data corruption, just a missed correction window. Acceptable for a personal app; a stray
    // extra increment is a one-tap fix on the next visit.
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onIncrement,
            onLongClick = if (isExercise) { { showMenu = true } } else null,
        ),
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

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            options = listOf(
                RowMenuOption("Counter", onOpenExercise),
                RowMenuOption("Statistics", onOpenExerciseStats),
            ),
            onDismiss = { showMenu = false },
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

- [ ] **Step 4: Verify it builds and all tests pass**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardDispatchTest"`
Expected: PASS (2 tests)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green — confirms the rename didn't break any other reference, and Reading/Tanakh rows are byte-for-byte unchanged in this diff)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardDispatchTest.kt
git commit -m "feat: add Exercise dashboard long-press menu (Counter / Statistics)"
```

---

### Task 4: Remove `ExerciseCounterScreen`'s internal stats icon

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseCounterScreen.kt`
- Modify: `app/src/main/java/com/ziv/reminders/MainActivity.kt`

**Interfaces:**
- Consumes: nothing new. Removes: `ExerciseCounterScreen`'s `onOpenStats` parameter — verified as having exactly one caller (`MainActivity.kt`'s `composable("exerciseCounter")` block) before removal, so this is fully scoped.

- [ ] **Step 1: Remove the stats icon and `onOpenStats` parameter from `ExerciseCounterScreen`**

In `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseCounterScreen.kt`, change the function signature from:

```kotlin
@Composable
fun ExerciseCounterScreen(viewModel: ExerciseViewModel, onOpenStats: () -> Unit, onBack: () -> Unit) {
```

to:

```kotlin
@Composable
fun ExerciseCounterScreen(viewModel: ExerciseViewModel, onBack: () -> Unit) {
```

And remove this block (the top-right stats icon row) entirely:

```kotlin
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onOpenStats) {
                Icon(imageVector = Icons.Default.DateRange, contentDescription = "Your Progress", tint = GoalGreen)
            }
        }
```

Also remove these 3 now-unused imports (verified against the rest of the file — nothing else uses them):

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.IconButton
```

`Arrangement` was only used by the removed `Row`'s `horizontalArrangement = Arrangement.End` — no other `Row`/`Column` in this file specifies an arrangement. `DateRange` was only used by the removed icon. `IconButton` was only used by the removed stats button (the sub-counter rows use `OutlinedButton`, not `IconButton`). Keep `Icon` and `Icons` — both stay in use via the FAB's `Icon(imageVector = Icons.Default.Add, ...)`.

- [ ] **Step 2: Update `MainActivity.kt`'s call site**

In `app/src/main/java/com/ziv/reminders/MainActivity.kt`, change:

```kotlin
                    composable("exerciseCounter") {
                        ExerciseCounterScreen(
                            viewModel = exerciseViewModel,
                            onOpenStats = { navController.navigate("activity") },
                            onBack = { navController.popBackStack() },
                        )
                    }
```

to:

```kotlin
                    composable("exerciseCounter") {
                        ExerciseCounterScreen(
                            viewModel = exerciseViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
```

- [ ] **Step 3: Verify it builds and all tests pass**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL — confirms no other call site referenced `onOpenStats` and no import was left dangling.

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all tests green — this task touches no logic layer, only Compose UI wiring)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseCounterScreen.kt app/src/main/java/com/ziv/reminders/MainActivity.kt
git commit -m "feat: remove ExerciseCounterScreen's now-redundant stats icon"
```

---

## Final Verification

After all 4 tasks:

```bash
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug
```

Both must succeed. Then on-device verification (per this session's established convention — Compose UI has no test coverage, so this is the real check):
1. Short-tap the Exercise row → count increments in place (no navigation) → "Incremented" snackbar with "Undo" appears → tap Undo → count reverts by exactly 1.
2. Long-press the Exercise row → menu appears with "Counter" and "Statistics" → tap "Counter" → lands on the full counter screen (big `+` button, sub-drill counters, no stats icon visible) → back → long-press again → tap "Statistics" → lands on the Activity screen's Exercise section.
3. Tap the Exercise row repeatedly past goal → count keeps incrementing past goal/5, uncapped.
4. Confirm Reading and Tanakh rows behave exactly as before (tap, long-press, snackbars) — zero regression.

<!-- AUTONOMOUS DECISION LOG -->
## Decision Audit Trail

| # | Phase | Decision | Classification | Principle | Rationale | Rejected |
|---|-------|----------|-----------------|-----------|-----------|----------|
| 1 | CEO | Add a one-paragraph rationale distinguishing this plan from the 2026-07-19 decision it partially reverses | Mechanical | P5 (explicit over clever) | CEO review found the reversal is a legitimate scope correction (only the aggregate count returns to inline tap; the sub-counters that caused the original data-loss bug stay protected), but the plan didn't say so — left as an implicit reader-inference | Leaving it unstated (original draft) |
| 2 | Design | Move "Cancel" out of `RowLongPressMenu`'s `confirmButton` slot into the body alongside the real options; `confirmButton` renders empty | Mechanical | P5 (explicit over clever) | Material3's `confirmButton` slot renders with more visual emphasis than body `TextButton`s — Cancel occupying it made "do nothing" look like the recommended choice ahead of the actual options | Leaving Cancel in `confirmButton` (original draft) |
| 3 | Design | Document (code comment, not a functional fix) that long-pressing away during a pending undo-snackbar silently cancels that coroutine | Taste (documented, not silently dropped) | P3 (pragmatic) | Real but narrow edge case; for a personal app a stray extra increment is a one-tap fix next visit, not worth a guard | Adding a pending-write lock/guard |
| 4 | Eng | Reword Task 2 Step 4's "Expected: PASS (12 tests)" (misleading — it's actually a build failure at that checkpoint, not an individual test failure) to "Do not run yet" | Mechanical | P5 (explicit over clever) | The plan's own prose already warned not to trust this result, but the "Expected: PASS" line contradicted that warning | Leaving the contradictory wording |

## GSTACK REVIEW REPORT

**Phases run:** CEO (Phase 1), Design (Phase 2 — UI scope: Compose dialogs, long-press, rows), Eng (Phase 3). DX (Phase 3.5) skipped — no developer-facing scope.

**Dual voices:** Codex CLI unavailable in this environment — all three phases ran as `[subagent-only]` (one independent Claude subagent per phase, no prior-phase context). No User Challenges surfaced (no case where a second independent voice existed to agree with a challenge — single-voice findings only, all Taste/Mechanical, none rising to a challenge of the user's stated direction).

**CEO findings:** 1 mechanical fix (decision #1). Confirmed this is a legitimate scope correction, not thrash. Confirmed the `RowLongPressMenu` reusability bet is low-probability-of-payoff but cheap-if-wrong (~15 lines), acceptable as designed — no change needed. Flagged (not fixed, accepted) a medium-severity long-press-discoverability regret scenario and a low-severity undo-window risk shape matching an already-accepted pattern elsewhere in this codebase.

**Design findings:** 2 mechanical fixes (decisions #2, #3). Confirmed the 3-way long-press-pattern difference across Tanakh/Reading/Exercise is appropriate (tracks genuinely different action kinds), not an inconsistency needing a fix. Confirmed dialog copy specificity is sufficient and the over-goal-tap "✓ 6/5" display is unambiguous, if unusual — no fix needed.

**Eng findings:** 1 mechanical fix (decision #4, doc wording only). Confirmed the `RowMenuOption`/`RowLongPressMenu` mechanism is genuinely as small as claimed, the suspend-conversion compile-safety reasoning (including the `repeat`-is-inline claim) is correct, the rename has zero missed callers repo-wide, and the full `DashboardScreen.kt` code block has no literal compile defects. No Critical or Important findings in any phase.

**Outcome:** 4 of 4 findings auto-fixed directly in this plan (all Mechanical, all cheap). 2 items explicitly accepted as documented, non-blocking risk (long-press discoverability decay; pending-undo-lost-on-navigation). Zero User Challenges — nothing requires your decision beyond final approval.

**PLAN STATUS: APPROVED — ready for subagent-driven-development.**
