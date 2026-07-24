# Dashboard Row Icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each dashboard row a small full-color icon at the far left, next to the status dot — Exercise gets Shape's real hexagon+plus icon, Reading gets ReadBook's real open-book icon, Tanakh gets a new two-stone-tablets icon.

**Architecture:** 3 new self-contained vector drawable resources (Shape's and ReadBook's real icon geometry, flattened from their adaptive-icon layers into single vectors; a brand-new design for Tanakh). Each row composable in `DashboardScreen.kt` gains one `Image` composable as the new first child of its existing inner `Row` (the one already holding the status dot and name/streak `Column`).

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Android VectorDrawable resources.

## Global Constraints

- No Room schema changes, no new DI wiring, no backend/logic changes — this is 3 new drawable resources plus 3 one-line insertions into already-existing composables.
- The 3 new icons render via `Image` + `painterResource`, not `Icon` — they are multi-color vectors; `Icon` would force-tint them to a single color and destroy the point of reusing the real source-app colors.
- `contentDescription = null` on all 3 `Image`s — the habit name `Text` immediately to the right already conveys the same information; a screen reader announcing "Exercise icon, Exercise, Streak 0 days" would be redundant.
- This codebase's Compose UI composables have **no unit-test precedent** — implement directly, verify via `assembleDebug` and on-device at the end, no new Compose UI tests.
- Personal side-project (builder mode) — build exactly what's specified below.
- Build/test commands: `./gradlew.bat :app:assembleDebug` and `./gradlew.bat :app:testDebugUnitTest` (run from the repo root, via the PowerShell tool — this environment's Bash tool has no git/gradle in PATH).

---

### Task 1: Add the 3 icon drawable resources

**Files:**
- Create: `app/src/main/res/drawable/ic_habit_exercise.xml`
- Create: `app/src/main/res/drawable/ic_habit_reading.xml`
- Create: `app/src/main/res/drawable/ic_habit_tanakh.xml`

**Interfaces:**
- Consumes: nothing new.
- Produces: `R.drawable.ic_habit_exercise`, `R.drawable.ic_habit_reading`, `R.drawable.ic_habit_tanakh` — Task 2 references these via `painterResource`.

- [ ] **Step 1: Create the Exercise icon**

`ic_habit_exercise.xml` — Shape's real hexagon+plus icon, flattened: the hexagon recolored from white to Shape's own background green (`#2E7D32`, the same value this app's `GoalGreen` constant already uses), the plus sign recolored from dark green to white for contrast. Both path shapes are transcribed byte-for-byte from Shape's actual `app/src/main/res/drawable/ic_launcher_foreground.xml`.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Hexagon, recolored to Shape's own background green (was white-on-green in the
         adaptive icon; flattened here since there's no separate background layer) -->
    <path
        android:fillColor="#2E7D32"
        android:pathData="M54,22 L81.71,38 L81.71,70 L54,86 L26.29,70 L26.29,38 Z"/>
    <!-- Plus sign, recolored to white for contrast against the green hexagon -->
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M50,38 L58,38 L58,50 L70,50 L70,58 L58,58 L58,70 L50,70 L50,58 L38,58 L38,50 L50,50 Z"/>
</vector>
```

- [ ] **Step 2: Create the Reading icon**

`ic_habit_reading.xml` — ReadBook's real open-book icon, transcribed byte-for-byte from ReadBook's actual `app/src/main/res/drawable/ic_launcher_foreground.xml` (5 paths: grounding shadow, left page, right page, spine, bookmark ribbon), resized from the adaptive-icon convention down to a 24dp inline icon.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#22000000" android:pathData="M24,80 a30,6 0,1,0 60,0 a30,6 0,1,0 -60,0 Z" />
    <path android:fillColor="#F5C463" android:pathData="M54,40 C46,36 34,37 26,44 L26,74 C34,81 46,80 54,84 Z" />
    <path android:fillColor="#E4AC49" android:pathData="M54,40 C62,36 74,37 82,44 L82,74 C74,81 62,80 54,84 Z" />
    <path android:fillColor="#B9832F" android:pathData="M52,40 C53,39 55,39 56,40 L56,84 C55,85 53,85 52,84 Z" />
    <path android:fillColor="#E2604A" android:pathData="M56,22 L64,22 L64,58 L60,50 L56,58 Z" />
</vector>
```

- [ ] **Step 3: Create the Tanakh icon**

`ic_habit_tanakh.xml` — new design, two stone tablets (Ten Commandments silhouette). Same `108`×`108` viewport convention as the other two icons for consistency. Right tablet and its text lines are slightly darker than the left, matching ReadBook's own light-from-the-left page-shading convention for a visual family resemblance across all 3 icons.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- Left tablet -->
    <path android:fillColor="#90A4AE" android:pathData="M23,52 A15,15 0 0 1 53,52 L53,86 L23,86 Z"/>
    <!-- Right tablet, slightly darker -->
    <path android:fillColor="#78909C" android:pathData="M55,52 A15,15 0 0 1 85,52 L85,86 L55,86 Z"/>
    <!-- Engraved text lines, left tablet -->
    <path android:fillColor="#455A64" android:pathData="M29,60 L47,60 L47,63 L29,63 Z M29,68 L47,68 L47,71 L29,71 Z M29,76 L47,76 L47,79 L29,79 Z"/>
    <!-- Engraved text lines, right tablet (darker, matching its tablet) -->
    <path android:fillColor="#37474F" android:pathData="M61,60 L79,60 L79,63 L61,63 Z M61,68 L79,68 L79,71 L61,71 Z M61,76 L79,76 L79,79 L61,79 Z"/>
</vector>
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/drawable/ic_habit_exercise.xml app/src/main/res/drawable/ic_habit_reading.xml app/src/main/res/drawable/ic_habit_tanakh.xml
git commit -m "feat: add dashboard row icons (Shape's, ReadBook's, and a new Tanakh tablets icon)"
```

---

### Task 2: Wire the icons into the dashboard rows

**Files:**
- Modify: `app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt`

**Interfaces:**
- Consumes: `R.drawable.ic_habit_exercise`, `R.drawable.ic_habit_reading`, `R.drawable.ic_habit_tanakh` (Task 1).
- Produces: nothing consumed elsewhere — this is the final task.

- [ ] **Step 1: Add the 4 new imports**

`DashboardScreen.kt` currently imports none of `androidx.compose.foundation.Image`, `androidx.compose.foundation.border`, `androidx.compose.ui.res.painterResource`, or `com.ziv.reminders.R` — it only uses `Icon`-based composables today. Add alongside the existing imports (following the exact precedent `ExerciseCounterScreen.kt` already establishes for the `Image`/`painterResource`/`R.drawable.*` part of this pattern):

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import com.ziv.reminders.R
```

- [ ] **Step 2: Give `HabitStatusDot` a background-colored ring, so it stays visually distinct from a same-colored icon next to it**

**Real collision caught during `/autoplan` Design review:** Exercise's icon is solid `#2E7D32` (Shape's real green) and the dot's own "complete" state is `GoalGreen` — the exact same hex. A completed Exercise row would show two same-colored shapes 8dp apart, reading as one blurred blob instead of a distinct icon + status signal. Recoloring the icon isn't an option (it would break the "real Shape branding" premise this whole plan is built on), so the dot instead gets a thin ring in the screen's own background color — the same technique Slack/Discord use for status dots overlaid on same-colored avatars, so the dot reads as a distinct floating badge regardless of what color sits next to it.

Change:

```kotlin
@Composable
private fun HabitStatusDot(color: Color) {
    Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
}
```

to:

```kotlin
@Composable
private fun HabitStatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.background, CircleShape)
    )
}
```

- [ ] **Step 3: Add the icon to `CounterHabitRow` (Exercise)**

Change:

```kotlin
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

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            options = listOf(
                RowMenuOption("Counter", onOpenExercise),
                RowMenuOption("Statistics", onOpenExerciseStats),
            ),
```

to:

```kotlin
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painter = painterResource(R.drawable.ic_habit_exercise), contentDescription = null, modifier = Modifier.size(20.dp))
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

    if (showMenu) {
        RowLongPressMenu(
            title = habit.name,
            options = listOf(
                RowMenuOption("Counter", onOpenExercise),
                RowMenuOption("Statistics", onOpenExerciseStats),
            ),
```

(Only the inner `Row`'s first child changes — everything else in `CounterHabitRow`, including the `if (showMenu)` block shown above purely as an anchor for precise diff placement, is unchanged.)

- [ ] **Step 4: Add the icon to `TimerHabitRow` (Reading)**

Change:

```kotlin
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitStatusDot(color = if (status.completed) GoalGreen else MaterialTheme.colorScheme.error)
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
            }
        }
        val minutes = displaySeconds / 60
```

to:

```kotlin
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painter = painterResource(R.drawable.ic_habit_reading), contentDescription = null, modifier = Modifier.size(20.dp))
            HabitStatusDot(color = if (status.completed) GoalGreen else MaterialTheme.colorScheme.error)
            Column {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                Text("Streak: ${habit.streak}d", style = MaterialTheme.typography.bodySmall)
            }
        }
        val minutes = displaySeconds / 60
```

- [ ] **Step 5: Add the icon to `ScheduleCursorHabitRow` (Tanakh)**

Change:

```kotlin
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HabitStatusDot(
                color = when {
```

to:

```kotlin
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Image(painter = painterResource(R.drawable.ic_habit_tanakh), contentDescription = null, modifier = Modifier.size(20.dp))
            HabitStatusDot(
                color = when {
```

(Everything from `color = when {` onward — the full 3-branch `when` and the rest of `ScheduleCursorHabitRow` — is unchanged.)

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Run the full test suite**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ziv/reminders/ui/dashboard/DashboardScreen.kt
git commit -m "feat: render habit icons on each dashboard row, next to the status dot"
```

- [ ] **Step 9: On-device verification (manual, end of plan)**

Install and manually verify on the connected device:
1. `./gradlew.bat :app:installDebug`
2. Confirm Exercise's row shows a green hexagon with a white plus, at the far left before the dot.
3. Confirm Reading's row shows the open-book icon (amber pages, spine, ribbon), at the far left before the dot.
4. Confirm Tanakh's row shows the two-stone-tablets icon, visually distinct from Reading's book.
5. Confirm all 3 icons render legibly at their actual on-screen size — check both light and dark theme if easy to toggle.
6. Confirm the outer row layout is unaffected: habit names still flush-left, status text still flush-right, no elements pushed out of position.
7. **Specifically confirm the Design-review fix**: mark Exercise complete (5/5) and check the dot no longer blurs into the same-colored icon — the background-colored ring should keep them visually distinct.
8. Sweep `adb logcat` across the whole session for `FATAL EXCEPTION`/`AndroidRuntime.*Exception` → zero matches.

---

## Self-Review Notes

- **Spec coverage:** all 5 Confirmed Decisions map to tasks — (1)(2) real source-app icon reuse → Task 1 Steps 1-2; (3) full original color → `Image` (not `Icon`) in Task 2; (4) new Tanakh tablets design → Task 1 Step 3; (5) placement inside the existing inner Row → Task 2 Steps 3-5.
- **Placeholder scan:** no TBDs — every step has complete code or an exact command.
- **Type consistency:** `Image(painter = painterResource(R.drawable.ic_habit_*), contentDescription = null, modifier = Modifier.size(20.dp))` used identically across all 3 row composables in Task 2 — verified against the actual current file contents (not re-derived from memory) before writing this plan.
- **Design-review fix carried through:** `HabitStatusDot`'s new background-colored ring (Task 2 Step 2) and the 20dp icon size (down from the original 24dp) are both now consistent across every reference to them in this plan.
