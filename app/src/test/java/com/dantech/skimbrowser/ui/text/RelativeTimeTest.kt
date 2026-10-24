package com.dantech.skimbrowser.ui.text

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on Robolectric: dates are formatted with the platform's ICU, as on a phone. */
@RunWith(AndroidJUnit4::class)
class RelativeTimeTest {

    private val singapore = ZoneId.of("Asia/Singapore")

    // Wednesday 21 October 2026, 15:00 in Singapore.
    private val now = at(2026, 10, 21, 15, 0)

    @Test
    fun underAMinuteIsJustNow() {
        assertEquals(UiText.Res(R.string.time_just_now), relative(now))
        assertEquals(UiText.Res(R.string.time_just_now), relative(now - 59_000))
    }

    @Test
    fun aTimeInTheFutureIsJustNow() {
        assertEquals(UiText.Res(R.string.time_just_now), relative(now + 5 * MINUTE))
    }

    @Test
    fun underAnHourCountsWholeMinutes() {
        assertEquals(UiText.Plural(R.plurals.time_minutes_ago, 1), relative(now - MINUTE))
        assertEquals(UiText.Plural(R.plurals.time_minutes_ago, 59), relative(now - HOUR + 1))
    }

    @Test
    fun laterTheSameDayCountsWholeHours() {
        assertEquals(UiText.Plural(R.plurals.time_hours_ago, 1), relative(now - HOUR))
        assertEquals(UiText.Plural(R.plurals.time_hours_ago, 2), relative(now - 2 * HOUR - 59 * MINUTE))
        assertEquals(UiText.Plural(R.plurals.time_hours_ago, 15), relative(at(2026, 10, 21, 0, 0)))
    }

    @Test
    fun thePreviousCalendarDayIsYesterday() {
        assertEquals(UiText.Res(R.string.date_yesterday), relative(at(2026, 10, 20, 23, 59)))
        assertEquals(UiText.Res(R.string.date_yesterday), relative(at(2026, 10, 20, 0, 0)))
    }

    @Test
    fun minutesWinOverYesterdayJustAfterMidnight() {
        val justAfterMidnight = at(2026, 10, 21, 0, 10)

        assertEquals(
            UiText.Plural(R.plurals.time_minutes_ago, 20),
            relativeTimeText(at(2026, 10, 20, 23, 50), justAfterMidnight, singapore, Locale.US),
        )
    }

    @Test
    fun olderDatesShowTheAbbreviatedMonthAndDay() {
        assertEquals(UiText.Raw("Oct 19"), relative(at(2026, 10, 19, 22, 0)))
        assertEquals(UiText.Raw("Jan 1"), relative(at(2026, 1, 1, 8, 0)))
    }

    @Test
    fun anotherYearAddsTheYear() {
        assertEquals(UiText.Raw("Dec 31, 2025"), relative(at(2025, 12, 31, 23, 0)))
    }

    @Test
    fun theDateFollowsTheLocale() {
        assertEquals(UiText.Raw("19. Okt."), relativeTimeText(at(2026, 10, 19, 22, 0), now, singapore, Locale.GERMANY))
    }

    @Test
    fun daysAreCountedInTheGivenZone() {
        // 15:00 UTC on the 20th is 23:00 in Singapore; two hours later it is the 21st there only.
        val visited = LocalDateTime.of(2026, 10, 20, 15, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val shownAt = visited + 2 * HOUR

        assertEquals(UiText.Res(R.string.date_yesterday), relativeTimeText(visited, shownAt, singapore, Locale.US))
        assertEquals(
            UiText.Plural(R.plurals.time_hours_ago, 2),
            relativeTimeText(visited, shownAt, ZoneOffset.UTC, Locale.US),
        )
    }

    @Test
    fun theDateIsFormattedInTheGivenZone() {
        // 20:00 UTC on 18 October is already the 19th in Singapore.
        val visited = LocalDateTime.of(2026, 10, 18, 20, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

        assertEquals(UiText.Raw("Oct 19"), relativeTimeText(visited, now, singapore, Locale.US))
        assertEquals(UiText.Raw("Oct 18"), relativeTimeText(visited, now, ZoneOffset.UTC, Locale.US))
    }

    private fun relative(epochMs: Long) = relativeTimeText(epochMs, now, singapore, Locale.US)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(singapore).toInstant().toEpochMilli()

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
