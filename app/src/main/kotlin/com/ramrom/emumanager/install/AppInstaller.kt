package com.ramrom.emumanager.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import com.ramrom.emumanager.access.AccessManager
import com.ramrom.emumanager.access.AccessMode
import com.ramrom.emumanager.access.AccessResult
import com.ramrom.emumanager.engine.install.Releases
import com.ramrom.emumanager.engine.install.ResolvedApk
import com.ramrom.emumanager.knowledge.EmulatorProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

sealed interface InstallOutcome {
    data object Installed : InstallOutcome
    /** The user dismissed Android's install prompt; retry asks again. */
    data object Cancelled : InstallOutcome
    data class Failed(val reason: String, val retryable: Boolean) : InstallOutcome
}

/**
 * P06: resolve the APK from the app's release feed, download it to our cache, install it. Elevated
 * installs silently through Shizuku; limited mode goes through Android's installer, which asks the
 * user to confirm each app. Only apps and hosts named in knowledge data are ever fetched.
 */
class AppInstaller(private val context: Context, private val access: AccessManager) {
    private val cache = File(context.cacheDir, "apks").apply { mkdirs() }

    suspend fun resolve(app: EmulatorProfile): ResolvedApk? = withContext(Dispatchers.IO) {
        val url = Releases.releasesUrl(app.source) ?: return@withContext null
        // Projects that tag every build as a prerelease say so in their note.
        val allowPre = app.source.note.contains("prerelease", ignoreCase = true)
        Releases.pick(get(url), app.source.assetRegex, allowPre)
    }

    suspend fun download(apk: ResolvedApk, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        require(apk.url.startsWith("https://")) { "Refusing a non-https download" }
        val out = File(cache, apk.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        val conn = (URL(apk.url).openConnection() as HttpURLConnection).apply { instanceFollowRedirects = true; connectTimeout = 20_000; readTimeout = 60_000 }
        try {
            if (conn.responseCode != 200) error("Download failed (HTTP ${conn.responseCode})")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: apk.size
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(256 * 1024); var done = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        output.write(buf, 0, n); done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
            // The feed states the size; a short or padded file is not the file we were promised.
            if (apk.size > 0 && out.length() != apk.size) { out.delete(); error("Download was incomplete") }
            out
        } finally { conn.disconnect() }
    }

    suspend fun install(apk: File): InstallOutcome = try {
        if (access.mode.value == AccessMode.Elevated) {
            when (val r = access.access.installApk(apk.absolutePath)) {
                is AccessResult.Ok -> InstallOutcome.Installed
                is AccessResult.Denied -> installWithPrompt(apk)
                is AccessResult.Failed -> InstallOutcome.Failed(explain(r.message), retryable = true)
            }
        } else installWithPrompt(apk)
    } finally {
        apk.delete()
    }

    /** Limited mode: Android's own installer. The user taps Install; we wait for the result. */
    private suspend fun installWithPrompt(apk: File): InstallOutcome = withContext<InstallOutcome>(Dispatchers.Main) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            return@withContext InstallOutcome.Failed("Allow RamROM Emu Manager to install apps, then try again.", retryable = true)
        }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        val action = "${context.packageName}.INSTALL_RESULT.$sessionId"
        suspendCancellableCoroutine<InstallOutcome> { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            @Suppress("DEPRECATION")
                            (intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))?.let {
                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(it)
                            }
                        }
                        else -> {
                            runCatching { context.unregisterReceiver(this) }
                            val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                            if (cont.isActive) cont.resume(when (status) {
                                PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Installed
                                PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.Cancelled
                                else -> InstallOutcome.Failed(explain(msg), retryable = status != PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
                            })
                        }
                    }
                }
            }
            context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
            cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) }; runCatching { installer.abandonSession(sessionId) } }
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                val pi = PendingIntent.getBroadcast(context, sessionId, Intent(action).setPackage(context.packageName), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                session.commit(pi.intentSender)
            }
        }
    }

    /** pm and the installer speak in codes; say what they mean. */
    private fun explain(raw: String): String = when {
        "INSTALL_FAILED_DEPRECATED_SDK_VERSION" in raw -> "This version is too old for this Android. Android blocks it; we won't work around that."
        "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in raw -> "A different build of this app is installed (signed differently). Remove it first to switch."
        "INSTALL_FAILED_INSUFFICIENT_STORAGE" in raw -> "Not enough free space to install."
        "INSTALL_FAILED_VERSION_DOWNGRADE" in raw -> "A newer version is already installed."
        else -> raw.ifBlank { "Install failed" }
    }

    private fun get(url: String): String {
        require(url.startsWith("https://"))
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept", "application/json"); setRequestProperty("User-Agent", "RamROM-Emu-Manager"); connectTimeout = 20_000; readTimeout = 30_000
        }
        try {
            if (conn.responseCode != 200) error("Release list unavailable (HTTP ${conn.responseCode})")
            return conn.inputStream.bufferedReader().readText()
        } finally { conn.disconnect() }
    }
}
