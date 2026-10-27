package com.gamevault.unified.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.artworkDataStore by preferencesDataStore(name = "artwork_state")

/**
 * IDs of games known to have NO cover art on the PC (from hasCover=false in
 * full details). The list/grid skips the /cover request entirely for these,
 * which kills the 404-per-scroll storm that upsets the bridge.
 */
class MissingArtStore(private val context: Context) {

    private val key = stringSetPreferencesKey("artless_ids")

    suspend fun snapshot(): Set<String> =
        context.artworkDataStore.data.map { it[key] ?: emptySet() }.first()

    suspend fun add(ids: Collection<String>) {
        if (ids.isEmpty()) return
        context.artworkDataStore.edit { it[key] = (it[key] ?: emptySet()) + ids }
    }

    suspend fun remove(id: String) {
        context.artworkDataStore.edit { it[key] = (it[key] ?: emptySet()) - id }
    }

    suspend fun replace(ids: Set<String>) {
        context.artworkDataStore.edit { it[key] = ids }
    }
}
