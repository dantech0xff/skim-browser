package com.dantech.skimbrowser.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.testing.BOARD_STATUS_BAR_HEIGHT
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureComponent
import com.dantech.skimbrowser.testing.setSkimContent
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.graphics.Color as AndroidColor

/** The tag of the captured frame in every test of this file. */
private const val FRAME = "frame"

/**
 * Sets [content] in a full-width frame of [height] dp filled with [background], the way each group
 * below lays out a strip of its board.
 */
private fun ContentScreenshotTest.setStrip(
    height: Int,
    dark: Boolean = false,
    background: @Composable () -> Color = { MaterialTheme.colorScheme.surface },
    content: @Composable () -> Unit,
) {
    composeRule.setSkimContent(dark = dark) {
        Box(Modifier.fillMaxWidth().height(height.dp).background(background()).testTag(FRAME)) { content() }
    }
}

/** Captures the frame as golden [name] and checks the accessibility rules. */
private fun ContentScreenshotTest.capture(name: String) {
    composeRule.onNodeWithTag(FRAME).captureComponent(name)
    composeRule.assertA11y()
}

/** The drawn 40 dp copy and feedback buttons sit 4 dp apart, so their 48 dp targets overlap by 4. */
private val CompactButtonOverlap = (-4).dp

/**
 * Buttons, chips, icon buttons, the badge, switches, the reader size slider and the AI progress,
 * light and dark. Where a board draws the component, the golden is a horizontal strip of that board
 * starting at the y its test names, so a crop of the PNG at that y compares directly.
 * Cards, tab cards and the chat of 06 follow in the same way.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class ContentScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun restoreAnimations() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    // Buttons: every size and style, with an icon, and disabled.

    @Test
    fun buttons() = captureButtons("buttons", dark = false)

    @Test
    fun buttonsInTheDarkTheme() = captureButtons("buttons-dark", dark = true)

    private fun captureButtons(name: String, dark: Boolean) {
        setStrip(height = 500, dark = dark) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkimButton(stringResource(R.string.welcome_get_started), onClick = {}, Modifier.fillMaxWidth(), size = SkimButtonSize.M)
                Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SkimButton(
                        stringResource(R.string.action_try_again),
                        onClick = {},
                        Modifier.fillMaxWidth(),
                        size = SkimButtonSize.M,
                        icon = R.drawable.ic_refresh,
                    )
                    SkimButton(
                        stringResource(R.string.error_internet_settings),
                        onClick = {},
                        Modifier.fillMaxWidth(),
                        size = SkimButtonSize.M,
                        style = SkimButtonStyle.Tonal,
                        icon = R.drawable.ic_wifi,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkimButton(stringResource(R.string.setup_set_as_default), onClick = {})
                    SkimButton(stringResource(R.string.setup_choose_file), onClick = {}, style = SkimButtonStyle.Tonal)
                    SkimButton(stringResource(R.string.action_cancel), onClick = {}, style = SkimButtonStyle.Text)
                    SkimButton(stringResource(R.string.action_see_all), onClick = {}, style = SkimButtonStyle.Outlined)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkimButtonStyle.entries.forEach { style ->
                        SkimButton(stringResource(R.string.ai_chip_key_numbers), onClick = {}, size = SkimButtonSize.XS, style = style)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkimButtonStyle.entries.forEach { style ->
                        SkimButton(stringResource(R.string.action_done), onClick = {}, style = style, enabled = false)
                    }
                }
            }
        }
        capture(name)
    }

    // Chips and compact icon buttons of 06: from y 531 (the copy and feedback row) to 831.

    @Test
    fun chipsOfAnAnswer() = captureChips("chips-06", dark = false)

    @Test
    fun chipsOfAnAnswerInTheDarkTheme() = captureChips("chips-06-dark", dark = true)

    private fun captureChips(name: String, dark: Boolean) {
        setStrip(height = 300, dark = dark, background = { MaterialTheme.colorScheme.surfaceContainerLow }) {
            // The bottom of the Summary card, 8 dp under its buttons.
            Box(
                Modifier
                    .offset(x = 16.dp)
                    .size(380.dp, 52.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerLowest,
                        RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                    ),
            )
            Row(
                Modifier.padding(start = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(CompactButtonOverlap),
            ) {
                CompactIconButton(R.drawable.ic_content_copy, stringResource(R.string.cd_copy_summary), onClick = {})
                CompactIconButton(R.drawable.ic_thumb_up, stringResource(R.string.cd_good_summary), onClick = {})
                CompactIconButton(R.drawable.ic_thumb_down, stringResource(R.string.cd_poor_summary), onClick = {})
            }
            // XS chips lay out 48 dp high: 8 dp above the drawn 32 dp chip.
            FlowRow(
                Modifier.padding(start = 16.dp, top = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AiSuggestionChip(stringResource(R.string.ai_chip_key_numbers), onClick = {})
                AiSuggestionChip(stringResource(R.string.ai_chip_explain_simply), onClick = {})
                AiSuggestionChip(stringResource(R.string.ai_chip_the_catch), onClick = {})
            }
            // The citation under the answer; the board has no disabled one, so it sits beside it.
            Row(Modifier.padding(start = 16.dp, top = 252.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CitationChip(paragraph = 6, enabled = true, onClick = {})
                CitationChip(paragraph = 6, enabled = false, onClick = {})
            }
        }
        capture(name)
    }

    // Icon buttons: compact sizes, Send (on, off), Stop, Text size (off, on).

    @Test
    fun iconButtons() = captureIconButtons("icon-buttons", dark = false)

    @Test
    fun iconButtonsInTheDarkTheme() = captureIconButtons("icon-buttons-dark", dark = true)

    private fun captureIconButtons(name: String, dark: Boolean) {
        setStrip(height = 128, dark = dark) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompactIconButton(R.drawable.ic_content_copy, stringResource(R.string.cd_copy_answer), onClick = {})
                    CompactIconButton(
                        R.drawable.ic_close,
                        stringResource(R.string.cd_close_tab),
                        onClick = {},
                        iconSize = 18.dp,
                        visualSize = 36.dp,
                    )
                    CompactIconButton(
                        R.drawable.ic_more_vert,
                        stringResource(R.string.cd_group_options),
                        onClick = {},
                        iconSize = 24.dp,
                    )
                    CompactIconButton(
                        R.drawable.ic_thumb_down,
                        stringResource(R.string.cd_poor_answer),
                        onClick = {},
                        enabled = false,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SendButton(enabled = true, onClick = {})
                    SendButton(enabled = false, onClick = {})
                    StopButton(onClick = {})
                    TextSizeToggle(checked = false, onCheckedChange = {})
                    TextSizeToggle(checked = true, onCheckedChange = {})
                }
            }
        }
        capture(name)
    }

    // Text size on the reader: the top 88 dp of 08 (off) and 08b (on).

    @Test
    fun textSizeOff() = captureReaderTopRow("text-size-toggle-08", checked = false)

    @Test
    fun textSizeOn() = captureReaderTopRow("text-size-toggle-08b", checked = true)

    private fun captureReaderTopRow(name: String, checked: Boolean) {
        setStrip(height = 88) {
            Column {
                Spacer(Modifier.height(BOARD_STATUS_BAR_HEIGHT))
                Row(
                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {}) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            stringResource(R.string.cd_exit_reader_view),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    TextSizeToggle(checked = checked, onCheckedChange = {})
                }
            }
        }
        capture(name)
    }

    // Badge of a tab group header (09): from y 216, the header row, then more counts.

    @Test
    fun countBadges() = captureBadges("count-badge-09", dark = false)

    @Test
    fun countBadgesInTheDarkTheme() = captureBadges("count-badge-09-dark", dark = true)

    private fun captureBadges(name: String, dark: Boolean) {
        setStrip(height = 96, dark = dark) {
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    Modifier.height(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Heat pump research",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    CountBadge(4)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 1, 12, 128).forEach { CountBadge(it) }
                }
            }
        }
        capture(name)
    }

    // Switches: the whole of 10 (switch rows), and every state on its own.

    @Test
    fun aiAndPrivacySwitchRows() {
        composeRule.setSkimContent { AiPrivacyScreen(Modifier.testTag(FRAME)) }
        capture("switch-rows-10")
    }

    @Test
    fun switches() = captureSwitches("switches", dark = false)

    @Test
    fun switchesInTheDarkTheme() = captureSwitches("switches-dark", dark = true)

    private fun captureSwitches(name: String, dark: Boolean) {
        setStrip(height = 240, dark = dark, background = { MaterialTheme.colorScheme.surfaceContainerLow }) {
            val colors = MaterialTheme.colorScheme
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SegmentedColumn {
                    SkimListItem(
                        headline = stringResource(R.string.skim_ai),
                        supporting = stringResource(R.string.site_info_skim_ai_summary),
                        index = 0,
                        count = 2,
                        onClick = null,
                        modifier = Modifier.toggleableRow(checked = true, onCheckedChange = {}),
                        leading = { SunnyMark(size = 40.dp, iconSize = 20.dp) },
                        trailing = { SkimSwitch(checked = true, onCheckedChange = null) },
                    )
                    SkimListItem(
                        headline = stringResource(R.string.site_info_location),
                        supporting = stringResource(R.string.site_permission_blocked),
                        index = 1,
                        count = 2,
                        onClick = null,
                        modifier = Modifier.toggleableRow(checked = false, onCheckedChange = {}),
                        leading = { IconTile(R.drawable.ic_location_on, colors.surfaceContainer, colors.onSurfaceVariant) },
                        trailing = { SkimSwitch(checked = false, onCheckedChange = null) },
                    )
                }
                // On their own, switches are labelled by whatever they sit next to; here, by a description.
                val label = Modifier.semantics { contentDescription = "Skim AI" }
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SkimSwitch(checked = true, onCheckedChange = {}, label)
                    SkimSwitch(checked = false, onCheckedChange = {}, label)
                    SkimSwitch(checked = true, onCheckedChange = {}, label, enabled = false)
                    SkimSwitch(checked = false, onCheckedChange = {}, label, enabled = false)
                }
            }
        }
        capture(name)
    }

    // Reader size slider: 110% where 08b draws it (from y 751), then the ends and 100%.

    @Test
    fun readerSizeSliderOfTheTextSettings() {
        setStrip(height = 52, background = { MaterialTheme.colorScheme.surfaceContainerLow }) {
            // The slider row lays out 48 dp around the drawn 44 dp handle.
            ReaderSizeSlider(percent = 110, onPercent = {}, Modifier.padding(start = 24.dp, top = 2.dp, end = 24.dp))
        }
        capture("reader-size-slider-08b")
    }

    @Test
    fun readerSizeSliderRange() = captureSliderRange("reader-size-slider-range", dark = false)

    @Test
    fun readerSizeSliderRangeInTheDarkTheme() = captureSliderRange("reader-size-slider-range-dark", dark = true)

    private fun captureSliderRange(name: String, dark: Boolean) {
        setStrip(height = 200, dark = dark, background = { MaterialTheme.colorScheme.surfaceContainerLow }) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(80, 100, 200).forEach { ReaderSizeSlider(percent = it, onPercent = {}) }
            }
        }
        capture(name)
    }

    // AI progress of 06c: the Generating card, from y 415 to 559.

    @Test
    fun aiProgress() = captureAiProgress("ai-progress-06c", dark = false)

    @Test
    fun aiProgressInTheDarkTheme() = captureAiProgress("ai-progress-06c-dark", dark = true)

    @Test
    fun aiProgressWithAnimationsRemoved() = captureAiProgress("ai-progress-reduced-motion", dark = false, reducedMotion = true)

    @Test
    fun aiProgressWithAnimationsRemovedInTheDarkTheme() =
        captureAiProgress("ai-progress-reduced-motion-dark", dark = true, reducedMotion = true)

    private fun captureAiProgress(name: String, dark: Boolean, reducedMotion: Boolean = false) {
        if (reducedMotion) {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        } else {
            // A fixed frame of the moving indicator and wave, with a long stretch of wave in it.
            composeRule.mainClock.autoAdvance = false
        }
        setStrip(height = 144, dark = dark, background = { MaterialTheme.colorScheme.surfaceContainerLow }) {
            Box(
                Modifier
                    .offset(x = 16.dp)
                    .size(380.dp, 144.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(20.dp))
                    .padding(16.dp),
            ) {
                Column {
                    // The Summary label and the 16 dp gap under it.
                    Spacer(Modifier.height(36.dp))
                    AiLoadingIndicator(stringResource(R.string.cd_writing_summary))
                    // The drawn wave is centered 22 dp under the indicator; its row is 10 dp high.
                    Spacer(Modifier.height(17.dp))
                    AiWavyProgress()
                }
            }
        }
        if (!reducedMotion) composeRule.mainClock.advanceTimeBy(AiProgressFrameMillis)
        capture(name)
    }

    // Cards: the two cards of 03 (radius 28, padding 20), from y 168.

    @Test
    fun setupCards() = captureSetupCards("cards-03", dark = false)

    @Test
    fun setupCardsInTheDarkTheme() = captureSetupCards("cards-03-dark", dark = true)

    private fun captureSetupCards(name: String, dark: Boolean) {
        setStrip(height = 372, dark = dark) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SetupCard(
                    title = stringResource(R.string.setup_default_title),
                    body = stringResource(R.string.setup_default_body),
                    action = stringResource(R.string.setup_set_as_default),
                    tile = SetupTile.DefaultBrowser,
                )
                SetupCard(
                    title = stringResource(R.string.setup_import_title),
                    body = stringResource(R.string.setup_import_body),
                    action = stringResource(R.string.setup_choose_file),
                    tile = SetupTile.Import,
                )
            }
        }
        capture(name)
    }

    // AI hero card of Home (04), from y 196: the card grows to its two lines of text.

    @Test
    fun aiHeroCard() = captureHeroCard("ai-hero-card-04", dark = false)

    @Test
    fun aiHeroCardInTheDarkTheme() = captureHeroCard("ai-hero-card-04-dark", dark = true)

    private fun captureHeroCard(name: String, dark: Boolean) {
        setStrip(height = 130, dark = dark) {
            AiHeroCard(
                title = pluralStringResource(R.plurals.home_summarize_tabs, 7, 7),
                body = stringResource(R.string.home_summarize_tabs_body),
                onClick = {},
                modifier = Modifier.padding(16.dp),
            )
        }
        capture(name)
    }

    // Answer card of 06: the Summary card, from y 263.

    @Test
    fun answerCard() = captureAnswerCard("answer-card-06", dark = false)

    @Test
    fun answerCardInTheDarkTheme() = captureAnswerCard("answer-card-06-dark", dark = true)

    private fun captureAnswerCard(name: String, dark: Boolean) {
        setStrip(height = 336, dark = dark, background = { MaterialTheme.colorScheme.surfaceContainerLow }) {
            SummaryCard(Modifier.padding(16.dp))
        }
        capture(name)
    }

    // Chat of 06: the suggested questions, a question, its answer and citation, from y 591.

    @Test
    fun chat() = captureChat("chat-06", dark = false)

    @Test
    fun chatInTheDarkTheme() = captureChat("chat-06-dark", dark = true)

    private fun captureChat(name: String, dark: Boolean) {
        setStrip(height = 240, dark = dark, background = { MaterialTheme.colorScheme.surfaceContainerLow }) {
            Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SuggestedQuestions()
                ChatTurn()
            }
        }
        capture(name)
    }

    // Chat input: the bottom 80 dp of 06 (empty), 06c (Skim writing) and 06c No connection (off).

    @Test
    fun chatInput() = captureChatInput("chat-input-06", enabled = true, generating = false)

    @Test
    fun chatInputWhileSkimWrites() = captureChatInput("chat-input-06c", enabled = true, generating = true)

    @Test
    fun chatInputOffline() = captureChatInput("chat-input-06c-offline", enabled = false, generating = false)

    @Test
    fun chatInputWithAQuestion() = captureChatInput("chat-input-typed", enabled = true, generating = false, text = QUESTION)

    private fun captureChatInput(name: String, enabled: Boolean, generating: Boolean, text: String = "") {
        setStrip(height = 80, background = { MaterialTheme.colorScheme.surfaceContainerLow }) {
            ChatInputPill(
                state = rememberTextFieldState(text),
                placeholder = stringResource(R.string.ai_ask_about_page),
                enabled = enabled,
                generating = generating,
                onSend = {},
                onStop = {},
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
            )
        }
        capture(name)
    }

    @Test
    fun chatInputsInTheDarkTheme() {
        setStrip(height = 288, dark = true, background = { MaterialTheme.colorScheme.surfaceContainerLow }) {
            val placeholder = stringResource(R.string.ai_ask_about_page)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ChatInputPill(rememberTextFieldState(), placeholder, enabled = true, generating = false, onSend = {}, onStop = {})
                ChatInputPill(rememberTextFieldState(QUESTION), placeholder, enabled = true, generating = false, onSend = {}, onStop = {})
                ChatInputPill(rememberTextFieldState(), placeholder, enabled = true, generating = true, onSend = {}, onStop = {})
                ChatInputPill(rememberTextFieldState(), placeholder, enabled = false, generating = false, onSend = {}, onStop = {})
            }
        }
        capture("chat-inputs-dark")
    }

    // Tab cards: the first group of 09 (from y 256), and the incognito tabs of 09b (from y 244).

    @Test
    fun tabCards() {
        setStrip(height = 316) {
            TabGrid(
                incognito = false,
                tabs = listOf(
                    SampleTab("Why heat pumps are winning in cold climates", "daylightjournal.com", isCurrent = true, hasSkimChat = true),
                    SampleTab("Heat pump sizing calculator", "heatcalc.example"),
                    SampleTab("Installer reviews near me", "reviews.example"),
                    SampleTab("Heat pump rebates 2026", "energysaver.example"),
                ),
            )
        }
        capture("tab-cards-09")
    }

    @Test
    fun incognitoTabCards() {
        setStrip(height = 160, dark = true) {
            TabGrid(
                incognito = true,
                tabs = listOf(
                    SampleTab("Anniversary gift ideas", "giftguide.example", isCurrent = true),
                    SampleTab("Flights to Lisbon in May", "flights.example"),
                ),
            )
        }
        capture("tab-cards-09b")
    }

    @Test
    fun tabCardsWithoutPreviews() = captureTabCardsWithoutPreviews("tab-cards-no-preview", dark = false)

    @Test
    fun tabCardsWithoutPreviewsInTheDarkTheme() = captureTabCardsWithoutPreviews("tab-cards-no-preview-dark", dark = true)

    private fun captureTabCardsWithoutPreviews(name: String, dark: Boolean) {
        setStrip(height = 160, dark = dark) {
            TabGrid(
                incognito = false,
                tabs = listOf(
                    SampleTab("Kotlin 2.2 release notes", "kotlinlang.org", isCurrent = true, hasSkimChat = true, preview = false),
                    SampleTab("Osaka night buses: a complete guide", "tabi-note.jp", hasSkimChat = true, preview = false),
                ),
            )
        }
        capture(name)
    }

    private companion object {
        const val AiProgressFrameMillis = 900L
    }
}

/** The tile of a card on 03. */
private enum class SetupTile { DefaultBrowser, Import }

/**
 * A card of 03: radius 28 and 20 dp of padding, the tile beside the title and text, and a tonal S
 * button at the end. The button lays out 48 dp around its 40 dp shape, so the spacing around it is
 * 4 dp less than drawn.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupCard(title: String, body: String, action: String, tile: SetupTile) {
    val colors = MaterialTheme.colorScheme
    SkimCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 28.dp,
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp - ButtonFrameOverhang),
        verticalArrangement = Arrangement.spacedBy(16.dp - ButtonFrameOverhang),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            when (tile) {
                SetupTile.DefaultBrowser -> ShapeArt(
                    MaterialShapes.Cookie4Sided,
                    size = 48.dp,
                    icon = R.drawable.ic_public,
                    iconSize = 24.dp,
                    container = colors.primaryContainer,
                    content = colors.onPrimaryContainer,
                )
                SetupTile.Import -> ShapeArt(
                    MaterialShapes.Clover4Leaf,
                    size = 48.dp,
                    icon = R.drawable.ic_bookmarks,
                    iconSize = 24.dp,
                    container = colors.secondaryContainer,
                    content = colors.onSecondaryContainer,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(body, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        SkimButton(action, onClick = {}, Modifier.align(Alignment.End), style = SkimButtonStyle.Tonal)
    }
}

/** A tab of the sample grids: the page's title and host, and whether the board draws a preview. */
private data class SampleTab(
    val title: String,
    val host: String,
    val isCurrent: Boolean = false,
    val hasSkimChat: Boolean = false,
    val preview: Boolean = true,
)

/** Tab cards two to a row, 12 dp apart, 8 dp below the top of the strip and 16 dp from its sides. */
@Composable
private fun TabGrid(incognito: Boolean, tabs: List<SampleTab>) {
    val preview = remember { samplePagePreview() }
    Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tabs.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tab ->
                    TabCard(
                        title = tab.title,
                        letter = siteLetter(host = tab.host, title = null),
                        preview = if (tab.preview) preview else null,
                        isCurrent = tab.isCurrent,
                        hasSkimChat = tab.hasSkimChat,
                        incognito = incognito,
                        onClick = {},
                        onClose = {},
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * A captured page as the boards sketch one: white, a heading bar, an image block and two lines of
 * text, at the 168 by 92 dp of a preview at 2x. These are the colors of the sample page, not app
 * colors.
 */
private fun samplePagePreview(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(336, 184, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(AndroidColor.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun block(left: Float, top: Float, width: Float, height: Float, radius: Float, color: Int) {
        paint.color = color
        canvas.drawRoundRect(RectF(left, top, left + width, top + height), radius, radius, paint)
    }
    // 10 dp of padding and 6 dp between the parts, as on the first card of 09.
    block(20f, 20f, 207f, 16f, 8f, AndroidColor.rgb(0xC9, 0xCA, 0xE4))
    block(20f, 48f, 296f, 56f, 8f, AndroidColor.rgb(0xDC, 0xE2, 0xE8))
    block(20f, 116f, 266f, 10f, 6f, AndroidColor.rgb(0xE4, 0xE4, 0xF2))
    block(20f, 138f, 237f, 10f, 6f, AndroidColor.rgb(0xE4, 0xE4, 0xF2))
    return bitmap.asImageBitmap()
}

/** AI & privacy (10), without its footer. */
@Composable
private fun AiPrivacyScreen(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    LargeTitleScaffold(title = stringResource(R.string.settings_ai_privacy), onBack = {}, modifier = modifier) {
        item { SectionLabel(stringResource(R.string.settings_section_privacy), first = true) }
        item {
            SegmentedColumn {
                SkimListItem(
                    headline = stringResource(R.string.ai_settings_ask_before_sending),
                    supporting = stringResource(R.string.ai_settings_ask_before_sending_summary),
                    index = 0,
                    count = 3,
                    onClick = null,
                    modifier = Modifier.toggleableRow(checked = true, onCheckedChange = {}),
                    leading = { IconTile(R.drawable.ic_cloud_upload, colors.secondaryContainer, colors.onSecondaryContainer) },
                    trailing = { SkimSwitch(checked = true, onCheckedChange = null) },
                )
                SkimListItem(
                    headline = stringResource(R.string.ai_settings_keep_history),
                    supporting = stringResource(R.string.ai_settings_keep_history_summary),
                    index = 1,
                    count = 3,
                    onClick = null,
                    modifier = Modifier.toggleableRow(checked = true, onCheckedChange = {}),
                    leading = { IconTile(R.drawable.ic_history, colors.secondaryContainer, colors.onSecondaryContainer) },
                    trailing = { SkimSwitch(checked = true, onCheckedChange = null) },
                )
                SkimListItem(
                    headline = stringResource(R.string.skim_history_title),
                    supporting = pluralStringResource(R.plurals.ai_settings_conversations, 5, 5),
                    index = 2,
                    count = 3,
                    onClick = {},
                    leading = { SunnyMark(size = 40.dp, iconSize = 20.dp) },
                )
            }
        }
        item {
            SegmentedColumn(Modifier.padding(top = 16.dp)) {
                SkimListItem(
                    headline = stringResource(R.string.ai_settings_delete_all),
                    index = 0,
                    count = 1,
                    onClick = {},
                    leading = { IconTile(R.drawable.ic_delete, colors.errorContainer, colors.onErrorContainer) },
                    headlineColor = colors.error,
                    headlineWeight = FontWeight.Medium,
                )
            }
        }
    }
}
