package com.dantech.skimbrowser.ui.browsing

import androidx.lifecycle.ViewModel
import com.dantech.skimbrowser.domain.translate.PageTranslator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PageTranslationViewModel @Inject constructor(
    private val translator: PageTranslator,
) : ViewModel() {
    val sessions = translator.sessions

    fun translate(tabId: String, deviceLanguageTag: String) = translator.translate(tabId, deviceLanguageTag)

    fun showOriginal(tabId: String, original: Boolean) = translator.showOriginal(tabId, original)

    fun dismiss(tabId: String) = translator.dismiss(tabId)

    fun allow(tabId: String, host: String, deviceLanguageTag: String) = translator.allow(tabId, host, deviceLanguageTag)

    fun onDocument(tabId: String, loadNonce: Long) = translator.onDocument(tabId, loadNonce)
}
