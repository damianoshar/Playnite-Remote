package com.gamevault.unified.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.uiDataStore: DataStore<Preferences> by preferencesDataStore(name = "ui_prefs")

enum class ViewMode { LIST, GRID }

data class UiViewPrefs(
    val viewMode: ViewMode = ViewMode.LIST,
    val gridSpan: Int = 3,
    val pageSize: Int = 0, // 0 = All
    val retainImages: Boolean = false,
    val appThemeName: String = "DEFAULT",
    val fullOffline: Boolean = false,
    val lastFullSync: Long = 0,
    val notes: Map<String, String> = emptyMap(),
    val collections: Map<String, Set<String>> = emptyMap()
)

class UiPrefs(private val context: Context) {

    private val modeKey = stringPreferencesKey("view_mode")
    private val spanKey = intPreferencesKey("grid_span")
    private val pageSizeKey = intPreferencesKey("page_size")
    private val retainImagesKey = stringPreferencesKey("retain_images")
    private val themeKey = stringPreferencesKey("app_theme")
    private val fullOfflineKey = stringPreferencesKey("full_offline")
    private val lastFullSyncKey = stringPreferencesKey("last_full_sync")
    private val notesKey = stringPreferencesKey("game_notes")
    private val collectionsKey = stringPreferencesKey("collections")

    val prefsFlow: Flow<UiViewPrefs> =
        context.uiDataStore.data.map { prefs ->
            val raw = prefs[notesKey].orEmpty()
            val notes = mutableMapOf<String, String>()
            if (raw.isNotBlank()) {
                try {
                    val obj = org.json.JSONObject(raw)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        notes[k] = obj.optString(k)
                    }
                } catch (e: Exception) { /* ignore corrupt */ }
            }
            val collectionsRaw = prefs[collectionsKey].orEmpty()
            val collections = mutableMapOf<String, Set<String>>()
            if (collectionsRaw.isNotBlank()) {
                try {
                    val obj = org.json.JSONObject(collectionsRaw)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val arr = obj.optJSONArray(k)
                        val ids = mutableSetOf<String>()
                        if (arr != null) for (i in 0 until arr.length()) ids.add(arr.optString(i))
                        collections[k] = ids
                    }
                } catch (e: Exception) { /* ignore corrupt */ }
            }
            UiViewPrefs(
                viewMode = prefs[modeKey]?.let {
                    try { ViewMode.valueOf(it) } catch (e: IllegalArgumentException) { ViewMode.LIST }
                } ?: ViewMode.LIST,
                gridSpan = (prefs[spanKey] ?: 3).coerceIn(2, 5),
                pageSize = prefs[pageSizeKey] ?: 0,
                retainImages = prefs[retainImagesKey].toBoolean(),
                appThemeName = prefs[themeKey] ?: "DEFAULT",
                fullOffline = prefs[fullOfflineKey].toBoolean(),
                lastFullSync = prefs[lastFullSyncKey]?.toLongOrNull() ?: 0,
                notes = notes,
                collections = collections
            )
        }

    suspend fun snapshot(): UiViewPrefs = prefsFlow.first()

    suspend fun saveViewMode(mode: ViewMode) {
        context.uiDataStore.edit { it[modeKey] = mode.name }
    }

    suspend fun saveGridSpan(span: Int) {
        context.uiDataStore.edit { it[spanKey] = span.coerceIn(2, 5) }
    }

    /** 0 = All (no pagination). */
    suspend fun savePageSize(size: Int) {
        context.uiDataStore.edit { it[pageSizeKey] = size.coerceIn(0, 500) }
    }

    suspend fun saveRetainImages(enabled: Boolean) {
        context.uiDataStore.edit { it[retainImagesKey] = enabled.toString() }
    }

    suspend fun saveAppTheme(name: String) {
        context.uiDataStore.edit { it[themeKey] = name }
    }

    suspend fun saveFullOffline(enabled: Boolean) {
        context.uiDataStore.edit { it[fullOfflineKey] = enabled.toString() }
    }

    suspend fun saveLastFullSync(timestamp: Long) {
        context.uiDataStore.edit { it[lastFullSyncKey] = timestamp.toString() }
    }

    suspend fun saveNote(gameId: String, note: String) {
        context.uiDataStore.edit { prefs ->
            val raw = prefs[notesKey].orEmpty()
            val obj = if (raw.isBlank()) org.json.JSONObject() else org.json.JSONObject(raw)
            if (note.isBlank()) obj.remove(gameId) else obj.put(gameId, note)
            prefs[notesKey] = obj.toString()
        }
    }

    suspend fun saveNotes(notes: Map<String, String>) {
        context.uiDataStore.edit { prefs ->
            val obj = org.json.JSONObject()
            notes.forEach { (k, v) -> obj.put(k, v) }
            prefs[notesKey] = obj.toString()
        }
    }

    fun exportJson(prefs: UiViewPrefs): String {
        val obj = org.json.JSONObject()
        obj.put("viewMode", prefs.viewMode.name)
        obj.put("gridSpan", prefs.gridSpan)
        obj.put("pageSize", prefs.pageSize)
        obj.put("retainImages", prefs.retainImages)
        obj.put("appThemeName", prefs.appThemeName)
        obj.put("fullOffline", prefs.fullOffline)
        obj.put("lastFullSync", prefs.lastFullSync)
        val notes = org.json.JSONObject()
        prefs.notes.forEach { (k, v) -> notes.put(k, v) }
        obj.put("notes", notes)
        val colls = org.json.JSONObject()
        prefs.collections.forEach { (name, ids) ->
            val arr = org.json.JSONArray()
            ids.forEach { arr.put(it) }
            colls.put(name, arr)
        }
        obj.put("collections", colls)
        return obj.toString(2)
    }

    suspend fun importJson(json: String): UiViewPrefs {
        val obj = org.json.JSONObject(json)
        val mode = try { ViewMode.valueOf(obj.optString("viewMode", "LIST")) } catch (e: Exception) { ViewMode.LIST }
        val span = obj.optInt("gridSpan", 3).coerceIn(2, 5)
        val pageSize = obj.optInt("pageSize", 0).coerceIn(0, 500)
        val retain = obj.optBoolean("retainImages", false)
        val theme = obj.optString("appThemeName", "DEFAULT")
        val fullOffline = obj.optBoolean("fullOffline", false)
        val lastSync = obj.optLong("lastFullSync", 0)
        val notesObj = obj.optJSONObject("notes")
        val notes = mutableMapOf<String, String>()
        if (notesObj != null) {
            val keys = notesObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                notes[k] = notesObj.optString(k)
            }
        }
        val collsObj = obj.optJSONObject("collections")
        val collections = mutableMapOf<String, Set<String>>()
        if (collsObj != null) {
            val keys = collsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = collsObj.optJSONArray(k)
                val ids = mutableSetOf<String>()
                if (arr != null) for (i in 0 until arr.length()) ids.add(arr.optString(i))
                collections[k] = ids
            }
        }
        saveViewMode(mode)
        saveGridSpan(span)
        savePageSize(pageSize)
        saveRetainImages(retain)
        saveAppTheme(theme)
        saveFullOffline(fullOffline)
        saveLastFullSync(lastSync)
        saveNotes(notes)
        saveCollections(collections)
        return UiViewPrefs(mode, span, pageSize, retain, theme, fullOffline, lastSync, notes, collections)
    }

    suspend fun saveCollections(collections: Map<String, Set<String>>) {
        context.uiDataStore.edit { prefs ->
            val obj = org.json.JSONObject()
            collections.forEach { (name, ids) ->
                val arr = org.json.JSONArray()
                ids.forEach { arr.put(it) }
                obj.put(name, arr)
            }
            prefs[collectionsKey] = obj.toString()
        }
    }
}
