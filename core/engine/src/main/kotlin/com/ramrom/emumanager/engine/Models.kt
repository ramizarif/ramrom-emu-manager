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
    val frontendId: String?,
    val systems: List<PlanSystem>,
    val bios: BiosSummary?
) {
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
    val folderGrants: List<com.ramrom.emumanager.engine.inventory.FolderGrant> = emptyList(),
    val biosFiles: List<com.ramrom.emumanager.engine.inventory.BiosFile> = emptyList(),
    val appPermissions: List<com.ramrom.emumanager.engine.inventory.AppPermissionState> = emptyList(),
    val esdeConfig: com.ramrom.emumanager.engine.inventory.EsdeConfig = com.ramrom.emumanager.engine.inventory.EsdeConfig(),
    val orphanedMedia: List<com.ramrom.emumanager.engine.inventory.OrphanedMedia> = emptyList(),
    /** False when folder grants couldn't be read (needs elevated dumpsys): grant rules stay quiet. */
    val grantsKnown: Boolean = false,
    /** Systems whose BIOS lives where we can't look (Android/data without Shizuku). */
    val biosUnchecked: Set<String> = emptySet(),
)
