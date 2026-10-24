package com.dantech.skimbrowser.domain.usecase

import com.dantech.skimbrowser.domain.shortcut.Shortcut
import com.dantech.skimbrowser.domain.shortcut.ShortcutRepository

class AddShortcut(private val shortcuts: ShortcutRepository) {
    suspend operator fun invoke(name: String, url: String) = shortcuts.add(name, url)
}

class UpdateShortcut(private val shortcuts: ShortcutRepository) {
    suspend operator fun invoke(id: Long, name: String, url: String) = shortcuts.update(id, name, url)
}

class RemoveShortcut(private val shortcuts: ShortcutRepository) {
    suspend operator fun invoke(id: Long): Shortcut? = shortcuts.remove(id)
}

class RestoreShortcut(private val shortcuts: ShortcutRepository) {
    suspend operator fun invoke(shortcut: Shortcut) = shortcuts.restore(shortcut)
}
