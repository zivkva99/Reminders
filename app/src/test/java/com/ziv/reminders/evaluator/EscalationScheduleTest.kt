package com.ziv.reminders.evaluator

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class EscalationScheduleTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun millisUntilNextCheck_beforeMorningCheck_returnsDelayToMorning() {
        val now = ZonedDateTime.of(2026, 7, 16, 6, 0, 0, 0, zone) // 6am
        val expected = Duration.ofHours(2).toMillis() // to 8am same day

        assertEquals(expected, millisUntilNextCheck(now))
    }

    @Test
    fun millisUntilNextCheck_betweenMorningAndAfternoon_returnsDelayToAfternoon() {
        val now = ZonedDateTime.of(2026, 7, 16, 10, 0, 0, 0, zone) // 10am
        val expected = Duration.ofHours(3).toMillis() // to 1pm same day

        assertEquals(expected, millisUntilNextCheck(now))
    }

    @Test
    fun millisUntilNextCheck_afterAfternoonCheck_returnsDelayToNextMorningsCheck() {
        val now = ZonedDateTime.of(2026, 7, 16, 15, 0, 0, 0, zone) // 3pm
        val expected = Duration.ofHours(17).toMillis() // to 8am the next day

        assertEquals(expected, millisUntilNextCheck(now))
    }

    @Test
    fun millisUntilNextCheck_exactlyAtACheckHour_rollsForwardToTheNextOne() {
        val now = ZonedDateTime.of(2026, 7, 16, 8, 0, 0, 0, zone) // exactly 8am
        val expected = Duration.ofHours(5).toMillis() // to 1pm same day, not 8am again

        assertEquals(expected, millisUntilNextCheck(now))
    }
}
