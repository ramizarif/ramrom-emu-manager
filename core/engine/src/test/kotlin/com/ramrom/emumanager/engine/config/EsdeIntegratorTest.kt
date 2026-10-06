package com.ramrom.emumanager.engine.config

import com.ramrom.emumanager.knowledge.*
import org.junit.Assert.*
import org.junit.Test

class EsdeIntegratorTest {
    private fun createTestBundle(): KnowledgeBundle {
        return KnowledgeBundle(
            schemaVersion = 1,
            dataVersion = "1.0",
            emulators = listOf(
                // Built-in emulator (no custom config needed)
                EmulatorProfile(
                    id = "dolphin",
                    name = "Dolphin",
                    systems = listOf("gc", "wii"),
                    packages = listOf("org.dolphinemu.dolphin"),
                    configFormat = ConfigFormat.DOLPHIN_INI
                ),
                // Custom emulator requiring ES-DE integration
                EmulatorProfile(
                    id = "eden",
                    name = "Eden",
                    systems = listOf("switch"),
                    packages = listOf("dev.eden.eden_emulator"),
                    configFormat = ConfigFormat.QT_INI,
                    esdeLaunch = EsdeLaunchConfig(
                        findRuleName = "EDEN",
                        packageActivity = "dev.eden.eden_emulator/org.yuzu.yuzu_emu.activities.EmulationActivity",
                        command = "%ROM% -f"
                    )
                ),
                // Another custom emulator
                EmulatorProfile(
                    id = "citron",
                    name = "Citron",
                    systems = listOf("switch"),
                    packages = listOf("org.citron.citron_emu"),
                    configFormat = ConfigFormat.QT_INI,
                    esdeLaunch = EsdeLaunchConfig(
                        findRuleName = "CITRON",
                        packageActivity = "org.citron.citron_emu/org.citron.citron_emu.EmulationActivity",
                        command = "%ROM%"
                    )
                )
            ),
            frontends = listOf(
                FrontendProfile(
                    id = "es-de",
                    packages = listOf("org.es_de.frontend"),
                    paths = FrontendPaths(
                        settings = "/sdcard/ES-DE/settings/es_settings.xml",
                        customSystems = "/sdcard/ES-DE/custom_systems/"
                    ),
                    systemFolders = mapOf(
                        "switch" to "switch",
                        "gc" to "gc",
                        "wii" to "wii"
                    ),
                    mustBeClosedToWrite = true
                )
            ),
            systems = listOf(
                SystemDef(
                    id = "switch",
                    name = "Nintendo Switch",
                    maker = "Nintendo"
                ),
                SystemDef(
                    id = "gc",
                    name = "GameCube",
                    maker = "Nintendo"
                ),
                SystemDef(
                    id = "wii",
                    name = "Nintendo Wii",
                    maker = "Nintendo"
                )
            )
        )
    }

    @Test
    fun planDetectCustomEmulators() {
        val bundle = createTestBundle()
        val integrator = EsdeIntegrator(bundle)

        val plan = integrator.plan(
            chosenEmulators = listOf("eden", "dolphin"),
            systemsPerEmulator = mapOf(
                "eden" to listOf("switch"),
                "dolphin" to listOf("gc", "wii")
            )
        )

        // Eden needs custom config, Dolphin doesn't
        assertEquals(1, plan.findRulesXmlEntries.size)
        assertEquals("EDEN", plan.findRulesXmlEntries[0].name)
        assertEquals("dev.eden.eden_emulator", plan.findRulesXmlEntries[0].packageName)
    }

    @Test
    fun planGeneratesSystemsXmlEntries() {
        val bundle = createTestBundle()
        val integrator = EsdeIntegrator(bundle)

        val plan = integrator.plan(
            chosenEmulators = listOf("eden"),
            systemsPerEmulator = mapOf("eden" to listOf("switch"))
        )

        // Switch system should map to Eden
        assertEquals(1, plan.systemsXmlEntries.size)
        assertEquals("switch", plan.systemsXmlEntries[0].name)
        assertEquals("%ROM% -f", plan.systemsXmlEntries[0].command)
    }

    @Test
    fun planDetectsNoChangesNeeded() {
        val bundle = createTestBundle()
        val integrator = EsdeIntegrator(bundle)

        // If all emulators are built-in (no esdeLaunch config)
        val builtInBundle = bundle.copy(
            emulators = listOf(
                EmulatorProfile(
                    id = "dolphin",
                    name = "Dolphin",
                    systems = listOf("gc", "wii"),
                    packages = listOf("org.dolphinemu.dolphin"),
                    esdeLaunch = null
                )
            )
        )
        val integrator2 = EsdeIntegrator(builtInBundle)

        val plan = integrator2.plan(
            chosenEmulators = listOf("dolphin"),
            systemsPerEmulator = mapOf("dolphin" to listOf("gc", "wii"))
        )

        assertFalse(plan.requiresSystemsXml)
        assertFalse(plan.requiresFindRulesXml)
        assertFalse(plan.mustCloseEsde) // No changes needed
    }

    @Test
    fun planMultipleCustomEmulators() {
        val bundle = createTestBundle()
        val integrator = EsdeIntegrator(bundle)

        val plan = integrator.plan(
            chosenEmulators = listOf("eden", "citron"),
            systemsPerEmulator = mapOf(
                "eden" to listOf("switch"),
                "citron" to listOf("switch")
            )
        )

        assertEquals(2, plan.findRulesXmlEntries.size)
        assertEquals(2, plan.systemsXmlEntries.size)
        assertTrue(plan.mustCloseEsde)
    }

    @Test
    fun generateSystemsXmlMergesEntries() {
        val bundle = createTestBundle()
        val integrator = EsdeIntegrator(bundle)

        val entries = listOf(
            EsdeIntegrator.SystemXmlEntry(
                name = "switch",
                fullName = "Nintendo Switch",
                command = "%ROM% -f"
            )
        )

        val xml = integrator.generateSystemsXml("", entries)

        assertTrue(xml.contains("switch"))
        assertTrue(xml.contains("Nintendo Switch"))
        assertTrue(xml.contains("%ROM% -f"))
    }

    @Test
    fun generateFindRulesXmlCreatesRules() {
        val bundle = createTestBundle()
        val integrator = EsdeIntegrator(bundle)

        val entries = listOf(
            EsdeIntegrator.FindRulesXmlEntry(
                name = "EDEN",
                packageName = "dev.eden.eden_emulator",
                activity = "org.yuzu.yuzu_emu.activities.EmulationActivity"
            )
        )

        val xml = integrator.generateFindRulesXml("", entries)

        assertTrue(xml.contains("EDEN"))
        assertTrue(xml.contains("dev.eden.eden_emulator"))
        assertTrue(xml.contains("EmulationActivity"))
    }

    @Test
    fun planDetectsChangesInExistingSystemsXml() {
        val bundle = createTestBundle()
        val integrator = EsdeIntegrator(bundle)

        // Simulate existing XML with some systems
        val existingXml = """
            <?xml version="1.0"?>
            <systems>
                <system name="gc">
                    <fullname>GameCube</fullname>
                    <command>dolphin %ROM%</command>
                </system>
            </systems>
        """.trimIndent()

        val plan = integrator.plan(
            chosenEmulators = listOf("eden", "citron"),
            systemsPerEmulator = mapOf(
                "eden" to listOf("switch"),
                "citron" to listOf("switch")
            ),
            currentSystemsXml = existingXml
        )

        // Should detect that switch entries are missing
        assertTrue(plan.requiresSystemsXml)
        assertEquals(2, plan.systemsXmlEntries.size)
    }

    @Test
    fun planWithEmptyCurrentXml() {
        val bundle = createTestBundle()
        val integrator = EsdeIntegrator(bundle)

        val plan = integrator.plan(
            chosenEmulators = listOf("eden"),
            systemsPerEmulator = mapOf("eden" to listOf("switch")),
            currentSystemsXml = ""
        )

        // Should detect changes needed
        assertTrue(plan.requiresSystemsXml)
    }

    @Test
    fun planEscapesXmlSpecialChars() {
        val bundle = createTestBundle()
        val integrator = EsdeIntegrator(bundle)

        val entries = listOf(
            EsdeIntegrator.SystemXmlEntry(
                name = "test",
                fullName = "Name & Description <with> \"quotes\"",
                command = "%ROM% & echo 'test'"
            )
        )

        val xml = integrator.generateSystemsXml("", entries)

        // Should escape & < > quotes
        assertTrue(xml.contains("&amp;"))
        assertTrue(xml.contains("&lt;"))
        assertTrue(xml.contains("&gt;"))
    }

    @Test
    fun planPackageActivityParsing() {
        val bundle = createTestBundle()
        val integrator = EsdeIntegrator(bundle)

        val plan = integrator.plan(
            chosenEmulators = listOf("eden"),
            systemsPerEmulator = mapOf("eden" to listOf("switch"))
        )

        val rule = plan.findRulesXmlEntries[0]
        assertEquals("dev.eden.eden_emulator", rule.packageName)
        assertEquals("org.yuzu.yuzu_emu.activities.EmulationActivity", rule.activity)
    }
}
