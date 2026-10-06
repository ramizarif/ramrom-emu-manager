package com.ramrom.emumanager.access

import org.junit.Test
import kotlin.test.assertEquals

/**
 * JVM unit tests for AccessManager state machine (pure function).
 */
class AccessManagerStateMachineTest {

    @Test
    fun notSetUpWhenNothingPersisted() {
        val mode = AccessManager.computeAccessMode(null, true, true)
        assertEquals(AccessMode.NotSetUp, mode)
    }

    @Test
    fun limitedWhenUserChooseLimited() {
        val mode = AccessManager.computeAccessMode("limited", true, true)
        assertEquals(AccessMode.Limited, mode)
    }

    @Test
    fun elevatedWhenPersistedElevatedAndBinderAliveAndPermissionGranted() {
        val mode = AccessManager.computeAccessMode("elevated", true, true)
        assertEquals(AccessMode.Elevated, mode)
    }

    @Test
    fun disconnectedWhenPersistedElevatedButBinderNotAlive() {
        val mode = AccessManager.computeAccessMode("elevated", false, true)
        assertEquals(AccessMode.Disconnected, mode)
    }

    @Test
    fun disconnectedWhenPersistedElevatedButPermissionNotGranted() {
        val mode = AccessManager.computeAccessMode("elevated", true, false)
        assertEquals(AccessMode.Disconnected, mode)
    }

    @Test
    fun disconnectedWhenPersistedElevatedButBothMissing() {
        val mode = AccessManager.computeAccessMode("elevated", false, false)
        assertEquals(AccessMode.Disconnected, mode)
    }

    @Test
    fun limitedStaysLimitedEvenIfBinderDead() {
        val mode = AccessManager.computeAccessMode("limited", false, true)
        assertEquals(AccessMode.Limited, mode)
    }

    @Test
    fun limitedStaysLimitedEvenIfPermissionDenied() {
        val mode = AccessManager.computeAccessMode("limited", true, false)
        assertEquals(AccessMode.Limited, mode)
    }

    @Test
    fun notSetUpWhenUnknownChoice() {
        val mode = AccessManager.computeAccessMode("unknown", true, true)
        assertEquals(AccessMode.NotSetUp, mode)
    }

    @Test
    fun elevatedWhenPersistedElevatedWithAllConditions() {
        val mode = AccessManager.computeAccessMode("elevated", true, true)
        assertEquals(AccessMode.Elevated, mode)
    }
}
