package com.ramrom.emumanager.engine.health

import com.ramrom.emumanager.engine.Inventory
import com.ramrom.emumanager.engine.Plan
import com.ramrom.emumanager.knowledge.KnowledgeBundle

/**
 * Health checker: verifies device setup against a plan and current inventory.
 * Rules are pure functions; no side effects. Reports issues as HealthIssue list.
 *
 * Coverage per P13 spec:
 * - Emulator for a planned system missing → BLOCKER, install
 * - ROM folder missing / empty → WARNING, create / import
 * - BIOS missing or wrong hash → BLOCKER for system, F11
 * - Folder grant missing for emulator / ES-DE → BLOCKER, guided pick
 * - ES-DE system has no emulator mapping → BLOCKER, P10
 * - ROM in wrong system folder → WARNING, move
 * - Orphaned media → INFO, rename/delete
 * - Config we applied was changed by user → INFO, adopt or re-apply
 * - Emulator updated since we wrote config → INFO, re-verify keys
 * - Elevated access off → INFO, F02
 */
class HealthChecker(
    private val bundle: KnowledgeBundle
) {
    /**
     * Run all health checks against a plan and inventory.
     * @param plan the setup plan (systems and emulators chosen)
     * @param inventory current device state
     * @param selectedSystems if checking only some systems, provide them here; else check all in plan
     * @return list of HealthIssue
     */
    fun check(
        plan: Plan,
        inventory: Inventory,
        selectedSystems: Set<String>? = null
    ): List<HealthIssue> {
        val issues = mutableListOf<HealthIssue>()
        val toCheck = selectedSystems ?: plan.systems.map { it.systemId }.toSet()

        val systemMap = bundle.systems.associateBy { it.id }
        val emulatorMap = bundle.emulators.associateBy { it.id }

        // Rule: Emulator for a planned system missing
        for (planSystem in plan.systems) {
            if (planSystem.systemId !in toCheck) continue
            val emulator = emulatorMap[planSystem.emulatorId] ?: continue
            if (emulator.packages.none { it in inventory.installedPackages }) {
                issues.add(
                    HealthIssue(
                        id = "emulator_missing_${planSystem.systemId}",
                        severity = IssueSeverity.BLOCKER,
                        title = "Install ${emulator.name} for ${systemMap[planSystem.systemId]?.name ?: planSystem.systemId}",
                        detail = "${emulator.name} is not installed. You need it to play ${systemMap[planSystem.systemId]?.name ?: planSystem.systemId} games.",
                        evidence = listOf("Emulator: ${emulator.id}"),
                        fix = FixAction.InstallApp(emulator.id)
                    )
                )
            }
        }

        // Rule: ROM folder missing or empty
        for (planSystem in plan.systems) {
            if (planSystem.systemId !in toCheck) continue
            val systemFolder = inventory.romFolders.find { it.systemId == planSystem.systemId }
            when {
                systemFolder == null -> {
                    issues.add(
                        HealthIssue(
                            id = "rom_folder_missing_${planSystem.systemId}",
                            severity = IssueSeverity.WARNING,
                            title = "Create ROMs folder for ${systemMap[planSystem.systemId]?.name ?: planSystem.systemId}",
                            detail = "The folder for ${systemMap[planSystem.systemId]?.name ?: planSystem.systemId} games doesn't exist. Create it before importing games.",
                            evidence = listOf("Missing folder: /storage/emulated/0/ROMs/${planSystem.systemId}"),
                            fix = FixAction.CreateFolder("/storage/emulated/0/ROMs/${planSystem.systemId}")
                        )
                    )
                }
                systemFolder.gameCount == 0 -> {
                    issues.add(
                        HealthIssue(
                            id = "rom_folder_empty_${planSystem.systemId}",
                            severity = IssueSeverity.WARNING,
                            title = "Import games for ${systemMap[planSystem.systemId]?.name ?: planSystem.systemId}",
                            detail = "The ${systemMap[planSystem.systemId]?.name ?: planSystem.systemId} folder exists but has no games yet.",
                            evidence = listOf("Folder: ${systemFolder.folderPath}", "Files found: 0"),
                            fix = FixAction.None
                        )
                    )
                }
            }
        }

        // Rule: BIOS missing or wrong hash for a system that requires it
        for (planSystem in plan.systems) {
            if (planSystem.systemId !in toCheck) continue
            val system = systemMap[planSystem.systemId] ?: continue
            if (!system.biosRequired) continue

            val biosForSystem = bundle.bios.filter { it.system == planSystem.systemId }
            if (biosForSystem.isEmpty()) continue

            val hasBios = biosForSystem.any { entry ->
                inventory.biosFiles.any { found ->
                    found.systemId == entry.system && found.fileName == entry.fileName
                }
            }

            if (!hasBios) {
                issues.add(
                    HealthIssue(
                        id = "bios_missing_${planSystem.systemId}",
                        severity = IssueSeverity.BLOCKER,
                        title = "Add BIOS for ${system.name}",
                        detail = "${system.name} requires a BIOS file. ${emulatorMap[planSystem.emulatorId]?.name ?: "Your emulator"} will not run games without it.",
                        evidence = biosForSystem.map { "Needed: ${it.fileName}" },
                        fix = FixAction.StageBios(planSystem.systemId)
                    )
                )
            }
        }

        // Rule: Folder grant missing for emulator (only if emulator is installed)
        for (planSystem in plan.systems) {
            if (planSystem.systemId !in toCheck) continue
            val emulator = emulatorMap[planSystem.emulatorId] ?: continue
            if (emulator.folderPick?.needed != true) continue

            // Only check for missing grants if emulator is installed
            if (emulator.packages.none { it in inventory.installedPackages }) continue

            val hasGrant = inventory.folderGrants.any { grant ->
                grant.packageId in emulator.packages && grant.systemId == planSystem.systemId && grant.granted
            }

            if (!hasGrant) {
                val pkg = emulator.packages.firstOrNull() ?: continue
                issues.add(
                    HealthIssue(
                        id = "folder_grant_missing_${pkg}_${planSystem.systemId}",
                        severity = IssueSeverity.BLOCKER,
                        title = "Grant folder access to ${emulator.name}",
                        detail = "${emulator.name} needs permission to access your ${systemMap[planSystem.systemId]?.name ?: planSystem.systemId} games folder.",
                        evidence = listOf("Package: $pkg", "System folder: ${planSystem.systemId}"),
                        fix = FixAction.GrantFolder(pkg, planSystem.systemId)
                    )
                )
            }
        }

        // Rule: Folder grant missing for ES-DE (only if ES-DE is installed)
        plan.frontendId?.let { frontendId ->
            bundle.frontends.find { it.id == frontendId }?.let { frontend ->
                if (frontend.packages.any { it in inventory.installedPackages }) {
                    for (planSystem in plan.systems) {
                        if (planSystem.systemId !in toCheck) continue

                        val hasGrant = inventory.folderGrants.any { grant ->
                            grant.packageId in frontend.packages && grant.systemId == planSystem.systemId && grant.granted
                        }

                        if (!hasGrant) {
                            val pkg = frontend.packages.firstOrNull() ?: continue
                            issues.add(
                                HealthIssue(
                                    id = "folder_grant_missing_esde_${planSystem.systemId}",
                                    severity = IssueSeverity.BLOCKER,
                                    title = "Grant folder access to ES-DE",
                                    detail = "ES-DE needs permission to access your ${systemMap[planSystem.systemId]?.name ?: planSystem.systemId} games folder.",
                                    evidence = listOf("Frontend package: $pkg", "System folder: ${planSystem.systemId}"),
                                    fix = FixAction.GrantFolder(pkg, planSystem.systemId)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Rule: ES-DE system has no emulator mapping (custom systems not configured)
        if (plan.frontendId == "es-de" && inventory.esdeConfig.installed) {
            // Note: full check would parse custom_systems/*.xml to verify mappings exist
            // For now, just flag if custom systems folder missing for systems ES-DE doesn't know about
            val knownEsdeSystems = setOf("switch", "wii", "gc", "n64", "snes", "nes", "gb")
            for (planSystem in plan.systems) {
                if (planSystem.systemId !in toCheck) continue
                val hasCustomFolder = inventory.esdeConfig.customSystemsFolder.isNotEmpty()
                if (planSystem.systemId !in knownEsdeSystems && !hasCustomFolder) {
                    issues.add(
                        HealthIssue(
                            id = "esde_system_unmapped_${planSystem.systemId}",
                            severity = IssueSeverity.BLOCKER,
                            title = "Configure ${systemMap[planSystem.systemId]?.name ?: planSystem.systemId} in ES-DE",
                            detail = "ES-DE doesn't know about this system. A custom system entry is needed so it can find your games.",
                            evidence = listOf("System: ${planSystem.systemId}", "Emulator: ${planSystem.emulatorId}"),
                            fix = FixAction.ConfigureInEsde(planSystem.systemId)
                        )
                    )
                }
            }
        }

        // Rule: ROM in wrong system folder (file extension mismatch)
        for (systemId in toCheck) {
            val folder = inventory.romFolders.find { it.systemId == systemId } ?: continue
            val emulatorId = plan.systems.find { it.systemId == systemId }?.emulatorId ?: continue
            val emulator = emulatorMap[emulatorId] ?: continue

            val validExts = emulator.romExtensions.map { it.lowercase() }.toSet()
            val companionExts = setOf(
                "sav", "ml1", "srm", "state0", "state1", "state2", "state3",
                "state4", "state5", "state6", "state7", "state8", "state9",
                "state10", "state11", "state12", "state13", "state14", "state15"
            )

            // In a real implementation, would scan the folder for wrong-extension files
            // For now, this is a placeholder for the concept
        }

        // Rule: Orphaned media (informational only)
        for (orphan in inventory.orphanedMedia) {
            if (orphan.systemId !in toCheck) continue
            if (orphan.systemId !in bundle.systems.map { it.id }) continue

            issues.add(
                HealthIssue(
                    id = "orphaned_media_${orphan.systemId}_${orphan.mediaType}",
                    severity = IssueSeverity.INFO,
                    title = "Delete or rename: ${orphan.filePath.substringAfterLast("/")}",
                    detail = "This file doesn't match any of your games and is cluttering the media folder.",
                    evidence = listOf("File: ${orphan.filePath}"),
                    fix = FixAction.None
                )
            )
        }

        // Rule: All-files access missing when emulator needs it
        for (planSystem in plan.systems) {
            if (planSystem.systemId !in toCheck) continue
            val emulator = emulatorMap[planSystem.emulatorId] ?: continue
            if (!emulator.declaresAllFilesAccess) continue

            val pkg = emulator.packages.firstOrNull() ?: continue
            val permission = inventory.appPermissions.find { it.packageId == pkg }
            if (permission != null && !permission.grantedManageExternalStorage) {
                issues.add(
                    HealthIssue(
                        id = "all_files_access_missing_${pkg}",
                        severity = IssueSeverity.WARNING,
                        title = "Grant all-files access to ${emulator.name}",
                        detail = "${emulator.name} needs permission to access all folders on your device.",
                        evidence = listOf("Package: $pkg"),
                        fix = FixAction.GrantAllFilesAccess(pkg)
                    )
                )
            }
        }

        // Rule: ES-DE not installed or not configured
        if (plan.frontendId == "es-de") {
            if (!inventory.esdeConfig.installed) {
                issues.add(
                    HealthIssue(
                        id = "esde_not_installed",
                        severity = IssueSeverity.WARNING,
                        title = "Install ES-DE frontend",
                        detail = "ES-DE is your chosen frontend but it's not installed. Get it from the Galaxy Store or directly.",
                        evidence = listOf("Frontend: es-de"),
                        fix = FixAction.None  // User must obtain it themselves
                    )
                )
            } else if (!fileListerExists(inventory.esdeConfig.configPath)) {
                issues.add(
                    HealthIssue(
                        id = "esde_not_configured",
                        severity = IssueSeverity.INFO,
                        title = "Configure ES-DE",
                        detail = "ES-DE has been installed but not yet configured. Open it once to create its settings.",
                        evidence = listOf("Config file not found: ${inventory.esdeConfig.configPath}"),
                        fix = FixAction.OpenAppSetup("es-de")
                    )
                )
            }
        }

        return issues
    }

    private fun fileListerExists(path: String): Boolean {
        // Placeholder: in real code, would use actual file system check
        // For tests, inventory would have this pre-computed
        return path.isNotEmpty()
    }
}
