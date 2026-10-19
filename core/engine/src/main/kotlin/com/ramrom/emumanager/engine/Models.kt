package com.ramrom.emumanager.engine

import com.ramrom.emumanager.access.Capability

/**
 * Input models for the plan compiler.
 */

/**
 * A setup plan specifying target device, frontend, systems and emulators.
 */
data class Plan(
    val targetDeviceId: String?,
    val chipFamily: String,
    /** The first frontend, kept for older callers; [frontendIds] is the whole list. */
    val frontendId: String?,
    val systems: List<PlanSystem>,
    val bios: BiosSummary?,
    /** Every frontend the user chose, all set up alike (ES-DE, Cocoon, iiSU, RetroHrai!). */
    val frontendIds: List<String> = listOfNotNull(frontendId),
) {
    /** The chosen frontends' profiles, in the user's order. */
    fun frontends(bundle: com.ramrom.emumanager.knowledge.KnowledgeBundle) = frontendIds.mapNotNull { id -> bundle.frontends.firstOrNull { it.id == id } }

    /** ES-DE's profile when it's one of them: gamelists, labels and custom systems are ES-DE's alone. */
    fun esde(bundle: com.ramrom.emumanager.knowledge.KnowledgeBundle) = bundle.esde?.takeIf { it.id in frontendIds }

    data class PlanSystem(
        val systemId: String,
        val emulatorId: String
    )
}

/**
 * BIOS inventory summary: what files are available and what's needed.
 */
data class BiosSummary(
    val found: Set<String> = emptySet(),    // system IDs with valid BIOS
    val missing: Set<String> = emptySet(),  // system IDs needing BIOS
    val wrongVersion: Set<String> = emptySet() // system IDs with wrong-version BIOS
)

/**
 * Current device inventory: what's already installed/configured.
 * Supports incremental updates via mtime cache and lazy computation.
 */
data class Inventory(
    val installedPackages: Set<String> = emptySet(),  // package IDs like "com.github.yuzu.emu.ea"
    val grantedFolderTrees: Set<String> = emptySet(), // system IDs some app holds a folder grant for
    val existingFolders: Set<String> = emptySet(),    // ROM folder names (frontend's) that exist
    val esdeConfigured: Boolean = false,
    // Extended fields for full inventory data
    val installedApps: List<com.ramrom.emumanager.engine.inventory.InstalledApp> = emptyList(),
    val romFolders: List<com.ramrom.emumanager.engine.inventory.RomFolder> = emptyList(),
    /** ES-DE's emulator choice per system folder; null when gamelists weren't read. */
    val esdeChoices: Map<String, String?>? = null,
    /** Systems the user's custom es_systems.xml redefines: name → first command's label. */
    val esdeCustomDefaults: Map<String, String> = emptyMap(),
    val folderGrants: List<com.ramrom.emumanager.engine.inventory.FolderGrant> = emptyList(),
    val biosFiles: List<com.ramrom.emumanager.engine.inventory.BiosFile> = emptyList(),
    val appPermissions: List<com.ramrom.emumanager.engine.inventory.AppPermissionState> = emptyList(),
    val esdeConfig: com.ramrom.emumanager.engine.inventory.EsdeConfig = com.ramrom.emumanager.engine.inventory.EsdeConfig(),
    val orphanedMedia: List<com.ramrom.emumanager.engine.inventory.OrphanedMedia> = emptyList(),
    /** False when folder grants couldn't be read (needs elevated dumpsys): grant rules stay quiet. */
    val grantsKnown: Boolean = false,
    /**
     * Folder picks an earlier run confirmed (step ids). Used only while [grantsKnown] is false
     * (limited mode can't read other apps' grants), so switching modes doesn't ask for them again.
     */
    val confirmedSteps: Set<String> = emptySet(),
    /** Folder grants as Android lists them, by package: shows where a frontend points (Cocoon at Roms, not ROMs). */
    val grantPaths: Map<String, List<String>> = emptyMap(),
    /** Systems whose BIOS lives where we can't look (Android/data without Shizuku). */
    val biosUnchecked: Set<String> = emptySet(),
    /** Emulators with their keys in place (Eden's prod.keys), and ones whose keys we couldn't look for. */
    val keysFound: Set<String> = emptySet(),
    val keysUnchecked: Set<String> = emptySet(),
)
