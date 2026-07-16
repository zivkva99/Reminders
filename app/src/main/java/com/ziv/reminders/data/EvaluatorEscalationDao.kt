package com.ziv.reminders.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface EvaluatorEscalationDao {
    @Query("SELECT * FROM evaluator_escalation WHERE habitInstanceId = :habitInstanceId AND date = :date")
    suspend fun getByDate(habitInstanceId: Long, date: String): EvaluatorEscalation?

    @Upsert
    suspend fun upsert(escalation: EvaluatorEscalation)
}
