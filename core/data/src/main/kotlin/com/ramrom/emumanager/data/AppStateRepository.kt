package com.ramrom.emumanager.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * AppStateRepository: access layer for app state with defaults.
 * Ensures a default row exists; app uses state to decide Home vs Welcome flow.
 */
class AppStateRepository(private val dao: AppStateDao) {
    /**
     * Flow of app state, emitting a default row if none exists.
     */
    val state: Flow<AppState> = dao.getAppState().map { existing ->
        existing ?: AppState()
    }

    /**
     * Mark first run complete (user finished setup).
     * Upserts: creates default row if missing, updates if exists.
     */
    suspend fun markFirstRunDone() {
        edit { it.copy(firstRunDone = true) }
    }

    /**
     * Set the detected device profile ID.
     * Upserts: creates default row if missing, updates if exists.
     */
    suspend fun setDeviceProfile(id: String) {
        edit { it.copy(deviceProfileId = id) }
    }

    /**
     * Acknowledge a notice version (blocks future warnings).
     * Upserts: creates default row if missing, updates if exists.
     */
    suspend fun ackNotice(version: Int) {
        edit { it.copy(noticeVersionAck = version) }
    }

    /** Change one field and keep the rest; the single row is replaced, never duplicated. */
    private suspend fun edit(change: (AppState) -> AppState) = dao.insert(change(state.first()))
}
