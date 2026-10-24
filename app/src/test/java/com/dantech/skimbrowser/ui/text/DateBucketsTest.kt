package com.dantech.skimbrowser.ui.text

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DateBucketsTest {

    private val singapore = ZoneId.of("Asia/Singapore")

    // Wednesday 21 October 2026, 15:00 in Singapore.
    private val now = at(2026, 10, 21, 15, 0)

    @Test
    fun theSameCalendarDayIsToday() {
        assertEquals(DateBucket.Today, bucket(at(2026, 10, 21, 0, 0)))
        assertEquals(DateBucket.Today, bucket(at(2026, 10, 21, 14, 59)))
    }

    @Test
    fun aTimeInTheFutureIsToday() {
        assertEquals(DateBucket.Today, bucket(at(2026, 10, 22, 9, 0)))
    }

    @Test
    fun thePreviousCalendarDayIsYesterday() {
        assertEquals(DateBucket.Yesterday, bucket(at(2026, 10, 20, 23, 59)))
        assertEquals(DateBucket.Yesterday, bucket(at(2026, 10, 20, 0, 0)))
    }

    @Test
    fun earlierThisWeekStartsOnSundayInTheUs() {
        assertEquals(DateBucket.EarlierThisWeek, bucket(at(2026, 10, 19, 12, 0), Locale.US)) // Monday
        assertEquals(DateBucket.EarlierThisWeek, bucket(at(2026, 10, 18, 0, 0), Locale.US)) // Sunday
        assertEquals(DateBucket.Earlier, bucket(at(2026, 10, 17, 23, 59), Locale.US)) // Saturday
    }

    @Test
    fun earlierThisWeekStartsOnMondayInFrance() {
        assertEquals(DateBucket.EarlierThisWeek, bucket(at(2026, 10, 19, 0, 0), Locale.FRANCE)) // Monday
        assertEquals(DateBucket.Earlier, bucket(at(2026, 10, 18, 23, 59), Locale.FRANCE)) // Sunday
    }

    @Test
    fun yesterdayWinsWhenItFallsInThePreviousWeek() {
        val monday = at(2026, 10, 19, 9, 0)

        assertEquals(DateBucket.Yesterday, bucketOf(at(2026, 10, 18, 22, 0), monday, singapore, Locale.FRANCE))
        assertEquals(DateBucket.Earlier, bucketOf(at(2026, 10, 17, 22, 0), monday, singapore, Locale.FRANCE))
    }

    @Test
    fun daysAreCountedInTheGivenZone() {
        // 23:30 UTC on the 20th is 07:30 on the 21st in Singapore.
        val visited = LocalDateTime.of(2026, 10, 20, 23, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
        val shownAt = LocalDateTime.of(2026, 10, 21, 2, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        assertEquals(DateBucket.Today, bucketOf(visited, shownAt, singapore, Locale.US))
        assertEquals(DateBucket.Yesterday, bucketOf(visited, shownAt, ZoneOffset.UTC, Locale.US))
    }

    private fun bucket(epochMs: Long, locale: Locale = Locale.US) = bucketOf(epochMs, now, singapore, locale)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(singapore).toInstant().toEpochMilli()
}
