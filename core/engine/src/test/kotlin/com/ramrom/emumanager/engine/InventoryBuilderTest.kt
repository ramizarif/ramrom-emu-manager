package com.ramrom.emumanager.engine

import com.ramrom.emumanager.engine.inventory.FileLister
import com.ramrom.emumanager.engine.inventory.InventoryBuilder
import com.ramrom.emumanager.engine.inventory.RomFolder
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class InventoryBuilderTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val bundle: KnowledgeBundle by lazy { loadBundle() }

    private fun loadBundle(): KnowledgeBundle {
        val bundleJson = this::class.java.classLoader?.getResourceAsStream("knowledge/bundle-v1.json")
            ?.bufferedReader()
            ?.readText()
            ?: error("Could not load bundle-v1.json")
        return json.decodeFromString(KnowledgeBundle.serializer(), bundleJson)
    }

    /**
     * Mock FileLister for testing with a realistic Thor setup.
     */
    private fun createMockThorSetup(): FileLister {
        return object : FileLister {
            private val files = mapOf(
                "/storage/emulated/0/ROMs" to listOf(
                    "switch/", "wii/", "gc/", "n3ds/"
                ),
                "/storage/emulated/0/ROMs/switch" to listOf(
                    "game1.xci", "game2.xci", "game3.nsp", ".game_backup.xci"
                ),
                "/storage/emulated/0/ROMs/wii" to listOf(
                    "wii_game1.wbfs", "wii_game2.wbfs", "wii_game1.wbfs.sav"
                ),
                "/storage/emulated/0/ROMs/gc" to listOf(
                    "game1.iso", "game2.iso"
                ),
                "/storage/emulated/0/ROMs/n3ds" to listOf(
                    "game1.3ds", "game2.cia"
                ),
                "/storage/emulated/0/ES-DE" to listOf(
                    "settings/", "custom_systems/", "downloaded_media/"
                ),
                "/storage/emulated/0/ES-DE/settings" to listOf(
                    "es_settings.xml"
                ),
                "/storage/emulated/0/ES-DE/custom_systems" to listOf(
                    "eden.xml"
                ),
                "/storage/emulated/0/ES-DE/downloaded_media" to listOf(
                    "switch/", "wii/"
                ),
                "/storage/emulated/0/ES-DE/downloaded_media/switch/covers" to listOf(
                    "game1.png", "game2.png", "orphaned_game.png"
                ),
                "/storage/emulated/0/ES-DE/downloaded_media/wii/covers" to listOf(
                    "wii_game1.png"
                )
            )

            override fun listFiles(folderPath: String): List<String> {
                return files[folderPath] ?: emptyList()
            }

            override fun exists(path: String): Boolean {
                return files.containsKey(path) || files.values.any { it.contains(path.substringAfterLast("/")) }
            }

            override fun isDirectory(path: String): Boolean {
                return files.containsKey(path)
            }
        }
    }

    @Test
    fun testBuildInventoryWithInstalledApps() {
        val lister = createMockThorSetup()
        val builder = InventoryBuilder(bundle, lister)

        val inventory = builder.build(
            installedPackages = setOf(
                "com.github.yuzu.emu.ea",  // Eden
                "org.dolphinemu.dolphin",   // Dolphin
                "com.skyemu.nds",           // Azahar (N3DS)
                "org.es_de.frontend"        // ES-DE
            ),
            folderGrantsPerPackage = mapOf(
                "com.github.yuzu.emu.ea" to setOf("switch"),
                "org.dolphinemu.dolphin" to setOf("wii", "gc"),
                "com.skyemu.nds" to setOf("n3ds"),
                "org.es_de.frontend" to setOf("switch", "wii", "gc", "n3ds")
            ),
            allFilesAccessPerPackage = mapOf(
                "com.github.yuzu.emu.ea" to true,
                "org.dolphinemu.dolphin" to true,
                "com.skyemu.nds" to true
            )
        )

        assertEquals(4, inventory.installedPackages.size)
        assertTrue(inventory.installedPackages.contains("com.github.yuzu.emu.ea"))
        assertEquals(true, inventory.esdeConfigured)
    }

    @Test
    fun testBuildRomFolders() {
        val lister = createMockThorSetup()
        val builder = InventoryBuilder(bundle, lister)

        val inventory = builder.build(
            installedPackages = setOf("com.github.yuzu.emu.ea", "org.dolphinemu.dolphin"),
            folderGrantsPerPackage = emptyMap(),
            allFilesAccessPerPackage = emptyMap()
        )

        val switchFolder = inventory.romFolders.find { it.systemId == "switch" }
        assertRomFolderExists(switchFolder, "switch", 3)  // game1.xci, game2.xci, game3.nsp

        val wiiFolder = inventory.romFolders.find { it.systemId == "wii" }
        assertRomFolderExists(wiiFolder, "wii", 2)  // wii_game1.wbfs, wii_game2.wbfs (ignore .sav)

        val gcFolder = inventory.romFolders.find { it.systemId == "gc" }
        assertRomFolderExists(gcFolder, "gc", 2)
    }

    @Test
    fun testFolderGrantTracking() {
        val lister = createMockThorSetup()
        val builder = InventoryBuilder(bundle, lister)

        val grants = mapOf(
            "com.github.yuzu.emu.ea" to setOf("switch"),
            "org.dolphinemu.dolphin" to setOf("wii", "gc")
        )

        val inventory = builder.build(
            installedPackages = setOf("com.github.yuzu.emu.ea", "org.dolphinemu.dolphin"),
            folderGrantsPerPackage = grants,
            allFilesAccessPerPackage = emptyMap()
        )

        assertEquals(3, inventory.folderGrants.size)
        assertTrue(
            inventory.folderGrants.any { it.packageId == "com.github.yuzu.emu.ea" && it.systemId == "switch" }
        )
        assertTrue(
            inventory.folderGrants.any { it.packageId == "org.dolphinemu.dolphin" && it.systemId == "wii" }
        )
    }

    @Test
    fun testEsdeConfigDetection() {
        val lister = createMockThorSetup()
        val builder = InventoryBuilder(bundle, lister)

        val inventory = builder.build(
            installedPackages = setOf("org.es_de.frontend"),
            folderGrantsPerPackage = emptyMap(),
            allFilesAccessPerPackage = emptyMap()
        )

        assertEquals(true, inventory.esdeConfigured)
        assertEquals(true, inventory.esdeConfig.installed)
        assertTrue(inventory.esdeConfig.configPath.contains("es_settings.xml"))
    }

    @Test
    fun testEmptyInventory() {
        // Use a minimal mock for empty inventory
        val lister = object : FileLister {
            override fun listFiles(folderPath: String): List<String> = emptyList()
            override fun exists(path: String): Boolean = false
            override fun isDirectory(path: String): Boolean = false
        }

        val builder = InventoryBuilder(bundle, lister)

        val inventory = builder.build(
            installedPackages = emptySet(),
            folderGrantsPerPackage = emptyMap(),
            allFilesAccessPerPackage = emptyMap()
        )

        assertEquals(0, inventory.installedPackages.size)
        assertEquals(false, inventory.esdeConfigured)
        assertTrue(inventory.romFolders.isEmpty())
    }

    private fun assertRomFolderExists(folder: RomFolder?, systemId: String, expectedGameCount: Int) {
        assertTrue("ROM folder for $systemId should exist", folder != null)
        if (folder != null) {
            assertEquals("Game count for $systemId", expectedGameCount, folder.gameCount)
        }
    }
}
