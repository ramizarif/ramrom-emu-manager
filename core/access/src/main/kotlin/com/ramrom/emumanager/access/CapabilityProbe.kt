package com.ramrom.emumanager.access

import android.content.Context
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Probes Shizuku capabilities by running harmless tests.
 */
class CapabilityProbe(private val context: Context) {

    suspend fun probe(service: IAccessService): Set<Capability> = withContext(Dispatchers.IO) {
        val probeDir = getProbeDir()
        return@withContext try {
            val result = service.probeCapabilities(probeDir)
            result.mapNotNull { cap ->
                when (cap) {
                    "WRITE_APP_DATA" -> Capability.WRITE_APP_DATA
                    "INSTALL_SILENT" -> Capability.INSTALL_SILENT
                    "FORCE_STOP" -> Capability.FORCE_STOP
                    "GRANT_RUNTIME_PERMISSION" -> Capability.GRANT_RUNTIME_PERMISSION
                    "SET_APPOP" -> Capability.SET_APPOP
                    "READ_URI_GRANTS" -> Capability.READ_URI_GRANTS
                    "READ_SHARED_STORAGE" -> Capability.READ_SHARED_STORAGE
                    "WRITE_SHARED_STORAGE" -> Capability.WRITE_SHARED_STORAGE
                    "LAUNCH_APP" -> Capability.LAUNCH_APP
                    else -> null
                }
            }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun getProbeDir(): String {
        // Use our own Android/data/<pkg>/files/probe directory (absolute path)
        val probeDir = context.getExternalFilesDir("probe") ?: context.getDir("probe", Context.MODE_PRIVATE)
        return probeDir.absolutePath
    }
}
