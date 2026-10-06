package com.ramrom.emumanager.engine

import com.ramrom.emumanager.knowledge.*
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class BiosIdentifierTest {
    private fun makeBiosEntry(
        system: String,
        fileName: String,
        md5: String = "abc123def456",
        size: Long = 1024,
        emulatorId: String? = null,
        emulatorPath: String = "/bios/path"
    ): BiosEntry {
        val targets = if (emulatorId != null) {
            listOf(BiosTarget(emulatorId = emulatorId, path = emulatorPath, renameTo = fileName))
        } else {
            emptyList()
        }
        return BiosEntry(
            system = system,
            fileName = fileName,
            md5 = md5,
            sha1 = "def789abc123",
            size = size,
            variant = "rev1",
            required = true,
            targets = targets,
            source = BiosSource.NO_INTRO
        )
    }

    private fun makeBundle(bios: List<BiosEntry>): KnowledgeBundle {
        return KnowledgeBundle(
            schemaVersion = 1,
            dataVersion = "1.0",
            bios = bios,
            systems = listOf(
                SystemDef(id = "ps2", name = "PlayStation 2", biosRequired = true),
                SystemDef(id = "wii", name = "Nintendo Wii", biosRequired = true),
                SystemDef(id = "switch", name = "Nintendo Switch", biosRequired = false),
                SystemDef(id = "n3ds", name = "Nintendo 3DS", biosRequired = true)
            )
        )
    }

    @Test
    fun hashMatchIdentifiesBios() {
        val biosEntry = makeBiosEntry(
            system = "ps2",
            fileName = "ps2-bios.bin",
            md5 = "abc123def456",
            size = 1024L,
            emulatorId = "nethersx2"
        )
        val bundle = makeBundle(listOf(biosEntry))

        val candidate = CandidateFile(
            path = "/sdcard/bios/ps2-bios.bin",
            size = 1024L,
            md5 = { "abc123def456" }
        )

        val identifier = BiosIdentifier(bundle)
        val results = identifier.checkBios(
            listOf(candidate),
            listOf("ps2" to "nethersx2")
        )

        assertEquals(1, results.size)
        assertTrue(results[0].status is BiosCheckStatus.Found)
        val found = results[0].status as BiosCheckStatus.Found
        assertEquals("ps2-bios.bin", found.entry.fileName)
    }

    @Test
    fun sizeSkipsHashingWhenNoMatch() {
        val biosEntry = makeBiosEntry(
            system = "ps2",
            fileName = "ps2-bios.bin",
            size = 2048L,
            emulatorId = "nethersx2"
        )
        val bundle = makeBundle(listOf(biosEntry))

        var md5Called = false
        val candidate = CandidateFile(
            path = "/sdcard/bios/ps2-bios.bin",
            size = 1024L,  // Wrong size
            md5 = {
                md5Called = true
                "abc123def456"
            }
        )

        val identifier = BiosIdentifier(bundle)
        val results = identifier.checkBios(
            listOf(candidate),
            listOf("ps2" to "nethersx2")
        )

        assertFalse("MD5 should not be called when size doesn't match", md5Called)
        assertEquals(1, results.size)
        assertTrue(results[0].status is BiosCheckStatus.Missing)
    }

    @Test
    fun wrongHashDetectsVersionMismatch() {
        val biosEntry = makeBiosEntry(
            system = "ps2",
            fileName = "ps2-bios.bin",
            md5 = "correcthash123456",
            size = 1024L,
            emulatorId = "nethersx2"
        )
        val bundle = makeBundle(listOf(biosEntry))

        val candidate = CandidateFile(
            path = "/sdcard/bios/ps2-bios.bin",
            size = 1024L,
            md5 = { "wronghash7890abcd" }
        )

        val identifier = BiosIdentifier(bundle)
        val results = identifier.checkBios(
            listOf(candidate),
            listOf("ps2" to "nethersx2")
        )

        assertEquals(1, results.size)
        assertTrue(results[0].status is BiosCheckStatus.WrongVersion)
        val wrongVersion = results[0].status as BiosCheckStatus.WrongVersion
        assertTrue(wrongVersion.note.contains("expected"))
    }

    @Test
    fun emulatorWithoutTargetDoesntCount() {
        val biosEntry = makeBiosEntry(
            system = "ps2",
            fileName = "ps2-bios.bin",
            md5 = "abc123def456",
            size = 1024L,
            emulatorId = "nethersx2"  // Only targets nethersx2
        )
        val bundle = makeBundle(listOf(biosEntry))

        val candidate = CandidateFile(
            path = "/sdcard/bios/ps2-bios.bin",
            size = 1024L,
            md5 = { "abc123def456" }
        )

        val identifier = BiosIdentifier(bundle)
        val results = identifier.checkBios(
            listOf(candidate),
            listOf("ps2" to "duckstation")  // Different emulator
        )

        assertEquals(1, results.size)
        assertTrue(results[0].status is BiosCheckStatus.Missing)
    }

    @Test
    fun guidedOnlyForSystemsWithoutEntries() {
        val bundle = KnowledgeBundle(
            schemaVersion = 1,
            dataVersion = "1.0",
            bios = emptyList(),  // No BIOS entries
            systems = listOf(
                SystemDef(id = "n3ds", name = "Nintendo 3DS", biosRequired = true)
            )
        )

        val identifier = BiosIdentifier(bundle)
        val results = identifier.checkBios(
            emptyList(),
            listOf("n3ds" to "azahar")
        )

        assertEquals(1, results.size)
        assertEquals(BiosCheckStatus.GuidedOnly, results[0].status)
    }

    @Test
    fun buildStagePlanForFoundFiles() {
        val biosEntry = makeBiosEntry(
            system = "ps2",
            fileName = "ps2-bios.bin",
            md5 = "abc123def456",
            size = 1024L,
            emulatorId = "nethersx2",
            emulatorPath = "{appData}/bios/"
        )
        val bundle = makeBundle(listOf(biosEntry))

        val candidate = CandidateFile(
            path = "/sdcard/bios/ps2-bios.bin",
            size = 1024L,
            md5 = { "abc123def456" }
        )

        val identifier = BiosIdentifier(bundle)
        val results = identifier.checkBios(
            listOf(candidate),
            listOf("ps2" to "nethersx2")
        )

        val stagePlan = identifier.buildStagePlan(results)
        assertEquals(1, stagePlan.size)
        assertEquals("ps2-bios.bin", stagePlan[0].finalFileName)
        assertEquals("{appData}/bios/", stagePlan[0].destPath)
    }

    @Test
    fun toSummaryCountsCorrectly() {
        val biosEntryFound = makeBiosEntry(
            system = "ps2",
            fileName = "ps2-bios.bin",
            md5 = "abc123def456",
            size = 1024L,
            emulatorId = "nethersx2"
        )
        val biosEntryWrong = makeBiosEntry(
            system = "wii",
            fileName = "wii-bootcode.bin",
            md5 = "def456abc123",
            size = 512L,
            emulatorId = "dolphin"
        )
        val bundle = makeBundle(listOf(biosEntryFound, biosEntryWrong))

        val candidateFound = CandidateFile(
            path = "/sdcard/bios/ps2-bios.bin",
            size = 1024L,
            md5 = { "abc123def456" }
        )
        val candidateWrong = CandidateFile(
            path = "/sdcard/bios/wii-bootcode.bin",
            size = 512L,
            md5 = { "wrong7890abcd1234" }
        )

        val identifier = BiosIdentifier(bundle)
        val results = identifier.checkBios(
            listOf(candidateFound, candidateWrong),
            listOf("ps2" to "nethersx2", "wii" to "dolphin")
        )

        val summary = identifier.toSummary(results)
        assertEquals(setOf("ps2"), summary.found)
        assertEquals(setOf("wii"), summary.wrongVersion)
        assertEquals(emptySet<String>(), summary.missing)
    }

    @Test
    fun caseInsensitiveHashMatch() {
        val biosEntry = makeBiosEntry(
            system = "ps2",
            fileName = "ps2-bios.bin",
            md5 = "ABC123DEF456",
            size = 1024L,
            emulatorId = "nethersx2"
        )
        val bundle = makeBundle(listOf(biosEntry))

        val candidate = CandidateFile(
            path = "/sdcard/bios/ps2-bios.bin",
            size = 1024L,
            md5 = { "abc123def456" }  // Lowercase
        )

        val identifier = BiosIdentifier(bundle)
        val results = identifier.checkBios(
            listOf(candidate),
            listOf("ps2" to "nethersx2")
        )

        assertEquals(1, results.size)
        assertTrue(results[0].status is BiosCheckStatus.Found)
    }

    @Test
    fun multipleEmulatorsPerSystem() {
        val biosEntry = makeBiosEntry(
            system = "ps2",
            fileName = "ps2-bios.bin",
            md5 = "abc123def456",
            size = 1024L,
            emulatorId = "duckstation"
        )
        val bundle = makeBundle(listOf(biosEntry))

        val candidate = CandidateFile(
            path = "/sdcard/bios/ps2-bios.bin",
            size = 1024L,
            md5 = { "abc123def456" }
        )

        val identifier = BiosIdentifier(bundle)
        // Plan has ps2 for both duckstation and nethersx2
        val results = identifier.checkBios(
            listOf(candidate),
            listOf("ps2" to "duckstation", "ps2" to "nethersx2")
        )

        // Should find for duckstation (has entry), missing for nethersx2 (no entry target)
        assertTrue(results.any { result -> result.status is BiosCheckStatus.Found })
    }

    @Test
    fun eachFileIsHashedOnce() {
        val bundle = loadRealBundle()
        var calls = 0
        // 512 KiB matches every PS1 entry; the hash must still be computed once.
        val file = CandidateFile("/x/unknown.bin", 524288) { calls++; "00000000000000000000000000000000" }
        BiosIdentifier(bundle).checkBios(listOf(file), listOf("ps1" to "duckstation"))
        org.junit.Assert.assertEquals(1, calls)
    }

    @Test
    fun optionalBiosIsNeverMissing() {
        val bundle = loadRealBundle()
        val id = BiosIdentifier(bundle)
        val summary = id.toSummary(id.checkBios(emptyList(), listOf("gc" to "dolphin", "ps1" to "duckstation")))
        org.junit.Assert.assertEquals(setOf("ps1"), summary.missing)
    }

    private fun loadRealBundle(): KnowledgeBundle {
        val text = java.io.File("../knowledge/src/main/assets/knowledge/bundle-v1.json").readText()
        return kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString(KnowledgeBundle.serializer(), text)
    }
}
