package com.ziv.reminders.ui.dashboard

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class IntervalDueRowDisplayTest {

    @Test
    fun dueDateInPast_isOverdue_pluralText() {
        val display = deriveIntervalDueRowDisplay(
            dueDate = LocalDate.of(2026, 7, 24), today = LocalDate.of(2026, 7, 27),
        )
        assertEquals(IntervalDueUrgency.OVERDUE, display.urgency)
        assertEquals("3 days overdue", display.statusText)
    }

    @Test
    fun dueDateOneDayInPast_isOverdue_singularText() {
        val display = deriveIntervalDueRowDisplay(
            dueDate = LocalDate.of(2026, 7, 26), today = LocalDate.of(2026, 7, 27),
        )
        assertEquals("1 day overdue", display.statusText)
    }

    @Test
    fun dueDateIsToday_isDueToday() {
        val display = deriveIntervalDueRowDisplay(
            dueDate = LocalDate.of(2026, 7, 27), today = LocalDate.of(2026, 7, 27),
        )
        assertEquals(IntervalDueUrgency.DUE_TODAY, display.urgency)
        assertEquals("Due today", display.statusText)
    }

    @Test
    fun dueDateInFuture_isNotDue_pluralText() {
        val display = deriveIntervalDueRowDisplay(
            dueDate = LocalDate.of(2026, 7, 30), today = LocalDate.of(2026, 7, 27),
        )
        assertEquals(IntervalDueUrgency.NOT_DUE, display.urgency)
        assertEquals("In 3 days", display.statusText)
    }

    @Test
    fun dueDateOneDayInFuture_isNotDue_singularText() {
        val display = deriveIntervalDueRowDisplay(
            dueDate = LocalDate.of(2026, 7, 28), today = LocalDate.of(2026, 7, 27),
        )
        assertEquals("In 1 day", display.statusText)
    }
}
