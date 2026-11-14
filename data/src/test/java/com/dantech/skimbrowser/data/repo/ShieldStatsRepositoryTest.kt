package com.dantech.skimbrowser.data.repo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.skimbrowser.domain.shield.BlockedHost
import com.dantech.skimbrowser.domain.shield.ShieldDelta
import com.dantech.skimbrowser.testing.inMemorySkimDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShieldStatsRepositoryTest {

    private val database = inMemorySkimDatabase()
    private val repository = ShieldStatsRepository(database)

    @After
    fun close() {
        database.close()
    }

    @Test
    fun aSecondAddAccumulatesAndResetRemovesIt() = runTest {
        repository.add(
            listOf(
                ShieldDelta(
                    epochDay = 5,
                    requests = 2,
                    popups = 1,
                    cosmetic = 3,
                    bytes = 100,
                    hosts = mapOf("ads.example" to 2),
                ),
            ),
        )
        repository.add(
            listOf(
                ShieldDelta(
                    epochDay = 5,
                    requests = 1,
                    redirects = 1,
                    bytes = 50,
                    hosts = mapOf("ads.example" to 1, "tracker.example" to 4),
                ),
            ),
        )
        val stored = repository.observe(5).first()
        assertEquals(3L, stored.today.requests)
        assertEquals(1L, stored.today.popups)
        assertEquals(1L, stored.today.redirects)
        assertEquals(3L, stored.today.cosmetic)
        assertEquals(150L, stored.today.bytes)
        assertEquals(
            listOf(BlockedHost("tracker.example", 4), BlockedHost("ads.example", 3)),
            stored.topHosts,
        )
        repository.clear()
        val cleared = repository.observe(5).first()
        assertEquals(0L, cleared.allTime.requests)
        assertTrue(cleared.topHosts.isEmpty())
    }

    @Test
    fun anEmptyAddDoesNotWrite() = runTest {
        repository.add(emptyList())
        assertEquals(0L, repository.observe(1).first().allTime.requests)
    }
}
