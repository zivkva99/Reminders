package com.ziv.reminders.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * kind is stored as a plain String (HabitKind.name), not a Room-mapped enum column — so a
 * future kind's migration only needs a data INSERT, never a schema change to this column.
 * counterGoal is nullable because non-Counter kinds (added by later plans) won't use it;
 * later plans add their own nullable per-kind config columns the same way.
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
)
