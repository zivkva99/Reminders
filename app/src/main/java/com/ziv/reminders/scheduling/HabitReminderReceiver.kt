package com.ziv.reminders.scheduling

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ziv.reminders.RemindersApp
import com.ziv.reminders.data.CounterHabitRepository
import com.ziv.reminders.data.EXERCISE_HABIT_INSTANCE_ID
import com.ziv.reminders.data.EvaluatorEscalationDao
import com.ziv.reminders.data.HabitInstanceDao
import com.ziv.reminders.data.HabitStats
import com.ziv.reminders.data.HabitStatus
import com.ziv.reminders.data.READING_HABIT_INSTANCE_ID
import com.ziv.reminders.data.ScheduleCursorRepository
import com.ziv.reminders.data.TANAKH_HABIT_INSTANCE_ID
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.data.WeeklySummary
import com.ziv.reminders.engine.HabitEngine
import com.ziv.reminders.notifications.HabitNotifications
import com.ziv.reminders.service.TimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Fired by AlarmManager for four purposes, dispatched by intent.action: the hourly reminder
 * (ACTION_REMINDER, including snooze-rescheduled re-firings — a no-op if today is already
 * completed or already escalated), Reading's notification Start button (ACTION_START_READING),
 * Reading's notification Snooze button (ACTION_SNOOZE_READING), and the weekly cross-habit
 * summary (ACTION_WEEKLY_SUMMARY). */
class HabitReminderReceiver : BroadcastReceiver() {

    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var habitInstanceDaoOverride: HabitInstanceDao? = null
    internal var habitEngineOverride: HabitEngine? = null
    internal var evaluatorEscalationDaoOverride: EvaluatorEscalationDao? = null
    internal var habitSchedulerOverride: HabitScheduler? = null
    internal var counterHabitRepositoryOverride: CounterHabitRepository? = null
    internal var timerHabitRepositoryOverride: TimerHabitRepository? = null
    internal var scheduleCursorRepositoryOverride: ScheduleCursorRepository? = null
    internal var scopeOverride: CoroutineScope? = null

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            HabitScheduler.ACTION_START_READING -> handleStartReading(context, intent)
            HabitScheduler.ACTION_SNOOZE_READING -> handleSnoozeReading(context, intent)
            HabitScheduler.ACTION_WEEKLY_SUMMARY -> handleWeeklySummary(context)
            else -> handleReminder(context, intent)
        }
    }

    private fun handleReminder(context: Context, intent: Intent) {
        val habitInstanceId = intent.getLongExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, -1L)
        if (habitInstanceId == -1L) return
        val pendingResult = goAsync()
        // Each override is checked via ?: BEFORE the RemindersApp cast, not a separate eager
        // `val container = (context.applicationContext as RemindersApp)...` line — that would
        // evaluate the cast unconditionally, throwing ClassCastException in every test even
        // when overrides are provided (Robolectric's application context isn't a RemindersApp
        // instance). Mirrors ReadBook's NudgeReceiver exactly, which relies on this same
        // short-circuiting for the same reason.
        val dao = habitInstanceDaoOverride
            ?: (context.applicationContext as RemindersApp).container.habitInstanceDao
        val engine = habitEngineOverride
            ?: (context.applicationContext as RemindersApp).container.habitEngine
        val escalationDao = evaluatorEscalationDaoOverride
            ?: (context.applicationContext as RemindersApp).container.evaluatorEscalationDao
        val scope = scopeOverride ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val instance = dao.getById(habitInstanceId) ?: return@launch
                val status = engine.todayStatus(instance, today())
                val completed = when (status) {
                    is HabitStatus.CounterStatus -> status.completed
                    is HabitStatus.TimerStatus -> status.completed
                    is HabitStatus.ScheduleCursorStatus -> status.completed
                }
                val alreadyEscalatedToday = escalationDao.getByDate(habitInstanceId, today().toString())?.escalated == true
                if (!completed && !alreadyEscalatedToday) {
                    HabitNotifications.createChannel(context, instance)
                    val manager = context.getSystemService(NotificationManager::class.java)
                    manager.notify(HabitNotifications.notificationId(instance), HabitNotifications.buildReminderNotification(context, instance))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleStartReading(context: Context, intent: Intent) {
        val habitInstanceId = intent.getLongExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, -1L)
        if (habitInstanceId == -1L) return
        // Converges with the in-app start path (DashboardViewModel.onToggleTimer) instead of
        // calling TimerHabitRepository directly — a direct repository call would leave a
        // running DB session with no foreground service and no ongoing ticking notification.
        ContextCompat.startForegroundService(
            context,
            Intent(context, TimerService::class.java)
                .setAction(TimerService.ACTION_START)
                .putExtra(TimerService.EXTRA_HABIT_INSTANCE_ID, habitInstanceId),
        )
    }

    private fun handleSnoozeReading(context: Context, intent: Intent) {
        val habitInstanceId = intent.getLongExtra(HabitScheduler.EXTRA_HABIT_INSTANCE_ID, -1L)
        if (habitInstanceId == -1L) return
        // Dismiss the current reminder — otherwise it sits stale for the next 15 minutes even
        // though the user just acted on it.
        context.getSystemService(NotificationManager::class.java).cancel(habitInstanceId.toInt())
        val scheduler = habitSchedulerOverride
            ?: (context.applicationContext as RemindersApp).container.habitScheduler
        scheduler.scheduleSnooze(habitInstanceId)
    }

    private fun handleWeeklySummary(context: Context) {
        val pendingResult = goAsync()
        val dao = habitInstanceDaoOverride
            ?: (context.applicationContext as RemindersApp).container.habitInstanceDao
        val counterRepo = counterHabitRepositoryOverride
            ?: (context.applicationContext as RemindersApp).container.counterHabitRepository
        val timerRepo = timerHabitRepositoryOverride
            ?: (context.applicationContext as RemindersApp).container.timerHabitRepository
        val cursorRepo = scheduleCursorRepositoryOverride
            ?: (context.applicationContext as RemindersApp).container.scheduleCursorRepository
        val scope = scopeOverride ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                val date = today()
                val exercise = dao.getById(EXERCISE_HABIT_INSTANCE_ID)
                val reading = dao.getById(READING_HABIT_INSTANCE_ID)
                val tanakh = dao.getById(TANAKH_HABIT_INSTANCE_ID)
                val exerciseDates = exercise?.let { HabitStats.parseDates(counterRepo.completedDates(it)) } ?: emptySet()
                val readingDates = reading?.let { HabitStats.parseDates(timerRepo.completedDates(it)) } ?: emptySet()
                val tanakhDates = tanakh?.let { HabitStats.parseDates(cursorRepo.completedDates(it)) } ?: emptySet()
                val summary = WeeklySummary.compute(exerciseDates, readingDates, tanakhDates, date)

                // Added per /autoplan CEO review: a fresh install (or any week with no habit
                // activity at all) would otherwise post "Exercise 0/7 · Reading 0/7 · Tanakh
                // 0/7" every Sunday — a useless nag with no suppression.
                if (summary.exerciseDays == 0 && summary.readingDays == 0 && summary.tanakhDays == 0) return@launch

                HabitNotifications.createWeeklySummaryChannel(context)
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.notify(HabitNotifications.WEEKLY_SUMMARY_NOTIFICATION_ID, HabitNotifications.buildWeeklySummaryNotification(context, summary))
            } finally {
                pendingResult.finish()
            }
        }
    }
}
