package com.dantech.skimbrowser

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

/**
 * History, bookmarks, Skim chats and cookies are promised to stay on this phone, so they must be
 * excluded from cloud backup and from device-to-device transfer.
 */
@RunWith(AndroidJUnit4::class)
class BackupRulesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val everyDomain = setOf("root", "file", "database", "sharedpref", "external")

    @Test
    fun mergedManifestTurnsBackupOff() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun mergedManifestAppliesTheBackupRules() {
        // Robolectric leaves the rule resource ids of ApplicationInfo unset, so read the merged
        // manifest that AGP hands to Robolectric. The sandbox class loader does not see AGP's test
        // config, the application class loader (the one Robolectric reads it with) does.
        val config = Properties().apply {
            val stream = ClassLoader.getSystemResourceAsStream("com/android/tools/test_config.properties")
                ?: error("test_config.properties is missing: unit tests need isIncludeAndroidResources")
            stream.use(::load)
        }
        val application = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File(config.getProperty("android_merged_manifest")))
            .getElementsByTagName("application")
            .item(0) as Element

        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(ANDROID_NS, "dataExtractionRules"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(ANDROID_NS, "fullBackupContent"))
    }

    @Test
    fun dataExtractionRulesExcludeEveryDomainFromCloudBackupAndDeviceTransfer() {
        assertEquals(
            mapOf("cloud-backup" to everyDomain, "device-transfer" to everyDomain),
            excludedDomains(R.xml.data_extraction_rules),
        )
    }

    @Test
    fun fullBackupContentExcludesEveryDomain() {
        assertEquals(mapOf("full-backup-content" to everyDomain), excludedDomains(R.xml.backup_rules))
    }

    /** The domains excluded as a whole (`path="."`) in each section of a backup rules file. */
    private fun excludedDomains(xmlRes: Int): Map<String, Set<String>> {
        val sections = linkedMapOf<String, MutableSet<String>>()
        var section: String? = null
        context.resources.getXml(xmlRes).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (val tag = parser.name) {
                    "full-backup-content", "cloud-backup", "device-transfer" -> {
                        section = tag
                        sections.getOrPut(tag) { mutableSetOf() }
                    }
                    "exclude" -> {
                        val current = section ?: error("<exclude> outside of a section")
                        assertEquals("path of an exclude in $current", ".", parser.getAttributeValue(null, "path"))
                        sections.getValue(current) += parser.getAttributeValue(null, "domain")
                    }
                    "include" -> fail("<include> in $section would let data leave the phone")
                }
            }
        }
        return sections
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
