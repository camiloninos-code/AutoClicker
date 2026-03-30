package com.autoclicker.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*

class AutoClickService : AccessibilityService() {

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentState = AppConstants.STATE_STOPPED
    private lateinit var prefs: SharedPreferences

    // Screenshot capturado por takeScreenshot() API 30+
    private var lastScreenshot: Bitmap? = null

    private val widgetReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                AppConstants.ACTION_WIDGET_START -> {
                    val scriptName = prefs.getString(AppConstants.PREF_WIDGET_SCRIPT, null)
                    if (scriptName != null && currentState != AppConstants.STATE_RUNNING) {
                        val scripts = ScriptRepository.loadScripts(this@AutoClickService)
                        scripts.firstOrNull { it.name == scriptName }?.let { startScript(it) }
                    }
                }
                AppConstants.ACTION_WIDGET_STOP -> stopScript()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences("autoclicker_prefs", Context.MODE_PRIVATE)
        val filter = IntentFilter().apply {
            addAction(AppConstants.ACTION_WIDGET_START)
            addAction(AppConstants.ACTION_WIDGET_STOP)
        }
        registerReceiver(widgetReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopScript() }

    override fun onUnbind(intent: Intent?): Boolean {
        try { unregisterReceiver(widgetReceiver) } catch (_: Exception) {}
        return super.onUnbind(intent)
    }

    // ── Control ────────────────────────────────────────────────────────────────

    fun startScript(config: ScriptConfig) {
        job?.cancel()
        job = scope.launch {
            broadcastState(AppConstants.STATE_RUNNING)
            saveScriptForWidget(config.name)

            if (config.startDelayMs > 0) runCountdown((config.startDelayMs / 1000).toInt())

            val repeat = if (config.repeatCount == 0) Int.MAX_VALUE else config.repeatCount
            outer@ for (i in 0 until repeat) {
                if (!isActive) break
                for (point in config.points) {
                    if (!isActive) break@outer
                    val delay = if (point.delayMs > 0) point.delayMs else config.intervalMs
                    delay(delay)
                    performGesture(point)

                    // Condición de parada (v4)
                    if (config.stopCondition.enabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            requestScreenshot()
                            delay(100) // dar tiempo al callback
                        }
                        if (checkStopCondition(config.stopCondition)) break@outer
                    }
                }
            }
            broadcastState(AppConstants.STATE_STOPPED)
        }
    }

    fun stopScript() { job?.cancel(); broadcastState(AppConstants.STATE_STOPPED) }
    fun pauseScript() { job?.cancel(); broadcastState(AppConstants.STATE_PAUSED) }

    // ── Cuenta regresiva ───────────────────────────────────────────────────────

    private suspend fun runCountdown(seconds: Int) {
        for (i in seconds downTo 1) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                Intent(AppConstants.ACTION_COUNTDOWN).putExtra(AppConstants.EXTRA_COUNTDOWN, i)
            )
            delay(1000L)
        }
    }

    // ── Gesto (tap o swipe) ────────────────────────────────────────────────────

    private suspend fun performGesture(point: ClickPoint) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path().apply {
            moveTo(point.x.toFloat(), point.y.toFloat())
            if (point.isSwipe) lineTo(point.swipeToX.toFloat(), point.swipeToY.toFloat())
        }
        val duration = if (point.isSwipe) point.swipeDurationMs else point.durationMs
        val stroke = GestureDescription.StrokeDescription(path, 0L, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        suspendCancellableCoroutine<Unit> { cont ->
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(g: GestureDescription?) = cont.resume(Unit) {}
                override fun onCancelled(g: GestureDescription?) = cont.resume(Unit) {}
            }, null)
        }
    }

    // ── Screenshot (API 30+) ───────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestScreenshot() {
        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    lastScreenshot?.recycle()
                    lastScreenshot = Bitmap.wrapHardwareBuffer(
                        result.hardwareBuffer, result.colorSpace
                    )?.copy(Bitmap.Config.ARGB_8888, false)
                    result.hardwareBuffer.close()
                }
                override fun onFailure(errorCode: Int) {
                    lastScreenshot = null
                }
            }
        )
    }

    // ── Condición de parada ────────────────────────────────────────────────────

    private fun checkStopCondition(sc: StopCondition): Boolean {
        val bmp = lastScreenshot ?: return false
        return try {
            val pixel = bmp.getPixel(
                sc.pixelX.coerceIn(0, bmp.width - 1),
                sc.pixelY.coerceIn(0, bmp.height - 1)
            )
            val matches = colorMatches(pixel, sc.targetColor, sc.tolerance)
            if (sc.stopWhenMatches) matches else !matches
        } catch (_: Exception) { false }
    }

    private fun colorMatches(c1: Int, c2: Int, tol: Int): Boolean {
        val dr = Math.abs(android.graphics.Color.red(c1) - android.graphics.Color.red(c2))
        val dg = Math.abs(android.graphics.Color.green(c1) - android.graphics.Color.green(c2))
        val db = Math.abs(android.graphics.Color.blue(c1) - android.graphics.Color.blue(c2))
        return dr <= tol && dg <= tol && db <= tol
    }

    // ── Broadcast / Widget ─────────────────────────────────────────────────────

    private fun broadcastState(state: String) {
        currentState = state
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(AppConstants.ACTION_STATE_CHANGED).putExtra(AppConstants.EXTRA_STATE, state)
        )
        updateWidget()
    }

    private fun updateWidget() {
        val ids = AppWidgetManager.getInstance(this)
            .getAppWidgetIds(ComponentName(this, AutoClickerWidget::class.java))
        val clickerState = when (currentState) {
            AppConstants.STATE_RUNNING -> ClickerState.RUNNING
            AppConstants.STATE_PAUSED  -> ClickerState.PAUSED
            else                       -> ClickerState.IDLE
        }
        AutoClickerWidget.updateAll(this, AppWidgetManager.getInstance(this), ids, clickerState)
    }

    private fun saveScriptForWidget(name: String) =
        prefs.edit().putString(AppConstants.PREF_WIDGET_SCRIPT, name).apply()

    companion object { var instance: AutoClickService? = null }

    override fun onCreate() { super.onCreate(); instance = this }
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
        lastScreenshot?.recycle()
        lastScreenshot = null
    }
}
