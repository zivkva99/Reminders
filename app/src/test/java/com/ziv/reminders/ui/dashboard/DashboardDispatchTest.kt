package com.ziv.reminders.ui.dashboard

import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.READING_HABIT_INSTANCE_ID
import com.ziv.reminders.data.TANAKH_HABIT_INSTANCE_ID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardDispatchTest {

    @Test
    fun shouldNavigateToExerciseDetail_exerciseInstanceId_isTrue() {
        assertTrue(shouldNavigateToExerciseDetail(EXERCISE_HABIT_INSTANCE_ID))
    }

    @Test
    fun shouldNavigateToExerciseDetail_otherInstanceIds_isFalse() {
        // Regression guard: a hypothetical future second COUNTER-kind habit must not be
        // silently redirected into the Exercise navigation flow just because it shares
        // HabitKind.COUNTER — dispatch is by instance ID, not by kind.
        assertFalse(shouldNavigateToExerciseDetail(READING_HABIT_INSTANCE_ID))
        assertFalse(shouldNavigateToExerciseDetail(TANAKH_HABIT_INSTANCE_ID))
        assertFalse(shouldNavigateToExerciseDetail(999L))
    }
}
