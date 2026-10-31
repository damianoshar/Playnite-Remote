package com.gamevault.unified.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gamevault.unified.data.model.Game
import com.gamevault.unified.data.model.RecentGame
import com.gamevault.unified.ui.components.Section
import com.gamevault.unified.ui.components.fmtDateTime
import com.gamevault.unified.viewmodel.LibraryViewModel

/** Library analytics from GET /api/stats (cached for offline use). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: LibraryViewModel) {
    val state by vm.ui.collectAsState()
    val stats = state.stats

    // Zero insets here: the outer AppNav Scaffold already pads for the status
    // and navigation bars. Consuming them again in this nested Scaffold
    // stacked a phantom 104px strip above the bottom bar (plus a dead gap
    // under the status bar from the TopAppBar's own inset padding).
    Scaffold(
        topBar = { TopAppBar(title = { Text("Statistics") }, windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)) },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)
    ) { padding ->
        if (stats == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("No stats yet — connect to the PC and sync.") }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            if (state.isOffline) {
                Text(
                    "Offline — stats from ${state.lastSynced}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Games", "${stats.totalGames}", Modifier.weight(1f))
                StatCard("Installed", "${stats.installed}", Modifier.weight(1f))
                StatCard("Favorites", "${stats.favorite}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Hours played", "${stats.totalPlaytimeHours}", Modifier.weight(1f))
                StatCard("Uncategorized", "${stats.uncategorized}", Modifier.weight(1f))
                StatCard("Hidden", "${stats.hidden}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))

            Section("By source") {
                BarList(
                    items = stats.bySource.map { it.name to it.count },
                    total = stats.totalGames
                )
            }
            Section("Top genres") {
                BarList(
                    items = stats.topGenres.map { it.name to it.count },
                    total = stats.topGenres.firstOrNull()?.count ?: 1
                )
            }
            Section("By completion status") {
                BarList(
                    items = stats.byStatus.map { it.name to it.count },
                    total = stats.totalGames
                )
            }
            Section("Recently played") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    stats.recentlyPlayed.forEach { recent ->
                        RecentRow(recent = recent, titleFor = { id ->
                            state.games.firstOrNull { it.id == id }?.title ?: recent.title
                        }) {
                            vm.openDetails(Game(id = recent.id, title = recent.title))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BarList(items: List<Pair<String, Int>>, total: Int) {
    if (items.isEmpty()) {
        Text("—", style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (name, count) ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        name.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text("$count", style = MaterialTheme.typography.bodySmall)
                }
                LinearProgressIndicator(
                    progress = { if (total > 0) count.toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RecentRow(recent: RecentGame, titleFor: (String) -> String, onClick: () -> Unit) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                titleFor(recent.id),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                fmtDateTime(recent.lastPlayed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
