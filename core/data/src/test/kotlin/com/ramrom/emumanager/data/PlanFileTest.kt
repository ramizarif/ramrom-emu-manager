package com.ramrom.emumanager.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for PlanFile encode/decode and version handling.
 */
class PlanFileTest {
    @Test
    fun testEncodeDecode() {
        val plan = PlanFile(
            formatVersion = 1,
            appVersion = "1.0.0",
            createdAt = 1000L,
            targetDeviceProfileId = "ayn-thor",
            frontendId = "es-de",
            systems = listOf(
                PlanSystem(systemId = "nes", emulatorId = "nestopia"),
                PlanSystem(systemId = "snes", emulatorId = "snes9x")
            ),
            biosCheck = true
        )

        val encoded = PlanFile.encode(plan)
        val decoded = PlanFile.decode(encoded)

        assertTrue(decoded.isSuccess)
        assertEquals(plan.appVersion, decoded.getOrNull()?.appVersion)
        assertEquals(plan.targetDeviceProfileId, decoded.getOrNull()?.targetDeviceProfileId)
        assertEquals(2, decoded.getOrNull()?.systems?.size)
        assertEquals("nes", decoded.getOrNull()?.systems?.get(0)?.systemId)
    }

    @Test
    fun testNewerVersionRejected() {
        val json = """
            {
                "formatVersion": 2,
                "appVersion": "2.0.0",
                "createdAt": 1000,
                "targetDeviceProfileId": "ayn-thor",
                "systems": [],
                "biosCheck": false
            }
        """.trimIndent()

        val result = PlanFile.decode(json)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is PlanFileVersionError)
        assertTrue((error as? PlanFileVersionError)?.message?.contains("formatVersion 2") == true)
        assertTrue((error as? PlanFileVersionError)?.message?.contains("appVersion 2.0.0") == true)
    }

    @Test
    fun testInvalidJsonFails() {
        val result = PlanFile.decode("{ broken json }")
        assertTrue(result.isFailure)
    }
}
