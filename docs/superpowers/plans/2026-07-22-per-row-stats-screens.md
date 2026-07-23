# Per-Row Statistics — Dedicated Stats Screens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each dashboard row (Exercise, Reading, Tanakh) a long-press "Statistics" option that opens a dedicated, habit-scoped stats screen — replacing Exercise's current "opens the shared all-3-habit Activity screen" behavior, adding a menu to Reading (Reset today / Statistics) in place of its current direct-to-reset-confirm long-press, and adding a long-press menu to Tanakh (Statistics only) where none exists today.

**Architecture:** Three new/updated Compose screens (`ExerciseStatsScreen`, `ReadingStatsScreen`, `TanakhStatsScreen`), each a `Scaffold` + `TopAppBar` (habit name, back button) wrapping a scrollable `Column` that reuses the same section composables `ActivityScreen.kt` already built (`HabitStatsSummary`, `SectionCaption`, `EmptySectionState`, `HeatmapGrid`, `ReadingDayDetailDialog`, `TanakhDayDetailDialog`, `ExerciseActivitySection`). Three new NavHost destinations route to them. `DashboardScreen.kt`'s `TimerHabitRow` (Reading) and `ScheduleCursorHabitRow` (Tanakh) gain the same `RowLongPressMenu` mechanism `CounterHabitRow` (Exercise) already uses.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Navigation-Compose, manual DI via `AppContainer`.

## Global Constraints

- No new Room schema. No new DI wiring — reuse the already-instantiated `exerciseViewModel`/`activityViewModel` from `MainActivity.kt` (both created once in `setContent`, already passed into `ActivityScreen` today).
- This codebase's Compose UI composables have **no unit-test precedent** — every task below is implement-directly, verify-with-`assembleDebug`-and-the-existing-suite, then a single on-device pass at the very end. Do not write new Compose UI tests; none of the modified logic (repositories, ViewModels) changes in this plan, so the existing test suite is expected to stay green with zero new test files.
- `ReadingStatsScreen` and `TanakhStatsScreen` both compute `today` internally via `remember { LocalDate.now() }` — neither takes it as a parameter, kept symmetric with each other and with `ActivityScreen.kt`'s own pattern.
- All 3 stats screens (`ExerciseStatsScreen`, `ReadingStatsScreen`, `TanakhStatsScreen`) use the identical shape: `Scaffold` + `TopAppBar` (title = habit name, back button via `navigationIcon`) wrapping `Column(Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()))`.
- Personal side-project, builder mode — build exactly what's specified below. Do not build a generic "per-habit stats screen" base/template composable; each of the 3 screens is its own small, standalone composable, matching the design doc's explicit choice.
- Build/test commands: `./gradlew.bat :app:assembleDebug` and `./gradlew.bat :app:testDebugUnitTest` (run from the repo root, Windows).
- **Added during `/autoplan` Design review:** each of the 3 stats screens' `isLoaded` gate renders a one-line "Loading…" `Text` instead of silently returning nothing — on the shared `ActivityScreen` a blank flash was low-stakes (the user is already mid-browse), but on a single-purpose screen reached by an explicit long-press tap, the same blank flash reads as broken. Reading's long-press menu also lists "Statistics" before "Reset today" (info before the destructive action, matching Exercise's Counter-before-Statistics and Tanakh's single-option shape) and renders "Reset today" in the error color, matching the existing reset-confirm dialog's own destructive-button convention.
- **Noted during `/autoplan` Design review, not fixed:** the 3 stats screens hardcode their `TopAppBar` title strings ("Exercise"/"Reading"/"Tanakh") rather than threading `habit.name` from the dashboard row. Low severity — no rename feature exists, and these are fixed seed-data habit names (see `HabitSeeding.kt`), not user-editable — plumbing a title through Navigation-Compose's route arguments for this would be disproportionate to the benefit.

---

### Task 1: Make ActivityScreen.kt's shared section composables reusable

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/activity/ActivityScreen.kt:117-230`

**Interfaces:**
- Consumes: nothing new.
- Produces: `internal fun HabitStatsSummary(title: String, state: ActivitySectionState)`, `internal fun EmptySectionState()`, `internal fun SectionCaption(text: String)`, `internal fun ReadingDayDetailDialog(viewModel: ActivityViewModel, date: LocalDate, onDismiss: () -> Unit)`, `internal fun TanakhDayDetailDialog(viewModel: ActivityViewModel, date: LocalDate, today: LocalDate, onDismiss: () -> Unit)` — all 5 change from file-private to `internal` so Task 3/4's new files (same package, `com.ziv.reminders.ui.activity`) can call them. No parameter or behavior changes.

- [ ] **Step 1: Change the 5 composables from `private` to `internal`**

In `app/src/main/java/com/ziv/reminders/ui/activity/ActivityScreen.kt`, change each of these 5 function signatures — remove the `private` modifier, add `internal` (no other changes to bodies):

```kotlin
@Composable
internal fun HabitStatsSummary(title: String, state: ActivitySectionState) {
```

```kotlin
@Composable
internal fun EmptySectionState() {
```

```kotlin
@Composable
internal fun SectionCaption(text: String) {
```

```kotlin
@Composable
internal fun ReadingDayDetailDialog(viewModel: ActivityViewModel, date: LocalDate, onDismiss: () -> Unit) {
```

```kotlin
@Composable
internal fun TanakhDayDetailDialog(viewModel: ActivityViewModel, date: LocalDate, today: LocalDate, onDismiss: () -> Unit) {
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Verify the existing test suite is unaffected**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (same pass count as before this change — this is a pure visibility change, no test should reference these composables since Compose UI has no test precedent in this codebase)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/activity/ActivityScreen.kt
git commit -m "refactor: make ActivityScreen's shared section composables internal for reuse by per-habit stats screens"
```

---

### Task 2: Add ExerciseStatsScreen composable

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseStatsScreen.kt:1-41` (imports + new composable inserted before the existing `ExerciseActivitySection`)

**Interfaces:**
- Consumes: existing `ExerciseActivitySection(viewModel: ExerciseViewModel)` (unchanged, defined later in the same file).
- Produces: `fun ExerciseStatsScreen(viewModel: ExerciseViewModel, onBack: () -> Unit)` — a full screen (Scaffold + TopAppBar + scrollable Column) that Task 6 wires as the `"exerciseStats"` NavHost destination.

- [ ] **Step 1: Add the new imports**

In `app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseStatsScreen.kt`, add these imports alongside the existing ones (existing imports are untouched):

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
```

- [ ] **Step 2: Add the new `ExerciseStatsScreen` composable**

Insert this immediately before the existing `@Composable fun ExerciseActivitySection(viewModel: ExerciseViewModel)` (i.e., right after the file's doc-comment block, before line 40 of the current file):

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseStatsScreen(viewModel: ExerciseViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exercise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            ExerciseActivitySection(viewModel = viewModel)
        }
    }
}
```

- [ ] **Step 3: Show a loading indicator instead of a blank screen while data loads**

**Added during `/autoplan` Design review:** `ExerciseActivitySection`'s existing early-return (`if (!uiState.isLoaded) return`) renders nothing while the Room read completes. On the shared Activity screen this was low-stakes (the user is already mid-browse across 3 sections); on the new single-purpose `ExerciseStatsScreen` it's the entire screen content, so a silent blank body under the top bar reads as broken. Change the existing line in `ExerciseActivitySection` (in the same file) from:

```kotlin
    if (!uiState.isLoaded) return
```

to:

```kotlin
    if (!uiState.isLoaded) {
        Text("Loading…", modifier = Modifier.padding(24.dp))
        return
    }
```

(`Text`, `Modifier`, and `.dp` are already imported in this file.)

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/exercise/ExerciseStatsScreen.kt
git commit -m "feat: add ExerciseStatsScreen (Exercise-only stats, own top bar + back button)"
```

---

### Task 3: Add ReadingStatsScreen

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/ui/activity/ReadingStatsScreen.kt`

**Interfaces:**
- Consumes: `internal fun HabitStatsSummary`, `internal fun SectionCaption`, `internal fun EmptySectionState`, `internal fun ReadingDayDetailDialog` (all from Task 1's `ActivityScreen.kt`, same package); `fun HeatmapGrid(dates: Set<LocalDate>, today: LocalDate, onDayClick: (LocalDate) -> Unit)` (already public, `HeatmapGrid.kt`); `ActivityViewModel`'s existing public `uiState`/`refresh()`.
- Produces: `fun ReadingStatsScreen(viewModel: ActivityViewModel, onBack: () -> Unit)` — Task 6 wires this as the `"readingStats"` NavHost destination.

- [ ] **Step 1: Create the file**

```kotlin
package com.ziv.reminders.ui.activity

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatsScreen(viewModel: ActivityViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsState()
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reading") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            // Added during /autoplan Design review: this screen's own isLoaded gate is the ONLY
            // loading signal (unlike the shared ActivityScreen, there's no outer multi-section
            // gate) — a silent early return would render a blank body under the top bar for the
            // duration of the Room read, reading as broken rather than loading.
            if (!uiState.isLoaded) {
                Text("Loading…", modifier = Modifier.padding(24.dp))
                return@Column
            }

            HabitStatsSummary("Reading", uiState.reading)
            SectionCaption("Tap a day to review or delete a session")
            if (uiState.reading.completedDates.isEmpty()) {
                EmptySectionState()
            } else {
                HeatmapGrid(dates = uiState.reading.completedDates, today = today, onDayClick = { selectedDate = it })
            }
        }
    }

    selectedDate?.let { date ->
        ReadingDayDetailDialog(viewModel = viewModel, date = date, onDismiss = { selectedDate = null })
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/activity/ReadingStatsScreen.kt
git commit -m "feat: add ReadingStatsScreen (Reading-only stats, own top bar + back button)"
```

---

### Task 4: Add TanakhStatsScreen

**Files:**
- Create: `app/src/main/java/com/ziv/reminders/ui/activity/TanakhStatsScreen.kt`

**Interfaces:**
- Consumes: `internal fun HabitStatsSummary`, `internal fun SectionCaption`, `internal fun EmptySectionState`, `internal fun TanakhDayDetailDialog` (all from Task 1's `ActivityScreen.kt`, same package); `fun HeatmapGrid(...)` (already public); `ActivityViewModel`'s existing public `uiState`/`refresh()`.
- Produces: `fun TanakhStatsScreen(viewModel: ActivityViewModel, onBack: () -> Unit)` — Task 6 wires this as the `"tanakhStats"` NavHost destination.

- [ ] **Step 1: Create the file**

```kotlin
package com.ziv.reminders.ui.activity

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TanakhStatsScreen(viewModel: ActivityViewModel, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val uiState by viewModel.uiState.collectAsState()
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tanakh") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            // Added during /autoplan Design review: same reasoning as ReadingStatsScreen — this
            // screen's isLoaded gate is the only loading signal, so a silent early return would
            // render a blank body under the top bar for the duration of the Room read.
            if (!uiState.isLoaded) {
                Text("Loading…", modifier = Modifier.padding(24.dp))
                return@Column
            }

            HabitStatsSummary("Tanakh", uiState.tanakh)
            SectionCaption("Tap today's cell to undo — past days are view-only")
            if (uiState.tanakh.completedDates.isEmpty()) {
                EmptySectionState()
            } else {
                HeatmapGrid(dates = uiState.tanakh.completedDates, today = today, onDayClick = { selectedDate = it })
            }
        }
    }

    selectedDate?.let { date ->
        TanakhDayDetailDialog(viewModel = viewModel, date = date, today = today, onDismiss = { selectedDate = null })
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/activity/TanakhStatsScreen.kt
git commit -m "feat: add TanakhStatsScreen (Tanakh-only stats, own top bar + back button)"
```

---

### Task 5: DashboardScreen — repoint Exercise's Statistics, add Reading/Tanakh long-press menus

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt` (imports; `DashboardScreen` signature + its `HabitRow` call site; `HabitRow` signature + its dispatch; `TimerHabitRow`; `ScheduleCursorHabitRow`)

**Interfaces:**
- Consumes: existing `RowLongPressMenu(title: String, options: List<RowMenuOption>, onDismiss: () -> Unit)` composable (already defined in this file; gains error-coloring for `isDestructive` options in Step 5 below, call signature unchanged).
- Produces: `DashboardScreen(viewModel, onOpenExercise, onOpenActivity, onOpenExerciseStats, onOpenReadingStats, onOpenTanakhStats)` — Task 6 wires the 3 new parameters at `MainActivity.kt`'s `DashboardScreen(...)` call site. Also produces `RowMenuOption(label, onSelect, isDestructive = false)` (Step 5 below adds the trailing `isDestructive` field) — no other task/file consumes `RowMenuOption` directly.

- [ ] **Step 1: Remove the now-unused `clickable` import**

`ScheduleCursorHabitRow` currently uses `Modifier.clickable(onClick = onMarkRead)`; after Step 5 below it uses `combinedClickable` instead, and `clickable` is not used anywhere else in this file. Remove this line:

```kotlin
import androidx.compose.foundation.clickable
```

- [ ] **Step 2: Add `DashboardScreen`'s 3 new parameters and wire them at the `HabitRow` call site**

Change the `DashboardScreen` function signature from:

```kotlin
fun DashboardScreen(viewModel: DashboardViewModel, onOpenExercise: () -> Unit = {}, onOpenActivity: () -> Unit = {}) {
```

to:

```kotlin
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenExercise: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenExerciseStats: () -> Unit = {},
    onOpenReadingStats: () -> Unit = {},
    onOpenTanakhStats: () -> Unit = {},
) {
```

Change the `HabitRow` call site's last two arguments from:

```kotlin
                    onOpenExercise = onOpenExercise,
                    onOpenExerciseStats = onOpenActivity,
                )
```

to:

```kotlin
                    onOpenExercise = onOpenExercise,
                    onOpenExerciseStats = onOpenExerciseStats,
                    onOpenReadingStats = onOpenReadingStats,
                    onOpenTanakhStats = onOpenTanakhStats,
                )
```

- [ ] **Step 3: Thread the 2 new callbacks through `HabitRow`'s signature and dispatch**

Change:

```kotlin
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
```

to:

```kotlin
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
    onOpenReadingStats: () -> Unit,
    onOpenTanakhStats: () -> Unit,
) {
    when (habit.status) {
        is HabitStatus.CounterStatus -> CounterHabitRow(habit, habit.status, onIncrement, onOpenExercise, onOpenExerciseStats)
        is HabitStatus.TimerStatus -> TimerHabitRow(habit, habit.status, onToggleTimer, onResetReadingToday, fetchReadingSessionCountToday, onOpenReadingStats)
        is HabitStatus.ScheduleCursorStatus -> ScheduleCursorHabitRow(habit, habit.status, onMarkRead, onOpenTanakhStats)
    }
}
```

- [ ] **Step 4: Give `TimerHabitRow` (Reading) a long-press menu**

Change `TimerHabitRow`'s signature and body from:

```kotlin
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
```

to:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.TimerStatus,
    onToggleTimer: (Int) -> Unit,
    onResetToday: () -> Unit,
    fetchSessionCountToday: suspend () -> Int,
    onOpenReadingStats: () -> Unit,
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
    var showMenu by remember { mutableStateOf(false) }
    val rowScope = rememberCoroutineScope()

    Row(
        // Pass the currently-displayed (ticked-down) value, not status.remainingSeconds — the
        // ViewModel's optimistic flip uses this to avoid visually resetting to the stale
        // pre-session baseline the instant Stop is tapped. Long-press now opens a menu
        // (Reset today / Statistics) instead of jumping straight to the reset confirm dialog.
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { onToggleTimer(displaySeconds) },
            onLongClick = { showMenu = true },
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

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            // Added during /autoplan Design review: Statistics listed first (matches Exercise's
            // safe-option-first "Counter" and Tanakh's single-entry menu), destructive "Reset
            // today" listed second and marked isDestructive so it renders in the error color —
            // the prior draft put the destructive action in the same top slot every other row
            // uses for a benign action, risking a habit-driven mis-tap.
            options = listOf(
                RowMenuOption("Statistics", onOpenReadingStats),
                // Fetches the session count first (same as the row's previous direct
                // long-click behavior) so the confirm dialog below can show what's about to
                // be lost — a destructive, irreversible action shouldn't be confirmed blind.
                // isDestructive is named (not trailing-lambda) because it's no longer the last
                // parameter once isDestructive follows it — see Step 5's RowMenuOption reorder.
                RowMenuOption(
                    "Reset today",
                    onSelect = {
                        rowScope.launch {
                            sessionCountToday = fetchSessionCountToday()
                            showResetConfirm = true
                        }
                    },
                    isDestructive = true,
                ),
            ),
            onDismiss = { showMenu = false },
        )
    }

    if (showResetConfirm) {
```

(Everything after `if (showResetConfirm) {` — the `AlertDialog` block and the function's closing brace — is unchanged.)

- [ ] **Step 5: Add an `isDestructive` flag to `RowMenuOption`/`RowLongPressMenu` for the error-colored "Reset today" option above**

**Added during `/autoplan` Design review:** `RowLongPressMenu` currently renders every option as a visually identical `TextButton` — no way to distinguish a destructive action from a safe one. Change the existing `RowMenuOption` data class and `RowLongPressMenu` composable (both already defined in this file, just above `CounterHabitRow`) from:

```kotlin
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
```

to:

```kotlin
private data class RowMenuOption(val label: String, val onSelect: () -> Unit, val isDestructive: Boolean = false)

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
                    // isDestructive options (added during /autoplan Design review) render in the
                    // error color — matching the reset-confirm dialog's own destructive-button
                    // convention — so a habit-driven "tap the top option" reflex from another
                    // row's menu doesn't land on a destructive action unstyled.
                    TextButton(
                        onClick = { onDismiss(); option.onSelect() },
                        colors = if (option.isDestructive) {
                            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.textButtonColors()
                        },
                    ) { Text(option.label) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        confirmButton = {},
    )
}
```

(`ButtonDefaults` and `MaterialTheme` are already imported in this file.) `isDestructive` is placed *after* `onSelect` (not between `label` and `onSelect`) specifically so `CounterHabitRow`'s existing, untouched `RowMenuOption("Counter", onOpenExercise)` and `RowMenuOption("Statistics", onOpenExerciseStats)` calls, and Tanakh's new `RowMenuOption("Statistics", onOpenTanakhStats)` call (Step 6 below), all keep compiling unchanged as positional 2-arg calls (`label`, `onSelect`) with `isDestructive` defaulting to `false` — Kotlin resolves positional arguments strictly left-to-right by declared parameter order, so a defaulted parameter placed *before* the last positional argument used by a call site would break it (that was the bug in an earlier draft of this step); placing the new defaulted parameter last avoids touching any call site that doesn't need it.

- [ ] **Step 6: Give `ScheduleCursorHabitRow` (Tanakh) a long-press menu**

Change `ScheduleCursorHabitRow` from:

```kotlin
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

to:

```kotlin
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduleCursorHabitRow(
    habit: HabitRowUiState,
    status: HabitStatus.ScheduleCursorStatus,
    onMarkRead: () -> Unit,
    onOpenTanakhStats: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    // Once the schedule is exhausted there's nothing left to mark read — tap is a no-op so a
    // stray tap can't advance the cursor past the end or credit a phantom streak day (see
    // ScheduleCursorRepository.markRead's matching finished-state no-op guard). combinedClickable's
    // onClick is non-nullable (unlike onLongClick), so this gating now lives inside the lambda
    // instead of being expressed by omitting a click modifier entirely — one accepted, minor UX
    // change: a finished row now shows a tap ripple, even though tapping still does nothing.
    // Noted during /autoplan Eng review, not fixed: a finished row previously had no click
    // modifier at all, so TalkBack didn't announce it as interactive; combinedClickable is now
    // always applied, so TalkBack will announce "double tap to activate" even though tapping is
    // a no-op. Accepted for a personal app — not worth a conditional modifier branch to avoid.
    // Long-press is always available (Statistics makes sense regardless of finished state).
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (!status.finished) onMarkRead() },
            onLongClick = { showMenu = true },
        ),
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

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            options = listOf(RowMenuOption("Statistics", onOpenTanakhStats)),
            onDismiss = { showMenu = false },
        )
    }
}
```

- [ ] **Step 7: Verify it compiles**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Verify the existing test suite still passes**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.ziv.reminders.ui.dashboard.DashboardDispatchTest"`
Expected: BUILD SUCCESSFUL (this test covers `hasExerciseDetailMenu`, which is untouched by this task — confirms the dispatch logic itself has zero regressions)

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt
git commit -m "feat: repoint Exercise Statistics + add Reading/Tanakh long-press menus"
```

---

### Task 6: Wire the 3 new NavHost destinations in MainActivity

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/MainActivity.kt` (line numbers below are approximate — a since-landed unrelated commit added a battery-optimization-exemption method after this plan was drafted, per `/autoplan` Eng review; every diff below is anchored to unique surrounding text, not line numbers, so it applies regardless)

**Interfaces:**
- Consumes: `DashboardScreen`'s 3 new parameters (Task 5), `ExerciseStatsScreen(viewModel: ExerciseViewModel, onBack: () -> Unit)` (Task 2), `ReadingStatsScreen(viewModel: ActivityViewModel, onBack: () -> Unit)` (Task 3), `TanakhStatsScreen(viewModel: ActivityViewModel, onBack: () -> Unit)` (Task 4).
- Produces: nothing further — this is the final integration task.

- [ ] **Step 1: Add the 3 new imports**

In `app/src/main/java/com/ziv/reminders/MainActivity.kt`, add alongside the existing `com.ziv.reminders.ui.*` imports:

```kotlin
import com.ziv.reminders.ui.activity.ReadingStatsScreen
import com.ziv.reminders.ui.activity.TanakhStatsScreen
import com.ziv.reminders.ui.exercise.ExerciseStatsScreen
```

- [ ] **Step 2: Wire the 3 new `DashboardScreen` arguments and add the 3 new NavHost destinations**

Change:

```kotlin
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
```

to:

```kotlin
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
                            onOpenExerciseStats = { navController.navigate("exerciseStats") },
                            onOpenReadingStats = { navController.navigate("readingStats") },
                            onOpenTanakhStats = { navController.navigate("tanakhStats") },
                        )
                    }
                    composable("exerciseCounter") {
                        ExerciseCounterScreen(
                            viewModel = exerciseViewModel,
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
                    composable("exerciseStats") {
                        ExerciseStatsScreen(
                            viewModel = exerciseViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("readingStats") {
                        ReadingStatsScreen(
                            viewModel = activityViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("tanakhStats") {
                        TanakhStatsScreen(
                            viewModel = activityViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run the full test suite**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/MainActivity.kt
git commit -m "feat: wire exerciseStats/readingStats/tanakhStats NavHost destinations"
```

- [ ] **Step 6: On-device verification (manual, end of plan)**

Install and manually verify on the connected device:
1. `./gradlew.bat :app:installDebug`
2. Long-press Exercise → menu shows Counter/Statistics/Cancel → tap Statistics → opens an Exercise-only screen (streak/month/total + heatmap), back button returns to dashboard.
3. Long-press Reading → menu shows Reset today/Statistics/Cancel → tap Statistics → opens a Reading-only screen (streak/total + heatmap), back button returns to dashboard. Separately, long-press Reading → Reset today → confirms the existing session-count-preview confirm dialog still appears and behaves as before.
4. Long-press Tanakh → menu shows Statistics/Cancel (no third option) → tap Statistics → opens a Tanakh-only screen (streak/total + heatmap), back button returns to dashboard.
5. Tap the dashboard's top-bar list icon → confirms the unfiltered Activity screen still shows all 3 habits plus the combo-streak banner, unchanged.
6. Short-tap all 3 rows → confirms increment/toggle-timer/mark-read and their undo snackbars are all unaffected.
7. Sweep `adb logcat` across the whole session → zero crashes.

---

## Self-Review Notes

- **Spec coverage:** all 6 Confirmed Decisions from the spec map to tasks — (1) 3 new screens/destinations → Tasks 2-4, 6; (2) Reading's menu → Task 5 Step 4; (3) Tanakh's menu → Task 5 Step 5; (4) Exercise repoint → Task 5 Step 2-3, Task 6; (5) top-bar icon untouched → no task touches `ActivityScreen`'s own behavior or the `"activity"` route/`onOpenActivity`; (6) short-tap unchanged → verified explicitly in Task 6 Step 6's on-device pass.
- **Placeholder scan:** no TBDs — every step has complete code or an exact command.
- **Type consistency:** `RowMenuOption`, `RowLongPressMenu`, `HabitStatsSummary`, `SectionCaption`, `EmptySectionState`, `HeatmapGrid`, `ReadingDayDetailDialog`, `TanakhDayDetailDialog`, `ActivitySectionState` are used with identical signatures across Tasks 1, 3, 4, 5 — verified against the actual current file contents (not assumed from the design doc) before writing this plan.
