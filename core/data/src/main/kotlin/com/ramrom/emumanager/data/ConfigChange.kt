package com.ramrom.emumanager.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ConfigChangeTarget {
    APP,
    FILE
}

enum class ConfigChangeReason {
    PROFILE,
    FIX,
    CUSTOMIZE,
    WIZARD
}

/**
 * ConfigChange: record of a change to another app's config files.
 * Serves as undo source (P09).
 */
@Entity(
    tableName = "config_changes",
    indices = [
        Index(value = ["gameId"]),
        Index(value = ["at"])
    ]
)
data class ConfigChange(
    @PrimaryKey
    val id: String,
    val at: Long,
    val target: ConfigChangeTarget,
    val reason: ConfigChangeReason,
    val gameId: String? = null,
    val keysBefore: String = "", // JSON
    val keysAfter: String = "", // JSON
    val snapshotPath: String = "",
    val revertedAt: Long? = null,
    /** The file written, `{appData}` resolved. */
    @ColumnInfo(defaultValue = "") val filePath: String = "",
    /** The app that owns the file, closed before writing or undoing. */
    @ColumnInfo(defaultValue = "") val pkg: String = "",
    /** What the user saw: the profile or fix name. */
    @ColumnInfo(defaultValue = "") val title: String = "",
    /** False when the file was created by this change; undo then deletes it. */
    @ColumnInfo(defaultValue = "1") val existedBefore: Boolean = true,
    /** Copy of what was written, so undo can tell whether the app changed the file since. */
    @ColumnInfo(defaultValue = "") val writtenPath: String = "",
)
