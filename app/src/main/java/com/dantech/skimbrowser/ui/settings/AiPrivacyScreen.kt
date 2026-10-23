package com.dantech.skimbrowser.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dantech.skimbrowser.AppContainer
import com.dantech.skimbrowser.BuildConfig
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.ui.components.IconTile
import com.dantech.skimbrowser.ui.components.LargeTitleScaffold
import com.dantech.skimbrowser.ui.components.SectionLabel
import com.dantech.skimbrowser.ui.components.SegmentedColumn
import com.dantech.skimbrowser.ui.components.SkimListItem
import com.dantech.skimbrowser.ui.components.SkimSwitch
import com.dantech.skimbrowser.ui.components.SunnyMark
import com.dantech.skimbrowser.ui.components.toggleableRow
import com.dantech.skimbrowser.ui.navigation.DeleteAiDataKey
import com.dantech.skimbrowser.ui.navigation.SkimNavigator
import kotlinx.coroutines.launch

/** AI & privacy (10). */
@Composable
fun AiPrivacyRoute(container: AppContainer, navigator: SkimNavigator) {
    val model = viewModel {
        AiPrivacyViewModel(
            container.settings,
            container.setAskBeforeSending,
            container.setKeepChatHistory,
            container.chatRepository,
        )
    }
    val state by model.state.collectAsStateWithLifecycle()
    AiPrivacyScreen(
        state = state,
        onBack = navigator::back,
        onAskBeforeSending = model::setAskBeforeSending,
        onKeepChatHistory = model::setKeepChatHistory,
        onSkimHistory = navigator::openSkimHistory,
        onDeleteAll = { navigator.push(DeleteAiDataKey) },
        onDataPolicy = BuildConfig.AI_DATA_POLICY_URL.takeIf { it.isNotBlank() }?.let { url ->
            { navigator.openNewTab(incognito = false, url = url) }
        },
    )
}

/** Delete all AI data (15). The delete runs on the app scope and the dialog closes at once. */
@Composable
fun DeleteAiDataRoute(container: AppContainer, navigator: SkimNavigator) {
    DeleteAiDataDialog(
        onDelete = {
            container.appScope.launch { container.aiDataEraser.deleteAllAiData() }
            navigator.back()
        },
        onDismiss = navigator::back,
    )
}

@Composable
fun AiPrivacyScreen(
    state: AiPrivacyUiState,
    onBack: () -> Unit,
    onAskBeforeSending: (Boolean) -> Unit,
    onKeepChatHistory: (Boolean) -> Unit,
    onSkimHistory: () -> Unit,
    onDeleteAll: () -> Unit,
    onDataPolicy: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    LargeTitleScaffold(
        title = stringResource(R.string.settings_ai_privacy),
        onBack = onBack,
        modifier = modifier,
    ) {
        item { SectionLabel(stringResource(R.string.settings_section_privacy), first = true) }
        item {
            SegmentedColumn {
                SwitchRow(
                    headline = stringResource(R.string.ai_settings_ask_before_sending),
                    supporting = stringResource(R.string.ai_settings_ask_before_sending_summary),
                    icon = R.drawable.ic_cloud_upload,
                    checked = state.askBeforeSending,
                    index = 0,
                    onCheckedChange = onAskBeforeSending,
                )
                SwitchRow(
                    headline = stringResource(R.string.ai_settings_keep_history),
                    supporting = stringResource(R.string.ai_settings_keep_history_summary),
                    icon = R.drawable.ic_history,
                    checked = state.keepChatHistory,
                    index = 1,
                    onCheckedChange = onKeepChatHistory,
                )
                SkimListItem(
                    headline = stringResource(R.string.skim_history_title),
                    supporting = pluralStringResource(
                        R.plurals.ai_settings_conversations,
                        state.conversationCount,
                        state.conversationCount,
                    ),
                    index = 2,
                    count = 3,
                    onClick = onSkimHistory,
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
                    onClick = onDeleteAll,
                    leading = { IconTile(R.drawable.ic_delete, colors.errorContainer, colors.onErrorContainer) },
                    headlineColor = colors.error,
                    headlineWeight = FontWeight.Medium,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.padding(start = 32.dp, top = 24.dp, end = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.ai_settings_footer),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.ai_settings_data_link),
                        modifier = if (onDataPolicy != null) Modifier.clickable(onClick = onDataPolicy) else Modifier,
                        color = if (onDataPolicy != null) colors.primary else colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SwitchRow(
    headline: String,
    supporting: String,
    @DrawableRes icon: Int,
    checked: Boolean,
    index: Int,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SkimListItem(
        headline = headline,
        supporting = supporting,
        index = index,
        count = 3,
        onClick = null,
        modifier = Modifier.toggleableRow(checked, onCheckedChange),
        leading = { IconTile(icon, colors.secondaryContainer, colors.onSecondaryContainer) },
        trailing = { SkimSwitch(checked = checked, onCheckedChange = null) },
    )
}
