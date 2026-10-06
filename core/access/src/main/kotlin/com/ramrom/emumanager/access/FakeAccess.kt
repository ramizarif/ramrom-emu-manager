package com.ramrom.emumanager.access

/**
 * Fake Access implementation for tests and previews.
 * All operations succeed with predictable results.
 */
class FakeAccess(
    initialCapabilities: Set<Capability> = Capability.entries.toSet(),
) : Access {

    override var capabilities: Set<Capability> = initialCapabilities
        private set

    private val fileStore = mutableMapOf<String, ByteArray>()
    private val runningApps = mutableSetOf<String>()

    override suspend fun readFile(path: String): AccessResult<ByteArray?> {
        return AccessResult.Ok(fileStore[path])
    }

    override suspend fun writeFile(path: String, bytes: ByteArray): AccessResult<Unit> {
        fileStore[path] = bytes
        return AccessResult.Ok(Unit)
    }

    override suspend fun deleteFile(path: String): AccessResult<Unit> {
        fileStore.remove(path)
        return AccessResult.Ok(Unit)
    }

    override suspend fun listDir(path: String): AccessResult<List<String>> {
        return AccessResult.Ok(fileStore.keys.filter { it.startsWith(path) })
    }

    override suspend fun stat(path: String): AccessResult<FileStat> {
        val bytes = fileStore[path]
        return if (bytes != null) {
            AccessResult.Ok(FileStat(size = bytes.size.toLong(), mtime = 0, isDir = false))
        } else {
            AccessResult.Failed("Not found")
        }
    }

    override suspend fun exists(path: String): AccessResult<Boolean> {
        return AccessResult.Ok(fileStore.containsKey(path))
    }

    override suspend fun forceStop(pkg: String): AccessResult<Unit> {
        runningApps.remove(pkg)
        return AccessResult.Ok(Unit)
    }

    override suspend fun installApk(file: String): AccessResult<Unit> {
        return AccessResult.Ok(Unit)
    }

    override suspend fun uninstall(pkg: String): AccessResult<Unit> {
        return AccessResult.Ok(Unit)
    }

    override suspend fun grantPermission(pkg: String, perm: String): AccessResult<Unit> {
        return AccessResult.Ok(Unit)
    }

    override suspend fun revokePermission(pkg: String, perm: String): AccessResult<Unit> {
        return AccessResult.Ok(Unit)
    }

    override suspend fun setAppOp(pkg: String, uid: Int, op: String, mode: AppOpMode): AccessResult<Unit> {
        return AccessResult.Ok(Unit)
    }

    override suspend fun dumpsys(service: String, args: String): AccessResult<String> {
        return AccessResult.Ok("fake dumpsys output")
    }

    override suspend fun isRunning(pkg: String): AccessResult<Boolean> {
        return AccessResult.Ok(runningApps.contains(pkg))
    }

    // Helpers for testing
    fun setCapabilities(caps: Set<Capability>) {
        capabilities = caps
    }

    fun markAppRunning(pkg: String) {
        runningApps.add(pkg)
    }

    fun markAppStopped(pkg: String) {
        runningApps.remove(pkg)
    }
}
