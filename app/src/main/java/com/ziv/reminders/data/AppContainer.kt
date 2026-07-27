package com.ziv.reminders.data

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.scheduling.HabitScheduler

/** Manual DI — no framework needed at this app's size. One instance, owned by RemindersApp. */
class AppContainer(context: Context) : DashboardDataSource, ExerciseDetailDataSource, ActivityDataSource {
    private val appContext = context.applicationContext

    private val db: AppDatabase by lazy {
        Room.databaseBuilder(appContext, AppDatabase::class.java, "reminders.db")
            // Never fallbackToDestructiveMigration() — see Global Constraints.
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8,
            )
            .build()
    }

    override val habitInstanceDao get() = db.habitInstanceDao()
    val counterDailyProgressDao get() = db.counterDailyProgressDao()
    val timerDailyProgressDao get() = db.timerDailyProgressDao()
    val scheduleCursorProgressDao get() = db.scheduleCursorProgressDao()
    val scheduleCursorDailyProgressDao get() = db.scheduleCursorDailyProgressDao()
    val evaluatorEscalationDao get() = db.evaluatorEscalationDao()
    val exerciseSubCounterProgressDao get() = db.exerciseSubCounterProgressDao()
    val readingSessionLogDao get() = db.readingSessionLogDao()
    val computedScheduleProgressDao get() = db.computedScheduleProgressDao()
    val computedScheduleWatchLogDao get() = db.computedScheduleWatchLogDao()
    val intervalDueProgressDao get() = db.intervalDueProgressDao()
    val intervalDueLogDao get() = db.intervalDueLogDao()
    override val counterHabitRepository: CounterHabitRepository by lazy { CounterHabitRepository(counterDailyProgressDao) }
    override val timerHabitRepository: TimerHabitRepository by lazy {
        TimerHabitRepository(
            timerDailyProgressDao, SystemClock, readingSessionLogDao,
            runInTransaction = { block -> db.withTransaction { block() } },
        )
    }
    override val subCounterRepository: SubCounterRepository by lazy { SubCounterRepository(exerciseSubCounterProgressDao) }

    /** Falls back to an empty schedule (never throws) if the bundled asset is ever missing or
     * malformed — mirrors ReadBook's own tanakhSchedule loader; a crash here must not take down
     * the whole app. */
    val tanakhSchedule: List<ScheduleEntry> by lazy {
        try {
            val csvText = appContext.assets.open("tanakh_schedule.csv").bufferedReader().use { it.readText() }
            parseTanakhSchedule(csvText)
        } catch (e: Exception) {
            emptyList()
        }
    }
    override val scheduleCursorRepository: ScheduleCursorRepository by lazy {
        ScheduleCursorRepository(scheduleCursorProgressDao, scheduleCursorDailyProgressDao, tanakhSchedule)
    }
    override val computedScheduleRepository: ComputedScheduleRepository by lazy {
        ComputedScheduleRepository(
            computedScheduleProgressDao, computedScheduleWatchLogDao,
            runInTransaction = { block -> db.withTransaction { block() } },
        )
    }

    override val habitEngine: HabitEngine by lazy {
        HabitEngine(counterHabitRepository, timerHabitRepository, scheduleCursorRepository, computedScheduleRepository)
    }
    val crossHabitEvaluator: CrossHabitEvaluator by lazy { CrossHabitEvaluator(habitInstanceDao, habitEngine, evaluatorEscalationDao) }
    val habitScheduler: HabitScheduler by lazy { HabitScheduler(appContext) }
}

interface DashboardDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val timerHabitRepository: TimerHabitRepository
    val scheduleCursorRepository: ScheduleCursorRepository
    val computedScheduleRepository: ComputedScheduleRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
}

/** Parallel to DashboardDataSource, not an extension of it — keeps DashboardDataSource
 * free of Exercise-only members. AppContainer implements both. Unchanged by this plan: Exercise
 * doesn't need computedScheduleRepository. */
interface ExerciseDetailDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
    val subCounterRepository: SubCounterRepository
}

/** Parallel to the other two, not an extension of either. Still unchanged by this plan even after
 * the Scope Revision (see the section below the CEO Phase 1 header) added a real stats screen for
 * this kind: `ComputedScheduleStatsScreen` (Task 6) deliberately reuses `DashboardDataSource`
 * instead of joining the combined Activity screen's `ActivityViewModel`/`ActivityDataSource` — it
 * already has everything the new screen needs (`habitInstanceDao`, `computedScheduleRepository`,
 * `habitEngine`), so extending this interface too would be redundant, not required. */
interface ActivityDataSource {
    val habitInstanceDao: HabitInstanceDao
    val counterHabitRepository: CounterHabitRepository
    val timerHabitRepository: TimerHabitRepository
    val scheduleCursorRepository: ScheduleCursorRepository
    val habitEngine: com.ziv.reminders.engine.HabitEngine
}
