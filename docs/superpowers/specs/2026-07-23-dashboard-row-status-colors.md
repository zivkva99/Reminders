# Design: Dashboard Row Status Colors (Red/Orange/Green)

Generated via `/office-hours` (1 round of adversarial review, 7/10 → 3 issues fixed inline) on 2026-07-23
Repo: Reminders (Android, package `com.ziv.reminders`)
Status: APPROVED
Full design doc: `C:\Users\zivk\.gstack\projects\Reminders\zivk-main-design-20260723-141438.md`

## Problem Statement

The dashboard's 3 habit rows carry no at-a-glance visual signal of "am I on track today." Add a small status-color dot to each row.

**Real gap found in the existing code:** `HabitStatus.ScheduleCursorStatus` currently collapses `ScheduleEntryStatus.OnSchedule` (today's dated chapter due, unread) and `ScheduleEntryStatus.Waiting` (nothing due yet) into the same shape — no field distinguishes them, which is exactly what Orange vs. Green needs.

**Second gap found:** the existing `completed` flag means "was *any* entry marked read today," not "was the entry *dated* today read." If 4 chapters behind and today's catch-up only clears the oldest overdue entry, `completed` is `true` even though today's actual dated chapter is still unread and the backlog persists. Tanakh's color must be computed from the pure schedule-position status, not from `completed`.

## Confirmed Decisions

1. **Exercise**: 2 colors only. Green if `HabitStatus.CounterStatus.completed`, else Red. No backend change.
2. **Reading**: 2 colors only. Green if `HabitStatus.TimerStatus.completed`, else Red. No backend change.
3. **Tanakh**: 3 colors from schedule-position status directly:
   - **Red** = `Behind` (`dueCount > 0`) — regardless of `completed`.
   - **Orange** = `OnSchedule` (today's dated chapter due, unread).
   - **Green** = `Waiting` or `Finished` (cursor past today's entry, or no chapters left) — nothing outstanding.
4. **New backend field**: `HabitStatus.ScheduleCursorStatus` gains `isDueToday: Boolean`, computed in `ScheduleCursorRepository.todayStatus()`'s existing `when` branches — `true` only for `OnSchedule`.
5. **Rendering**: a small colored dot next to each habit's name — not an edge stripe, not a full-row background tint, to avoid clashing with the existing error-colored "N behind" text.
6. **Colors reused where they already exist**: Red → `MaterialTheme.colorScheme.error` (already used for "N behind" text and the Reading reset button). Green → existing `GoalGreen` constant (`0xFF2E7D32`, already used for "goal met" across Exercise screens and the shared heatmap). Orange → one new constant `StatusOrange` added alongside `GoalGreen`/`HeatmapHit`/`HeatmapPending` in `ExerciseColors.kt`.

## Approaches Considered

- **A) Minimal + dot indicator (CHOSEN)** — one new `isDueToday: Boolean` field, color computed inline per-row, rendered as a small dot. Smallest diff.
- **B) Shared `RowColorState` enum across all 3 `HabitStatus` subtypes** — not chosen, no second use case for a shared cross-kind color concept, touches Exercise/Reading's data layer for zero new behavior.
- **C) Minimal backend + colored edge stripe** — not chosen, bigger visual change than a dot.

## Review Findings Summary

One round of adversarial review, quality score 7/10. Found 1 Important issue (adding the dot as a literal 3rd child of the existing `Arrangement.SpaceBetween` Row would break the flush-left/flush-right layout — SpaceBetween redistributes all children evenly) and 2 Minor (imprecise dot sizing, missing explicit call-out of 2 new cross-package imports). All 3 fixed inline: the dot and the existing name/streak Column are now wrapped together in a new inner Row (`Arrangement.spacedBy(8.dp)`) as the outer Row's single first child; exact sizing (`10.dp` dot, `8.dp` gap) specified; the 2 new imports (`GoalGreen`, `StatusOrange` from `ui.exercise`) called out explicitly.

## Success Criteria

- Exercise/Reading dots: green once today's goal is met, red otherwise, never orange.
- Tanakh dot: red with any backlog (even if something was marked read today), orange when today's dated chapter is due and unread with no backlog, green when caught up/ahead or finished.
- Existing row content (name, streak, status text, "N behind" text, checkmarks) visually unchanged apart from the new dot.
- Full test suite green, including updated/new repository tests for `isDueToday`. On-device verification confirming all 3 dots show the correct color for real current state, and that completing Exercise flips its dot live.

## Distribution Plan

Existing deployment path (`installDebug` via Gradle) — personal single-device app.

## Next Steps

Implementation plan: `docs/superpowers/plans/2026-07-23-dashboard-row-status-colors.md`, executed via `subagent-driven-development`.
