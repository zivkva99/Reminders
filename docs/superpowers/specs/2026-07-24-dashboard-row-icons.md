# Design: Dashboard Row Icons

Generated via `/office-hours` (1 round of adversarial review, 8/10 → 3 issues fixed inline) on 2026-07-24
Repo: Reminders (Android, package `com.ziv.reminders`)
Status: APPROVED
Full design doc: `C:\Users\zivk\.gstack\projects\Reminders\zivk-main-design-20260724-091738.md`

## Problem Statement

Each dashboard row now carries a status dot (red/orange/green) but no icon identifying the habit itself. Exercise and Reading should use their real source apps' actual icons (Shape and ReadBook — both exist as sibling repos on this machine, confirmed by directly reading their icon files). Tanakh has no source app and needs a brand-new icon that reads as unmistakably biblical.

## Confirmed Decisions

1. **Exercise's icon is Shape's real launcher icon**: a hexagon with a plus sign inside, on background color `#2E7D32` — the same color this app's `GoalGreen` constant derives from.
2. **Reading's icon is ReadBook's real launcher icon**: an open book with two shaded pages, a spine, a bookmark ribbon, and a grounding shadow.
3. **Both render in full original color** — the status dot stays the sole "status" signal; icons are identity, not status.
4. **Tanakh's icon is a new design: two stone tablets** (Ten Commandments silhouette) — chosen over closed-book-with-Star-of-David and a scroll for maximum silhouette distinction from Reading's open-book icon.
5. **Placement**: icon is the new leading element inside each row's existing inner `Row` (the one holding the status dot + name/streak Column) — `[Icon] [Dot] [Column]` — not a new top-level child of the outer `SpaceBetween` Row.

## Technical Note: Adaptive-Icon Flattening

Shape's and ReadBook's launcher icons are Android adaptive icons (separate background + foreground layers, composited by the OS). A row icon isn't going through that system, so each needs flattening into one self-contained vector:
- **Reading**: no flattening needed, its foreground is already self-contained. Paths reused verbatim, just resized.
- **Exercise**: its foreground alone (white hexagon, dark green plus) depends on the green background layer for contrast. Flattened by recoloring the hexagon to Shape's own background color (`#2E7D32`) and the plus to white — reproduces the same visible result as the real composited icon.

## Approaches Considered

- **A) Flatten and directly reuse the real source-app vectors (CHOSEN)** — smallest diff, most literal interpretation of the request.
- **B) Redraw simplified/stylized versions** — not chosen, the real vectors are already simple and detail would be lost for no benefit.
- **C) Generic Material icons** — not chosen, contradicts the explicit request for the real source-app icons.

## Review Findings Summary

One round of adversarial review, quality score 8/10. Found 1 real, compile-breaking gap (the doc's code sample used `Image`/`painterResource`/`R` without ever instructing that `DashboardScreen.kt` needs these 3 as new imports — confirmed absent from its current import block) and 2 minor clarity notes (no same-project precedent for declaring a vector smaller than its viewport — noted as standard-but-new; the code sample not labeling which composable it belongs to). All 3 fixed inline. Independently verified: both source-app path-data transcriptions are byte-identical to the real files in the sibling `Shape`/`ReadBook` repos, and the new Tanakh tablet's SVG arc math was traced by hand and confirmed to produce a correct convex rounded-top shape, not a malformed one.

**Superseded by `/autoplan` Design review (post-spec, binding — see the plan file for the full fixes):** icon render size dropped from 24dp to **20dp** (crowded the status dot and text at 24dp), and `HabitStatusDot` gained a `Modifier.border(1.dp, MaterialTheme.colorScheme.background, CircleShape)` ring (Exercise's icon green is byte-identical to the dot's own "complete" green — the ring keeps them visually distinct instead of blurring together). Both fixes shipped in the implementation; this spec's original numbers below are historical.

## Success Criteria

- Exercise row shows a green hexagon with a white plus, matching Shape's real icon, at the far left before the dot.
- Reading row shows ReadBook's real open-book icon, at the far left.
- Tanakh row shows two stone tablets, visually distinct from Reading's book.
- Outer row layout (flush-left name, flush-right status text) unaffected — outer `SpaceBetween` Row still has exactly 2 top-level children.
- Full test suite green (no logic changes). On-device verification confirming all 3 icons render correctly and legibly at 20dp (see note above — reduced from this doc's original 24dp during Design review).

## Distribution Plan

Existing deployment path (`installDebug` via Gradle) — personal single-device app.

## Next Steps

Implementation plan: `docs/superpowers/plans/2026-07-24-dashboard-row-icons.md`, executed via `subagent-driven-development`.
