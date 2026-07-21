# Design: Exercise Dashboard Row — Inline Increment + Long-Press Menu

Generated via `/office-hours` (2 rounds of adversarial review, ended 9/10) on 2026-07-21
Repo: Reminders (Android, package `com.ziv.reminders`)
Status: APPROVED
Full design doc: `C:\Users\zivk\.gstack\projects\Reminders\zivk-main-design-20260721-130751.md`
Related: `zivk-main-design-20260719-134511.md` (original Exercise detail screen design — this design partially reverses one of its decisions)

## Problem Statement

The Exercise dashboard row was the only row that didn't increment inline — tapping it navigated away to a full-screen counter instead (a deliberate choice in the original Exercise-detail-screen design, made to fix a real sub-counter data-loss bug). The user wants short-tap to increment inline like Reading/Tanakh's rows already do, with a quick-undo snackbar, and long-press to offer a choice of destination (Counter screen or Statistics) instead.

## Confirmed Decisions (from full review — see full design doc for details)

1. **Short-tap increments the aggregate count by 1** — identical to what `ExerciseCounterScreen`'s `+` button already does. Sub-drill counters (Lateral Raise, Arm Rotation, Sit-up, Push-up) stay screen-only, untouched by the dashboard.
2. **Undo reverses exactly one increment**, floored at 0, un-sets `completed` if it drops below goal — same shape as `ScheduleCursorRepository.undoMarkRead`'s guard. New `CounterHabitRepository.undoIncrement` method.
3. **Long-press replaces short-tap's current "navigate to full screen" behavior entirely.** Short-tap becomes a pure increment action, matching any other Counter-kind habit; long-press becomes the only path to navigation, via a small reusable menu mechanism (`RowMenuOption`/`RowLongPressMenu`) — chosen over a one-off inline dialog specifically for reusability, even though no second use case exists today (a deliberate departure from this session's usual anti-premature-generalization stance, confirmed explicitly by the user).
4. **Scope is Exercise's row only** — Reading and Tanakh rows are unaffected.
5. **`ExerciseCounterScreen`'s internal stats icon is removed** — confirmed as the only caller of `onOpenStats` (`MainActivity.kt`), so removal is fully scoped with no other call sites.
6. **Accepted, not fixed:** a pre-existing rapid-double-tap race (read-then-write, not atomic) already present in `onMarkRead`'s identical wiring — acknowledged, not engineered around, for a personal single-user app.
7. **Accepted, not new:** tapping an already-completed row still increments past goal, uncapped — matches `ExerciseCounterScreen`'s existing `+` button behavior exactly, not a new cap introduced by this design.

## Approaches Considered

- **A) Minimal, inline Exercise-specific dialog** — smallest diff, reuses shipped patterns verbatim; 2-option logic not reusable.
- **B) Generalized long-press menu system (CHOSEN)** — small reusable mechanism (one data class, one composable); no second use case exists yet, chosen anyway for reusability.
- **C) Separate quick-add button, tap unchanged** — doesn't match what was asked; adds new UI chrome.

## Review Findings Summary

Two rounds of adversarial review. Round 1 found 5 issues, all Minor/Clarity-level (no Critical/blocking findings): an unaddressed rapid-double-tap race (fixed by explicit acknowledgment, not a guard), unstated over-goal-tap behavior (fixed via a new Success Criteria bullet), a stale function name (`shouldNavigateToExerciseDetail` reused for long-press-only logic — renamed to `hasExerciseDetailMenu`), missing plumbing code (the `HabitRow`/`DashboardScreen` signature threading for the new stats callback — added in full), and incomplete Success Criteria for repeated-undo scenarios (added). Round 2 confirmed all 5 resolved, verified the nullable `onLongClick` API shape against the actual Compose Foundation bytecode this project depends on, and found no new issues. Final Quality Score: 9/10.

## Success Criteria

- Short-tapping the Exercise row increments in place (no navigation), shows an "Undo" snackbar, and undo correctly reverses exactly one increment, floored at 0.
- Tapping an already-completed row still increments past goal, uncapped (matches existing `+` button behavior).
- Repeated undo and undo-after-navigate-away both behave correctly.
- Long-pressing the Exercise row shows a menu with "Counter" and "Statistics"; either option navigates correctly; no crash regardless of choice or dismissal.
- `ExerciseCounterScreen` no longer shows its own stats icon; big `+` button and sub-drill counters unchanged.
- Reading and Tanakh rows show zero behavior change. Full test suite green; on-device verification with no crashes.

## Distribution Plan

Existing deployment path (`installDebug` via Gradle to the connected device) — personal single-device app, no separate distribution channel.

## Next Steps

Implementation plan: `docs/superpowers/plans/2026-07-21-exercise-row-longpress-menu.md`, executed via `subagent-driven-development` — same flow as every prior plan this session.
