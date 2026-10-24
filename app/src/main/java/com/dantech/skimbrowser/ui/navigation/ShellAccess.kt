package com.dantech.skimbrowser.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dantech.skimbrowser.di.ShellEntryPoint
import dagger.hilt.android.EntryPointAccessors

/** The process types the current composition may read. */
@Composable
fun rememberShell(): ShellEntryPoint {
    val app = LocalContext.current.applicationContext
    return remember(app) { shellOf(app) }
}

internal fun shellOf(app: Context): ShellEntryPoint =
    EntryPointAccessors.fromApplication(app, ShellEntryPoint::class.java)
