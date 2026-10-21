package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.dantech.skimbrowser.R

/*
 * Sample content of Ask about this page (06), laid out with the components where the board draws
 * it. The page title, the summary, the question and the answer are sample data, as on the board.
 */

internal const val PAGE_TITLE = "Why heat pumps are winning in cold climates"
internal const val PAGE_HOST = "daylightjournal.com"
internal const val QUESTION = "How much would I save per year?"
internal const val ANSWER = "The article puts it at \$300 to \$900 a year for a home switching from oil heating. " +
    "Switching from gas saves less, and the exact figure depends on local energy prices."
private val SummaryPoints = listOf(
    "Modern cold-climate heat pumps keep heating at minus 25 °C, so the old \"not below freezing\" rule no longer holds.",
    "The gains come from variable-speed compressors and refrigerants that stay efficient in cold air.",
    "Switching from oil saves roughly \$300 to \$900 a year; from gas the savings are smaller and depend on local prices.",
)

/** The 48 dp touch frame of a 40 dp button reaches 4 dp past it on each side. */
internal val ButtonFrameOverhang = 4.dp

/** The 48 dp touch frame of a 32 dp chip reaches 8 dp past it on each side. */
internal val ChipFrameOverhang = 8.dp

/**
 * Lays out content [overhang] shorter at the top and the bottom, so a row of buttons with 48 dp
 * touch frames takes the space of their drawn shapes and the board's spacing applies to the shapes.
 * The frames may reach past the space given: the content is measured that much taller.
 */
internal fun Modifier.drawnHeight(overhang: Dp): Modifier = layout { measurable, constraints ->
    val inset = overhang.roundToPx()
    val placeable = measurable.measure(constraints.offset(vertical = 2 * inset))
    layout(placeable.width, placeable.height - 2 * inset) { placeable.place(0, -inset) }
}

/** The header of 06: the 44 dp Sunny, the page title and host, Close. 60 dp high. */
@Composable
internal fun AskPageHeader() {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SunnyMark(size = 44.dp, iconSize = 22.dp)
        Column(Modifier.weight(1f)) {
            Text(PAGE_TITLE, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(PAGE_HOST, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = {}, colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onSurfaceVariant)) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cd_close))
        }
    }
}

/** The Summary card of 06: the label, three points, then copy and feedback. 304 dp high as drawn. */
@Composable
internal fun SummaryCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    AnswerCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_auto_awesome_filled), null, Modifier.size(20.dp), tint = colors.tertiary)
            Text(stringResource(R.string.ai_summary), color = colors.tertiary, style = MaterialTheme.typography.titleSmallEmphasized)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryPoints.forEach { point ->
                Row {
                    Box(Modifier.width(20.dp), contentAlignment = Alignment.TopCenter) {
                        Text("•", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(point, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        // The buttons start 8 dp before the text and sit 4 dp apart, as drawn.
        Row(
            Modifier
                .offset(x = -(8.dp + ButtonFrameOverhang))
                .drawnHeight(ButtonFrameOverhang),
            horizontalArrangement = Arrangement.spacedBy(-ButtonFrameOverhang),
        ) {
            CompactIconButton(R.drawable.ic_content_copy, stringResource(R.string.cd_copy_summary), onClick = {})
            CompactIconButton(R.drawable.ic_thumb_up, stringResource(R.string.cd_good_summary), onClick = {})
            CompactIconButton(R.drawable.ic_thumb_down, stringResource(R.string.cd_poor_summary), onClick = {})
        }
    }
}

/** The suggested questions under the summary of 06. */
@Composable
internal fun SuggestedQuestions(modifier: Modifier = Modifier) {
    FlowRow(modifier.drawnHeight(ChipFrameOverhang), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AiSuggestionChip(stringResource(R.string.ai_chip_key_numbers), onClick = {})
        AiSuggestionChip(stringResource(R.string.ai_chip_explain_simply), onClick = {})
        AiSuggestionChip(stringResource(R.string.ai_chip_the_catch), onClick = {})
    }
}

/** A question and its answer with the citation 8 dp under it, as on 06. */
@Composable
internal fun ChatTurn(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UserBubble(QUESTION)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnswerText(ANSWER)
            CitationChip(paragraph = 6, enabled = true, onClick = {}, Modifier.drawnHeight(ChipFrameOverhang))
        }
    }
}

/** Ask about this page as drawn on 06: 736 dp high with its handle row. */
@Composable
internal fun AskPageSheetContent() {
    AskPageHeader()
    Column(
        Modifier
            .fillMaxWidth()
            .height(560.dp)
            .padding(start = 16.dp, top = 4.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SummaryCard()
        SuggestedQuestions()
        ChatTurn()
    }
    Box(Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp)) {
        ChatInputPill(
            state = rememberTextFieldState(),
            placeholder = stringResource(R.string.ai_ask_about_page),
            enabled = true,
            generating = false,
            onSend = {},
            onStop = {},
        )
    }
}
