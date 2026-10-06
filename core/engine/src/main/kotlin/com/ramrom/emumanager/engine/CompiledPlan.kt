package com.ramrom.emumanager.engine

/**
 * A compiled plan: a DAG of executable steps ready to run.
 */
data class CompiledPlan(
    val steps: List<Step>,
    /** What S09 found, carried through so S10 can report it. */
    val bios: BiosSummary? = null,
) {
    /** The counts S10 shows. Already-done steps don't count; they're what Adopt found. */
    fun summary(): PlanSummary {
        val pending = steps.filter { it.status == StepStatus.Pending }
        val installs = pending.filter { it.type is StepType.InstallApp }
        val obtains = pending.filter { it.type is StepType.GuidedObtainApp }
        return PlanSummary(
            appsTotal = installs.size + obtains.size,
            appsAuto = installs.count { it.executor == Executor.Auto },
            appsYouGet = obtains.size,
            stepsTotal = pending.size,
            stepsNeedYou = pending.count { it.executor == Executor.Guided },
            handsOnMinutes = (pending.sumOf { it.handsOnSeconds } + 59) / 60,
            machineMinutes = (pending.sumOf { it.estimatedSeconds } + 59) / 60,
            biosFound = bios?.found.orEmpty(),
            biosNeeded = bios?.let { it.found + it.missing + it.wrongVersion }.orEmpty(),
        )
    }
}

/**
 * Summary of what the plan requires: displayed on S10.
 */
data class PlanSummary(
    val appsTotal: Int,
    val appsAuto: Int,
    val appsYouGet: Int,
    val stepsTotal: Int,
    val stepsNeedYou: Int,
    val handsOnMinutes: Int,
    val machineMinutes: Int,
    val biosFound: Set<String>,
    val biosNeeded: Set<String>,
    val savedMinutesIfConnected: Int = 0 // Minutes saved if full access enabled
)
