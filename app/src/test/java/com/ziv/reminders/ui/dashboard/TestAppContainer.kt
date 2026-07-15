package com.ziv.reminders.ui.dashboard

import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.DashboardDataSource
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.engine.HabitEngine

class TestAppContainer(db: AppDatabase) : DashboardDataSource {
    override val habitInstanceDao = db.habitInstanceDao()
    override val counterHabitRepository = CounterHabitRepository(db.counterDailyProgressDao())
    private val timerHabitRepository = TimerHabitRepository(db.timerDailyProgressDao(), SystemClock)
    override val habitEngine = HabitEngine(counterHabitRepository, timerHabitRepository)
}
