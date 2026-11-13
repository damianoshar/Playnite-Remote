package com.gamevault.unified.data.local

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure (Context-free) serialization of the user-visible settings.
 *
 * Kept separate from [UiPrefs] so it can be unit-tested without Android and
 * reused by backup/restore flows.
 */
object SettingsJson {

    /** Serializes the settings to a pretty-printed JSON document. */
    fun export(prefs: UiViewPrefs): String {
        val obj = JSONObject()
        obj.put("viewMode", prefs.viewMode.name)
        obj.put("gridSpan", prefs.gridSpan)
        obj.put("pageSize", prefs.pageSize)
        obj.put("retainImages", prefs.retainImages)
        obj.put("appThemeName", prefs.appThemeName)
        obj.put("fullOffline", prefs.fullOffline)
        obj.put("lastFullSync", prefs.lastFullSync)

        val notes = JSONObject()
        prefs.notes.forEach { (k, v) -> notes.put(k, v) }
        obj.put("notes", notes)

        val collections = JSONObject()
        prefs.collections.forEach { (name, ids) ->
            val arr = JSONArray()
            ids.forEach { arr.put(it) }
            collections.put(name, arr)
        }
        obj.put("collections", collections)

        return obj.toString(2)
    }

    /**
     * Parses a settings document. Unknown/missing fields fall back to the
     * defaults; out-of-range numbers are clamped.
     */
    fun parse(json: String): UiViewPrefs {
        val obj = JSONObject(json)

        val mode = try {
            ViewMode.valueOf(obj.optString("viewMode", "LIST"))
        } catch (e: Exception) {
            ViewMode.LIST
        }
        val span = obj.optInt("gridSpan", 3).coerceIn(2, 5)
        val pageSize = obj.optInt("pageSize", 0).coerceIn(0, 500)
        val retain = obj.optBoolean("retainImages", false)
        val theme = obj.optString("appThemeName", "DEFAULT")
        val fullOffline = obj.optBoolean("fullOffline", false)
        val lastSync = obj.optLong("lastFullSync", 0)

        val notes = mutableMapOf<String, String>()
        obj.optJSONObject("notes")?.let { notesObj ->
            val keys = notesObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                notes[k] = notesObj.optString(k)
            }
        }

        val collections = mutableMapOf<String, Set<String>>()
        obj.optJSONObject("collections")?.let { collsObj ->
            val keys = collsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = collsObj.optJSONArray(k)
                val ids = mutableSetOf<String>()
                if (arr != null) for (i in 0 until arr.length()) ids.add(arr.optString(i))
                collections[k] = ids
            }
        }

        return UiViewPrefs(
            viewMode = mode,
            gridSpan = span,
            pageSize = pageSize,
            retainImages = retain,
            appThemeName = theme,
            fullOffline = fullOffline,
            lastFullSync = lastSync,
            notes = notes,
            collections = collections
        )
    }
}
