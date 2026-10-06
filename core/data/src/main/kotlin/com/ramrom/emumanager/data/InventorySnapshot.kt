package com.ramrom.emumanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * InventorySnapshot: cached state of installed apps, ROM folders, BIOS, etc.
 * Incremental updates supported.
 */
@Entity(tableName = "inventory_snapshots")
data class InventorySnapshot(
    @PrimaryKey
    val takenAt: Long,
    val apps: String = "", // JSON list of {pkg, versionName, versionCode}
    val esde: String = "", // JSON {theme, colorScheme, romDirectory, mediaDirectory}
    val romFolders: String = "", // JSON list of {system, count, totalBytes}
    val bios: String = "", // JSON list of {system, fileName, md5, present}
    val grants: String = "", // JSON list of {pkg, treeUri, system}
    val media: String = "" // JSON {system -> {type -> count}}
)
