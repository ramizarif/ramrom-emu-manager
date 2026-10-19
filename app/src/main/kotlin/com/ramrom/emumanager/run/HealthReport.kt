package com.ramrom.emumanager.run

import com.ramrom.emumanager.engine.Plan
import com.ramrom.emumanager.engine.health.FixAction
import com.ramrom.emumanager.engine.health.HealthIssue
import com.ramrom.emumanager.engine.health.IssueSeverity
import com.ramrom.emumanager.engine.health.SetupScore
import com.ramrom.emumanager.knowledge.KnowledgeBundle

/** One planned system on S14: ready, or the most serious thing in its way. */
data class SystemHealth(val systemId: String, val name: String, val emulatorName: String, val top: HealthIssue?, val ready: Boolean) {
    /** Ready, but with something worth tidying (a stray file): shown under the name, not as a problem. */
    val note get() = top?.takeIf { ready && it.severity != IssueSeverity.INFO }
}

/** One chosen frontend on S14: installed and pointed at the games, or what's in the way. */
data class FrontendHealth(val id: String, val name: String, val top: HealthIssue?, val folderKnown: Boolean = true) {
    val ready get() = top == null
}

/** S14's whole report. */
data class HealthReport(
    val plan: Plan,
    val issues: List<HealthIssue>,
    val systems: List<SystemHealth>,
    val score: com.ramrom.emumanager.engine.health.SetupScore,
    val frontends: List<FrontendHealth> = emptyList(),
    /** Games in the planned systems' folders. None yet: setup's next step is bringing them in. */
    val games: Int = 0,
) {
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
                    emus[s.emulatorId]?.name ?: s.emulatorId,
                    // The setup problem first if there is one, else the most serious note.
                    mine.filter(SetupScore::affectsSetup).minByOrNull { it.severity.ordinal } ?: mine.minByOrNull { it.severity.ordinal },
                    // Same rule as the percentage, so "97% set up" and the Ready tags agree.
                    mine.none(SetupScore::affectsSetup))
            }
            val frontends = plan.frontends(bundle).map { f ->
                val ids = setOfNotNull("frontend_missing_${f.id}", "frontend_folder_${f.id}", "all_files_${f.id}", "frontend_first_run".takeIf { f.id == KnowledgeBundle.ESDE })
                FrontendHealth(f.id, f.name.ifEmpty { f.id }, issues.filter { it.id in ids && SetupScore.affectsSetup(it) }.minByOrNull { it.severity.ordinal },
                    folderKnown = f.id == KnowledgeBundle.ESDE || f.gamesFolderGrant)
            }
            return HealthReport(plan, issues, systems, com.ramrom.emumanager.engine.health.SetupScore.of(plan, issues, bundle), frontends)
        }
    }
}
