package com.ramrom.emumanager.access

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for privileged operations. Callers ask for capabilities, never for "Shizuku".
 *
 * Suspend functions return [AccessResult] to indicate success, denial, or failure.
 * All paths must be absolute (e.g., /storage/emulated/0/Android/data/...).
 */
interface Access {
    /** Set of capabilities currently available. */
    val capabilities: Set<Capability>

    /** Read a file, returning null if not found or denied. */
    suspend fun readFile(path: String): AccessResult<ByteArray?>

    /** Write a file (creates or overwrites). */
    suspend fun writeFile(path: String, bytes: ByteArray): AccessResult<Unit>

    /** Delete a file. */
    suspend fun deleteFile(path: String): AccessResult<Unit>

    /** List directory contents. */
    suspend fun listDir(path: String): AccessResult<List<String>>

    /** Get file/directory stats (size, mtime, etc). */
    suspend fun stat(path: String): AccessResult<FileStat>

    /** Check if a path exists. */
    suspend fun exists(path: String): AccessResult<Boolean>

    /** MD5 of a file, lowercase hex, or null if it isn't there. Reads it whole unless an implementation can hash in place. */
    suspend fun md5(path: String): AccessResult<String?> = when (val r = readFile(path)) {
        is AccessResult.Ok -> AccessResult.Ok(r.value?.let { b -> java.security.MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) } })
        is AccessResult.Denied -> r
        is AccessResult.Failed -> r
    }

    /** Force-stop a package. */
    suspend fun forceStop(pkg: String): AccessResult<Unit>

    /** Install an APK from a file. */
    suspend fun installApk(file: String): AccessResult<Unit>

    /** Uninstall a package. */
    suspend fun uninstall(pkg: String): AccessResult<Unit>

    /** Grant a runtime permission. */
    suspend fun grantPermission(pkg: String, perm: String): AccessResult<Unit>

    /** Revoke a runtime permission. */
    suspend fun revokePermission(pkg: String, perm: String): AccessResult<Unit>

    /** Set an app operation mode. */
    suspend fun setAppOp(pkg: String, uid: Int, op: String, mode: AppOpMode): AccessResult<Unit>

    /** Get restricted dumpsys output (activity permissions, package info). */
    suspend fun dumpsys(service: String, args: String): AccessResult<String>

    /** Check if a package is running. */
    suspend fun isRunning(pkg: String): AccessResult<Boolean>
}

data class FileStat(
    val size: Long,
    val mtime: Long,
    val isDir: Boolean,
)

enum class AppOpMode {
    ALLOW,
    DENY,
    IGNORE,
}
