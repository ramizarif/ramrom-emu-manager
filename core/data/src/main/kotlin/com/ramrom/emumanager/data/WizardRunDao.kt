package com.ramrom.emumanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for WizardRun.
 */
@Dao
interface WizardRunDao {
    @Query("SELECT * FROM wizard_runs WHERE id = :id")
    fun getRunById(id: String): Flow<WizardRun?>

    @Query("SELECT * FROM wizard_runs WHERE planId = :planId ORDER BY startedAt DESC")
    fun getRunsByPlanId(planId: String): Flow<List<WizardRun>>

    @Query("SELECT * FROM wizard_runs WHERE status = :status ORDER BY startedAt DESC")
    fun getRunsByStatus(status: WizardRunStatus): Flow<List<WizardRun>>

    @Insert
    suspend fun insert(run: WizardRun)

    @Update
    suspend fun update(run: WizardRun)

    @Delete
    suspend fun delete(run: WizardRun)
}
