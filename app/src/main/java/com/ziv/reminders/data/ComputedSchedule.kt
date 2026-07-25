package com.ziv.reminders.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure date-arithmetic release-schedule math — no Room/Android dependency, no persisted per-item
 * table. Given one known anchor (an item number and the date it released) and a fixed interval,
 * every other item's release date is fully computable. See the C++ Weekly design doc's
 * Recommended Approach for why this was chosen over a persisted, finite per-item schedule table
 * (Approach C, rejected): the series has no end date, so a finite table would silently run dry
 * without periodic reseeding, forever.
 */
fun computeReleaseDate(anchorItemNumber: Int, anchorDate: LocalDate, intervalDays: Int, itemNumber: Int): LocalDate =
    anchorDate.plusDays((itemNumber - anchorItemNumber).toLong() * intervalDays)

/**
 * dueCount counts every item released on or before today, up to and including nextItemNumber's
 * own release, treating "released today" as 1 rather than 0 — so isDueToday (dueCount == 1) and
 * "behind by more than one release" (dueCount > 1) are cleanly distinguishable. This is
 * deliberately NOT the same shape as ScheduleEntryStatus's dueCount (Tanakh's Behind case, which
 * never includes "due today" — OnSchedule is its own separate branch): the two kinds' dueCount
 * fields answer differently-scoped questions, so callers (e.g. ComputedScheduleHabitRow's dot
 * color) must not assume `dueCount > 0` alone means "behind" the way Tanakh's does — here that
 * test must be `dueCount > 1`.
 */
fun deriveComputedScheduleStatus(
    nextItemNumber: Int,
    anchorItemNumber: Int,
    anchorDate: LocalDate,
    intervalDays: Int,
    today: LocalDate,
): HabitStatus.ComputedScheduleStatus {
    val releaseDate = computeReleaseDate(anchorItemNumber, anchorDate, intervalDays, nextItemNumber)
    val dueCount = if (!releaseDate.isAfter(today)) {
        (ChronoUnit.DAYS.between(releaseDate, today) / intervalDays) + 1
    } else {
        0L
    }
    return HabitStatus.ComputedScheduleStatus(
        nextItemNumber = nextItemNumber,
        dueCount = dueCount.toInt(),
        isDueToday = dueCount == 1L,
    )
}
