package com.ramrom.emumanager.engine.inventory

/**
 * An installed app with version info.
 */
data class InstalledApp(
    val packageId: String,
    val versionName: String = "",
    val versionCode: Int = 0
)

/**
 * A ROM folder with game count.
 */
data class RomFolder(
    val systemId: String,      // e.g., "switch", "wii"
    val folderPath: String,    // absolute path like "/storage/emulated/0/ROMs/switch"
    val gameCount: Int = 0     // number of files with valid extensions for this system
)

/**
 * A folder grant (SAF tree URI) for an emulator or frontend.
 */
data class FolderGrant(
    val packageId: String,
    val systemId: String,      // e.g., "switch" (what folder it gives access to)
    val granted: Boolean = true
)

/**
 * BIOS file info discovered during inventory scan.
 */
data class BiosFile(
    val systemId: String,
    val fileName: String,
    val filePath: String,
    val md5: String = "",      // could be computed if needed
    val size: Long = 0
)

/**
 * Orphaned media (files in downloaded_media that don't match any ROM).
 */
data class OrphanedMedia(
    val systemId: String,
    val mediaType: String,     // e.g., "covers", "screenshots"
    val filePath: String,      // absolute path
    val matchedRomBasename: String? = null  // which ROM basenames could have matched
)

/**
 * App permission state (all-files access).
 */
data class AppPermissionState(
    val packageId: String,
    val declaresManageExternalStorage: Boolean = false,
    val grantedManageExternalStorage: Boolean = false
)

/**
 * ES-DE configuration state.
 */
data class EsdeConfig(
    val installed: Boolean = false,
    val configPath: String = "",  // absolute path to es_settings.xml
    val romDirectory: String = "/storage/emulated/0/ROMs",
    val mediaDirectory: String = "",
    val customSystemsFolder: String = "",
    val themesFolder: String = "",
    val customThemesEnabled: Boolean = false
)
