package com.dantech.skimbrowser

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/** Debug and releaseSmoke share this runner. It installs [HiltTestApplication] for both. */
class SkimTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
