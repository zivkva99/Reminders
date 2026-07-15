package com.ziv.reminders.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.ziv.reminders.RemindersApp
import com.ziv.reminders.data.Clock
import com.ziv.reminders.data.HabitInstance
import com.ziv.reminders.data.HabitInstanceDao
import com.ziv.reminders.data.SystemClock
import com.ziv.reminders.data.TimerHabitRepository
import com.ziv.reminders.notifications.HabitNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Foreground service owning the currently-running timer session. Keyed by habitInstanceId (the
 * same generalization HabitScheduler/HabitReminderReceiver already use) so a future second Timer
 * instance needs zero new classes — but state (autoCompleteJob) is a single field, not a map:
 * this app is single-user with one realistic session at a time, matching ReadBook's own
 * ReadingTimerService, which this mirrors almost exactly.
 */
class TimerService : Service() {

    internal lateinit var habitInstanceDao: HabitInstanceDao
    internal lateinit var timerHabitRepository: TimerHabitRepository
    internal lateinit var scope: CoroutineScope
    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var clock: Clock = SystemClock

    private var autoCompleteJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // Robolectric's test application is a plain android.app.Application, not RemindersApp
        // (robolectric.properties — see HabitReminderReceiver's identical note): a safe cast lets
        // onCreate() run without crashing under Robolectric.buildService(...).create(), which
        // invokes onCreate() before TimerServiceTest gets a chance to inject its own overrides
        // directly onto these lateinit fields.
        (application as? RemindersApp)?.container?.let { container ->
            habitInstanceDao = container.habitInstanceDao
            timerHabitRepository = container.timerHabitRepository
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val habitInstanceId = intent?.getLongExtra(EXTRA_HABIT_INSTANCE_ID, -1L) ?: -1L
        if (habitInstanceId == -1L) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_START -> handleStart(habitInstanceId)
            ACTION_STOP -> handleStop(habitInstanceId)
        }
        return START_NOT_STICKY
    }

    private fun handleStart(habitInstanceId: Long) {
        HabitNotifications.createTimerChannel(this, habitInstanceId)
        // Placeholder — startForeground() must be called synchronously, before the suspending
        // instance lookup below. Corrected via updateNotification() within milliseconds, once
        // the real instance and remaining time are known (mirrors ReadBook's proven pattern).
        startForeground(
            HabitNotifications.timerNotificationId(habitInstanceId),
            HabitNotifications.buildTimerPlaceholderNotification(this, habitInstanceId),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        autoCompleteJob?.cancel()
        autoCompleteJob = scope.launch {
            val instance = habitInstanceDao.getById(habitInstanceId) ?: return@launch finish()
            val row = timerHabitRepository.start(instance, today())
            val startedAt = row.activeSessionStartedAt ?: return@launch finish() // already completed
            val targetRemaining = row.remainingSeconds
            // Recompute remaining from the wall clock every tick rather than decrementing by the
            // nominal step — delay() can resume much later than requested when backgrounded
            // (Doze/OEM throttling), and a cumulative decrement would understate elapsed time.
            while (true) {
                val elapsedSeconds = ((clock.nowMillis() - startedAt) / 1000L).toInt()
                val remaining = (targetRemaining - elapsedSeconds).coerceAtLeast(0)
                if (remaining <= 0) break
                updateNotification(instance, remaining)
                delay(minOf(NOTIFICATION_UPDATE_INTERVAL_SECONDS, remaining.toLong()) * 1000L)
            }
            timerHabitRepository.stop(instance, today())
            finish()
        }
    }

    private fun updateNotification(instance: HabitInstance, remainingSeconds: Int) {
        getSystemService(NotificationManager::class.java).notify(
            HabitNotifications.timerNotificationId(instance.id),
            HabitNotifications.buildTimerNotification(this, instance, remainingSeconds),
        )
    }

    private fun handleStop(habitInstanceId: Long) {
        autoCompleteJob?.cancel()
        scope.launch {
            val instance = habitInstanceDao.getById(habitInstanceId)
            if (instance != null) timerHabitRepository.stop(instance, today())
            finish()
        }
    }

    private fun finish() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.ziv.reminders.action.START_TIMER"
        const val ACTION_STOP = "com.ziv.reminders.action.STOP_TIMER"
        const val EXTRA_HABIT_INSTANCE_ID = "com.ziv.reminders.extra.HABIT_INSTANCE_ID"
        private const val NOTIFICATION_UPDATE_INTERVAL_SECONDS = 30L
    }
}
