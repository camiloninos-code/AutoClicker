package com.autoclicker.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * CaptureOverlayService — Captura coordenadas de toque (clic o swipe).
 *
 * Modos (v3):
 *  - MODE_SINGLE: un toque → un clic → se detiene.
 *  - MODE_MULTI:  múltiples toques → un clic por toque → hasta que el usuario vuelva.
 *  - MODE_SWIPE:  primer toque = inicio, segundo toque = fin → un swipe → se detiene.
 */
class CaptureOverlayService : Service() {

    companion object {
        const val EXTRA_MODE = "capture_mode"
        const val MODE_SINGLE = "single"
        const val MODE_MULTI  = "multi"
        const val MODE_SWIPE  = "swipe"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private var mode = MODE_SINGLE

    // Para modo SWIPE
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var waitingForSwipeEnd = false
    private var markerView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_SINGLE
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(AppConstants.NOTIF_ID_CAPTURE, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        inflateOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeMarker()
        try { windowManager.removeView(overlayView) } catch (_: Exception) {}
    }

    private fun inflateOverlay() {
        overlayView = FrameLayout(this).apply {
            // Tinte verde muy sutil para feedback visual en modo normal
            setBackgroundColor(
                if (mode == MODE_SWIPE) 0x33_FF_80_00.toInt()  // naranja para swipe
                else 0x22_00_FF_00.toInt()
            )
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        overlayView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                handleTouch(event.rawX, event.rawY)
            }
            true
        }

        windowManager.addView(overlayView, params)
    }

    private fun handleTouch(x: Float, y: Float) {
        when (mode) {
            MODE_SINGLE -> {
                broadcastClick(x, y)
                stopSelf()
            }
            MODE_MULTI -> {
                broadcastClick(x, y)
                // Sigue activo para más toques
            }
            MODE_SWIPE -> {
                if (!waitingForSwipeEnd) {
                    // Primer toque: guardar inicio + mostrar marcador
                    swipeStartX = x
                    swipeStartY = y
                    waitingForSwipeEnd = true
                    showSwipeMarker(x, y)
                } else {
                    // Segundo toque: completar el swipe
                    removeMarker()
                    broadcastSwipe(swipeStartX, swipeStartY, x, y)
                    stopSelf()
                }
            }
        }
    }

    /**
     * Muestra un círculo naranja en el punto de inicio del swipe.
     */
    private fun showSwipeMarker(x: Float, y: Float) {
        val markerSize = 60
        val marker = object : View(this) {
            override fun onDraw(canvas: Canvas) {
                val cx = markerSize / 2f
                val cy = markerSize / 2f
                canvas.drawCircle(cx, cy, cx - 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(220, 255, 120, 0)
                    style = Paint.Style.FILL
                })
                canvas.drawCircle(cx, cy, cx - 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                })
                canvas.drawText("1", cx, cy + 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 22f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                })
            }
        }
        markerView = marker

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            markerSize, markerSize, overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = (x - markerSize / 2).toInt()
            this.y = (y - markerSize / 2).toInt()
        }

        windowManager.addView(marker, params)
    }

    private fun removeMarker() {
        markerView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            markerView = null
        }
    }

    private fun broadcastClick(x: Float, y: Float) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(AppConstants.ACTION_POINT_CAPTURED).apply {
                putExtra(AppConstants.EXTRA_POINT_X, x)
                putExtra(AppConstants.EXTRA_POINT_Y, y)
            }
        )
    }

    private fun broadcastSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(AppConstants.ACTION_SWIPE_CAPTURED).apply {
                putExtra(AppConstants.EXTRA_POINT_X,  x1)
                putExtra(AppConstants.EXTRA_POINT_Y,  y1)
                putExtra(AppConstants.EXTRA_SWIPE_X2, x2)
                putExtra(AppConstants.EXTRA_SWIPE_Y2, y2)
            }
        )
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, CaptureOverlayService::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )
        val text = when (mode) {
            MODE_SWIPE -> "Toca el punto INICIO del swipe (luego toca el FIN)"
            MODE_MULTI -> "Toca varios puntos. Vuelve a la app para terminar."
            else -> "Toca donde quieres agregar un clic"
        }
        return NotificationCompat.Builder(this, AppConstants.NOTIF_CHANNEL_ID)
            .setContentTitle("Modo captura activo")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_add)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", stopIntent)
            .setOngoing(true)
            .build()
    }
}
