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

    /** Scan the device and re-run the rules. Null result: no plan yet to check against. */
    suspend fun check(): HealthReport? = lock.withLock {
        val bundle = app.knowledge.getBundle() ?: return null
        val plan = app.runs.lastPlan() ?: return null
        _checking.value = true
        try {
            val inventory = DeviceInventory(app, bundle, app.access).scan()
            val issues = HealthChecker(bundle).check(plan, inventory, accessLost = app.access.mode.value == AccessMode.Disconnected)
            return HealthReport.of(plan, issues, bundle).also { _report.value = it }
        } finally { _checking.value = false }
    }
}
