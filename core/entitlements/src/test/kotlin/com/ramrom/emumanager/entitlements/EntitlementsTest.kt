package com.ramrom.emumanager.entitlements

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsTest {
    @Test fun keyShape() {
        assertTrue(looksLikeLicenseKey("4F2A6C1E-1B2C-4D5E-8F90-A1B2C3D49C1E"))
        assertTrue(looksLikeLicenseKey("  4f2a6c1e-1b2c-4d5e-8f90-a1b2c3d49c1e \n"))
        assertFalse(looksLikeLicenseKey("4F2A6C1E-1B2C-4D5E-8F90"))
        assertFalse(looksLikeLicenseKey("hello"))
        assertFalse(looksLikeLicenseKey(null))
    }

    @Test fun fakeUnlocks() = runTest {
        val e = FakeEntitlements()
        assertFalse(e.has(Feature.Customize))
        assertEquals(ActivationResult.NotRecognised, e.activate("00000000-0000-0000-0000-000000000000"))
        assertTrue(e.activate("4F2A6C1E-1B2C-4D5E-8F90-A1B2C3D49C1E") is ActivationResult.Activated)
        assertTrue(e.has(Feature.Customize))
    }
}
