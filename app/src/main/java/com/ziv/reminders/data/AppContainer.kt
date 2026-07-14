package com.ziv.reminders.data

import android.content.Context
import androidx.room.Room
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.scheduling.HabitScheduler

/** Manual DI — no framework needed at this app's size. One instance, owned by RemindersApp. */
class AppContainer(context: Context) : DashboardDataSource {
    private val appContext = context.applicationContext

    private val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "reminders.db").build()
    }

    override val habitInstanceDao get() = db.habitInstanceDao()
    val counterDailyProgressDao get() = db.counterDailyProgressDao()
    override val counterHabitRepository: CounterHabitRepository by lazy { CounterHabitRepository(counterDailyProgressDao) }
    override val habitEngine: HabitEngine by lazy { HabitEngine(counterHabitRepository) }
    val habitScheduler: HabitScheduler by lazy { HabitScheduler(appContext) }
}

interface DashboardDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
}
