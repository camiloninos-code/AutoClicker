package com.autoclicker.app

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlin.math.hypot

/**
 * v4 — Grabador en tiempo real.
 * Detecta taps (≥0ms) y swipes (distancia ≥ MIN_SWIPE_PX entre inicio y fin).
 * Toque largo (≥LONG_PRESS_MS) queda guardado con su duración real.
 * Envía ACTION_RECORD_POINT o ACTION_SWIPE_CAPTURED vía LocalBroadcast.
 */
class RecorderOverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "recorder_channel"
        private const val NOTIF_ID = 5
        private const val MIN_SWIPE_PX = 60f   // distancia mínima para considerar swipe
        private const val LONG_PRESS_MS = 500L  // duración mínima para long press
    }

    private lateinit var wm: WindowManager
    private lateinit var overlayView: View
    private lateinit var tvCount: TextView
    private var pointCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        setupOverlay()
    }

    private fun setupOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_recorder, null)
        tvCount = overlayView.findViewById(R.id.tvRecorderCount)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        var downTime = 0L
        var downX = 0f; var downY = 0f
        var maxDist = 0f
        var lastX = 0f; var lastY = 0f

        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    downX = event.rawX; downY = event.rawY
                    lastX = downX; lastY = downY
                    maxDist = 0f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lastX = event.rawX; lastY = event.rawY
                    val dist = hypot(lastX - downX, lastY - downY)
                    if (dist > maxDist) maxDist = dist
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - downTime
                    if (maxDist >= MIN_SWIPE_PX) {
                        // Es un swipe
                        broadcastSwipe(downX.toInt(), downY.toInt(),
                                       lastX.toInt(), lastY.toInt())
                    } else {
                        // Es tap (corto o long press según duración)
                        broadcastTap(downX.toInt(), downY.toInt(), duration)
                    }
                    true
                }
                else -> false
            }
        }

        overlayView.findViewById<View>(R.id.btnRecorderStop).setOnClickListener {
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(Intent(AppConstants.ACTION_RECORDER_STOP))
            stopSelf()
        }

        wm.addView(overlayView, params)
    }

    private fun broadcastTap(x: Int, y: Int, durationMs: Long) {
        pointCount++
        updateCount()
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(AppConstants.ACTION_RECORD_POINT).apply {
                putExtra(AppConstants.EXTRA_X, x)
                putExtra(AppConstants.EXTRA_Y, y)
                putExtra(AppConstants.EXTRA_DURATION_MS, durationMs)
            }
        )
    }

    private fun broadcastSwipe(x1: Int, y1: Int, x2: Int, y2: Int) {
        pointCount++
        updateCount()
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(AppConstants.ACTION_SWIPE_CAPTURED).apply {
                putExtra(AppConstants.EXTRA_X, x1)
                putExtra(AppConstants.EXTRA_Y, y1)
                putExtra(AppConstants.EXTRA_SWIPE_X2, x2)
                putExtra(AppConstants.EXTRA_SWIPE_Y2, y2)
            }
        )
    }

    private fun updateCount() {
        val taps   = "puntos grabados"
        tvCount.text = "⏺ $pointCount $taps"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Grabador", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recorder_notification_title))
            .setContentText(getString(R.string.recorder_notification_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true).build()

    override fun onDestroy() {
        super.onDestroy()
        try { wm.removeView(overlayView) } catch (_: Exception) {}
    }
}
