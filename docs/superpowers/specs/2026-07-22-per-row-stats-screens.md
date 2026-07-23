# Design: Per-Row Statistics — Dedicated Stats Screens + Reading/Tanakh Long-Press Menus

Generated via `/office-hours` (1 round of adversarial review, 6/10 → issues fixed inline) on 2026-07-22
Repo: Reminders (Android, package `com.ziv.reminders`)
Status: APPROVED
Full design doc: `C:\Users\zivk\.gstack\projects\Reminders\zivk-main-design-20260722-114332.md`
Related: `zivk-main-design-20260721-130751.md` (Exercise dashboard long-press menu — this design extends the same mechanism to Reading/Tanakh and changes what Exercise's Statistics option opens)

## Problem Statement

Every dashboard row now increments/toggles/marks-read inline. Long-press behavior is inconsistent: Exercise already has a menu (Counter/Statistics) but "Statistics" opens the shared, unfiltered Activity screen (all 3 habits + combo-streak banner); Reading has no menu at all (long-press jumps straight to a destructive reset-confirm); Tanakh has no long-press whatsoever. The user wants every row's long-press to reach a habit-scoped "Statistics" destination, with Reading additionally offering "Reset today" in that same menu.

**Notable history:** `ExerciseStatsScreen.kt` carries its own doc comment recording that it used to be a standalone `"exerciseStats"` NavHost destination, consolidated into the shared Activity screen during the ReadBook Activity Log plan (2026-07-19/20). This design deliberately reverses that consolidation for the per-row entry point, while leaving the consolidated shared screen itself untouched (still reachable via the dashboard's top-bar list icon).

## Confirmed Decisions

1. **Long-press → Statistics is per-habit, via 3 new dedicated screens/NavHost destinations** (`exerciseStats`, `readingStats`, `tanakhStats`) — not a filtered mode on the existing shared Activity screen. Chosen explicitly over parameterizing the single shared screen (smaller diff) and over a bottom-sheet/dialog (no navigation) — see Approaches Considered.
2. **Reading's long-press changes from "always jump straight to reset-confirm" to "show a menu first"** — options: "Reset today" and "Statistics". "Reset today" runs the exact same fetch-session-count-then-confirm-dialog flow that exists today.
3. **Tanakh's long-press is entirely new** — a single "Statistics" option, plus `RowLongPressMenu`'s existing always-present "Cancel".
4. **Exercise's existing "Statistics" menu option is repointed** from the shared Activity screen to the new dedicated `exerciseStats` screen. This requires `DashboardScreen` to gain a genuine new `onOpenExerciseStats` parameter (today it's a hardcoded alias of `onOpenActivity`).
5. **The dashboard's top-bar list icon is untouched** — still opens the unfiltered, all-3-habit Activity screen with its combo-streak banner, as a separate overview.
6. **Short-tap behavior on all 3 rows is completely unchanged.**

## Approaches Considered

- **A) Parameterize the existing Activity screen** — smallest diff, reuses everything built. Not chosen — user preferred cleaner per-screen separation.
- **B) Three separate per-habit stats screens (CHOSEN)** — new `ExerciseStatsScreen`/`ReadingStatsScreen`/`TanakhStatsScreen` composables, 3 new NavHost destinations. More scaffold code, but each screen is trivially simple and the underlying section composables are already modular — only thin wrappers are added.
- **C) Bottom sheet / dialog instead of navigating away** — not chosen — inconsistent with Exercise's already-shipped navigating Statistics path, heatmap grids want more room than a sheet gives.

## Review Findings Summary

One round of adversarial review. Found 1 Consistency issue (a hard contradiction: the doc's stated approach required a 3rd new `DashboardScreen` parameter for Exercise's repointed callback, but the parameter count stated in the doc only listed 2 — fixed) and 7 Clarity/Completeness/Feasibility issues, all resolved inline: symmetric `today` computation between the two new Activity-package screens (both compute internally, no parameter), full specification of `TanakhStatsScreen`'s composables (previously underspecified relative to Reading's), explicit spelling-out of how Tanakh's tap-gating works under `combinedClickable`'s non-nullable `onClick`, explicit statement that all 3 new/updated screens share the identical `Scaffold`+`TopAppBar`+scrollable-`Column` shape, an accepted-and-noted inefficiency (`ActivityViewModel.refresh()` always loads all 3 habits, wasteful but negligible for a personal app), and two edge-case conclusions stated explicitly (pending-reset vs. navigate-away can't collide; Tanakh's Finished state is irrelevant to its stats screen).

## Success Criteria

- Long-pressing Exercise shows Counter/Statistics; Statistics opens an Exercise-only screen (streak/month/total + heatmap + sub-counter edit dialog), no other habit visible.
- Long-pressing Reading shows Reset today/Statistics; Reset today behaves identically to today; Statistics opens a Reading-only screen (streak/total + heatmap + session-delete dialog on day tap).
- Long-pressing Tanakh shows Statistics (+ Cancel); opens a Tanakh-only screen (streak/total + heatmap + today-only-undo dialog on day tap, past days view-only).
- The dashboard's top-bar list icon still opens the unchanged, unfiltered, all-3-habit Activity screen with its combo-streak banner.
- Short-tap behavior and existing undo snackbars on all 3 rows are unaffected. Full test suite green; on-device verification with no crashes.

## Distribution Plan

Existing deployment path (`installDebug` via Gradle to the connected device) — personal single-device app, no separate distribution channel.

## Next Steps

Implementation plan: `docs/superpowers/plans/2026-07-22-per-row-stats-screens.md`, executed via `subagent-driven-development` — same flow as every prior plan this session.
