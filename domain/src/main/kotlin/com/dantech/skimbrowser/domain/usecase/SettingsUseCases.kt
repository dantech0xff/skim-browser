package com.dantech.skimbrowser.domain.usecase

import com.dantech.skimbrowser.domain.settings.ReaderFont
import com.dantech.skimbrowser.domain.settings.SearchEngine
import com.dantech.skimbrowser.domain.settings.SettingsRepository
import com.dantech.skimbrowser.domain.settings.ThemeSetting

class SetSearchEngine(private val settings: SettingsRepository) {
    suspend operator fun invoke(engine: SearchEngine) = settings.setSearchEngine(engine)
}

class SetTheme(private val settings: SettingsRepository) {
    suspend operator fun invoke(theme: ThemeSetting) = settings.setTheme(theme)
}

class SetAskBeforeSending(private val settings: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = settings.setAskBeforeSending(enabled)
}

class SetKeepChatHistory(private val settings: SettingsRepository) {
    suspend operator fun invoke(enabled: Boolean) = settings.setKeepChatHistory(enabled)
}

class SetReaderTextScale(private val settings: SettingsRepository) {
    suspend operator fun invoke(percent: Int) = settings.setReaderTextScale(percent)
}

class SetReaderFont(private val settings: SettingsRepository) {
    suspend operator fun invoke(font: ReaderFont) = settings.setReaderFont(font)
}

class CompleteOnboarding(private val settings: SettingsRepository) {
    suspend operator fun invoke() = settings.setOnboardingCompleted()
}
