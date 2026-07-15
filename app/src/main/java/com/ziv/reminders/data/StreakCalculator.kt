package com.ziv.reminders.data

import java.time.LocalDate

/**
 * Combines two rules neither kind's own inline streak logic covers alone: CounterHabitRepository
 * anchors at yesterday when today isn't done yet (the day isn't over), but its every-day
 * enabledDaysMask means it never needed to skip disabled days. The Reading habit runs Sun-Thu
 * only (mirroring ReadBook's actual default), so a naive consecutive-day walk would wrongly
 * break its streak every Friday/Saturday — this calculator skips disabled days entirely (they
 * neither extend nor break the streak), mirroring ReadBook's real StreakCalculator. Deliberately
 * not applied to Counter's existing (already shipped, on-device-verified) currentStreak — its
 * enabledDaysMask is always all-days, so the two are behaviorally identical for it anyway.
 */
object StreakCalculator {

    private const val MAX_LOOKBACK_DAYS = 3650L // ~10 years — safety bound, not a real limit

    fun calculate(completedDates: Set<LocalDate>, enabledDaysMask: Int, today: LocalDate): Int {
        if (enabledDaysMask == 0) return 0

        val anchor = if (today in completedDates) today else today.minusDays(1)
        var streak = 0
        var date = anchor
        var daysChecked = 0L
        while (daysChecked < MAX_LOOKBACK_DAYS) {
            if (isEnabledDay(date, enabledDaysMask)) {
                if (date in completedDates) streak++ else break
            }
            date = date.minusDays(1)
            daysChecked++
        }
        return streak
    }
}
