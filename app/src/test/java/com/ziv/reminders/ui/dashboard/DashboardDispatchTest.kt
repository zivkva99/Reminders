package com.ziv.reminders.ui.dashboard

import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.READING_HABIT_INSTANCE_ID
import com.ziv.reminders.data.TANAKH_HABIT_INSTANCE_ID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardDispatchTest {

    @Test
    fun hasExerciseDetailMenu_exerciseInstanceId_isTrue() {
        assertTrue(hasExerciseDetailMenu(EXERCISE_HABIT_INSTANCE_ID))
    }

    @Test
    fun hasExerciseDetailMenu_otherInstanceIds_isFalse() {
        // Regression guard: a hypothetical future second COUNTER-kind habit must not be
        // silently offered the Exercise long-press menu just because it shares
        // HabitKind.COUNTER — dispatch is by instance ID, not by kind.
        assertFalse(hasExerciseDetailMenu(READING_HABIT_INSTANCE_ID))
        assertFalse(hasExerciseDetailMenu(TANAKH_HABIT_INSTANCE_ID))
        assertFalse(hasExerciseDetailMenu(999L))
    }
}
