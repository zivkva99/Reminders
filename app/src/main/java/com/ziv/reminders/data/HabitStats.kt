package com.ziv.reminders.data

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Pure, Android-free port of Shape's TrainingStats (Shape/app/src/main/java/com/shape/
 * data/TrainingStats.kt), providing the month/best-month/"new record" detection Reminders
 * has none of today (only currentStreak exists, in CounterHabitRepository).
 *
 * parseDates takes a List<String> — one row per completed day, matching
 * CounterDailyProgressDao.getCompletedDates's return shape — rather than Shape's single
 * comma-joined CSV string, since Reminders stores one Room row per completed day.
 */
object HabitStats {

    fun parseDates(raw: List<String>, onMalformedEntry: (String) -> Unit = {}): Set<LocalDate> =
        raw.mapNotNull { entry ->
            try {
                LocalDate.parse(entry.trim())
            } catch (e: DateTimeParseException) {
                onMalformedEntry(entry)
                null
            }
        }.toSet()

    fun recordSuffix(isRecord: Boolean, label: String): String =
        if (isRecord) " — new $label!" else ""

    fun monthCount(dates: Set<LocalDate>, today: LocalDate): Int =
        dates.count { it.year == today.year && it.month == today.month }

    // If today hasn't been hit yet, the day isn't over — the streak counts through
    // yesterday and isn't broken until midnight passes without today being hit.
    fun currentStreak(dates: Set<LocalDate>, today: LocalDate): Int =
        currentStreakDates(dates, today).size

    private fun currentStreakDates(dates: Set<LocalDate>, today: LocalDate): Set<LocalDate> {
        val anchor = if (today in dates) today else today.minusDays(1)
        val result = mutableSetOf<LocalDate>()
        var cursor = anchor
        while (cursor in dates) {
            result += cursor
            cursor = cursor.minusDays(1)
        }
        return result
    }

    fun longestStreak(dates: Set<LocalDate>): Int {
        var longest = 0
        var current = 0
        var previous: LocalDate? = null
        for (date in dates.sorted()) {
            current = if (previous != null && date == previous.plusDays(1)) current + 1 else 1
            longest = maxOf(longest, current)
            previous = date
        }
        return longest
    }

    fun bestMonth(dates: Set<LocalDate>): Int =
        dates.groupingBy { it.year to it.month }.eachCount().values.maxOrNull() ?: 0

    // "New record" (strictly greater) must be judged against a baseline that EXCLUDES the
    // currently in-progress streak/month — comparing against a baseline that includes the
    // in-progress run is self-referential and can never be true on the exact day the
    // record is actually set.
    fun isNewStreakRecord(dates: Set<LocalDate>, today: LocalDate): Boolean {
        val inProgress = currentStreakDates(dates, today)
        val baseline = longestStreak(dates - inProgress)
        return currentStreak(dates, today) > baseline
    }

    fun isNewMonthRecord(dates: Set<LocalDate>, today: LocalDate): Boolean {
        val currentMonthDates = dates.filter { it.year == today.year && it.month == today.month }.toSet()
        val baseline = bestMonth(dates - currentMonthDates)
        return monthCount(dates, today) > baseline
    }
}
