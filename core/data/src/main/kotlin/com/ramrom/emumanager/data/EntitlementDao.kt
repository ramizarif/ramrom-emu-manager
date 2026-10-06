package com.ramrom.emumanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Entitlement.
 */
@Dao
interface EntitlementDao {
    @Query("SELECT * FROM entitlements LIMIT 1")
    fun getEntitlement(): Flow<Entitlement?>

    @Query("SELECT * FROM entitlements WHERE state = :state LIMIT 1")
    fun getEntitlementByState(state: EntitlementState): Flow<Entitlement?>

    @Insert
    suspend fun insert(entitlement: Entitlement)

    @Update
    suspend fun update(entitlement: Entitlement)

    @Delete
    suspend fun delete(entitlement: Entitlement)

    @Query("DELETE FROM entitlements")
    suspend fun clear()
}
