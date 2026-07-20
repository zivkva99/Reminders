package com.ziv.reminders.ui.activity

import java.time.LocalDate

enum class HeatmapDayStatus { HIT, PENDING, MISS }

data class HeatmapDay(val date: LocalDate, val status: HeatmapDayStatus)

/**
 * Pure computation behind HeatmapGrid's rendering, extracted from the original private
 * HeatmapGrid function in ExerciseStatsScreen.kt with zero logic changes — this is the real
 * safety net for sharing the composable across habit kinds, since no Compose UI test coverage
 * exists for this app's screens (see Global Constraints).
 */
fun buildHeatmapDays(dates: Set<LocalDate>, today: LocalDate): List<HeatmapDay> {
    // Aligned to a fixed 7-column, Sunday-start grid so rows correspond to real calendar
    // weeks regardless of screen width — mirrors Shape's own HeatmapGrid exactly.
    val windowStart = today.minusMonths(12)
    val daysSinceSunday = windowStart.dayOfWeek.value % 7
    val alignedStart = windowStart.minusDays(daysSinceSunday.toLong())

    // Weeks reversed (most recent week first) so today is always near the top with no
    // scrolling — mirrors Shape's own HeatmapGrid exactly.
    val orderedDays = generateSequence(alignedStart) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .toList()
        .chunked(7)
        .reversed()
        .flatten()

    return orderedDays.map { day ->
        val status = when {
            day == today && day !in dates -> HeatmapDayStatus.PENDING
            day in dates -> HeatmapDayStatus.HIT
            else -> HeatmapDayStatus.MISS
        }
        HeatmapDay(day, status)
    }
}
