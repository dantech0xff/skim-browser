package com.dantech.skimbrowser.domain.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationLanguagesTest {

    @Test
    fun theStoredTagWinsWhenItIsALanguageThisPageCanTarget() {
        val cases = listOf(
            Triple("fr", "en-US", "fr"),
            Triple("FR", "en-US", "fr"),
            Triple("zh-Hant", "en-US", "zh"),
            Triple("pt_BR", "en", "pt"),
        )

        cases.forEach { (stored, device, expected) ->
            assertEquals(expected, TranslationLanguages.resolve(stored, device))
        }
    }

    @Test
    fun noStoredChoiceUsesThePhoneLanguageThenEnglish() {
        val cases = listOf(
            Triple(null, "vi-VN", "vi"),
            Triple("", "ja_JP", "ja"),
            Triple("xx", "en-GB", "en"),
            Triple(null, "zz-ZZ", "en"),
            Triple("not a tag", "", "en"),
        )

        cases.forEach { (stored, device, expected) ->
            assertEquals("$stored / $device", expected, TranslationLanguages.resolve(stored, device))
        }
    }

    @Test
    fun aTagOutsideTheCatalogIsNotStored() {
        assertNull(TranslationLanguages.canonical("la"))
        assertNull(TranslationLanguages.canonical("e"))
        assertNull(TranslationLanguages.canonical("12"))
        assertEquals("en", TranslationLanguages.canonical("en-US"))
    }
}
