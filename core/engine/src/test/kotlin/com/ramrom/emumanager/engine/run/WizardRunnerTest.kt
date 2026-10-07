package com.ramrom.emumanager.engine.run

import com.ramrom.emumanager.engine.CompiledPlan
import com.ramrom.emumanager.engine.Executor
import com.ramrom.emumanager.engine.Step
import com.ramrom.emumanager.engine.StepStatus
import com.ramrom.emumanager.engine.StepType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WizardRunnerTest {
    private fun auto(id: String, subject: String = id, vararg deps: String) =
        Step(id, StepType.InstallApp, subject, deps.toList(), Executor.Auto, estimatedSeconds = 10)
    private fun guided(id: String, vararg deps: String) =
        Step(id, StepType.GuidedFolderPick, id, deps.toList(), Executor.Guided, userFacingText = "Pick the folder")

    /** Records order and concurrency; behaviour per step is scriptable. */
    private class FakeExecutor(
        val results: MutableMap<String, MutableList<StepResult>> = mutableMapOf(),
        val verified: MutableSet<String> = mutableSetOf(),
        val workMs: Long = 100,
        val unverifiable: Set<String> = emptySet(),
    ) : StepExecutor {
        val started = mutableListOf<String>()
        var running = 0; var maxRunning = 0
        val runningSubjects = mutableSetOf<String>(); var sameAppOverlap = false
        override suspend fun execute(step: Step, progress: (Float) -> Unit): StepResult {
            started += step.id
            running++; maxRunning = maxOf(maxRunning, running)
            if (!runningSubjects.add(step.subject)) sameAppOverlap = true
            delay(workMs)
            running--; runningSubjects.remove(step.subject)
            val r = results[step.id]?.removeFirstOrNull() ?: StepResult.Ok
            if (r == StepResult.Ok) verified += step.id
            return r
        }
        override suspend fun verify(step: Step) = step.id in verified || step.id in unverifiable
        override suspend fun canVerify(step: Step) = step.id !in unverifiable
    }

    /**
     * The runner's own scope on the test scheduler. Not backgroundScope: advanceUntilIdle() skips
     * work that exists only there, so nothing would ever run.
     */
    private fun TestScope.runnerScope() = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())

    private fun status(r: WizardRunner, id: String) = r.state.value.stepProgress.getValue(id).status

    @Test fun dependenciesRunInOrder() = runTest {
        val exec = FakeExecutor()
        val r = WizardRunner(CompiledPlan(listOf(auto("c", "c", "b"), auto("b", "b", "a"), auto("a"))), exec, InMemoryRunStore(), "run", retryDelayMs = 10)
        r.start(runnerScope()); advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), exec.started)
        assertTrue(r.state.value.isComplete); assertFalse(r.state.value.isRunning)
    }

    @Test fun parallelismIsBoundedAndUsed() = runTest {
        val exec = FakeExecutor()
        val r = WizardRunner(CompiledPlan((1..5).map { auto("s$it") }), exec, InMemoryRunStore(), "run", parallelism = 2)
        r.start(runnerScope()); advanceUntilIdle()
        assertEquals(2, exec.maxRunning)
        assertTrue(r.state.value.isComplete)
    }

    @Test fun sameAppNeverOverlaps() = runTest {
        val exec = FakeExecutor()
        val r = WizardRunner(CompiledPlan(listOf(auto("install", "dolphin"), auto("config", "dolphin"), auto("other", "eden"))), exec, InMemoryRunStore(), "run", parallelism = 3)
        r.start(runnerScope()); advanceUntilIdle()
        assertFalse(exec.sameAppOverlap)
        assertTrue(r.state.value.isComplete)
    }

    @Test fun transientFailureRetriesThenSucceeds() = runTest {
        val exec = FakeExecutor(results = mutableMapOf("a" to mutableListOf(StepResult.Failed("timeout"), StepResult.Ok)))
        val r = WizardRunner(CompiledPlan(listOf(auto("a"))), exec, InMemoryRunStore(), "run", retryDelayMs = 10)
        r.start(runnerScope()); advanceUntilIdle()
        assertEquals(StepRunStatus.Done, status(r, "a"))
        assertEquals(2, r.state.value.stepProgress.getValue("a").attempts)
    }

    @Test fun permanentFailureStopsDependentsAndUserRetryRecovers() = runTest {
        val exec = FakeExecutor(results = mutableMapOf("a" to mutableListOf(StepResult.Failed("bad apk", retryable = false))))
        val r = WizardRunner(CompiledPlan(listOf(auto("a"), auto("b", "b", "a"))), exec, InMemoryRunStore(), "run")
        r.start(runnerScope()); advanceUntilIdle()
        assertEquals(StepRunStatus.Failed("bad apk"), status(r, "a"))
        assertEquals(StepRunStatus.Pending, status(r, "b"))
        assertFalse("nothing can move without the user", r.state.value.isRunning)
        r.retryStep("a"); advanceUntilIdle()
        assertTrue(r.state.value.isComplete)
    }

    @Test fun skipBlocksDependentsAndTheRunStillSettles() = runTest {
        val exec = FakeExecutor()
        val r = WizardRunner(CompiledPlan(listOf(guided("pick"), auto("cfg", "cfg", "pick"), auto("other"))), exec, InMemoryRunStore(), "run")
        r.start(runnerScope()); advanceUntilIdle()
        assertTrue(status(r, "pick") is StepRunStatus.NeedsYou)
        r.skipStepForNow("pick"); advanceUntilIdle()
        assertEquals(StepRunStatus.Blocked("pick"), status(r, "cfg"))
        assertEquals(StepRunStatus.Done, status(r, "other"))
        assertFalse(r.state.value.isRunning)
        assertFalse(r.state.value.isComplete)
        r.retryStep("pick"); advanceUntilIdle()
        assertEquals(StepRunStatus.Pending, status(r, "cfg"))
        assertTrue(status(r, "pick") is StepRunStatus.NeedsYou)
    }

    @Test fun guidedStepCountsOnlyWhenTheDeviceAgrees() = runTest {
        val exec = FakeExecutor()
        val r = WizardRunner(CompiledPlan(listOf(guided("pick"), auto("cfg", "cfg", "pick"))), exec, InMemoryRunStore(), "run")
        r.start(runnerScope()); advanceUntilIdle()
        r.markGuidedDone("pick"); advanceUntilIdle()
        assertTrue("not verified yet", status(r, "pick") is StepRunStatus.NeedsYou)
        exec.verified += "pick"
        r.markGuidedDone("pick"); advanceUntilIdle()
        assertTrue(r.state.value.isComplete)
    }

    @Test fun aStepWeCantCheckWaitsForTheUsersWord() = runTest {
        // Vita3K's firmware: coming back from the app must not count, "I did it" must.
        val r = WizardRunner(CompiledPlan(listOf(guided("firmware"))), FakeExecutor(unverifiable = setOf("firmware")), InMemoryRunStore(), "run")
        r.start(runnerScope()); advanceUntilIdle()
        r.recheck("firmware"); advanceUntilIdle()
        assertTrue(status(r, "firmware") is StepRunStatus.NeedsYou)
        r.markGuidedDone("firmware"); advanceUntilIdle()
        assertTrue(r.state.value.isComplete)
    }

    @Test fun aGuidedStepAlreadyTrueNeedsNobody() = runTest {
        // Eden's keys are already in: the step settles without asking.
        val exec = FakeExecutor(verified = mutableSetOf("keys"))
        val r = WizardRunner(CompiledPlan(listOf(guided("keys"))), exec, InMemoryRunStore(), "run")
        r.start(runnerScope()); advanceUntilIdle()
        assertTrue(r.state.value.isComplete)
    }

    @Test fun pauseHoldsNewStepsAndResumeContinues() = runTest {
        val exec = FakeExecutor()
        val r = WizardRunner(CompiledPlan(listOf(auto("a"), auto("b", "b", "a"))), exec, InMemoryRunStore(), "run")
        r.pause()
        r.start(runnerScope()); advanceUntilIdle()
        assertTrue(exec.started.isEmpty())
        r.resume(); advanceUntilIdle()
        assertTrue(r.state.value.isComplete)
    }

    @Test fun crashMidStepResumesByVerifyingNotRerunning() = runTest {
        val store = InMemoryRunStore()
        val plan = CompiledPlan(listOf(auto("a"), auto("b", "b", "a")))
        // A previous process died while "a" was running; the install had actually finished.
        store.saveSnapshot(RunSnapshot("run", plan, 0, mapOf(
            "a" to StepSnapshot("a", StepRunStatus.Running(0.6f)),
            "b" to StepSnapshot("b", StepRunStatus.Pending)), false))
        val exec = FakeExecutor(verified = mutableSetOf("a"))
        val r = WizardRunner(plan, exec, store, "run")
        r.start(runnerScope()); advanceUntilIdle()
        assertEquals(listOf("b"), exec.started)
        assertTrue(r.state.value.isComplete)
    }

    @Test fun crashMidStepRerunsWhenTheDeviceSaysItDidntHappen() = runTest {
        val store = InMemoryRunStore()
        val plan = CompiledPlan(listOf(auto("a")))
        store.saveSnapshot(RunSnapshot("run", plan, 0, mapOf("a" to StepSnapshot("a", StepRunStatus.Running())), false))
        val exec = FakeExecutor()
        val r = WizardRunner(plan, exec, store, "run")
        r.start(runnerScope()); advanceUntilIdle()
        assertEquals(listOf("a"), exec.started)
        assertTrue(r.state.value.isComplete)
    }

    @Test fun everyTransitionIsSaved() = runTest {
        val store = InMemoryRunStore()
        val r = WizardRunner(CompiledPlan(listOf(auto("a"))), FakeExecutor(), store, "run")
        r.start(runnerScope()); advanceUntilIdle()
        assertEquals(StepRunStatus.Done, store.loadSnapshot("run")!!.steps.getValue("a").status)
    }

    @Test fun alreadyDoneStepsAreNotRun() = runTest {
        val exec = FakeExecutor()
        val plan = CompiledPlan(listOf(auto("a").copy(status = StepStatus.AlreadyDone), auto("b", "b", "a")))
        val r = WizardRunner(plan, exec, InMemoryRunStore(), "run")
        r.start(runnerScope()); advanceUntilIdle()
        assertEquals(listOf("b"), exec.started)
    }
}
