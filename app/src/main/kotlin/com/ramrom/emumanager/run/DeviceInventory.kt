package com.ramrom.emumanager.run

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import com.ramrom.emumanager.access.Access
import com.ramrom.emumanager.access.AccessManager
import com.ramrom.emumanager.access.AccessMode
import com.ramrom.emumanager.access.AccessResult
import com.ramrom.emumanager.engine.Inventory
import com.ramrom.emumanager.engine.inventory.DeviceFacts
import com.ramrom.emumanager.engine.inventory.FileLister
import com.ramrom.emumanager.engine.inventory.InstalledApp
import com.ramrom.emumanager.engine.inventory.InventoryBuilder
import com.ramrom.emumanager.engine.inventory.UriGrants
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/** P03 on this device: reads apps, grants and files, then lets the engine build the [Inventory]. */
class DeviceInventory(private val context: Context, private val bundle: KnowledgeBundle, private val access: AccessManager) {
    private val pm = context.packageManager

    suspend fun scan(): Inventory = withContext(Dispatchers.IO) {
        val elevated = access.mode.value == AccessMode.Elevated
        val installed = (bundle.emulators.flatMap { it.packages } + bundle.frontends.flatMap { it.packages }).distinct().mapNotNull { pkg ->
            runCatching { pm.getPackageInfo(pkg, 0) }.getOrNull()?.let { pkg to InstalledApp(pkg, it.versionName.orEmpty(), it.longVersionCode) }
        }.toMap()
        val ops = context.getSystemService(AppOpsManager::class.java)
        val facts = DeviceFacts(
            installed = installed,
            allFilesAccess = installed.keys.associateWith { pkg ->
                val uid = runCatching { pm.getApplicationInfo(pkg, 0).uid }.getOrNull()
                uid != null && ops.unsafeCheckOpNoThrow("android:manage_external_storage", uid, pkg) == AppOpsManager.MODE_ALLOWED
            },
            grantedPermissions = installed.keys.associateWith { pkg ->
                bundle.emulators.firstOrNull { pkg in it.packages }?.permissions.orEmpty()
                    .filter { pm.checkPermission(it, pkg) == PackageManager.PERMISSION_GRANTED }.toSet()
            },
            folderGrants = if (elevated) grants(installed.keys) else null,
            romRoot = DeviceStepExecutor.DEFAULT_ROM_ROOT,
            canReadAppData = elevated,
        )
        InventoryBuilder(bundle, Lister(access.access.takeIf { elevated })).build(facts)
    }

    private suspend fun grants(packages: Set<String>): Map<String, List<String>>? {
        val dump = (access.access.dumpsys("activity", "permissions") as? AccessResult.Ok)?.value ?: return null
        val byUid = UriGrants.parse(dump)
        return packages.associateWith { pkg ->
            val uid = runCatching { pm.getApplicationInfo(pkg, 0).uid }.getOrNull()
            uid?.let { byUid[it] }.orEmpty()
        }
    }

    /** Plain file reads, except Android/data, which only Shizuku can see. */
    private class Lister(private val elevated: Access?) : FileLister {
        private fun private(path: String) = "/Android/data/" in path || path.endsWith("/Android/data")
        override fun listFiles(folderPath: String): List<String> {
            val root = File(folderPath)
            return root.walkTopDown().maxDepth(4).filter { it.isFile }.map { it.relativeTo(root).path }.toList()
        }
        override fun exists(path: String): Boolean =
            if (private(path)) elevated?.let { a -> runBlocking { (a.exists(path) as? AccessResult.Ok)?.value } } == true
            else File(path).exists()
        override fun isDirectory(path: String) = File(path).isDirectory
    }
}
