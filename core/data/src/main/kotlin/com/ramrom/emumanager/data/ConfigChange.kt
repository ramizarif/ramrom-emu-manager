package com.ramrom.emumanager.data

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
    val revertedAt: Long? = null
)
