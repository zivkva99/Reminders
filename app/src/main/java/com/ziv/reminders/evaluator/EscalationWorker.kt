package com.ziv.reminders.evaluator

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ziv.reminders.RemindersApp
import com.ziv.reminders.data.CrossHabitEvaluator
import com.ziv.reminders.data.HabitInstanceDao
import com.ziv.reminders.data.READING_HABIT_INSTANCE_ID
import com.ziv.reminders.notifications.HabitNotifications
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs CrossHabitEvaluator, posts the escalated notification if it fires, then
 * unconditionally re-enqueues itself for whichever of {8am, 1pm} comes next — the same
 * self-chaining shape RolloverReceiver uses on AlarmManager, just on WorkManager. A
 * failed run must not break the chain, matching BootReceiver/RemindersApp's resilience.
 */
class EscalationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    internal var crossHabitEvaluatorOverride: CrossHabitEvaluator? = null
    internal var habitInstanceDaoOverride: HabitInstanceDao? = null
    internal var today: () -> LocalDate = { LocalDate.now() }
    internal var nowOverride: (() -> ZonedDateTime)? = null

    override suspend fun doWork(): Result {
        // Lazy — same reason as every other receiver/service in this app: Robolectric's test
        // Application isn't a RemindersApp instance, so the cast must only happen if no
        // override was injected.
        val container = (applicationContext as? RemindersApp)?.container
        val evaluator = crossHabitEvaluatorOverride ?: container?.crossHabitEvaluator
        val habitInstanceDao = habitInstanceDaoOverride ?: container?.habitInstanceDao

        try {
            if (evaluator != null && habitInstanceDao != null) {
                val escalated = evaluator.evaluate(today())
                if (escalated) {
                    val reading = habitInstanceDao.getById(READING_HABIT_INSTANCE_ID)
                    if (reading != null) {
                        HabitNotifications.createEscalatedChannel(applicationContext, reading.id)
                        val manager = applicationContext.getSystemService(NotificationManager::class.java)
                        manager.notify(
                            HabitNotifications.notificationId(reading),
                            HabitNotifications.buildEscalatedReminderNotification(applicationContext, reading),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Never let one bad run break the chain — the next line still reschedules.
        } finally {
            scheduleNext(applicationContext, nowOverride?.invoke() ?: ZonedDateTime.now())
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "com.ziv.reminders.evaluator.ESCALATION"

        /** Call from RemindersApp's startup self-heal — idempotent, never resets an
         * already-running chain. */
        fun ensureScheduled(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
            val request = OneTimeWorkRequestBuilder<EscalationWorker>()
                .setInitialDelay(millisUntilNextCheck(now), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        private fun scheduleNext(context: Context, now: ZonedDateTime) {
            val request = OneTimeWorkRequestBuilder<EscalationWorker>()
                .setInitialDelay(millisUntilNextCheck(now), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
