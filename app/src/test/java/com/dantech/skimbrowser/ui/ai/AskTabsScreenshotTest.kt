package com.dantech.skimbrowser.ui.ai

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.data.db.entity.MessageFeedback
import com.dantech.skimbrowser.testing.PHONE_QUALIFIERS
import com.dantech.skimbrowser.testing.assertA11y
import com.dantech.skimbrowser.testing.captureBoard
import com.dantech.skimbrowser.testing.setSkimContent
import com.dantech.skimbrowser.ui.components.SkimBottomSheet
import com.dantech.skimbrowser.ui.components.rememberSkimSheetState
import com.dantech.skimbrowser.ui.home.HomeFixtures
import com.dantech.skimbrowser.ui.home.HomeScreen
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Ask Skim about the open tabs (06b), over Home. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class AskTabsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun askSkimFromHome() {
        composeRule.setSkimContent { Sheet() }
        composeRule.waitForIdle()
        captureBoard("06b-ask-skim-from-home-tabs")
        composeRule.assertA11y()
    }
}

@Composable
private fun Sheet() {
    HomeScreen(
        state = HomeFixtures.Board,
        actions = HomeFixtures.Recorder().actions,
        toolbar = { HomeFixtures.Toolbar(HomeFixtures.newTabPage(), 7) },
    )
    SkimBottomSheet(
        onDismiss = {},
        paneTitle = stringResource(R.string.ask_skim),
        state = rememberSkimSheetState(skipPartiallyExpanded = true),
    ) {
        AskTabsSheet(
            state = board(),
            actions = AskTabsActions(
                onClose = {},
                onSend = {},
                onStop = {},
                onChip = {},
                onRetry = {},
                onCopy = {},
                onGood = {},
                onPoor = {},
                onOpenGroup = {},
            ),
            snackbar = remember { SnackbarHostState() },
            bodyHeight = 640.dp,
        )
    }
}

private fun board() = AskTabsUiState(
    tabCount = 7,
    prompt = "Summarize my 7 open tabs",
    overview = TabsOverviewState.Ready(
        intro = "Your tabs cover three topics.",
        groups = persistentListOf(
            OverviewGroupUi(
                "Heat pump research",
                "Across 4 tabs you were comparing sizing, installer reviews and 2026 rebates. " +
                    "Cold-climate models now work down to minus 25 °C.",
                "heat",
            ),
            OverviewGroupUi(
                "Osaka trip",
                "Your 2 tabs cover night buses from Namba and Umeda after midnight and a hotel search for 2 nights near Namba.",
                "osaka",
            ),
            OverviewGroupUi("Other", "One tab with the Kotlin 2.2 release notes.", "kotlin"),
        ),
        feedback = MessageFeedback.NONE,
        messageId = 1,
        followUps = persistentListOf("Which installer looked best?", "What is left to book in Osaka?"),
    ),
    inputEnabled = true,
)
