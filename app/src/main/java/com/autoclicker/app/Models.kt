package com.autoclicker.app

import java.io.Serializable

enum class ClickerState { IDLE, RUNNING, PAUSED }

data class ClickPoint(
    val x: Int,
    val y: Int,
    val delayMs: Long = 0L,
    val durationMs: Long = 50L,
    val label: String = "",
    val swipeToX: Int = -1,
    val swipeToY: Int = -1,
    val swipeDurationMs: Long = 300L
) : Serializable {
    val isSwipe: Boolean get() = swipeToX >= 0 && swipeToY >= 0
}

data class StopCondition(
    val enabled: Boolean = false,
    val pixelX: Int = 0,
    val pixelY: Int = 0,
    val targetColor: Int = 0,
    val tolerance: Int = 15,
    val stopWhenMatches: Boolean = true
) : Serializable

data class ScriptConfig(
    val name: String = "Script",
    val points: List<ClickPoint> = emptyList(),
    val repeatCount: Int = 1,
    val intervalMs: Long = 1000L,
    val startDelayMs: Long = 0L,
    val stopCondition: StopCondition = StopCondition()
) : Serializable {
    val isInfinite: Boolean get() = repeatCount <= 0
    val cycles: Int get() = repeatCount
}

object AppConstants {
    const val ACTION_STATE_CHANGED   = "com.autoclicker.app.STATE_CHANGED"
    const val ACTION_CLICK_CAPTURED  = "com.autoclicker.app.CLICK_CAPTURED"
    const val ACTION_SWIPE_CAPTURED  = "com.autoclicker.app.SWIPE_CAPTURED"
    const val ACTION_COUNTDOWN       = "com.autoclicker.app.COUNTDOWN"
    const val ACTION_RECORD_POINT    = "com.autoclicker.app.RECORD_POINT"
    const val ACTION_CYCLE_UPDATE    = "com.autoclicker.app.CYCLE_UPDATE"
    const val ACTION_START           = "com.autoclicker.app.START"
    const val ACTION_PAUSE           = "com.autoclicker.app.PAUSE"
    const val ACTION_RESUME          = "com.autoclicker.app.RESUME"
    const val ACTION_STOP            = "com.autoclicker.app.STOP"
    const val ACTION_POINT_CAPTURED  = "com.autoclicker.app.POINT_CAPTURED"

    const val EXTRA_STATE        = "state"
    const val EXTRA_X            = "x"
    const val EXTRA_Y            = "y"
    const val EXTRA_SWIPE_X2     = "swipe_x2"
    const val EXTRA_SWIPE_Y2     = "swipe_y2"
    const val EXTRA_COUNTDOWN    = "countdown"
    const val EXTRA_DURATION_MS  = "duration_ms"
    const val EXTRA_CYCLE        = "cycle"
    const val EXTRA_SCRIPT       = "script"
    const val EXTRA_POINT_X      = "point_x"
    const val EXTRA_POINT_Y      = "point_y"

    const val ACTION_WIDGET_START  = "com.autoclicker.app.WIDGET_START"
    const val ACTION_WIDGET_STOP   = "com.autoclicker.app.WIDGET_STOP"
    const val ACTION_RECORDER_STOP = "com.autoclicker.app.RECORDER_STOP"
    const val PREF_WIDGET_SCRIPT   = "widget_last_script"

    const val STATE_RUNNING = "RUNNING"
    const val STATE_PAUSED  = "PAUSED"
    const val STATE_STOPPED = "STOPPED"

    const val MODE_SINGLE = "single"
    const val MODE_MULTI  = "multi"
    const val MODE_SWIPE  = "swipe"

    const val NOTIF_CHANNEL_ID   = "autoclicker_channel"
    const val NOTIF_ID_OVERLAY   = 1
    const val NOTIF_ID_CAPTURE   = 2
    const val NOTIF_ID_RECORDER  = 3
}
