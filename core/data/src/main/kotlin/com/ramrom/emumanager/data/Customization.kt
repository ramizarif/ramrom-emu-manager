package com.ramrom.emumanager.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CustomizationKind {
    GAME_MEDIA,
    THEME_VARIABLE,
    BACKGROUND
}

/**
 * Customization: user customizations (theme variables, game media, backgrounds).
 * Links to ConfigChange for undo.
 */
@Entity(
    tableName = "customizations",
    foreignKeys = [
        ForeignKey(
            entity = ConfigChange::class,
            parentColumns = ["id"],
            childColumns = ["changeId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["kind", "target"]),
        Index(value = ["changeId"])
    ]
)
data class Customization(
    @PrimaryKey
    val id: String,
    val kind: CustomizationKind,
    val target: String, // e.g., theme id, game id, or UI element
    val value: String = "", // JSON value
    val filePath: String = "", // path if file-backed
    val appliedAt: Long,
    val changeId: String? = null
)
