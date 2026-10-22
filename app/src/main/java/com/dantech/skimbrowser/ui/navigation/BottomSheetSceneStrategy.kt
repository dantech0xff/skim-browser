/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Adapted from the BottomSheet recipe of Navigation 3 (android/nav3-recipes): the sheet is Skim's
// SkimBottomSheet, whose state reaches the entry through LocalSkimSheetState.

package com.dantech.skimbrowser.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.dantech.skimbrowser.ui.components.SkimBottomSheet
import com.dantech.skimbrowser.ui.components.SkimSheetState
import com.dantech.skimbrowser.ui.components.rememberSkimSheetState

/**
 * The state of the sheet the current entry shows in, null outside a sheet. Ask about this page
 * (06) uses it to lower the sheet to half height when a citation is tapped.
 */
val LocalSkimSheetState = staticCompositionLocalOf<SkimSheetState?> { null }

/** How an entry shows as a sheet: TalkBack announces [paneTitle] when it opens. */
data class SheetProperties(@param:StringRes val paneTitle: Int, val skipPartiallyExpanded: Boolean)

/** An [OverlayScene] that shows [entry] in a [SkimBottomSheet]; closing the sheet goes back. */
internal data class BottomSheetScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val properties: SheetProperties,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable () -> Unit = {
        // The sheet opens a window of its own: the scene's lifecycle is carried into it.
        val lifecycleOwner = rememberLifecycleOwner()
        val state = rememberSkimSheetState(properties.skipPartiallyExpanded)
        SkimBottomSheet(onDismiss = onBack, paneTitle = stringResource(properties.paneTitle), state = state) {
            CompositionLocalProvider(
                LocalLifecycleOwner provides lifecycleOwner,
                LocalSkimSheetState provides state,
            ) {
                entry.Content()
            }
        }
    }
}

/**
 * A [SceneStrategy] that shows an entry with [bottomSheet] in its metadata in a [SkimBottomSheet]
 * over the entries below it. Swiping the sheet down, tapping the scrim and Back close it.
 *
 * List it before any strategy that is not an overlay.
 */
class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val properties = lastEntry.metadata[BottomSheetKey] ?: return null
        return BottomSheetScene(
            key = lastEntry.contentKey,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = lastEntry,
            properties = properties,
            onBack = onBack,
        )
    }

    companion object {
        /**
         * Metadata that shows an entry as a sheet titled [paneTitle]. It opens at full height, or with
         * [skipPartiallyExpanded] false at half height, draggable to full height (06).
         */
        fun bottomSheet(@StringRes paneTitle: Int, skipPartiallyExpanded: Boolean = true): Map<String, Any> =
            metadata { put(BottomSheetKey, SheetProperties(paneTitle, skipPartiallyExpanded)) }

        object BottomSheetKey : NavMetadataKey<SheetProperties>
    }
}
