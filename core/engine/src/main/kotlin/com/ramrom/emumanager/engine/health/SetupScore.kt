package com.ramrom.emumanager.engine.health

import com.ramrom.emumanager.engine.Plan
import com.ramrom.emumanager.knowledge.KnowledgeBundle

/**
 * How set up the device is, as a percentage people can watch go up. Counted only against what the
 * plan asks for, so ten systems all working is 100%, not "ten of everything". Built from health's
 * findings, so the number and the report never disagree.
 *
 * Counts: per system, the emulator installed, its access to the games, the ROM folder, the BIOS when
 * the system needs one, and ES-DE opening the right emulator; per device, the frontend installed and
 * opened once. Doesn't count: games in folders (that's the library), notes and tips, notification
 * permissions, or limited mode. A BIOS we can't look at isn't counted either way.
 */
data class SetupScore(val done: Int, val total: Int, val missing: List<Missing>) {
    /** Rounded down, so 100 means every item: 44 of 45 reads 97, never a flattering 100. */
    val percent: Int get() = if (total == 0) 100 else done * 100 / total

    /** One thing not in place, for "what's left" lists. */
    data class Missing(val systemId: String?, val what: String)

    companion object {
        private val SETUP = listOf("frontend_missing", "frontend_first_run", "app_missing_", "all_files_", "folder_pick_",
            "folder_missing_", "bios_missing_", "esde_choice_")

        /** Counts against setup (and a system's Ready). Tidying (strays, leftovers, empty folders) doesn't. */
        fun affectsSetup(issue: HealthIssue) = issue.severity != IssueSeverity.INFO && SETUP.any { issue.id.startsWith(it) }

        fun of(plan: Plan, issues: List<HealthIssue>, bundle: KnowledgeBundle): SetupScore {
            val ids = issues.associateBy { it.id }
            fun open(id: String) = ids[id]?.let { it.severity != IssueSeverity.INFO } == true
            var done = 0; var total = 0
            val missing = mutableListOf<Missing>()
            fun item(ok: Boolean, systemId: String?, what: String) { total++; if (ok) done++ else missing += Missing(systemId, what) }

            val frontend = plan.frontendId?.let { id -> bundle.frontends.firstOrNull { it.id == id } }
            val fe = "ES-DE" // the only frontend so far; FrontendProfile has no display name yet
            val frontendReady = !open("frontend_missing") && !open("frontend_first_run")
            if (frontend != null) {
                item(!open("frontend_missing"), null, "$fe installed")
                if (!open("frontend_missing")) item(!open("frontend_first_run"), null, "$fe opened once")
            }
            for (s in plan.systems) {
                val emu = bundle.emulators.firstOrNull { it.id == s.emulatorId }?.name ?: s.emulatorId
                val sys = s.systemId
                // Health stays quiet about what hangs off a missing app, so a missing app fails those too.
                val app = !open("app_missing_${s.emulatorId}")
                item(app, sys, "$emu installed")
                // Only what stops games opening: all-files access, or a folder pick when no frontend hands games over.
                item(app && !open("all_files_${s.emulatorId}") && !open("folder_pick_${s.emulatorId}"), sys, "$emu can reach your games")
                item(!open("folder_missing_$sys"), sys, "ROM folder")
                val needsBios = bundle.bios.any { it.system == sys && it.required && it.targets.any { t -> t.emulatorId == s.emulatorId } }
                if (needsBios && (!app || "bios_unchecked_$sys" !in ids)) item(app && !open("bios_missing_$sys"), sys, "BIOS")
                if (frontend != null) item(app && frontendReady && !open("esde_choice_$sys"), sys, "$fe opens it in $emu")
            }
            return SetupScore(done, total, missing)
        }
    }
}
