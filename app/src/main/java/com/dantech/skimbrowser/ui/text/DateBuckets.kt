package com.dantech.skimbrowser.ui.text

import androidx.annotation.StringRes
import com.dantech.skimbrowser.R
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

/** The day sections of Skim history (04c) and History (13), newest first. */
enum class DateBucket(@param:StringRes val title: Int) {
    Today(R.string.date_today),
    Yesterday(R.string.date_yesterday),
    EarlierThisWeek(R.string.date_earlier_this_week),
    Earlier(R.string.date_earlier),
}

/**
 * The section [epochMs] belongs to when the list is shown at [now]. Days are calendar days in
 * [zone], and the week starts on the first day of the week of [locale], so "Earlier this week"
 * matches the user's calendar. Yesterday wins when it falls in the previous week, and a time in the
 * future (a clock that moved back) counts as today.
 */
fun bucketOf(
    epochMs: Long,
    now: Long,
    zone: ZoneId,
    locale: Locale = Locale.getDefault(),
): DateBucket {
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val weekStart = today.with(WeekFields.of(locale).dayOfWeek(), 1)
    return when {
        !day.isBefore(today) -> DateBucket.Today
        day == today.minusDays(1) -> DateBucket.Yesterday
        !day.isBefore(weekStart) -> DateBucket.EarlierThisWeek
        else -> DateBucket.Earlier
    }
}
