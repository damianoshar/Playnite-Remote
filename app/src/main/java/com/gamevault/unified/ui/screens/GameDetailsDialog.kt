package com.gamevault.unified.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.gamevault.unified.ui.components.ChipRow
import com.gamevault.unified.ui.components.InfoRow
import com.gamevault.unified.ui.components.LabeledChips
import com.gamevault.unified.ui.components.Section
import com.gamevault.unified.ui.components.fmtBytes
import com.gamevault.unified.ui.components.fmtDateTime
import com.gamevault.unified.ui.components.fmtDurationSecs
import com.gamevault.unified.viewmodel.LibraryViewModel

/**
 * Full-screen game details. Rendered above all tabs from AppNav so it can
 * be opened from the library list and from stats "recently played".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailsDialog(vm: LibraryViewModel) {
    val state by vm.ui.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    if (!state.detailsLoading && state.selectedDetails == null) return

    Dialog(
        onDismissRequest = { vm.closeDetails() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val details = state.selectedDetails
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(details?.title ?: "Loading…") },
                        navigationIcon = {
                            IconButton(onClick = { vm.closeDetails() }) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (details != null) {
                                IconButton(
                                    onClick = {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                        vm.toggleFavorite()
                                    },
                                    enabled = !state.favoriteBusy
                                ) {
                                    Icon(
                                        if (details.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                        contentDescription = "Toggle favorite",
                                        tint = if (details.favorite) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, details.title)
                                        putExtra(android.content.Intent.EXTRA_TEXT,
                                            "${details.title} (${details.source}) - ${details.playtimeSeconds / 3600}h played, score ${details.userScore ?: "?"}")
                                    }
                                    context.startActivity(android.content.Intent.createChooser(share, "Share game"))
                                }) {
                                    Icon(Icons.Filled.Share, contentDescription = "Share")
                                }
                            }
                        }
                    )
                }
            ) { padding ->
                if (details == null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                    return@Scaffold
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (details.backgroundUrl.isNotBlank()) {
                        AsyncImage(
                            model = details.backgroundUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    Section("Overview") {
                        InfoRow("Source", details.source)
                        InfoRow("Playtime", fmtDurationSecs(details.playtimeSeconds))
                        InfoRow("Sessions", details.playCount.takeIf { it > 0 }?.toString() ?: "")
                        InfoRow("Last played", fmtDateTime(details.lastActivity))
                        InfoRow("Status", details.completionStatus)
                        InfoRow("Release date", details.releaseDate)
                        InfoRow("Installed", if (details.isInstalled) "Yes — on PC" else "")
                        InfoRow("Install size", fmtBytes(details.installSize))
                        InfoRow("Version", details.version)
                        InfoRow("Store ID", details.gameId)
                        InfoRow("Added", fmtDateTime(details.added))
                        InfoRow("Modified", fmtDateTime(details.modified))
                        if (details.hidden) InfoRow("Hidden", "Yes")
                    }

                    val scores = buildList {
                        details.communityScore?.let { add("Community $it") }
                        details.criticScore?.let { add("Critic $it") }
                        details.userScore?.let { add("Mine ${it}") }
                    }
                    if (scores.isNotEmpty()) {
                        Section("Scores") { ChipRow(scores) }
                    }

                    if (details.description.isNotBlank()) {
                        Section("Description") {
                            Text(
                                details.description.stripHtml(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    if (details.notes.isNotBlank()) {
                        Section("My notes") {
                            Text(details.notes, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Section("People & series") {
                        LabeledChips("Developers", details.developers)
                        LabeledChips("Publishers", details.publishers)
                        LabeledChips("Series", details.series)
                    }

                    Section("Classification") {
                        LabeledChips("Genres", details.genres)
                        LabeledChips("Categories", details.categories)
                        LabeledChips("Tags", details.tags)
                        LabeledChips("Features", details.features)
                        LabeledChips("Platforms", details.platforms)
                        LabeledChips("Age ratings", details.ageRatings)
                        LabeledChips("Regions", details.regions)
                    }

                    if (details.installDirectory.isNotBlank()) {
                        Section("Install path") {
                            Text(details.installDirectory, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (details.links.isNotEmpty()) {
                        Section("Links") {
                            val uri = LocalUriHandler.current
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                details.links.forEach { link ->
                                    OutlinedButton(onClick = { uri.openUri(link.url) }) {
                                        Text(link.name.ifBlank { link.url })
                                    }
                                }
                            }
                        }
                    }

                    Section("Notes") {
                        val d = details
                        if (d != null) {
                            var note by remember(d.id, state.notes[d.id]) { mutableStateOf(state.notes[d.id].orEmpty()) }
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it },
                                label = { Text("Your note") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(onClick = { vm.setNote(d.id, note) }) { Text("Save note") }
                        }
                    }

                    AchievementsSection(vm)
                    ActivitySection(vm)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (details.isInstalled) {
                            Button(onClick = { vm.launchOnPc(details.id) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Launch on PC")
                            }
                        }
                        OutlinedButton(onClick = { vm.closeDetails() }) { Text("Close") }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AchievementsSection(vm: LibraryViewModel) {
    val state by vm.ui.collectAsState()
    val data = state.selectedAchievements ?: return
    if (!data.available) return
    Section("Achievements (${data.unlocked}/${data.total})") {
        val ratio = if (data.total > 0) data.unlocked.toFloat() / data.total else 0f
        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data.items.forEach { item ->
                Column {
                    Text(
                        (if (item.unlocked) "✓ " else "· ") + item.name.ifBlank { "Hidden" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.unlocked) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val sub = buildList {
                        if (item.description.isNotBlank()) add(item.description)
                        item.percent?.let { add("${it.toInt()}%") }
                        if (item.dateUnlocked.isNotBlank()) add(fmtDateTime(item.dateUnlocked))
                    }.joinToString(" • ")
                    if (sub.isNotBlank()) {
                        Text(sub, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivitySection(vm: LibraryViewModel) {
    val state by vm.ui.collectAsState()
    val data = state.selectedActivity ?: return
    if (!data.available) return
    Section("Play sessions (${data.sessionCount}, ${data.totalHours}h)") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            data.sessions.take(20).forEach { session ->
                Text(
                    "${fmtDateTime(session.date)} • ${fmtDurationSecs(session.elapsedSeconds)}" +
                        (if (session.action.isNotBlank()) " • ${session.action}" else ""),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun String.stripHtml(): String =
    replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
