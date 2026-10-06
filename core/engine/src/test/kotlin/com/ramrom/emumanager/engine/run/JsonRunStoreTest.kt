package com.ramrom.emumanager.engine.run

import com.ramrom.emumanager.engine.CompiledPlan
import com.ramrom.emumanager.engine.Executor
import com.ramrom.emumanager.engine.Step
import com.ramrom.emumanager.engine.StepType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class JsonRunStoreTest {
    private val plan = CompiledPlan(listOf(Step("a", StepType.InstallApp, "a", executor = Executor.Auto)))
    private val dir = Files.createTempDirectory("runs").toFile()

    @Test fun everyStatusRoundTrips() = runTest {
        val all = listOf(
            StepRunStatus.Pending, StepRunStatus.Running(.5f), StepRunStatus.Verifying, StepRunStatus.Done,
            StepRunStatus.AlreadyDone, StepRunStatus.NeedsYou("pick \"ROMs\""), StepRunStatus.Failed("no net"),
            StepRunStatus.Skipped, StepRunStatus.Blocked("x"),
        )
        val store = JsonRunStore(dir, plan)
        store.saveSnapshot(RunSnapshot("r", plan, 42, all.mapIndexed { i, s -> "s$i" to StepSnapshot("s$i", s, i, if (i == 6) "no net" else null) }.toMap(), isPaused = true))
        val back = store.loadSnapshot("r")!!
        assertEquals(42L, back.startedAtMs)
        assertTrue(back.isPaused)
        all.forEachIndexed { i, s -> assertEquals(s, back.steps.getValue("s$i").status); assertEquals(i, back.steps.getValue("s$i").attempts) }
        assertEquals("no net", back.steps.getValue("s6").lastError)
    }

    @Test fun damagedFileStartsFresh() = runTest {
        java.io.File(dir, "bad.json").writeText("{not json")
        assertNull(JsonRunStore(dir, plan).loadSnapshot("bad"))
    }

    /** A run stopped mid-way resumes from the file: done stays done, mid-flight is re-checked. */
    @Test fun runnerResumesFromFile() = runTest {
        val store = JsonRunStore(dir, plan)
        store.saveSnapshot(RunSnapshot("r2", plan, 1, mapOf("a" to StepSnapshot("a", StepRunStatus.Running(.3f)))))
        val exec = object : StepExecutor {
            var executed = 0
            override suspend fun execute(step: Step, progress: (Float) -> Unit): StepResult { executed++; return StepResult.Ok }
            override suspend fun verify(step: Step) = true
        }
        val r = WizardRunner(plan, exec, store, "r2")
        r.start(CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())); advanceUntilIdle()
        assertEquals(StepRunStatus.Done, r.state.value.stepProgress.getValue("a").status)
        assertEquals(0, exec.executed) // the device said it was already there
    }
}
