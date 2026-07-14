package com.ziv.reminders.scheduling

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ziv.reminders.data.AppDatabase
import com.ziv.reminders.data.HabitInstance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    @Before
    fun clearAnyPreExistingAlarms() {
        shadowOf(alarmManager).getScheduledAlarms().forEach { it.operation?.let(alarmManager::cancel) }
    }

    @Test
    fun onReceive_bootCompleted_schedulesTodaysRemindersForEveryInstance_andRollover() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCoroutineContext(StandardTestDispatcher(testScheduler))
            .build()
        db.habitInstanceDao().insertIfAbsent(
            HabitInstance(1L, "COUNTER", "Exercise", 0b1111111, "t", "b", 5)
        )

        val receiver = BootReceiver()
        receiver.today = { LocalDate.now().plusDays(1) }
        receiver.habitInstanceDaoOverride = db.habitInstanceDao()
        receiver.habitSchedulerOverride = HabitScheduler(context)
        receiver.scopeOverride = CoroutineScope(StandardTestDispatcher(testScheduler))

        // BootReceiver calls goAsync(), which needs the real broadcast dispatch mechanism just
        // like HabitReminderReceiver/RolloverReceiver — direct onReceive() would NPE on the
        // PendingResult. android:exported="false" in the manifest is fine for real devices
        // (the system still delivers BOOT_COMPLETED to non-exported receivers registered in the
        // manifest); it only affects registerReceiver's RECEIVER_NOT_EXPORTED flag choice here.
        context.registerReceiver(receiver, android.content.IntentFilter(Intent.ACTION_BOOT_COMPLETED), Context.RECEIVER_NOT_EXPORTED)
        context.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        testScheduler.advanceUntilIdle()

        assertEquals(6, shadowOf(alarmManager).getScheduledAlarms().size)

        db.close()
    }
}
