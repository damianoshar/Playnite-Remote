package com.gamevault.unified.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamevault.unified.data.model.ServerConfig
import com.gamevault.unified.data.model.ServerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.bridgeDataStore: DataStore<Preferences> by preferencesDataStore(name = "bridge_server")

/**
 * Stores the saved bridge connections.
 *
 * Several profiles can coexist (different PCs, or a second account on the
 * same PC); one profile is active at a time. The legacy single
 * `server_base_url`/`server_token` pair is migrated into a profile on first
 * read, so existing installs keep working.
 */
class ServerStorage(private val context: Context) {

    private val baseUrlKey = stringPreferencesKey("server_base_url")
    private val tokenKey = stringPreferencesKey("server_token")
    private val profilesKey = stringPreferencesKey("server_profiles")
    private val activeIdKey = stringPreferencesKey("active_server_id")

    /** All saved profiles, in the order the user added them. */
    val profilesFlow: Flow<List<ServerProfile>> =
        context.bridgeDataStore.data.map { prefs ->
            val decoded = ServerProfilesJson.decode(prefs[profilesKey].orEmpty())
            if (decoded.isNotEmpty()) return@map decoded
            // Legacy migration: single config -> single profile.
            listOfNotNull(legacyProfile(prefs))
        }

    /** The active profile, or null when nothing is connected. */
    val activeProfileFlow: Flow<ServerProfile?> =
        context.bridgeDataStore.data.map { prefs ->
            val decoded = ServerProfilesJson.decode(prefs[profilesKey].orEmpty())
            val legacy = legacyProfile(prefs)
            val profiles = decoded.ifEmpty { listOfNotNull(legacy) }
            if (profiles.isEmpty()) return@map null
            val activeId = prefs[activeIdKey]
            profiles.firstOrNull { it.id == activeId }
                ?: legacy?.takeIf { decoded.isEmpty() }
                ?: profiles.first()
        }

    /** Connection details of the active profile (null when disconnected). */
    val configFlow: Flow<ServerConfig?> = activeProfileFlow.map { it?.toConfig() }

    suspend fun snapshot(): ServerConfig? = configFlow.first()

    suspend fun snapshotProfile(): ServerProfile? = activeProfileFlow.first()

    suspend fun allProfiles(): List<ServerProfile> = profilesFlow.first()

    /**
     * Saves a connection. If the same address+token already exists, that
     * profile is updated (and made active) instead of being duplicated.
     */
    suspend fun save(config: ServerConfig, name: String = ""): ServerProfile {
        val candidate = ServerProfile(name = name, baseUrl = config.baseUrl.trim(), token = config.token.trim())
        val existing = allProfiles()
        val match = existing.firstOrNull { ServerProfile.matches(it, candidate) }
        val profile = if (match != null) {
            match.copy(name = name.ifBlank { match.name })
        } else {
            candidate
        }
        val updated = if (match != null) {
            existing.map { if (it.id == match.id) profile else it }
        } else {
            existing + profile
        }
        context.bridgeDataStore.edit { prefs ->
            prefs[profilesKey] = ServerProfilesJson.encode(updated)
            prefs[activeIdKey] = profile.id
            // Keep the legacy keys in sync so older code paths still work.
            prefs[baseUrlKey] = profile.baseUrl
            prefs[tokenKey] = profile.token
        }
        return profile
    }

    /** Makes an existing profile active without re-testing the connection. */
    suspend fun activate(profileId: String) {
        val profile = allProfiles().firstOrNull { it.id == profileId } ?: return
        context.bridgeDataStore.edit { prefs ->
            prefs[activeIdKey] = profile.id
            prefs[baseUrlKey] = profile.baseUrl
            prefs[tokenKey] = profile.token
        }
    }

    /** Removes a profile. Returns true when the active one was removed. */
    suspend fun remove(profileId: String): Boolean {
        val profiles = allProfiles()
        val target = profiles.firstOrNull { it.id == profileId } ?: return false
        val wasActive = snapshotProfile()?.id == profileId
        val remaining = profiles.filterNot { it.id == profileId }
        context.bridgeDataStore.edit { prefs ->
            prefs[profilesKey] = ServerProfilesJson.encode(remaining)
            if (wasActive) {
                val next = remaining.firstOrNull()
                if (next != null) {
                    prefs[activeIdKey] = next.id
                    prefs[baseUrlKey] = next.baseUrl
                    prefs[tokenKey] = next.token
                } else {
                    prefs.remove(activeIdKey)
                    prefs.remove(baseUrlKey)
                    prefs.remove(tokenKey)
                }
            }
        }
        return wasActive
    }

    /** Disconnects without forgetting the saved profiles. */
    suspend fun disconnect() {
        context.bridgeDataStore.edit { prefs ->
            prefs.remove(activeIdKey)
            prefs.remove(baseUrlKey)
            prefs.remove(tokenKey)
        }
    }

    /** Disconnects and forgets every saved profile. */
    suspend fun clear() {
        context.bridgeDataStore.edit { it.clear() }
    }

    private fun legacyProfile(prefs: Preferences): ServerProfile? {
        val base = prefs[baseUrlKey].orEmpty()
        val token = prefs[tokenKey].orEmpty()
        if (base.isBlank() || token.isBlank()) return null
        return ServerProfile(
            id = LEGACY_PROFILE_ID,
            name = "",
            baseUrl = base,
            token = token
        )
    }

    companion object {
        /** Stable id for the profile migrated from the old single-config keys. */
        const val LEGACY_PROFILE_ID = "legacy"
    }
}
