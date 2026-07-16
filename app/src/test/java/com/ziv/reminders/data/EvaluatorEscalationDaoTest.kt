package com.ziv.reminders.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EvaluatorEscalationDaoTest {

    private fun newDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @Test
    fun getByDate_noRow_returnsNull() = runTest {
        val db = newDb()
        assertNull(db.evaluatorEscalationDao().getByDate(2L, "2026-07-16"))
        db.close()
    }

    @Test
    fun upsert_thenGetByDate_returnsTheRow() = runTest {
        val db = newDb()
        val escalation = EvaluatorEscalation(habitInstanceId = 2L, date = "2026-07-16", escalated = true)
        db.evaluatorEscalationDao().upsert(escalation)

        assertEquals(escalation, db.evaluatorEscalationDao().getByDate(2L, "2026-07-16"))
        db.close()
    }

    @Test
    fun upsert_sameKey_replacesInsteadOfDuplicating() = runTest {
        val db = newDb()
        db.evaluatorEscalationDao().upsert(EvaluatorEscalation(2L, "2026-07-16", escalated = false))
        db.evaluatorEscalationDao().upsert(EvaluatorEscalation(2L, "2026-07-16", escalated = true))

        assertEquals(true, db.evaluatorEscalationDao().getByDate(2L, "2026-07-16")?.escalated)
        db.close()
    }

    @Test
    fun getByDate_differentInstanceOrDate_returnsNull() = runTest {
        val db = newDb()
        db.evaluatorEscalationDao().upsert(EvaluatorEscalation(2L, "2026-07-16", escalated = true))

        assertNull(db.evaluatorEscalationDao().getByDate(3L, "2026-07-16"))
        assertNull(db.evaluatorEscalationDao().getByDate(2L, "2026-07-17"))
        db.close()
    }
}
