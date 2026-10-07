package com.ramrom.emumanager.run

import android.os.Environment
import com.ramrom.emumanager.EmuApplication
import com.ramrom.emumanager.access.AccessResult
import com.ramrom.emumanager.data.ConfigChange
import com.ramrom.emumanager.data.ConfigChangeReason
import com.ramrom.emumanager.data.ConfigChangeTarget
import com.ramrom.emumanager.engine.config.ChangeSet
import com.ramrom.emumanager.engine.config.ConfigCodec
import com.ramrom.emumanager.engine.config.ConfigTransaction
import com.ramrom.emumanager.engine.config.DolphinIniCodec
import com.ramrom.emumanager.engine.config.EsdeLaunch
import com.ramrom.emumanager.engine.config.FileStore
import com.ramrom.emumanager.engine.config.QtIniCodec
import com.ramrom.emumanager.engine.config.SettingsProfiles
import com.ramrom.emumanager.knowledge.ConfigFormat
import com.ramrom.emumanager.knowledge.EmulatorProfile
import com.ramrom.emumanager.knowledge.GameSetting
import com.ramrom.emumanager.knowledge.GameSettingsProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One setting on S21: its name, what it is now and what it becomes, in the emulator's own words. */
data class SettingRow(val name: String, val before: String, val after: String)

/** One file S21 will write. [running] is null when this access mode can't tell. */
data class FilePlan(
    val pkg: String,
    val appName: String,
    val path: String,
    val existed: Boolean,
    val rows: List<SettingRow>,
    val alreadySet: Int,
    val changes: ChangeSet,
    val running: Boolean?,
)

/**
 * Everything S21 shows before a write. [blocked] means nothing can be written here (limited mode
 * and a file in Android/data); [profile]'s settings are then shown for the user to set by hand.
 */
data class ApplyPlan(
    val title: String,
    val gameId: String?,
    val emulator: EmulatorProfile,
    val profile: GameSettingsProfile,
    val files: List<FilePlan>,
    val blocked: String?,
) {
    val nothingToDo get() = blocked == null && files.all { it.rows.isEmpty() }
    /** Apps that will be closed first; UX-RULES §3.4 says so before it happens. */
    val closes get() = files.filter { it.rows.isNotEmpty() && it.running == true }.map { it.appName }.distinct()
}

sealed interface UndoOutcome {
    data object Restored : UndoOutcome
    /** The file changed after our write; undoing now would throw that change away. */
    data object ChangedSince : UndoOutcome
    data class Failed(val reason: String) : UndoOutcome
}

/**
 * P09 on the device: preview (S21), close the owning app, write through a snapshot, record history
 * (S22) and undo with the 3-way check. Files in Android/data go through elevated access; shared
 * storage is written directly.
 */
class ConfigWriter(private val app: EmuApplication) {
    private val historyDir = File(app.filesDir, "config-history").also { it.mkdirs() }

    /** The plan S21 is showing; set by whoever opens it. */
    val pending = MutableStateFlow<ApplyPlan?>(null)

    fun history(): Flow<List<ConfigChange>> = app.db.configChangeDao().getAllChanges()

    /** Installed builds of [emu] (Eden stable and nightly are separate apps with separate files). */
    fun installed(emu: EmulatorProfile) = emu.packages.filter { pkg ->
        runCatching { app.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    /**
     * [launch] is the build ES-DE opens the game in; a game's settings go to that build only. Device
     * defaults ([launch] null) go to every installed build.
     */
    suspend fun plan(emu: EmulatorProfile, profile: GameSettingsProfile, title: String, gameId: String?, launch: EsdeLaunch.Pick? = null): ApplyPlan = withContext(Dispatchers.IO) {
        val write = SettingsProfiles.write(emu, profile)
        val pkgs = when (launch) {
            null -> installed(emu)
            is EsdeLaunch.Pick.Package -> listOf(launch.pkg)
            else -> emptyList()
        }
        val blocked = when {
            write == null -> "${emu.name} keeps these settings somewhere RamROM can't write."
            launch is EsdeLaunch.Pick.OtherEmulator -> "ES-DE opens this game in ${launch.name}, so ${emu.name} settings wouldn't be used. Choose ${emu.name} for it in ES-DE first, or keep ${launch.name}."
            pkgs.isEmpty() -> "Install ${emu.name} first."
            "{appData}" in write.filePath && !app.access.capabilities.value.contains(com.ramrom.emumanager.access.Capability.WRITE_APP_DATA) ->
                "${emu.name}'s settings are in a protected folder. With elevated access RamROM sets them for you; for now, set them in ${emu.name}."
            else -> null
        }
        if (blocked != null || write == null) return@withContext ApplyPlan(title, gameId, emu, profile, emptyList(), blocked)
        // A shared-storage config (Azahar) is one file whichever build is installed.
        val targets = if ("{appData}" in write.filePath) pkgs else pkgs.take(1)
        val files = targets.map { pkg ->
            val path = resolve(write.filePath, pkg)
            val store = AccessStore()
            val result = ConfigTransaction(codecFor(emu), store, LocalStore()).preview(path, write.changes)
            val perGame = profile.gameKey.titleId.isNotEmpty()
            val current = codecFor(emu).parse(result.originalText).values
            // Changed keys, by setting: a per-game override can change only its marker lines.
            val touched = result.changes.map { it.first.substringBefore('\\') }.toSet()
            val rows = profile.settings.filter { it.key in touched }.map { s ->
                val inEffect = when {
                    perGame && current["${s.key}\\use_global"] != "false" -> null
                    emu.marksNonDefault && current["${s.key}\\default"] == "true" -> null
                    else -> current[s.key]
                }
                SettingRow(nameOf(s), describe(emu, s.key, inEffect, perGame, result.originalText.isNotEmpty()), valueOf(s))
            }
            FilePlan(pkg, appName(pkg, emu), path, store.exists(path), rows, profile.settings.size - rows.size, write.changes,
                (app.access.access.isRunning(pkg) as? AccessResult.Ok)?.value)
        }
        ApplyPlan(title, gameId, emu, profile, files, null)
    }

    /** Close what has to close, then write each file. Returns the first failure, or null. */
    suspend fun apply(plan: ApplyPlan): String? = withContext(Dispatchers.IO) {
        for (f in plan.files.filter { it.rows.isNotEmpty() }) {
            if (f.running != false) app.access.access.forceStop(f.pkg).let { r ->
                if (f.running == true && r !is AccessResult.Ok) return@withContext "Couldn't close ${f.appName}. Close it and try again."
            }
            val id = UUID.randomUUID().toString()
            val before = File(historyDir, "$id.before"); val after = File(historyDir, "$id.after")
            val store = AccessStore()
            val existed = store.exists(f.path)
            val result = runCatching { ConfigTransaction(codecFor(plan.emulator), store, LocalStore()).execute(f.path, f.changes, before.path) }
                .getOrElse { e -> app.log.e("config", "write ${f.path}", e); return@withContext "Couldn't write ${f.appName}'s settings: ${e.message}" }
            after.writeText(result.newText)
            // History counts settings, not lines: a per-game override can change only its markers.
            val codec = codecFor(plan.emulator)
            val was = codec.parse(result.originalText).values; val now = codec.parse(result.newText).values
            val touched = result.changes.map { it.first.substringBefore('\\') }.distinct()
            app.db.configChangeDao().insert(ConfigChange(
                id = id, at = System.currentTimeMillis(), target = ConfigChangeTarget.FILE, reason = ConfigChangeReason.PROFILE,
                gameId = plan.gameId,
                keysBefore = JSONObject(touched.associateWith { was[it] ?: JSONObject.NULL }).toString(),
                keysAfter = JSONObject(touched.associateWith { now[it] ?: JSONObject.NULL }).toString(),
                snapshotPath = before.path, filePath = f.path, pkg = f.pkg, title = "${plan.title} · ${f.appName}",
                existedBefore = existed, writtenPath = after.path,
            ))
            app.log.i("config", "wrote ${f.path}: ${touched.joinToString { "$it=${now[it]}" }}")
        }
        null
    }

    /**
     * A whole-file edit for formats without a key codec (an ES-DE gamelist): closes [pkg] if it's
     * open (ES-DE writes gamelists on exit), snapshots, writes, records history. Null on success.
     */
    suspend fun writeText(pkg: String, appName: String, path: String, title: String, keys: Map<String, String>, edit: (String) -> String): String? = withContext(Dispatchers.IO) {
        if ((app.access.access.isRunning(pkg) as? AccessResult.Ok)?.value != false) app.access.access.forceStop(pkg)
        val store = AccessStore()
        val existed = store.exists(path)
        val before = store.read(path).orEmpty()
        val after = edit(before)
        if (after == before) return@withContext null
        val id = UUID.randomUUID().toString()
        val beforeFile = File(historyDir, "$id.before"); val afterFile = File(historyDir, "$id.after")
        if (existed) beforeFile.writeText(before)
        runCatching { store.write(path, after) }.onFailure { e -> app.log.e("config", "write $path", e); return@withContext "Couldn't write $appName's settings: ${e.message}" }
        if (store.read(path) != after) return@withContext "Couldn't write $appName's settings: the file didn't read back the same."
        afterFile.writeText(after)
        app.db.configChangeDao().insert(ConfigChange(
            id = id, at = System.currentTimeMillis(), target = ConfigChangeTarget.FILE, reason = ConfigChangeReason.PROFILE,
            keysAfter = JSONObject(keys).toString(), snapshotPath = beforeFile.path, filePath = path, pkg = pkg,
            title = "$title · $appName", existedBefore = existed, writtenPath = afterFile.path,
        ))
        app.log.i("config", "wrote $path: $keys")
        null
    }

    /**
     * Tell ES-DE to open [systemId] in [emu] (S12's frontend step and S15's fix). Null on success,
     * including when its choice already opens [emu] or its default does.
     */
    suspend fun setEsdeChoice(systemId: String, emu: EmulatorProfile): String? {
        val bundle = app.knowledge.getBundle() ?: return null
        val fe = bundle.esde ?: return null
        val pkg = fe.packages.firstOrNull { p -> runCatching { app.packageManager.getPackageInfo(p, 0) }.isSuccess } ?: return "Install ES-DE first."
        val dir = fe.paths.gamelists.ifEmpty { return null }
        val customs = withContext(Dispatchers.IO) {
            runCatching { File(fe.paths.customSystems, "es_systems.xml").takeIf { it.exists() }?.readText() }.getOrNull()
        }?.let { EsdeLaunch.customDefaults(it) }.orEmpty()
        val folder = EsdeLaunch.folderFor(fe, systemId, customs.keys)
        val custom = customs[folder]
        val label = EsdeLaunch.wanted(fe, systemId, emu, installed(emu).firstOrNull(), custom) ?: return null
        val path = "$dir/$folder/gamelist.xml"
        val current = withContext(Dispatchers.IO) { runCatching { File(path).readText() }.getOrDefault("") }
        if (EsdeLaunch.opens(EsdeLaunch.systemLabel(current), emu, fe)) return null
        val system = bundle.systems.firstOrNull { it.id == systemId }?.name ?: systemId
        return writeText(pkg, "ES-DE", path, "$system opens in ${emu.name}", mapOf("alternativeEmulator" to label)) { EsdeLaunch.withSystemLabel(it, label) }
    }

    /** Put the file back as it was. Unless [force], refuses when the file changed since our write. */
    suspend fun undo(change: ConfigChange, force: Boolean = false): UndoOutcome = withContext(Dispatchers.IO) {
        // Undo restores whole files; the codec only matters for emulators' INI files.
        val emu = app.knowledge.getBundle()?.emulators?.firstOrNull { change.pkg in it.packages }
        val written = runCatching { File(change.writtenPath).readText() }.getOrNull()
            ?: return@withContext UndoOutcome.Failed("The copy needed to undo this is gone.")
        // The app could save over the restored file on exit, so it closes first, as for a write.
        if ((app.access.access.isRunning(change.pkg) as? AccessResult.Ok)?.value != false) app.access.access.forceStop(change.pkg)
        val tx = ConfigTransaction(emu?.let(::codecFor) ?: QtIniCodec(), AccessStore(), LocalStore())
        val r = runCatching { tx.undoChecked(change.filePath, change.snapshotPath, written, change.existedBefore, force) }
            .getOrElse { e -> app.log.e("config", "undo ${change.filePath}", e); return@withContext UndoOutcome.Failed(e.message ?: "Undo failed") }
        when (r) {
            ConfigTransaction.UndoResult.Restored -> {
                app.db.configChangeDao().update(change.copy(revertedAt = System.currentTimeMillis()))
                File(change.writtenPath).delete()
                UndoOutcome.Restored
            }
            is ConfigTransaction.UndoResult.Changed -> UndoOutcome.ChangedSince
            ConfigTransaction.UndoResult.NoSnapshot -> UndoOutcome.Failed("The copy needed to undo this is gone.")
        }
    }

    /** The latest change still in effect for [gameId], for S19's "Undo last change". */
    suspend fun lastFor(gameId: String) = app.db.configChangeDao().getChangesByGameId(gameId).first().firstOrNull { it.revertedAt == null }

    private fun resolve(path: String, pkg: String) =
        path.replace("{appData}", "${Environment.getExternalStorageDirectory()}/Android/data/$pkg")

    private fun appName(pkg: String, emu: EmulatorProfile) =
        if (emu.packages.size > 1 && pkg.endsWith(".nightly")) "${emu.name} Nightly" else emu.name

    private fun codecFor(emu: EmulatorProfile): ConfigCodec = when (emu.configFormat) {
        ConfigFormat.DOLPHIN_INI -> DolphinIniCodec()
        else -> QtIniCodec()
    }

    /** "Resolution: 1X (720p/1080p)" → name and value; labels without a colon are the name. */
    private fun nameOf(s: GameSetting) = s.label.substringBefore(": ", s.label)
    private fun valueOf(s: GameSetting) = if (": " in s.label) s.label.substringAfter(": ") else s.value

    /** The current value in the same words as the new one, where any profile names it. */
    private fun describe(emu: EmulatorProfile, key: String, raw: String?, perGame: Boolean, fileExists: Boolean): String {
        if (raw == null || raw.isEmpty()) return if (perGame) "${emu.name}'s setting" else "${emu.name}'s default"
        val named = app.knowledge.getBundle()?.settingsProfiles.orEmpty()
            .filter { it.emulatorId == emu.id }.flatMap { it.settings }
            .firstOrNull { it.key == key && it.value.equals(raw, ignoreCase = true) && ": " in it.label }
        return named?.let(::valueOf) ?: raw
    }

    /** [FileStore] over the current access mode. Blocking; only called from Dispatchers.IO. */
    private inner class AccessStore : FileStore {
        override fun read(path: String) = runBlocking { (app.access.access.readFile(path) as? AccessResult.Ok)?.value?.toString(Charsets.UTF_8) }
        override fun write(path: String, text: String) = runBlocking {
            when (val r = app.access.access.writeFile(path, text.toByteArray())) {
                is AccessResult.Ok -> Unit
                is AccessResult.Denied -> error(r.reason)
                is AccessResult.Failed -> error(r.message)
            }
        }
        override fun exists(path: String) = runBlocking { (app.access.access.exists(path) as? AccessResult.Ok)?.value == true }
        override fun delete(path: String) { runBlocking { app.access.access.deleteFile(path) } }
    }

    /** Snapshots live in our own files dir. */
    private class LocalStore : FileStore {
        override fun read(path: String) = File(path).takeIf { it.exists() }?.readText()
        override fun write(path: String, text: String) { File(path).apply { parentFile?.mkdirs() }.writeText(text) }
        override fun exists(path: String) = File(path).exists()
        override fun delete(path: String) { File(path).delete() }
    }
}
