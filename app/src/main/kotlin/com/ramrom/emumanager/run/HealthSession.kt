package com.ramrom.emumanager.run

import com.ramrom.emumanager.EmuApplication
import com.ramrom.emumanager.access.AccessMode
import com.ramrom.emumanager.engine.health.HealthChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The latest S14 report, shared by S14 and S15 so a fix on one shows on the other. */
class HealthSession(private val app: EmuApplication) {
    private val _report = MutableStateFlow<HealthReport?>(null)
    val report: StateFlow<HealthReport?> = _report.asStateFlow()
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()
    private val lock = Mutex()
    private val prefs = app.getSharedPreferences("health", android.content.Context.MODE_PRIVATE)

    /** The setup percentage the user last saw, so the next one counts up (or down) from it. */
    val lastSeenPercent: Int get() = prefs.getInt("seen_percent", 0)
    fun markSeen(percent: Int) { prefs.edit().putInt("seen_percent", percent).apply() }

    /** Scan the device and re-run the rules. Null result: no plan yet to check against. */
    suspend fun check(): HealthReport? = lock.withLock {
        val bundle = app.knowledge.getBundle() ?: return null
        val plan = app.runs.lastPlan() ?: return null
        _checking.value = true
        try {
            val inventory = DeviceInventory(app, bundle, app.access).scan()
            val issues = HealthChecker(bundle).check(plan, inventory, accessLost = app.access.mode.value == AccessMode.Disconnected)
            android.util.Log.d("RamROM/health", "${app.access.mode.value}: " + issues.joinToString { "${it.id}=${it.severity}" })
            return HealthReport.of(plan, issues, bundle).also { _report.value = it }
        } finally { _checking.value = false }
    }
}
