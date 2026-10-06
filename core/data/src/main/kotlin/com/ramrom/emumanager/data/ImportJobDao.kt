package com.ramrom.emumanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for ImportJob.
 */
@Dao
interface ImportJobDao {
    @Query("SELECT * FROM import_jobs WHERE id = :id")
    fun getJobById(id: String): Flow<ImportJob?>

    @Query("SELECT * FROM import_jobs ORDER BY id DESC")
    fun getAllJobs(): Flow<List<ImportJob>>

    @Query("SELECT * FROM import_jobs WHERE status = :status")
    fun getJobsByStatus(status: ImportJobStatus): Flow<List<ImportJob>>

    @Insert
    suspend fun insert(job: ImportJob)

    @Update
    suspend fun update(job: ImportJob)

    @Delete
    suspend fun delete(job: ImportJob)
}

/**
 * DAO for ImportItem (resumable).
 */
@Dao
interface ImportItemDao {
    @Query("SELECT * FROM import_items WHERE id = :id")
    fun getItemById(id: String): Flow<ImportItem?>

    @Query("SELECT * FROM import_items WHERE jobId = :jobId ORDER BY id")
    fun getItemsByJobId(jobId: String): Flow<List<ImportItem>>

    @Query("SELECT * FROM import_items WHERE jobId = :jobId AND status = :status")
    fun getItemsByJobIdAndStatus(jobId: String, status: ImportItemStatus): Flow<List<ImportItem>>

    @Insert
    suspend fun insert(item: ImportItem)

    @Update
    suspend fun update(item: ImportItem)

    @Delete
    suspend fun delete(item: ImportItem)
}
