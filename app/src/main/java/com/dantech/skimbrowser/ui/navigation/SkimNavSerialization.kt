package com.dantech.skimbrowser.ui.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * How the back stack is saved: every [SkimKey] is listed with its generated serializer, so
 * restoring a key never looks a class up by name and keeps working once R8 has shrunk the app.
 * A key missing here cannot be saved; the key serialization test fails for it.
 */
val skimNavConfig: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(WelcomeKey::class, WelcomeKey.serializer())
            subclass(SetupKey::class, SetupKey.serializer())
            subclass(BrowserKey::class, BrowserKey.serializer())
            subclass(ReaderKey::class, ReaderKey.serializer())
            subclass(TabsKey::class, TabsKey.serializer())
            subclass(SettingsKey::class, SettingsKey.serializer())
            subclass(AiPrivacyKey::class, AiPrivacyKey.serializer())
            subclass(HistoryKey::class, HistoryKey.serializer())
            subclass(BookmarksKey::class, BookmarksKey.serializer())
            subclass(SkimHistoryKey::class, SkimHistoryKey.serializer())
            subclass(LicensesKey::class, LicensesKey.serializer())
            subclass(MoreMenuKey::class, MoreMenuKey.serializer())
            subclass(LinkOptionsKey::class, LinkOptionsKey.serializer())
            subclass(SiteInfoKey::class, SiteInfoKey.serializer())
            subclass(AskPageKey::class, AskPageKey.serializer())
            subclass(AskTabsKey::class, AskTabsKey.serializer())
            subclass(AiPermissionKey::class, AiPermissionKey.serializer())
            subclass(ShortcutDialogKey::class, ShortcutDialogKey.serializer())
            subclass(SearchEngineDialogKey::class, SearchEngineDialogKey.serializer())
            subclass(ThemeDialogKey::class, ThemeDialogKey.serializer())
            subclass(ClearBrowsingDataKey::class, ClearBrowsingDataKey.serializer())
            subclass(DeleteAiDataKey::class, DeleteAiDataKey.serializer())
            subclass(ClearSkimHistoryKey::class, ClearSkimHistoryKey.serializer())
            subclass(RenameGroupKey::class, RenameGroupKey.serializer())
            subclass(EditBookmarkKey::class, EditBookmarkKey.serializer())
            subclass(MoveToFolderKey::class, MoveToFolderKey.serializer())
        }
    }
}
