package com.ramrom.emumanager.engine.run

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [RunStore] implementation for testing.
 * Snapshots are kept in memory and cleared on delete.
 */
class InMemoryRunStore : RunStore {
    private val snapshots = ConcurrentHashMap<String, RunSnapshot>()

    override suspend fun saveSnapshot(snapshot: RunSnapshot) {
        snapshots[snapshot.runId] = snapshot
    }

    override suspend fun loadSnapshot(runId: String): RunSnapshot? {
        return snapshots[runId]
    }

    override suspend fun deleteSnapshot(runId: String) {
        snapshots.remove(runId)
    }

    /** Test helper: get all stored snapshots. */
    fun getAllSnapshots(): Map<String, RunSnapshot> = snapshots.toMap()

    /** Test helper: clear all snapshots. */
    fun clear() = snapshots.clear()
}
