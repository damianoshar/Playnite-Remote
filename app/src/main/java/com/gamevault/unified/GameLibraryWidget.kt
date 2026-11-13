package com.gamevault.unified

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.gamevault.unified.data.local.CacheLayout
import com.gamevault.unified.data.local.ServerStorage
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
        val file = activeSnapshotFile(context) ?: return "Playnite Remote" to "No sync yet — tap to open"
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

    /**
     * Locates the snapshot of the active server profile. Falls back to the
     * legacy single-cache file so pre-multi-server installs keep showing data.
     */
    private fun activeSnapshotFile(context: Context): File? {
        val root = File(context.filesDir, CacheLayout.ROOT_DIR)
        val legacy = File(context.filesDir, CacheLayout.LEGACY_LIBRARY_FILE)
        val scope = activeScopeId(context)
        if (scope != null) {
            val scoped = CacheLayout.libraryFile(root, scope)
            if (scoped.exists()) return scoped
        }
        if (legacy.exists()) return legacy
        // Fall back to any scope that has a snapshot (only one server active).
        root.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val candidate = CacheLayout.libraryFile(root, dir.name.removePrefix("scope_"))
                if (candidate.exists()) return candidate
            }
        }
        return null
    }

    /** Reads the active profile id from the shared DataStore, if any. */
    private fun activeScopeId(context: Context): String? = try {
        // onUpdate already runs on a background thread, so blocking here is safe.
        kotlinx.coroutines.runBlocking {
            ServerStorage(context.applicationContext).snapshotProfile()?.id
        }
    } catch (e: Exception) {
        null
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
