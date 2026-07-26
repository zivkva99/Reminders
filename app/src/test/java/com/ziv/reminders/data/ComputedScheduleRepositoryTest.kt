package com.ziv.reminders.data

import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeComputedScheduleProgressDao : ComputedScheduleProgressDao {
    val rows = mutableMapOf<Long, ComputedScheduleProgress>()
    override suspend fun getByInstance(habitInstanceId: Long) = rows[habitInstanceId]
    override suspend fun insertIfAbsent(progress: ComputedScheduleProgress) {
        rows.putIfAbsent(progress.habitInstanceId, progress)
    }
    override suspend fun upsert(progress: ComputedScheduleProgress) { rows[progress.habitInstanceId] = progress }
}

// Added per the Scope Revision (see the section below the CEO Phase 1 header) — this fake didn't
// exist in the plan's original draft of this task, since currentStreak() had nothing to read.
private class FakeComputedScheduleWatchLogDao : ComputedScheduleWatchLogDao {
    val rows = mutableListOf<ComputedScheduleWatchLog>()
    override suspend fun insert(entry: ComputedScheduleWatchLog): Long {
        // Simulates Room's autoGenerate primary key assignment: the returned row id is also
        // reflected onto the stored row's own id field, matching real @Insert(autoGenerate=true)
        // behavior (the caller-passed entry's id=0 placeholder is never the persisted id).
        val stored = entry.copy(id = rows.size + 1L)
        rows += stored
        return stored.id
    }
    override suspend fun getWatchedDates(habitInstanceId: Long): List<String> =
        rows.filter { it.habitInstanceId == habitInstanceId }.map { it.date }.distinct()
}

class ComputedScheduleRepositoryTest {

    // Episode 543 releases 2026-07-21 given this anchor/interval — arbitrary test values, not
    // episode 542's real release date.
    private val instance = HabitInstance(
        id = 4L, kind = "COMPUTED_SCHEDULE", name = "C++ Weekly", enabledDaysMask = 0b1111111,
        notificationTitle = "t", notificationBody = "b", counterGoal = null,
        anchorItemNumber = 542, anchorDate = "2026-07-14", intervalDays = 7,
    )

    @Test
    fun todayStatus_noProgressRow_fallsBackToAnchorItemNumberPlusOne() = runTest {
        val repo = ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao())

        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 7, 20))

        assertEquals(543, status.nextItemNumber)
        assertEquals(0, status.dueCount)
    }

    @Test
    fun todayStatus_reflectsThePersistedProgressRow() = runTest {
        val progressDao = FakeComputedScheduleProgressDao()
        progressDao.rows[4L] = ComputedScheduleProgress(4L, nextItemNumber = 550)
        val repo = ComputedScheduleRepository(progressDao, FakeComputedScheduleWatchLogDao())

        // item 550's release date: 2026-07-14 + (550-542)*7 = 2026-09-08.
        val status = repo.todayStatus(instance, today = LocalDate.of(2026, 9, 8))

        assertEquals(550, status.nextItemNumber)
        assertEquals(1, status.dueCount)
    }

    @Test
    fun markNextWatched_dueCountZero_isNoOp() = runTest {
        val progressDao = FakeComputedScheduleProgressDao()
        progressDao.rows[4L] = ComputedScheduleProgress(4L, nextItemNumber = 543)
        val watchLogDao = FakeComputedScheduleWatchLogDao()
        val repo = ComputedScheduleRepository(progressDao, watchLogDao)

        repo.markNextWatched(instance, today = LocalDate.of(2026, 7, 20)) // one day before release

        assertEquals(543, progressDao.rows[4L]?.nextItemNumber)
        // Added per the Scope Revision: a no-op tap must not log a phantom watch event either.
        assertEquals(emptyList(), watchLogDao.rows)
    }

    @Test
    fun markNextWatched_due_advancesByExactlyOne() = runTest {
        val progressDao = FakeComputedScheduleProgressDao()
        progressDao.rows[4L] = ComputedScheduleProgress(4L, nextItemNumber = 543)
        val watchLogDao = FakeComputedScheduleWatchLogDao()
        val repo = ComputedScheduleRepository(progressDao, watchLogDao)
        val releaseDay = LocalDate.of(2026, 7, 21)

        repo.markNextWatched(instance, today = releaseDay)

        assertEquals(544, progressDao.rows[4L]?.nextItemNumber)
        // Added per the Scope Revision: markNextWatched must log the watch event (today's date,
        // the episode number that was actually just watched — 543, the pre-increment
        // nextItemNumber — not 544, the new one) inside the same call, not as a separate step a
        // caller could forget.
        assertEquals(listOf(ComputedScheduleWatchLog(id = 1L, habitInstanceId = 4L, date = releaseDay.toString(), episodeNumber = 543)), watchLogDao.rows)
    }

    @Test
    fun markNextWatched_multipleReleasesBehind_stillAdvancesByExactlyOneNotTheWholeBacklog() = runTest {
        val progressDao = FakeComputedScheduleProgressDao()
        progressDao.rows[4L] = ComputedScheduleProgress(4L, nextItemNumber = 543)
        val repo = ComputedScheduleRepository(progressDao, FakeComputedScheduleWatchLogDao())

        // 3 releases behind (dueCount 3) — a single tap must still advance by exactly 1.
        repo.markNextWatched(instance, today = LocalDate.of(2026, 8, 4))

        assertEquals(544, progressDao.rows[4L]?.nextItemNumber)
        // Strengthened per /autoplan Eng review (Section 3's Task 3 GAP #2): also assert dueCount
        // recomputed after the tap still reflects the correct remaining backlog (was 3, now 2),
        // not just that nextItemNumber incremented by 1 — an implementation bug that silently
        // skipped or double-counted backlog episodes would pass the assertion above alone.
        val statusAfter = repo.todayStatus(instance, today = LocalDate.of(2026, 8, 4))
        assertEquals(2, statusAfter.dueCount)
    }

    @Test
    fun currentStreak_noWatchLogRows_returnsZero() = runTest {
        val repo = ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao())

        assertEquals(0, repo.currentStreak(instance, LocalDate.of(2026, 7, 21)))
    }

    @Test
    fun currentStreak_delegatesToHabitStats_overTheWatchLogsDistinctDates() = runTest {
        // Real streak math now (per the Scope Revision — this test replaces the plan's original
        // currentStreak_alwaysReturnsZero, which is no longer correct). 3 consecutive watched
        // days ending today.
        val watchLogDao = FakeComputedScheduleWatchLogDao()
        watchLogDao.rows += ComputedScheduleWatchLog(1L, 4L, "2026-07-19", 541)
        watchLogDao.rows += ComputedScheduleWatchLog(2L, 4L, "2026-07-20", 542)
        watchLogDao.rows += ComputedScheduleWatchLog(3L, 4L, "2026-07-21", 543)
        val repo = ComputedScheduleRepository(FakeComputedScheduleProgressDao(), watchLogDao)

        assertEquals(3, repo.currentStreak(instance, today = LocalDate.of(2026, 7, 21)))
    }

    @Test
    fun currentStreak_gapBreaksTheStreak() = runTest {
        val watchLogDao = FakeComputedScheduleWatchLogDao()
        watchLogDao.rows += ComputedScheduleWatchLog(1L, 4L, "2026-07-14", 542) // isolated, a week before
        watchLogDao.rows += ComputedScheduleWatchLog(2L, 4L, "2026-07-21", 543)
        val repo = ComputedScheduleRepository(FakeComputedScheduleProgressDao(), watchLogDao)

        assertEquals(1, repo.currentStreak(instance, today = LocalDate.of(2026, 7, 21)))
    }

    @Test
    fun todayStatus_missingAnchorConfig_throwsIllegalStateException() = runTest {
        // Added per /autoplan Eng review (Section 3's Task 3 GAP #1) — pins the intentional-crash
        // contract named in the CEO phase's Failure Modes Registry, so a future refactor of
        // todayStatus can't silently swap it for a softer default.
        val incompleteInstance = instance.copy(anchorDate = null)
        val repo = ComputedScheduleRepository(FakeComputedScheduleProgressDao(), FakeComputedScheduleWatchLogDao())

        assertFailsWith<IllegalStateException> { repo.todayStatus(incompleteInstance, LocalDate.of(2026, 7, 21)) }
    }
}
