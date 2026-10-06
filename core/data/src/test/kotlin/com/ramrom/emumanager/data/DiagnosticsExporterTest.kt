package com.ramrom.emumanager.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

/**
 * Tests for DiagnosticsExporter redaction.
 * Pure redaction logic, no Android runtime required.
 */
class DiagnosticsExporterTest {
    @Test
    fun testRedactRomPath() {
        val exporter = DiagnosticsExporter(File("/tmp"), includeGameFileNames = false)

        val path = "/sdcard/ROMs/nes/SuperMarioBros.nes"
        val redacted = exporter.redactPath(path)

        assertEquals("/sdcard/ROMs/nes/<n files>", redacted)
    }

    @Test
    fun testRedactRomPathCaseInsensitive() {
        val exporter = DiagnosticsExporter(File("/tmp"), includeGameFileNames = false)

        val path1 = "/sdcard/ROMs/nes/game.nes"
        val path2 = "/sdcard/roms/nes/game.nes"
        val path3 = "/sdcard/Roms/nes/game.nes"

        val redacted1 = exporter.redactPath(path1)
        val redacted2 = exporter.redactPath(path2)
        val redacted3 = exporter.redactPath(path3)

        // All should contain the redacted pattern
        assertTrue(redacted1.contains("ROMs/nes/<n files>"))
        assertTrue(redacted2.contains("ROMs/nes/<n files>"))
        assertTrue(redacted3.contains("ROMs/nes/<n files>"))
    }

    @Test
    fun testRedactRomPathWithFileNames() {
        val exporter = DiagnosticsExporter(File("/tmp"), includeGameFileNames = true)

        val path = "/sdcard/ROMs/snes/AlinkToThePast.smc"
        val redacted = exporter.redactPath(path)

        assertTrue(redacted.contains("ROMs/snes/AlinkToThePast.smc"))
    }

    @Test
    fun testRedactMultipleSegments() {
        val exporter = DiagnosticsExporter(File("/tmp"), includeGameFileNames = false)

        val path = "/sdcard/ROMs/switch/game.nsp"
        val redacted = exporter.redactPath(path)

        assertTrue(redacted.contains("ROMs/switch/<n files>"))
    }

    @Test
    fun testNonRomPathUntouched() {
        val exporter = DiagnosticsExporter(File("/tmp"), includeGameFileNames = false)

        val path = "/data/local/tmp/cache.txt"
        val redacted = exporter.redactPath(path)

        assertEquals(path, redacted)
    }

    @Test
    fun testBuildRedactedBundle() {
        val exporter = DiagnosticsExporter(File("/tmp"), includeGameFileNames = false)

        val data = mapOf(
            "ROM Folder" to "/sdcard/ROMs/nes/game.nes",
            "Config" to "/etc/config.txt"
        )

        val bundle = exporter.buildRedactedBundle(data)

        assertTrue(bundle.contains("ROM Folder"))
        assertTrue(bundle.contains("ROMs/nes/<n files>"))
        assertTrue(bundle.contains("/etc/config.txt"))
    }
}
