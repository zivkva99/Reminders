package com.ziv.reminders.ui.activity

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeatmapDayTest {

    private val today = LocalDate.of(2026, 7, 19)

    @Test
    fun buildHeatmapDays_todayInDates_todayEntryIsHit() {
        val days = buildHeatmapDays(setOf(today), today)
        val todayEntry = days.first { it.date == today }
        assertEquals(HeatmapDayStatus.HIT, todayEntry.status)
    }

    @Test
    fun buildHeatmapDays_todayNotInDates_todayEntryIsPending() {
        val days = buildHeatmapDays(emptySet(), today)
        val todayEntry = days.first { it.date == today }
        assertEquals(HeatmapDayStatus.PENDING, todayEntry.status)
    }

    @Test
    fun buildHeatmapDays_pastDayNotInDates_isMiss() {
        val yesterday = today.minusDays(1)
        val days = buildHeatmapDays(emptySet(), today)
        val entry = days.first { it.date == yesterday }
        assertEquals(HeatmapDayStatus.MISS, entry.status)
    }

    @Test
    fun buildHeatmapDays_pastDayInDates_isHit() {
        val yesterday = today.minusDays(1)
        val days = buildHeatmapDays(setOf(yesterday), today)
        val entry = days.first { it.date == yesterday }
        assertEquals(HeatmapDayStatus.HIT, entry.status)
    }

    @Test
    fun buildHeatmapDays_firstEntry_isSundayAligned() {
        // Every 7-day chunk boundary aligns to Sunday by construction, regardless of what
        // weekday `today` falls on — the alignment math shifts windowStart backward to the
        // nearest Sunday before chunking.
        val days = buildHeatmapDays(emptySet(), today)
        assertEquals(DayOfWeek.SUNDAY, days.first().date.dayOfWeek)
    }

    @Test
    fun buildHeatmapDays_todayAppearsWithinFirstWeek_nearTheTop() {
        // Weeks are reversed (most recent week first) so today is always near the top of the
        // list with no scrolling — mirrors the original composable's documented intent.
        val days = buildHeatmapDays(emptySet(), today)
        assertTrue(days.take(7).any { it.date == today })
    }

    @Test
    fun buildHeatmapDays_noDuplicateDates() {
        val days = buildHeatmapDays(emptySet(), today)
        assertEquals(days.size, days.map { it.date }.toSet().size)
    }
}
