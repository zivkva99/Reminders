# Design: Reading Reset, Tanakh Quick-Undo, and Behind-Count Indicator

Generated via `/office-hours` (3 rounds of adversarial review) on 2026-07-20
Repo: Reminders (Android, package `com.ziv.reminders`)
Status: APPROVED
Full design doc: `C:\Users\zivk\.gstack\projects\Reminders\zivk-main-design-20260720-151658.md`
Related: `docs/superpowers/specs/2026-07-19-readbook-activity-log.md` (same session's prior feature, not superseded — this spec adds 3 small features on top of what that one shipped)

## Problem Statement

After the Unified Activity Log shipped, the user flagged 3 remaining ReadBook-parity gaps:

1. **No "Reset today" for Reading** — confirmed absent from the codebase.
2. **Tanakh's undo exists but is undiscoverable** — `ScheduleCursorRepository.undoMarkRead` was wired into the Activity screen by the prior plan, but is reachable only via Dashboard → Activity icon → scroll past Exercise's heatmap → Tanakh section → tap today's cell.
3. **No "chapters behind" indicator on the dashboard** — `HabitStatus.ScheduleCursorStatus.dueCount` is already computed and threaded through, but never read anywhere in the UI layer.

## Confirmed Decisions (from full review — see full design doc for details)

1. **Reading "Reset today":** long-press the dashboard's Reading row (no new UI chrome), gated behind a confirm `AlertDialog`. Full reset, confirmed by the user against a lighter recommended option: `TimerDailyProgress` resets to full target/not-completed, and **all** of today's `ReadingSessionLog` rows are deleted — "pretend today never happened," not just a completion-flag rollback.
2. **Race condition, corrected across 2 review rounds:** the original "stop the service, then reset" sequencing didn't guarantee ordering, since `TimerService.handleStop()` persists via a fire-and-forget coroutine. Fixed by having `TimerHabitRepository.resetToday()` call the existing idempotent `stop()` **itself, awaited, as its first internal step** — never routed through the service/Intent — so any active session is closed out (and logged) before resetToday's own writes run. The caller (`DashboardViewModel`) sends `TimerService.ACTION_STOP` only **after** `resetToday()` returns, purely to tear down the foreground notification — never concurrently with the DB write. A narrow residual race (the service's own `autoCompleteJob` firing at the identical instant) is accepted as a documented edge case for a personal single-user app, not silently ignored.
3. **Atomicity:** `resetToday()`'s upsert-then-delete pair runs inside an injectable `runInTransaction` lambda, defaulting to a no-op passthrough — mirroring `TimerHabitRepository`'s existing nullable-default `sessionLogDao` pattern so every fake-DAO-based unit test keeps compiling unchanged. Production wiring (`AppContainer`) passes `AppDatabase.withTransaction { }` for real atomicity; a plain Room `@Transaction` annotation doesn't apply here since the method spans two separate DAOs.
4. **Tanakh quick-undo:** a `Snackbar` on `DashboardScreen`'s existing `Scaffold` (added in the prior plan) with an "Undo" action, shown after marking a chapter read. `DashboardViewModel.onMarkRead` is converted from a fire-and-forget `viewModelScope.launch` into a plain `suspend fun` (mirroring the `ExerciseViewModel.adjustSubCounterForDate` fix from the prior plan) so the caller can await it before showing the snackbar; a new `suspend fun onUndoMarkRead` calls the already-built `ScheduleCursorRepository.undoMarkRead`. The existing Activity-screen undo path is unchanged — this adds a second, faster path to the same operation. If the user navigates away before tapping "Undo," Compose's `SnackbarHost` is scoped to `DashboardScreen`'s own composition and is torn down gracefully — no crash, no dangling state.
5. **Tanakh "N behind" indicator:** `ScheduleCursorHabitRow`'s existing text (`"${status.book} ${status.chapterHeb}"`) gains a `" · N behind"` suffix when `status.dueCount > 0` — pure display-layer change, no new data.

## Approaches Considered

- **A) One combined plan, all 3 features together (CHOSEN)** — matches the user's repeated preference from the prior mission ("all in one big plan"); all 3 are small and non-overlapping.
- **B) Three separate small plans** — rejected, coordination overhead not justified given how independent the features already are.
- **C) Bundle by shared UI surface** (Tanakh's two features together, Reading separate) — rejected in favor of A for the same reason as B.

## Review Findings Summary

Three rounds of adversarial subagent review. Round 1 found 5 issues (all fixed). Round 2 verified 3 of those fixes and found the race-condition fix was an overclaim plus a leftover stale class-name reference and a Success-Criteria self-contradiction — all fixed. Round 3 confirmed the corrected race-condition design is sound (traced against the actual `TimerHabitRepository.stop()`/`TimerService` code) and found only two low-severity documentation nits (an internally-inconsistent timing claim, and a `@Transaction`-vs-`withTransaction` mechanism mismatch), both closed with one-sentence corrections. Final Quality Score: 8/10, ready to implement.

## Success Criteria

- Long-pressing the Reading dashboard row shows a confirm dialog; confirming resets the timer to full, clears completion, and deletes today's session log — verified both idle and while a session is actively running.
- Marking Tanakh read from the dashboard shows an "Undo" snackbar; tapping it reverts the mark without needing to open the Activity screen.
- Tanakh's dashboard row shows a "N behind" suffix whenever `dueCount > 0`, nothing extra otherwise.
- Zero regressions to existing dashboard/Activity behavior; full test suite green; on-device verification with no crashes.

## Distribution Plan

Existing deployment path (`installDebug` via Gradle to the connected device) — personal single-device app, no separate distribution channel.

## Next Steps

Implementation plan: `docs/superpowers/plans/2026-07-20-reading-reset-tanakh-undo.md`, executed via `subagent-driven-development` — same flow as the prior two plans this session.
