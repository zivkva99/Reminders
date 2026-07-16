# Design: Cross-Habit Evaluator

Generated via brainstorming session on 2026-07-16
Repo: Reminders (Android, package `com.ziv.reminders`)
Status: APPROVED

## Problem Statement

The original Reminders design doc (2026-07-14) scoped a `WorkManager`-based cross-habit
evaluator as the one capability none of the three source apps (Shape, ReadBook) had —
letting habits react to each other, rather than three independent alarm trains running
in isolation. It deliberately deferred picking the actual v1 rule to planning time,
scoping only the shape of the constraint: "the evaluator only updates or escalates an
already-fired notification for an existing habit — it never issues a competing/duplicate
notification ahead of or racing against that habit's own alarm," and explicitly rejected
building general rule-authoring infrastructure ahead of a known need.

With all three habit kinds (Counter, Timer, Schedule-cursor) now shipped and verified,
this is that decision: one concrete rule, connecting Exercise (Counter) and Reading
(Timer), plus the scheduling and notification-channel work needed to run it safely
alongside the existing per-habit hourly reminder system.

## The Rule

Twice daily — **8:00am** and **1:00pm** — check three things about *today*:

- Exercise (`HabitStatus.CounterStatus`) is **not** completed
- Reading (`HabitStatus.TimerStatus`) is **not** completed
- Reading's `currentStreak > 0` (there's an active streak worth protecting)

If all three hold, escalate Reading's reminder notification: stronger wording, and a
higher-priority channel (sound + heads-up), replacing (not duplicating) Reading's
existing reminder notification for today. If the condition isn't met at 8am but becomes
true by 1pm, escalation happens then instead. If it's already true at 8am, the escalated
notification simply lands early — the 1pm run re-checks but has nothing further to do if
Reading is by then completed (the run is a no-op once Reading is done, regardless of
Exercise's state).

This is a single hardcoded condition, not a generic rule-authoring engine — consistent
with the original design doc's explicit "3 kinds, not a fully generic engine" instinct
applied here to the evaluator too.

## Constraints

(Inherited from the main design doc, still binding.)

- Personal, single-user app. No accounts, no server.
- Package `com.ziv.reminders`, `minSdk = 35`, `targetSdk = 36`.
- Scheduling for the *existing* per-habit reminders stays `AlarmManager.setWindow()`
  (inexact) — unchanged by this design. The evaluator itself uses `WorkManager`, per the
  main design doc's explicit choice (periodic/deferred work is an acceptable, even
  correct, fit for a twice-daily check with no need for to-the-minute precision).
- The evaluator must never race ahead of or duplicate a habit's own alarm-driven
  notification — it may only *update* an existing notification identity (same
  channel-independent notification ID) with escalated content.
- No general rule-authoring UI or config — the rule is Kotlin code, not data.

## The Coordination Problem, and Its Resolution

Reading's existing hourly reminders (`HabitScheduler.REMINDER_HOURS` = 9, 10, 11, 12, 13,
shared across all habit instances via `AlarmManager`/`HabitReminderReceiver`) already post
a plain reminder whenever Reading isn't completed, regardless of Exercise. If the
evaluator escalates Reading's notification at 8am, the very next hourly reminder (9am)
would otherwise silently overwrite it with the plain version — undoing the escalation an
hour later.

**Resolution:** a new per-day flag, `evaluator_escalation(habitInstanceId, date,
escalated)`, mirroring the shape of the existing daily-progress tables. Once the
evaluator escalates a given instance on a given day, `HabitReminderReceiver` checks this
flag before posting its normal reminder and skips if already escalated — the escalated
notification stands untouched for the rest of that day. A new day starts fresh
automatically (no row for that date = not escalated), same as every other daily-progress
table in this app. Reading actually being completed (whenever that happens) already
suppresses *all* further reminders via the existing `completed` check, escalated or not —
no new cancel-on-completion logic is needed.

## Approaches Considered

### Approach A: Raw `PeriodicWorkRequest`, shared escalation channel
One 24-hour (or 5-hour, to hit both 8am/1pm-ish) periodic request, and a single shared
"escalations" channel reused by any future rule rather than one per instance. Less code,
but periodic `WorkManager` requests anchor to first-enqueue time and drift across days/
Doze deferrals with no way to re-anchor to a specific wall-clock target, and a shared
channel is a weaker fit given the codebase's established per-instance-channel precedent
(the Timer kind's own ongoing-notification channel).

### Approach B: Self-rescheduling `WorkManager` chain, per-instance escalation channel (chosen)
A single one-time `WorkRequest`-based job that, each time it runs, computes whichever of
{8am, 1pm} comes next and re-enqueues itself with the right initial delay — the same
self-chaining shape `RolloverReceiver` already uses on `AlarmManager`, just on
`WorkManager`. Adds one new per-instance channel (`habit_<id>_escalated`,
`IMPORTANCE_HIGH` + sound) so the escalated notification is genuinely more prominent —
Android ties notification importance to the channel, not a per-notification field, on
this app's `minSdk`, so a second channel is the only way to actually raise priority for
one firing without permanently changing Reading's normal reminder channel. Slightly more
code than Approach A, but noticeably tighter daily-anchor timing and consistent with the
existing architectural precedent.

## Recommended Approach

**Approach B.** The tighter timing anchor matters for a twice-daily rule tied to specific
times of day, and reusing the per-instance-channel pattern already proven by the Timer
kind is more consistent than introducing a new shared-channel convention for a single
rule.

## Architecture

- **`EvaluatorEscalation`** (Room entity) + **`EvaluatorEscalationDao`**: `getByDate(habitInstanceId,
  date): EvaluatorEscalation?`, `upsert(...)`. Schema v3→v4 migration, real `Migration`
  object (never `fallbackToDestructiveMigration()`), no new `habit_instance` column.
- **`CrossHabitEvaluator`**: `suspend fun evaluate(today: LocalDate)` — reads Exercise's
  and Reading's `todayStatus`/`currentStreak` via the existing `HabitEngine`
  (looking up both known seeded instance IDs, `EXERCISE_HABIT_INSTANCE_ID` and
  `READING_HABIT_INSTANCE_ID`), applies the rule, and if escalating: writes the
  `EvaluatorEscalation` flag for Reading + posts the escalated notification.
- **`EscalationWorker : CoroutineWorker`**: calls `CrossHabitEvaluator.evaluate(...)`
  inside a try/catch (a failure must not break the chain), then unconditionally
  re-enqueues the next run via `WorkManager.enqueueUniqueWork` (`ExistingWorkPolicy.REPLACE`
  for the self-reschedule) targeting whichever of {8am, 1pm} is next.
- **`HabitNotifications`** gains: `escalatedChannelId(habitInstanceId)`,
  `createEscalatedChannel(...)` (`IMPORTANCE_HIGH`, sound), and
  `buildEscalatedReminderNotification(context, instance)` — posted under the *same*
  `notificationId(instance)` as the normal reminder, so it replaces rather than
  duplicates.
- **`HabitReminderReceiver`** gains one check before posting its normal reminder: skip if
  `EvaluatorEscalationDao.getByDate(habitInstanceId, today)?.escalated == true`.
- **`RemindersApp.onCreate()`** ensures the `EscalationWorker` chain is enqueued
  (idempotent — `enqueueUniqueWork` with `ExistingWorkPolicy.KEEP` so app-open self-heal
  never duplicates or resets an already-running chain), alongside the existing
  scheduler/reconcile calls.

## Error Handling

`EscalationWorker` always re-enqueues the next run, even if `evaluate()` throws — the
chain must survive a single bad run indefinitely, the same resilience contract
`BootReceiver`/`RemindersApp`'s self-heal already holds for the rest of the app. If
either habit instance is missing (shouldn't happen given startup seeding, but defensive),
the evaluator skips silently rather than crashing.

## Testing Plan

- `CrossHabitEvaluator`: TDD'd with fake DAOs/a real `HabitEngine` — escalates when all
  three conditions hold; doesn't when Exercise is done; doesn't when Reading is done;
  doesn't when Reading's streak is 0; correctly writes the escalation flag.
- `HabitReminderReceiver`: one new test for the skip-when-escalated-today path.
- `EscalationWorker`: this project's first use of `androidx.work:work-testing`
  (`TestListenableWorkerBuilder`) — no existing precedent in this codebase to follow, so
  this establishes the pattern for any future `WorkManager` usage.
- On-device: like the hourly hourly-reminder-firing checks in prior plans, actually
  observing a real 8am/1pm firing end-to-end isn't forceable without manipulating the
  system clock (which this project has consistently avoided) — on-device verification
  leans on `adb shell dumpsys jobscheduler`/WorkManager's own diagnostics to confirm the
  job is scheduled at all, plus natural observation over the following day(s).

## Success Criteria

- The evaluator runs at 8am and 1pm daily, indefinitely, surviving app kills, reboots,
  and a single failed run.
- When the three-part condition is met, Reading's notification is visibly escalated
  (stronger wording, higher-priority channel) and is never immediately overwritten by
  the normal hourly reminder for the rest of that day.
- Completing Reading (however/whenever) suppresses all further reminders that day,
  escalated or not, via the existing `completed` check — no regression to that behavior.
- No new exported components, no new user-facing configuration — matches the "config,
  not code" instinct the rest of this app follows.

## Next Steps

Hand off to `superpowers:writing-plans` for a task-by-task implementation plan, following
the same TDD/subagent-driven-development process as Plans 1-3.
