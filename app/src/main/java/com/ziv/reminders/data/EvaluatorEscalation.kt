package com.ziv.reminders.data

import androidx.room.Entity

/**
 * A per-day flag: was this habit instance's reminder escalated by CrossHabitEvaluator
 * today? Mirrors the shape of the other daily-progress tables. HabitReminderReceiver
 * checks this before posting its normal reminder — an already-escalated notification for
 * today must not be silently downgraded back to plain wording an hour later.
 */
@Entity(tableName = "evaluator_escalation", primaryKeys = ["habitInstanceId", "date"])
data class EvaluatorEscalation(
    val habitInstanceId: Long,
    val date: String,
    val escalated: Boolean,
)
