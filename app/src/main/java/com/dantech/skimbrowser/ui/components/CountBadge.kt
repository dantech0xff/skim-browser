package com.dantech.skimbrowser.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/**
 * The number of tabs in a tab group, beside its name (09): a 24 dp pill in secondaryContainer with
 * the count in labelMediumEmphasized, 8 dp from each end. One digit makes a 24 dp circle.
 */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    require(count >= 0) { "A count can't be negative, got $count." }
    val colors = MaterialTheme.colorScheme
    Badge(
        // Sized from outside: the material3 badge is 16 dp and keeps 4 dp at each end.
        modifier = modifier.sizeIn(minWidth = BadgeHeight, minHeight = BadgeHeight),
        containerColor = colors.secondaryContainer,
        contentColor = colors.onSecondaryContainer,
    ) {
        Text(
            text = count.toString(),
            modifier = Modifier.padding(horizontal = ExtraTextPadding),
            style = MaterialTheme.typography.labelMediumEmphasized,
            maxLines = 1,
        )
    }
}

private val BadgeHeight = 24.dp

/** Added to the 4 dp the material3 badge keeps at each end: the drawn badge keeps 8. */
private val ExtraTextPadding = 4.dp

@PreviewLightDark
@Composable
private fun CountBadgePreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        Row(
            Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(4, 2, 1, 12, 128).forEach { CountBadge(it) }
        }
    }
}
