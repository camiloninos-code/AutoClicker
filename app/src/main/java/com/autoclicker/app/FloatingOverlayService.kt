package com.autoclicker.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * FloatingOverlayService — Botón flotante Play/Pause/Stop.
 *
 * Flota sobre CUALQUIER app usando SYSTEM_ALERT_WINDOW.
 * El usuario puede arrastrar el botón a cualquier parte de la pantalla.
 *
 * Escucha cambios de estado desde AutoClickService y actualiza su UI.
 */
class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams

    private var currentState: ClickerState = ClickerState.IDLE
    private var currentScript: ScriptConfig? = null
    private var cycleCount = 0

    // Para arrastre del overlay
    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f

    // ─── Broadcast receiver ───────────────────────────────────────────────────

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AppConstants.ACTION_STATE_CHANGED -> {
                    val stateName = intent.getStringExtra(AppConstants.EXTRA_STATE) ?: return
                    currentState = ClickerState.valueOf(stateName)
                    updateOverlayUI()
                }
                AppConstants.ACTION_CYCLE_UPDATE -> {
                    cycleCount = intent.getIntExtra(AppConstants.EXTRA_CYCLE, 0)
                    updateCycleCounter()
                }
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(AppConstants.NOTIF_ID_OVERLAY, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        inflateOverlay()
        registerStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val script = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getSerializableExtra(AppConstants.EXTRA_SCRIPT, ScriptConfig::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getSerializableExtra(AppConstants.EXTRA_SCRIPT) as? ScriptConfig
        }
        if (script != null) currentScript = script
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            try { windowManager.removeView(floatingView) } catch (_: Exception) {}
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(stateReceiver)
    }

    // ─── Overlay ──────────────────────────────────────────────────────────────

    private fun inflateOverlay() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_floating, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 200
        }

        windowManager.addView(floatingView, params)
        setupClickListeners()
        setupDrag()
        updateOverlayUI()
    }

    private fun setupClickListeners() {
        floatingView.findViewById<ImageButton>(R.id.btnPlayPause)?.setOnClickListener {
            when (currentState) {
                ClickerState.IDLE    -> sendCommand(AppConstants.ACTION_START)
                ClickerState.RUNNING -> sendCommand(AppConstants.ACTION_PAUSE)
                ClickerState.PAUSED  -> sendCommand(AppConstants.ACTION_RESUME)
            }
        }
        floatingView.findViewById<ImageButton>(R.id.btnStop)?.setOnClickListener {
            sendCommand(AppConstants.ACTION_STOP)
        }
    }

    private fun setupDrag() {
        val dragHandle = floatingView.findViewById<View>(R.id.dragHandle) ?: floatingView
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun updateOverlayUI() {
        val btnPlayPause = floatingView.findViewById<ImageButton>(R.id.btnPlayPause) ?: return
        val btnStop = floatingView.findViewById<ImageButton>(R.id.btnStop) ?: return

        when (currentState) {
            ClickerState.IDLE -> {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                btnStop.visibility = View.GONE
                cycleCount = 0
                updateCycleCounter()
            }
            ClickerState.RUNNING -> {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                btnStop.visibility = View.VISIBLE
            }
            ClickerState.PAUSED -> {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                btnStop.visibility = View.VISIBLE
            }
        }
    }

    private fun updateCycleCounter() {
        val tvCycles = floatingView.findViewById<TextView>(R.id.tvCycleCount) ?: return
        val script = currentScript
        val total = if (script?.isInfinite == true) "∞" else script?.cycles?.toString() ?: "0"
        tvCycles.text = "$cycleCount/$total"
        tvCycles.visibility = if (currentState == ClickerState.IDLE) View.GONE else View.VISIBLE
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun sendCommand(action: String) {
        val intent = Intent(action)
        if (action == AppConstants.ACTION_START && currentScript != null) {
            intent.putExtra(AppConstants.EXTRA_SCRIPT, currentScript)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun registerStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(AppConstants.ACTION_STATE_CHANGED)
            addAction(AppConstants.ACTION_CYCLE_UPDATE)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(stateReceiver, filter)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConstants.NOTIF_CHANNEL_ID,
                "Auto Clicker",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Control del auto clicker" }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AppConstants.NOTIF_CHANNEL_ID)
            .setContentTitle("Auto Clicker activo")
            .setContentText("Overlay flotante en ejecución")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }
}
