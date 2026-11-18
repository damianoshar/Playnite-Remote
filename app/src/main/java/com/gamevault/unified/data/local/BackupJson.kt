package com.gamevault.unified.data.local

import com.gamevault.unified.data.model.ServerProfile
import org.json.JSONObject

/**
 * Backup/restore document: every saved PC (address + token) plus the UI
 * settings, in a single JSON file the user can copy to another phone.
 *
 * The file contains bearer tokens — treat it like a password.
 */
object BackupJson {

    const val FORMAT = "playnite-remote-backup"

    /** Bumped when the document layout changes. */
    const val FORMAT_VERSION = 1

    /** Decoded backup. [settings] is null when the file has no settings block. */
    data class Backup(
        val profiles: List<ServerProfile>,
        val settings: UiViewPrefs?,
        val version: Int
    )

    fun encode(profiles: List<ServerProfile>, settings: UiViewPrefs): String {
        val obj = JSONObject()
        obj.put("format", FORMAT)
        obj.put("version", FORMAT_VERSION)
        obj.put("exportedAt", System.currentTimeMillis())
        obj.put("servers", ServerProfilesJson.encode(profiles))
        obj.put("settings", JSONObject(SettingsJson.export(settings)))
        return obj.toString(2)
    }

    /**
     * Parses a backup document. Throws [org.json.JSONException] when the file
     * is not a backup at all; missing sections simply come back empty.
     */
    fun decode(json: String): Backup {
        val obj = JSONObject(json)
        val format = obj.optString("format")
        if (format.isNotBlank() && format != FORMAT) {
            throw IllegalArgumentException("Not a Playnite Remote backup (format: $format)")
        }
        val profiles = ServerProfilesJson.decode(obj.optString("servers"))
        val settings = obj.optJSONObject("settings")?.let { block ->
            try {
                SettingsJson.parse(block.toString())
            } catch (e: Exception) {
                null
            }
        }
        return Backup(
            profiles = profiles,
            settings = settings,
            version = obj.optInt("version", 1)
        )
    }
}
