package com.ramrom.emumanager.run

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.ramrom.emumanager.MainActivity
import kotlin.math.abs

/**
 * S13's floating step chip (UX-RULES §2.4): while the user is in another app doing a guided step,
 * a small card over it shows the step, the exact value and "Back to setup". Draggable; tapping the
 * title collapses it to a handle. It never takes focus, so the other app keeps the controller.
 * Without "display over other apps" a notification carries the same content instead.
 */
object StepChip {
    private const val NOTIFICATION_ID = 9
    private const val CHANNEL = "step"
    private var view: View? = null

    fun canOverlay(context: Context) = Settings.canDrawOverlays(context)

    fun show(context: Context, title: String, value: String?, valueLabel: String) {
        hide(context)
        val app = context.applicationContext
        if (canOverlay(app)) runCatching { overlay(app, title, value, valueLabel) }.onFailure { notify(app, title, value) }
        else notify(app, title, value)
    }

    fun hide(context: Context) {
        val app = context.applicationContext
        view?.let { v -> runCatching { app.getSystemService(WindowManager::class.java).removeView(v) } }
        view = null
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun backIntent(context: Context) = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /**
     * Bring our task forward. The chip's visible window is what lets a background app do this, so
     * it stays up until the switch has happened.
     */
    private fun back(context: Context) {
        val moved = runCatching { context.getSystemService(android.app.ActivityManager::class.java).appTasks.firstOrNull()?.moveToFront() != null }.getOrDefault(false)
        if (!moved) context.startActivity(backIntent(context))
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ hide(context) }, 400)
    }

    private fun notify(context: Context, title: String, value: String?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Setup steps", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(context, 2, backIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(NOTIFICATION_ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(value?.let { "Use: $it  ·  tap to go back to setup" } ?: "Tap to go back to setup")
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build())
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun overlay(context: Context, title: String, value: String?, valueLabel: String) {
        val wm = context.getSystemService(WindowManager::class.java)
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()
        fun round(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
        val ink = 0xFF171B2F.toInt(); val mint = 0xFF16C08A.toInt(); val soft = 0xFFB8BED3.toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Wherever the user last dragged it: one move lasts the whole setup, not one step.
            val at = context.getSharedPreferences("step_chip", Context.MODE_PRIVATE)
            gravity = Gravity.TOP or Gravity.START; x = at.getInt("x", dp(16)); y = at.getInt("y", dp(72))
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = round(ink, 16)
            setPadding(dp(14), dp(10), dp(14), dp(12))
            elevation = dp(8).toFloat()
        }
        val head = TextView(context).apply {
            text = "▾  $title"; setTextColor(0xFFFFFFFF.toInt()); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            maxWidth = dp(300)
        }
        card.addView(head)
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (value != null) {
            body.addView(TextView(context).apply { text = valueLabel.ifEmpty { "Use" }; setTextColor(soft); textSize = 11f; setPadding(0, dp(6), 0, dp(2)) })
            body.addView(TextView(context).apply {
                text = value; setTextColor(0xFFFFFFFF.toInt()); textSize = 17f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                background = round(0x22FFFFFF, 8); setPadding(dp(10), dp(6), dp(10), dp(6)); maxWidth = dp(300)
            })
        }
        body.addView(TextView(context).apply { text = "Drag me if I'm in the way"; setTextColor(soft); textSize = 10f; setPadding(0, dp(8), 0, 0) })
        body.addView(TextView(context).apply {
            text = "Back to setup"; setTextColor(ink); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; background = round(mint, 12); setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
            setOnClickListener { back(context) }
        })
        card.addView(body)

        // Drag anywhere on the card; a tap without movement on the title collapses or expands it.
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        val drag = View.OnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) { params.x = startX + dx.toInt(); params.y = startY + dy.toInt(); runCatching { wm.updateViewLayout(card, params) } }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) context.getSharedPreferences("step_chip", Context.MODE_PRIVATE).edit().putInt("x", params.x).putInt("y", params.y).apply()
                    // The title toggles; when it's folded, the whole chip is the title.
                    if (!dragging && (v === head || body.visibility != View.VISIBLE)) {
                        val open = body.visibility == View.VISIBLE
                        body.visibility = if (open) View.GONE else View.VISIBLE
                        head.text = (if (open) "▸  " else "▾  ") + title.let { if (open) it.take(18) + if (it.length > 18) "…" else "" else it }
                        // Re-measure, and keep the whole card on screen when it opens near an edge.
                        card.post {
                            val screen = context.resources.displayMetrics
                            params.y = params.y.coerceIn(0, maxOf(0, screen.heightPixels - card.height - dp(8)))
                            params.x = params.x.coerceIn(0, maxOf(0, screen.widthPixels - card.width - dp(8)))
                            runCatching { wm.updateViewLayout(card, params) }
                        }
                    }
                    true
                }
                else -> false
            }
        }
        head.setOnTouchListener(drag)
        card.setOnTouchListener(drag)
        wm.addView(card, params)
        view = card
    }
}
