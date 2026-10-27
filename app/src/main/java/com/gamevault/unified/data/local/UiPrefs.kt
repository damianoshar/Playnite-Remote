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
    val lastFullSync: Long = 0
)

class UiPrefs(private val context: Context) {

    private val modeKey = stringPreferencesKey("view_mode")
    private val spanKey = intPreferencesKey("grid_span")
    private val pageSizeKey = intPreferencesKey("page_size")
    private val retainImagesKey = stringPreferencesKey("retain_images")
    private val themeKey = stringPreferencesKey("app_theme")
    private val fullOfflineKey = stringPreferencesKey("full_offline")
    private val lastFullSyncKey = stringPreferencesKey("last_full_sync")

    val prefsFlow: Flow<UiViewPrefs> =
        context.uiDataStore.data.map { prefs ->
            UiViewPrefs(
                viewMode = prefs[modeKey]?.let {
                    try { ViewMode.valueOf(it) } catch (e: IllegalArgumentException) { ViewMode.LIST }
                } ?: ViewMode.LIST,
                gridSpan = (prefs[spanKey] ?: 3).coerceIn(2, 5),
                pageSize = prefs[pageSizeKey] ?: 0,
                retainImages = prefs[retainImagesKey].toBoolean(),
                appThemeName = prefs[themeKey] ?: "DEFAULT",
                fullOffline = prefs[fullOfflineKey].toBoolean(),
                lastFullSync = prefs[lastFullSyncKey]?.toLongOrNull() ?: 0
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
}
