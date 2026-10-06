package com.ramrom.emumanager.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

enum class WizardStepType {
    INSTALL_APP,
    FIRST_LAUNCH,
    GRANT_RUNTIME_PERMISSION,
    GRANT_ALL_FILES_ACCESS,
    CREATE_FOLDER,
    WRITE_FRONTEND_CONFIG,
    WRITE_EMULATOR_CONFIG,
    STAGE_BIOS,
    GUIDED_FOLDER_PICK,
    GUIDED_IN_APP_SETUP,
    GUIDED_OBTAIN_APP,
    IMPORT,
    HEALTH_CHECK
}

enum class WizardStepStatus {
    PENDING,
    RUNNING,
    VERIFYING,
    DONE,
    DONE_UNVERIFIED,
    FAILED,
    NEEDS_USER,
    SKIPPED,
    ALREADY_DONE
}

enum class WizardStepExecutor {
    AUTO,
    GUIDED
}

/**
 * WizardStep: a single step in a wizard run.
 * Persisted on every transition (P05).
 */
@Entity(
    tableName = "wizard_steps",
    primaryKeys = ["runId", "stepId"],
    foreignKeys = [
        ForeignKey(
            entity = WizardRun::class,
            parentColumns = ["id"],
            childColumns = ["runId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["runId", "status"])
    ]
)
data class WizardStep(
    val runId: String,
    val stepId: String,
    val type: WizardStepType,
    val params: String = "", // JSON
    val dependsOn: List<String> = emptyList(), // JSON list
    val executor: WizardStepExecutor,
    val status: WizardStepStatus,
    val attempts: Int = 0,
    val lastError: String? = null,
    val verifiedAt: Long? = null,
    val unverified: Boolean = false
)
