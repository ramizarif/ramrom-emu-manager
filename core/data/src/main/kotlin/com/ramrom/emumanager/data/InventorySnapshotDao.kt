package com.ramrom.emumanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for InventorySnapshot (cached).
 */
@Dao
interface InventorySnapshotDao {
    @Query("SELECT * FROM inventory_snapshots ORDER BY takenAt DESC LIMIT 1")
    fun getLatestSnapshot(): Flow<InventorySnapshot?>

    @Query("SELECT * FROM inventory_snapshots ORDER BY takenAt DESC")
    fun getAllSnapshots(): Flow<List<InventorySnapshot>>

    @Insert
    suspend fun insert(snapshot: InventorySnapshot)

    @Update
    suspend fun update(snapshot: InventorySnapshot)

    @Delete
    suspend fun delete(snapshot: InventorySnapshot)

    @Query("DELETE FROM inventory_snapshots WHERE takenAt < :beforeTime")
    suspend fun deleteOlderThan(beforeTime: Long)
}
