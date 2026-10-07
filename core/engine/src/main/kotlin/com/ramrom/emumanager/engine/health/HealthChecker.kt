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
        }

        // ES-DE's per-system choice: its defaults are RetroArch cores for several systems, so a plan
        // that picks a standalone emulator needs one, or games open in the wrong app.
        val choices = inventory.esdeChoices
        if (frontend != null && choices != null && frontend.packages.any { it in installed }) {
            for (s in chosen) {
                val emu = emulators[s.emulatorId] ?: continue
                if (!isInstalled(emu.id)) continue
                val pkg = emu.packages.firstOrNull { it in installed }
                val folder = com.ramrom.emumanager.engine.config.EsdeLaunch.folderFor(frontend, s.systemId,
                    inventory.esdeCustomDefaults.keys + choices.filterValues { it != null }.keys)
                val custom = inventory.esdeCustomDefaults[folder]
                val wanted = com.ramrom.emumanager.engine.config.EsdeLaunch.wanted(frontend, s.systemId, emu, pkg, custom) ?: continue
                val current = choices[folder]
                if (com.ramrom.emumanager.engine.config.EsdeLaunch.opens(current, emu, frontend)) continue
                val opensIn = current ?: custom ?: frontend.defaultLabels[s.systemId] ?: "ES-DE's default"
                // Labels without "(Standalone)" are RetroArch cores; "in Dolphin" alone would read as fine.
                val opensInApp = if ("(Standalone)" in opensIn || opensIn == "ES-DE's default") opensIn.removeSuffix(" (Standalone)") else "RetroArch ($opensIn)"
                issues += HealthIssue("esde_choice_${s.systemId}", IssueSeverity.WARNING, "ES-DE opens ${name(s.systemId)} games in $opensInApp",
                    "Your plan plays them in ${emu.name}. ES-DE needs to be told once, per system.",
                    listOf("Now: $opensIn", "Should be: $wanted", "File: ES-DE/gamelists/$folder/gamelist.xml"),
                    FixAction.ConfigureInEsde(s.systemId), s.systemId)
            }
        }

        // ROM folders, named the ES-DE way even without ES-DE: it's the layout emulators get pointed at too.
        val layout = frontend ?: bundle.frontends.firstOrNull()
        val looksHere = if (frontend != null) "ES-DE looks for" else "Keep your"
        if (layout != null) {
            for (s in chosen) {
                // The standard folder or an alias the setup uses (the Thor keeps PS1 in ROMs/ps1, not psx):
                // the one a custom ES-DE system points at first, else the one with the most files.
                val names = listOf(layout.systemFolders[s.systemId] ?: s.systemId) + layout.folderAliases[s.systemId].orEmpty()
                val present = names.mapNotNull { n -> inventory.romFolders.firstOrNull { it.folderName == n } }
                val folder = present.firstOrNull { it.folderName in inventory.esdeCustomDefaults } ?: present.maxByOrNull { it.files.size }
                val folderName = folder?.folderName ?: names.first()
                val path = "${inventory.esdeConfig.romDirectory}/$folderName"
                if (folder == null) issues += HealthIssue("folder_missing_${s.systemId}", IssueSeverity.WARNING, "No ${name(s.systemId)} folder",
                    "$looksHere ${name(s.systemId)} games in ROMs/$folderName.", listOf("Missing: $path"), FixAction.CreateFolder(path), s.systemId)
                else {
                    // Judged against the emulator chosen for this system, not every emulator that could run it.
                    val emu = emulators[s.emulatorId]
                    val emuName = emu?.name ?: "The emulator"
                    val opens = emu?.romExtensions?.map { it.lowercase() }?.toSet().orEmpty()
                    val strayNames = folder.strays.map { it.fileName }.toSet()
                    val candidates = folder.files.filter { it !in strayNames }
                    val games = if (opens.isEmpty()) candidates.size else candidates.count { it.substringAfterLast('.').lowercase() in opens }
                    // Loose files only: a subfolder of non-games is the emulator's own (PPSSPP keeps saves in ROMs/psp/PSP).
                    val unplayable = if (opens.isEmpty()) emptyList() else candidates.filter { '/' !in it && it.substringAfterLast('.').lowercase() !in opens }
                    if (unplayable.isNotEmpty()) {
                        val archives = unplayable.any { it.substringAfterLast('.').lowercase() in ARCHIVES }
                        issues += HealthIssue("folder_unplayable_${s.systemId}", if (games == 0) IssueSeverity.WARNING else IssueSeverity.INFO,
                            "${unplayable.size} file${if (unplayable.size == 1) "" else "s"} in ROMs/$folderName $emuName can't open",
                            if (archives) "Compressed files need unpacking first." else "$emuName opens ${opens.joinToString(", ") { ".$it" }} files.",
                            unplayable.take(5) + listOfNotNull(if (unplayable.size > 5) "and ${unplayable.size - 5} more" else null), FixAction.None, s.systemId)
                    } else if (games == 0) issues += HealthIssue("folder_empty_${s.systemId}", IssueSeverity.INFO, "No ${name(s.systemId)} games yet",
                        "Put your ${name(s.systemId)} games in ROMs/$folderName and they'll show up.", listOf("Folder: ${folder.folderPath}"), FixAction.None, s.systemId)
                    folder.strays.groupBy { it.belongsTo }.forEach { (other, files) ->
                        val otherFolder = layout.systemFolders[other] ?: other
                        issues += HealthIssue("stray_${s.systemId}_$other", IssueSeverity.WARNING,
                            "${files.size} ${name(other)} file${if (files.size == 1) "" else "s"} in ROMs/$folderName",
                            "${if (frontend != null) "ES-DE won't find" else "Its emulator won't open"} ${if (files.size == 1) "it" else "them"} there. Move ${if (files.size == 1) "it" else "them"} to ROMs/$otherFolder.",
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
                    // Any installed build will do: on the Thor ES-DE launches Eden Nightly, which holds the grant.
                    inventory.folderGrants.none { g -> g.packageId in emu.packages && g.packageId in installed && g.granted && (g.systemId == s.systemId || g.systemId.isEmpty()) }
                }
                // ES-DE hands the emulator the game itself, so the pick only matters for browsing inside the emulator.
                val viaEsde = frontend != null && frontend.packages.any { it in installed }
                if (missing.isNotEmpty()) issues += HealthIssue("folder_pick_$emuId", if (viaEsde) IssueSeverity.INFO else IssueSeverity.BLOCKER,
                    if (viaEsde) "${emu.name} doesn't list your games itself" else "${emu.name} can't see your games folder",
                    if (viaEsde) "Games still open from ES-DE. To see them inside ${emu.name} too, choose the folder there."
                    else "Android only lets you choose it inside ${emu.name}.", missing.map { "ROMs/${frontend?.systemFolders?.get(it.systemId) ?: it.systemId}" },
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
