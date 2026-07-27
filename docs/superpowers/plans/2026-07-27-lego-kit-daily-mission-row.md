<!-- /autoplan restore point: C:\Users\zivk\.gstack\projects\Reminders\main-autoplan-restore-20260727-001506.md -->
# Lego Kit Daily Mission Row Implementation Plan

**Status: IMPLEMENTED** — reviewed via `/autoplan` on 2026-07-27 (CEO + Design + Eng phases,
Claude subagent voices only, Codex unavailable). 9 findings surfaced and fixed across all 3
phases (including 1 CRITICAL bug: `CounterHabitRepository.currentStreak` ignored
`enabledDaysMask` entirely — see Task 1.5). 0 taste decisions, 0 user challenges. All 6 tasks
implemented, tested, and committed the same day (commits `d27d60e`, `b4b6a7f`, `99e2d2b`,
`20f51eb`, `d2375f1`); Task 5 verified live on a real device via adb (screenshots confirmed tap,
dim, streak, Statistics, and Undo all work correctly), with only the Friday/Saturday case
confirmed by code-path reasoning + Task 1.5's hermetic test rather than changing the device's
system date.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Design doc:** `~/.gstack/projects/Reminders/zivk-main-design-20260726-234613.md` (APPROVED,
2026-07-26, via `/office-hours`).

**Goal:** Add a 5th dashboard row — "Lego Kit" — a daily mission active Sunday–Thursday that
reads "Add one kit." Tapping marks it done for the day and disables further taps until the
next day. Long-pressing opens a menu with two options: Statistics (streak + heatmap) and Undo
(reverses today's mark).

**Architecture:** No new `HabitKind`, no new Room schema, no migration. This reuses the existing
`COUNTER` kind exactly as Exercise does (`counterGoal = 1`), and the existing
`enabledDaysMask` bitmask exactly as Reading does (`0b0011111`, Sun–Thu) — already fully
generalized across `HabitEngine` and `HabitScheduler`.

**Correction (caught by independent Eng review, fixed as Task 1.5 below):** this paragraph's
first draft also claimed `enabledDaysMask` was already generalized across
`CounterHabitRepository`/`StreakCalculator` — that was wrong. `CounterHabitRepository.currentStreak`
never routed through the mask-aware `StreakCalculator` at all (unlike Timer/ScheduleCursor/
ComputedSchedule); it was safe only because Exercise, the sole `COUNTER` instance until now, uses
an all-days mask. Seeding a second `COUNTER` instance with a Sun-Thu mask exposes that gap — see
Task 1.5, which fixes `CounterHabitRepository.currentStreak` to respect the mask before Lego Kit
ships. Everything else in this Architecture section is unaffected. The only genuinely new
Compose UI-layer code is:

1. `DashboardScreen.kt`'s `HabitRow` `when` block dispatches on `HabitStatus` subtype today
   (`is HabitStatus.CounterStatus -> CounterHabitRow(...)`) — not by instance ID. This branch
   must be restructured to check `habit.instanceId` so Lego Kit routes to a new
   `LegoKitHabitRow` composable instead of `CounterHabitRow`.
2. `LegoKitHabitRow`: tap is a no-op once `status.completed` is true (mirrors
   `ScheduleCursorHabitRow`'s `if (!status.finished)` and `ComputedScheduleHabitRow`'s
   `if (status.dueCount > 0)` guards), and dims (reduced alpha) when completed or on an
   off-day (Fri/Sat) — the dashboard still always renders every row regardless of
   `enabledDaysMask` (existing rule, commit `6647fd2`), this row is just visually inert then.
   No transient Snackbar on tap (unlike Exercise) — Undo lives in the long-press menu instead,
   so there is exactly one Undo affordance, not two.
3. A new standalone callback in `DashboardScreen`'s `forEach` block that calls
   `viewModel.onUndoIncrement(habit.instanceId)` directly — today `onUndoIncrement` (already
   generic on `DashboardViewModel`, unchanged by this plan) is only invoked from inside
   Exercise's transient-Snackbar action closure.
4. `LegoKitStatsViewModel`/`LegoKitStatsScreen` — a near-literal clone of
   `ComputedScheduleStatsViewModel`/`ComputedScheduleStatsScreen`'s shape (streak +
   `HabitStatsSummary` + `HeatmapGrid`), sourced from `CounterHabitRepository.completedDates`
   (already exists, currently only unit-tested, never wired to a screen) and
   `HabitEngine.currentStreak` (already generic). `DashboardDataSource` already exposes
   `counterHabitRepository` and `habitEngine` — no interface change needed.
5. Seeding: one more `insertIfAbsent` call in `HabitSeeding.kt`, following the existing
   4-instance pattern exactly (`LEGO_KIT_HABIT_INSTANCE_ID = 5L`).
6. Navigation: one more `composable("legoKitStats")` destination in `MainActivity.kt`, following
   the `cppWeeklyStats` pattern exactly.

**Tech Stack:** Same as every prior plan in this repo — Kotlin 2.3.0, Jetpack Compose (Material
3), Room 2.7.1 (KSP, unchanged — no migration this plan), JUnit4 + Robolectric 4.16.1,
`kotlinx-coroutines-test`. No new dependencies.

## Global Constraints

- Package / application ID: `com.ziv.reminders`. `minSdk = 35`, `targetSdk = 36`.
- Current schema stays at **version 7** — this plan adds zero columns and zero tables. If review
  finds a reason a schema change is actually needed, that is a scope change requiring its own
  migration task, not a silent addition to this plan.
- Single row, single purpose: this is a **Lego-Kit-specific** dashboard row and stats screen, not
  a generalized "one-tap daily mission" framework — per the design doc's Approach C rejection
  (only one concrete use case exists; this repo's own `TODOS.md` repeatedly rejects generalizing
  ahead of a second use case). A hypothetical future second such habit is just another seeded
  `COUNTER` instance plus its own dedicated row composable, following this same pattern.
- No in-app "add habit" UI — seeded via `ensureHabitsSeeded`, same mechanism as every other row.
- This codebase's Compose UI composables have **no unit-test precedent** (see the C++ Weekly
  plan's own Global Constraints for the same statement) — the dashboard row and stats screen are
  implement-directly, verified via `assembleDebug` and the existing suite staying green, plus
  on-device verification in the final task. `LegoKitStatsViewModel` (not a composable) does get
  unit tests, mirroring `ComputedScheduleStatsViewModelTest`.
- Every commit after a task leaves `./gradlew.bat :app:testDebugUnitTest` green.
- Build/test commands: `./gradlew.bat :app:assembleDebug` and `./gradlew.bat
  :app:testDebugUnitTest` (repo root, PowerShell — this environment's Bash tool has no
  git/gradle in PATH).
- Icon: a Lego stud/cube, matching the existing per-habit vector drawable convention. Exact
  artwork left to whoever draws it (per design doc's Open Questions) — a placeholder geometric
  shape is acceptable for this plan; swapping the drawable later has zero code impact.

---

## File Structure

```
Reminders/
  app/src/main/res/drawable/
    ic_habit_legokit.xml                    (Create — Task 1)
  app/src/main/java/com/ziv/reminders/
    data/
      HabitSeeding.kt                        (Modify — Task 1)
      CounterHabitRepository.kt              (Modify — Task 1.5)
    ui/dashboard/
      DashboardUiState.kt                    (Modify — Task 2)
      DashboardViewModel.kt                  (Modify — Task 2)
      DashboardScreen.kt                     (Modify — Task 2)
      LegoKitStatsViewModel.kt               (Create — Task 3)
      LegoKitStatsScreen.kt                  (Create — Task 3)
    MainActivity.kt                          (Modify — Task 4)
  app/src/test/java/com/ziv/reminders/
    data/
      CounterHabitRepositoryTest.kt          (Modify — Task 1.5)
    ui/dashboard/
      DashboardViewModelTest.kt              (Modify — Task 2)
      DashboardDispatchTest.kt               (Modify — Task 2)
      LegoKitStatsViewModelTest.kt           (Create — Task 3)
```

---

### Task 1: Seed the Lego Kit habit instance + icon

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt`
- Create: `app/src/main/res/drawable/ic_habit_legokit.xml`

**Interfaces:**
- Produces: `LEGO_KIT_HABIT_INSTANCE_ID = 5L` constant; a 5th `HabitInstance` row
  (`kind = COUNTER`, `counterGoal = 1`, `enabledDaysMask = 0b0011111`).
- Consumed by: Task 2 (dashboard dispatch), Task 3 (stats screen).

No dedicated unit test — `ensureHabitsSeeded` has no existing test file (verified: none of the
4 current instances have one); seeding correctness is exercised indirectly via
`DashboardViewModelTest`/on-device verification, matching existing precedent.

- [ ] **Step 1: Add the icon drawable**

Create `app/src/main/res/drawable/ic_habit_legokit.xml` as a simple vector drawable (a 2×2 Lego
stud/cube shape), matching the viewport (`24dp x 24dp`) and style of the existing
`ic_habit_*.xml` drawables.

- [ ] **Step 2: Add the seed row**

In `app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt`, add:

```kotlin
const val LEGO_KIT_HABIT_INSTANCE_ID = 5L
```

And inside `ensureHabitsSeeded`, after the existing C++ Weekly `try`/`catch` block:

```kotlin
dao.insertIfAbsent(
    HabitInstance(
        id = LEGO_KIT_HABIT_INSTANCE_ID,
        kind = HabitKind.COUNTER.name,
        name = "Lego Kit",
        enabledDaysMask = 0b0011111, // Sun-Thu, matching Reading's mask
        notificationTitle = "Reminders",
        notificationBody = "Add one Lego kit today?",
        counterGoal = 1,
    )
)
```

No `try`/`catch` needed here (unlike C++ Weekly's anchor-date placeholder) — every field has a
concrete value, nothing can throw.

- [ ] **Step 3: Run the full suite, then commit**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests still green — this step only adds data, no new code paths
under test yet).

```bash
git add app/src/main/java/com/ziv/reminders/data/HabitSeeding.kt app/src/main/res/drawable/ic_habit_legokit.xml
git commit -m "feat: seed the Lego Kit COUNTER habit instance"
```

---

### Task 1.5: Fix `CounterHabitRepository.currentStreak` to respect `enabledDaysMask`

**Eng review finding (CRITICAL, fixed in this revision):** the CEO phase's Architecture section
claimed `enabledDaysMask` is "already fully generalized across `HabitEngine`,
`CounterHabitRepository`, `StreakCalculator`, and `HabitScheduler`" — that claim is **wrong** for
`CounterHabitRepository` specifically, and independent Eng review caught it. Today,
`CounterHabitRepository.currentStreak` delegates to `HabitStats.currentStreak`, a naive
consecutive-*calendar*-day walk with no `enabledDaysMask` awareness at all —
`TimerHabitRepository`/`ScheduleCursorRepository` both route through the mask-aware
`StreakCalculator.calculate` instead, specifically to avoid breaking Sun-Thu-style streaks on
off-days. `StreakCalculator.kt`'s own doc comment explains why `CounterHabitRepository` was
never switched over: *"Deliberately not applied to Counter's existing... currentStreak — its
enabledDaysMask is always all-days, so the two are behaviorally identical for it anyway."* That
assumption held because Exercise (the only `COUNTER` instance until now) uses an all-days mask.
Seeding Lego Kit as a **second** `COUNTER` instance with a Sun-Thu mask breaks it: a user tapping
Lego Kit every Sun-Thu would see the dashboard row's streak (and the new Stats screen's streak)
reset to 1 every Friday and Saturday, silently, with no test catching it.

**Fix:** `CounterHabitRepository.currentStreak` switches to `StreakCalculator.calculate` — safe
for Exercise's existing all-days mask (verified: `StreakCalculator.calculate`'s anchor logic —
`if (today in dates) today else today.minusDays(1)` — is identical to `HabitStats.currentStreak`'s,
and with every day enabled, walking and skip-if-disabled reduces to the same naive consecutive-day
walk; confirmed against `CounterHabitRepositoryTest.kt`'s two existing all-days streak tests).

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/data/CounterHabitRepository.kt`
- Modify: `app/src/test/java/com/ziv/reminders/data/CounterHabitRepositoryTest.kt`

**Interfaces:**
- Changes: `CounterHabitRepository.currentStreak`'s internal implementation only — signature
  unchanged (`suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int`).

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/java/com/ziv/reminders/data/CounterHabitRepositoryTest.kt`. Uses fixed,
verified calendar dates (not `LocalDate.now()`) so the test is hermetic regardless of when it
runs — 2026-01-01 is a Thursday, 2026-01-02 Friday, 2026-01-03 Saturday, 2026-01-04 Sunday:

```kotlin
@Test
fun currentStreak_skipsDisabledDays_forASunThuMaskedInstance() = runTest {
    val db = newDb() // matches this file's existing in-memory DB setup helper
    val instance = HabitInstance(
        id = 5L, kind = "COUNTER", name = "Lego Kit", enabledDaysMask = 0b0011111,
        notificationTitle = "t", notificationBody = "b", counterGoal = 1,
    )
    val repo = CounterHabitRepository(db.counterDailyProgressDao())
    // Thursday 2026-01-01 and Sunday 2026-01-04 both completed; Friday/Saturday untouched
    // (the instance isn't enabled those days, so nothing would ever be marked then).
    db.counterDailyProgressDao().upsert(CounterDailyProgress(5L, "2026-01-01", count = 1, completed = true))
    db.counterDailyProgressDao().upsert(CounterDailyProgress(5L, "2026-01-04", count = 1, completed = true))

    // A naive consecutive-calendar-day walk (the bug) sees Sat/Fri as misses and returns 1.
    // The correct, mask-aware answer skips the two disabled days and returns 2.
    assertEquals(2, repo.currentStreak(instance, today = LocalDate.of(2026, 1, 4)))
}
```

(Adjust constructor argument names/order to match this file's existing test helpers exactly —
follow the same pattern the file's other tests already use for building a DB/instance/repo.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.CounterHabitRepositoryTest"`
Expected: FAIL — the new test asserts `2`, current implementation returns `1`.

- [ ] **Step 3: Fix the implementation**

In `CounterHabitRepository.kt`, change:

```kotlin
suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int =
    HabitStats.currentStreak(HabitStats.parseDates(dao.getCompletedDates(instance.id)), today)
```

to:

```kotlin
suspend fun currentStreak(instance: HabitInstance, today: LocalDate): Int =
    StreakCalculator.calculate(HabitStats.parseDates(dao.getCompletedDates(instance.id)), instance.enabledDaysMask, today)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.data.CounterHabitRepositoryTest"`
Expected: PASS — including the two pre-existing all-days tests
(`currentStreak_todayNotDoneYet_countsThroughYesterday`, `currentStreak_gapBreaksIt`), which must
still pass unchanged (they exercise an all-days mask, where `StreakCalculator.calculate` and
`HabitStats.currentStreak` are behaviorally identical).

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests still green — this change is universal to every `COUNTER`
instance, so the full suite is the real regression gate here, not just this one test file).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/data/CounterHabitRepository.kt app/src/test/java/com/ziv/reminders/data/CounterHabitRepositoryTest.kt
git commit -m "fix: make CounterHabitRepository.currentStreak respect enabledDaysMask"
```

---

### Task 2: Dashboard row — dispatch restructure, `LegoKitHabitRow`, standalone Undo callback

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardUiState.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt`
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`
- Modify: `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`
- Modify: `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardDispatchTest.kt`

**Interfaces:**
- Produces: `fun isLegoKitRow(instanceId: Long): Boolean` (mirrors `hasExerciseDetailMenu`'s
  shape); `LegoKitHabitRow` composable; a new `onOpenLegoKitStats: () -> Unit` parameter on
  `DashboardScreen` and the private `HabitRow` function; a new `onUndoIncrementDirect` lambda
  wired in `DashboardScreen`'s `forEach` block; `HabitRowUiState.enabledDaysMask: Int` (new
  field).
- Consumes: `DashboardViewModel.onUndoIncrement` (existing, unchanged), `DashboardViewModel.onIncrement`
  (existing, unchanged).

**CEO review finding (fixed in this revision):** the first draft of this task hardcoded a
`SUN_THU_MASK` placeholder inside `LegoKitHabitRow` instead of reading the instance's real
`enabledDaysMask`, with the fix noted only as a comment. That's a real gap, not a footnote — an
implementer could ship the hardcoded version, all existing tests would still pass (nothing
exercises Friday/Saturday dimming), and the row would silently be wrong for any future
`COUNTER`-kind row seeded with a different mask. Step 1 below now threads the real mask through
`HabitRowUiState` as a first-class step, verified by a `DashboardViewModelTest` case (the layer
that's actually unit-testable — the Compose dimming itself stays on-device-verified per Global
Constraints, but the *data* driving it is now tested).

- [ ] **Step 1: Write the failing test for `enabledDaysMask` threading**

Add to `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt`:

```kotlin
@Test
fun refresh_populatesEnabledDaysMaskFromTheInstance() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
        .build()
    db.habitInstanceDao().insertIfAbsent(
        HabitInstance(5L, "COUNTER", "Lego Kit", 0b0011111, "t", "b", counterGoal = 1)
    )
    val viewModel = DashboardViewModel(TestAppContainer(db))

    viewModel.refresh()
    testScheduler.advanceUntilIdle()

    assertEquals(0b0011111, viewModel.uiState.value.habits[0].enabledDaysMask)

    db.close()
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: FAIL — `HabitRowUiState` has no `enabledDaysMask` field yet (compile error).

- [ ] **Step 3: Add `enabledDaysMask` to `HabitRowUiState` and populate it in `refresh()`**

In `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardUiState.kt`:

```kotlin
data class HabitRowUiState(
    val instanceId: Long,
    val name: String,
    val status: HabitStatus,
    val streak: Int,
    val enabledDaysMask: Int,
)
```

In `DashboardViewModel.refresh()`, `HabitRowUiState(instance.id, instance.name, status, streak)`
becomes `HabitRowUiState(instance.id, instance.name, status, streak, instance.enabledDaysMask)` —
`refresh()` already reads the full `HabitInstance`, so this is a one-line addition.

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardViewModelTest"`
Expected: PASS.

- [ ] **Step 4: Write the failing dispatch test**

Add to `app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardDispatchTest.kt`:

```kotlin
@Test
fun isLegoKitRow_legoKitInstanceId_isTrue() {
    assertTrue(isLegoKitRow(LEGO_KIT_HABIT_INSTANCE_ID))
}

@Test
fun isLegoKitRow_otherInstanceIds_isFalse() {
    assertFalse(isLegoKitRow(EXERCISE_HABIT_INSTANCE_ID))
    assertFalse(isLegoKitRow(READING_HABIT_INSTANCE_ID))
    assertFalse(isLegoKitRow(TANAKH_HABIT_INSTANCE_ID))
    assertFalse(isLegoKitRow(CPP_WEEKLY_HABIT_INSTANCE_ID))
    assertFalse(isLegoKitRow(999L))
}
```

(Add matching imports for both `LEGO_KIT_HABIT_INSTANCE_ID` and `CPP_WEEKLY_HABIT_INSTANCE_ID`
from `com.ziv.reminders.data` — the file currently only imports `EXERCISE_HABIT_INSTANCE_ID`/
`READING_HABIT_INSTANCE_ID`/`TANAKH_HABIT_INSTANCE_ID`; the snippet above references
`CPP_WEEKLY_HABIT_INSTANCE_ID` too, which isn't imported yet and would otherwise be a compile
error — caught by independent Eng review.)

- [ ] **Step 5: Run the test to verify it fails**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardDispatchTest"`
Expected: FAIL — `isLegoKitRow` doesn't exist yet (compile error).

- [ ] **Step 6: Implement `isLegoKitRow` and restructure dispatch**

In `DashboardScreen.kt`, add next to `hasExerciseDetailMenu`:

```kotlin
// Same "dispatch by ID, not by shared HabitKind" rule as hasExerciseDetailMenu — Lego Kit is
// also a COUNTER-kind habit (shares HabitStatus.CounterStatus), so it must not fall through to
// CounterHabitRow's always-tappable, never-dimmed rendering just because the status type matches.
fun isLegoKitRow(instanceId: Long): Boolean = instanceId == LEGO_KIT_HABIT_INSTANCE_ID
```

Restructure the `HabitRow` `when` block's `CounterStatus` branch:

```kotlin
is HabitStatus.CounterStatus -> if (isLegoKitRow(habit.instanceId)) {
    LegoKitHabitRow(habit, habit.status, onIncrementLegoKit, onUndoLegoKit, onOpenLegoKitStats)
} else {
    CounterHabitRow(habit, habit.status, onIncrement, onOpenExercise, onOpenExerciseStats)
}
```

Thread `onIncrementLegoKit: () -> Unit`, `onUndoLegoKit: () -> Unit`, and
`onOpenLegoKitStats: () -> Unit` through both `DashboardScreen`'s public signature and the
private `HabitRow` signature, matching how `onOpenCppWeeklyStats` is already threaded.

In `DashboardScreen`'s `forEach` block, add (no Snackbar — see Task's Architecture note 2):

```kotlin
onIncrementLegoKit = {
    coroutineScope.launch { viewModel.onIncrement(habit.instanceId) }
},
onUndoLegoKit = {
    coroutineScope.launch { viewModel.onUndoIncrement(habit.instanceId) }
},
onOpenLegoKitStats = onOpenLegoKitStats,
```

- [ ] **Step 7: Implement `LegoKitHabitRow`**

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LegoKitHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.CounterStatus,
    onIncrement: () -> Unit,
    onUndo: () -> Unit,
    onOpenStats: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val isEnabledToday = isEnabledDay(today, habit.enabledDaysMask)
    val isDimmed = status.completed || !isEnabledToday

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDimmed) 0.5f else 1f)
            .combinedClickable(
                onClick = { if (!status.completed && isEnabledToday) onIncrement() },
                onLongClick = { showMenu = true },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painter = painterResource(R.drawable.ic_habit_legokit), contentDescription = null, modifier = Modifier.size(40.dp))
            // Design review finding (fixed in this revision): red is reserved everywhere else in
            // this codebase for "behind schedule, needs attention" (ScheduleCursorHabitRow,
            // ComputedScheduleHabitRow both only use error-red for a real dueCount). Lego Kit is
            // the first row that dims for a SCHEDULE reason (off-day), not just a completion
            // reason — reusing the plain completed/error binary here would show a red dot on a
            // dimmed, non-actionable Friday row, reading as "overdue" rather than "not today."
            HabitStatusDot(
                color = when {
                    status.completed -> GoalGreen
                    !isEnabledToday -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.error
                },
            )
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            text = when {
                status.completed -> "✓ Added"
                !isEnabledToday -> "Not today"
                else -> "Add one kit"
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            options = buildList {
                add(RowMenuOption("Statistics", onOpenStats))
                if (status.completed) add(RowMenuOption("Undo", onUndo))
            },
            onDismiss = { showMenu = false },
        )
    }
}
```

`isEnabledToday` now reads the real per-instance mask (`habit.enabledDaysMask`, added in Step 3)
via the existing `isEnabledDay` helper from `EnabledDays.kt` — no hardcoded mask. Add
`import com.ziv.reminders.data.isEnabledDay` to `DashboardScreen.kt` (not currently imported
there).

- [ ] **Step 8: Run tests, verify green, then commit**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardDispatchTest"`
Expected: PASS.

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests still green).

Run: `./gradlew.bat :app:assembleDebug`
Expected: PASS (Compose UI has no unit-test precedent — this is the verification gate for the
new composable per Global Constraints).

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardUiState.kt app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardViewModel.kt app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardViewModelTest.kt app/src/test/java/com/ziv/reminders/ui/dashboard/DashboardDispatchTest.kt
git commit -m "feat: add the Lego Kit dashboard row with tap-to-disable and Statistics/Undo menu"
```

---

### Task 3: Statistics screen

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/ui/dashboard/LegoKitStatsViewModel.kt`
- Create: `app/src/main/java/com/ziv/reminders/ui/dashboard/LegoKitStatsScreen.kt`
- Create: `app/src/test/java/com/ziv/reminders/ui/dashboard/LegoKitStatsViewModelTest.kt`

**Interfaces:**
- Produces: `class LegoKitStatsViewModel(dataSource: DashboardDataSource) : ViewModel()` with
  `uiState: StateFlow<ActivitySectionState>` and `refresh()`; `LegoKitStatsScreen(viewModel, onBack)`.
- Consumes: `CounterHabitRepository.completedDates` (existing), `HabitEngine.currentStreak`
  (existing), `ActivitySectionState`/`HabitStatsSummary`/`HeatmapGrid`/`EmptySectionState`
  (existing, shared with `ComputedScheduleStatsScreen`).

- [ ] **Step 1: Write the failing ViewModel tests**

`app/src/test/java/com/ziv/reminders/ui/dashboard/LegoKitStatsViewModelTest.kt` (mirrors
`ComputedScheduleStatsViewModelTest.kt`'s structure exactly, swapping the watch-log source for
`CounterDailyProgress` rows):

```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.CounterDailyProgress
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
class LegoKitStatsViewModelTest {

    private fun newDb(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(scheduler))
            .build()
    }

    @Test
    fun refresh_noHistory_populatesEmptyState() = runTest {
        val db = newDb(testScheduler)
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(5L, "COUNTER", "Lego Kit", 0b0011111, "t", "b", counterGoal = 1)
        )
        val viewModel = LegoKitStatsViewModel(TestAppContainer(db))

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.streak)
        assertEquals(0, state.totalCount)
        assertTrue(state.completedDates.isEmpty())

        db.close()
    }

    @Test
    fun refresh_withCompletedDays_populatesStreakTotalAndHeatmapDates() = runTest {
        val db = newDb(testScheduler)
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(5L, "COUNTER", "Lego Kit", 0b0011111, "t", "b", counterGoal = 1)
        )
        val today = LocalDate.now()
        db.counterDailyProgressDao().upsert(
            CounterDailyProgress(habitInstanceId = 5L, date = today.minusDays(1).toString(), count = 1, completed = true)
        )
        db.counterDailyProgressDao().upsert(
            CounterDailyProgress(habitInstanceId = 5L, date = today.toString(), count = 1, completed = true)
        )
        val viewModel = LegoKitStatsViewModel(TestAppContainer(db))

        viewModel.refresh()
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.streak)
        assertEquals(2, state.totalCount)
        assertEquals(setOf(today.minusDays(1), today), state.completedDates)

        db.close()
    }
}
```

**Note on off-day streak coverage (Eng review finding):** the two tests above only ever use
adjacent calendar days (`today.minusDays(1)`, `today`), so neither exercises the Sun-Thu-spanning-
Fri/Sat scenario Task 1.5 fixes. That scenario is **not** duplicated here — `LegoKitStatsViewModel.refresh()`
calls `LocalDate.now()` internally with no injectable clock (matching `ComputedScheduleStatsViewModel`'s
identical existing pattern, and the same known limitation already tracked in TODOS.md for
`ActivityViewModel`), so a fixed-date test at this layer wouldn't be hermetic. The correct, fully
hermetic coverage for the mask-aware streak fix lives in Task 1.5's `CounterHabitRepositoryTest`
addition, which takes `today` as an explicit parameter — `LegoKitStatsViewModel.refresh()` calls
`habitEngine.currentStreak(instance, today)`, which dispatches straight to
`counterHabitRepository.currentStreak(instance, today)` with no additional logic in between, so
Task 1.5's test is sufficient; duplicating it here would add ceremony without added confidence.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.LegoKitStatsViewModelTest"`
Expected: FAIL — `LegoKitStatsViewModel` doesn't exist yet (compile error).

- [ ] **Step 3: Implement the ViewModel**

`app/src/main/java/com/ziv/reminders/ui/dashboard/LegoKitStatsViewModel.kt`:

```kotlin
package com.ziv.reminders.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.data.LEGO_KIT_HABIT_INSTANCE_ID
import com.ziv.reminders.ui.activity.ActivitySectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

// Mirrors ComputedScheduleStatsViewModel's shape exactly — own small ViewModel over
// DashboardDataSource, not folded into ActivityViewModel (same reasoning: that combined
// ViewModel already carries Exercise/Reading/Tanakh-specific concerns this kind has none of).
class LegoKitStatsViewModel(private val dataSource: DashboardDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivitySectionState())
    val uiState: StateFlow<ActivitySectionState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val instance = dataSource.habitInstanceDao.getById(LEGO_KIT_HABIT_INSTANCE_ID) ?: return@launch

            val dates = HabitStats.parseDates(dataSource.counterHabitRepository.completedDates(instance))
            val streak = dataSource.habitEngine.currentStreak(instance, today)

            _uiState.value = ActivitySectionState(streak, HabitStats.totalCount(dates), dates)
        }
    }

    companion object {
        fun factory(dataSource: DashboardDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>) = LegoKitStatsViewModel(dataSource) as T
            }
    }
}
```

`app/src/main/java/com/ziv/reminders/ui/dashboard/LegoKitStatsScreen.kt` (literal clone of
`ComputedScheduleStatsScreen.kt` with the title and viewmodel type swapped):

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
fun LegoKitStatsScreen(viewModel: LegoKitStatsViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.uiState.collectAsState()
    val today = LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lego Kit") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            HabitStatsSummary("Lego Kit", state)
            if (state.completedDates.isEmpty()) {
                EmptySectionState()
            } else {
                HeatmapGrid(dates = state.completedDates, today = today, onDayClick = {})
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.LegoKitStatsViewModelTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Run the full suite, then commit**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests still green).

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/LegoKitStatsViewModel.kt app/src/main/java/com/ziv/reminders/ui/dashboard/LegoKitStatsScreen.kt app/src/test/java/com/ziv/reminders/ui/dashboard/LegoKitStatsViewModelTest.kt
git commit -m "feat: add the Lego Kit long-press Statistics screen"
```

---

### Task 4: Navigation wiring

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/MainActivity.kt`

**Interfaces:**
- Consumes: `LegoKitStatsViewModel.factory` (Task 3), `LegoKitStatsScreen` (Task 3),
  `DashboardScreen`'s new `onOpenLegoKitStats` parameter (Task 2).

No test precedent at this layer (Activity-level Compose wiring) — verified via `assembleDebug`
and Task 5's on-device check.

- [ ] **Step 1: Wire the ViewModel, destination, and DashboardScreen parameter**

In `MainActivity.kt`, add:

```kotlin
val legoKitStatsViewModel: LegoKitStatsViewModel =
    viewModel(factory = LegoKitStatsViewModel.factory(container))
```

Add `onOpenLegoKitStats = { navController.navigate("legoKitStats") }` to the `DashboardScreen(...)`
call.

Add a new destination alongside `cppWeeklyStats`:

```kotlin
composable("legoKitStats") {
    LegoKitStatsScreen(
        viewModel = legoKitStatsViewModel,
        onBack = { navController.popBackStack() },
    )
}
```

Add the matching imports (`LegoKitStatsScreen`, `LegoKitStatsViewModel`).

- [ ] **Step 2: Run the full suite and build, then commit**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: PASS (all existing tests still green).

Run: `./gradlew.bat :app:assembleDebug`
Expected: PASS.

```bash
git add app/src/main/java/com/ziv/reminders/MainActivity.kt
git commit -m "feat: wire Lego Kit stats navigation into MainActivity"
```

---

### Task 5: On-device manual verification — DONE (2026-07-27, real device, adb-driven)

- [x] Installed the debug build and confirmed:
  - [x] The dashboard shows 5 rows, "Lego Kit" as the 5th (red placeholder icon, "Add one kit").
  - [x] On a Sun–Thu day (verified on the actual current day, Monday): tapping the row marked it
    "✓ Added," dimmed it (icon/dot/text faded), streak went 0d → 1d, and a second tap was a
    confirmed no-op (streak stayed 1d, no Snackbar appeared).
  - [x] Long-press → "Statistics" opened a screen showing "Streak: 1 day", "Total: 1 day", and a
    heatmap with exactly one green cell (today).
  - [x] Long-press → "Undo" appeared (alongside Statistics and Cancel) only after marking, and
    selecting it un-dimmed the row, reset the streak to 0d, restored the red dot and "Add one
    kit" text, and made it tappable again.
  - [~] Friday/Saturday dimmed-non-tappable state: **not exercised live** — changing the real
    device's system date was judged too invasive for a quick UI check on the user's actual
    phone (risks disrupting other apps/alarms/account sync, and most non-rooted devices can't do
    it via `adb shell` anyway). Verified instead by code-path reasoning: `LegoKitHabitRow`'s
    `isEnabledToday = isEnabledDay(today, habit.enabledDaysMask)` uses the exact same
    `isEnabledDay` helper already shipping in production for the Reading row's Sun-Thu mask, and
    `CounterHabitRepositoryTest.currentStreak_skipsDisabledDays_forASunThuMaskedInstance` (Task
    1.5) hermetically proves the mask math is correct for a Sun-Thu instance spanning Fri/Sat
    with fixed, verified calendar dates — not dependent on live device date at all.
  - [x] No notification fires for Lego Kit on Friday/Saturday — verified by inspection:
    `HabitScheduler.scheduleRemindersForToday`'s existing `isEnabledDay` guard is unchanged by
    this plan and applies uniformly to every `HabitInstance`, Lego Kit included.

---

## CEO Review (Phase 1) — via /autoplan, SELECTIVE EXPANSION mode

### 0A. Premise Challenge — CONFIRMED by user
1. Right problem: implement exactly what the approved `/office-hours` design doc scoped (5th
   habit row, reuse `COUNTER` + `enabledDaysMask`, no new `HabitKind`). Agreed.
2. Direct outcome: this approach delivers the working row without indirection. Agreed.
3. Doing nothing leaves a real (if minor) personal want unaddressed — not hypothetical. Agreed.

### 0B. Existing Code Leverage
Every sub-problem maps to existing code — see "What already exists" below. Nothing in this plan
rebuilds anything; it is 100% additive at the Compose UI layer plus one new small ViewModel.

### 0C. Dream State Mapping
```
CURRENT STATE                          THIS PLAN                           12-MONTH IDEAL
4 habit rows (Exercise/Reading/  --->  5th row (Lego Kit), COUNTER   --->  If a 2nd simple
Tanakh/C++ Weekly). Dispatch by        kind reused, dispatch                one-tap daily-
HabitStatus subtype; instance-ID       restructured to route by             mission row ever
checks exist only inside               instanceId within CounterStatus,     shows up, extract
CounterHabitRow's long-press-menu      standalone Undo callback added,      a shared
gate (hasExerciseDetailMenu).          new Statistics screen cloned         SimpleMissionRow +
                                        from C++ Weekly's shape.             Statistics/Undo
                                                                             menu component.
                                                                             Until then, one-off
                                                                             composables per row
                                                                             stay correct (see
                                                                             TODOS.md's repeated
                                                                             anti-premature-
                                                                             generalization norm).
```
This plan moves toward the ideal: it's the second real-world instance of "dispatch by
instanceId inside a shared `HabitStatus` branch," which is exactly the precedent a future
generalization decision would need.

### 0C-bis. Implementation Alternatives — reconfirmed from the design doc
The three approaches (A: reuse `CounterHabitRow` as-is; B: dedicated composable [chosen]; C:
generalized "simple mission row") were already evaluated and decided in the approved design doc.
No new information from writing this plan changes that analysis. **Auto-decided (P1
completeness):** Approach B — A is incomplete (doesn't disable), C is premature generalization
for one use case. Not a close call; no taste decision needed here.

### 0D. Selective Expansion Analysis
**Complexity check:** 8 files touched (after the Task 2 fix above), 1 new class
(`LegoKitStatsViewModel`) + 2 new composables — under the >8-files / >2-new-classes smell
threshold. No complexity flag.

**Minimum set:** Already minimal for correctness — the `enabledDaysMask` threading fix (added
above) is not deferrable; it's required for the "dimmed on off-day" behavior the design doc's
Premise 5 committed to. No further reduction available without breaking the spec.

**Expansion scan (candidates considered, not added — see Deferred to TODOS.md below):**
1. Notification "Mark added" action button (skip the app entirely from the notification) —
   genuinely new `PendingIntent`/`BroadcastReceiver` infrastructure, not a small touch. Matches
   an identical deferred TODOS.md entry already written for C++ Weekly for the same reason.
2. "New streak record" callout on the stats screen — would be new functionality not present in
   *any* of the 4 existing stats screens (including the one being cloned), so adding it only to
   Lego Kit would be inconsistent scope creep rather than reuse. Better scoped as an app-wide
   TODOS.md candidate if ever picked up, not smuggled into this plan.

**Auto-decided (P2 boil-lakes / P3 pragmatic):** both candidates are outside this plan's blast
radius (candidate 1 needs new infra; candidate 2 spans all 4 existing stats screens, not just
this one) → deferred to TODOS.md, not added to scope.

### 0E. Temporal Interrogation
Given the plan's size (CC-compressed: ~30-45 min total, not the 6+ human-hours this section
normally interrogates), the one real ambiguity that would have bitten an implementer mid-task —
the hardcoded `enabledDaysMask` placeholder — was caught and resolved in Task 2 above before
implementation starts. No other foundational decisions remain open.

### 0F. Mode Confirmation
**SELECTIVE EXPANSION** (auto-selected: feature enhancement on an existing system). Approach B
from 0C-bis carries forward as this plan's scope for all sections below.

### CEO Dual Voices

Codex: unavailable (`CODEX_NOT_AVAILABLE` — not installed in this environment). Tagged
`[subagent-only]` per the degradation matrix.

**CLAUDE SUBAGENT (CEO — strategic independence):**
Independent cold read (no access to this conversation, only the plan file + repo code).
Verified every factual claim in the plan against the actual source (`HabitRowUiState`,
`hasExerciseDetailMenu`, commit `6647fd2`, `CounterHabitRepository.completedDates`,
`isEnabledDay`) — all checked out. Findings:
1. Right problem: yes — minimal-scope personal utility add-on, correctly sized, not a candidate
   for "10x" reframing.
2. Premises: mostly stated and verified; flagged the same `enabledDaysMask` hardcoding gap
   independently (medium severity, since no test exercised the Friday/Saturday case) — **fixed
   above in this revision** before the subagent's finding was even read, since the same gap was
   caught during this session's own drafting.
3. Six-month regret: low risk. Noted a soft UX risk — Undo lives only behind long-press, no
   toast/snackbar hint, so a user who forgets the gesture might find an accidental tap
   inconvenient to reverse. Minor, self-inflicted, easily fixed later if it's ever actually a
   problem — not worth blocking on for a personal app.
4. Alternatives: correctly rejects A and C for the reasons already in the design doc; no
   under-analyzed alternative found.
5. Competitive risk: correctly N/A for a personal app.

**CEO DUAL VOICES — CONSENSUS TABLE:**
```
═══════════════════════════════════════════════════════════════
  Dimension                           Claude  Codex  Consensus
  ──────────────────────────────────── ─────── ─────── ─────────
  1. Premises valid?                   YES     N/A    CONFIRMED (subagent-only)
  2. Right problem to solve?           YES     N/A    CONFIRMED (subagent-only)
  3. Scope calibration correct?        YES     N/A    CONFIRMED (subagent-only)
  4. Alternatives sufficiently explored?YES    N/A    CONFIRMED (subagent-only)
  5. Competitive/market risks covered? N/A     N/A    N/A (personal app)
  6. 6-month trajectory sound?         YES*    N/A    CONFIRMED (subagent-only)
═══════════════════════════════════════════════════════════════
* with one noted, accepted, non-blocking soft-UX risk (Undo discoverability).
```
No user challenges (both — the only — voice agrees with the user's stated direction on every
dimension). No taste decisions from this phase (the one real finding was fixed inline, not left
as a choice).

### Review Sections 1-10

**Section 1 (Architecture):** Dependency graph — `LegoKitHabitRow`/`LegoKitStatsViewModel`
depend only on existing, already-generic components (`CounterHabitRepository`, `HabitEngine`,
`RowLongPressMenu`, `isEnabledDay`). No new coupling introduced.
```
DashboardScreen ──> HabitRow (dispatch) ──> LegoKitHabitRow ──> CounterHabitRepository (existing)
                                        └──> CounterHabitRow  ──> HabitEngine (existing)
LegoKitStatsScreen ──> LegoKitStatsViewModel ──> DashboardDataSource (existing interface, no change)
```
State machine (row): {tappable} ⇄ {dimmed-completed} on tap/day-rollover; {dimmed-off-day}
independent of completion state (off-day + completed-today is not a reachable state — a new
calendar day always starts `completed = false` for that date, and the tap gate itself requires
`isEnabledToday`, so the two dimming reasons never need to be visually distinguished). Rollback:
trivial `git revert` — no migration, no schema change. No new single point of failure.

**Section 2 (Error & Rescue Map):**
```
METHOD/CODEPATH                        | WHAT CAN GO WRONG        | RESCUED?
----------------------------------------|--------------------------|----------
CounterHabitRepository.increment/undo   | Room I/O failure         | N — pre-existing, app-wide
                                         |                          |     gap (TODOS.md, not
                                         |                          |     introduced by this plan)
LegoKitStatsViewModel.refresh           | instance not found       | Y — early return (matches
                                         |                          |     ComputedScheduleStatsViewModel
                                         |                          |     exactly)
```
No new exception classes, no new gaps beyond the pre-existing app-wide one (already tracked in
TODOS.md as its own separate item, correctly out of this plan's blast radius).

**Section 3 (Security):** N/A — local-only single-user app, no network calls, no auth
boundaries, no new PII (habit-completion dates, same sensitivity class as the other 4 habits
already stored identically). No new attack surface.

**Section 4 (Data Flow & Interaction Edge Cases):**
Interaction edge cases checked: double-tap (gated by `!status.completed`), navigate-away during
long-press menu (dialog just dismisses, no state corruption), Undo-with-nothing-to-undo
(menu option only rendered when `status.completed`), zero-history stats screen (`EmptySectionState`,
reused). One accepted, non-blocking edge case found and **not fixed**, matching this codebase's
own documented risk tolerance for similar races elsewhere (see `CounterHabitRow`'s own accepted
comment about a pending-snackbar race): two extremely fast taps before recomposition could both
fire `increment()`, pushing the count to 2/1 instead of 1/1 — cosmetically harmless (`completed`
stays true), no data corruption, matches Exercise's own already-accepted multi-increment
behavior at the repository level. Auto-decided (P6 pragmatic): accept, do not add
debounce/transaction logic for a single-user personal app.

**Section 5 (Code Quality):** `LegoKitHabitRow`'s Row/Image/Dot/Column/Text shape duplicates the
pattern already used identically by all 4 existing row composables — this is the established
convention, not a new DRY violation. Naming (`LegoKitHabitRow`/`LegoKitStatsViewModel`/
`LegoKitStatsScreen`) mirrors `ComputedScheduleHabitRow`/`ComputedScheduleStatsViewModel`/
`ComputedScheduleStatsScreen` exactly. No over- or under-engineering found.

**Section 6 (Test Review):** Covered in full by Task 2 Step 1 (mask threading),
Task 2 Step 4 (dispatch), Task 3 Steps 1-2 (stats streak/heatmap) — see the Eng phase's Test
Diagram below for the complete mapping. No flakiness risk (the one `LocalDate.now()` usage in
`LegoKitStatsViewModel` matches `ComputedScheduleStatsViewModel`'s already-accepted pattern).

**Section 7 (Performance):** All queries are single-row/single-instance Room reads already used
identically elsewhere — no N+1, no new indexes needed, no caching warranted at this app's scale
(5 dashboard rows, one user).

**Section 8 (Observability):** This app has exactly one logging call in total (`HabitSeeding`'s
`Log.e`, deliberately singular per that file's own comment) — consistent with that established
minimalism, no new logging is warranted for this plan.

**Section 9 (Deployment):** No migration, no feature flag needed (single manual-install personal
app, no staged rollout target). Rollback: `git revert` + reinstall.

**Section 10 (Long-Term Trajectory):** Reversibility 5/5 — deleting the seed row and the new
files fully removes the feature with no cleanup debt. No new path dependency; if anything, this
strengthens the existing "dispatch by instanceId" precedent for any future similar row.

### NOT in scope
- Notification "Mark added" action button — deferred to TODOS.md (matches existing C++ Weekly
  precedent for the same class of work).
- App-wide "new streak record" callout — deferred to TODOS.md as a cross-cutting candidate, not
  scoped to just this row.
- The pre-existing app-wide "no error handling around Room reads in ViewModels" gap — already
  tracked in TODOS.md, out of this plan's blast radius.

*(Corrected during Design phase: the off-day status-dot color was initially listed here as a
deferred cross-cutting item. The Design review's independent subagent voice correctly identified
that it's actually in this plan's own blast radius, not inherited from Reading/Tanakh — see
Design Review Pass 3. It was fixed in Task 2, not deferred.)*

### What already exists (leveraged, not rebuilt)
`HabitKind.COUNTER`, `HabitInstance.enabledDaysMask`/`counterGoal`, `CounterHabitRepository`
(increment/undoIncrement/completedDates/currentStreak), `HabitEngine` dispatch,
`StreakCalculator`, `HabitScheduler`'s `isEnabledDay` notification guard,
`ActivitySectionState`/`HabitStatsSummary`/`HeatmapGrid`/`EmptySectionState`,
`RowLongPressMenu`/`RowMenuOption`, `DashboardDataSource`.

### Completion Summary (CEO Phase)
Mode: SELECTIVE EXPANSION. 1 real finding (enabledDaysMask hardcoding) — fixed inline in Task 2
before this phase's dual voice even ran (independently re-confirmed by the subagent). 2 expansion
candidates scanned, both deferred to TODOS.md as outside blast radius. 0 taste decisions. 0 user
challenges. Dual voices: Claude subagent only (Codex unavailable), 5/6 dimensions CONFIRMED
(1 N/A — no competitive dimension for a personal app).

---

## Design Review (Phase 2) — via /autoplan

**Classifier:** APP UI (task-focused dashboard row + stats screen, native Android/Compose,
Material 3 defaults) — not marketing/landing. The AI-slop blacklist, hero rules, and landing-page
rules do not apply; this plan reuses an existing, already-shipped Row-based dashboard pattern
verbatim, not a web page.

**0A. Initial Design Rating: 8/10.** Specific, concrete copy ("Add one kit" / "✓ Added"),
exact composable structure mirroring the 4 existing rows, and an explicit dimming rule. What a
10 looks like: the same plan, plus an explicit interaction-state table (added below) and a
decision on off-day status-dot color (surfaced below, deferred).

**0B. DESIGN.md status:** none exists in this repo. The app uses ad-hoc `MaterialTheme` plus two
custom semantic colors (`GoalGreen`, `StatusOrange`) — this plan introduces zero new colors,
tokens, or components, so there is nothing new to misalign.

**0C. Existing design leverage:** `HabitStatusDot`, `RowLongPressMenu`/`RowMenuOption`,
`HeatmapGrid`, `HabitStatsSummary`, `EmptySectionState`, the `GoalGreen`/error color convention —
100% reused, matching this plan's Architecture section.

**0D. Focus areas:** auto-decided (P1, all dimensions) — reviewed all 7 passes below; no
narrowing needed for a plan this small.

### Design Dual Voices

Codex: unavailable, tagged `[subagent-only]`.

**CLAUDE SUBAGENT (design — independent review):** see findings folded into Passes 1-3 and 7
below (marked "subagent-confirmed" where the independent read matched this session's own
analysis).

### Pass 1: Information Architecture — 9/10
Row hierarchy: icon → status dot → name+streak (left), status text (right) — identical order to
all 4 existing rows. Stats screen: title bar → streak/total summary → heatmap — identical to
`ComputedScheduleStatsScreen`.
```
Dashboard ("Today")                    Lego Kit Stats
┌─────────────────────────────┐        ┌─────────────────────────────┐
│ [icon] ● Lego Kit      Add   │  tap   │ ← Lego Kit                  │
│         Streak: 3d    one kit│ ─────▶ │ Streak: 3 · Total: 12       │
└─────────────────────────────┘        │ [heatmap grid]              │
        long-press                     └─────────────────────────────┘
        ↓
   [Statistics] [Undo] [Cancel]
```
No deviation from established hierarchy — nothing to fix.

### Pass 2: Interaction State Coverage — 7/10
```
FEATURE       | LOADING              | EMPTY                | ERROR                | SUCCESS            | PARTIAL
--------------|----------------------|-----------------------|-----------------------|---------------------|--------
Row tap       | N/A (local DB, sync- | N/A                   | not handled (pre-     | "✓ Added", dimmed,  | N/A (atomic
              | feeling, instant)    |                       | existing app-wide gap,| green dot           | single increment)
              |                      |                       | see CEO Section 2)    |                     |
Stats screen  | brief blank Scaffold | EmptySectionState     | not handled (same     | streak+heatmap      | N/A
              | before first refresh | (existing, reused)    | pre-existing gap)     | rendered            |
              | (matches the other 3|                       |                       |                     |
              | stats screens exactly)|                      |                       |                     |
Long-press    | N/A                  | N/A                   | N/A                   | dialog renders      | N/A
menu          |                      |                       |                       |                     |
Undo option   | N/A                  | not offered unless    | not handled (same     | row reverts to      | N/A
              |                      | `status.completed`    | pre-existing gap)     | tappable            |
```
One finding: off-day dimmed state was previously unspecified in prose — now made explicit in
this table. No new error-handling gap introduced beyond the pre-existing app-wide one (already
tracked in TODOS.md, out of this plan's blast radius).

### Pass 3: User Journey & Emotional Arc — 8/10
```
STEP                          | USER FEELS              | PLAN SPECIFIES?
-------------------------------|--------------------------|------------------
Sees "Add one kit" row         | neutral/motivated        | yes, exact copy given
Taps, sees "✓ Added," dimmed   | small satisfaction       | yes
Sees row dimmed on Fri/Sat     | possible mild confusion  | partially — dimmed, but
                                | ("am I behind?")         | see finding below
Long-presses, sees the menu    | in control               | yes, exact options given
```
**Finding — FIXED in this revision, HIGH severity per the subagent's independent review (a
stronger call than this session's own initial low-severity/defer framing, and the correct one):**
on an off-day, the first draft dimmed the row but left `HabitStatusDot` red
(`status.completed == false`) and the text reading "Add one kit" — identical content to a live,
actionable row, just faded. Red is reserved everywhere else in this codebase for "behind
schedule, needs attention" (`ScheduleCursorHabitRow`/`ComputedScheduleHabitRow` both only use
error-red for a real `dueCount`). **This session's initial CEO-phase framing incorrectly treated
this as a pre-existing, cross-cutting Reading/Tanakh issue and deferred it — that was wrong on
the facts.** Reading and Tanakh never dim at all (no alpha treatment exists for them), so this
specific "dimmed-and-still-red" combination is new, introduced by *this* plan, not inherited —
squarely in blast radius, not a cross-cutting concern. **Corrected decision (P2 boil lakes):**
fixed directly in Task 2's `LegoKitHabitRow` — the dot now renders `MaterialTheme.colorScheme.outline`
(neutral) when `!isEnabledToday`, and the status text reads "Not today" instead of "Add one kit"
in that state, so a not-due day is visually distinct from an overdue one.

### Pass 4: AI Slop Risk — N/A (not a marketing surface)
This is a native Android dashboard row using an already-shipped Material3 pattern verbatim —
none of the AI-slop blacklist patterns (gradients, 3-column grids, icon-in-circle decoration,
hero copy) apply to a `Row` composable copying an existing row's exact shape.

### Pass 5: Design System Alignment — 10/10
No DESIGN.md; the app's only "system" is `MaterialTheme` + `GoalGreen`/`StatusOrange`. This plan
adds zero new tokens/colors/components, so there is nothing to misalign.

### Pass 6: Responsive & Accessibility — 7/10 (matches existing baseline, not worse)
Single form factor (phone) — same as all 4 existing rows, no new responsive gap. Touch target:
full-width row, same height class as existing rows (icon 40dp + padding), meets the 44px
minimum, consistent with existing rows. `contentDescription = null` on the icon `Image` matches
all 4 existing rows exactly (the row's `Text` composables carry the TalkBack-accessible name) —
not a new gap, not this plan's to fix. `combinedClickable` makes the row focusable/announced
correctly, same as existing rows.

### Pass 7: Unresolved Design Decisions
```
DECISION NEEDED                    | IF DEFERRED, WHAT HAPPENS
------------------------------------|---------------------------------------------------
Off-day status-dot color            | Deferred to TODOS.md (app-wide, see Pass 3) — ships
                                     | with the same red-dot behavior Reading/Tanakh already
                                     | have; not a regression, just not improved here.
Exact Lego-cube icon artwork        | Placeholder geometric shape ships (per Global
                                     | Constraints); swappable later, zero code impact.
```
Neither blocks implementation — both already carry an explicit resolution (defer / placeholder).

### Design Litmus Scorecard (App UI dimensions, N/A where marketing-only)
```
═══════════════════════════════════════════════════════════════
  Dimension                              Claude  Codex  Consensus
  ──────────────────────────────────────  ─────── ─────── ─────────
  1. Information hierarchy clear?         9/10    N/A    CONFIRMED (subagent-only)
  2. Interaction states specified?        7/10    N/A    CONFIRMED (subagent-only)
  3. Emotional arc considered?            9/10*   N/A    CONFIRMED (subagent-only)
                                                          * after fixing the off-day dot/text
  4. AI-slop-free?                        N/A     N/A    N/A (not a marketing surface)
  5. Design system alignment?             10/10   N/A    CONFIRMED (subagent-only)
  6. Responsive & accessible?             7/10    N/A    CONFIRMED (subagent-only,
                                                          matches existing baseline)
  7. Unresolved decisions surfaced?       YES     N/A    CONFIRMED (subagent-only)
═══════════════════════════════════════════════════════════════
```

### Completion Summary (Design Phase)
Overall: 8.5/10 average across scored dimensions. One finding (off-day status-dot/text
ambiguity) — the independent subagent voice rated it more severely than this session's own
initial pass and was right to: it's new behavior this plan introduces, not an inherited gap.
Fixed directly in Task 2. Zero taste decisions, zero user challenges.

---

## Eng Review (Phase 3) — via /autoplan

### Step 0: Scope Challenge
Every sub-problem maps to existing, already-generic code (see "What already exists" in the CEO
section) — nothing is rebuilt by choice. One thing *was* rebuilt by necessity: independent Eng
review found that `CounterHabitRepository.currentStreak` was never actually generalized for
`enabledDaysMask` despite the CEO phase's claim that it was — see Task 1.5 and the critical
finding below. Complexity check: 9 files touched (8 + `CounterHabitRepository.kt`/
`CounterHabitRepositoryTest.kt` from Task 1.5 — at the >8 threshold, but this is a bug fix to
existing shared infrastructure the plan depends on for correctness, not new scope; not treated as
a complexity smell). No scope reduction found otherwise — the plan is minimal for correctness
after all fixes above.

### Eng Dual Voices

Codex: unavailable, tagged `[subagent-only]`.

**CLAUDE SUBAGENT (eng — independent review):** cold read against the real repo source (Room
2.7.1/KSP, JUnit4+Robolectric). Verified every constructor/interface/signature the plan
references. Findings:

1. **CRITICAL — streak math silently breaks every Friday/Saturday.** `CounterHabitRepository.currentStreak`
   delegated to `HabitStats.currentStreak`, a naive consecutive-calendar-day walk with no
   `enabledDaysMask` awareness — unlike `TimerHabitRepository`/`ScheduleCursorRepository`, which
   both route through the mask-aware `StreakCalculator.calculate`. `StreakCalculator.kt`'s own
   doc comment confirms this was a deliberate, narrow assumption: *"Deliberately not applied to
   Counter's existing... currentStreak — its enabledDaysMask is always all-days, so the two are
   behaviorally identical for it anyway."* That assumption broke the moment this plan seeded a
   second `COUNTER` instance with a Sun-Thu mask. **Fixed as Task 1.5** — see that task for the
   full fix, hermetic test (fixed dates, not `LocalDate.now()`), and verification that Exercise's
   existing all-days streak tests still pass unchanged.
2. **HIGH — the original test plan couldn't have caught #1.** `LegoKitStatsViewModelTest`'s
   streak test only ever used adjacent calendar days, which can never exercise an off-day gap.
   Addressed: Task 1.5 adds the correct, hermetic, fixed-date test at the
   `CounterHabitRepository` layer (the right layer, since `LegoKitStatsViewModel.refresh()` isn't
   clock-injectable — see the note added to Task 3 explaining why duplicating this test at the
   ViewModel layer would not be hermetic).
3. **MEDIUM — compile-breaking import gap in the plan's own dispatch test snippet.** Task 2's
   `DashboardDispatchTest` addition referenced `CPP_WEEKLY_HABIT_INSTANCE_ID`, which the real file
   doesn't currently import — an implementer following the snippet literally would hit a compile
   error. **Fixed** — Task 2's instructions now call out both missing imports explicitly.
4. Everything else verified clean: the dispatch restructure's smart-cast pattern compiles
   identically to the existing `CounterHabitRow` branch; every other constructor/interface/field
   the plan references matches the real source exactly; the accepted fast-double-tap race is
   correctly reasoned and low-severity; security is genuinely N/A (no new I/O boundary);
   `HabitRowUiState.enabledDaysMask` has exactly one construction site, so that addition is
   low-risk.

**ENG DUAL VOICES — CONSENSUS TABLE:**
```
═══════════════════════════════════════════════════════════════
  Dimension                           Claude  Codex  Consensus
  ──────────────────────────────────── ─────── ─────── ─────────
  1. Architecture sound?               YES     N/A    CONFIRMED (subagent-only)
  2. Test coverage sufficient?         NO→YES* N/A    CONFIRMED after fix (subagent-only)
  3. Performance risks addressed?      YES     N/A    CONFIRMED (subagent-only)
  4. Security threats covered?         N/A     N/A    N/A (no network/auth boundary)
  5. Error paths handled?              YES**   N/A    CONFIRMED (subagent-only)
  6. Deployment risk manageable?       YES     N/A    CONFIRMED (subagent-only)
═══════════════════════════════════════════════════════════════
* found 2 real coverage gaps (findings 1-2), both fixed via Task 1.5 before this table was written.
** modulo the pre-existing, already-tracked app-wide Room error-handling gap — not this plan's.
```

### Section 1: Architecture Review
```
DashboardScreen (composable)
  └─ HabitRow (dispatch, RESTRUCTURED)
       ├─ CounterStatus branch, now itself branches by instanceId:
       │    ├─ isLegoKitRow(id) → LegoKitHabitRow (NEW)
       │    │      ├─ HabitStatusDot (existing, reused)
       │    │      ├─ RowLongPressMenu/RowMenuOption (existing, reused)
       │    │      └─ isEnabledDay (existing, reused — newly imported into this file)
       │    └─ else → CounterHabitRow (existing, UNCHANGED)
       ├─ TimerStatus branch → TimerHabitRow (existing, unchanged)
       ├─ ScheduleCursorStatus branch → ScheduleCursorHabitRow (existing, unchanged)
       └─ ComputedScheduleStatus branch → ComputedScheduleHabitRow (existing, unchanged)

DashboardViewModel (methods UNCHANGED — onIncrement/onUndoIncrement already instance-ID-generic)
  └─ refresh() — ONE new field populated: HabitRowUiState.enabledDaysMask

CounterHabitRepository.currentStreak (FIXED, Task 1.5)
  └─ now routes through StreakCalculator.calculate (mask-aware), same as Timer/ScheduleCursor

LegoKitStatsScreen (NEW) ── LegoKitStatsViewModel (NEW)
  └─ DashboardDataSource (existing interface, UNCHANGED — already exposes everything needed)
       ├─ counterHabitRepository.completedDates (existing)
       └─ habitEngine.currentStreak (existing, now correctly mask-aware for COUNTER too)

MainActivity: NavHost + composable("legoKitStats") (NEW leaf destination, no graph restructuring)
```
**Coupling:** zero new coupling between kind-specific code — `LegoKitHabitRow` and
`CounterHabitRow` are sibling branches inside the same `when`, sharing no state; both depend only
on already-generic interfaces. The `CounterHabitRepository.currentStreak` fix is a pure
implementation swap (same signature), affecting every `COUNTER` instance uniformly — not new
coupling, a correctness fix to shared infrastructure.
**Scaling / SPOF:** N/A — single-user local app, same single Room DB every other habit already
uses; no new single point of failure.
**Rollback:** trivial `git revert` — no migration, no schema change (confirmed: `AppDatabase`
version stays at 7, entities list unchanged).

### Section 2: Code Quality Review
No DRY violation — `LegoKitHabitRow`'s Row/Image/Dot/Column/Text shape is the established
5th-instance-of-a-pattern already used identically by `CounterHabitRow`/`TimerHabitRow`/
`ScheduleCursorHabitRow`/`ComputedScheduleHabitRow`. Naming mirrors `ComputedSchedule*` exactly.
Cyclomatic complexity: the dot-color/status-text `when` branches 3 ways (completed / off-day /
else) — well under the 5-branch flag threshold. No over-engineering (no new abstraction beyond
what's needed) and no under-engineering beyond the one accepted race (CEO Section 4) — the
critical under-engineering that *was* found (Task 1.5's bug) is now fixed, not accepted.

### Section 3: Test Review
Full coverage matrix, test ambition check, flakiness/pyramid assessment, and load notes written
to the test plan artifact: `~/.gstack/projects/Reminders/zivk-main-test-plan-20260727-002000.md`
(updated to include Task 1.5's coverage). Summary: every new unit-testable codepath
(`enabledDaysMask` threading, dispatch, mask-aware streak calculation, stats streak/heatmap) has
a new test; every Compose-UI-only codepath (tap-gating, dimming, menu visibility) has no
unit-test precedent app-wide and is covered by Task 5's on-device checklist instead, matching
this repo's own established constraint.

### Section 4: Performance Review
All new/changed queries are single-row/single-instance Room reads, identical in shape to every
existing habit kind's queries — no N+1, no new index needed, no caching warranted at this app's
scale. `StreakCalculator.calculate`'s bounded lookback (`MAX_LOOKBACK_DAYS = 3650`) is existing,
already-shipped code, unchanged by this plan. No new background jobs, no new connection pool
pressure.

### Completion Summary (Eng Phase)
3 findings from independent review: 1 critical (mask-unaware streak math, fixed as Task 1.5),
1 high (test gap that would have hidden the critical bug, fixed alongside it), 1 medium (missing
test import, fixed inline in Task 2). All three fixed in this revision, none deferred. 0 taste
decisions, 0 user challenges. Architecture, code quality, and performance all clean beyond the
fixed findings.

### Failure Modes Registry (consolidated across all 3 phases)
```
FAILURE MODE                              | PHASE FOUND | STATUS
-------------------------------------------|-------------|------------------
enabledDaysMask hardcoded, not threaded    | CEO         | FIXED (Task 2)
Off-day dot/text reused "overdue" red      | Design      | FIXED (Task 2)
CounterHabitRepository.currentStreak       | Eng         | FIXED (Task 1.5)
  ignores enabledDaysMask entirely         |             |
Test gap that would've hidden the above    | Eng         | FIXED (Task 1.5/3)
Missing test import (compile error)        | Eng         | FIXED (Task 2)
Fast-double-tap race (goal=1 → 2/1)        | CEO         | ACCEPTED (cosmetic
                                            |             | only, matches
                                            |             | existing precedent)
Pre-existing app-wide Room error handling  | CEO         | OUT OF SCOPE
gap                                        |             | (tracked in TODOS.md)
```
No open critical or high-severity gaps remain — every CRITICAL/HIGH finding across all 3 phases
was fixed in this revision, not deferred.

### Cross-Phase Themes
No cross-phase themes — each phase's independent voice found a distinct, real issue in a
different layer (CEO: data threading; Design: visual state semantics; Eng: streak-calculation
math), not the same concern recurring across phases. Notably, though, all three real findings
share one underlying pattern worth naming: this plan is the *first* time a second `COUNTER`-kind,
non-all-days-masked habit has been added to this codebase, and each phase independently found a
different place where "Exercise was the only `COUNTER` instance, and it's always-enabled" had
been silently baked in as an assumption (the UI dispatch, the dimming visual logic, and the
streak math). That's a useful signal for any *future* second-instance-of-an-existing-kind plan:
audit every place the existing kind's repository/engine code assumed "there's only ever been one
of these" before assuming reuse is automatically safe.

---

<!-- AUTONOMOUS DECISION LOG -->
## Decision Audit Trail

| # | Phase | Decision | Classification | Principle | Rationale | Rejected |
|---|-------|----------|-----------------|-----------|-----------|----------|
| 1 | CEO | Fix `enabledDaysMask` hardcoding in Task 2 (thread through `HabitRowUiState`) | Mechanical | P2 (boil lakes) | In blast radius, <1 day, required for the spec's off-day dimming behavior | — |
| 2 | CEO | Approach B (dedicated composable) reconfirmed at plan level | Mechanical | P1 (completeness) | A is incomplete, C is premature generalization; not close | Approach A, Approach C |
| 3 | CEO | Defer notification "Mark added" action button | Mechanical | P3 (pragmatic) | Outside blast radius — new PendingIntent/BroadcastReceiver infra, matches existing C++ Weekly deferral | Adding to this plan |
| 4 | CEO | Defer app-wide "new streak record" callout | Mechanical | P3 (pragmatic) | Spans all 4 existing stats screens, not scoped to just this row | Adding to this plan |
| 5 | CEO | Accept the fast-double-tap race (goal=1 could become 2/1) without a fix | Mechanical | P6 (pragmatic) | Matches this codebase's own accepted risk tolerance for similar races (CounterHabitRow's documented precedent); cosmetic only, no data corruption | Adding debounce/transaction logic |
| 6 | Design | Fix off-day status-dot color + status text (neutral dot, "Not today") instead of deferring | Taste (subagent disagreed with this session's initial framing, with valid reasoning) | P2 (boil lakes) | New behavior this plan introduces (first row to dim for a schedule reason), not inherited from Reading/Tanakh — in blast radius, S effort | Deferring to TODOS.md (this session's own initial, incorrect call) |
| 7 | Eng | Fix `CounterHabitRepository.currentStreak` to route through `StreakCalculator` (Task 1.5) | Mechanical | P2 (boil lakes) | Critical correctness bug this plan's own seeding would expose; safe for Exercise's existing all-days mask (verified against existing tests) | Shipping with the bug, deferring to a follow-up |
| 8 | Eng | Add hermetic fixed-date streak test at the repository layer, not the ViewModel layer | Mechanical | P5 (explicit over clever) | `LegoKitStatsViewModel.refresh()` isn't clock-injectable (matches existing `ActivityViewModel` limitation); testing at the right layer avoids a non-hermetic test | Duplicating the test through the ViewModel |
| 9 | Eng | Add missing `CPP_WEEKLY_HABIT_INSTANCE_ID` import to Task 2's dispatch test instructions | Mechanical | P5 (explicit over clever) | Compile-breaking gap in the plan's own snippet | — |

---
