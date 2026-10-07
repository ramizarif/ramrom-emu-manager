package com.ramrom.emumanager.engine.run

import com.ramrom.emumanager.engine.Step

/** What running one step produced. */
sealed class StepResult {
    data object Ok : StepResult()
    /** The step can't finish without the user (e.g. Android's install prompt was dismissed). */
    data class NeedsUser(val reason: String) : StepResult()
    data class Failed(val reason: String, val retryable: Boolean = true) : StepResult()
}

/**
 * Carries out steps on the device. The runner owns ordering, retries and state; the executor only
 * acts and checks. Both must be idempotent: after a crash a step may be checked or run again.
 */
interface StepExecutor {
    /** Do the step's work. Report progress (0..1) for long steps like downloads. */
    suspend fun execute(step: Step, progress: (Float) -> Unit): StepResult

    /**
     * Is the step's end state true on the device right now? Used after a guided step ("I did it"),
     * after a crash mid-step, and after execute() to confirm. Never trusts a step's own report.
     */
    suspend fun verify(step: Step): Boolean

    /**
     * Can [verify] actually see this step's end state? When it can't (setup done inside another
     * app), only the user's "I did it" completes the step; coming back from the app doesn't.
     */
    suspend fun canVerify(step: Step): Boolean = true
}
