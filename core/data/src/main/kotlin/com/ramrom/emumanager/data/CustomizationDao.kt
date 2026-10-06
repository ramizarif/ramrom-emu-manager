package com.ramrom.emumanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Customization.
 */
@Dao
interface CustomizationDao {
    @Query("SELECT * FROM customizations WHERE id = :id")
    fun getCustomizationById(id: String): Flow<Customization?>

    @Query("SELECT * FROM customizations WHERE kind = :kind AND target = :target")
    fun getByKindAndTarget(kind: CustomizationKind, target: String): Flow<List<Customization>>

    @Query("SELECT * FROM customizations WHERE kind = :kind ORDER BY appliedAt DESC")
    fun getByKind(kind: CustomizationKind): Flow<List<Customization>>

    @Query("SELECT * FROM customizations ORDER BY appliedAt DESC")
    fun getAllCustomizations(): Flow<List<Customization>>

    @Insert
    suspend fun insert(customization: Customization)

    @Update
    suspend fun update(customization: Customization)

    @Delete
    suspend fun delete(customization: Customization)
}
