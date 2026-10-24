package com.dantech.skimbrowser

import androidx.activity.ComponentActivity
import com.dantech.skimbrowser.browser.BrowserHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Hosts route tests that call `hiltViewModel`. The activity graph supplies the browser host. */
@AndroidEntryPoint
class HiltRouteActivity : ComponentActivity() {
    @Inject lateinit var host: BrowserHost
}
