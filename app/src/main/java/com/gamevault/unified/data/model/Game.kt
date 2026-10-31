package com.gamevault.unified.data.model

import java.util.UUID

/**
 * Game as returned by the playnite-bridge plugin (GET /api/games).
 * playtime is in SECONDS (Playnite core unit).
 * All facet lists are included so every server-side filter works offline.
 */
data class Game(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val source: String = "",
    val coverUrl: String = "",
    val playtimeSeconds: Long = 0,
    val playCount: Long = 0,
    val isInstalled: Boolean = false,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val completionStatus: String = "",
    val genres: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val features: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val userScore: Int? = null,
    val lastActivity: String = ""
) {
    val playtimeText: String
        get() {
            val minutes = playtimeSeconds / 60
            return when {
                minutes <= 0 -> "Never played"
                minutes < 60 -> "$minutes min"
                else -> "${minutes / 60}h ${minutes % 60}m"
            }
        }
}

data class GameLink(
    val name: String = "",
    val url: String = ""
)

/** Full details from GET /api/games/{id} — every field the API exposes. */
data class GameDetails(
    val id: String = "",
    val title: String = "",
    val sortingName: String = "",
    val source: String = "",
    val description: String = "",
    val notes: String = "",
    val developers: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val series: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val features: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val ageRatings: List<String> = emptyList(),
    val regions: List<String> = emptyList(),
    val links: List<GameLink> = emptyList(),
    val completionStatus: String = "",
    val releaseDate: String = "",
    val added: String = "",
    val modified: String = "",
    val coverUrl: String = "",
    val backgroundUrl: String = "",
    val playtimeSeconds: Long = 0,
    val playCount: Long = 0,
    val lastActivity: String = "",
    val isInstalled: Boolean = false,
    val installDirectory: String = "",
    val installSize: Long? = null,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val communityScore: Int? = null,
    val criticScore: Int? = null,
    val userScore: Int? = null,
    val gameId: String = "",
    val version: String = "",
    val hasCover: Boolean = false
)

data class AchievementInfo(
    val name: String = "",
    val description: String = "",
    val unlocked: Boolean = false,
    val dateUnlocked: String = "",
    val percent: Double? = null
)

data class Achievements(
    val available: Boolean = false,
    val total: Int = 0,
    val unlocked: Int = 0,
    val locked: Int = 0,
    val items: List<AchievementInfo> = emptyList()
)

data class PlaySession(
    val date: String = "",
    val elapsedSeconds: Long = 0,
    val action: String = ""
)

data class Activity(
    val available: Boolean = false,
    val totalSeconds: Long = 0,
    val totalHours: Double = 0.0,
    val sessionCount: Int = 0,
    val sessions: List<PlaySession> = emptyList()
)

data class SourceCount(val name: String = "", val count: Int = 0)
data class StatusCount(val name: String = "", val count: Int = 0)
data class GenreCount(val name: String = "", val count: Int = 0)
data class RecentGame(val id: String = "", val title: String = "", val lastPlayed: String = "")

/** Snapshot of GET /api/stats. */
data class StatsSnapshot(
    val totalGames: Int = 0,
    val installed: Int = 0,
    val notInstalled: Int = 0,
    val hidden: Int = 0,
    val favorite: Int = 0,
    val uncategorized: Int = 0,
    val totalPlaytimeHours: Double = 0.0,
    val bySource: List<SourceCount> = emptyList(),
    val byStatus: List<StatusCount> = emptyList(),
    val topGenres: List<GenreCount> = emptyList(),
    val recentlyPlayed: List<RecentGame> = emptyList()
)
