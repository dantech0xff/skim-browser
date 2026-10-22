package com.dantech.skimbrowser.ui.components

import android.graphics.Matrix
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.transformed
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting
import java.net.IDN
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The Skim AI mark: auto_awesome on a Sunny shape. Every AI surface uses it in tertiaryContainer.
 * [brand] draws the logo of Welcome (01) in primary instead: the logo is the brand, not an AI
 * surface.
 *
 * @param contentDescription "Skim" where the mark stands alone (04); null where it is decoration.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SunnyMark(
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    brand: Boolean = false,
    contentDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    ShapeArt(
        shape = MaterialShapes.Sunny,
        size = size,
        icon = R.drawable.ic_auto_awesome_filled,
        iconSize = iconSize,
        container = if (brand) colors.primary else colors.tertiaryContainer,
        content = if (brand) colors.onPrimary else colors.onTertiaryContainer,
        modifier = modifier,
        contentDescription = contentDescription,
    )
}

/**
 * An icon centered on a MaterialShapes polygon: the art of 01 and 11, the incognito hero of 09c
 * and the AI marks.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapeArt(
    shape: RoundedPolygon,
    size: Dp,
    @DrawableRes icon: Int,
    iconSize: Dp,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier.size(size).clip(shape.toDrawnShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription, Modifier.size(iconSize), tint = content)
    }
}

/**
 * A site's first letter on a Cookie4Sided tile, used in place of a favicon. Defaults are the 40 dp
 * list tile of 04c; the Continue cards of 04 (24 dp) and tab cards of 09 (20 dp) pass their own
 * size, style and colors. The letter is decoration next to the site's name, so it is not read out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FaviconLetterTile(
    letter: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMediumEmphasized,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    LetterTile(letter, MaterialShapes.Cookie4Sided.toDrawnShape(), size, textStyle, container, content, modifier)
}

/**
 * The 64 dp tile of a shortcut on Home (04): its first letter on the shape of its [position], so
 * neighbouring shortcuts look different at a glance. The name below it is the label.
 *
 * @param container surfaceContainerHighest; secondaryContainer while the shortcut's menu is open (15).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShortcutTile(
    letter: String,
    position: Int,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    LetterTile(
        letter = letter,
        shape = shortcutShapeFor(position).toDrawnShape(),
        size = ShortcutTileSize,
        textStyle = MaterialTheme.typography.titleLargeEmphasized,
        container = container,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    )
}

/** The Add tile after the shortcuts (04): a 64 dp circle with a 2 dp dashed border and a plus. */
@Composable
fun AddShortcutTile(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val border = colors.outlineVariant
    Box(
        modifier = modifier
            .size(ShortcutTileSize)
            .drawBehind {
                val width = DashedBorderWidth.toPx()
                val radius = (size.minDimension - width) / 2
                val circumference = 2 * PI.toFloat() * radius
                val dash = DashLength.toPx()
                // Whole dashes only, so the pattern closes evenly around the circle.
                val dashes = max(1, (circumference / (dash + DashGap.toPx())).roundToInt())
                drawCircle(
                    color = border,
                    radius = radius,
                    style = Stroke(
                        width = width,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(dash, circumference / dashes - dash),
                        ),
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

/**
 * A 40 dp icon tile for list items: a circle by default, or the rounded square of a folder (14).
 * Decoration next to the item's text, so it has no description unless one is passed.
 */
@Composable
fun IconTile(
    @DrawableRes icon: Int,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription, Modifier.size(iconSize), tint = content)
    }
}

/**
 * The letter shown for a site without a favicon: the first letter or digit of [host] without
 * "www." (internationalized hosts are decoded first), or of [title] when there is no host, in upper
 * case. Shortcuts pass their name as [title] and no host. Empty when neither has a letter or digit.
 */
fun siteLetter(host: String?, title: String?): String {
    val source = host?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { IDN.toUnicode(it.lowercase(Locale.ROOT), IDN.ALLOW_UNASSIGNED) }
        ?.removePrefix("www.")
        ?: title
        ?: return ""
    var index = 0
    while (index < source.length) {
        val codePoint = source.codePointAt(index)
        if (Character.isLetterOrDigit(codePoint)) {
            return String(Character.toChars(codePoint)).uppercase()
        }
        index += Character.charCount(codePoint)
    }
    return ""
}

/**
 * The shape of the shortcut at [position] on Home: the drawn order Cookie9Sided, Clover4Leaf,
 * SoftBurst, Cookie4Sided, Flower, Cookie12Sided, Pentagon, repeating after the seventh.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun shortcutShapeFor(position: Int): RoundedPolygon {
    val shapes = listOf(
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Clover4Leaf,
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Flower,
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Pentagon,
    )
    return shapes[position.mod(shapes.size)]
}

/**
 * The clip shape of [this] polygon as the boards draw it. Two shapes are turned on every board
 * compared with MaterialShapes: Cookie4Sided has its lobes up, down, left and right (a favicon reads
 * as a rounded diamond, 45 degrees from the library's corner lobes), and Clover8Leaf has a lobe on
 * top (22.5 degrees from the library's notch on top). The other shapes keep their orientation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RoundedPolygon.toDrawnShape(): Shape = when {
    this === MaterialShapes.Cookie4Sided -> Cookie4SidedAsDrawn
    this === MaterialShapes.Clover8Leaf -> Clover8LeafAsDrawn
    else -> this
}.toShape()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val Cookie4SidedAsDrawn: RoundedPolygon by lazy { MaterialShapes.Cookie4Sided.turned(45f) }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val Clover8LeafAsDrawn: RoundedPolygon by lazy { MaterialShapes.Clover8Leaf.turned(22.5f) }

/**
 * [this] turned by [degrees] around its center, then scaled on its exact bounds so it fills the unit
 * square again, as the drawn shapes fill their tiles. toShape(startAngle) cannot do this: it only
 * moves the point where the outline starts.
 */
private fun RoundedPolygon.turned(degrees: Float): RoundedPolygon {
    val turned = transformed(Matrix().apply { setRotate(degrees, centerX, centerY) })
    val (left, top, right, bottom) = turned.calculateBounds(approximate = false).toList()
    val side = max(right - left, bottom - top)
    return turned.transformed(
        Matrix().apply {
            setTranslate(-left + (side - (right - left)) / 2, -top + (side - (bottom - top)) / 2)
            postScale(1 / side, 1 / side)
        },
    )
}

@Composable
private fun LetterTile(
    letter: String,
    shape: Shape,
    size: Dp,
    textStyle: TextStyle,
    container: Color,
    content: Color,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, color = content, style = textStyle, maxLines = 1)
    }
}

private val ShortcutTileSize = 64.dp
private val DashedBorderWidth = 2.dp
private val DashLength = 6.dp
private val DashGap = 4.dp

@PreviewLightDark
@Composable
private fun ShortcutTilesPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Row(
            Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf("Wikipedia", "GitHub", "YouTube", "Hacker News").forEachIndexed { position, name ->
                ShortcutTile(siteLetter(host = null, title = name), position)
            }
            AddShortcutTile()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@PreviewLightDark
@Composable
private fun MarksAndTilesPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        val colors = MaterialTheme.colorScheme
        Column(
            Modifier.background(colors.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                SunnyMark(size = 64.dp, iconSize = 32.dp)
                SunnyMark(size = 36.dp, iconSize = 20.dp, brand = true)
                FaviconLetterTile(siteLetter(host = "www.daylightjournal.com", title = null))
                IconTile(R.drawable.ic_search, colors.secondaryContainer, colors.onSecondaryContainer)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ShapeArt(
                    MaterialShapes.SoftBurst,
                    size = 128.dp,
                    icon = R.drawable.ic_public_off,
                    iconSize = 56.dp,
                    container = colors.primaryContainer,
                    content = colors.onPrimaryContainer,
                )
                ShapeArt(
                    MaterialShapes.Cookie12Sided,
                    size = 72.dp,
                    icon = R.drawable.ic_visibility_off,
                    iconSize = 36.dp,
                    container = colors.secondaryContainer,
                    content = colors.onSecondaryContainer,
                )
            }
        }
    }
}
