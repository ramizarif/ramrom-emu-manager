package com.ramrom.emumanager.access

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Manages access implementations (elevated/limited) and picks the right one.
 * Handles Shizuku lifecycle, permissions, and mode transitions.
 */
class AccessManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val shizukuStatus = ShizukuStatus(context)
    private val probe = CapabilityProbe(context)

    private val elevatedAccess = ShizukuAccess(context, probe)
    private val limitedAccess = LimitedAccess(context)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    // A saved limited choice needs no probing, so it holds from the first frame; otherwise screens
    // read NotSetUp until initialize() finishes and send the user back through access setup.
    private val _mode = MutableStateFlow(
        if (prefs.getString(PREF_CHOICE, null) == CHOICE_LIMITED) AccessMode.Limited else AccessMode.NotSetUp
    )
    private val _setup = MutableStateFlow<ShizukuSetup>(ShizukuSetup(false, false, false, ShizukuPermission.NotAsked))
    private val _capabilities = MutableStateFlow(if (_mode.value == AccessMode.Limited) limitedAccess.capabilities else emptySet())

    val mode: StateFlow<AccessMode> = _mode.asStateFlow()
    val setup: StateFlow<ShizukuSetup> = _setup.asStateFlow()
    val capabilities: StateFlow<Set<Capability>> = _capabilities.asStateFlow()

    val shizuku: ShizukuStatus = shizukuStatus

    /** Current Access implementation (elevated or limited). */
    val access: Access
        get() = when (_mode.value) {
            AccessMode.Elevated -> elevatedAccess
            AccessMode.Limited -> limitedAccess
            else -> limitedAccess
        }

    init {
        // Register Shizuku binder listeners once
        Shizuku.addBinderReceivedListenerSticky {
            onBinderReceived()
        }
        Shizuku.addBinderDeadListener {
            onBinderDead()
        }
        // The answer to our permission request. Denied is only ever shown after the user said no.
        Shizuku.addRequestPermissionResultListener { _, result ->
            prefs.edit().putBoolean(PREF_DENIED, result != android.content.pm.PackageManager.PERMISSION_GRANTED).apply()
            refresh()
        }
    }

    /**
     * Refresh setup state (called on resume and periodically while S04 is open).
     */
    fun refresh() {
        val s = shizukuStatus.getSetup()
        _setup.value = if (s.permission == ShizukuPermission.Denied && !prefs.getBoolean(PREF_DENIED, false)) {
            s.copy(permission = ShizukuPermission.NotAsked)
        } else s
    }

    /**
     * Request Shizuku permission (result arrives via OnRequestPermissionResultListener -> setup updates).
     */
    fun requestPermission() {
        shizukuStatus.requestPermission()
    }

    /**
     * Connect to Shizuku UserService and run capability probe.
     * Suspends until onServiceConnected or timeout (5s).
     * Returns true if elevated, false if connect failed.
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.Main) {
        if (setup.value.permission != ShizukuPermission.Granted) {
            return@withContext false
        }

        val result = elevatedAccess.connect()
        if (result) {
            _capabilities.value = elevatedAccess.capabilities
            _mode.value = AccessMode.Elevated
            prefs.edit().putString(PREF_CHOICE, CHOICE_ELEVATED).apply()
            return@withContext true
        }
        return@withContext false
    }

    /**
     * User chose limited mode; persist the choice.
     */
    fun chooseLimited() {
        _mode.value = AccessMode.Limited
        _capabilities.value = limitedAccess.capabilities
        prefs.edit().putString(PREF_CHOICE, CHOICE_LIMITED).apply()
    }

    /**
     * Initialize on app start based on persisted choice and binder state.
     */
    suspend fun initialize() = withContext(Dispatchers.Main) {
        refresh()

        val savedChoice = prefs.getString(PREF_CHOICE, null)
        val binderAlive = shizukuStatus.isServerRunning()
        val permissionGranted = setup.value.permission == ShizukuPermission.Granted

        _mode.value = computeAccessMode(savedChoice, binderAlive, permissionGranted)

        when (_mode.value) {
            AccessMode.Elevated -> if (!connect()) _mode.value = AccessMode.Disconnected
            AccessMode.Limited -> _capabilities.value = limitedAccess.capabilities
            else -> {} // NotSetUp or Disconnected
        }
    }

    private fun onBinderReceived() {
        refresh()
        // Auto-reconnect if we were elevated and binder came back with permission
        if (_mode.value == AccessMode.Disconnected && setup.value.permission == ShizukuPermission.Granted) {
            // This is called from Shizuku's thread; post reconnect to main
            val savedChoice = prefs.getString(PREF_CHOICE, null)
            if (savedChoice == CHOICE_ELEVATED) scope.launch { connect() }
        }
    }

    private fun onBinderDead() {
        refresh()
        // If we were elevated, transition to Disconnected
        if (_mode.value == AccessMode.Elevated) {
            _mode.value = AccessMode.Disconnected
        }
    }

    companion object {
        private const val PREFS_NAME = "access_manager"
        private const val PREF_CHOICE = "choice"
        private const val CHOICE_ELEVATED = "elevated"
        private const val CHOICE_LIMITED = "limited"
        private const val PREF_DENIED = "denied"

        /**
         * Pure function to compute AccessMode from inputs.
         * Testable without Android context.
         */
        fun computeAccessMode(
            persistedChoice: String?,
            binderAlive: Boolean,
            permissionGranted: Boolean,
        ): AccessMode {
            return when (persistedChoice) {
                CHOICE_ELEVATED -> {
                    when {
                        binderAlive && permissionGranted -> AccessMode.Elevated
                        binderAlive -> AccessMode.Disconnected
                        else -> AccessMode.Disconnected
                    }
                }
                CHOICE_LIMITED -> AccessMode.Limited
                else -> AccessMode.NotSetUp
            }
        }
    }
}
