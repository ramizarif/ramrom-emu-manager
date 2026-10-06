package com.ramrom.emumanager.access

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Limited access without elevated privileges.
 * Uses MANAGE_EXTERNAL_STORAGE for file access under /storage/emulated/0 (except Android/data and Android/obb).
 * System PackageInstaller for installs is TODO.
 */
class LimitedAccess(private val context: Context) : Access {

    /**
     * Capabilities in limited mode based on actual permissions granted.
     */
    override val capabilities: Set<Capability>
        get() {
            val caps = mutableSetOf<Capability>()
            // Always can read shared storage
            caps.add(Capability.READ_SHARED_STORAGE)
            // Can write if MANAGE_EXTERNAL_STORAGE is granted
            if (Environment.isExternalStorageManager()) {
                caps.add(Capability.WRITE_SHARED_STORAGE)
            }
            return caps
        }

    /**
     * Check if a path is in protected areas (Android/data, Android/obb).
     */
    private fun isProtectedPath(path: String): Boolean {
        val normalized = path.lowercase()
        return normalized.contains("/android/data/") || normalized.contains("/android/obb/")
    }

    override suspend fun readFile(path: String): AccessResult<ByteArray?> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (isProtectedPath(path)) {
                return@withContext AccessResult.Denied("Needs elevated access")
            }
            val f = File(path)
            if (f.exists()) {
                AccessResult.Ok(f.readBytes())
            } else {
                AccessResult.Ok(null)
            }
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Read failed")
        }
    }

    override suspend fun writeFile(path: String, bytes: ByteArray): AccessResult<Unit> = withContext(Dispatchers.IO) {
        if (!Environment.isExternalStorageManager()) {
            return@withContext AccessResult.Denied("MANAGE_EXTERNAL_STORAGE not granted")
        }
        if (isProtectedPath(path)) {
            return@withContext AccessResult.Denied("Needs elevated access")
        }
        return@withContext try {
            val f = File(path)
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
            AccessResult.Ok(Unit)
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Write failed")
        }
    }

    override suspend fun deleteFile(path: String): AccessResult<Unit> = withContext(Dispatchers.IO) {
        if (!Environment.isExternalStorageManager()) {
            return@withContext AccessResult.Denied("MANAGE_EXTERNAL_STORAGE not granted")
        }
        if (isProtectedPath(path)) {
            return@withContext AccessResult.Denied("Needs elevated access")
        }
        return@withContext try {
            File(path).delete()
            AccessResult.Ok(Unit)
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Delete failed")
        }
    }

    override suspend fun listDir(path: String): AccessResult<List<String>> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (isProtectedPath(path)) {
                return@withContext AccessResult.Denied("Needs elevated access")
            }
            val dir = File(path)
            val entries = dir.listFiles()?.map { it.name } ?: emptyList()
            AccessResult.Ok(entries)
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "List failed")
        }
    }

    override suspend fun stat(path: String): AccessResult<FileStat> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (isProtectedPath(path)) {
                return@withContext AccessResult.Denied("Needs elevated access")
            }
            val f = File(path)
            if (f.exists()) {
                AccessResult.Ok(FileStat(
                    size = f.length(),
                    mtime = f.lastModified(),
                    isDir = f.isDirectory
                ))
            } else {
                AccessResult.Failed("File not found")
            }
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Stat failed")
        }
    }

    override suspend fun exists(path: String): AccessResult<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (isProtectedPath(path)) {
                return@withContext AccessResult.Denied("Needs elevated access")
            }
            AccessResult.Ok(File(path).exists())
        } catch (e: Exception) {
            AccessResult.Failed(e.message ?: "Exists check failed")
        }
    }

    override suspend fun forceStop(pkg: String): AccessResult<Unit> {
        return AccessResult.Denied("Limited access cannot force-stop apps")
    }

    override suspend fun installApk(file: String): AccessResult<Unit> {
        return AccessResult.Denied("Install from the system prompt")
    }

    override suspend fun uninstall(pkg: String): AccessResult<Unit> {
        return AccessResult.Denied("Limited access cannot uninstall apps")
    }

    override suspend fun grantPermission(pkg: String, perm: String): AccessResult<Unit> {
        return AccessResult.Denied("Limited access cannot grant permissions")
    }

    override suspend fun revokePermission(pkg: String, perm: String): AccessResult<Unit> {
        return AccessResult.Denied("Limited access cannot revoke permissions")
    }

    override suspend fun setAppOp(pkg: String, uid: Int, op: String, mode: AppOpMode): AccessResult<Unit> {
        return AccessResult.Denied("Limited access cannot set app operations")
    }

    override suspend fun dumpsys(service: String, args: String): AccessResult<String> {
        return AccessResult.Denied("Limited access cannot read dumpsys")
    }

    override suspend fun isRunning(pkg: String): AccessResult<Boolean> {
        return AccessResult.Denied("Limited access cannot check if apps are running")
    }

    /** Intent to open MANAGE_EXTERNAL_STORAGE settings. */
    fun openManageStorageIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
    }
}
