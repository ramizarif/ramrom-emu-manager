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
    /** The real Lemon Squeezy client replaces this in M6. */
    val entitlements: Entitlements by lazy { FakeEntitlements() }

    override fun onCreate() {
        super.onCreate()
        scope.launch { access.initialize() }
    }
}
