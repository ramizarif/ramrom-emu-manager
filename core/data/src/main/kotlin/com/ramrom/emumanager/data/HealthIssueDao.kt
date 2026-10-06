package com.ramrom.emumanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for HealthIssue.
 */
@Dao
interface HealthIssueDao {
    @Query("SELECT * FROM health_issues WHERE id = :id")
    fun getIssueById(id: String): Flow<HealthIssue?>

    @Query("SELECT * FROM health_issues WHERE ruleId = :ruleId AND subject = :subject LIMIT 1")
    fun getByRuleAndSubject(ruleId: String, subject: String): Flow<HealthIssue?>

    @Query("SELECT * FROM health_issues WHERE severity = :severity ORDER BY lastSeen DESC")
    fun getIssuesBySeverity(severity: HealthIssueSeverity): Flow<List<HealthIssue>>

    @Query("SELECT * FROM health_issues WHERE ignored = 0 ORDER BY lastSeen DESC")
    fun getActiveIssues(): Flow<List<HealthIssue>>

    @Query("SELECT * FROM health_issues ORDER BY lastSeen DESC")
    fun getAllIssues(): Flow<List<HealthIssue>>

    @Insert
    suspend fun insert(issue: HealthIssue)

    @Update
    suspend fun update(issue: HealthIssue)

    @Delete
    suspend fun delete(issue: HealthIssue)
}
