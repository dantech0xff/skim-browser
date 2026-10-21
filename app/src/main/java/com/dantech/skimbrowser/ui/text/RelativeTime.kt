package com.dantech.skimbrowser.ui.text

import android.icu.text.DateFormat
import android.icu.util.TimeZone
import com.dantech.skimbrowser.R
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS

/**
 * How long ago [epochMs] was, measured at [now], in the words Home draws ("2 h ago", "Yesterday"):
 * "Just now" under a minute (and for times in the future), then minutes, then hours while it is
 * still the same day, "Yesterday" for the previous calendar day, and the date after that.
 *
 * Calendar days are counted in [zone]. The date uses the skeleton that
 * `DateUtils.formatDateTime(FORMAT_SHOW_DATE or FORMAT_ABBREV_MONTH)` uses ("Oct 14" in English),
 * with the year added when it differs from the year of [now]. It is formatted here, in [locale],
 * because [UiText] carries no Context.
 */
fun relativeTimeText(
    epochMs: Long,
    now: Long,
    zone: ZoneId,
    locale: Locale = Locale.getDefault(),
): UiText {
    val elapsed = now - epochMs
    if (elapsed < MINUTE_MS) return UiText.Res(R.string.time_just_now)
    if (elapsed < HOUR_MS) return UiText.Plural(R.plurals.time_minutes_ago, (elapsed / MINUTE_MS).toInt())

    val day = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return when (day) {
        today -> UiText.Plural(R.plurals.time_hours_ago, (elapsed / HOUR_MS).toInt())
        today.minusDays(1) -> UiText.Res(R.string.date_yesterday)
        else -> {
            val skeleton = if (day.year == today.year) "MMMd" else "yMMMd"
            val format = DateFormat.getInstanceForSkeleton(skeleton, locale)
            format.timeZone = TimeZone.getTimeZone(zone.id)
            UiText.Raw(format.format(Date(epochMs)))
        }
    }
}
