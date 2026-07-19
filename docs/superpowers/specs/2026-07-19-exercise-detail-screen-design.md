# Design: Exercise Detail Screen (Shape port)

Generated via `/office-hours` + `/autoplan` review on 2026-07-19
Repo: Reminders (Android, package `com.ziv.reminders`)
Status: APPROVED

## Problem Statement

Reminders' dashboard renders three tappable rows (Exercise, Reading, Tanakh). Exercise is
currently a COUNTER-kind habit where tapping the row directly increments today's count in
place. The goal: tapping "Exercise" opens a new full-screen flow that looks and behaves
like the user's other app, Shape — a big "+" button, four named per-exercise rep counters
(lateral raise, arm rotation, sit-up, push-up), and a stats view (streak, this-month
count, "new record" callouts, 12-month heatmap). Back returns to the unchanged 3-row
dashboard. Reading and Tanakh are untouched — they keep their current inline tap behavior.

Beyond the visual port, the four rep sub-counters — purely decorative even in Shape itself
(no history, no daily reset) — gain real per-day persistence, and each heatmap day becomes
tappable to reveal that day's rep breakdown.

## Confirmed Decisions (from full review — see below)

1. **Interaction model:** full navigate. Tapping the Exercise row always opens the new
   screen; the increment button lives inside it, not on the dashboard row.
2. **Navigation:** Navigation-Compose (`NavHost`/`NavController`), not a hand-rolled
   `enum` + `BackHandler`. Three destinations: `dashboard`, `exerciseCounter`,
   `exerciseStats` — mirroring Shape's own two-screen internal structure (Counter +
   Stats, reached via an icon button) with Dashboard as the new root.
3. **Data reuse:** Exercise's existing goal/streak tracking (`CounterHabitRepository`,
   `HabitEngine`, `CounterDailyProgress`) is reused untouched — not rebuilt, not migrated
   to Shape's DataStore model.
4. **New pure stats logic:** `HabitStats.kt`, ported from Shape's `TrainingStats.kt`
   (month count, best month, "new record" detection) — Reminders has none of this today,
   only `currentStreak`.
5. **Sub-counter persistence:** new Room table, **one row per (exerciseKey, date)** with a
   single `count` column — NOT one row per date with 4 columns. (A 4-column design would
   let a partial `@Upsert` silently clobber the other 3 columns; keying by exerciseKey
   instead mirrors Shape's own independent-key model and eliminates that risk by
   construction.)
6. **Colors:** small, targeted semantic constants only (goal-reached green, heatmap
   hit/miss/pending — 4 values total), layered on top of Reminders' existing Material You
   dynamic theme. Not a full theme override — Reminders deliberately uses
   `dynamicDarkColorScheme`/`dynamicLightColorScheme` with no custom brand palette; Shape's
   own hardcoded `lightColorScheme()`/`darkColorScheme()` is not imported.

## Constraints

(Inherited from the main Reminders design doc, still binding.)

- Personal, single-user app. No accounts, no server, no CI/CD — local
  `./gradlew installDebug`.
- Package `com.ziv.reminders`, `minSdk = 35`, `targetSdk = 36`.
- Every Room schema change ships with a real `Migration` object; never
  `fallbackToDestructiveMigration()`.
- Scope is the Exercise row only — Reading and Tanakh dashboard rows and their existing
  behavior are untouched.

## Architecture

```
MainActivity
  └── NavHost(startDestination = "dashboard")
        ├── composable("dashboard")
        │     └── DashboardScreen (existing, modified: Exercise row's onClick
        │           navigates to "exerciseCounter" instead of incrementing inline;
        │           dispatches by instance ID, not by HabitKind generically)
        ├── composable("exerciseCounter")
        │     └── ExerciseCounterScreen (NEW, ported from Shape's CounterScreen.kt)
        │           ├── ExerciseViewModel (NEW — scoped to the Exercise flow only,
        │           │     NOT shared with DashboardViewModel)
        │           ├── uses: CounterHabitRepository, HabitEngine (existing, untouched)
        │           └── uses: SubCounterRepository (NEW) → SubCounterDao (NEW) →
        │                 `exercise_sub_counter_progress` table (NEW, migration v4→v5)
        └── composable("exerciseStats")
              └── ExerciseStatsScreen (NEW, ported from Shape's StatsScreen.kt)
                    ├── uses: HabitStats (NEW, pure, ported from TrainingStats.kt)
                    │     └── reads: CounterDailyProgressDao.getCompletedDates (existing)
                    └── uses: SubCounterRepository (NEW) — feeds the heatmap-day-tap
                          AlertDialog
```

`AppContainer` gains a second, parallel DI interface — `ExerciseDetailDataSource`
(mirroring `DashboardDataSource`'s exact shape) — exposing `counterHabitRepository`,
`habitEngine`, and the new `subCounterRepository`. `AppContainer` implements both
interfaces; `DashboardDataSource` itself is not extended with Exercise-only members.

## Approaches Considered

### Approach A: Literal port (data-model only, no history)
Copy Shape's Counter/Stats screens as-is; sub-counters become simple persistent values
with no per-day history — exactly as decorative as Shape today.
- Effort: S/M. Risk: Low. Ships fastest, but the heatmap-day-tap feature has nothing to
  show.

### Approach B: Generalized detail-screen framework
Build a generic "any dashboard row can declare a detail screen" abstraction instead of an
Exercise-specific flow, so Reading/Tanakh could reuse it later.
- Effort: L. Risk: Med. Rejected — speculative generality with no second concrete use
  case yet; more files and abstraction for a want scoped to exactly one row today.

### Approach C: Literal port + real workout log (chosen)
Same as A, plus real per-day persistence for the 4 rep sub-counters and a tappable
heatmap day showing that day's breakdown.
- Effort: M/L. Risk: Low (additive migration, following an established table pattern).
- Turns a cosmetic port into an actual per-day workout record.

## Recommended Approach

**Approach C.** Worth the extra schema work for a real log instead of a decorative copy;
the additional Room table is small and follows a proven, already-used table shape.

## Review Findings (from `/autoplan` — CEO + Design + Eng, dual-voice)

Full narrative review (premise challenges, dual-voice consensus tables, ASCII diagrams,
every auto-decision with rationale) lives at
`~/.gstack/projects/Reminders/zivk-main-design-20260719-134511.md`. The load-bearing
findings that changed this design, summarized:

- **Schema data-loss bug (critical, Eng review):** an earlier draft of this design used
  one row per date with 4 columns for the sub-counters. A partial `@Upsert` on a single
  column would silently clobber the other three. Fixed by re-keying to one row per
  (exerciseKey, date) — see Confirmed Decision 5.
- **Missing second screen (Design review):** an earlier draft used a single new nav
  state (`ExerciseDetail`), but Shape is actually two screens (Counter + Stats via an
  icon button) — collapsing them into one would have silently dropped structure. Fixed
  by using three destinations (see Confirmed Decision 2).
- **DI seam gap (Eng review):** `AppContainer`'s existing `DashboardDataSource` interface
  doesn't expose what the new Exercise flow needs. Fixed with a parallel
  `ExerciseDetailDataSource` interface (see Architecture above).
- **Stale dashboard count on back-navigation (Design review):** the dashboard's existing
  `ON_RESUME`-based refresh is tied to real Activity lifecycle events, not to
  Navigation-Compose destination re-entry. The implementation plan adds an explicit
  refresh trigger on returning to the dashboard destination, rather than relying on the
  existing mechanism alone.
- **Color palette conflict (CEO + Design, confirmed by both review voices):** Reminders'
  theme is Material You dynamic color with no custom brand; Shape hardcodes its own
  palette. Resolved via Confirmed Decision 6 (targeted semantic constants only).
- **Interaction-model friction (CEO review, user-decided):** an independent review voice
  flagged that replacing the dashboard's 1-tap increment with tap→navigate→tap adds
  friction to the single most frequent daily action, and suggested a hybrid (keep 1-tap
  increment, add a secondary affordance for the detail view). The user confirmed the
  full-navigate model as originally specified — the "friction" is the intended
  experience, not a bug to route around.
- **Navigation-library taste decision (CEO + Eng review):** a hand-rolled `enum` +
  `BackHandler` was initially recommended (matching this codebase's stated "no framework
  needed at this app's size" philosophy). The user chose Navigation-Compose instead —
  see Confirmed Decision 2.

## Success Criteria

- Tapping "Exercise" on the Reminders dashboard opens a screen visually and behaviorally
  matching Shape's Counter+Stats screens, reading/writing Reminders' existing Room data.
- Back (gesture or button) returns to the 3-row dashboard, with the Exercise row showing
  an up-to-date count (not stale).
- Reading and Tanakh rows behave exactly as they do today.
- Rep sub-counter values from a given day are retrievable later by tapping that day's
  heatmap cell; days before this feature existed (or with no data) show "No data for
  this day," never a misleading default value.

## Distribution Plan

N/A — personal device install only, no release pipeline.

## Next Steps

Hand off to `superpowers:writing-plans` for a task-by-task implementation plan, following
the same TDD/subagent-driven-development process as prior plans in this repo.
