package com.ziv.reminders.ui.dashboard

import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.isEnabledDay
import java.time.LocalDate

// Ordinal order IS the sort order (red first, then orange, then green) — declared in this order
// so `.sortedBy { }` needs no separate priority mapping.
internal enum class RowUrgency { RED, ORANGE, GREEN }

/**
 * Pure function used only to order the dashboard's rows (red first, orange, green last, stable
 * within each color) — mirrors each row Composable's own HabitStatusDot color `when` branches in
 * DashboardScreen.kt exactly. Kept here as a separate, unit-tested function rather than reading
 * the color back out of the UI layer; if a row's dot logic ever changes, this must be updated to
 * match or the sort order will silently drift out of sync with what the dots show.
 */
internal fun dashboardRowUrgency(instanceId: Long, status: HabitStatus, today: LocalDate, enabledDaysMask: Int): RowUrgency = when (status) {
    is HabitStatus.CounterStatus -> when {
        status.completed -> RowUrgency.GREEN
        // LegoKitHabitRow dims to the neutral outline color (not red) on a day it isn't
        // enabled — that's "not today", not "needs attention", so it sorts with the
        // non-urgent rows rather than the overdue ones.
        isLegoKitRow(instanceId) && !isEnabledDay(today, enabledDaysMask) -> RowUrgency.GREEN
        else -> RowUrgency.RED
    }
    is HabitStatus.TimerStatus -> if (status.completed) RowUrgency.GREEN else RowUrgency.RED
    is HabitStatus.ScheduleCursorStatus -> when {
        status.dueCount > 0 && status.entriesReadToday >= 2 -> RowUrgency.ORANGE
        status.dueCount > 0 -> RowUrgency.RED
        status.isDueToday -> RowUrgency.ORANGE
        else -> RowUrgency.GREEN
    }
    is HabitStatus.ComputedScheduleStatus -> when {
        status.dueCount > 1 -> RowUrgency.RED
        status.isDueToday -> RowUrgency.ORANGE
        else -> RowUrgency.GREEN
    }
    is HabitStatus.IntervalDueStatus -> when (deriveIntervalDueRowDisplay(status.dueDate, today).urgency) {
        IntervalDueUrgency.OVERDUE -> RowUrgency.RED
        IntervalDueUrgency.DUE_TODAY -> RowUrgency.ORANGE
        IntervalDueUrgency.NOT_DUE -> RowUrgency.GREEN
    }
}
