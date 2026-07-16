package com.ziv.reminders.evaluator

import java.time.Duration
import java.time.ZonedDateTime

/**
 * Pure calculation of how long to delay until the next of {8am, 1pm} (or whichever
 * check hours are passed) strictly after [now] — never returns a zero/negative delay,
 * even if [now] lands exactly on a check hour. No Android dependency; EscalationWorker
 * (evaluator/EscalationWorker.kt) uses this to self-reschedule after every run.
 */
fun millisUntilNextCheck(now: ZonedDateTime, checkHours: List<Int> = listOf(8, 13)): Long {
    val today = now.toLocalDate()
    val candidates = checkHours.map { hour -> today.atTime(hour, 0).atZone(now.zone) } +
        checkHours.map { hour -> today.plusDays(1).atTime(hour, 0).atZone(now.zone) }
    val next = candidates.first { it.isAfter(now) }
    return Duration.between(now, next).toMillis()
}
