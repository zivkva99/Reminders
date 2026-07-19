package com.ziv.reminders.ui.exercise

import androidx.compose.ui.graphics.Color

/**
 * Small, targeted semantic colors only — NOT a full theme override. Reminders uses
 * Material You dynamic color everywhere else (RemindersTheme.kt); these 4 values carry
 * real status meaning (goal reached, heatmap hit/miss/pending) that a per-device dynamic
 * palette can't express, so they're layered on top of the dynamic MaterialTheme only
 * within the Exercise screens, mirroring Shape's own GoalGreen/Heatmap* constants without
 * importing Shape's full lightColorScheme()/darkColorScheme() override.
 */
val GoalGreen = Color(0xFF2E7D32)
val HeatmapHit = GoalGreen
val HeatmapMiss = Color(0xFFE0E0E0)
val HeatmapPending = Color(0xFFFFD54F)
