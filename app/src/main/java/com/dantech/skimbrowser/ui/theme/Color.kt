package com.dantech.skimbrowser.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Two schemes share one accent, Google blue #0B57D0, and the tertiary roles of Skim AI.
//
// The chrome scheme is for what floats over a web page: the browsing pill and the bars above it.
// Its surfaces are achromatic, the same gray family as Chrome and Safari, so the browser frame
// matches the page under it, dark pages included.
//
// The Skim scheme is for every surface Skim draws itself. Each neutral role keeps the tone of the
// chrome scheme, so text contrast is the same, and takes the lavender hue of the design boards
// (HCT hue 283) at a small chroma.

val SkimChromeLightColors = lightColorScheme(
    primary = Color(0xFF0B57D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF0B57D0),
    onPrimaryContainer = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF8AB4F8),
    secondary = Color(0xFF5F6368),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8EAED),
    onSecondaryContainer = Color(0xFF202124),
    tertiary = Color(0xFF8E3A88),
    onTertiary = Color(0xFFFFEEF8),
    tertiaryContainer = Color(0xFFFC99EF),
    onTertiaryContainer = Color(0xFF651162),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF202124),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFF5F6368),
    surfaceTint = Color(0xFF5F6368),
    inverseSurface = Color(0xFF303134),
    inverseOnSurface = Color(0xFFE8EAED),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF747775),
    outlineVariant = Color(0xFFC4C7C5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1F3F4),
    surfaceContainerHigh = Color(0xFFE8EAED),
    surfaceContainerHighest = Color(0xFFDADCE0),
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDADCE0),
)

// Page #202124, omnibox and toolbar #303134, accent #8AB4F8.
val SkimChromeDarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF202124),
    primaryContainer = Color(0xFF8AB4F8),
    onPrimaryContainer = Color(0xFF202124),
    inversePrimary = Color(0xFF0B57D0),
    secondary = Color(0xFFC4C7C5),
    onSecondary = Color(0xFF202124),
    secondaryContainer = Color(0xFF3C4043),
    onSecondaryContainer = Color(0xFFE8EAED),
    tertiary = Color(0xFFFFAFF2),
    onTertiary = Color(0xFF701D6D),
    tertiaryContainer = Color(0xFFFC99EF),
    onTertiaryContainer = Color(0xFF651162),
    background = Color(0xFF202124),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF202124),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF3C4043),
    onSurfaceVariant = Color(0xFF9AA0A6),
    surfaceTint = Color(0xFFC4C7C5),
    inverseSurface = Color(0xFFE8EAED),
    inverseOnSurface = Color(0xFF202124),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF8E918F),
    outlineVariant = Color(0xFF444746),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3C4043),
    surfaceContainer = Color(0xFF303134),
    surfaceContainerHigh = Color(0xFF303134),
    surfaceContainerHighest = Color(0xFF3C4043),
    surfaceContainerLow = Color(0xFF292A2D),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceDim = Color(0xFF131314),
)

// White stays the page of Home and Reader; the containers on it carry the tint. The ink keeps the
// red of the neutral ink, 0x20: one step lower and text edges render about 5 L* lighter.
val SkimLightColors = SkimChromeLightColors.copy(
    secondary = Color(0xFF616170),
    secondaryContainer = Color(0xFFE9E8FF),
    onSecondaryContainer = Color(0xFF20212F),
    onBackground = Color(0xFF20212F),
    onSurface = Color(0xFF20212F),
    surfaceVariant = Color(0xFFEAE8FC),
    onSurfaceVariant = Color(0xFF616170),
    surfaceTint = Color(0xFF616170),
    inverseSurface = Color(0xFF30303B),
    inverseOnSurface = Color(0xFFECE9EF),
    outline = Color(0xFF767582),
    outlineVariant = Color(0xFFC7C5D3),
    surfaceContainerLow = Color(0xFFFBF8FF),
    surfaceContainer = Color(0xFFF4F1FE),
    surfaceContainerHigh = Color(0xFFEAE8FC),
    surfaceContainerHighest = Color(0xFFDADAF4),
    surfaceDim = Color(0xFFDADAF4),
)

// surfaceContainerHigh is a step of its own here, between surfaceContainer and the highest.
val SkimDarkColors = SkimChromeDarkColors.copy(
    secondary = Color(0xFFC7C5D1),
    secondaryContainer = Color(0xFF3E3E4F),
    onSecondaryContainer = Color(0xFFECE9EF),
    background = Color(0xFF212127),
    onBackground = Color(0xFFECE9EF),
    surface = Color(0xFF212127),
    onSurface = Color(0xFFECE9EF),
    surfaceVariant = Color(0xFF3E3E4C),
    onSurfaceVariant = Color(0xFF9F9EAA),
    surfaceTint = Color(0xFFC7C5D1),
    inverseSurface = Color(0xFFECE9EF),
    inverseOnSurface = Color(0xFF20202B),
    outline = Color(0xFF908F9B),
    outlineVariant = Color(0xFF464550),
    surfaceBright = Color(0xFF3E3E4C),
    surfaceContainerLow = Color(0xFF292933),
    surfaceContainer = Color(0xFF30303B),
    surfaceContainerHigh = Color(0xFF373743),
    surfaceContainerHighest = Color(0xFF3E3E4C),
    surfaceDim = Color(0xFF131318),
)
