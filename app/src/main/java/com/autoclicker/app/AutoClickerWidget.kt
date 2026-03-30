package com.autoclicker.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * AutoClickerWidget — Widget de pantalla de inicio (v3).
 *
 * Permite iniciar/detener el auto-clicker directamente desde el launcher
 * sin necesidad de abrir la app. Muestra el estado actual y el nombre
 * del último script ejecutado.
 *
 * Arquitectura:
 * - Botón START → PendingIntent → ACTION_WIDGET_START → AutoClickService
 * - Botón STOP  → PendingIntent → ACTION_WIDGET_STOP  → AutoClickService
 * - Botón APP   → abre MainActivity
 * - Estado actualizado por AutoClickService.updateWidget()
 */
class AutoClickerWidget : AppWidgetProvider() {

    companion object {

        /** Llamado por AutoClickService cuando cambia el estado */
        fun updateAll(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            state: ClickerState
        ) {
            for (id in ids) {
                val views = buildViews(context, state)
                manager.updateAppWidget(id, views)
            }
        }

        private fun buildViews(context: Context, state: ClickerState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_autoclicker)

            // Estado
            val (stateText, stateColor) = when (state) {
                ClickerState.RUNNING -> "● EN EJECUCIÓN" to 0xFF4CAF50.toInt()
                ClickerState.PAUSED  -> "⏸ PAUSADO"      to 0xFFFF9800.toInt()
                ClickerState.IDLE    -> "○ Detenido"     to 0xFF9E9E9E.toInt()
            }
            views.setTextViewText(R.id.tvWidgetState, stateText)
            views.setTextColor(R.id.tvWidgetState, stateColor)

            // Botón START
            val startIntent = Intent(AppConstants.ACTION_WIDGET_START).apply {
                component = ComponentName(context, WidgetCommandReceiver::class.java)
            }
            val startPi = PendingIntent.getBroadcast(
                context, 1, startIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.btnWidgetStart, startPi)

            // Botón STOP
            val stopIntent = Intent(AppConstants.ACTION_WIDGET_STOP).apply {
                component = ComponentName(context, WidgetCommandReceiver::class.java)
            }
            val stopPi = PendingIntent.getBroadcast(
                context, 2, stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.btnWidgetStop, stopPi)

            // Botón abrir app
            val openPi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btnWidgetOpen, openPi)

            return views
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Estado inicial cuando se agrega el widget
        updateAll(context, manager, ids, ClickerState.IDLE)
    }
}
