package com.ramrom.emumanager.run

import com.ramrom.emumanager.EmuApplication
import com.ramrom.emumanager.engine.install.ResolvedApk
import com.ramrom.emumanager.install.AppInstaller
import com.ramrom.emumanager.install.InstallOutcome
import com.ramrom.emumanager.knowledge.EmulatorProfile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What we know about an app's newest release. */
sealed interface Latest {
    data object Unknown : Latest
    data object Checking : Latest
    data class Known(val apk: ResolvedApk) : Latest
    data class Failed(val reason: String) : Latest
}

/** An install from S32/S33 in flight. */
sealed interface InstallState {
    data class Working(val progress: Float) : InstallState
    data class Failed(val reason: String) : InstallState
    data object Done : InstallState
}

/**
 * S32's live state: newest releases (fetched on request, kept for the session) and installs in
 * flight. Lives in the application so switching sections doesn't lose a running install.
 */
class AppsSession(private val app: EmuApplication) {
    private val _latest = MutableStateFlow<Map<String, Latest>>(emptyMap())
    val latest: StateFlow<Map<String, Latest>> = _latest.asStateFlow()
    private val _installs = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val installs: StateFlow<Map<String, InstallState>> = _installs.asStateFlow()
    private val installer by lazy { AppInstaller(app, app.access) }

    /** Ask each app's release feed once. Only apps we install ourselves have a feed. */
    suspend fun checkUpdates(apps: List<EmulatorProfile>) = coroutineScope {
        apps.filter { com.ramrom.emumanager.engine.install.Releases.releasesUrl(it.source) != null }.map { e ->
            async {
                _latest.update { it + (e.id to Latest.Checking) }
                val r = runCatching { installer.resolve(e) }
                _latest.update { it + (e.id to r.fold({ apk -> apk?.let(Latest::Known) ?: Latest.Failed("No Android build in its latest releases") }, { Latest.Failed("Couldn't reach it") })) }
            }
        }.awaitAll()
    }

    /** Download and install the newest build. In limited mode Android asks the user to confirm. */
    fun install(e: EmulatorProfile, onDone: () -> Unit = {}) {
        if (_installs.value[e.id] is InstallState.Working) return
        app.scope.launch {
            _installs.update { it + (e.id to InstallState.Working(0f)) }
            val outcome = runCatching {
                val apk = (latest.value[e.id] as? Latest.Known)?.apk ?: installer.resolve(e) ?: error("No Android build in its latest releases")
                val file = installer.download(apk) { p -> _installs.update { m -> m + (e.id to InstallState.Working(p * .9f)) } }
                installer.install(file)
            }.getOrElse { InstallOutcome.Failed(it.message ?: "Download stopped", true) }
            _installs.update {
                it + (e.id to when (outcome) {
                    InstallOutcome.Installed -> InstallState.Done
                    InstallOutcome.Cancelled -> InstallState.Failed("The install prompt was closed.")
                    is InstallOutcome.Failed -> InstallState.Failed(outcome.reason)
                })
            }
            onDone()
        }
    }
}
