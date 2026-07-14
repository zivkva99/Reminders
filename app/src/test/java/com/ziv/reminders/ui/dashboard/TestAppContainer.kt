package com.ziv.reminders.ui.dashboard

import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.engine.HabitEngine

class TestAppContainer(db: AppDatabase) : DashboardDataSource {
    override val habitInstanceDao = db.habitInstanceDao()
    override val counterHabitRepository = CounterHabitRepository(db.counterDailyProgressDao())
    override val habitEngine = HabitEngine(counterHabitRepository)
}
