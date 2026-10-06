package com.ramrom.emumanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AppState: single-row table for app-level state.
 */
@Entity(tableName = "app_state")
data class AppState(
    @PrimaryKey
    val id: Int = 0,
    val noticeVersionAck: Int = 0,
    val deviceProfileId: String? = null,
    val mode: AppMode = AppMode.LIMITED,
    val lastCapabilities: String = "", // JSON-encoded set of capabilities
    val firstRunDone: Boolean = false
)
