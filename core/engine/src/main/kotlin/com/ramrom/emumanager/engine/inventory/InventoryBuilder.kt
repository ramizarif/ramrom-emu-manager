package com.ramrom.emumanager.engine.inventory

import com.ramrom.emumanager.engine.Inventory
import com.ramrom.emumanager.knowledge.KnowledgeBundle

/**
 * Builds an Inventory snapshot from raw facts.
 * Pure function: takes raw data, produces Inventory.
 * No ROM hashing is performed (for performance: < 30s for 1TB library).
 */
class InventoryBuilder(
    private val bundle: KnowledgeBundle,
    private val fileLister: FileLister
) {
    /**
     * Build a complete inventory from raw facts.
     * @param installedPackages set of package IDs like "com.github.yuzu.emu.ea"
     * @param folderGrantsPerPackage map of package ID -> set of system folder names with grants
     * @param allFilesAccessPerPackage map of package ID -> whether it has MANAGE_EXTERNAL_STORAGE granted
     * @param romRootPath absolute path to ROM root, typically "/storage/emulated/0/ROMs"
     * @param esdeBasePath absolute path to ES-DE folder, typically "/storage/emulated/0/ES-DE"
     * @return complete Inventory snapshot
     */
    fun build(
        installedPackages: Set<String>,
        folderGrantsPerPackage: Map<String, Set<String>>,
        allFilesAccessPerPackage: Map<String, Boolean>,
        romRootPath: String = "/storage/emulated/0/ROMs",
        esdeBasePath: String = "/storage/emulated/0/ES-DE"
    ): Inventory {
        val installedApps = buildInstalledApps(installedPackages)
        val romFolders = buildRomFolders(romRootPath, installedApps)
        val folderGrants = buildFolderGrants(folderGrantsPerPackage)
        val grantedSystems = folderGrants.map { it.systemId }.toSet()
        val existingFolders = romFolders.map { it.systemId }.toSet()

        val appPermissions = buildAppPermissions(installedPackages, allFilesAccessPerPackage)
        val esdeConfig = buildEsdeConfig(esdeBasePath)
        val biosFiles = buildBiosFiles(bundle)
        val orphanedMedia = buildOrphanedMedia(esdeBasePath, romFolders)

        return Inventory(
            installedPackages = installedPackages,
            grantedFolderTrees = grantedSystems,
            existingFolders = existingFolders,
            esdeConfigured = esdeConfig.installed,
            installedApps = installedApps,
            romFolders = romFolders,
            folderGrants = folderGrants,
            biosFiles = biosFiles,
            appPermissions = appPermissions,
            esdeConfig = esdeConfig,
            orphanedMedia = orphanedMedia
        )
    }

    private fun buildInstalledApps(packageIds: Set<String>): List<InstalledApp> {
        // In a real implementation, this would query PackageManager
        // For now, return basic entries from the known packages
        return packageIds.map { pkg ->
            InstalledApp(packageId = pkg, versionName = "", versionCode = 0)
        }.sortedBy { it.packageId }
    }

    private fun buildRomFolders(
        romRootPath: String,
        installedApps: List<InstalledApp>
    ): List<RomFolder> {
        if (!fileLister.exists(romRootPath) || !fileLister.isDirectory(romRootPath)) {
            return emptyList()
        }

        val systemIds = bundle.systems.map { it.id }.toSet()
        val systemExtensions = buildSystemExtensionsMap()

        val folders = mutableListOf<RomFolder>()
        for (systemId in systemIds) {
            val systemFolderPath = "$romRootPath/$systemId"
            if (!fileLister.exists(systemFolderPath)) continue

            val files = fileLister.listFiles(systemFolderPath)
            val gameCount = files.count { file ->
                val extensions = systemExtensions[systemId] ?: emptyList()
                isGameFile(file, extensions)
            }

            if (files.isNotEmpty()) {
                folders.add(
                    RomFolder(
                        systemId = systemId,
                        folderPath = systemFolderPath,
                        gameCount = gameCount
                    )
                )
            }
        }

        return folders
    }

    private fun buildSystemExtensionsMap(): Map<String, List<String>> {
        val map = mutableMapOf<String, List<String>>()
        for (emulator in bundle.emulators) {
            for (systemId in emulator.systems) {
                val extensions = emulator.romExtensions.map { it.lowercase() }
                map[systemId] = extensions
            }
        }
        return map
    }

    private fun isGameFile(filePath: String, extensions: List<String>): Boolean {
        if (extensions.isEmpty()) return true
        // Ignore hidden files (starting with dot)
        if (filePath.startsWith(".")) return false

        val lower = filePath.lowercase()
        val companionExts = setOf(
            ".sav", ".ml1", ".srm", ".state0", ".state1", ".state2", ".state3",
            ".state4", ".state5", ".state6", ".state7", ".state8", ".state9",
            ".state10", ".state11", ".state12", ".state13", ".state14", ".state15"
        )
        for (ext in companionExts) {
            if (lower.endsWith(ext)) return false
        }
        return extensions.any { lower.endsWith(".$it") }
    }

    private fun buildFolderGrants(folderGrantsPerPackage: Map<String, Set<String>>): List<FolderGrant> {
        val grants = mutableListOf<FolderGrant>()
        for ((packageId, systemIds) in folderGrantsPerPackage) {
            for (systemId in systemIds) {
                grants.add(
                    FolderGrant(
                        packageId = packageId,
                        systemId = systemId,
                        granted = true
                    )
                )
            }
        }
        return grants
    }

    private fun buildAppPermissions(
        installedPackages: Set<String>,
        allFilesAccessPerPackage: Map<String, Boolean>
    ): List<AppPermissionState> {
        return installedPackages.map { pkg ->
            AppPermissionState(
                packageId = pkg,
                declaresManageExternalStorage = bundle.emulators
                    .find { emulator -> pkg in emulator.packages }
                    ?.declaresAllFilesAccess ?: false,
                grantedManageExternalStorage = allFilesAccessPerPackage[pkg] ?: false
            )
        }
    }

    private fun buildEsdeConfig(esdeBasePath: String): EsdeConfig {
        if (!fileLister.exists(esdeBasePath) || !fileLister.isDirectory(esdeBasePath)) {
            return EsdeConfig(installed = false)
        }

        val settingsPath = "$esdeBasePath/settings/es_settings.xml"
        val customSystemsPath = "$esdeBasePath/custom_systems"
        val themesPath = "$esdeBasePath/downloaded_media"
        val customThemesPath = "/storage/emulated/0/Android/data/org.es_de.frontend/files/themes"

        // ES-DE is only "installed" if the settings file exists
        val installed = fileLister.exists(settingsPath)

        return EsdeConfig(
            installed = installed,
            configPath = settingsPath,
            romDirectory = "/storage/emulated/0/ROMs",  // default, would be parsed from settings
            mediaDirectory = themesPath,
            customSystemsFolder = customSystemsPath,
            themesFolder = customThemesPath
        )
    }

    private fun buildBiosFiles(bundle: KnowledgeBundle): List<BiosFile> {
        // In a real implementation, would scan emulator BIOS folders
        // For now, return empty - health checker will verify what's needed
        return emptyList()
    }

    private fun buildOrphanedMedia(
        esdeBasePath: String,
        romFolders: List<RomFolder>
    ): List<OrphanedMedia> {
        val mediaRootPath = "$esdeBasePath/downloaded_media"
        if (!fileLister.exists(mediaRootPath)) return emptyList()

        val romBasenames = romFolders.flatMap { folder ->
            fileLister.listFiles(folder.folderPath)
                .map { it.substringBeforeLast(".") }
                .toSet()
        }.toSet()

        val orphans = mutableListOf<OrphanedMedia>()
        for (systemId in bundle.systems.map { it.id }) {
            val systemMediaPath = "$mediaRootPath/$systemId"
            if (!fileLister.exists(systemMediaPath)) continue

            val mediaTypes = bundle.frontends.find { it.id == "es-de" }?.mediaTypes?.map { it.folder } ?: emptyList()
            for (mediaType in mediaTypes) {
                val mediaTypePath = "$systemMediaPath/$mediaType"
                if (!fileLister.exists(mediaTypePath)) continue

                val files = fileLister.listFiles(mediaTypePath)
                for (file in files) {
                    val basename = file.substringBeforeLast(".")
                    if (basename !in romBasenames) {
                        orphans.add(
                            OrphanedMedia(
                                systemId = systemId,
                                mediaType = mediaType,
                                filePath = "$mediaTypePath/$file",
                                matchedRomBasename = null
                            )
                        )
                    }
                }
            }
        }

        return orphans
    }
}
