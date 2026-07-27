package com.ziv.reminders.ui.dashboard

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal enum class IntervalDueUrgency { OVERDUE, DUE_TODAY, NOT_DUE }

internal data class IntervalDueRowDisplay(val urgency: IntervalDueUrgency, val statusText: String)

/** Pure function — no Android/Compose dependency. daysOverdue > 0 means dueDate is in the past
 * (overdue by that many days); == 0 means due today; < 0 means dueDate is still in the future. */
internal fun deriveIntervalDueRowDisplay(dueDate: LocalDate, today: LocalDate): IntervalDueRowDisplay {
    val daysOverdue = ChronoUnit.DAYS.between(dueDate, today).toInt()
    return when {
        daysOverdue > 0 -> IntervalDueRowDisplay(
            IntervalDueUrgency.OVERDUE,
            "$daysOverdue day${if (daysOverdue == 1) "" else "s"} overdue",
        )
        daysOverdue == 0 -> IntervalDueRowDisplay(IntervalDueUrgency.DUE_TODAY, "Due today")
        else -> IntervalDueRowDisplay(
            IntervalDueUrgency.NOT_DUE,
            "In ${-daysOverdue} day${if (daysOverdue == -1) "" else "s"}",
        )
    }
}
