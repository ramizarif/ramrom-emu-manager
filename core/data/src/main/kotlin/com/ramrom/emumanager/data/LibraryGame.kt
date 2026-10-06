package com.ramrom.emumanager.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SettingsState {
    NONE,
    APPLIED,
    USER_CHANGED,
    OUTDATED
}

/**
 * LibraryGame: a game in the ROM library, built from inventory and imports.
 */
@Entity(
    tableName = "library_games",
    indices = [
        Index(value = ["system"]),
        Index(value = ["displayName"])
    ]
)
data class LibraryGame(
    @PrimaryKey
    val id: String,
    val system: String,
    val filePath: String,
    val displayName: String,
    val ids: String = "", // JSON {titleId?, serial?, hashes?}
    val sizeBytes: Long = 0,
    val mtime: Long = 0,
    val mediaPresent: List<String> = emptyList(), // JSON list of media types present
    val appliedProfileId: String? = null,
    val settingsState: SettingsState = SettingsState.NONE
)
