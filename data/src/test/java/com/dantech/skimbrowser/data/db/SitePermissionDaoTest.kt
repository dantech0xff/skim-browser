package com.dantech.skimbrowser.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.dantech.skimbrowser.data.db.dao.SitePermissionDao
import com.dantech.skimbrowser.data.db.entity.PermissionState
import com.dantech.skimbrowser.data.db.entity.SitePermissionEntity
import com.dantech.skimbrowser.data.db.entity.SitePermissionKind
import com.dantech.skimbrowser.data.db.entity.SkimAiState
import com.dantech.skimbrowser.testing.awaitItemMatching
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import com.dantech.skimbrowser.testing.longQuery
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SitePermissionDaoTest {

    private lateinit var db: SkimDatabase
    private lateinit var dao: SitePermissionDao

    @Before
    fun setUp() {
        db = inMemorySkimDatabase()
        dao = db.sitePermissionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deleteAllSkimAiDataDropsAiOnlyRowsAndKeepsOtherDecisions() = runTest {
        dao.setSkimAi("aionly.example", SkimAiState.ALLOWED, now = 1)
        dao.setSkimAi("daylightjournal.com", SkimAiState.ASK, now = 1)
        dao.setPermission("daylightjournal.com", SitePermissionKind.LOCATION, PermissionState.BLOCKED, now = 1)
        dao.setPermission("camera.example", SitePermissionKind.CAMERA, PermissionState.ALLOWED, now = 1)

        dao.deleteAllSkimAiData(now = 9)

        assertNull(dao.get("aionly.example"))
        assertEquals(
            SitePermissionEntity(host = "daylightjournal.com", location = PermissionState.BLOCKED, updatedAt = 9),
            dao.get("daylightjournal.com"),
        )
        assertEquals(
            SitePermissionEntity(host = "camera.example", camera = PermissionState.ALLOWED, updatedAt = 1),
            dao.get("camera.example"),
        )
        assertEquals(2L, db.longQuery("SELECT COUNT(*) FROM site_permissions"))
    }

    @Test
    fun aHostHasOneRowThatEachSetterUpdates() = runTest {
        dao.setSkimAi("daylightjournal.com", SkimAiState.ALLOWED, now = 1)
        dao.setPermission("daylightjournal.com", SitePermissionKind.MICROPHONE, PermissionState.BLOCKED, now = 2)
        dao.setSkimAi("daylightjournal.com", SkimAiState.ASK, now = 3)

        assertEquals(
            SitePermissionEntity(host = "daylightjournal.com", skimAi = SkimAiState.ASK, microphone = PermissionState.BLOCKED, updatedAt = 3),
            dao.get("daylightjournal.com"),
        )
        assertEquals(1L, db.longQuery("SELECT COUNT(*) FROM site_permissions"))
    }

    @Test
    fun clearingTheLastDecisionDeletesTheRow() = runTest {
        dao.setPermission("daylightjournal.com", SitePermissionKind.LOCATION, PermissionState.BLOCKED, now = 1)
        dao.setPermission("daylightjournal.com", SitePermissionKind.LOCATION, PermissionState.UNSET, now = 2)
        dao.setSkimAi("never.example", SkimAiState.UNSET, now = 3)

        assertEquals(0L, db.longQuery("SELECT COUNT(*) FROM site_permissions"))
    }

    @Test
    fun getAllReturnsOnlyStoredHosts() = runTest {
        dao.setSkimAi("a.example", SkimAiState.ALLOWED, now = 1)
        dao.setSkimAi("b.example", SkimAiState.ASK, now = 1)

        val stored = dao.getAll(listOf("a.example", "b.example", "c.example")).associate { it.host to it.skimAi }

        assertEquals(mapOf("a.example" to SkimAiState.ALLOWED, "b.example" to SkimAiState.ASK), stored)
    }

    @Test
    fun observeFollowsTheRowOfOneHost() = runTest {
        dao.observe("daylightjournal.com").test {
            assertNull(awaitItem())
            dao.setSkimAi("daylightjournal.com", SkimAiState.ALLOWED, now = 1)
            awaitItemMatching { it?.skimAi == SkimAiState.ALLOWED }
            dao.setSkimAi("other.example", SkimAiState.ALLOWED, now = 2)
            dao.setSkimAi("daylightjournal.com", SkimAiState.UNSET, now = 3)
            awaitItemMatching { it == null }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
