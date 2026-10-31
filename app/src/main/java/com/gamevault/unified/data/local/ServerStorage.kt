package com.gamevault.unified.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamevault.unified.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.bridgeDataStore: DataStore<Preferences> by preferencesDataStore(name = "bridge_server")

class ServerStorage(private val context: Context) {

    private val baseUrlKey = stringPreferencesKey("server_base_url")
    private val tokenKey = stringPreferencesKey("server_token")

    val configFlow: Flow<ServerConfig?> =
        context.bridgeDataStore.data.map { prefs ->
            val base = prefs[baseUrlKey].orEmpty()
            val token = prefs[tokenKey].orEmpty()
            if (base.isBlank() || token.isBlank()) null else ServerConfig(base, token)
        }

    suspend fun snapshot(): ServerConfig? = configFlow.first()

    suspend fun save(config: ServerConfig) {
        context.bridgeDataStore.edit { prefs ->
            prefs[baseUrlKey] = config.baseUrl.trim()
            prefs[tokenKey] = config.token.trim()
        }
    }

    suspend fun clear() {
        context.bridgeDataStore.edit { it.clear() }
    }
}
