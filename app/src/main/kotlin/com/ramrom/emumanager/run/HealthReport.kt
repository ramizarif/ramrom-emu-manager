package com.ramrom.emumanager.run

import com.ramrom.emumanager.engine.Plan
import com.ramrom.emumanager.engine.health.FixAction
import com.ramrom.emumanager.engine.health.HealthIssue
import com.ramrom.emumanager.engine.health.IssueSeverity
import com.ramrom.emumanager.knowledge.KnowledgeBundle

/** One planned system on S14: ready, or the most serious thing in its way. */
data class SystemHealth(val systemId: String, val name: String, val emulatorName: String, val top: HealthIssue?) {
    val ready get() = top == null || top.severity == IssueSeverity.INFO
}

/** S14's whole report. */
data class HealthReport(val plan: Plan, val issues: List<HealthIssue>, val systems: List<SystemHealth>) {
    val blocking get() = issues.filter { it.severity == IssueSeverity.BLOCKER }
    val warnings get() = issues.filter { it.severity == IssueSeverity.WARNING }
    val tips get() = issues.filter { it.severity == IssueSeverity.INFO }
    val readyCount get() = systems.count { it.ready }
    val allGood get() = blocking.isEmpty() && warnings.isEmpty()

    companion object {
        fun of(plan: Plan, issues: List<HealthIssue>, bundle: KnowledgeBundle): HealthReport {
            val emus = bundle.emulators.associateBy { it.id }
            val systems = plan.systems.map { s ->
                val pkgs = emus[s.emulatorId]?.packages.orEmpty()
                // App-wide issues (an emulator missing, its access) count against every system it runs.
                val mine = issues.filter { i ->
                    i.systemId == s.systemId || (i.systemId == null && when (val f = i.fix) {
                        is FixAction.InstallApp -> f.appId == s.emulatorId
                        is FixAction.GrantAllFilesAccess -> f.packageId in pkgs
                        is FixAction.GrantPermission -> f.packageId in pkgs
                        is FixAction.GrantFolder -> f.packageId in pkgs
                        else -> false
                    })
                }
                SystemHealth(s.systemId, bundle.systems.firstOrNull { it.id == s.systemId }?.name ?: s.systemId,
                    emus[s.emulatorId]?.name ?: s.emulatorId, mine.minByOrNull { it.severity.ordinal })
            }
            return HealthReport(plan, issues, systems)
        }
    }
}
