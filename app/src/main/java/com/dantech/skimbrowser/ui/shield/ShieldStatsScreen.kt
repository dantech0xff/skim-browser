package com.dantech.skimbrowser.ui.shield

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.domain.shield.ByteUnit
import com.dantech.skimbrowser.domain.shield.DurationUnit
import com.dantech.skimbrowser.domain.shield.ShieldEstimate
import com.dantech.skimbrowser.domain.shield.scaleBytes
import com.dantech.skimbrowser.domain.shield.scaleDuration
import com.dantech.skimbrowser.ui.components.DialogAction
import com.dantech.skimbrowser.ui.components.LargeTitleScaffold
import com.dantech.skimbrowser.ui.components.SectionLabel
import com.dantech.skimbrowser.ui.components.SkimAlertDialog
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import java.text.NumberFormat

@Composable
fun ShieldStatsRoute(navigator: SkimNavigator, model: ShieldStatsViewModel = hiltViewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    ShieldStatsScreen(state, onBack = navigator::back, onReset = model::reset)
}

@Composable
fun ShieldStatsScreen(
    state: ShieldStatsUiState,
    onBack: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    LargeTitleScaffold(
        title = stringResource(R.string.shield_stats_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        item {
            Text(
                text = stringResource(R.string.shield_stats_intro),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { TotalsTable(state) }
        item {
            Text(
                text = stringResource(R.string.shield_stats_chart_title),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item { WeekChart(state.chart) }
        item {
            SectionLabel(stringResource(R.string.shield_stats_top_sites), first = false)
            if (state.topHosts.isEmpty()) {
                Text(
                    text = stringResource(R.string.shield_stats_no_sites),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    state.topHosts.forEach { host ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(host.host, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(grouped(host.count), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        item {
            TextButton(
                onClick = { confirm = true },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.shield_stats_reset))
            }
        }
    }
    if (confirm) {
        SkimAlertDialog(
            title = stringResource(R.string.shield_stats_reset_title),
            confirm = DialogAction(
                label = stringResource(R.string.action_reset),
                onClick = {
                    confirm = false
                    onReset()
                },
            ),
            onDismissRequest = { confirm = false },
            dismiss = DialogAction(
                label = stringResource(R.string.action_cancel),
                onClick = { confirm = false },
            ),
            destructiveConfirm = true,
            text = { Text(stringResource(R.string.shield_stats_reset_body)) },
        )
    }
}

@Composable
private fun TotalsTable(state: ShieldStatsUiState) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        HeaderRow()
        CountRow(stringResource(R.string.shield_stats_requests), state.today.requests, state.week.requests, state.allTime.requests)
        CountRow(stringResource(R.string.shield_stats_popups), state.today.popups, state.week.popups, state.allTime.popups)
        CountRow(stringResource(R.string.shield_stats_redirects), state.today.redirects, state.week.redirects, state.allTime.redirects)
        CountRow(stringResource(R.string.shield_stats_cosmetic), state.today.cosmetic, state.week.cosmetic, state.allTime.cosmetic)
        TextRow(stringResource(R.string.shield_stats_data), byteText(state.today), byteText(state.week), byteText(state.allTime))
        TextRow(stringResource(R.string.shield_stats_time), timeText(state.today), timeText(state.week), timeText(state.allTime))
    }
}

@Composable
private fun HeaderRow() {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text("", modifier = Modifier.weight(1.4f))
        PeriodHeader(stringResource(R.string.shield_stats_today))
        PeriodHeader(stringResource(R.string.shield_stats_week))
        PeriodHeader(stringResource(R.string.shield_stats_all_time))
    }
}

@Composable
private fun RowScope.PeriodHeader(label: String) {
    Text(
        text = label,
        modifier = Modifier.weight(1f),
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CountRow(label: String, today: Long, week: Long, allTime: Long) {
    TextRow(label, grouped(today), grouped(week), grouped(allTime))
}

@Composable
private fun TextRow(label: String, today: String, week: String, allTime: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.bodyMedium)
        ValueCell(today)
        ValueCell(week)
        ValueCell(allTime)
    }
}

@Composable
private fun RowScope.ValueCell(value: String) {
    Text(
        text = value,
        modifier = Modifier.weight(1f),
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WeekChart(counts: List<Long>, modifier: Modifier = Modifier) {
    val reveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "shieldWeek",
    )
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val description = stringResource(R.string.shield_stats_chart_description, counts.joinToString())
    Canvas(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = description },
    ) {
        if (counts.isEmpty()) return@Canvas
        val max = counts.maxOf { it }.coerceAtLeast(1L).toFloat()
        val gap = 8.dp.toPx()
        val barWidth = (size.width - gap * (counts.size - 1)) / counts.size
        val radius = CornerRadius(6.dp.toPx())
        counts.forEachIndexed { index, value ->
            val left = index * (barWidth + gap)
            val height = size.height * (value.toFloat() / max) * reveal
            drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), radius)
            if (height > 0f) {
                drawRoundRect(bar, Offset(left, size.height - height), Size(barWidth, height), radius)
            }
        }
    }
}

@Composable
private fun byteText(period: ShieldPeriodUi): String {
    val scaled = scaleBytes(period.bytes)
    val number = if (scaled.tenths == 0) grouped(scaled.whole) else "${grouped(scaled.whole)}.${scaled.tenths}"
    val unit = when (scaled.unit) {
        ByteUnit.BYTE -> R.string.shield_stats_bytes
        ByteUnit.KILOBYTE -> R.string.shield_stats_kilobytes
        ByteUnit.MEGABYTE -> R.string.shield_stats_megabytes
        ByteUnit.GIGABYTE -> R.string.shield_stats_gigabytes
    }
    return stringResource(unit, number)
}

@Composable
private fun timeText(period: ShieldPeriodUi): String {
    val saved = ShieldEstimate.timeSavedMillis(period.bytes, period.requests)
    val scaled = scaleDuration(saved)
    val quiet = period.requests == 0L && period.bytes == 0L
    if (scaled.unit == DurationUnit.NONE) {
        return if (quiet) {
            pluralStringResource(R.plurals.shield_stats_seconds, 0, 0)
        } else {
            stringResource(R.string.shield_stats_time_under_a_second)
        }
    }
    val amount = scaled.amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val plural = when (scaled.unit) {
        DurationUnit.SECOND -> R.plurals.shield_stats_seconds
        DurationUnit.MINUTE -> R.plurals.shield_stats_minutes
        DurationUnit.HOUR -> R.plurals.shield_stats_hours
        DurationUnit.NONE -> R.plurals.shield_stats_seconds
    }
    return pluralStringResource(plural, amount, amount)
}

private fun grouped(value: Long): String = NumberFormat.getIntegerInstance().format(value)
