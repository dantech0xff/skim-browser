package com.dantech.skimbrowser

import com.dantech.skimbrowser.data.di.ShortcutCatalogModule
import com.dantech.skimbrowser.domain.shortcut.ShortcutCatalog
import com.dantech.skimbrowser.domain.shortcut.ShortcutPreset
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unit tests keep an empty shortcut list, so Home cases that start from nothing stay that way.
 * The bundled presets are covered in `:data`.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ShortcutCatalogModule::class])
abstract class TestShortcutCatalogModule {
    @Binds abstract fun catalog(empty: EmptyShortcutCatalog): ShortcutCatalog
}

@Singleton
class EmptyShortcutCatalog @Inject constructor() : ShortcutCatalog {
    override suspend fun presets(): List<ShortcutPreset> = emptyList()
}
