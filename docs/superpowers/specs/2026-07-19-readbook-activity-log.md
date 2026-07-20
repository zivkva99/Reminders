# Design: Unified Activity Log — porting ReadBook's remaining features into Reminders

Generated via `/office-hours` (3 rounds of adversarial review) on 2026-07-19
Repo: Reminders (Android, package `com.ziv.reminders`)
Status: APPROVED
Full design doc: `C:\Users\zivk\.gstack\projects\Reminders\zivk-main-design-20260719-193820.md`

## Problem Statement

ReadBook (a separate personal app) had six features never rebuilt into Reminders when Reading/Tanakh's core tracking was ported earlier: a Reading stats/history screen, a per-session history log, actionable Start/Snooze notifications, a weekly summary notification, undo/day-edit affordances, and a lifetime total-days stat. Scope was extended during brainstorming to also add a Tanakh stats screen and to make the weekly summary cross-habit (Exercise+Reading+Tanakh combo-streak) instead of Reading-only.

## Confirmed Decisions (from full review — see full design doc for details)

1. **Unified Activity screen** (not three separate per-habit screens): one new `"activity"` NavHost destination shows Exercise/Reading/Tanakh sections side by side, reached from a new icon on a new `Scaffold`/`TopAppBar` on `DashboardScreen.kt` (currently a bare `Column`).
2. **`ExerciseCounterScreen`'s existing `onOpenStats` button is repointed** to the new `"activity"` destination; the standalone `"exerciseStats"` route is removed once the Activity screen's Exercise section is verified equivalent. This is the only place existing navigation behavior changes.
3. **`HeatmapGrid` is extracted** from `ExerciseStatsScreen.kt` into a shared composable, with its date-alignment/status logic first pulled into a pure, unit-tested function (`buildHeatmapDays`) — since no test coverage exists today for the Compose UI itself, this is the actual safety net for the extraction, not "existing tests."
4. **Reading gets a real session-log table** (`ReadingSessionLog`, autoincrement PK, one row per start/stop segment), wired into `TimerHabitRepository`'s existing `finishSession` path via an optional (nullable, default-null) constructor parameter — chosen specifically to avoid breaking the 9 existing test files that construct `TimerHabitRepository` with its current 2-arg signature.
5. **Lifetime `totalCount` is computed on-the-fly** (`completedDates.size`), not stored — no schema change, matching this codebase's existing philosophy (`StreakCalculator`/`TimerHabitRepository` never cache running totals).
6. **Undo/day-edit is per-kind, not shared code:** Reading gets session-log deletion; Tanakh gets "undo most recent mark-read" (decrement cursor + that day's count) — meaningful only for the cursor's current position, not arbitrary past days, since Tanakh has a single global cursor, not independent per-day state. **Exercise's existing `SubCounterDetailDialog` stays view-only, unchanged** — its own retroactive-edit TODO item remains separately deferred in `TODOS.md`, not solved by this plan.
7. **Cross-habit weekly aggregation is new logic**, written alongside (not through) the existing single-condition `CrossHabitEvaluator` — a 7-day-window query producing a per-habit weekly count plus a combo-streak count. This combo-streak number is surfaced in **two places**: a small element at the top of the Activity screen, and the payload of the new weekly-summary notification (resolves the design doc's "Reviewer Concern" about ambiguous placement).
8. **Notification actions:** `HabitReminderReceiver` gains real `intent.action` branching (it has none today — confirmed by reading `onReceive`, which unconditionally re-notifies regardless of the intent it was launched with). Reading's Start action routes through `ContextCompat.startForegroundService(...)` with `TimerService.ACTION_START` — never a direct repository call — so it converges with the in-app start path instead of leaving a running DB session with no foreground service. Snooze schedules a one-shot `AlarmManager.setWindow()` alarm with its own `requestCode`, additive to (not replacing) `HabitScheduler`'s existing hourly cadence. The weekly-summary notification is a plain tap-to-open (no action buttons), scheduled by its own new alarm in `HabitScheduler`, self-healed the same way as the existing hourly/rollover alarms (`RemindersApp.onCreate`, `BootReceiver`, `RolloverReceiver`).

## Approaches Considered

- **A) Literal extension** — three separate per-habit stats screens, simplest, fastest, lowest risk.
- **B) Generalized detail-screen framework** — one generic screen/data layer for all habit kinds; rejected as premature abstraction (repeats a pattern the Exercise feature's own founding design doc already rejected for lacking a second use case).
- **C) Unified cross-habit Activity Log (CHOSEN)** — one screen for all three habits, most ambitious, matches the user's demonstrated preference for architectural symmetry over shipping speed (chosen against the recommendation of A).

## Review Findings Summary

Three rounds of adversarial subagent review caught 11 issues total, all fixed or explicitly resolved in the full design doc, most substantively: the notification Start-action would have bypassed `TimerService`'s foreground service (fixed by routing through it); `ExerciseCounterScreen`'s existing stats button would have been left as a duplicate entry point (fixed by repointing + removing the standalone route); the "reusing `CrossHabitEvaluator`" and "reusing `HeatmapGrid`'s tests" claims were both overstated (corrected to "new logic alongside" and "new characterization tests," respectively). Two residual moderate-but-non-blocking concerns (combo-streak placement, notification-scheduling specifics) are resolved above and in the implementation plan.

## Success Criteria

- Reading and Tanakh both have a stats screen (streak, lifetime-total, heatmap) reachable from the new Activity entry point; existing dashboard row tap behavior for all three habits is unchanged.
- Reading has a real per-session history log visible in its Activity section.
- Reading's nudge notification has working Start and Snooze-15m actions that converge with the in-app start/stop path.
- One Sunday weekly-summary notification fires with per-habit counts and a cross-habit combo-streak number; the same number also appears on the Activity screen.
- Tapping a day in Reading's or Tanakh's section supports correcting/undoing that data (Exercise's dialog intentionally stays as-is).
- Existing Exercise stats screen/tests show zero behavior change after the `HeatmapGrid` extraction.
- All new Room schema changes are additive migrations with Robolectric tests; `totalCount` needs no schema change at all.

## Distribution Plan

Existing deployment path (`installDebug` via Gradle to the connected device) — personal single-device app, no separate distribution channel.

## Next Steps

Implementation plan: `docs/superpowers/plans/2026-07-19-readbook-activity-log.md`, executed via `subagent-driven-development` — same flow as the Exercise detail-screen feature earlier this session.
