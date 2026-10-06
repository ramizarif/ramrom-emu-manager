package com.ramrom.emumanager.engine.health

import com.ramrom.emumanager.engine.Inventory
import com.ramrom.emumanager.engine.Plan
import com.ramrom.emumanager.knowledge.KnowledgeBundle

/**
 * P13: compares the plan with what's really on the device. Pure: rules read the [Inventory] and
 * nothing else, so S14 can re-run them cheaply after every fix.
 *
 * Rules that need facts we can't read yet stay silent rather than guess: folder grants only when
 * [Inventory.grantsKnown], BIOS in Android/data only when elevated. "Config changed by the user"
 * and "emulator updated since our write" arrive with P09's change history.
 */
class HealthChecker(private val bundle: KnowledgeBundle) {
    private val systems = bundle.systems.associateBy { it.id }
    private val emulators = bundle.emulators.associateBy { it.id }
    private fun name(systemId: String) = systems[systemId]?.name ?: systemId

    fun check(plan: Plan, inventory: Inventory, selectedSystems: Set<String>? = null, accessLost: Boolean = false): List<HealthIssue> {
        val chosen = plan.systems.filter { selectedSystems == null || it.systemId in selectedSystems }
        val installed = inventory.installedPackages
        fun isInstalled(emuId: String) = emulators[emuId]?.packages?.any { it in installed } == true
        val issues = mutableListOf<HealthIssue>()
        val frontend = plan.frontendId?.let { id -> bundle.frontends.firstOrNull { it.id == id } }

        // Apps: one issue per missing emulator, naming every system that waits on it.
        chosen.groupBy { it.emulatorId }.forEach { (emuId, list) ->
            val emu = emulators[emuId] ?: return@forEach
            if (isInstalled(emuId)) return@forEach
            val names = list.joinToString(", ") { name(it.systemId) }
            issues += HealthIssue("app_missing_$emuId", IssueSeverity.BLOCKER, "${emu.name} isn't installed",
                "You need it to play $names.", listOf("App: ${emu.packages.joinToString(" or ")}"), FixAction.InstallApp(emuId),
                list.singleOrNull()?.systemId)
        }

        if (frontend != null) {
            val esdeHere = frontend.packages.any { it in installed }
            if (!esdeHere) issues += HealthIssue("frontend_missing", IssueSeverity.BLOCKER, "ES-DE isn't installed",
                "It's the frontend in your plan: your games show up there.", emptyList(), FixAction.InstallApp(frontend.id))
            else if (!inventory.esdeConfig.installed) issues += HealthIssue("frontend_first_run", IssueSeverity.WARNING, "Open ES-DE once",
                "It finishes setting itself up the first time it runs.", listOf("Not found yet: ${inventory.esdeConfig.configPath}"), FixAction.OpenAppSetup(frontend.id))

            // ROM folders, named the frontend's way.
            for (s in chosen) {
                val folderName = frontend.systemFolders[s.systemId] ?: s.systemId
                val folder = inventory.romFolders.firstOrNull { it.folderName == folderName }
                val path = "${inventory.esdeConfig.romDirectory}/$folderName"
                if (folder == null) issues += HealthIssue("folder_missing_${s.systemId}", IssueSeverity.WARNING, "No ${name(s.systemId)} folder",
                    "ES-DE looks for ${name(s.systemId)} games in ROMs/$folderName.", listOf("Missing: $path"), FixAction.CreateFolder(path), s.systemId)
                else {
                    // Judged against the emulator chosen for this system, not every emulator that could run it.
                    val emu = emulators[s.emulatorId]
                    val emuName = emu?.name ?: "The emulator"
                    val opens = emu?.romExtensions?.map { it.lowercase() }?.toSet().orEmpty()
                    val strayNames = folder.strays.map { it.fileName }.toSet()
                    val candidates = folder.files.filter { it !in strayNames }
                    val games = if (opens.isEmpty()) candidates.size else candidates.count { it.substringAfterLast('.').lowercase() in opens }
                    val unplayable = if (opens.isEmpty()) emptyList() else candidates.filter { it.substringAfterLast('.').lowercase() !in opens }
                    if (unplayable.isNotEmpty()) {
                        val archives = unplayable.any { it.substringAfterLast('.').lowercase() in ARCHIVES }
                        issues += HealthIssue("folder_unplayable_${s.systemId}", if (games == 0) IssueSeverity.WARNING else IssueSeverity.INFO,
                            "${unplayable.size} file${if (unplayable.size == 1) "" else "s"} in ROMs/$folderName $emuName can't open",
                            if (archives) "Compressed files need unpacking first." else "$emuName opens ${opens.joinToString(", ") { ".$it" }} files.",
                            unplayable.take(5) + listOfNotNull(if (unplayable.size > 5) "and ${unplayable.size - 5} more" else null), FixAction.None, s.systemId)
                    } else if (games == 0) issues += HealthIssue("folder_empty_${s.systemId}", IssueSeverity.INFO, "No ${name(s.systemId)} games yet",
                        "Put your ${name(s.systemId)} games in ROMs/$folderName and they'll show up.", listOf("Folder: ${folder.folderPath}"), FixAction.None, s.systemId)
                    folder.strays.groupBy { it.belongsTo }.forEach { (other, files) ->
                        val otherFolder = frontend.systemFolders[other] ?: other
                        issues += HealthIssue("stray_${s.systemId}_$other", IssueSeverity.WARNING,
                            "${files.size} ${name(other)} file${if (files.size == 1) "" else "s"} in ROMs/$folderName",
                            "ES-DE won't find ${if (files.size == 1) "it" else "them"} there. Move ${if (files.size == 1) "it" else "them"} to ROMs/$otherFolder.",
                            files.take(5).map { it.fileName } + listOfNotNull(if (files.size > 5) "and ${files.size - 5} more" else null), FixAction.None, s.systemId)
                    }
                }
            }
        }

        // BIOS: only what the system's chosen emulator needs, only the required files.
        for (s in chosen) {
            if (!isInstalled(s.emulatorId)) continue
            val needed = bundle.bios.filter { it.system == s.systemId && it.required && it.targets.any { t -> t.emulatorId == s.emulatorId } }
            if (needed.isEmpty()) continue
            val have = inventory.biosFiles.any { it.systemId == s.systemId && it.emulatorId == s.emulatorId }
            when {
                have -> Unit
                s.systemId in inventory.biosUnchecked -> issues += HealthIssue("bios_unchecked_${s.systemId}", IssueSeverity.INFO,
                    "We can't check the ${name(s.systemId)} BIOS", "It lives in ${emulators[s.emulatorId]?.name}'s private folder. Connect Shizuku and we can look.",
                    needed.map { it.fileName }.distinct(), FixAction.ReconnectAccess, s.systemId)
                else -> issues += HealthIssue("bios_missing_${s.systemId}", IssueSeverity.BLOCKER, "${name(s.systemId)} needs a BIOS file",
                    "${emulators[s.emulatorId]?.name} won't start ${name(s.systemId)} games without one. Any one of these works.",
                    needed.map { it.fileName }.distinct(), FixAction.StageBios(s.systemId), s.systemId)
            }
        }

        // Per-app access.
        for (emuId in chosen.map { it.emulatorId }.distinct()) {
            val emu = emulators[emuId] ?: continue
            val pkg = emu.packages.firstOrNull { it in installed } ?: continue
            val perms = inventory.appPermissions.firstOrNull { it.packageId == pkg } ?: continue
            if (emu.declaresAllFilesAccess && !perms.grantedManageExternalStorage) issues += HealthIssue("all_files_$emuId", IssueSeverity.BLOCKER,
                "${emu.name} can't read your games", "It needs \"Allow access to manage all files\".", listOf("App: $pkg"), FixAction.GrantAllFilesAccess(pkg))
            perms.missingPermissions.forEach { p -> issues += HealthIssue("perm_${emuId}_${p.substringAfterLast('.')}", IssueSeverity.WARNING,
                "${emu.name} is missing a permission", "It asks for ${com.ramrom.emumanager.engine.PermissionNames.of(p)}.", listOf(p), FixAction.GrantPermission(pkg, p)) }
            // Folder picks: only when we could read the grants.
            if (inventory.grantsKnown && emu.folderPick?.needed == true) {
                val missing = chosen.filter { it.emulatorId == emuId }.filter { s ->
                    inventory.folderGrants.none { g -> g.packageId == pkg && g.granted && (g.systemId == s.systemId || g.systemId.isEmpty()) }
                }
                if (missing.isNotEmpty()) issues += HealthIssue("folder_pick_$emuId", IssueSeverity.BLOCKER, "${emu.name} can't see your games folder",
                    "Android only lets you choose it inside ${emu.name}.", missing.map { "ROMs/${frontend?.systemFolders?.get(it.systemId) ?: it.systemId}" },
                    FixAction.GrantFolder(pkg, missing.first().systemId), missing.singleOrNull()?.systemId)
            }
        }

        // Media ES-DE scraped for games that are gone: a tip, grouped per system.
        inventory.orphanedMedia.filter { o -> chosen.any { it.systemId == o.systemId } }.groupBy { it.systemId }.forEach { (sys, list) ->
            issues += HealthIssue("orphans_$sys", IssueSeverity.INFO, "${list.size} leftover ${name(sys)} image${if (list.size == 1) "" else "s"}",
                "Art for games that aren't in your folder any more.", list.take(5).map { it.filePath.substringAfterLast('/') }, FixAction.None, sys)
        }

        if (accessLost) issues += HealthIssue("access_off", IssueSeverity.INFO, "Shizuku is off",
            "Fixes that need it wait until it's back on.", emptyList(), FixAction.ReconnectAccess)

        return issues.sortedBy { it.severity.ordinal }
    }

    private companion object {
        val ARCHIVES = setOf("7z", "zip", "rar", "gz", "tar")
    }
}
