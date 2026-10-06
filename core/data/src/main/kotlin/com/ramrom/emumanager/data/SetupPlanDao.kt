package com.ramrom.emumanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for SetupPlan.
 */
@Dao
interface SetupPlanDao {
    @Query("SELECT * FROM setup_plans WHERE id = :id")
    fun getPlanById(id: String): Flow<SetupPlan?>

    @Query("SELECT * FROM setup_plans ORDER BY updatedAt DESC")
    fun getAllPlans(): Flow<List<SetupPlan>>

    @Insert
    suspend fun insert(plan: SetupPlan)

    @Update
    suspend fun update(plan: SetupPlan)

    @Delete
    suspend fun delete(plan: SetupPlan)
}
