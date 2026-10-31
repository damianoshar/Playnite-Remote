package com.gamevault.unified.data.remote

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// --- playnite-bridge DTOs (rollacode/playnite-bridge docs/api.md + skill.md) --
// Compact shape: Services/GameSerializationService.SerializeCompact
// Full shape: ...SerializeFull. Stats: Handlers/AppHandler.GetStats.

data class BridgeGameDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("source") val source: String? = null,
    @SerializedName("genres") val genres: List<String> = emptyList(),
    @SerializedName("categories") val categories: List<String> = emptyList(),
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("features") val features: List<String> = emptyList(),
    @SerializedName("platforms") val platforms: List<String> = emptyList(),
    @SerializedName("completionStatus") val completionStatus: String? = null,
    @SerializedName("isInstalled") val isInstalled: Boolean = false,
    @SerializedName("favorite") val favorite: Boolean = false,
    @SerializedName("hidden") val hidden: Boolean = false,
    @SerializedName("playtime") val playtime: Long = 0,
    @SerializedName("playCount") val playCount: Long = 0,
    @SerializedName("lastActivity") val lastActivity: String? = null,
    @SerializedName("userScore") val userScore: Int? = null
)

data class GamesPageDto(
    @SerializedName("total") val total: Int = 0,
    @SerializedName("offset") val offset: Int = 0,
    @SerializedName("limit") val limit: Int = 500,
    @SerializedName("games") val games: List<BridgeGameDto> = emptyList()
)

data class BridgeLinkDto(
    @SerializedName("name") val name: String = "",
    @SerializedName("url") val url: String = ""
)

data class GameDetailsDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("sortingName") val sortingName: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("developers") val developers: List<String> = emptyList(),
    @SerializedName("publishers") val publishers: List<String> = emptyList(),
    @SerializedName("series") val series: List<String> = emptyList(),
    @SerializedName("genres") val genres: List<String> = emptyList(),
    @SerializedName("categories") val categories: List<String> = emptyList(),
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("features") val features: List<String> = emptyList(),
    @SerializedName("platforms") val platforms: List<String> = emptyList(),
    @SerializedName("ageRatings") val ageRatings: List<String> = emptyList(),
    @SerializedName("regions") val regions: List<String> = emptyList(),
    @SerializedName("links") val links: List<BridgeLinkDto>? = null,
    @SerializedName("completionStatus") val completionStatus: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("modified") val modified: String? = null,
    @SerializedName("isInstalled") val isInstalled: Boolean = false,
    @SerializedName("installDirectory") val installDirectory: String? = null,
    @SerializedName("installSize") val installSize: Long? = null,
    @SerializedName("hidden") val hidden: Boolean = false,
    @SerializedName("favorite") val favorite: Boolean = false,
    @SerializedName("playtime") val playtime: Long = 0,
    @SerializedName("playCount") val playCount: Long = 0,
    @SerializedName("lastActivity") val lastActivity: String? = null,
    @SerializedName("communityScore") val communityScore: Int? = null,
    @SerializedName("criticScore") val criticScore: Int? = null,
    @SerializedName("userScore") val userScore: Int? = null,
    @SerializedName("gameId") val gameId: String? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("hasCover") val hasCover: Boolean = false,
    @SerializedName("hasBackground") val hasBackground: Boolean = false
)

data class IdNameDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = ""
)

data class AppInfoDto(
    @SerializedName("version") val version: String? = null
)

data class SourceCountDto(
    @SerializedName("source") val source: String = "",
    @SerializedName("count") val count: Int = 0
)

data class StatusCountDto(
    @SerializedName("status") val status: String = "",
    @SerializedName("count") val count: Int = 0
)

data class GenreCountDto(
    @SerializedName("genre") val genre: String = "",
    @SerializedName("count") val count: Int = 0
)

data class RecentGameDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("lastPlayed") val lastPlayed: String? = null
)

data class StatsDto(
    @SerializedName("totalGames") val totalGames: Int = 0,
    @SerializedName("installed") val installed: Int = 0,
    @SerializedName("notInstalled") val notInstalled: Int = 0,
    @SerializedName("hidden") val hidden: Int = 0,
    @SerializedName("favorite") val favorite: Int = 0,
    @SerializedName("categorized") val categorized: Int = 0,
    @SerializedName("uncategorized") val uncategorized: Int = 0,
    @SerializedName("totalPlaytimeHours") val totalPlaytimeHours: Double = 0.0,
    @SerializedName("bySource") val bySource: List<SourceCountDto> = emptyList(),
    @SerializedName("byCompletionStatus") val byCompletionStatus: List<StatusCountDto> = emptyList(),
    @SerializedName("topGenres") val topGenres: List<GenreCountDto> = emptyList(),
    @SerializedName("recentlyPlayed") val recentlyPlayed: List<RecentGameDto> = emptyList()
)

data class BridgeAchievementDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("unlocked") val unlocked: Boolean = false,
    @SerializedName("dateUnlocked") val dateUnlocked: String? = null,
    @SerializedName("percent") val percent: Double? = null
)

data class AchievementsDto(
    @SerializedName("installed") val installed: Boolean = false,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("unlocked") val unlocked: Int = 0,
    @SerializedName("locked") val locked: Int = 0,
    @SerializedName("achievements") val achievements: List<BridgeAchievementDto> = emptyList()
)

data class BridgeSessionDto(
    @SerializedName("date") val date: String? = null,
    @SerializedName("elapsedSeconds") val elapsedSeconds: Long = 0,
    @SerializedName("action") val action: String? = null
)

data class ActivityDto(
    @SerializedName("installed") val installed: Boolean = false,
    @SerializedName("totalSeconds") val totalSeconds: Long = 0,
    @SerializedName("totalHours") val totalHours: Double = 0.0,
    @SerializedName("sessionCount") val sessionCount: Int = 0,
    @SerializedName("sessions") val sessions: List<BridgeSessionDto> = emptyList()
)

interface BridgeApi {
    @GET("api/games")
    suspend fun getGames(
        @Query("limit") limit: Int = 2000,
        @Query("offset") offset: Int = 0,
        @Query("hidden") includeHidden: Boolean = true
    ): GamesPageDto

    @GET("api/games/{id}")
    suspend fun getGame(@Path("id") id: String): GameDetailsDto

    @PUT("api/games/{id}")
    suspend fun updateGame(@Path("id") id: String, @Body fields: JsonObject): Response<ResponseBody>

    @GET("api/games/{id}/achievements")
    suspend fun getAchievements(@Path("id") id: String): AchievementsDto

    @GET("api/games/{id}/activity")
    suspend fun getActivity(@Path("id") id: String): ActivityDto

    @GET("api/sources")
    suspend fun getSources(): List<IdNameDto>

    @GET("api/app/info")
    suspend fun getAppInfo(): AppInfoDto

    @GET("api/stats")
    suspend fun getStats(): StatsDto

    @POST("api/games/{id}/launch")
    suspend fun launch(@Path("id") id: String): JsonObject
}
