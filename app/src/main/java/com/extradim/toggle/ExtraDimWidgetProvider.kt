package com.extradim.toggle

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home-screen widget: tap to toggle Extra Dim on/off.
 *
 * Uses goAsync() and structured coroutines off the main thread.
 */
class ExtraDimWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val enabled = withContext(Dispatchers.IO) {
                    ExtraDimController.isEnabled(context)
                }
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(id, buildViews(context, enabled))
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val enabled = withContext(Dispatchers.IO) {
                        ExtraDimController.toggle(context)
                    }
                    updateAllWidgets(context, enabled)
                } finally {
                    pendingResult.finish()
                }
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.extradim.toggle.action.TOGGLE"

        fun updateAll(context: Context) {
            val enabled = ExtraDimController.isEnabled(context)
            updateAllWidgets(context, enabled)
        }

        private fun updateAllWidgets(context: Context, enabled: Boolean) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, ExtraDimWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val views = buildViews(context, enabled)
                ids.forEach { id -> mgr.updateAppWidget(id, views) }
            }
        }

        fun buildViews(context: Context, enabled: Boolean): RemoteViews {
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
    }
}
