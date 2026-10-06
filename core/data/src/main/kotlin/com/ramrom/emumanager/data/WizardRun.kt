package com.ramrom.emumanager.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class WizardRunStatus {
    IN_PROGRESS,
    FINISHED,
    FAILED
}

/**
 * WizardRun: an execution of a setup plan.
 */
@Entity(
    tableName = "wizard_runs",
    foreignKeys = [
        ForeignKey(
            entity = SetupPlan::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["planId"])
    ]
)
data class WizardRun(
    @PrimaryKey
    val id: String,
    val planId: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: WizardRunStatus = WizardRunStatus.IN_PROGRESS
)
