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
 */
data class Inventory(
    val installedPackages: Set<String> = emptySet(),
    val grantedFolderTrees: Set<String> = emptySet(), // e.g., "switch", "wii"
    val existingFolders: Set<String> = emptySet(),
    val esdeConfigured: Boolean = false
)
