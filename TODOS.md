# TODOS

## Review

### Retroactive edit of a past day's rep counts

**What:** Let the user tap a heatmap day (in the new Exercise stats screen) and correct that day's logged rep counts, not just view them.

**Why:** Mistakes happen (wrong count logged, forgot to log same-day) and today there's no way to fix history for any habit kind.

**Context:** Surfaced during the `/autoplan` review of the Shape-into-Reminders exercise port (2026-07-19). Explicitly deferred because it touches every habit kind's "no edit history" precedent, not just Exercise — a bigger, cross-cutting feature than this plan's scope.

**Effort:** M
**Priority:** P3
**Depends on:** The exercise-port plan (per-day sub-counter table) shipping first.

---

### Generalize the per-day sub-metric table for other habit kinds

**What:** The new `exercise_sub_counter_progress` table (4 rep counters per day) is Exercise-specific. Consider a reusable "sub-metric" table shape other habit kinds could use later (e.g., Reading session notes, Tanakh chapter difficulty rating).

**Why:** Avoids re-deriving the same per-day-keyed-table pattern from scratch if a second use case shows up.

**Context:** Surfaced during the `/autoplan` review (2026-07-19). Deliberately NOT built now — no second concrete use case exists yet, and building it speculatively would repeat the exact mistake Approach B (generalized detail-screen framework) was rejected for in the original design doc.

**Effort:** M
**Priority:** P4
**Depends on:** A second habit kind actually needing per-day sub-metrics.

---

### Weekly aggregate summary notification

**What:** Shape has a Saturday weekly-summary notification (days this week/month, streak, record callouts). Reminders' notification system today is per-kind only (`ReminderReceiver`-equivalent, `WeeklySummaryReceiver` in Shape) — no aggregate-across-habits summary exists in Reminders.

**Why:** Would give a single weekly check-in across Exercise/Reading/Tanakh instead of only per-habit reminders.

**Context:** Surfaced during the `/autoplan` review (2026-07-19). Deferred — touches Reminders' notification scheduling architecture broadly (would need its own alarm + receiver + cross-habit aggregation query), well outside this plan's blast radius (Exercise-only).

**Effort:** L
**Priority:** P2
**Depends on:** None — could be built independently at any time.

---

### App-wide: no error handling around Room reads in ViewModels

**What:** `DashboardViewModel.refresh()` (and the new `ExerciseViewModel`'s equivalent load) has no try/catch around Room queries — a query throwing (e.g., rare disk I/O failure) crashes the app.

**Why:** Currently silent/unhandled across the whole app, not introduced by any single feature.

**Context:** Surfaced during the `/autoplan` Eng review of the exercise-port plan (2026-07-19) — flagged as a pre-existing, app-wide gap rather than something to patch inline for just one ViewModel.

**Effort:** S-M
**Priority:** P3
**Depends on:** None.

---

### Sub-counter +/- buttons below minimum touch-target size

**What:** Shape's sub-counter +/- buttons are 40dp circular, slightly under the 44px accessibility touch-target guideline. This limitation carries over wherever Shape's `ExerciseRow` composable is ported.

**Why:** Minor a11y improvement; low severity, affects a small tap target used briefly per day.

**Context:** Surfaced during the `/autoplan` Design review (2026-07-19). Deferred rather than fixed during the port to keep the port literal — applies equally to Shape itself, not something this plan introduced.

**Effort:** S
**Priority:** P4
**Depends on:** None.

---

### Generalize the Navigation-Compose detail-screen pattern

**What:** The exercise-port plan adds Reminders' first navigation state machine (`NavHost` with `dashboard`/`exerciseCounter`/`exerciseStats` destinations), scoped only to Exercise. If Reading or Tanakh later want their own rich detail screens, extend the same `NavHost` with new destinations at that time.

**Why:** Avoids premature generalization now (no second concrete use case) while leaving a clear path for later.

**Context:** Surfaced in the CEO dream-state analysis during `/autoplan` review (2026-07-19) — the 12-month ideal is a shared detail-screen convention, but this plan deliberately establishes the pattern once rather than generalizing it prematurely (mirrors why Approach B was rejected in the original design doc). Implementation ended up using Navigation-Compose (user's explicit choice during the review gate) rather than the hand-rolled enum + BackHandler originally recommended, so any future extension is just new NavHost destinations, not a new pattern.

**Effort:** M
**Priority:** P3
**Depends on:** Reading or Tanakh actually needing a detail screen.

---

### Unused `SubCounterRepository.valueForDate` (singular)

**What:** `SubCounterRepository.valueForDate(exerciseKey, date)` (singular, single-key lookup) is only referenced by its own unit tests — production code goes through `valuesForDate` (plural, batch-by-date) via `ExerciseViewModel.subCounterValuesForDate`, which already delivers the "no fabricated default for a past day" behavior the singular method was designed for.

**Why:** Either it's intentional public API surface for a future caller, or it's dead code that should be removed along with its tests.

**Context:** Surfaced during the final whole-branch review of the exercise-port plan (2026-07-19). Not a bug — the null-for-missing-past-date semantic is preserved either way — just an unresolved "keep or remove" call.

**Effort:** S
**Priority:** P4
**Depends on:** None.

---

### Duplicated streak-calculation logic (CounterHabitRepository vs. HabitStats)

**What:** `CounterHabitRepository.currentStreak` (Room-backed, used for the displayed streak count) and `HabitStats.currentStreak`/`isNewStreakRecord` (pure, used for record detection) independently implement the same streak-anchor algorithm over the same completed-date set.

**Why:** Both currently agree, but a future change to one anchor rule could silently desync from the other — e.g. the displayed streak count and the "— new record!" suffix disagreeing.

**Context:** Surfaced during the final whole-branch review of the exercise-port plan (2026-07-19). Recommended fix (not urgent): have `CounterHabitRepository.currentStreak` delegate to `HabitStats.currentStreak` instead of reimplementing it.

**Effort:** S
**Priority:** P3
**Depends on:** None.

---

### Heatmap "miss" color is a fixed light gray in dark mode

**What:** `HeatmapMiss = Color(0xFFE0E0E0)` in `ExerciseColors.kt` renders as a near-white tile in dark mode, since it's a fixed value rather than derived from the current theme.

**Why:** Possible legibility/contrast issue in dark mode — worth checking on-device.

**Context:** Surfaced during the final whole-branch review of the exercise-port plan (2026-07-19). Falls within the already-approved "semantic-only constants mirroring Shape, layered on dynamic theme" decision, so not a defect against the plan — just worth a look if dark-mode use is common.

**Effort:** S
**Priority:** P4
**Depends on:** On-device dark-mode check (see Task 7 of the exercise-port plan).
