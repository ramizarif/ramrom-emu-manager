package com.ramrom.emumanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for KnowledgeBundleState.
 */
@Dao
interface KnowledgeBundleStateDao {
    @Query("SELECT * FROM knowledge_bundle_state LIMIT 1")
    fun getState(): Flow<KnowledgeBundleState?>

    @Insert
    suspend fun insert(state: KnowledgeBundleState)

    @Update
    suspend fun update(state: KnowledgeBundleState)

    @Delete
    suspend fun delete(state: KnowledgeBundleState)

    @Query("DELETE FROM knowledge_bundle_state")
    suspend fun clear()
}
