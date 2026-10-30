package com.dantech.skimbrowser.ui.onboarding

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import android.content.ActivityNotFoundException
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.system.DefaultBrowser
import com.dantech.skimbrowser.ui.components.ShapeArt
import com.dantech.skimbrowser.ui.components.SkimButton
import com.dantech.skimbrowser.ui.components.SkimButtonSize
import com.dantech.skimbrowser.ui.components.SkimButtonStyle
import com.dantech.skimbrowser.ui.components.SkimCard
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import com.dantech.skimbrowser.ui.theme.rememberReducedMotion

/**
 * Default browser and import (03): two optional steps, a privacy note and Start browsing. Set as
 * default turns into a disabled Done once Skim is the default browser ([SetupUiState.isDefault]).
 * The steps scroll when the text is too large for the screen; Start browsing stays at the bottom.
 * The title, each step tile, each step's text and the privacy row ease in. The step buttons and
 * Start browsing are already there. With Remove animations on, the settled layout is what shows.
 * Set as default crossfades into Done.
 */
@Composable
fun SetupScreen(
    state: SetupUiState,
    onSetDefault: () -> Unit,
    onChooseFile: () -> Unit,
    onStartBrowsing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    var settled by rememberSaveable { mutableStateOf(false) }
    val still = reduced || settled
    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 48.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingPiece(index = 0, motion = PieceMotion.Rise, still = still) {
                    Text(
                        stringResource(R.string.setup_title),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.headlineLargeEmphasized,
                    )
                }
                OnboardingPiece(index = 1, motion = PieceMotion.Rise, still = still) {
                    Text(
                        stringResource(R.string.setup_body),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SetupStep(
                    tile = SetupTile.DefaultBrowser,
                    title = stringResource(R.string.setup_default_title),
                    body = stringResource(R.string.setup_default_body),
                    action = stringResource(if (state.isDefault) R.string.action_done else R.string.setup_set_as_default),
                    actionEnabled = !state.isDefault,
                    onAction = onSetDefault,
                    still = still,
                    reduced = reduced,
                    tileIndex = 2,
                    textIndex = 3,
                )
                SetupStep(
                    tile = SetupTile.Import,
                    title = stringResource(R.string.setup_import_title),
                    body = stringResource(R.string.setup_import_body),
                    action = stringResource(R.string.setup_choose_file),
                    actionEnabled = true,
                    onAction = onChooseFile,
                    still = still,
                    reduced = reduced,
                    tileIndex = 4,
                    textIndex = 5,
                )
            }
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OnboardingPiece(index = 6, motion = PieceMotion.Pop, still = still) {
                    Icon(
                        painterResource(R.drawable.ic_verified_user),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
                OnboardingPiece(index = 7, motion = PieceMotion.Rise, still = still, onSettled = { settled = true }) {
                    Text(
                        stringResource(R.string.setup_privacy_note),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 40.dp),
        ) {
            SkimButton(
                text = stringResource(R.string.setup_start_browsing),
                onClick = onStartBrowsing,
                modifier = Modifier.fillMaxWidth(),
                size = SkimButtonSize.M,
            )
        }
    }
}

/**
 * 03 wired to the app: the view model checks the default browser again whenever the screen
 * resumes (the user may change it in the system's settings), Set as default starts the system's
 * request ([DefaultBrowser.requestIntent]), Choose file opens the picker for an HTML file, and
 * Start browsing ends onboarding for good.
 */
@Composable
fun SetupRoute(navigator: SkimNavigator) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val viewModel = hiltViewModel<SetupViewModel>()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        lifecycle.addObserver(viewModel)
        onDispose { lifecycle.removeObserver(viewModel) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val roleRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refreshDefault()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    SetupScreen(
        state = state,
        onSetDefault = {
            try {
                roleRequest.launch(DefaultBrowser.requestIntent(context))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No screen sets the default browser: ${e.javaClass.simpleName}")
            }
        },
        onChooseFile = {
            try {
                picker.launch(arrayOf(HTML_MIME_TYPE))
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No file picker: ${e.javaClass.simpleName}")
            }
        },
        onStartBrowsing = {
            viewModel.finishOnboarding()
            navigator.finishOnboarding()
        },
    )
}

private const val TAG = "SkimSetup"
private const val HTML_MIME_TYPE = "text/html"

/** The tile beside a step: the web on Cookie4Sided, bookmarks on Clover4Leaf. */
private enum class SetupTile { DefaultBrowser, Import }

/**
 * A step of 03: a card with radius 28 and 20 dp of padding, the tile beside the title and text, and
 * a tonal S button at the end. The button lays out 48 dp around its 40 dp shape, so the spacing
 * around it is 4 dp less than drawn.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupStep(
    tile: SetupTile,
    title: String,
    body: String,
    action: String,
    actionEnabled: Boolean,
    onAction: () -> Unit,
    still: Boolean,
    reduced: Boolean,
    tileIndex: Int,
    textIndex: Int,
) {
    val colors = MaterialTheme.colorScheme
    val fade = if (reduced) snap<Float>() else MaterialTheme.motionScheme.defaultEffectsSpec()
    SkimCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 28.dp,
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp - ButtonFrameOverhang),
        verticalArrangement = Arrangement.spacedBy(16.dp - ButtonFrameOverhang),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OnboardingPiece(index = tileIndex, motion = PieceMotion.Pop, still = still) {
                when (tile) {
                    SetupTile.DefaultBrowser -> StepTile(
                        MaterialShapes.Cookie4Sided, R.drawable.ic_public, colors.primaryContainer, colors.onPrimaryContainer,
                    )
                    SetupTile.Import -> StepTile(
                        MaterialShapes.Clover4Leaf, R.drawable.ic_bookmarks, colors.secondaryContainer, colors.onSecondaryContainer,
                    )
                }
            }
            if (still) {
                StepCopy(title, body, Modifier.weight(1f))
            } else {
                OnboardingPiece(
                    index = textIndex,
                    motion = PieceMotion.Rise,
                    still = false,
                    modifier = Modifier.weight(1f),
                ) {
                    StepCopy(title, body, Modifier.fillMaxWidth())
                }
            }
        }
        AnimatedContent(
            targetState = action to actionEnabled,
            modifier = Modifier.align(Alignment.End),
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            label = "setup-action",
        ) { (label, enabled) ->
            val current = label == action && enabled == actionEnabled
            SkimButton(
                text = label,
                onClick = { if (current) onAction() },
                style = SkimButtonStyle.Tonal,
                enabled = enabled,
            )
        }
    }
}

/** The title and body of a step. [modifier] carries the row weight once the entrance has settled. */
@Composable
private fun StepCopy(title: String, body: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = colors.onSurface, style = MaterialTheme.typography.titleMediumEmphasized)
        Text(body, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StepTile(shape: RoundedPolygon, @DrawableRes icon: Int, container: Color, content: Color) {
    ShapeArt(shape, size = 48.dp, icon = icon, iconSize = 24.dp, container = container, content = content)
}

/** How far the 48 dp frame of an S button reaches past its 40 dp shape, above and below. */
private val ButtonFrameOverhang = 4.dp

@PreviewLightDark
@Composable
private fun SetupScreenPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        SetupScreen(SetupUiState(isDefault = false), onSetDefault = {}, onChooseFile = {}, onStartBrowsing = {})
    }
}
