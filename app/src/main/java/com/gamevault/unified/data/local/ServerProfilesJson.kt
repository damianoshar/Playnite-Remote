package com.gamevault.unified.data.local

import com.gamevault.unified.data.model.ServerProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure (Context-free) codec for the saved server profiles.
 *
 * Kept separate from [ServerStorage] so it can be unit-tested without Android.
 */
object ServerProfilesJson {

    fun encode(profiles: List<ServerProfile>): String {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("baseUrl", p.baseUrl)
                    put("token", p.token)
                }
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<ServerProfile> {
        if (json.isBlank()) return emptyList()
        val arr = try {
            JSONArray(json)
        } catch (e: Exception) {
            return emptyList()
        }
        val out = mutableListOf<ServerProfile>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val baseUrl = obj.optString("baseUrl")
            val token = obj.optString("token")
            if (baseUrl.isBlank() || token.isBlank()) continue
            out += ServerProfile(
                id = obj.optString("id").ifBlank { ServerProfile().id },
                name = obj.optString("name"),
                baseUrl = baseUrl,
                token = token
            )
        }
        return out
    }
}
