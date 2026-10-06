package com.ramrom.emumanager.engine.run

import com.ramrom.emumanager.engine.CompiledPlan

/**
 * Snapshot of a single step's execution state.
 */
data class StepSnapshot(
    val stepId: String,
    val status: StepRunStatus,
    val attempts: Int = 0,
    val lastError: String? = null,
    val progress: Float = 0f, // 0.0 to 1.0 for running steps
)

/**
 * Snapshot of a wizard run in progress.
 * Used to resume after process death or user pause.
 */
data class RunSnapshot(
    val runId: String,
    val plan: CompiledPlan,
    val startedAtMs: Long,
    val steps: Map<String, StepSnapshot>,
    val isPaused: Boolean = false,
)

/**
 * Persists and loads wizard run snapshots.
 * Allows a run to be safely resumed after process death.
 */
interface RunStore {
    /**
     * Save a snapshot of the current run state.
     * Called after every state transition or progress update.
     */
    suspend fun saveSnapshot(snapshot: RunSnapshot)

    /**
     * Load the most recent snapshot for a run, or null if none exists.
     */
    suspend fun loadSnapshot(runId: String): RunSnapshot?

    /**
     * Delete the snapshot for a run (called when run completes).
     */
    suspend fun deleteSnapshot(runId: String)
}
