package com.dantech.skimbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TabEngineBridgeTest {

    private val bridge = TabEngineBridge()
    private val changes = mutableListOf<Pair<TabEngine?, TabEngine?>>()

    init {
        bridge.addListener { previous, current -> changes += previous to current }
    }

    @Test
    fun nothingIsAttachedBeforeAnActivityPlugsItsPoolIn() {
        assertNull(bridge.engine.value)
        assertFalse(bridge.isAttached(FakeTabEngine()))
    }

    @Test
    fun attachingMakesTheEngineCurrentAndTellsTheListenerAfterwards() {
        val engine = FakeTabEngine()
        var seenByListener: TabEngine? = null
        bridge.addListener { _, _ -> seenByListener = bridge.engine.value }

        bridge.attach(engine)

        assertSame(engine, bridge.engine.value)
        assertTrue(bridge.isAttached(engine))
        assertEquals(listOf<Pair<TabEngine?, TabEngine?>>(null to engine), changes)
        assertSame("the listener reads the new engine", engine, seenByListener)
    }

    @Test
    fun aNewerPoolReplacesTheOlderOneAndTheListenerHearsBoth() {
        val older = FakeTabEngine()
        val newer = FakeTabEngine()

        bridge.attach(older)
        bridge.attach(newer)

        assertTrue(bridge.isAttached(newer))
        assertFalse(bridge.isAttached(older))
        assertEquals(listOf(null to older, older to newer), changes)
    }

    @Test
    fun detachingTheReplacedPoolChangesNothing() {
        val older = FakeTabEngine()
        val newer = FakeTabEngine()
        bridge.attach(older)
        bridge.attach(newer)
        changes.clear()

        bridge.detach(older)

        assertSame(newer, bridge.engine.value)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun detachingTheCurrentPoolLeavesNoEngine() {
        val engine = FakeTabEngine()
        bridge.attach(engine)
        changes.clear()

        bridge.detach(engine)

        assertNull(bridge.engine.value)
        assertFalse(bridge.isAttached(engine))
        assertEquals(listOf<Pair<TabEngine?, TabEngine?>>(engine to null), changes)
    }
}
