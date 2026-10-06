package com.ramrom.emumanager.engine

import com.ramrom.emumanager.access.Capability

/**
 * Step types per P04 process specification.
 */
sealed class StepType {
    object InstallApp : StepType()
    object FirstLaunch : StepType()
    object GrantRuntimePermission : StepType()
    object GrantAllFilesAccess : StepType()
    object CreateFolder : StepType()
    object WriteFrontendConfig : StepType()
    object WriteEmulatorConfig : StepType()
    object StageBios : StepType()
    object GuidedFolderPick : StepType()
    object GuidedInAppSetup : StepType()
    object GuidedObtainApp : StepType()
    object Import : StepType()
    object HealthCheck : StepType()
}

/**
 * Executor type: whether the step runs automatically or needs user guidance.
 */
enum class Executor {
    Auto, Guided
}

/**
 * Step status: what state the step is in.
 */
enum class StepStatus {
    Pending,
    AlreadyDone,
    Done
}

/**
 * Verification spec for a step.
 */
data class VerifySpec(
    val kind: String, // e.g., "fileExists", "folderGrant", "appInstalled", "permission"
    val target: String = ""
)

/**
 * An executable step in the compiled plan.
 */
data class Step(
    val id: String,
    val type: StepType,
    val subject: String, // emulator ID or system ID or folder name
    val dependsOn: List<String> = emptyList(),
    val executor: Executor,
    val capabilityNeeded: Capability? = null,
    val verify: VerifySpec = VerifySpec("none"),
    val estimatedSeconds: Int = 0,
    val handsOnSeconds: Int = 0,
    val status: StepStatus = StepStatus.Pending,
    val userFacingText: String = "" // key or localized text
)
