package com.ramrom.emumanager

import android.app.Application
import com.ramrom.emumanager.access.AccessManager
import com.ramrom.emumanager.data.EmuDatabase
import com.ramrom.emumanager.data.EmuLog
import com.ramrom.emumanager.entitlements.Entitlements
import com.ramrom.emumanager.entitlements.FakeEntitlements
import com.ramrom.emumanager.knowledge.KnowledgeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** App-wide singletons. Manual wiring keeps the graph obvious; there are only a handful. */
class EmuApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val knowledge by lazy { KnowledgeStore(this).also { it.load() } }
    val access by lazy { AccessManager(this) }
    val db by lazy { EmuDatabase.create(this) }
    val log by lazy { EmuLog(this) }
    val appState by lazy { com.ramrom.emumanager.data.AppStateRepository(db.appStateDao()) }
    /** Offline title search (P20). Heavy to build; first use is off the main thread. */
    val titleSearch by lazy { com.ramrom.emumanager.knowledge.TitleSearch(com.ramrom.emumanager.knowledge.TitleIndex.load(this).getAllEntries()) }
    /** The one setup run (P05); outlives screens. */
    val runs by lazy { com.ramrom.emumanager.run.SetupRuns(this) }
    /** P13's latest report. */
    val health by lazy { com.ramrom.emumanager.run.HealthSession(this) }
    /** S32's release checks and installs. */
    val apps by lazy { com.ramrom.emumanager.run.AppsSession(this) }
    /** The real Lemon Squeezy client replaces this in M6. */
    val entitlements: Entitlements by lazy { FakeEntitlements() }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            access.initialize()
            // A run the process lost picks up where the device actually is.
            runCatching { runs.resumeIfAny() }.onFailure { log.e("run", "resume failed", it) }
        }
    }
}
