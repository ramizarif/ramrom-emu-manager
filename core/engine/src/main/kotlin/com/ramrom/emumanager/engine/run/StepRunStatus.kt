package com.ramrom.emumanager.engine.run

/** Where one step is in a run. S12 groups these into Needs you / Running and up next / Done. */
sealed class StepRunStatus {
    data object Pending : StepRunStatus()
    /** [started] false: scheduled but waiting for a free slot (or for another step on the same app). */
    data class Running(val progress: Float = 0f, val started: Boolean = true) : StepRunStatus()
    /** Checking the device after a crash or an "I did it"; resolves to Done or back to Pending/NeedsYou. */
    data object Verifying : StepRunStatus()
    data object Done : StepRunStatus()
    /** Already true before the run started (Adopt found it). */
    data object AlreadyDone : StepRunStatus()
    data class NeedsYou(val reason: String) : StepRunStatus()
    data class Failed(val reason: String) : StepRunStatus()
    /** The user chose "Skip for now". */
    data object Skipped : StepRunStatus()
    /** Waits on a skipped step; runs again once that step is retried. Not a failure. */
    data class Blocked(val by: String) : StepRunStatus()

    val isSettled: Boolean get() = this is Done || this is AlreadyDone || this is Skipped || this is Blocked || this is Failed
    val isSatisfied: Boolean get() = this is Done || this is AlreadyDone
}

data class StepRunProgress(
    val stepId: String,
    val status: StepRunStatus,
    val attempts: Int = 0,
    val lastError: String? = null,
)

/** The whole run as the UI sees it. */
data class RunState(
    val runId: String,
    /** False once nothing can move without the user: every step is done, skipped, blocked or failed. */
    val isRunning: Boolean = true,
    val isPaused: Boolean = false,
    val stepProgress: Map<String, StepRunProgress> = emptyMap(),
    /** Machine seconds left on steps that haven't finished. */
    val estimatedSecondsLeft: Int = 0,
) {
    private fun count(p: (StepRunStatus) -> Boolean) = stepProgress.values.count { p(it.status) }
    val pendingCount get() = count { it is StepRunStatus.Pending }
    val runningCount get() = count { it is StepRunStatus.Running || it is StepRunStatus.Verifying }
    val needsYouCount get() = count { it is StepRunStatus.NeedsYou }
    val doneCount get() = count { it.isSatisfied }
    val failedCount get() = count { it is StepRunStatus.Failed }
    val skippedCount get() = count { it is StepRunStatus.Skipped || it is StepRunStatus.Blocked }
    val overallProgress: Float get() = if (stepProgress.isEmpty()) 1f else doneCount.toFloat() / stepProgress.size
    /** Everything the plan asked for is in place. */
    val isComplete: Boolean get() = stepProgress.values.all { it.status.isSatisfied }
    /** Nothing left that will move on its own or is waiting on the user: done, skipped or failed. */
    val isSettled: Boolean get() = stepProgress.values.all { it.status.isSettled }
}
