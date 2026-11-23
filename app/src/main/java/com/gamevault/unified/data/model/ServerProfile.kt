package com.gamevault.unified.data.model

import java.util.UUID

/**
 * A saved PC/bridge connection. Several profiles can coexist (different PCs,
 * different accounts on the same PC); exactly one is active at a time.
 *
 * [id] is stable for the lifetime of the profile so per-server caches
 * (offline snapshot, cover art) survive switching back and forth.
 */
data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val baseUrl: String = "",
    val token: String = ""
) {
    /** Display label: explicit name, otherwise the host (without scheme). */
    val label: String
        get() = name.ifBlank { addressLabel }

    /**
     * "192.168.1.50:19821" — the address without the scheme. Used as the
     * fallback title so an unnamed profile does not repeat the full URL
     * that is already shown underneath it.
     */
    val addressLabel: String
        get() = baseUrl.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
            .substringBefore('/')
            .ifBlank { "Unnamed PC" }

    fun toConfig(): ServerConfig = ServerConfig(baseUrl, token)

    companion object {
        /** Matches profiles by address AND token, so re-saving the same
         *  connection updates it instead of creating a duplicate. */
        fun matches(a: ServerProfile, b: ServerProfile): Boolean =
            a.baseUrl.trim().trimEnd('/').equals(b.baseUrl.trim().trimEnd('/'), ignoreCase = true) &&
                a.token.trim() == b.token.trim()
    }
}
