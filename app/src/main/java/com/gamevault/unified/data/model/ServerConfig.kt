package com.gamevault.unified.data.model

/**
 * Connection to the playnite-bridge plugin running on the PC.
 * Example baseUrl: http://192.168.1.20:19821
 */
data class ServerConfig(
    val baseUrl: String = "",
    val token: String = ""
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()

    /** Base URL without trailing slash, with scheme. */
    fun trimmedBase(): String {
        var base = baseUrl.trim().trimEnd('/')
        if (base.isNotBlank() && !base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://$base"
        }
        return base
    }

    /** Retrofit-ready base URL (must end with /). */
    fun retrofitBase(): String = trimmedBase() + "/"

    fun coverUrl(gameId: String, type: String = "cover", width: Int? = null): String {
        val base = "${trimmedBase()}/api/games/$gameId/cover?type=$type"
        return if (width != null) "$base&width=$width" else base
    }
}
