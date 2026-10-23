package com.dantech.skimbrowser.ui.browsing

import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dantech.skimbrowser.R
import com.dantech.skimbrowser.browser.permissions.PendingSitePermission
import com.dantech.skimbrowser.browser.permissions.SitePermissionBridge
import com.dantech.skimbrowser.data.db.entity.SitePermissionKind
import com.dantech.skimbrowser.ui.components.DialogAction
import com.dantech.skimbrowser.ui.components.SkimAlertDialog
import com.dantech.skimbrowser.ui.theme.SkimTheme
import com.dantech.skimbrowser.ui.theme.ThemeSetting

/**
 * The site permission request of tab [tabId] that waits for the user, from the Activity's [bridge],
 * as the dialog of 15; nothing while there is none. A request of another tab never shows here, even
 * for the moment the bridge still holds it after the tab changed.
 *
 * Every answer goes to the bridge with the request the dialog shows, never one that arrived since:
 * Allow keeps the decision for the site, Block keeps the refusal, and Back or a tap on the scrim
 * refuses this time only, keeping nothing.
 */
@Composable
fun SitePermissionRequests(tabId: String, bridge: SitePermissionBridge) {
    val pending by bridge.pending.collectAsStateWithLifecycle()
    val request = pending?.takeIf { it.tabId == tabId } ?: return
    key(request) {
        SitePermissionDialog(
            pending = request,
            onAllow = { bridge.allow(request, remember = true) },
            onBlock = { bridge.block(request) },
            onDismiss = { bridge.dismiss(request) },
        )
    }
}

/**
 * Site permission (15): "Share your location with <site>?" with the location pin in primary over a
 * centered title, or the camera and microphone titles without an icon (the design's icon set has no
 * camera); the same body for every kind, then Block and Allow. Its window is the main window's own
 * dialog, over the page.
 *
 * @param onDismiss Back or a tap outside the dialog.
 */
@Composable
fun SitePermissionDialog(
    pending: PendingSitePermission,
    onAllow: () -> Unit,
    onBlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val asksLocation = SitePermissionKind.LOCATION in pending.kinds
    SkimAlertDialog(
        title = stringResource(sitePermissionTitleOf(pending.kinds), pending.host),
        confirm = DialogAction(stringResource(R.string.action_allow), onAllow),
        onDismissRequest = onDismiss,
        dismiss = DialogAction(stringResource(R.string.action_block), onBlock),
        icon = if (asksLocation) {
            { Icon(painterResource(R.drawable.ic_location_on), contentDescription = null) }
        } else {
            null
        },
        iconTint = MaterialTheme.colorScheme.primary,
        text = { Text(stringResource(R.string.site_permission_body)) },
    )
}

/** The title for a request of [kinds]: location alone, or the camera, the microphone or both. */
@StringRes
internal fun sitePermissionTitleOf(kinds: Set<SitePermissionKind>): Int {
    val camera = SitePermissionKind.CAMERA in kinds
    val microphone = SitePermissionKind.MICROPHONE in kinds
    return when {
        SitePermissionKind.LOCATION in kinds -> R.string.site_permission_location_title
        camera && microphone -> R.string.site_permission_camera_microphone_title
        camera -> R.string.site_permission_camera_title
        else -> R.string.site_permission_microphone_title
    }
}

@PreviewLightDark
@Composable
private fun SitePermissionDialogPreview() {
    SkimTheme(ThemeSetting.System, dynamicColor = false) {
        SitePermissionDialog(
            pending = PendingSitePermission("tab", "daylightjournal.com", setOf(SitePermissionKind.LOCATION), incognito = false),
            onAllow = {},
            onBlock = {},
            onDismiss = {},
        )
    }
}
