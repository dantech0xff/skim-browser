package com.dantech.skimbrowser.ui.shell

import androidx.compose.runtime.staticCompositionLocalOf
import com.dantech.skimbrowser.domain.browsing.OpenPages

/**
 * The activity's open pages. Clear browsing data reads this. Reading it outside the activity
 * content is an error.
 */
val LocalOpenPages = staticCompositionLocalOf<OpenPages> { error("Open pages are not provided") }
