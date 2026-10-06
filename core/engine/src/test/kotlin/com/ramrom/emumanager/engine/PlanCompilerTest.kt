package com.ramrom.emumanager.engine

import com.ramrom.emumanager.access.Capability
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class PlanCompilerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val bundle: KnowledgeBundle by lazy {
        loadBundle()
    }

    private fun loadBundle(): KnowledgeBundle {
        val bundleJson = Thread.currentThread().contextClassLoader?.getResourceAsStream("knowledge/bundle-v1.json")
            ?.bufferedReader()
            ?.readText()
            ?: this::class.java.classLoader?.getResourceAsStream("knowledge/bundle-v1.json")
            ?.bufferedReader()
            ?.readText()
            ?: error("Could not load bundle-v1.json from classpath")
        return json.decodeFromString(KnowledgeBundle.serializer(), bundleJson)
    }

    /** A saved run is matched to a recompiled plan by id; ids must not shift when the plan shrinks. */
    @Test
    fun stepIdsAreUniqueAndStableAcrossInventoryChanges() {
        val plan = Plan("ayn-thor", "sd8gen2", "es-de",
            listOf(Plan.PlanSystem("switch", "eden"), Plan.PlanSystem("gc", "dolphin"), Plan.PlanSystem("wii", "dolphin")), null)
        val caps = Capability.entries.toSet()
        val before = PlanCompiler().compile(plan, bundle, Inventory(), caps)
        val after = PlanCompiler().compile(plan, bundle, Inventory(existingFolders = setOf("switch")), caps)
        assertEquals(before.steps.size, before.steps.map { it.id }.toSet().size)
        assertTrue(after.steps.size < before.steps.size)
        val beforeIds = before.steps.map { it.id }.toSet()
        after.steps.forEach { assertTrue("${it.id} changed", it.id in beforeIds) }
    }

    @Test
    fun testFreshThorElevatedPlan() {
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(
                Plan.PlanSystem("switch", "eden"),
                Plan.PlanSystem("wii", "dolphin"),
                Plan.PlanSystem("gc", "dolphin"),
                Plan.PlanSystem("n3ds", "azahar"),
                Plan.PlanSystem("ps2", "nethersx2")
            ),
            bios = BiosSummary(
                found = setOf("wii", "gc", "ps2"),
                missing = setOf("n3ds")
            )
        )

        val inventory = Inventory(
            installedPackages = emptySet(),
            grantedFolderTrees = emptySet(),
            existingFolders = emptySet(),
            esdeConfigured = false
        )

        val capabilities = setOf(
            Capability.WRITE_APP_DATA,
            Capability.INSTALL_SILENT,
            Capability.FORCE_STOP,
            Capability.GRANT_RUNTIME_PERMISSION,
            Capability.SET_APPOP,
            Capability.READ_URI_GRANTS,
            Capability.READ_SHARED_STORAGE,
            Capability.WRITE_SHARED_STORAGE,
            Capability.LAUNCH_APP
        )

        val compiled = PlanCompiler().compile(plan, bundle, inventory, capabilities)

        assertTrue("Compiled plan should be acyclic", isDAGValid(compiled))
        assertTrue("Ordering should be valid", isOrderingValid(compiled))

        val summary = compiled.summary()
        assertTrue("Should have apps to install", summary.appsTotal > 0)
        assertTrue("Should have steps", summary.stepsTotal > 0)
        assertTrue("Should have hands-on time", summary.handsOnMinutes > 0)

        val folderPickSteps = compiled.steps.filter { it.type is StepType.GuidedFolderPick }
        assertTrue("Should have some folder picks", folderPickSteps.isNotEmpty())
    }

    @Test
    fun testLimitedModeUpgradesSteps() {
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(
                Plan.PlanSystem("switch", "eden"),
                Plan.PlanSystem("wii", "dolphin")
            ),
            bios = null
        )

        val inventory = Inventory()
        val limitedCapabilities = setOf(
            Capability.READ_SHARED_STORAGE,
            Capability.WRITE_SHARED_STORAGE
        )

        val compiled = PlanCompiler().compile(plan, bundle, inventory, limitedCapabilities)

        val guidedInstalls = compiled.steps.filter {
            it.type is StepType.InstallApp && it.executor == Executor.Guided
        }
        assertTrue("Limited mode should have guided installs", guidedInstalls.isNotEmpty())

        val guidedConfigs = compiled.steps.filter {
            it.type is StepType.WriteEmulatorConfig && it.executor == Executor.Guided
        }
        assertTrue("Limited mode should have guided config writes", guidedConfigs.isNotEmpty())

        val summary = compiled.summary()
        assertTrue("Limited mode should need user intervention", summary.stepsNeedYou > 0)
    }

    @Test
    fun testAdoptFlow_InstalledAndGrantedSteps() {
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(
                Plan.PlanSystem("switch", "eden"),
                Plan.PlanSystem("wii", "dolphin")
            ),
            bios = null
        )

        val inventory = Inventory(
            installedPackages = setOf(
                "dev.eden.eden_emulator",
                "org.es_de.frontend",
                "org.dolphinemu.dolphinemu"
            ),
            grantedFolderTrees = setOf("switch", "wii"),
            existingFolders = setOf("switch", "wii"),
            esdeConfigured = true
        )

        val capabilities = setOf(
            Capability.WRITE_APP_DATA,
            Capability.INSTALL_SILENT,
            Capability.GRANT_RUNTIME_PERMISSION
        )

        val compiled = PlanCompiler().compile(plan, bundle, inventory, capabilities)

        val alreadyDoneInstalls = compiled.steps.filter {
            it.type is StepType.InstallApp && it.status == StepStatus.AlreadyDone
        }
        assertTrue("Already-installed apps should be AlreadyDone", alreadyDoneInstalls.isNotEmpty())

        val alreadyDoneFolderPicks = compiled.steps.filter {
            it.type is StepType.GuidedFolderPick && it.status == StepStatus.AlreadyDone
        }
        assertTrue("Already-granted folders should be AlreadyDone", alreadyDoneFolderPicks.isNotEmpty())

        val summary = compiled.summary()
        assertEquals("Already-installed apps shouldn't count", 0, summary.appsYouGet)
    }

    @Test
    fun testDAGValidation_NoCycles() {
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(
                Plan.PlanSystem("switch", "eden")
            ),
            bios = null
        )

        val inventory = Inventory()
        val capabilities = emptySet<Capability>()

        val compiled = PlanCompiler().compile(plan, bundle, inventory, capabilities)

        assertTrue("Compiled plan should be acyclic", isDAGValid(compiled))
    }

    @Test
    fun testDAGValidation_AllDepsExist() {
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(
                Plan.PlanSystem("switch", "eden"),
                Plan.PlanSystem("wii", "dolphin")
            ),
            bios = null
        )

        val inventory = Inventory()
        val capabilities = setOf(Capability.WRITE_APP_DATA, Capability.INSTALL_SILENT)

        val compiled = PlanCompiler().compile(plan, bundle, inventory, capabilities)

        val allStepIds = compiled.steps.map { it.id }.toSet()

        for (step in compiled.steps) {
            for (dep in step.dependsOn) {
                assertTrue("Step ${step.id} depends on missing step $dep", allStepIds.contains(dep))
            }
        }
    }

    @Test
    fun testOrdering_InstallsBeforePermissions() {
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(
                Plan.PlanSystem("switch", "eden")
            ),
            bios = null
        )

        val inventory = Inventory()
        val capabilities = setOf(Capability.WRITE_APP_DATA, Capability.INSTALL_SILENT, Capability.GRANT_RUNTIME_PERMISSION)

        val compiled = PlanCompiler().compile(plan, bundle, inventory, capabilities)

        val stepsWithOrder = compiled.steps.mapIndexed { index, step -> step.id to index }
        val orderMap = stepsWithOrder.toMap()

        val permissionSteps = compiled.steps.filter { it.type is StepType.GrantRuntimePermission }

        for (permStep in permissionSteps) {
            for (dep in permStep.dependsOn) {
                val depIndex = orderMap[dep] ?: continue
                val permIndex = orderMap[permStep.id] ?: continue
                assertTrue("Dependency $dep should come before ${permStep.id}", depIndex < permIndex)
            }
        }
    }

    @Test
    fun testOrdering_FolderPicksAfterInstalls() {
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(
                Plan.PlanSystem("switch", "eden")
            ),
            bios = null
        )

        val inventory = Inventory()
        val capabilities = setOf(Capability.WRITE_APP_DATA, Capability.INSTALL_SILENT)

        val compiled = PlanCompiler().compile(plan, bundle, inventory, capabilities)

        val folderPickSteps = compiled.steps.filter { it.type is StepType.GuidedFolderPick }
        val installSteps = compiled.steps.filter { it.type is StepType.InstallApp }

        val lastInstallIndex = compiled.steps.indexOfLast { it in installSteps }
        val firstFolderPickIndex = compiled.steps.indexOfFirst { it in folderPickSteps }

        if (folderPickSteps.isNotEmpty() && installSteps.isNotEmpty()) {
            assertTrue("Installs should complete before folder picks begin", lastInstallIndex < firstFolderPickIndex)
        }
    }

    @Test
    fun testHealthCheckAtEnd() {
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(
                Plan.PlanSystem("switch", "eden")
            ),
            bios = null
        )

        val inventory = Inventory()
        val capabilities = emptySet<Capability>()

        val compiled = PlanCompiler().compile(plan, bundle, inventory, capabilities)

        val healthCheckStep = compiled.steps.find { it.type is StepType.HealthCheck }
        assertFalse("Should have a health check step", healthCheckStep == null)
        assertEquals("Health check should be last", compiled.steps.last(), healthCheckStep)
    }

    @Test
    fun testSummaryCalculations() {
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(
                Plan.PlanSystem("switch", "eden"),
                Plan.PlanSystem("wii", "dolphin")
            ),
            bios = BiosSummary(found = setOf("wii"), missing = setOf())
        )

        val inventory = Inventory()
        val capabilities = setOf(Capability.WRITE_APP_DATA, Capability.INSTALL_SILENT)

        val compiled = PlanCompiler().compile(plan, bundle, inventory, capabilities)
        val summary = compiled.summary()

        assertTrue("Should have auto-install apps", summary.appsAuto > 0)
        assertTrue("Should have more steps than apps", summary.stepsTotal > summary.appsTotal)
        assertTrue("Should have hands-on time", summary.handsOnMinutes > 0)
        assertEquals("BIOS found should match", setOf("wii"), summary.biosFound)
    }

    @Test
    fun stepsArePerAppNotPerSystem() {
        val plan = Plan("ayn-thor", "sd8gen2", "es-de",
            listOf(Plan.PlanSystem("gc", "dolphin"), Plan.PlanSystem("wii", "dolphin"), Plan.PlanSystem("gba", "retroarch"), Plan.PlanSystem("snes", "retroarch")), null)
        val compiled = PlanCompiler().compile(plan, bundle, Inventory(), Capability.entries.toSet())
        val appStep = { t: StepType -> t is StepType.InstallApp || t is StepType.GuidedObtainApp }
        assertEquals(1, compiled.steps.count { appStep(it.type) && it.subject == "dolphin" })
        assertEquals(1, compiled.steps.count { it.type is StepType.GuidedFolderPick && it.subject == "dolphin" })
        assertEquals(1, compiled.steps.count { appStep(it.type) && it.subject == "retroarch" })
        assertEquals(1, compiled.steps.count { it.type is StepType.WriteEmulatorConfig && it.subject == "dolphin" })
    }

    @Test
    fun appStepsDependOnTheirInstall() {
        val plan = Plan("ayn-thor", "sd8gen2", "es-de", listOf(Plan.PlanSystem("wii", "dolphin")), null)
        val compiled = PlanCompiler().compile(plan, bundle, Inventory(), Capability.entries.toSet())
        val install = compiled.steps.single { (it.type is StepType.InstallApp || it.type is StepType.GuidedObtainApp) && it.subject == "dolphin" }
        val config = compiled.steps.single { it.type is StepType.WriteEmulatorConfig && it.subject == "dolphin" }
        assertTrue(install.id in config.dependsOn)
    }

    @Test
    fun officialPagesAreGuidedAndReleasesAreAuto() {
        val plan = Plan(null, "sd8gen2", null, listOf(Plan.PlanSystem("gc", "dolphin"), Plan.PlanSystem("switch", "eden")), null)
        val steps = PlanCompiler().compile(plan, bundle, Inventory(), Capability.entries.toSet()).steps
        assertTrue(steps.any { it.type is StepType.GuidedObtainApp && it.subject == "dolphin" })
        assertTrue(steps.any { it.type is StepType.InstallApp && it.subject == "eden" && it.executor == Executor.Auto })
    }

    @Test
    fun paidAppsAreObtainedNotInstalled() {
        val plan = Plan("ayn-thor", "sd8gen2", "es-de", listOf(Plan.PlanSystem("ps2", "nethersx2")), null)
        val compiled = PlanCompiler().compile(plan, bundle, Inventory(), Capability.entries.toSet())
        assertTrue(compiled.steps.none { it.type is StepType.InstallApp && it.subject in setOf("nethersx2", "es-de") })
        assertEquals(setOf("nethersx2", "es-de"), compiled.steps.filter { it.type is StepType.GuidedObtainApp }.map { it.subject }.toSet())
        assertEquals(2, compiled.summary().appsYouGet)
        assertEquals(2, compiled.summary().appsTotal)
    }

    @Test
    fun limitedModeInstallsAreCountedAsApps() {
        val plan = Plan(null, "sd8gen2", null, listOf(Plan.PlanSystem("wii", "dolphin"), Plan.PlanSystem("psp", "ppsspp")), null)
        val s = PlanCompiler().compile(plan, bundle, Inventory(), setOf(Capability.READ_SHARED_STORAGE, Capability.WRITE_SHARED_STORAGE)).summary()
        assertEquals(2, s.appsTotal)
        assertEquals(0, s.appsAuto)
    }

    @Test
    fun biosCountsComeFromTheCheck() {
        val plan = Plan(null, "sd8gen2", null, listOf(Plan.PlanSystem("ps2", "nethersx2"), Plan.PlanSystem("n3ds", "azahar")),
            BiosSummary(found = setOf("ps2"), missing = setOf("n3ds")))
        val s = PlanCompiler().compile(plan, bundle, Inventory(), Capability.entries.toSet()).summary()
        assertEquals(setOf("ps2"), s.biosFound)
        assertEquals(setOf("ps2", "n3ds"), s.biosNeeded)
    }

    private fun isDAGValid(compiled: CompiledPlan): Boolean {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val stepMap = compiled.steps.associateBy { it.id }

        fun hasCycle(nodeId: String): Boolean {
            visited.add(nodeId)
            recursionStack.add(nodeId)

            val step = stepMap[nodeId] ?: return false
            for (dep in step.dependsOn) {
                if (dep !in visited) {
                    if (hasCycle(dep)) return true
                } else if (dep in recursionStack) {
                    return true
                }
            }

            recursionStack.remove(nodeId)
            return false
        }

        for (step in compiled.steps) {
            if (step.id !in visited) {
                if (hasCycle(step.id)) return false
            }
        }
        return true
    }

    private fun isOrderingValid(compiled: CompiledPlan): Boolean {
        val stepsByOrder = compiled.steps.mapIndexed { index, step -> step.id to index }.toMap()

        for (step in compiled.steps) {
            for (dep in step.dependsOn) {
                val depIndex = stepsByOrder[dep] ?: return false
                val stepIndex = stepsByOrder[step.id] ?: return false
                if (depIndex >= stepIndex) {
                    return false
                }
            }
        }
        return true
    }
}
