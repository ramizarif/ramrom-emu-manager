package com.ramrom.emumanager.access

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Elevated access via Shizuku UserService running as shell uid 2000.
 * All paths must be absolute (e.g., /storage/emulated/0/Android/data/...).
 */
class ShizukuAccess(
    private val context: Context,
    private val probe: CapabilityProbe,
) : Access {

    override var capabilities: Set<Capability> = emptySet()
        private set

    private var service: IAccessService? = null
    private var connectContinuation: ((Boolean) -> Unit)? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IAccessService.Stub.asInterface(binder)
            connectContinuation?.invoke(true)
            connectContinuation = null
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    /**
     * Connect to Shizuku UserService.
     * Suspends until onServiceConnected (or timeout 5s).
     * Returns true if successful and probed, false otherwise.
     */
    suspend fun connect(): Boolean = withContext(Dispatchers.Main) {
        if (service != null) return@withContext true // Already connected

        return@withContext (withTimeoutOrNull(5000L) {
            suspendCancellableCoroutine { cont ->
                connectContinuation = { success ->
                    cont.resume(success)
                }

                try {
                    val args = Shizuku.UserServiceArgs(
                        ComponentName(context.packageName, AccessService::class.java.name)
                    ).daemon(false).processNameSuffix("access").version(3) // bump when AccessService or AllowList changes

                    Shizuku.bindUserService(args, conn)
                } catch (e: Exception) {
                    connectContinuation = null
                    cont.resume(false)
                }
            }
        } ?: false) && probeCapabilities()
    }

    private suspend fun probeCapabilities(): Boolean = withContext(Dispatchers.IO) {
        val svc = service ?: return@withContext false
        return@withContext try {
            capabilities = probe.probe(svc)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun disconnect() {
        try {
            if (service != null) {
                Shizuku.unbindUserService(
                    Shizuku.UserServiceArgs(
                        ComponentName(context.packageName, AccessService::class.java.name)
                    ),
                    conn,
                    true
                )
            }
        } catch (e: Exception) {
            // Ignore
        }
        service = null
    }

    override suspend fun readFile(path: String): AccessResult<ByteArray?> = withContext(Dispatchers.IO) {
        if (Capability.READ_SHARED_STORAGE !in capabilities && Capability.WRITE_APP_DATA !in capabilities) {
            return@withContext AccessResult.Denied("Read capability not available")
        }
        val svc = service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            val bytes = svc.readFile(path)
            AccessResult.Ok(bytes)
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Read failed")
        }
    }

    override suspend fun writeFile(path: String, bytes: ByteArray): AccessResult<Unit> = withContext(Dispatchers.IO) {
        if (Capability.WRITE_APP_DATA !in capabilities) {
            return@withContext AccessResult.Denied("Write capability not available")
        }
        val svc = service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            svc.writeFile(path, bytes)
            AccessResult.Ok(Unit)
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Write failed")
        }
    }

    override suspend fun deleteFile(path: String): AccessResult<Unit> = withContext(Dispatchers.IO) {
        if (Capability.WRITE_APP_DATA !in capabilities) {
            return@withContext AccessResult.Denied("Delete capability not available")
        }
        val svc = service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            svc.deleteFile(path)
            AccessResult.Ok(Unit)
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Delete failed")
        }
    }

    override suspend fun listDir(path: String): AccessResult<List<String>> = withContext(Dispatchers.IO) {
        if (Capability.READ_SHARED_STORAGE !in capabilities && Capability.WRITE_APP_DATA !in capabilities) {
            return@withContext AccessResult.Denied("List capability not available")
        }
        val svc = service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            val entries = svc.listDir(path)
            AccessResult.Ok(entries?.toList() ?: emptyList())
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "List failed")
        }
    }

    override suspend fun stat(path: String): AccessResult<FileStat> = withContext(Dispatchers.IO) {
        // On the device, as shell: this side can't see Android/data, and reading a big file to size it overflows binder.
        val svc = service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            val parts = svc.exec(arrayOf("stat", "-c", "%s %Y %F", path)).trim().split(' ', limit = 3)
            val size = parts.getOrNull(0)?.toLongOrNull() ?: return@withContext AccessResult.Failed("Not found: $path")
            AccessResult.Ok(FileStat(size = size, mtime = (parts.getOrNull(1)?.toLongOrNull() ?: 0) * 1000, isDir = parts.getOrNull(2) == "directory"))
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Stat failed")
        }
    }

    override suspend fun exists(path: String): AccessResult<Boolean> {
        // List the parent rather than read the file: BIOS images run to megabytes.
        val parent = path.trimEnd('/').substringBeforeLast('/')
        val name = path.trimEnd('/').substringAfterLast('/')
        return when (val r = listDir(parent)) {
            is AccessResult.Ok -> AccessResult.Ok(name in r.value)
            is AccessResult.Denied -> r
            is AccessResult.Failed -> AccessResult.Ok(false)
        }
    }

    override suspend fun forceStop(pkg: String): AccessResult<Unit> = withContext(Dispatchers.IO) {
        if (Capability.FORCE_STOP !in capabilities) {
            return@withContext AccessResult.Denied("Force-stop capability not available")
        }
        val svc = service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            svc.run("am", "force-stop", pkg)
            AccessResult.Ok(Unit)
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Force-stop failed")
        }
    }

    override suspend fun installApk(file: String): AccessResult<Unit> = withContext(Dispatchers.IO) {
        if (Capability.INSTALL_SILENT !in capabilities) {
            return@withContext AccessResult.Denied("Install capability not available")
        }
        val svc = service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            val f = java.io.File(file)
            val output = android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                svc.installApk(fd, f.length())
            }
            // pm prints "Success" or "Failure [INSTALL_FAILED_...]"; anything else is a failure too.
            if (output.lines().any { it.trim() == "Success" }) AccessResult.Ok(Unit)
            else AccessResult.Failed(output.trim().ifEmpty { "Install failed" })
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Install failed")
        }
    }

    override suspend fun uninstall(pkg: String): AccessResult<Unit> = withContext(Dispatchers.IO) {
        if (Capability.INSTALL_SILENT !in capabilities) {
            return@withContext AccessResult.Denied("Uninstall capability not available")
        }
        val svc = service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            svc.run("pm", "uninstall", pkg)
            AccessResult.Ok(Unit)
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Uninstall failed")
        }
    }

    override suspend fun grantPermission(pkg: String, perm: String): AccessResult<Unit> = withContext(Dispatchers.IO) {
        if (Capability.GRANT_RUNTIME_PERMISSION !in capabilities) {
            return@withContext AccessResult.Denied("Grant permission capability not available")
        }
        val svc = service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            svc.run("pm", "grant", pkg, perm)
            AccessResult.Ok(Unit)
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Grant failed")
        }
    }

    override suspend fun revokePermission(pkg: String, perm: String): AccessResult<Unit> = withContext(Dispatchers.IO) {
        if (Capability.GRANT_RUNTIME_PERMISSION !in capabilities) {
            return@withContext AccessResult.Denied("Revoke permission capability not available")
        }
        val svc = service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            svc.run("pm", "revoke", pkg, perm)
            AccessResult.Ok(Unit)
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Revoke failed")
        }
    }

    override suspend fun setAppOp(pkg: String, uid: Int, op: String, mode: AppOpMode): AccessResult<Unit> = withContext(Dispatchers.IO) {
        if (Capability.SET_APPOP !in capabilities) {
            return@withContext AccessResult.Denied("AppOp capability not available")
        }
        val svc = service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            val modeStr = when (mode) {
                AppOpMode.ALLOW -> "allow"
                AppOpMode.DENY -> "deny"
                AppOpMode.IGNORE -> "ignore"
            }
            // `--uid <package>`: the uid-wide mode Settings shows. A uid number in that place is read as the op.
            svc.run("appops", "set", "--uid", pkg, op, modeStr)
            AccessResult.Ok(Unit)
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "SetAppOp failed")
        }
    }

    override suspend fun dumpsys(service: String, args: String): AccessResult<String> = withContext(Dispatchers.IO) {
        if (Capability.READ_URI_GRANTS !in capabilities) {
            return@withContext AccessResult.Denied("Dumpsys capability not available")
        }
        val svc = this@ShizukuAccess.service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            val output = svc.exec(arrayOf("dumpsys", service) + args.split(" ").filter { it.isNotEmpty() })
            AccessResult.Ok(output)
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Dumpsys failed")
        }
    }

    /** Hashed by toybox on the device: a 4 MB PS2 BIOS is too big to pass over binder. */
    override suspend fun md5(path: String): AccessResult<String?> = withContext(Dispatchers.IO) {
        val svc = service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            AccessResult.Ok(svc.exec(arrayOf("md5sum", path)).trim().substringBefore(' ').lowercase().takeIf { it.matches(Regex("[0-9a-f]{32}")) })
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "md5 failed")
        }
    }

    override suspend fun isRunning(pkg: String): AccessResult<Boolean> = withContext(Dispatchers.IO) {
        val svc = service ?: return@withContext AccessResult.Denied("Shizuku disconnected")
        return@withContext try {
            val output = svc.exec(arrayOf("pidof", pkg))
            AccessResult.Ok(output.isNotBlank())
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "IsRunning failed")
        }
    }

    /**
     * Runs a command that prints nothing on success. These tools report failure in their output and
     * exit codes we can't see, so an error line becomes an exception rather than a silent "done".
     */
    private fun IAccessService.run(vararg argv: String) {
        val out = exec(arrayOf(*argv))
        val bad = out.lines().firstOrNull { l -> FAILURE.any { l.trimStart().startsWith(it) } }
        if (bad != null) throw RuntimeException(bad.trim())
    }

    private companion object {
        val FAILURE = listOf("Error", "Exception", "Failure", "Security exception", "java.lang.", "Unknown ")
    }
}
