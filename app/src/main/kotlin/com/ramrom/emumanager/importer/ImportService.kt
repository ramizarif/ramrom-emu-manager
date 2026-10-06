package com.ramrom.emumanager.importer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.ramrom.emumanager.EmuApplication
import com.ramrom.emumanager.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/** Keeps an import alive with the screen off or the user elsewhere; the notification opens S24. */
class ImportService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watch: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channel(this)
        startForeground(ID, notification("Importing games", null, 0f, true), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (watch == null) watch = scope.launch { follow() }
        return START_NOT_STICKY
    }

    private suspend fun follow() {
        val nm = getSystemService(NotificationManager::class.java)
        (application as EmuApplication).imports.job.filterNotNull().collect { j ->
            if (j.finished) {
                val done = j.count(JobItem.State.Done)
                val failed = j.count(JobItem.State.Failed)
                stopForeground(STOP_FOREGROUND_DETACH)
                nm.notify(ID, notification(if (j.cancelled) "Import stopped" else "Import done",
                    listOfNotNull("$done game${if (done == 1) "" else "s"} added", if (failed > 0) "$failed didn't work" else null).joinToString("  ·  "), 1f, false))
                stopSelf()
            } else {
                val at = j.items.count { it.state != JobItem.State.Waiting && it.state != JobItem.State.Working } + 1
                nm.notify(ID, notification("Importing games", "$at of ${j.items.size}  ·  ${j.items.firstOrNull { it.state == JobItem.State.Working }?.title.orEmpty()}", j.progress, true))
            }
        }
    }

    private fun notification(title: String, text: String?, progress: Float, ongoing: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_IMPORT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(if (ongoing) android.R.drawable.stat_sys_download else android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .apply { if (ongoing) setProgress(100, (progress * 100).toInt(), false) }
            .setOnlyAlertOnce(true)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        private const val ID = 8
        private const val CHANNEL = "import"

        private fun channel(context: Context) = context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Import progress", NotificationManager.IMPORTANCE_LOW))

        fun start(context: Context) { runCatching { context.startForegroundService(Intent(context, ImportService::class.java)) } }
    }
}
