package com.gamevault.unified.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.network.HttpException
import com.gamevault.unified.data.local.ViewMode
import com.gamevault.unified.data.model.Game
import com.gamevault.unified.viewmodel.LibraryViewModel
import com.gamevault.unified.viewmodel.SortMode
import kotlin.math.roundToInt

private fun sortLabel(mode: SortMode): String = when (mode) {
    SortMode.NAME -> "Name"
    SortMode.PLAYTIME -> "Playtime"
    SortMode.PLAY_COUNT -> "Play count"
    SortMode.LAST_PLAYED -> "Last played"
    SortMode.SCORE -> "My score"
    SortMode.SOURCE -> "Source"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenServer: () -> Unit,
    onOpenStats: () -> Unit,
    // No default: a caller that forgets this must fail compilation LOUDLY,
    // not silently ship a bar that never hides.
    onScrollVisibility: (Boolean) -> Unit
) {
    val state by vm.ui.collectAsState()
    val snack = remember { SnackbarHostState() }
    var showFilters by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Filter+sort over thousands of games runs once per games/filters change,
    // not on every unrelated recomposition (e.g. loading spinner toggles).
    val visible = remember(state.games, state.filters, state.page, state.pageSize) { state.visibleGames }
    val filteredCount = remember(state.games, state.filters) { state.filteredGames.size }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    // Viewport-gated loading: only items near the screen may start image
    // requests. Anything scrolled past never fires — this is what keeps the
    // PC-side plugin from drowning during fast scrolls.
    val loadRange by remember(state.viewMode, state.gridSpan) {
        derivedStateOf {
            if (state.viewMode == ViewMode.LIST) {
                val vis = listState.layoutInfo.visibleItemsInfo
                if (vis.isEmpty()) IntRange.EMPTY
                else (vis.first().index - 6).coerceAtLeast(0)..(vis.last().index + 12)
            } else {
                val vis = gridState.layoutInfo.visibleItemsInfo
                if (vis.isEmpty()) IntRange.EMPTY
                else {
                    val back = state.gridSpan * 2
                    val fwd = state.gridSpan * 4
                    (vis.first().index - back).coerceAtLeast(0)..(vis.last().index + fwd)
                }
            }
        }
    }
    LaunchedEffect(state.page, state.viewMode, state.filters) {
        listState.scrollToItem(0)
        gridState.scrollToItem(0)
    }

    // Bottom-bar visibility, driven by a snapshotFlow collector (NOT derivedStateOf:
    // writing snapshot state inside a derived calculation resubscribes to
    // itself and flips the result on unrelated recompositions).
    // Direction uses the first visible item's KEY as guard: when a header
    // (spinner/banner) inserts at index 0, LazyColumn preserves content by
    // shifting the anchor 0 -> 1 with the SAME key visible — that is not a
    // scroll, so the offset decides. Only a changed key consults the index.
    // Bar hides on the first downward read, shows after 3 consecutive upward
    // reads, and scroll stops deliberately change nothing.
    LaunchedEffect(listState, gridState, state.viewMode) {
        var lastIndex = 0
        var lastOffset = 0
        var lastKey: Any? = null
        var upStreak = 3
        var lastSent = true
        snapshotFlow {
            if (state.viewMode == ViewMode.LIST) {
                Triple(
                    listState.firstVisibleItemIndex,
                    listState.firstVisibleItemScrollOffset,
                    listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key
                )
            } else {
                Triple(
                    gridState.firstVisibleItemIndex,
                    gridState.firstVisibleItemScrollOffset,
                    gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.key
                )
            }
        }.distinctUntilChanged().collect { (index, offset, key) ->
            val up = if (key != null && key == lastKey) offset <= lastOffset
            else if (index != lastIndex) lastIndex > index
            else offset <= lastOffset
            lastIndex = index
            lastOffset = offset
            lastKey = key
            // Down-reads only count while a scroll is actually in progress:
            // header insertions (banners) land the viewport on a new key with
            // no finger down, which is not a scroll. Up-reads stay ungated
            // so page turns (scrollToItem) still reshow the bar.
            val inProgress = if (state.viewMode == ViewMode.LIST)
                listState.isScrollInProgress else gridState.isScrollInProgress
            if (!up && !inProgress) return@collect
            val show = if (!up) {
                upStreak = 0
                false
            } else {
                upStreak += 1
                upStreak >= 3
            }
            if (show != lastSent) {
                lastSent = show
                onScrollVisibility(show)
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { snack.showSnackbar(it) }
    }
    LaunchedEffect(state.launchMessage) {
        state.launchMessage?.let { snack.showSnackbar(it) }
    }

    // Image circuit breaker: while tripped, no row may start a request.
    val paused = System.currentTimeMillis() < state.imagesPausedUntil
    // Without a configured server there is nothing to authenticate cover
    // requests with — firing them would only produce 401 bursts.
    val canLoadCovers = state.server != null && !paused

    // Header visibility flags (search/sort/chips live INSIDE the scrolling
    // list so they hide on scroll and take zero reserved space).
    val showOffline = state.isOffline && state.games.isNotEmpty()
    val showPausedBanner = paused && state.games.isNotEmpty()
    val showChips = state.filters.activeCount > 0
    val showSlider = state.viewMode == ViewMode.GRID
    val showMiniSpinner = state.isLoading
    val showFooter = state.pageSize > 0
    val headerCount = (if (showOffline) 1 else 0) + (if (showPausedBanner) 1 else 0) +
        1 + 1 + (if (showChips) 1 else 0) + (if (showSlider) 1 else 0) +
        (if (showMiniSpinner) 1 else 0)
    // Viewport load window translated into game-index space (layout indices
    // include the header items above).
    val gameRange = remember(loadRange, headerCount) {
        (loadRange.first - headerCount).coerceAtLeast(0)..(loadRange.last - headerCount)
    }

    // Zero insets here: the outer AppNav Scaffold already pads for the status
    // and navigation bars. Consuming them again in this nested Scaffold
    // stacked a phantom 104px strip above the bottom bar (plus a dead gap
    // under the status bar from the TopAppBar's own inset padding).
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                title = { Text(if (state.statsSummary.isBlank()) "Library" else "Library (${state.statsSummary})") },
                actions = {
                    IconButton(onClick = {
                        vm.setViewMode(
                            if (state.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                        )
                    }) {
                        Icon(
                            if (state.viewMode == ViewMode.LIST) Icons.Filled.GridView else Icons.Filled.ViewList,
                            contentDescription = "Toggle list/grid"
                        )
                    }
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Statistics")
                    }
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(
                            badge = {
                                val n = state.filters.activeCount
                                if (n > 0) Badge { Text("$n") }
                            }
                        ) {
                            Icon(Icons.Filled.FilterList, contentDescription = "Filters")
                        }
                    }
                    IconButton(onClick = { vm.refreshLibrary() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        // Zero: the outer AppNav Scaffold owns the system insets (see above).
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            if (state.server == null && state.games.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No PC server configured.", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onOpenServer) { Text("Connect to Playnite") }
                    }
                }
                return@Column
            }


            if (state.isLoading && state.games.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No games match these filters.")
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { vm.clearFilters() }) { Text("Clear filters") }
                    }
                }
            } else {
                if (state.viewMode == ViewMode.LIST) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
                    ) {
                        if (showOffline) {
                            item(key = "banner-offline") { OfflineBanner(state.lastSynced) }
                        }
                        if (showPausedBanner) {
                            item(key = "banner-paused") { PausedBanner() }
                        }
                        item(key = "search") { SearchField(vm) }
                        item(key = "sortrow") { SortShowRow(vm) }
                        if (showChips) {
                            item(key = "chips") { ActiveFilterChips(vm) }
                        }
                        if (showSlider) {
                            item(key = "slider") { GridSliderRow(vm) }
                        }
                        if (showMiniSpinner) {
                            item(key = "spin") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                            }
                        }
                        items(visible.size, key = { i -> visible[i].id }) { i ->
                            val game = visible[i]
                            GameRow(
                                game = game,
                                artMissing = game.id in state.artlessIds,
                                loadImage = canLoadCovers && (state.retainImages || i in gameRange),
                                onCoverResult = { ok, code -> vm.noteCoverResult(game.id, ok, code) },
                                onToggleFavorite = { vm.toggleFavorite(game) },
                                onClick = { vm.openDetails(game) }
                            )
                        }
                        if (showFooter) {
                            item(key = "footer") { FooterPager(vm, filteredCount) }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(state.gridSpan),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
                    ) {
                        if (showOffline) {
                            item(key = "banner-offline", span = { GridItemSpan(maxLineSpan) }) {
                                OfflineBanner(state.lastSynced)
                            }
                        }
                        if (showPausedBanner) {
                            item(key = "banner-paused", span = { GridItemSpan(maxLineSpan) }) {
                                PausedBanner()
                            }
                        }
                        item(key = "search", span = { GridItemSpan(maxLineSpan) }) { SearchField(vm) }
                        item(key = "sortrow", span = { GridItemSpan(maxLineSpan) }) { SortShowRow(vm) }
                        if (showChips) {
                            item(key = "chips", span = { GridItemSpan(maxLineSpan) }) { ActiveFilterChips(vm) }
                        }
                        if (showSlider) {
                            item(key = "slider", span = { GridItemSpan(maxLineSpan) }) { GridSliderRow(vm) }
                        }
                        if (showMiniSpinner) {
                            item(key = "spin", span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                            }
                        }
                        items(visible.size, key = { i -> visible[i].id }) { i ->
                            val game = visible[i]
                            GameGridCard(
                                game = game,
                                artMissing = game.id in state.artlessIds,
                                loadImage = canLoadCovers && (state.retainImages || i in gameRange),
                                onCoverResult = { ok, code -> vm.noteCoverResult(game.id, ok, code) },
                                onToggleFavorite = { vm.toggleFavorite(game) },
                                onClick = { vm.openDetails(game) }
                            )
                        }
                        if (showFooter) {
                            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                                FooterPager(vm, filteredCount)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = sheetState
        ) {
            FilterSheet(vm = vm, onClose = { showFilters = false })
        }
    }
}

private fun Set<String>.toggled(value: String) = if (contains(value)) this - value else this + value

@Composable
private fun OfflineBanner(lastSynced: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Text(
            "Offline — library from $lastSynced. Reconnect to the PC to refresh.",
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PausedBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            "Image loading paused — the PC is struggling. Retrying shortly.",
            modifier = Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SearchField(vm: LibraryViewModel) {
    val state by vm.ui.collectAsState()
    OutlinedTextField(
        value = state.filters.query,
        onValueChange = { vm.updateFilters(state.filters.copy(query = it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search games…") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortShowRow(vm: LibraryViewModel) {
    val state by vm.ui.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var sortExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = sortExpanded,
            onExpandedChange = { sortExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = sortLabel(state.filters.sort),
                onValueChange = {},
                readOnly = true,
                label = { Text("Sort") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false }
            ) {
                SortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(mode)) },
                        onClick = {
                            vm.updateFilters(state.filters.copy(sort = mode))
                            sortExpanded = false
                        }
                    )
                }
            }
        }
        var showExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = showExpanded,
            onExpandedChange = { showExpanded = it },
            modifier = Modifier.width(118.dp)
        ) {
            OutlinedTextField(
                value = if (state.pageSize <= 0) "All" else "${state.pageSize}",
                onValueChange = {},
                readOnly = true,
                label = { Text("Show") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = showExpanded,
                onDismissRequest = { showExpanded = false }
            ) {
                listOf(0, 50, 100, 200).forEach { size ->
                    DropdownMenuItem(
                        text = { Text(if (size == 0) "All" else "$size / page") },
                        onClick = { vm.setPageSize(size); showExpanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun GridSliderRow(vm: LibraryViewModel) {
    val state by vm.ui.collectAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Columns",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = state.gridSpan.toFloat(),
            onValueChange = { vm.setGridSpan(it.roundToInt()) },
            valueRange = 2f..5f,
            steps = 2,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${state.gridSpan}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FooterPager(vm: LibraryViewModel, filteredCount: Int) {
    val state by vm.ui.collectAsState()
    if (state.pageSize <= 0) return
    Column {
        val from = state.safePage * state.pageSize + 1
        val to = (state.safePage * state.pageSize + state.pageSize).coerceAtMost(filteredCount)
        Text(
            "Showing $from–$to of $filteredCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        if (state.totalPages > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { vm.setPage(state.page - 1) },
                    enabled = state.page > 0
                ) { Text("‹ Prev") }
                Text(
                    "${state.safePage + 1} / ${state.totalPages}",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    onClick = { vm.setPage(state.page + 1) },
                    enabled = state.page < state.totalPages - 1
                ) { Text("Next ›") }
            }
        }
    }
}

@Composable
private fun ActiveFilterChips(vm: LibraryViewModel) {
    val state by vm.ui.collectAsState()
    val f = state.filters
    val chips = mutableListOf<Pair<String, () -> Unit>>()
    f.selectedSources.forEach { chips += it to { vm.updateFilters(f.copy(selectedSources = f.selectedSources - it)) } }
    f.selectedGenres.forEach { chips += it to { vm.updateFilters(f.copy(selectedGenres = f.selectedGenres - it)) } }
    f.selectedCategories.forEach { chips += it to { vm.updateFilters(f.copy(selectedCategories = f.selectedCategories - it)) } }
    f.selectedTags.forEach { chips += it to { vm.updateFilters(f.copy(selectedTags = f.selectedTags - it)) } }
    f.selectedFeatures.forEach { chips += it to { vm.updateFilters(f.copy(selectedFeatures = f.selectedFeatures - it)) } }
    f.selectedPlatforms.forEach { chips += it to { vm.updateFilters(f.copy(selectedPlatforms = f.selectedPlatforms - it)) } }
    f.selectedStatuses.forEach { chips += it to { vm.updateFilters(f.copy(selectedStatuses = f.selectedStatuses - it)) } }
    if (f.installedOnly) chips += "Installed" to { vm.updateFilters(f.copy(installedOnly = false)) }
    if (f.favoriteOnly) chips += "Favorites" to { vm.updateFilters(f.copy(favoriteOnly = false)) }
    if (f.showHidden) chips += "Incl. hidden" to { vm.updateFilters(f.copy(showHidden = false)) }
    if (f.uncategorizedOnly) chips += "Uncategorized" to { vm.updateFilters(f.copy(uncategorizedOnly = false)) }
    if (f.unplayedOnly) chips += "Unplayed" to { vm.updateFilters(f.copy(unplayedOnly = false)) }
    f.minHours?.let { chips += "≥${it}h" to { vm.updateFilters(f.copy(minHours = null)) } }
    f.maxHours?.let { chips += "≤${it}h" to { vm.updateFilters(f.copy(maxHours = null)) } }
    f.minScore?.let { chips += "Score≥$it" to { vm.updateFilters(f.copy(minScore = null)) } }
    f.maxScore?.let { chips += "Score≤$it" to { vm.updateFilters(f.copy(maxScore = null)) } }

    if (chips.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(chips) { (label, clear) ->
            FilterChip(
                selected = true,
                onClick = clear,
                label = { Text(label, maxLines = 1) },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(vm: LibraryViewModel, onClose: () -> Unit) {
    val state by vm.ui.collectAsState()
    val f = state.filters
    val appContext = LocalContext.current.applicationContext
    val matchCount = remember(state.games, state.filters) { state.visibleGames.size }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Filters", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        MultiFacetDropdown("Source", f.selectedSources, state.sources,
            { vm.updateFilters(f.copy(selectedSources = f.selectedSources.toggled(it))) },
            { vm.updateFilters(f.copy(selectedSources = emptySet())) })
        MultiFacetDropdown("Genre", f.selectedGenres, state.genreOptions,
            { vm.updateFilters(f.copy(selectedGenres = f.selectedGenres.toggled(it))) },
            { vm.updateFilters(f.copy(selectedGenres = emptySet())) })
        MultiFacetDropdown("Category", f.selectedCategories, state.categoryOptions,
            { vm.updateFilters(f.copy(selectedCategories = f.selectedCategories.toggled(it))) },
            { vm.updateFilters(f.copy(selectedCategories = emptySet())) })
        MultiFacetDropdown("Tag", f.selectedTags, state.tagOptions,
            { vm.updateFilters(f.copy(selectedTags = f.selectedTags.toggled(it))) },
            { vm.updateFilters(f.copy(selectedTags = emptySet())) })
        MultiFacetDropdown("Feature", f.selectedFeatures, state.featureOptions,
            { vm.updateFilters(f.copy(selectedFeatures = f.selectedFeatures.toggled(it))) },
            { vm.updateFilters(f.copy(selectedFeatures = emptySet())) })
        MultiFacetDropdown("Platform", f.selectedPlatforms, state.platformOptions,
            { vm.updateFilters(f.copy(selectedPlatforms = f.selectedPlatforms.toggled(it))) },
            { vm.updateFilters(f.copy(selectedPlatforms = emptySet())) })
        MultiFacetDropdown("Completion status", f.selectedStatuses, state.statusOptions,
            { vm.updateFilters(f.copy(selectedStatuses = f.selectedStatuses.toggled(it))) },
            { vm.updateFilters(f.copy(selectedStatuses = emptySet())) })

        Spacer(Modifier.height(4.dp))
        ToggleRow("Installed only", f.installedOnly) { vm.updateFilters(f.copy(installedOnly = it)) }
        ToggleRow("Favorites only", f.favoriteOnly) { vm.updateFilters(f.copy(favoriteOnly = it)) }
        ToggleRow("Include hidden", f.showHidden) { vm.updateFilters(f.copy(showHidden = it)) }
        ToggleRow("Uncategorized only", f.uncategorizedOnly) { vm.updateFilters(f.copy(uncategorizedOnly = it)) }
        ToggleRow("Unplayed only", f.unplayedOnly) { vm.updateFilters(f.copy(unplayedOnly = it)) }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = f.minHours?.toString() ?: "",
                onValueChange = { vm.updateFilters(f.copy(minHours = it.toIntOrNull())) },
                label = { Text("Min hours") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = f.maxHours?.toString() ?: "",
                onValueChange = { vm.updateFilters(f.copy(maxHours = it.toIntOrNull())) },
                label = { Text("Max hours") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(4.dp))
        Text("Score (0-100)", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = f.minScore?.toString() ?: "",
                onValueChange = { vm.updateFilters(f.copy(minScore = it.toIntOrNull())) },
                label = { Text("Min score") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = f.maxScore?.toString() ?: "",
                onValueChange = { vm.updateFilters(f.copy(maxScore = it.toIntOrNull())) },
                label = { Text("Max score") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        Text("Display", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        var themeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = themeExpanded,
            onExpandedChange = { themeExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = com.gamevault.unified.ui.theme.appThemeFromName(state.appThemeName).label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Theme") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = themeExpanded,
                onDismissRequest = { themeExpanded = false }
            ) {
                com.gamevault.unified.ui.theme.AppTheme.entries.forEach { theme ->
                    DropdownMenuItem(
                        text = { Text(theme.label) },
                        onClick = { vm.setAppTheme(theme.name); themeExpanded = false }
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        ToggleRow("Keep all images", state.retainImages) {
            vm.setRetainImages(it)
            if (it) vm.warmCovers(appContext)
        }
        if (state.warmingCovers) {
            Text("Caching covers in the background…", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                "Loads every cover in the current view onto the phone and keeps " +
                    "rows loaded while scrolling, so going back up never reloads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.clearFilters() }, modifier = Modifier.weight(1f)) {
                Text("Clear all")
            }
            Button(onClick = onClose, modifier = Modifier.weight(1f)) {
                Text("Show $matchCount")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiFacetDropdown(
    label: String,
    selected: Set<String>,
    options: List<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit
) {
    if (options.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val display = when {
        selected.isEmpty() -> "Any"
        selected.size <= 2 -> selected.sorted().joinToString(", ")
        else -> "${selected.size} selected"
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        // Menu stays open while ticking boxes so several values can be picked.
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (selected.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Clear selection") },
                    onClick = { onClear(); expanded = false }
                )
            }
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = option in selected,
                                onCheckedChange = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    onClick = { onToggle(option) }
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun GameGridCard(
    game: Game,
    artMissing: Boolean,
    loadImage: Boolean,
    onCoverResult: (Boolean, Int?) -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Card(onClick = onClick) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    game.title.take(2).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                AsyncImage(
                    model = if (!loadImage || artMissing) null else game.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = { onCoverResult(true, null) },
                    onError = { onCoverResult(false, (it.result.throwable as? HttpException)?.response?.code) }
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                ) {
                    Icon(
                        if (game.favorite) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = "Toggle favorite",
                        tint = if (game.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (game.favorite) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(18.dp)
                    )
                }
            }
            Column(Modifier.padding(6.dp)) {
                Text(
                    game.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    game.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GameRow(
    game: Game,
    artMissing: Boolean,
    loadImage: Boolean,
    onCoverResult: (Boolean, Int?) -> Unit,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Initials sit behind the cover; if the PC has no cover art
            // (404 from /cover), the initials show through.
            Box(
                modifier = Modifier.size(96.dp, 54.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    game.title.take(2).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                AsyncImage(
                    model = if (!loadImage || artMissing) null else game.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp, 54.dp),
                    contentScale = ContentScale.Crop,
                    onSuccess = { onCoverResult(true, null) },
                    onError = { onCoverResult(false, (it.result.throwable as? HttpException)?.response?.code) }
                )
            }
            val view = LocalView.current
            IconButton(onClick = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onToggleFavorite()
            }, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (game.favorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = "Toggle favorite",
                    tint = if (game.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    game.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${game.source} • ${game.playtimeText}${if (game.isInstalled) " • Installed" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (game.favorite) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
