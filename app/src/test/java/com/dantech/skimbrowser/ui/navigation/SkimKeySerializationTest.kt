package com.dantech.skimbrowser.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.browser.HitTarget
import kotlin.reflect.KClass
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Every key survives being saved, the way the back stack is saved for process death. */
@RunWith(AndroidJUnit4::class)
class SkimKeySerializationTest {

    private val json = Json { serializersModule = skimNavConfig.serializersModule }
    private val navKey = PolymorphicSerializer(NavKey::class)

    @Test
    fun everyKeyRoundTripsAsANavKey() {
        for (key in everyKey) {
            val encoded = json.encodeToString(navKey, key)
            assertEquals(encoded, key, json.decodeFromString(navKey, encoded))
        }
    }

    @Test
    fun linkOptionsKeepTheLinkOrImageThatWasPressed() {
        val link = LinkOptionsKey("tab-1", HitTarget.Link("https://example.com/a?b=c#d", text = "Read \"more\""))
        val image = LinkOptionsKey("tab-1", HitTarget.Image("data:image/png;base64,iVBORw0KGgo=", alt = null, linkUrl = "https://example.com/"))

        for (key in listOf(link, image)) {
            assertEquals(key, json.decodeFromString(navKey, json.encodeToString(navKey, key)))
        }
    }

    @Test
    fun aBackStackOfEveryKeyRoundTripsThroughSavedState() {
        val serializer = NavBackStackSerializer(navKey)
        val backStack = NavBackStack<NavKey>(*everyKey.toTypedArray())

        val saved = encodeToSavedState(serializer, backStack, skimNavConfig)
        val restored = decodeFromSavedState(serializer, saved, skimNavConfig)

        assertEquals(everyKey, restored.toList())
    }

    @Test
    fun theSavedStateConfigurationListsEverySkimKey() {
        assertEquals(everySkimKey(), registeredNavKeys().keys)
    }

    @Test
    fun theRoundTripsCoverEveryKey() {
        assertEquals(registeredNavKeys().values.toSet(), everyKey.map { it::class }.toSet())
    }

    /**
     * The serial name of every class that can be a [SkimKey], sheets and dialogs included, as the
     * serialization plugin lists them for the sealed hierarchy when it compiles it.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun everySkimKey(): Set<String> = serializer<SkimKey>().descriptor.getElementDescriptor(1).elementNames.toSet()

    /** The keys [skimNavConfig] can save, by serial name. */
    @OptIn(ExperimentalSerializationApi::class)
    private fun registeredNavKeys(): Map<String, KClass<*>> {
        val registered = mutableMapOf<String, KClass<*>>()
        skimNavConfig.serializersModule.dumpTo(
            object : SerializersModuleCollector {
                override fun <T : Any> contextual(kClass: KClass<T>, provider: (List<KSerializer<*>>) -> KSerializer<*>) = Unit

                override fun <Base : Any, Sub : Base> polymorphic(
                    baseClass: KClass<Base>,
                    actualClass: KClass<Sub>,
                    actualSerializer: KSerializer<Sub>,
                ) {
                    if (baseClass == NavKey::class) registered[actualSerializer.descriptor.serialName] = actualClass
                }

                override fun <Base : Any> polymorphicDefaultSerializer(
                    baseClass: KClass<Base>,
                    defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
                ) = Unit

                override fun <Base : Any> polymorphicDefaultDeserializer(
                    baseClass: KClass<Base>,
                    defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
                ) = Unit
            },
        )
        return registered
    }

    private companion object {
        val everyKey: List<SkimKey> = listOf(
            WelcomeKey,
            SetupKey,
            BrowserKey,
            ReaderKey("tab-1"),
            TabsKey(incognito = true, focusTabId = "tab-2"),
            SettingsKey,
            AiPrivacyKey,
            HistoryKey,
            BookmarksKey(folderId = 42),
            SkimHistoryKey,
            LicensesKey,
            MoreMenuKey("tab-1"),
            LinkOptionsKey("tab-1", HitTarget.Image("https://example.com/cat.png", alt = "A cat", linkUrl = null)),
            SiteInfoKey("tab-1"),
            AskPageKey(tabId = "tab-1", savedChatId = 7, fromReader = true),
            AskTabsKey(prompt = "Summarize my open tabs", savedChatId = null),
            AiPermissionKey("tab-1", fromReader = true),
            ShortcutDialogKey(shortcutId = 3),
            SearchEngineDialogKey,
            ThemeDialogKey,
            ClearBrowsingDataKey,
            DeleteAiDataKey,
            ClearSkimHistoryKey,
            RenameGroupKey(groupId = 5),
            EditBookmarkKey(bookmarkId = 11),
            MoveToFolderKey(bookmarkId = 12),
        )
    }
}
