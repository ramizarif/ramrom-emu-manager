package com.ramrom.emumanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for WizardStep.
 * Supports atomic status + attempts updates per P05.
 */
@Dao
interface WizardStepDao {
    @Query("SELECT * FROM wizard_steps WHERE runId = :runId AND stepId = :stepId")
    fun getStep(runId: String, stepId: String): Flow<WizardStep?>

    @Query("SELECT * FROM wizard_steps WHERE runId = :runId ORDER BY stepId")
    fun getStepsByRunId(runId: String): Flow<List<WizardStep>>

    @Query("SELECT * FROM wizard_steps WHERE runId = :runId AND status = :status")
    fun getStepsByRunIdAndStatus(runId: String, status: WizardStepStatus): Flow<List<WizardStep>>

    @Insert
    suspend fun insert(step: WizardStep)

    @Update
    suspend fun update(step: WizardStep)

    @Delete
    suspend fun delete(step: WizardStep)

    /**
     * Atomic update: status + attempts + verifiedAt, all in one transaction.
     */
    @Query(
        """
        UPDATE wizard_steps
        SET status = :status, attempts = :attempts, verifiedAt = :verifiedAt, lastError = :lastError
        WHERE runId = :runId AND stepId = :stepId
        """
    )
    suspend fun updateStepStatusAtomic(
        runId: String,
        stepId: String,
        status: WizardStepStatus,
        attempts: Int,
        verifiedAt: Long?,
        lastError: String?
    )
}
