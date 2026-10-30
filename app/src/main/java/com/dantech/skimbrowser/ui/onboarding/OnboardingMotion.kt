package com.dantech.skimbrowser.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

/** How a piece arrives. A badge pops in place. A text block rises. */
internal enum class PieceMotion { Pop, Rise }

private const val PieceStaggerMs = 40L
private val PieceRise = 12.dp
private const val BadgeScaleFrom = 0.86f

/**
 * One piece of the onboarding entrance. Beat [index] waits, then a [PieceMotion.Pop] fades and
 * scales up around its own center, or a [PieceMotion.Rise] fades and rises [PieceRise].
 *
 * [still] draws [content] in its final place, with no motion layer: the screen after the entrance
 * finishes, and the screen when Remove animations is on. [modifier] stays outside that layer, so an
 * offset keeps a badge scaling around itself. [onSettled] runs from the last piece, after its fade
 * and its move have both reached the end, and not before the wait.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OnboardingPiece(
    index: Int,
    motion: PieceMotion,
    still: Boolean,
    modifier: Modifier = Modifier,
    onSettled: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (still) {
        if (modifier == Modifier) content() else Box(modifier) { content() }
        return
    }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(BadgeScaleFrom) }
    val rise = remember { Animatable(PieceRise, Dp.VectorConverter) }
    LaunchedEffect(Unit) {
        delay(index * PieceStaggerMs)
        val fade = async { alpha.animateTo(1f, effects) }
        val move = async {
            when (motion) {
                PieceMotion.Pop -> scale.animateTo(1f, effects)
                PieceMotion.Rise -> rise.animateTo(0.dp, spatial)
            }
            Unit
        }
        fade.await()
        move.await()
        onSettled?.invoke()
    }
    Box(
        modifier.graphicsLayer {
            this.alpha = alpha.value
            when (motion) {
                PieceMotion.Pop -> {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                PieceMotion.Rise -> translationY = rise.value.toPx()
            }
        },
    ) {
        content()
    }
}
