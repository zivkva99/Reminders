package com.ziv.reminders.ui.exercise

import androidx.compose.ui.graphics.Color

/**
 * Small, targeted semantic colors only — NOT a full theme override. Reminders uses
 * Material You dynamic color everywhere else (RemindersTheme.kt); these values carry
 * real status meaning (goal reached, heatmap hit/pending) that a per-device dynamic
 * palette can't express, so they're layered on top of the dynamic MaterialTheme only
 * within the Exercise screens, mirroring Shape's own GoalGreen/Heatmap* constants without
 * importing Shape's full lightColorScheme()/darkColorScheme() override.
 *
 * The heatmap "miss" (no-data) cell is intentionally NOT one of these fixed constants —
 * it carries no special meaning (it's an absence, not a status), so it uses
 * MaterialTheme.colorScheme.surfaceVariant at the call site instead, which already
 * adapts to light/dark automatically. A fixed hex here would render as a near-white
 * tile in dark mode.
 */
val GoalGreen = Color(0xFF2E7D32)
val HeatmapHit = GoalGreen
val HeatmapPending = Color(0xFFFFD54F)

// True orange (distinct from HeatmapPending's amber above) — the dashboard's Tanakh row uses
// this for "today's chapter is due and unread, no backlog" (see DashboardScreen.kt's
// HabitStatusDot wiring).
val StatusOrange = Color(0xFFF57C00)
