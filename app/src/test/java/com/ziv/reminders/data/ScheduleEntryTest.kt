package com.ziv.reminders.data

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleEntryTest {

    @Test
    fun parseTanakhSchedule_parsesEveryFieldFromAQuotedCsvRow() {
        val csv = "\"Book\",\"ChapterNum\",\"ChapterHeb\",\"Date\"\n" +
            "\"יהושע\",\"19\",\"י״ט\",\"14.6.2026\"\n"

        val entries = parseTanakhSchedule(csv)

        assertEquals(1, entries.size)
        assertEquals(ScheduleEntry(book = "יהושע", chapterHeb = "י״ט", date = LocalDate.of(2026, 6, 14)), entries[0])
    }

    @Test
    fun parseTanakhSchedule_parsesMultipleRowsInOrder() {
        val csv = "\"Book\",\"ChapterNum\",\"ChapterHeb\",\"Date\"\n" +
            "\"יהושע\",\"19\",\"י״ט\",\"14.6.2026\"\n" +
            "\"יהושע\",\"20\",\"כ׳\",\"15.6.2026\"\n"

        val entries = parseTanakhSchedule(csv)

        assertEquals(2, entries.size)
        assertEquals(LocalDate.of(2026, 6, 14), entries[0].date)
        assertEquals(LocalDate.of(2026, 6, 15), entries[1].date)
    }

    @Test
    fun parseTanakhSchedule_skipsBlankLines() {
        val csv = "\"Book\",\"ChapterNum\",\"ChapterHeb\",\"Date\"\n" +
            "\"יהושע\",\"19\",\"י״ט\",\"14.6.2026\"\n" +
            "\n"

        assertEquals(1, parseTanakhSchedule(csv).size)
    }

    @Test
    fun parseTanakhSchedule_emptyBody_returnsEmptyList() {
        val csv = "\"Book\",\"ChapterNum\",\"ChapterHeb\",\"Date\"\n"

        assertEquals(emptyList(), parseTanakhSchedule(csv))
    }
}
