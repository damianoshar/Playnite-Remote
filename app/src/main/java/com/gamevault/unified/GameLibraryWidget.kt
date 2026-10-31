package com.gamevault.unified

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.gson.Gson
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GameLibraryWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        Thread {
            try {
                for (id in appWidgetIds) {
                    updateWidget(context, appWidgetManager, id)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun updateWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        val (title, subtitle) = readStatus(context)
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_subtitle, subtitle)

        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)

        mgr.updateAppWidget(widgetId, views)
    }

    private fun readStatus(context: Context): Pair<String, String> {
        val file = File(context.filesDir, "library_cache/library.json")
        if (!file.exists()) return "Playnite Remote" to "No sync yet — tap to open"
        return try {
            val json = file.readText()
            val obj = com.google.gson.JsonParser.parseString(json).asJsonObject
            val savedAt = obj.get("savedAt")?.asLong ?: 0L
            val gameCount = obj.get("games")?.asJsonArray?.size() ?: 0
            val whenStr = if (savedAt > 0) {
                SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(savedAt))
            } else "—"
            "Playnite Remote" to "$gameCount games · synced $whenStr"
        } catch (e: Exception) {
            "Playnite Remote" to "Tap to open"
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, GameLibraryWidget::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, GameLibraryWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
