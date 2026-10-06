package com.ramrom.emumanager.engine.inventory

import com.ramrom.emumanager.engine.Inventory
import com.ramrom.emumanager.knowledge.KnowledgeBundle

/** What the Android side read from the device; everything else comes from files via [FileLister]. */
data class DeviceFacts(
    /** Bundle apps that are installed, by package. */
    val installed: Map<String, InstalledApp> = emptyMap(),
    val allFilesAccess: Map<String, Boolean> = emptyMap(),
    /** Granted runtime permissions per package (only the ones the bundle lists need be present). */
    val grantedPermissions: Map<String, Set<String>> = emptyMap(),
    /** Absolute folder paths each package holds a tree grant for; null when they can't be read. */
    val folderGrants: Map<String, List<String>>? = null,
    val romRoot: String = "/storage/emulated/0/ROMs",
    val esdeRoot: String = "/storage/emulated/0/ES-DE",
    val appDataRoot: String = "/storage/emulated/0/Android/data",
    /** True when [FileLister] can see inside Android/data (elevated). */
    val canReadAppData: Boolean = false,
)

/**
 * P03: turns device facts and a file listing into an [Inventory]. Read-only and pure; no ROM
 * hashing, so a big library stays fast. Folder names come from the frontend profile (ES-DE's
 * `ps1`, not our system id), extensions from every emulator that runs the system.
 */
class InventoryBuilder(
    private val bundle: KnowledgeBundle,
    private val files: FileLister,
) {
    private val frontend = bundle.frontends.firstOrNull()
    private fun folderOf(systemId: String) = frontend?.systemFolders?.get(systemId) ?: systemId
    /** The system's folder first, then any aliases. */
    private fun foldersOf(systemId: String) = listOf(folderOf(systemId)) + frontend?.folderAliases?.get(systemId).orEmpty()

    /** system → extensions any of its emulators open. */
    private val extensions: Map<String, Set<String>> = bundle.systems.associate { s ->
        s.id to bundle.emulators.filter { s.id in it.systems }.flatMap { e -> e.romExtensions.map { it.lowercase() } }.toSet()
    }

    /**
     * extension → the one system it identifies. RetroArch opens most formats for many systems, so
     * emulator lists alone say little; the import map of cartridge and disc formats says more.
     */
    private val telltale: Map<String, String> = extensions.flatMap { (s, exts) -> exts.map { it to s } }
        .groupBy({ it.first }, { it.second }).filterValues { it.size == 1 }.mapValues { it.value.single() } +
        com.ramrom.emumanager.engine.importer.ImportClassifier.SURE.filterValues { s -> bundle.systems.any { it.id == s } }

    fun build(facts: DeviceFacts): Inventory {
        val romFolders = romFolders(facts.romRoot)
        val grants = facts.folderGrants?.let { grants(it, facts.romRoot) }.orEmpty()
        val bios = bios(facts)
        val esde = esde(facts.esdeRoot)
        return Inventory(
            installedPackages = facts.installed.keys,
            grantedFolderTrees = grants.flatMap { g -> if (g.systemId.isEmpty()) bundle.systems.map { it.id } else listOf(g.systemId) }.toSet(),
            existingFolders = romFolders.map { it.folderName }.toSet(),
            esdeConfigured = esde.installed,
            installedApps = facts.installed.values.sortedBy { it.packageId },
            romFolders = romFolders,
            folderGrants = grants,
            biosFiles = bios.first,
            appPermissions = permissions(facts),
            esdeConfig = esde,
            orphanedMedia = orphans(facts.esdeRoot, romFolders),
            grantsKnown = facts.folderGrants != null,
            biosUnchecked = bios.second,
        )
    }

    private fun romFolders(root: String): List<RomFolder> {
        if (!files.isDirectory(root)) return emptyList()
        return bundle.systems.flatMap { s -> foldersOf(s.id).map { s.id to it } }.distinctBy { it.second }.mapNotNull { (systemId, folder) ->
            val path = "$root/$folder"
            if (!files.isDirectory(path)) return@mapNotNull null
            val own = extensions[systemId].orEmpty()
            var games = 0
            val strays = mutableListOf<StrayFile>()
            val unplayable = mutableListOf<String>()
            val kept = mutableListOf<String>()
            for (f in files.listFiles(path)) {
                val name = f.substringAfterLast('/')
                if (name.startsWith('.') || isCompanion(name)) continue
                kept += f
                val ext = name.substringAfterLast('.', "").lowercase()
                when {
                    own.isEmpty() || ext in own -> games++
                    telltale[ext] != null && telltale[ext] != systemId -> strays += StrayFile(f, telltale.getValue(ext))
                    else -> unplayable += f
                }
            }
            RomFolder(systemId, folder, path, games, strays, unplayable, kept)
        }
    }

    private fun isCompanion(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in COMPANIONS || ext.startsWith("state")
    }

    private fun grants(byPackage: Map<String, List<String>>, root: String): List<FolderGrant> =
        byPackage.flatMap { (pkg, paths) ->
            paths.map { it.trimEnd('/') }.flatMap { path ->
                when {
                    path == root || root.startsWith("$path/") -> listOf(FolderGrant(pkg, ""))
                    path.startsWith("$root/") -> {
                        val folder = path.removePrefix("$root/").substringBefore('/')
                        bundle.systems.filter { folder in foldersOf(it.id) }.map { FolderGrant(pkg, it.id) }
                    }
                    else -> emptyList()
                }
            }
        }.distinct()

    /** Known BIOS present where each target emulator looks. Returns (found, systems we couldn't look at). */
    private fun bios(facts: DeviceFacts): Pair<List<BiosFile>, Set<String>> {
        val found = mutableListOf<BiosFile>()
        val unchecked = mutableSetOf<String>()
        for (entry in bundle.bios) for (t in entry.targets) {
            val emu = bundle.emulators.firstOrNull { it.id == t.emulatorId } ?: continue
            val dir = if ("{appData}" in t.path) {
                val pkg = emu.packages.firstOrNull { it in facts.installed } ?: continue
                if (!facts.canReadAppData) { unchecked += entry.system; continue }
                t.path.replace("{appData}", "${facts.appDataRoot}/$pkg")
            } else t.path
            val name = t.renameTo.ifEmpty { entry.fileName }
            val path = "${dir.trimEnd('/')}/$name"
            if (files.exists(path)) found += BiosFile(entry.system, emu.id, name, path)
        }
        return found.distinct() to (unchecked - found.map { it.systemId }.toSet())
    }

    private fun permissions(facts: DeviceFacts): List<AppPermissionState> = facts.installed.keys.map { pkg ->
        val emu = bundle.emulators.firstOrNull { pkg in it.packages }
        AppPermissionState(
            packageId = pkg,
            declaresManageExternalStorage = emu?.declaresAllFilesAccess ?: false,
            grantedManageExternalStorage = facts.allFilesAccess[pkg] ?: false,
            missingPermissions = emu?.permissions.orEmpty().filter { it !in facts.grantedPermissions[pkg].orEmpty() },
        )
    }

    private fun esde(root: String): EsdeConfig {
        val settings = "$root/settings/es_settings.xml"
        return EsdeConfig(
            installed = files.exists(settings),
            configPath = settings,
            mediaDirectory = "$root/downloaded_media",
            customSystemsFolder = "$root/custom_systems",
        )
    }

    private fun orphans(esdeRoot: String, romFolders: List<RomFolder>): List<OrphanedMedia> {
        val types = frontend?.mediaTypes?.map { it.folder }.orEmpty()
        return romFolders.flatMap { folder ->
            val mediaDir = "$esdeRoot/downloaded_media/${folder.folderName}"
            if (!files.isDirectory(mediaDir)) return@flatMap emptyList()
            val games = files.listFiles(folder.folderPath).map { it.substringAfterLast('/').substringBeforeLast('.') }.toSet()
            types.flatMap { type ->
                val dir = "$mediaDir/$type"
                if (!files.isDirectory(dir)) emptyList()
                else files.listFiles(dir).filter { it.substringAfterLast('/').substringBeforeLast('.') !in games }
                    .map { OrphanedMedia(folder.systemId, type, "$dir/$it") }
            }
        }
    }

    private companion object {
        val COMPANIONS = setOf("sav", "ml1", "srm", "sta", "dsv", "txt", "nfo", "jpg", "png", "m3u", "ramrom-part")
    }
}
