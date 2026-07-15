package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * kind is stored as a plain String (HabitKind.name), not a Room-mapped enum column — so a
 * future kind's migration only needs a data INSERT, never a schema change to this column.
 * counterGoal/timerTargetSeconds are nullable per-kind config columns; each new kind adds its
 * own nullable trailing column the same way (a defaulted trailing param, so every existing
 * positional HabitInstance(...) call site keeps compiling unmodified).
 */
@Entity(tableName = "habit_instance")
data class HabitInstance(
    @PrimaryKey val id: Long,
    val kind: String,
    val name: String,
    val enabledDaysMask: Int,
    val notificationTitle: String,
    val notificationBody: String,
    val counterGoal: Int?,
    val timerTargetSeconds: Int? = null,
)
