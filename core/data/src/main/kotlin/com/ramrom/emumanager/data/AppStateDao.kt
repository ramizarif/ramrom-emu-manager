package com.ramrom.emumanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for AppState (single-row table).
 */
@Dao
interface AppStateDao {
    @Query("SELECT * FROM app_state LIMIT 1")
    fun getAppState(): Flow<AppState?>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(state: AppState)

    @Update
    suspend fun update(state: AppState)

    @Query("DELETE FROM app_state")
    suspend fun clear()
}
