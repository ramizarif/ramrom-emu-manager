package com.ramrom.emumanager.engine

import com.ramrom.emumanager.access.Capability
import com.ramrom.emumanager.knowledge.ConfigFormat
import com.ramrom.emumanager.knowledge.EmulatorProfile
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import com.ramrom.emumanager.knowledge.SourceType

/**
 * Time constants (in seconds) for step estimation.
 */
object TimeConstants {
    const val GUIDED_FOLDER_PICK_HANDS_ON = 45
    const val GUIDED_OBTAIN_APP_HANDS_ON = 120
    const val GUIDED_IN_APP_SETUP_HANDS_ON = 60
    const val USER_CONFIRMED_INSTALL_HANDS_ON = 20
    const val GUIDED_PERMISSION_HANDS_ON = 10
    const val GUIDED_ALL_FILES_ACCESS_HANDS_ON = 15

    const val APP_INSTALL_MACHINE = 45
    const val FIRST_LAUNCH_MACHINE = 10
    const val CONFIG_WRITE_MACHINE = 5
    const val BIOS_STAGE_MACHINE = 15
    const val FOLDER_CREATE_MACHINE = 2
}

/**
 * Compiler that converts a Plan + Inventory + Capabilities into a CompiledPlan.
 * Pure function, fully unit-testable.
 */
class PlanCompiler(
    /**
     * Plan the frontend config step (P10). Off in the app until its writer lands: a step that does
     * nothing must never ask the user to do it. Emulator steps need no flag: they appear only for
     * emulators with device defaults (L2) for this device.
     */
    private val writesConfigs: Boolean = true,
) {
    companion object {
        /** Sources with a stable, machine-readable APK link we may download ourselves. */
        val AUTO_SOURCES = setOf(SourceType.GITHUB_RELEASE, SourceType.FORGEJO_RELEASE, SourceType.DIRECT_URL)
    }

    fun compile(
        plan: Plan,
        bundle: KnowledgeBundle,
        inventory: Inventory,
        capabilities: Set<Capability>
    ): CompiledPlan {
        val steps = mutableListOf<Step>()
        fun add(step: Step): String { steps += step; return step.id }
        // Stable ids: a run saved before a restart is matched back to a recompiled plan by id, and
        // the plan may have shrunk meanwhile (a folder now exists), so no counters.
        fun id(prefix: String, subject: String) = "${prefix}_${subject}"

        val frontend = plan.frontendId?.let { fid -> bundle.frontends.firstOrNull { it.id == fid } }
        val emulatorMap = bundle.emulators.associateBy { it.id }
        // Steps are per app, not per system: Dolphin runs GameCube and Wii, RetroArch runs many.
        val emulators = plan.systems.mapNotNull { emulatorMap[it.emulatorId] }.distinctBy { it.id }
        val systemsOf = plan.systems.groupBy({ it.emulatorId }, { it.systemId })
        fun isInstalled(pkgs: List<String>) = pkgs.any { it in inventory.installedPackages }
        val canInstall = Capability.INSTALL_SILENT in capabilities
        val canWriteShared = Capability.WRITE_SHARED_STORAGE in capabilities
        val canWriteAppData = Capability.WRITE_APP_DATA in capabilities

        // The step that makes each app present; everything for that app waits on it.
        val ready = mutableMapOf<String, String>()

        // Phase 1: get every app onto the device.
        fun appStep(subject: String, packages: List<String>, source: SourceType) {
            ready[subject] = when {
                isInstalled(packages) -> add(Step(id("install", subject), StepType.InstallApp, subject, executor = Executor.Auto, status = StepStatus.AlreadyDone))
                // We only fetch from release APIs. Paid apps, store listings and official download
                // pages are the user's to get; we open the right place and wait for the install.
                source !in AUTO_SOURCES ->
                    add(Step(id("obtain", subject), StepType.GuidedObtainApp, subject, executor = Executor.Guided,
                        handsOnSeconds = TimeConstants.GUIDED_OBTAIN_APP_HANDS_ON, verify = VerifySpec("appInstalled", packages.firstOrNull().orEmpty())))
                canInstall -> add(Step(id("install", subject), StepType.InstallApp, subject, executor = Executor.Auto,
                    capabilityNeeded = Capability.INSTALL_SILENT, estimatedSeconds = TimeConstants.APP_INSTALL_MACHINE,
                    verify = VerifySpec("appInstalled", packages.firstOrNull().orEmpty())))
                // Limited mode: we download, Android asks the user to confirm.
                else -> add(Step(id("install", subject), StepType.InstallApp, subject, executor = Executor.Guided,
                    estimatedSeconds = TimeConstants.APP_INSTALL_MACHINE, handsOnSeconds = TimeConstants.USER_CONFIRMED_INSTALL_HANDS_ON,
                    verify = VerifySpec("appInstalled", packages.firstOrNull().orEmpty())))
            }
        }
        if (frontend != null) appStep(frontend.id, frontend.packages, frontend.source.type)
        for (e in emulators) appStep(e.id, e.packages, e.source.type)

        // Phase 2: apps that only create their folders on first launch.
        for (e in emulators) {
            if (e.firstLaunchCreatesDirs && !isInstalled(e.packages)) {
                ready[e.id] = add(Step(id("first_launch", e.id), StepType.FirstLaunch, e.id, listOf(ready.getValue(e.id)), Executor.Guided,
                    estimatedSeconds = TimeConstants.FIRST_LAUNCH_MACHINE, handsOnSeconds = 30))
            }
        }

        // Phase 3: permissions and all-files access.
        // A scan that already saw the grant (P03) makes the step already done, so a re-run asks nothing twice.
        fun permsOf(e: EmulatorProfile) = inventory.appPermissions.firstOrNull { it.packageId in e.packages }
        for (e in emulators) {
            val dep = listOf(ready.getValue(e.id))
            val perms = permsOf(e)
            if (e.permissions.isNotEmpty()) {
                val auto = Capability.GRANT_RUNTIME_PERMISSION in capabilities
                val done = perms != null && perms.missingPermissions.isEmpty()
                add(Step(id("perm", e.id), StepType.GrantRuntimePermission, e.id, dep,
                    if (auto) Executor.Auto else Executor.Guided,
                    capabilityNeeded = if (auto) Capability.GRANT_RUNTIME_PERMISSION else null,
                    estimatedSeconds = if (auto && !done) TimeConstants.CONFIG_WRITE_MACHINE else 0,
                    handsOnSeconds = if (auto || done) 0 else TimeConstants.GUIDED_PERMISSION_HANDS_ON,
                    status = if (done) StepStatus.AlreadyDone else StepStatus.Pending))
            }
            if (e.declaresAllFilesAccess) {
                val auto = Capability.SET_APPOP in capabilities
                val done = perms?.grantedManageExternalStorage == true
                add(Step(id("allfiles", e.id), StepType.GrantAllFilesAccess, e.id, dep,
                    if (auto) Executor.Auto else Executor.Guided,
                    capabilityNeeded = if (auto) Capability.SET_APPOP else null,
                    estimatedSeconds = if (auto && !done) TimeConstants.CONFIG_WRITE_MACHINE else 0,
                    handsOnSeconds = if (auto || done) 0 else TimeConstants.GUIDED_ALL_FILES_ACCESS_HANDS_ON,
                    status = if (done) StepStatus.AlreadyDone else StepStatus.Pending))
            }
        }

        // Phase 4: ROM folders, named the way the frontend expects. Shared storage, so limited mode can do it.
        val folderIds = mutableListOf<String>()
        if (frontend != null) {
            val folders = plan.systems.mapNotNull { frontend.systemFolders[it.systemId] }.distinct()
            for (folder in folders) {
                if (folder in inventory.existingFolders) continue
                folderIds += add(Step(id("folder", folder), StepType.CreateFolder, folder,
                    executor = if (canWriteShared) Executor.Auto else Executor.Guided,
                    capabilityNeeded = Capability.WRITE_SHARED_STORAGE, estimatedSeconds = TimeConstants.FOLDER_CREATE_MACHINE,
                    handsOnSeconds = if (canWriteShared) 0 else 5, verify = VerifySpec("fileExists", folder)))
            }
        }

        // Phase 5: frontend config. ES-DE keeps it in shared storage; it must be closed while we write.
        if (writesConfigs && frontend != null && !inventory.esdeConfigured) {
            add(Step(id("config", frontend.id), StepType.WriteFrontendConfig, frontend.id, folderIds + ready.getValue(frontend.id),
                if (canWriteShared) Executor.Auto else Executor.Guided,
                capabilityNeeded = Capability.WRITE_SHARED_STORAGE,
                estimatedSeconds = TimeConstants.CONFIG_WRITE_MACHINE,
                handsOnSeconds = if (canWriteShared) 0 else TimeConstants.GUIDED_IN_APP_SETUP_HANDS_ON))
        }

        // Phase 6: emulator device defaults (L2), where we have them. Some live in Android/data and
        // need elevated access.
        for (e in emulators) {
            if (e.configFormat == ConfigFormat.NONE) continue
            if (com.ramrom.emumanager.engine.config.SettingsProfiles.defaults(bundle, e.id, plan.targetDeviceId, plan.chipFamily).isEmpty()) continue
            val auto = if (e.pathsNeedElevated) canWriteAppData else canWriteShared || canWriteAppData
            add(Step(id("config", e.id), StepType.WriteEmulatorConfig, e.id, listOf(ready.getValue(e.id)),
                if (auto) Executor.Auto else Executor.Guided,
                capabilityNeeded = if (e.pathsNeedElevated) Capability.WRITE_APP_DATA else Capability.WRITE_SHARED_STORAGE,
                estimatedSeconds = if (auto) TimeConstants.CONFIG_WRITE_MACHINE else 0,
                handsOnSeconds = if (auto) 0 else TimeConstants.GUIDED_IN_APP_SETUP_HANDS_ON))
        }

        // Phase 7: stage BIOS the user already has (found on S09). Missing BIOS isn't a step; S09 reports it.
        plan.bios?.found?.forEach { systemId ->
            val emu = plan.systems.firstOrNull { it.systemId == systemId }?.emulatorId?.let { emulatorMap[it] }
            val elevated = emu?.pathsNeedElevated == true
            val auto = if (elevated) canWriteAppData else canWriteShared || canWriteAppData
            add(Step(id("bios", systemId), StepType.StageBios, systemId, listOfNotNull(emu?.id?.let(ready::get)),
                if (auto) Executor.Auto else Executor.Guided,
                capabilityNeeded = if (elevated) Capability.WRITE_APP_DATA else Capability.WRITE_SHARED_STORAGE,
                estimatedSeconds = TimeConstants.BIOS_STAGE_MACHINE,
                handsOnSeconds = if (auto) 0 else TimeConstants.GUIDED_IN_APP_SETUP_HANDS_ON))
        }

        // Phase 8: folder picks the user does inside each emulator (Android's folder picker can't be automated).
        for (e in emulators) {
            if (e.folderPick?.needed != true) continue
            val granted = e.id in inventory.grantedFolderTrees || systemsOf[e.id].orEmpty().any { it in inventory.grantedFolderTrees }
            add(Step(id("folder_pick", e.id), StepType.GuidedFolderPick, e.id, listOf(ready.getValue(e.id)) + folderIds, Executor.Guided,
                handsOnSeconds = if (granted) 0 else TimeConstants.GUIDED_FOLDER_PICK_HANDS_ON,
                status = if (granted) StepStatus.AlreadyDone else StepStatus.Pending,
                verify = VerifySpec("folderGrant", e.id)))
        }

        // Phase 9: in-app setup only the user can do (keys, firmware, first-run wizards).
        if (frontend != null && !inventory.esdeConfigured) {
            add(Step(id("in_app", frontend.id), StepType.GuidedInAppSetup, frontend.id, listOf(ready.getValue(frontend.id)), Executor.Guided,
                handsOnSeconds = TimeConstants.GUIDED_IN_APP_SETUP_HANDS_ON))
        }
        for (e in emulators) {
            if (e.inAppSetup.isEmpty()) continue
            add(Step(id("in_app", e.id), StepType.GuidedInAppSetup, e.id, listOf(ready.getValue(e.id)), Executor.Guided,
                handsOnSeconds = TimeConstants.GUIDED_IN_APP_SETUP_HANDS_ON * e.inAppSetup.size))
        }

        // Phase 10: health check over everything.
        add(Step(id("health", "all"), StepType.HealthCheck, "all", steps.filter { it.status == StepStatus.Pending }.map { it.id },
            Executor.Auto, estimatedSeconds = 5))

        validateDAG(steps)
        return CompiledPlan(steps, plan.bios)
    }

    private fun validateDAG(steps: List<Step>) {
        val stepIds = steps.map { it.id }.toSet()

        // Check every dependency exists
        for (step in steps) {
            for (dep in step.dependsOn) {
                require(stepIds.contains(dep)) {
                    "Step ${step.id} depends on $dep which does not exist"
                }
            }
        }

        // Check for cycles using DFS
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        fun hasCycle(nodeId: String): Boolean {
            visited.add(nodeId)
            recursionStack.add(nodeId)

            val step = steps.find { it.id == nodeId } ?: return false
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

        for (step in steps) {
            if (step.id !in visited) {
                require(!hasCycle(step.id)) {
                    "Dependency cycle detected in step ${step.id}"
                }
            }
        }
    }
}
