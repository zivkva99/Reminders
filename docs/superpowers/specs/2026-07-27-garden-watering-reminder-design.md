# Design: Garden Watering Reminder — the INTERVAL_DUE Habit Kind

Generated via `/office-hours` (fully-formed user spec, no spec-review issues found) on 2026-07-27
Repo: Reminders (Android, package `com.ziv.reminders`)
Status: APPROVED

Full design doc: `C:\Users\zivk\.gstack\projects\Reminders\zivk-main-design-20260727-154006.md`

## Problem Statement

A new dashboard row, "water the garden," with due-date semantics none of the four existing
habit kinds (Counter, Timer, Schedule-cursor, Computed-schedule) support: the user chooses how
many days ahead to reschedule *each time* they complete the action, rather than a fixed daily
goal or fixed external schedule. The row is "not done" whenever its due date is today or
earlier. Short-press (when due) and long-press → "mark done + reschedule" both open a day-count
picker and log a completion. Long-press also offers "reschedule only" (no completion logged) and
"Statistics" (watering history). Watering late always logs *today's* date in the history, never
the original due date.

## Confirmed Decisions

1. **New `HabitKind.INTERVAL_DUE`** — not a retrofit of `COMPUTED_SCHEDULE`, not a speculative
   generalized primitive. Zero risk to the four shipped kinds; matches this codebase's established
   "named kinds, not a generic engine" instinct. See Approaches Considered.
2. **Reschedule offset counts from today**, not from the original due date, for both the
   mark-done and reschedule-only actions — counting from the old due date can land the new due
   date in the past when overdue, which would defeat the point of rescheduling.
3. **Statistics is a plain history log** (total count + list of watered dates) — no streak.
   A streak has no honest definition for a self-chosen, variable interval.
4. **First due date at seed time is today** — the user sets the real first interval themselves
   on first use, rather than the app guessing a placeholder date.
5. **Icon delegated to this design**: proposed a single-color watering-can glyph, matching the
   existing flat icon style (Shape's dumbbell, ReadBook's book, Tanakh's tablets). Confirm/swap
   at implementation time.
6. Cross-model second opinion was offered and declined — this is the app's 5th near-identical
   "add a habit kind" feature, already stress-tested by four shipped predecessors.

## Approaches Considered

- **A) Retrofit `COMPUTED_SCHEDULE`** — smaller diff, reuses an existing table/dispatch branch.
  Not chosen — conflates auto-computed fixed-interval scheduling with user-chosen variable
  intervals inside one repository, risking both features on every future change to either.
- **B) New kind `INTERVAL_DUE` (CHOSEN)** — full new-kind addition (Room entities, repository,
  `HabitStatus` case, `HabitEngine` dispatch branch, dashboard row + new day-count-picker
  dialog, long-press menu, Statistics screen), following the exact shape of every prior kind
  addition. More total files than A, but a clean, isolated diff with zero risk to shipped kinds.
- **C) Generalized reusable interval-cursor primitive** — same separation as B, built upfront to
  serve a hypothetical future second variable-interval reminder. Not chosen — no second use case
  exists yet; this repeats a generalization pattern this codebase's own TODOs already rejected
  once ("Generalize the per-day sub-metric table for other habit kinds").

## Architecture Summary

`IntervalDueProgress` (next due date, one row per instance) + `IntervalDueLog` (append-only
completion log, autoincrement id, one row per watering event) + `IntervalDueRepository`
(`todayStatus`/`markDone`/`rescheduleOnly`/`history`, both write methods requiring
`intervalDays >= 1`, callable regardless of due state) + `HabitStatus.IntervalDueStatus` +
`HabitEngine` dispatch branch + a new `IntervalDuePickerDialog` composable (the one genuinely new
UI primitive — no prior kind ever asks the user to pick a number at completion time) + a new
`IntervalDueStatsScreen` (fixed single-instance destination, history list, no heatmap, no
streak). Status dot is binary (due=red/not due=green), matching Counter's convention rather than
Schedule-cursor's 3-state one. No new scheduler code needed — `HabitReminderReceiver` already
re-evaluates status fresh on every existing per-instance alarm firing, regardless of kind. Full
detail, including the exact Room schema shapes and test plan, in the full design doc linked
above.

## Review Findings Summary

Two rounds of adversarial spec review. Round 1 (7/10) found one real implementation blocker
(the log table's primary-key strategy was left undecided, which blocks writing the required
migration) plus several soft-deferred decisions (status-dot color mapping, day-count input
validation, long-press availability when not due, notification-tap behavior) — all fixed inline.
Round 2 (8/10) found three minor items (a field-naming mismatch between the class signature and
surrounding prose, no stated upper bound on the day-count input, and an under-specified sentence
on how alarm rescheduling works for an arbitrary due date) — all fixed inline. Remaining open
items are the exact day-count picker UI shape (presets vs. free entry) and cosmetic polish
(icon glyph, notification copy wording) — see the full design doc's Open Questions.

## Success Criteria

- Row shows "due" whenever its due date is today or earlier, "not due" otherwise.
- Short-press (when due) and long-press "mark done + reschedule" both open the day-count picker,
  set `nextDueDate = today + N`, and log today's date to the watering history.
- Long-press "reschedule only" updates the due date without logging a completion.
- Watering late always logs today's date, never the original due date — verified by letting the
  row go overdue before pressing it.
- Statistics shows total-watered count and the full history list, no streak number.
- Full test suite green; on-device verification with no crashes.

## Distribution Plan

Existing deployment path (`installDebug`/`assembleDebug` via Gradle to the connected device) —
unchanged from every prior feature in this app.

## Next Steps

Implementation plan written: `docs/superpowers/plans/2026-07-27-garden-watering-reminder.md`,
reviewed via `/autoplan` (CEO + Design + Eng phases). The day-count picker's exact interaction
(preset chips 3/5/7/10/14 alongside free numeric entry) was decided during that review as a
SELECTIVE EXPANSION cherry-pick, not left open.
