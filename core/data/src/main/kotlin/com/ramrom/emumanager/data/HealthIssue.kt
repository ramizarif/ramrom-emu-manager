package com.ramrom.emumanager.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class HealthIssueSeverity {
    TIP,
    INFO,
    WARNING,
    BLOCKING
}

/**
 * HealthIssue: a health check finding.
 */
@Entity(
    tableName = "health_issues",
    indices = [
        Index(value = ["ruleId", "subject"], unique = true)
    ]
)
data class HealthIssue(
    @PrimaryKey
    val id: String,
    val ruleId: String,
    val subject: String,
    val severity: HealthIssueSeverity,
    val firstSeen: Long,
    val lastSeen: Long,
    val ignored: Boolean = false,
    val fixedAt: Long? = null
)
