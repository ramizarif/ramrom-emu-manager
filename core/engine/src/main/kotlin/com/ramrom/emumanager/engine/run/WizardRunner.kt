package com.ramrom.emumanager.engine.run

import com.ramrom.emumanager.engine.CompiledPlan
import com.ramrom.emumanager.engine.Executor
import com.ramrom.emumanager.engine.Step
import com.ramrom.emumanager.engine.StepType
import com.ramrom.emumanager.engine.StepStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Runs a compiled plan (P05). Automatic steps start as soon as their dependencies are satisfied, up
 * to [parallelism] at once, and never two at a time for the same app (an emulator's install and
 * config write must not overlap). Guided steps wait for the user. Every change is saved, so a run
 * killed mid-way resumes from the device's real state: steps caught mid-flight are verified, not
 * blindly redone.
 */
class WizardRunner(
    private val plan: CompiledPlan,
    private val executor: StepExecutor,
    private val store: RunStore,
    val runId: String,
    private val parallelism: Int = 2,
    private val maxAttempts: Int = 3,
    private val retryDelayMs: Long = 2_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val steps = plan.steps.associateBy { it.id }
    private val dependents: Map<String, List<String>> =
        plan.steps.flatMap { s -> s.dependsOn.map { it to s.id } }.groupBy({ it.first }, { it.second })

    private val _state = MutableStateFlow(RunState(runId))
    val state: StateFlow<RunState> = _state.asStateFlow()

    private val lock = Mutex()
    private val slots = Semaphore(parallelism)
    private val appLocks = mutableMapOf<String, Mutex>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var startedAt = 0L
    private lateinit var scope: CoroutineScope

    /** Load or create the run and start scheduling in [scope]. Cancel the scope to stop. */
    suspend fun start(scope: CoroutineScope) {
        this.scope = scope
        val snapshot = store.loadSnapshot(runId)
        val restored = mutableListOf<String>()
        lock.withLock {
            startedAt = snapshot?.startedAtMs ?: clock()
            val progress = plan.steps.associate { s ->
                val saved = snapshot?.steps?.get(s.id)
                val status = when {
                    saved == null -> initial(s)
                    // Recompiled with more access (Shizuku came back): what it now sees done is done,
                    // and what it can now do itself stops waiting on the user.
                    !saved.status.isSettled && s.status == StepStatus.AlreadyDone -> StepRunStatus.AlreadyDone
                    saved.status is StepRunStatus.NeedsYou && s.executor == Executor.Auto -> StepRunStatus.Pending
                    // Caught mid-flight by a crash: ask the device what actually happened.
                    saved.status is StepRunStatus.Running || saved.status is StepRunStatus.Verifying -> StepRunStatus.Verifying.also { restored += s.id }
                    else -> saved.status
                }
                s.id to StepRunProgress(s.id, status, saved?.attempts ?: 0, saved?.lastError)
            }
            _state.value = RunState(runId, isPaused = snapshot?.isPaused ?: false, stepProgress = progress)
            persist()
        }
        restored.forEach { id -> scope.launch { reverify(id, fallback = StepRunStatus.Pending) } }
        scope.launch { schedule() }
        wake.trySend(Unit)
    }

    private fun initial(s: Step) = when (s.status) {
        StepStatus.AlreadyDone -> StepRunStatus.AlreadyDone
        StepStatus.Done -> StepRunStatus.Done
        StepStatus.Pending -> StepRunStatus.Pending
    }

    /** The user says a guided step is done. It counts only if the device agrees. */
    suspend fun markGuidedDone(stepId: String) {
        val step = steps[stepId] ?: return
        // Nothing to look at: their word is all there is.
        if (!safeCanVerify(step)) {
            lock.withLock { set(stepId, StepRunStatus.Done); updateRunning(); persist() }
            wake.trySend(Unit)
            return
        }
        reverify(stepId, fallback = StepRunStatus.NeedsYou("We couldn't see that change yet. Check it and try again."))
    }

    /**
     * Run a guided step's automatic part now, because the user asked: in limited mode we download
     * and Android asks them to confirm the install.
     */
    suspend fun runNow(stepId: String) {
        val step = steps[stepId] ?: return
        lock.withLock { set(stepId, StepRunStatus.Running()); updateRunning(); persist() }
        run(step)
    }

    /** Quietly look again (the user came back from another app): done if the device agrees, else unchanged. */
    suspend fun recheck(stepId: String) {
        val before = lock.withLock { status(stepId) } ?: return
        if (before !is StepRunStatus.NeedsYou) return
        // Coming back from an app proves nothing by itself: only a step we can check completes here.
        if (!safeCanVerify(steps[stepId] ?: return)) return
        reverify(stepId, fallback = before)
    }

    /** Try a failed, skipped or stuck step again; anything it blocked is unblocked. */
    suspend fun retryStep(stepId: String) {
        lock.withLock {
            set(stepId, StepRunStatus.Pending, attempts = 0)
            unblock(stepId)
            persist()
        }
        wake.trySend(Unit)
    }

    /** "Skip for now": the step and everything waiting on it step aside; the rest carries on. */
    suspend fun skipStepForNow(stepId: String) {
        lock.withLock {
            set(stepId, StepRunStatus.Skipped)
            block(stepId, stepId)
            persist()
        }
        wake.trySend(Unit)
    }

    /** Stop starting new steps; steps already running finish. */
    suspend fun pause() = lock.withLock { _state.value = _state.value.copy(isPaused = true); persist() }

    suspend fun resume() {
        lock.withLock { _state.value = _state.value.copy(isPaused = false); persist() }
        wake.trySend(Unit)
    }

    private suspend fun schedule() {
        for (signal in wake) {
            val (toStart, asked) = lock.withLock {
                if (_state.value.isPaused) return@withLock emptyList<Step>() to emptyList()
                val ready = plan.steps.filter(::isReady)
                ready.forEach { s ->
                    // Guided steps don't need a slot: they wait on the user, not the device.
                    set(s.id, if (s.executor == Executor.Guided) StepRunStatus.NeedsYou(s.userFacingText) else StepRunStatus.Running(started = false))
                }
                updateRunning()
                persist()
                ready.filter { it.executor == Executor.Auto } to ready.filter { it.executor == Executor.Guided }
            }
            toStart.forEach { s -> scope.launch { run(s) } }
            // A guided step that's already true on this device (Eden's keys are in) needs nobody.
            asked.forEach { s -> scope.launch { recheck(s.id) } }
        }
    }

    private suspend fun run(step: Step) {
        val appLock = lock.withLock { appLocks.getOrPut(step.subject) { Mutex() } }
        val outcome = slots.withPermit {
            appLock.withLock {
                lock.withLock { if (status(step.id) is StepRunStatus.Running) { set(step.id, StepRunStatus.Running()); persist() } }
                attempt(step)
            }
        }
        lock.withLock {
            set(step.id, outcome.first, attempts = outcome.second, error = (outcome.first as? StepRunStatus.Failed)?.reason)
            updateRunning()
            persist()
        }
        wake.trySend(Unit)
    }

    /** Runs with automatic retries for transient failures. Returns the final status and attempts. */
    private suspend fun attempt(step: Step): Pair<StepRunStatus, Int> {
        var lastError = "Failed"
        for (n in 1..maxAttempts) {
            val result = try {
                executor.execute(step) { p -> scope.launch { lock.withLock { if (status(step.id) is StepRunStatus.Running) set(step.id, StepRunStatus.Running(p)) } } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StepResult.Failed(e.message ?: "Something went wrong", retryable = true)
            }
            when (result) {
                StepResult.Ok -> return (if (safeVerify(step)) StepRunStatus.Done else StepRunStatus.Failed("It ran but the change isn't there.")) to n
                is StepResult.NeedsUser -> return StepRunStatus.NeedsYou(result.reason) to n
                is StepResult.Failed -> {
                    lastError = result.reason
                    if (!result.retryable) return StepRunStatus.Failed(result.reason) to n
                    if (n < maxAttempts) delay(retryDelayMs * n)
                }
            }
        }
        return StepRunStatus.Failed(lastError) to maxAttempts
    }

    private suspend fun reverify(stepId: String, fallback: StepRunStatus) {
        val step = steps[stepId] ?: return
        lock.withLock { set(stepId, StepRunStatus.Verifying); persist() }
        val ok = safeVerify(step)
        lock.withLock {
            set(stepId, if (ok) StepRunStatus.Done else fallback)
            updateRunning()
            persist()
        }
        wake.trySend(Unit)
    }

    private suspend fun safeCanVerify(step: Step) = try { executor.canVerify(step) } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    private suspend fun safeVerify(step: Step) = try { executor.verify(step) } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    // State helpers; callers hold [lock].

    private fun status(id: String) = _state.value.stepProgress[id]?.status

    private fun set(id: String, status: StepRunStatus, attempts: Int? = null, error: String? = null) {
        val old = _state.value.stepProgress[id] ?: StepRunProgress(id, status)
        _state.value = _state.value.copy(stepProgress = _state.value.stepProgress + (id to old.copy(
            status = status, attempts = attempts ?: old.attempts, lastError = error ?: if (status is StepRunStatus.Failed) old.lastError else null,
        )))
    }

    /**
     * Ready to start. The final check runs once everything before it has settled, done or not: a
     * skipped Vita3K step left "Check everything works" waiting forever.
     */
    private fun isReady(st: Step): Boolean {
        val now = status(st.id)
        if (st.type == StepType.HealthCheck) return (now is StepRunStatus.Pending || now is StepRunStatus.Blocked) &&
            st.dependsOn.all { status(it).let { d -> d?.isSatisfied == true || d is StepRunStatus.Skipped || d is StepRunStatus.Blocked || d is StepRunStatus.Failed } }
        return now is StepRunStatus.Pending && st.dependsOn.all { status(it)?.isSatisfied == true }
    }

    private fun block(skipped: String, from: String) {
        dependents[from].orEmpty().forEach { d ->
            if (steps[d]?.type == StepType.HealthCheck) return@forEach
            if (status(d) !is StepRunStatus.Done && status(d) !is StepRunStatus.AlreadyDone) { set(d, StepRunStatus.Blocked(skipped)); block(skipped, d) }
        }
    }

    private fun unblock(stepId: String) {
        _state.value.stepProgress.values.filter { (it.status as? StepRunStatus.Blocked)?.by == stepId }
            .forEach { set(it.stepId, StepRunStatus.Pending) }
    }

    private fun updateRunning() {
        val s = _state.value
        // Moving = something is working, or ready to. A step waiting on a failed or user step isn't.
        val moving = plan.steps.any { st ->
            when (status(st.id)) {
                is StepRunStatus.Running, StepRunStatus.Verifying -> true
                StepRunStatus.Pending -> isReady(st)
                is StepRunStatus.Blocked -> isReady(st)
                else -> false
            }
        }
        val left = plan.steps.filter { status(it.id)?.isSatisfied != true }.sumOf { it.estimatedSeconds }
        _state.value = s.copy(isRunning = moving, estimatedSecondsLeft = left)
    }

    private suspend fun persist() {
        val s = _state.value
        store.saveSnapshot(RunSnapshot(runId, plan, startedAt,
            s.stepProgress.mapValues { (_, p) -> StepSnapshot(p.stepId, p.status, p.attempts, p.lastError) }, s.isPaused))
    }
}
