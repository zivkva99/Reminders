# Design: Battery-Optimization Exemption Prompt

Generated via brainstorming session on 2026-07-16
Repo: Reminders (Android, package `com.ziv.reminders`)
Status: APPROVED

## Problem Statement

The original Reminders design doc (2026-07-14) flagged that some OEMs (Samsung, Xiaomi)
aggressively kill background alarms/work without a battery-optimization exemption, and
scoped a first-launch prompt for it — deferred until after all three habit kinds and the
cross-habit evaluator were built and proven. With all four of those now shipped, this is
that prompt.

Neither source app (Shape, ReadBook) has any existing battery-optimization-exemption
code to reuse — this is genuinely new to Reminders, not a port of proven logic.

## The Feature

On every `MainActivity.onCreate()`, alongside the existing (already-shipped)
`requestNotificationPermissionIfNeeded()` call, add a matching
`requestBatteryOptimizationExemptionIfNeeded()`:

- Check `PowerManager.isIgnoringBatteryOptimizations(packageName)`.
- If `false`, launch `Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
  Uri.parse("package:$packageName"))` via `startActivity(...)` — the system's own
  exemption dialog, no custom UI.
- No persisted "already asked" state, no denied-state banner: this mirrors what's
  *actually* shipped for the notification permission today (a silent per-launch
  check-and-request), not the original design doc's fuller "denied-state banner"
  aspiration, which was never built for notifications either — consistency with the
  real, current codebase wins over the original doc's more elaborate description.

## Manifest Change

`android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — a normal permission, granted
automatically at install (no runtime prompt for the permission itself; only the
resulting system dialog needs user interaction). Required to launch
`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

## Testing

No unit test for this feature. `MainActivity`'s existing notification-permission check
has zero test coverage in this codebase — it's plain Activity glue code around two
standard Android system calls, not repository/dispatch logic, and this follows that same
established precedent rather than introducing a new testing convention for a few lines
of boilerplate.

## Out of Scope

- **Xiaomi/MIUI Autostart note**: the design doc flagged this as conditional on the
  target phone being MIUI-based. The phone this app is verified on (Samsung SM-S938B) is
  not MIUI — this item is not applicable and is not part of this plan.
- Any custom in-app explanation screen, rationale dialog, or settings-screen deep link
  beyond the single system-intent launch described above.

## Success Criteria

- On a fresh install (or an existing install without the exemption already granted),
  opening the app triggers the system's battery-optimization exemption dialog.
- Once granted, subsequent launches no longer trigger the dialog (the underlying
  `PowerManager` check naturally reflects the granted state — no app-side state needed).
- No crash, no regression to the existing notification-permission request happening
  alongside it in the same `onCreate()`.

## Next Steps

Hand off to `superpowers:writing-plans` for a small implementation plan (likely a single
task, given the size of this change).
