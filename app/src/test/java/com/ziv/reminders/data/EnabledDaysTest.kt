package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnabledDaysTest {
    @Test
    fun isEnabledDay_sundayWithFullMask_isTrue() {
        val sunday = LocalDate.of(2026, 7, 12) // a known Sunday
        assertTrue(isEnabledDay(sunday, enabledDaysMask = 0b1111111))
    }

    @Test
    fun isEnabledDay_saturdayWithSunThuMask_isFalse() {
        val saturday = LocalDate.of(2026, 7, 18) // a known Saturday
        assertFalse(isEnabledDay(saturday, enabledDaysMask = 0b0011111)) // Sun-Thu
    }

    @Test
    fun isEnabledDay_thursdayWithSunThuMask_isTrue() {
        val thursday = LocalDate.of(2026, 7, 16) // a known Thursday
        assertTrue(isEnabledDay(thursday, enabledDaysMask = 0b0011111)) // Sun-Thu
    }
}
