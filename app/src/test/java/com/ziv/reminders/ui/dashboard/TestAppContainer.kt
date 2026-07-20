package com.ziv.reminders.ui.dashboard

import androidx.room.withTransaction
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.data.ScheduleEntry
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.engine.HabitEngine

class TestAppContainer(db: AppDatabase, schedule: List<ScheduleEntry> = emptyList()) : DashboardDataSource {
    override val habitInstanceDao = db.habitInstanceDao()
    override val counterHabitRepository = CounterHabitRepository(db.counterDailyProgressDao())
    override val timerHabitRepository = TimerHabitRepository(
        db.timerDailyProgressDao(), SystemClock, db.readingSessionLogDao(),
        runInTransaction = { block -> db.withTransaction { block() } },
    )
    override val scheduleCursorRepository = ScheduleCursorRepository(db.scheduleCursorProgressDao(), db.scheduleCursorDailyProgressDao(), schedule)
    override val habitEngine = HabitEngine(counterHabitRepository, timerHabitRepository, scheduleCursorRepository)
}
