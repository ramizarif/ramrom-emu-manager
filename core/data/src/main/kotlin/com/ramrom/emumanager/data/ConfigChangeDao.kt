package com.ramrom.emumanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for ConfigChange (undo history for P09).
 */
@Dao
interface ConfigChangeDao {
    @Query("SELECT * FROM config_changes WHERE id = :id")
    fun getChangeById(id: String): Flow<ConfigChange?>

    @Query("SELECT * FROM config_changes WHERE gameId = :gameId ORDER BY at DESC")
    fun getChangesByGameId(gameId: String): Flow<List<ConfigChange>>

    @Query("SELECT * FROM config_changes ORDER BY at DESC")
    fun getAllChanges(): Flow<List<ConfigChange>>

    @Query("SELECT * FROM config_changes WHERE gameId = :gameId AND at >= :afterTime ORDER BY at DESC")
    fun getChangesSince(gameId: String, afterTime: Long): Flow<List<ConfigChange>>

    @Insert
    suspend fun insert(change: ConfigChange)

    @Update
    suspend fun update(change: ConfigChange)

    @Delete
    suspend fun delete(change: ConfigChange)
}
