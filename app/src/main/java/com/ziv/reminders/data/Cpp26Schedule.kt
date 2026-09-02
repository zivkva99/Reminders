package com.ziv.reminders.data

import java.time.LocalDate

/**
 * The fixed, hand-picked chapter order for the C++26 habit (confirmed by the user 2026-09-02) —
 * not parsed from a bundled CSV asset like Tanakh's schedule, since this is a short, one-time
 * list the user specified directly rather than an external data feed. Order matters and is
 * exactly as given, not re-sorted numerically — note 485 comes before 484.
 */
val CPP26_CHAPTERS: List<Int> = listOf(
    414, 429, 459, 461, 463, 465, 471, 485, 484, 490,
    501, 503, 504, 505, 514, 516, 540, 542, 548,
)

/**
 * One entry per calendar day, 7 days a week — no weekend skip, unlike Tanakh's Sun-Thu cadence
 * (see HabitSeeding's enabledDaysMask for this instance). [startDate] is the due date of the
 * first chapter (414); every following chapter is due exactly one day after the previous one,
 * consecutively, with no gaps — matches the mechanics ScheduleCursorRepository/
 * deriveScheduleEntryStatus already implement generically for Tanakh (catch-up, Behind/OnSchedule/
 * Waiting/Finished), reused as-is here via a second per-instance schedule list.
 */
fun buildCpp26Schedule(startDate: LocalDate): List<ScheduleEntry> =
    CPP26_CHAPTERS.mapIndexed { index, chapter ->
        ScheduleEntry(book = "Ch.", chapterHeb = chapter.toString(), date = startDate.plusDays(index.toLong()))
    }
