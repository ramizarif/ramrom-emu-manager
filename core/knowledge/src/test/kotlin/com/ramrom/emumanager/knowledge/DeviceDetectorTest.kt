package com.ramrom.emumanager.knowledge

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DeviceDetectorTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun matchThor() {
        val bundle = loadBundle()
        val info = DeviceInfo(
            model = "AYN Thor",
            device = "kalama",
            socModel = "QCS8550",
            hasGamepad = true,
            apiLevel = 33
        )
        val result = DeviceDetector.match(info, bundle)
        assertEquals("ayn-thor", result.deviceId)
        assertEquals(DeviceVerification.VERIFIED, result.profile!!.verification)
        assertEquals(MatchKind.MODEL, result.matchKind)
        assertTrue(result.isVerified)
    }

    @Test
    fun matchRP5ByModel() {
        val bundle = loadBundle()
        val info = DeviceInfo(
            model = "Retroid Pocket 5",
            socModel = "SM8550",
            hasGamepad = true,
            apiLevel = 33
        )
        val result = DeviceDetector.match(info, bundle)
        assertEquals("retroid-pocket-5", result.deviceId)
        assertEquals(MatchKind.MODEL, result.matchKind)
    }

    @Test
    fun matchOdin3() {
        val bundle = loadBundle()
        val info = DeviceInfo(
            model = "AYN Odin 3",
            socModel = "SM8750",
            hasGamepad = true,
            apiLevel = 33
        )
        val result = DeviceDetector.match(info, bundle)
        assertEquals("ayn-odin-3", result.deviceId)
        assertEquals("sd8elite", result.profile!!.chipFamily)
    }

    @Test
    fun unknownGen2HandheldIsBestGuessNotThor() {
        val bundle = loadBundle()
        // Same platform codename and SoC as the Thor, different model.
        val info = DeviceInfo(model = "KT-R1", device = "kalama", socModel = "SM8550", hasGamepad = true, apiLevel = 33)
        val result = DeviceDetector.match(info, bundle)
        assertNotEquals("ayn-thor", result.deviceId)
        assertEquals(MatchKind.FAMILY, result.matchKind)
        assertEquals("sd8gen2", result.chipFamily?.id)
        assertFalse(result.isVerified)
        assertFalse(result.isPhoneDetected)
    }

    @Test
    fun thorSkuSocMapsToGen2() {
        val bundle = loadBundle()
        val info = DeviceInfo(model = "Mystery", socModel = "QCS8550", hasGamepad = true, apiLevel = 33)
        assertEquals("sd8gen2", DeviceDetector.match(info, bundle).chipFamily?.id)
    }

    @Test
    fun matchPixelAsPhone() {
        val bundle = loadBundle()
        val info = DeviceInfo(
            model = "Pixel 6",
            hasGamepad = false,
            apiLevel = 33
        )
        val result = DeviceDetector.match(info, bundle)
        // No exact match, no gamepad → phone detection
        assertTrue(result.isPhoneDetected)
        // Should still have generic fallback
        assertNotNull(result.profile)
        assertEquals("generic-android", result.profile!!.id)
    }

    @Test
    fun unmappedDeviceFallsBackToGeneric() {
        val bundle = loadBundle()
        val info = DeviceInfo(
            model = "UnknownDevice",
            socModel = "UnknownSoC",
            hasGamepad = true,
            apiLevel = 33
        )
        val result = DeviceDetector.match(info, bundle)
        assertEquals("generic-android", result.deviceId)
        assertEquals(MatchKind.NONE, result.matchKind)
    }

    private fun loadBundle(): KnowledgeBundle {
        val path = "src/main/assets/knowledge/bundle-v1.json"
        val file = File(path)
        val data = if (file.exists()) {
            file.readText()
        } else {
            throw IllegalStateException("Bundle file not found at $path (run from repo root)")
        }
        return json.decodeFromString<KnowledgeBundle>(data)
    }
}
