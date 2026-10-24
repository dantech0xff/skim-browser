package com.dantech.skimbrowser.browser

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Device tests launch this and read the one activity-scoped host. It does not destroy that host. */
@AndroidEntryPoint
class BrowserHostActivity : ComponentActivity() {
    @Inject lateinit var browserHost: BrowserHost
}
