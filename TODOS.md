# TODOS

## Review

### Parameterize per-instance stats screens/dashboard wiring instead of one bespoke set per instance

**What:** `DashboardScreen`'s `HabitRow` carries one `onOpenXStats: () -> Unit` parameter per
*instance* (not per kind), and each instance gets its own hand-wired NavHost route
(`exerciseStats`/`readingStats`/`tanakhStats`/`cppWeeklyStats`/`legoKitStats`/`gardenStats`) and,
where a kind is reused across instances, an ad hoc instance-ID branch (`isLegoKitRow`,
`hasExerciseDetailMenu`) to avoid two instances of the same `HabitKind` sharing the wrong UI.
Consider a single `habitInstanceId`-parameterized stats route + one shared ViewModel instead.

**Why:** Surfaced by an independent `/autoplan` CEO review voice during the garden-watering
reminder plan's review (2026-07-27): this is now the 5th consecutive habit-kind/instance addition
paying this exact one-off wiring cost in full, several on the same day. The "no second use case
yet" YAGNI argument that correctly justified not generalizing the first 1-2 times is weaker at
the 5th repetition with no sign of slowing — the recurring cost is now measurable, not
hypothetical.

**Pros:** A future 6th habit instance could add its stats screen with one NavHost route and zero
new `DashboardScreen`/`HabitRow` parameters, instead of another full callback-threading pass.

**Cons:** Real generalization work now (a parameterized route + shared ViewModel over
`DashboardDataSource`, replacing 6 near-identical dedicated ViewModels), and this exact
"generalize now vs. wait for a clearer signal" tradeoff was already argued the other way twice
before in this file (see completed items) — not a free decision, a real one.

**Context:** Surfaced during the `/autoplan` CEO review of the garden-watering reminder
(`INTERVAL_DUE` kind) plan (2026-07-27). Not built as part of that plan — it doesn't block the
garden-watering feature, and the independent reviewer's own recommendation was to document the
recurring cost, not to refactor mid-flight on an unrelated plan.

**Effort:** M
**Priority:** P3
**Depends on:** None — actionable whenever the recurring cost is judged worth paying down.

---

### Lego Kit row: notification "Mark added" action button

**What:** Add an inline action button to the Lego Kit reminder notification that marks today's
kit added directly, without opening the app.

**Why:** Removes a step from the most frictionless possible version of the reminder — see it,
tap it, done.

**Pros:** Matches what a truly frictionless reminder should feel like.

**Cons:** No existing precedent in this codebase — every other habit kind's notification only
opens the app on tap. This is genuinely new notification-action-button infrastructure (a
`PendingIntent` wired to a `BroadcastReceiver` that mutates state directly), not a 30-minute
touch — an identical TODO already exists for C++ Weekly for the same reason and is still open.

**Context:** Surfaced during the `/autoplan` CEO review of the Lego Kit daily mission row plan
(2026-07-27).

**Effort:** M
**Priority:** P3
**Depends on:** None.

---

### App-wide: "new streak record" callout on habit stats screens

**What:** Surface a "new record!" banner on a habit's Statistics screen when the current streak
exceeds the previous best.

**Why:** Small delight opportunity — `HabitStats` already computes an `isNewStreakRecord`-shaped
concept internally for streak calculation; surfacing it visually would reward long streaks.

**Context:** Surfaced during the `/autoplan` CEO review of the Lego Kit daily mission row plan
(2026-07-27). Deliberately NOT built as part of that plan — none of the 4 existing stats screens
(Exercise, Reading, Tanakh, C++ Weekly) have this either, so it's an app-wide candidate, not
something to scope-creep into a single new row.

**Effort:** S
**Priority:** P4
**Depends on:** None.

---

### ActivityViewModelTest: wall-clock drift in refresh_populatesAllThreeSectionsAndComboStreak

**What:** `ActivityViewModelTest.refresh_populatesAllThreeSectionsAndComboStreak` (in `app/src/test/java/com/ziv/reminders/ui/activity/ActivityViewModelTest.kt`) fails due to wall-clock date drift. `ActivityViewModel.refresh()` calls real `LocalDate.now()` directly with no injectable clock, while the test hardcodes `today = LocalDate.of(2026, 7, 19)`. As real time passes 2026-07-19, the test's fixture data (seeded against that hardcoded date) no longer lines up with what `refresh()` computes as "today," so the streak assertion fails.

**Why:** This is a real correctness gap in the test (not hermetic — depends on wall-clock time) that will keep recurring as time passes, masking any future genuine regression in this exact test.

**Pros:** Fixing makes the test deterministic and hermetic. The fix is well-understood: inject a `Clock`/date-provider into `ActivityViewModel`, matching whatever pattern other ViewModels in this codebase already use for testable "today" (check if one exists, e.g. `SystemClock` referenced elsewhere in the codebase).

**Cons:** Touches `ActivityViewModel`'s constructor/dependencies, which may ripple into other call sites (similar to how the C++ Weekly work discovered several `HabitEngine(...)` construction sites needed updating for an unrelated constructor change) — a small but real diff, not zero-cost.

**Context:** Surfaced during the final whole-branch `/autoplan`+`subagent-driven-development` review of the C++ Weekly reminder row plan (2026-07-25/26). Confirmed pre-existing via `git stash` bisection to a commit before that work began, and reconfirmed identically by 4 independent task reviewers across that work's implementation — never caused by that feature.

**Effort:** S-M
**Priority:** P3
**Depends on:** None.

---

### C++ Weekly row: long-press opens the YouTube channel/episode

**What:** Add a long-press action on the C++ Weekly dashboard row that opens the show's YouTube channel or the specific next episode via `Intent.ACTION_VIEW`.

**Why:** Quick delight — lets the user jump straight to watching instead of leaving the app to find it themselves.

**Pros:** Cheap to build (a single `Intent.ACTION_VIEW` call), directly serves the row's whole purpose (get to watching).

**Cons:** Reopens a premise the C++ Weekly design doc explicitly closed (no long-press menu at all for this row in v1, since it has no stats to show) — a long-press *action* isn't a stats menu, but it's still new long-press behavior on a row specced to have none.

**Context:** Surfaced during the `/autoplan` CEO review of the C++ Weekly reminder row plan (2026-07-25). Deferred rather than built now — the design doc's v1 scope was deliberately narrow (single row, no long-press), and adding this wasn't part of that agreed scope.

**Effort:** S
**Priority:** P3
**Depends on:** None.

---

### C++ Weekly row: notification "Mark watched" action button

**What:** Add an inline action button to the C++ Weekly reminder notification that advances `nextItemNumber` directly, without opening the app.

**Why:** Removes a step from the most frictionless possible version of the reminder — see it, tap it, done.

**Pros:** Matches what a truly frictionless reminder should feel like.

**Cons:** No existing precedent in this codebase — every other habit kind's notification only opens the app on tap (`HabitReminderReceiver`'s existing generalized flow). This is genuinely new notification-action-button infrastructure (a `PendingIntent` wired to a `BroadcastReceiver` that mutates state directly), not a 30-minute touch.

**Context:** Surfaced during the `/autoplan` CEO review of the C++ Weekly reminder row plan (2026-07-25).

**Effort:** M
**Priority:** P3
**Depends on:** None.

---

### Generalize the per-day sub-metric table for other habit kinds

**What:** The new `exercise_sub_counter_progress` table (4 rep counters per day) is Exercise-specific. Consider a reusable "sub-metric" table shape other habit kinds could use later (e.g., Reading session notes, Tanakh chapter difficulty rating).

**Why:** Avoids re-deriving the same per-day-keyed-table pattern from scratch if a second use case shows up.

**Context:** Surfaced during the `/autoplan` review (2026-07-19). Deliberately NOT built now — no second concrete use case exists yet, and building it speculatively would repeat the exact mistake Approach B (generalized detail-screen framework) was rejected for in the original design doc.

**Effort:** M
**Priority:** P4
**Depends on:** A second habit kind actually needing per-day sub-metrics.

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

### Reading reset confirm dialog undercounts sessions during an active session

**What:** The reset confirm dialog's preview text ("This deletes N sessions...") is sourced from `DashboardViewModel.readingSessionCountToday`, which counts only already-logged `ReadingSessionLog` rows. A currently-running session isn't logged until `stop()` runs, so if the user long-presses to reset while a session is actively ticking, the dialog undercounts by one (or, with zero prior completed sessions, shows the "clears today's progress" fallback with no session mention at all) — even though `resetToday()` will close out and delete that in-flight session too.

**Why:** Purely cosmetic — the reset itself is correct, only the preview text is slightly inaccurate, and only in the specific case where the destruction is largest (an active session). Cheapest fix if ever addressed: add 1 to the count when a session is currently running, or reword to "up to N sessions."

**Context:** Surfaced during the final whole-branch review of the Reading-reset/Tanakh-undo plan (2026-07-20). Reviewer's own assessment: "not worth blocking on... honestly it's fine as-is for this app."

**Effort:** S
**Priority:** P4
**Depends on:** None.

---

## Completed

### Unused `SubCounterRepository.valueForDate` (singular)

**What:** `SubCounterRepository.valueForDate(exerciseKey, date)` (singular, single-key lookup) was only referenced by its own unit tests — production code goes through `valuesForDate` (plural, batch-by-date) via `ExerciseViewModel.subCounterValuesForDate`, which already delivers the "no fabricated default for a past day" behavior the singular method was designed for.

**Why:** Dead code — removed along with its 2 tests; replaced with one test asserting `valuesForDate` on a missing past date returns an empty map rather than a fabricated default.

**Context:** Surfaced during the final whole-branch review of the exercise-port plan (2026-07-19).

**Completed:** commit e14cd66 (2026-07-19)

---

### Duplicated streak-calculation logic (CounterHabitRepository vs. HabitStats)

**What:** `CounterHabitRepository.currentStreak` and `HabitStats.currentStreak`/`isNewStreakRecord` independently implemented the same streak-anchor algorithm over the same completed-date set.

**Why:** Fixed — `CounterHabitRepository.currentStreak` now delegates to `HabitStats.currentStreak(HabitStats.parseDates(...), today)` instead of reimplementing the anchor/loop logic, so there's exactly one streak-anchor implementation. Existing `CounterHabitRepositoryTest` cases (behavioral, not implementation-specific) passed unchanged.

**Context:** Surfaced during the final whole-branch review of the exercise-port plan (2026-07-19).

**Completed:** commit e14cd66 (2026-07-19)

---

### Heatmap "miss" color is a fixed light gray in dark mode

**What:** `HeatmapMiss = Color(0xFFE0E0E0)` in `ExerciseColors.kt` rendered as a near-white tile in dark mode.

**Why:** Fixed — removed the `HeatmapMiss` constant; the heatmap's no-data cell now uses `MaterialTheme.colorScheme.surfaceVariant` at the call site, which already adapts to light/dark automatically (unlike `HeatmapHit`/`HeatmapPending`, "miss" carries no special status meaning, so it doesn't need a fixed semantic color). Verified on-device in both light and dark mode — dark mode now shows a proper dark gray tile instead of near-white.

**Context:** Surfaced during the final whole-branch review of the exercise-port plan (2026-07-19).

**Completed:** commit e14cd66 (2026-07-19)

---

### Retroactive edit of a past day's rep counts

**What:** Let the user tap a heatmap day (in the Exercise section of the unified Activity screen) and correct that day's logged rep counts, not just view them.

**Why:** Mistakes happen (wrong count logged, forgot to log same-day) and there was no way to fix history for any habit kind. Resolved for Exercise: `ExerciseViewModel.adjustSubCounterForDate` plus +/- edit controls in `SubCounterDetailDialog` let a past day's sub-counter values be corrected. Scoped to Exercise only (the sole habit kind with a per-day sub-metric table) — other habit kinds still have no edit-history mechanism, unchanged by this work.

**Context:** Surfaced during the `/autoplan` review of the Shape-into-Reminders exercise port (2026-07-19); resolved as a CEO cherry-pick during the `/autoplan` review of the ReadBook Activity Log plan (2026-07-19/20). That cherry-pick directly contradicted the plan's own inherited "Exercise stays view-only, unchanged" Global Constraint, which was corrected during the same review to allow it.

**Completed:** commit 4ad9fad (2026-07-20)

---

### Weekly aggregate summary notification

**What:** Shape has a Saturday weekly-summary notification (days this week/month, streak, record callouts). Reminders' notification system today is per-kind only (`ReminderReceiver`-equivalent, `WeeklySummaryReceiver` in Shape) — no aggregate-across-habits summary exists in Reminders.

**Why:** Resolved: `HabitScheduler.scheduleWeeklySummary` schedules a Sunday 09:00 alarm (self-healed daily alongside the existing rollover alarm from `RemindersApp.onCreate`/`BootReceiver`/`RolloverReceiver`), and `HabitReminderReceiver.handleWeeklySummary` aggregates Exercise/Reading/Tanakh completed-dates over the trailing 7-day window (`WeeklySummary.compute`) into a single cross-habit notification, with an all-zero-week suppression guard so a fresh install never posts a useless "0/7 · 0/7 · 0/7" nag.

**Context:** Surfaced during the `/autoplan` review (2026-07-19). Originally deferred as out of the exercise-port plan's blast radius; resolved as Task 7 of the ReadBook Activity Log plan (2026-07-19/20), which already needed the notification/scheduling framework touched for Reading's Start/Snooze actions.

**Completed:** commit 84defd9 (2026-07-20)
