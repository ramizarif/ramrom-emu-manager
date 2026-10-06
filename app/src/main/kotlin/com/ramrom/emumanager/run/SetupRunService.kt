package com.ramrom.emumanager.run

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * P19: keeps a run alive while the user is in another app (doing a guided step, say) or the screen
 * is off. The notification is the progress readout and the way back to S12.
 */
class SetupRunService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watch: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channel(this)
        startForeground(ID, notification("Getting your setup ready", null, 0f), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (watch == null) watch = scope.launch { follow() }
        return START_NOT_STICKY
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun follow() {
        val runs = (application as EmuApplication).runs
        val nm = getSystemService(NotificationManager::class.java)
        runs.active.flatMapLatest { it?.runner?.state ?: emptyFlow() }.collect { s ->
            val (title, text) = when {
                s.isComplete -> "Setup done" to "Open to see the health check"
                s.needsYouCount > 0 && s.runningCount == 0 -> "Your turn" to "${s.needsYouCount} thing${if (s.needsYouCount == 1) "" else "s"} need you"
                s.isPaused -> "Setup paused" to null
                else -> "Setting up" to "${s.doneCount} of ${s.stepProgress.size} done"
            }
            nm.notify(ID, notification(title, text, s.overallProgress))
            // Nothing left that moves on its own: the process can rest; the run file remembers.
            if (!s.isRunning) { stopForeground(STOP_FOREGROUND_DETACH); stopSelf() }
        }
    }

    private fun notification(title: String, text: String?, progress: Float): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, (progress * 100).toInt(), false)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        private const val ID = 7
        private const val CHANNEL = "setup"

        private fun channel(context: Context) = context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Setup progress", NotificationManager.IMPORTANCE_LOW))

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, SetupRunService::class.java)) }
        }

        fun stop(context: Context) { context.stopService(Intent(context, SetupRunService::class.java)) }
    }
}
