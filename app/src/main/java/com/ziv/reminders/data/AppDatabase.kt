package com.ziv.reminders.data

import androidx.room.Database
import androidx.room.RoomDatabase

// Schema v1 — nothing to migrate from yet. Starting with whichever later plan adds Timer's or
// Schedule-cursor's config columns (v1->v2), every change ships a real Migration object;
// never fallbackToDestructiveMigration() — see Global Constraints.
@Database(
    entities = [HabitInstance::class, CounterDailyProgress::class],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitInstanceDao(): HabitInstanceDao
    abstract fun counterDailyProgressDao(): CounterDailyProgressDao
}
