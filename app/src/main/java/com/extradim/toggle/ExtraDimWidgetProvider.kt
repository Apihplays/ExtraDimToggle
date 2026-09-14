package com.extradim.toggle

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home-screen widget: tap to toggle Extra Dim on/off.
 *
 * Uses goAsync() so the process is not killed while the (slow) root shell
 * runs, and immutable PendingIntents per current Android requirements.
 */
class ExtraDimWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        Thread {
            try {
                val enabled = ExtraDimController.isEnabled()
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(id, buildViews(context, enabled))
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            val pendingResult = goAsync()
            Thread {
                try {
                    val mgr = AppWidgetManager.getInstance(context)
                    val enabled = ExtraDimController.toggle()
                    val ids = mgr.getAppWidgetIds(
                        ComponentName(context, ExtraDimWidgetProvider::class.java)
                    )
                    ids.forEach { id -> mgr.updateAppWidget(id, buildViews(context, enabled)) }
                } finally {
                    pendingResult.finish()
                }
            }.start()
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun buildViews(context: Context, enabled: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_extra_dim)
        views.setTextViewText(R.id.widget_status, "Extra Dim")
        views.setTextViewText(
            R.id.widget_toggle,
            if (enabled) "ON" else "OFF"
        )
        val intent = Intent(context, ExtraDimWidgetProvider::class.java)
            .setAction(ACTION_TOGGLE)
            .setPackage(context.packageName)
        val pending = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_toggle, pending)
        views.setOnClickPendingIntent(R.id.widget_status, pending)
        return views
    }

    companion object {
        const val ACTION_TOGGLE = "com.extradim.toggle.action.TOGGLE"
    }
}
