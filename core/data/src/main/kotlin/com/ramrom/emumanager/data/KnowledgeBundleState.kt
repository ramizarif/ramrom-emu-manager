package com.ramrom.emumanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * KnowledgeBundleState: tracks knowledge data versioning for rollback.
 */
@Entity(tableName = "knowledge_bundle_state")
data class KnowledgeBundleState(
    @PrimaryKey
    val id: Int = 0,
    val dataVersion: String,
    val etag: String = "",
    val fetchedAt: Long,
    val previousVersion: String? = null
)
