package com.dantech.skimbrowser.data.di

import com.dantech.skimbrowser.data.shortcut.BundledShortcutCatalog
import com.dantech.skimbrowser.domain.shortcut.ShortcutCatalog
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The bundled shortcut file. Tests replace this module; a remote config would too. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ShortcutCatalogModule {
    @Binds abstract fun catalog(bundled: BundledShortcutCatalog): ShortcutCatalog
}
