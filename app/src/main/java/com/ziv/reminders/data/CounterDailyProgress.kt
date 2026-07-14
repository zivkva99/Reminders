package com.ziv.reminders.data

import androidx.room.Entity

@Entity(tableName = "counter_daily_progress", primaryKeys = ["habitInstanceId", "date"])
data class CounterDailyProgress(
    val habitInstanceId: Long,
    val date: String,
    val count: Int,
    val completed: Boolean,
)
