package com.autoclicker.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * WidgetCommandReceiver — Puente entre el widget (proceso del launcher)
 * y AutoClickService (proceso de la app).
 *
 * El widget envía broadcasts regulares aquí. Este receiver los
 * reenvía como broadcasts regulares que AutoClickService escucha.
 * Si el servicio de accesibilidad no está activo, el comando se ignora.
 */
class WidgetCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AppConstants.ACTION_WIDGET_START,
            AppConstants.ACTION_WIDGET_STOP -> {
                // Reenviar al mismo proceso — AutoClickService tiene un receiver
                // registrado para estas acciones en onServiceConnected()
                val forward = Intent(intent.action).apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(forward)
            }
        }
    }
}
