package com.ramrom.emumanager.engine.inventory

/** An app from the bundle found on the device. */
data class InstalledApp(
    val packageId: String,
    val versionName: String = "",
    val versionCode: Long = 0,
)

/** One system's ROM folder as the frontend names it (ES-DE: `ps1`, `n3ds`...). */
data class RomFolder(
    val systemId: String,
    /** Folder name under the ROM root, e.g. "switch". */
    val folderName: String,
    val folderPath: String,
    /** Files with an extension this system's emulators open. Companions (.sav, .state*) don't count. */
    val gameCount: Int = 0,
    /** Files that belong to exactly one other system, e.g. a `.cia` in `nds/` → n3ds. */
    val strays: List<StrayFile> = emptyList(),
    /** Files no emulator for this system opens and that aren't another system's: archives, odd formats. */
    val unplayable: List<String> = emptyList(),
    /** Every file except hidden ones and companions, relative to the folder. Health judges these per chosen emulator. */
    val files: List<String> = emptyList(),
)

data class StrayFile(val fileName: String, val belongsTo: String)

/** A tree URI an app holds, resolved to the system it covers (or "" for the whole ROMs root). */
data class FolderGrant(
    val packageId: String,
    val systemId: String,
    val granted: Boolean = true,
)

/** A known BIOS file present where a given emulator looks for it. */
data class BiosFile(
    val systemId: String,
    val emulatorId: String,
    val fileName: String,
    val filePath: String,
)

/** Media ES-DE scraped for a game that's no longer there. */
data class OrphanedMedia(
    val systemId: String,
    val mediaType: String,
    val filePath: String,
    val matchedRomBasename: String? = null,
)

data class AppPermissionState(
    val packageId: String,
    val declaresManageExternalStorage: Boolean = false,
    val grantedManageExternalStorage: Boolean = false,
    /** Runtime permissions the bundle lists for the app that aren't granted. */
    val missingPermissions: List<String> = emptyList(),
)

data class EsdeConfig(
    /** ES-DE has been opened at least once: its settings file exists. */
    val installed: Boolean = false,
    val configPath: String = "",
    val romDirectory: String = "/storage/emulated/0/ROMs",
    val mediaDirectory: String = "",
    val customSystemsFolder: String = "",
    val themesFolder: String = "",
    val customThemesEnabled: Boolean = false,
)
