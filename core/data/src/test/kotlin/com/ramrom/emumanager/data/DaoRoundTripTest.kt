package com.ramrom.emumanager.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** DAO round-trips on an in-memory Room database under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DaoRoundTripTest {
    private lateinit var db: EmuDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), EmuDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun testAppStateRoundTrip() = runBlocking {
        val dao = db.appStateDao()

        val state = AppState(
            id = 0,
            noticeVersionAck = 3,
            deviceProfileId = "ayn-thor",
            mode = AppMode.ELEVATED,
            firstRunDone = true
        )

        dao.insert(state)

        val retrieved = dao.getAppState().first()
        assertNotNull(retrieved)
        assertEquals("ayn-thor", retrieved.deviceProfileId)
        assertEquals(3, retrieved.noticeVersionAck)
        assertEquals(true, retrieved.firstRunDone)
    }

    @Test
    fun testSetupPlanWithSystemsRoundTrip() = runBlocking {
        val dao = db.setupPlanDao()

        val plan = SetupPlan(
            id = "plan-1",
            origin = PlanOrigin.NEW,
            targetDeviceProfileId = "ayn-thor",
            frontendId = "es-de",
            systems = listOf(
                PlanSystem(systemId = "nes", emulatorId = "nestopia"),
                PlanSystem(systemId = "snes", emulatorId = "snes9x"),
                PlanSystem(systemId = "n64", emulatorId = "mupen64plus")
            ),
            biosCheck = true,
            createdAt = 1000,
            updatedAt = 1000
        )

        dao.insert(plan)

        val retrieved = dao.getPlanById("plan-1").first()
        assertNotNull(retrieved)
        assertEquals(3, retrieved.systems.size)
        assertEquals("nes", retrieved.systems[0].systemId)
        assertEquals("nestopia", retrieved.systems[0].emulatorId)
        assertEquals("snes", retrieved.systems[1].systemId)
    }

    @Test
    fun testWizardStepAtomicUpdate() = runBlocking {
        val runDao = db.wizardRunDao()
        val planDao = db.setupPlanDao()
        val stepDao = db.wizardStepDao()

        // Insert plan and run
        val plan = SetupPlan(
            id = "plan-1",
            origin = PlanOrigin.NEW,
            targetDeviceProfileId = "ayn-thor",
            frontendId = null,
            systems = emptyList(),
            createdAt = 1000,
            updatedAt = 1000
        )
        planDao.insert(plan)

        val run = WizardRun(
            id = "run-1",
            planId = "plan-1",
            startedAt = 2000,
            status = WizardRunStatus.IN_PROGRESS
        )
        runDao.insert(run)

        // Insert initial step
        val step = WizardStep(
            runId = "run-1",
            stepId = "step-1",
            type = WizardStepType.INSTALL_APP,
            executor = WizardStepExecutor.AUTO,
            status = WizardStepStatus.PENDING
        )
        stepDao.insert(step)

        // Atomic update: status + attempts + verifiedAt
        stepDao.updateStepStatusAtomic(
            runId = "run-1",
            stepId = "step-1",
            status = WizardStepStatus.DONE,
            attempts = 1,
            verifiedAt = 3000,
            lastError = null
        )

        val retrieved = stepDao.getStep("run-1", "step-1").first()
        assertNotNull(retrieved)
        assertEquals(WizardStepStatus.DONE, retrieved.status)
        assertEquals(1, retrieved.attempts)
        assertEquals(3000, retrieved.verifiedAt)
    }

    @Test
    fun testConfigChangeByGameId() = runBlocking {
        val dao = db.configChangeDao()

        val change1 = ConfigChange(
            id = "change-1",
            at = 1000,
            target = ConfigChangeTarget.FILE,
            reason = ConfigChangeReason.PROFILE,
            gameId = "game-1",
            snapshotPath = "/path/1"
        )
        val change2 = ConfigChange(
            id = "change-2",
            at = 2000,
            target = ConfigChangeTarget.APP,
            reason = ConfigChangeReason.FIX,
            gameId = "game-1",
            snapshotPath = "/path/2"
        )
        val change3 = ConfigChange(
            id = "change-3",
            at = 3000,
            target = ConfigChangeTarget.FILE,
            reason = ConfigChangeReason.CUSTOMIZE,
            gameId = "game-2",
            snapshotPath = "/path/3"
        )

        dao.insert(change1)
        dao.insert(change2)
        dao.insert(change3)

        val forGame1 = dao.getChangesByGameId("game-1").first()
        assertEquals(2, forGame1.size)
        assertTrue(forGame1.all { it.gameId == "game-1" })
        assertEquals("change-2", forGame1[0].id) // most recent first
        assertEquals("change-1", forGame1[1].id)
    }

    @Test
    fun testHealthIssueUniqueConstraint() = runBlocking {
        val dao = db.healthIssueDao()

        val issue1 = HealthIssue(
            id = "issue-1",
            ruleId = "rule-biosCheck",
            subject = "nes",
            severity = HealthIssueSeverity.BLOCKING,
            firstSeen = 1000,
            lastSeen = 1000
        )
        val issue2 = HealthIssue(
            id = "issue-2",
            ruleId = "rule-biosCheck",
            subject = "snes",
            severity = HealthIssueSeverity.WARNING,
            firstSeen = 2000,
            lastSeen = 2000
        )

        dao.insert(issue1)
        dao.insert(issue2)

        // Query by rule + subject (unique index)
        val nesBiosIssue = dao.getByRuleAndSubject("rule-biosCheck", "nes").first()
        assertNotNull(nesBiosIssue)
        assertEquals("nes", nesBiosIssue.subject)
        assertEquals(HealthIssueSeverity.BLOCKING, nesBiosIssue.severity)
    }

    @Test
    fun testWishlistItemSearch() = runBlocking {
        val dao = db.wishlistItemDao()

        val item1 = WishlistItem(
            id = "wish-1",
            system = "nes",
            name = "Super Mario Bros",
            status = WishlistItemStatus.WISHLIST,
            addedAt = 1000
        )
        val item2 = WishlistItem(
            id = "wish-2",
            system = "snes",
            name = "A Link to the Past",
            status = WishlistItemStatus.OWNED,
            addedAt = 2000
        )
        val item3 = WishlistItem(
            id = "wish-3",
            system = "nes",
            name = "Mario Bros",
            status = WishlistItemStatus.WISHLIST,
            addedAt = 3000
        )

        dao.insert(item1)
        dao.insert(item2)
        dao.insert(item3)

        // Search for "Mario" (case-sensitive LIKE)
        val marioResults = dao.searchByName("%Mario%", limit = 10).first()
        assertTrue(marioResults.size >= 2) // should find both "Super Mario Bros" and "Mario Bros"
        assertTrue(marioResults.any { it.name == "Super Mario Bros" })
        assertTrue(marioResults.any { it.name == "Mario Bros" })
    }
}
