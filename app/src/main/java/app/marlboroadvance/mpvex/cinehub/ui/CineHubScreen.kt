package app.marlboroadvance.mpvex.cinehub.ui

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import app.marlboroadvance.mpvex.cinehub.model.*
import app.marlboroadvance.mpvex.cinehub.data.*
import app.marlboroadvance.mpvex.youtube.data.InvidiousClient
import app.marlboroadvance.mpvex.youtube.model.YoutubeVideo
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CineHubScreen(
    moviesList: List<MovieItem>,
    tvShowsList: List<TvShowItem>,
    onPlayRequested: (filePath: String, cleanTitle: String) -> Unit,
    onUpdateMediaInfo: (thumbnail: String, title: String, author: String, description: String, metadata: Map<String, String>) -> Unit = { _, _, _, _, _ -> }
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Movies", "TV Shows")
    
    var selectedMovie by remember { mutableStateOf<MovieItem?>(null) }
    var selectedTvShow by remember { mutableStateOf<TvShowItem?>(null) }
    
    var activeActorLookup by remember { mutableStateOf<ActorItem?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    
    // Pickers State
    var showPosterPickerFor by remember { mutableStateOf<String?>(null) }
    var availableArtworks by remember { mutableStateOf<TMDBImagesResponse?>(null) }
    
    var onlineMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var onlineTvShows by remember { mutableStateOf<List<TvShowItem>>(emptyList()) }
    var isOnlineLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val gridColumnCount = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 3

    LaunchedEffect(tabIndex) {
        if (onlineMovies.isEmpty() || onlineTvShows.isEmpty()) {
            isOnlineLoading = true
            scope.launch {
                try {
                    onlineMovies = CineCloudRepoClient.fetchOnlineMovies(context)
                    onlineTvShows = CineCloudRepoClient.fetchOnlineTvShows(context)
                } catch (e: Exception) {} finally {
                    isOnlineLoading = false
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BrowserTopBar(
                    title = "CineHub Engine",
                    isInSelectionMode = false,
                    selectedCount = 0,
                    totalCount = moviesList.size + tvShowsList.size,
                    onCancelSelection = {},
                    isHomeScreen = true,
                    onSearchClick = {},
                    actions = {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Engine Settings", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                )

                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 1.dp) {
                    TabRow(selectedTabIndex = tabIndex, containerColor = Color.Transparent, divider = {}) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = tabIndex == index,
                                onClick = { tabIndex = index },
                                text = { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (tabIndex == 0) "My Local Movies" else "My Local TV Series",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { /* Trigger background rescan via ViewModel */ }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                if (tabIndex == 0) {
                    if (moviesList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("No local files found inside target folders.", fontSize = 13.sp, color = Color.Gray)
                        }
                    } else {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(moviesList) { movie ->
                                Box(modifier = Modifier.width(135.dp)) {
                                    CineHubGridCard(title = movie.title, genre = movie.genre, rating = movie.userRating, posterPath = movie.posterPath, watchProgress = movie.watchProgress) {
                                        selectedMovie = movie
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (tvShowsList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("No local files found inside target folders.", fontSize = 13.sp, color = Color.Gray)
                        }
                    } else {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(tvShowsList) { show ->
                                Box(modifier = Modifier.width(135.dp)) {
                                    CineHubGridCard(title = show.title, genre = show.genre, rating = show.userRating, posterPath = show.posterPath, watchProgress = show.watchProgress) {
                                        selectedTvShow = show
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Trending Online Releases",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp)
                )
            }

            if (isOnlineLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                    }
                }
            } else {
                if (tabIndex == 0) {
                    val chunkedMovies = onlineMovies.chunked(gridColumnCount)
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (rowItems in chunkedMovies) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (movieItem in rowItems) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            CineHubGridCard(title = movieItem.title, genre = movieItem.genre, rating = movieItem.userRating, posterPath = movieItem.posterPath) {
                                                scope.launch {
                                                    val rawId = movieItem.videoFilePath.substringAfter("cnc_stream:").substringBefore(":")
                                                    val platformCode = movieItem.videoFilePath.substringAfterLast(":")
                                                    val directM3u8 = CineCloudRepoClient.resolveDirectStreamUrl(rawId, platformCode)
                                                    
                                                    onUpdateMediaInfo(
                                                        movieItem.posterPath ?: "", movieItem.title, "Cloud Stream", movieItem.plot,
                                                        mapOf("Genre" to movieItem.genre, "Rating" to movieItem.userRating.toString())
                                                    )

                                                    if (!directM3u8.isNullOrBlank()) onPlayRequested(directM3u8, movieItem.title)
                                                    else onPlayRequested("https://net52.cc/mobile/player.php?id=$rawId", movieItem.title)
                                                }
                                            }
                                        }
                                    }
                                    repeat(gridColumnCount - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                } else {
                    val chunkedTvShows = onlineTvShows.chunked(gridColumnCount)
                    item {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (rowItems in chunkedTvShows) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (tvShowItem in rowItems) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            CineHubGridCard(title = tvShowItem.title, genre = tvShowItem.genre, rating = tvShowItem.userRating, posterPath = tvShowItem.posterPath) {
                                                scope.launch {
                                                    val rawId = tvShowItem.folderPath.substringAfter("cnc_tv:").substringBefore(":")
                                                    val platformCode = tvShowItem.folderPath.substringAfterLast(":")
                                                    val directM3u8 = CineCloudRepoClient.resolveDirectStreamUrl(rawId, platformCode)
                                                    
                                                    onUpdateMediaInfo(
                                                        tvShowItem.posterPath ?: "", tvShowItem.title, "Cloud Network", tvShowItem.plot,
                                                        mapOf("Genre" to tvShowItem.genre, "Rating" to tvShowItem.userRating.toString())
                                                    )

                                                    if (!directM3u8.isNullOrBlank()) onPlayRequested(directM3u8, tvShowItem.title)
                                                    else onPlayRequested("https://net52.cc/mobile/player.php?id=$rawId", tvShowItem.title)
                                                }
                                            }
                                        }
                                    }
                                    repeat(gridColumnCount - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- MOVIES DETAIL OVERLAY (HERO UPGRADE) ---
        selectedMovie?.let { movie ->
            var isRefreshing by remember { mutableStateOf(false) }

            ModalBottomSheet(
                onDismissRequest = { selectedMovie = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
                    // HERO ARTWORK BLOCK
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                            AsyncImage(
                                model = movie.backdropPath ?: movie.posterPath,
                                contentDescription = "Backdrop",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        scope.launch {
                                            if (movie.tmdbId.isNotBlank()) {
                                                availableArtworks = CineOnlineScraper.fetchArtworkOptions(movie.tmdbId, "movie")
                                                showPosterPickerFor = "backdrop"
                                            }
                                        }
                                    }
                                )
                            )
                            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))))
                            
                            if (movie.logoPath != null) {
                                AsyncImage(
                                    model = movie.logoPath,
                                    contentDescription = "Logo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 16.dp).width(160.dp).height(80.dp)
                                )
                            }
                        }
                    }

                    item {
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                AsyncImage(
                                    model = movie.posterPath ?: android.R.drawable.ic_menu_gallery,
                                    contentDescription = movie.title,
                                    modifier = Modifier
                                        .width(110.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Gray)
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                scope.launch {
                                                    if (movie.tmdbId.isNotBlank()) {
                                                        availableArtworks = CineOnlineScraper.fetchArtworkOptions(movie.tmdbId, "movie")
                                                        showPosterPickerFor = "poster"
                                                    }
                                                }
                                            }
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(18.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(movie.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                                    if (movie.tagline.isNotBlank()) {
                                        Text("\"${movie.tagline}\"", style = MaterialTheme.typography.bodySmall, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("★ ${movie.userRating} | ${movie.premiered} | ${movie.runtime} min", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(movie.genre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedButton(
                                        onClick = {
                                            isRefreshing = true
                                            scope.launch {
                                                val refreshed = CineOnlineScraper.getOrFetchMovie(context, File(movie.videoFilePath).name, movie.tmdbId, forceRefresh = true)
                                                if (refreshed != null) selectedMovie = movie.copy().apply {
                                                    plot = refreshed.plot
                                                    posterPath = refreshed.posterPath
                                                    backdropPath = refreshed.backdropPath
                                                    actors = refreshed.actors
                                                    collection = refreshed.collection
                                                }
                                                isRefreshing = false
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        if (isRefreshing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        else {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Refresh Metadata", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // ACTOR DECK - OPENS DETAILED BOTTOM SHEET
                            if (movie.actors.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Text("Cast", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    items(movie.actors) { actor ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp).clickable { activeActorLookup = actor }) {
                                            AsyncImage(
                                                model = actor.thumbUrl, contentDescription = actor.name, contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(actor.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                            if (actor.character.isNotBlank()) {
                                                Text(actor.character, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }
                            
                            // COLLECTIONS
                            movie.collection?.let { collection ->
                                Spacer(modifier = Modifier.height(24.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(16.dp))) {
                                    AsyncImage(model = collection.backdropPath, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
                                    Column(modifier = Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Part of the", color = Color.White, fontSize = 12.sp)
                                        Text(collection.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    onUpdateMediaInfo(
                                        movie.posterPath ?: "", movie.title, movie.director, movie.plot,
                                        mapOf("Genre" to movie.genre, "Runtime" to "${movie.runtime}m", "TMDB" to movie.tmdbId)
                                    )
                                    selectedMovie = null
                                    onPlayRequested(movie.videoFilePath, movie.title)
                                },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Play Local File", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Text("Plot Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(movie.plot.ifEmpty { "No description available." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }

        // --- INTERACTIVE POSTER/BACKDROP PICKER ---
        if (showPosterPickerFor != null && availableArtworks != null) {
            val list = if (showPosterPickerFor == "poster") availableArtworks!!.posters else availableArtworks!!.backdrops
            ModalBottomSheet(onDismissRequest = { showPosterPickerFor = null }) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Select ${showPosterPickerFor!!.capitalize()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (showPosterPickerFor == "poster") 3 else 2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list) { img ->
                            val url = "${CineOnlineScraper.THUMB_BASE_URL}${img.file_path}"
                            val fullUrl = "${CineOnlineScraper.IMAGE_BASE_URL}${img.file_path}"
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(if (showPosterPickerFor == "poster") 2f / 3f else 16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (showPosterPickerFor == "poster") selectedMovie?.posterPath = fullUrl
                                        else selectedMovie?.backdropPath = fullUrl
                                        
                                        // Save back to JSON Cache
                                        selectedMovie?.let { MetadataCacheManager.saveToCache(context, "movie_${it.tmdbId}", it) }
                                        showPosterPickerFor = null
                                    }
                            )
                        }
                    }
                }
            }
        }

        // --- FULL ACTOR DETAILS PAGE ---
        activeActorLookup?.let { actor ->
            var actorDetails by remember { mutableStateOf<TMDBPersonDetails?>(null) }
            LaunchedEffect(actor.id) {
                if (actor.id.isNotBlank()) actorDetails = CineOnlineScraper.fetchActorDetails(context, actor.id)
            }
            ModalBottomSheet(onDismissRequest = { activeActorLookup = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    item {
                        Row {
                            AsyncImage(model = actor.thumbUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(120.dp).aspectRatio(2f/3f).clip(RoundedCornerShape(12.dp)))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(actor.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                                actorDetails?.birthday?.let { Text("Born: $it", color = Color.Gray) }
                                actorDetails?.known_for_department?.let { Text("Dept: $it", color = MaterialTheme.colorScheme.primary) }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Biography", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(actorDetails?.biography?.ifEmpty { "No biography available." } ?: "Loading bio...", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                        
                        // Cross-reference existing DB for Filmography
                        val (actorMovies, actorShows) = remember(actor.name) { NfoScanner.getSharedFilmography(actor.name, moviesList, tvShowsList) }
                        if (actorMovies.isNotEmpty() || actorShows.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("In Your Library", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                items(actorMovies) { m ->
                                    Box(modifier = Modifier.width(100.dp)) {
                                        CineHubGridCard(m.title, m.genre, m.userRating, m.posterPath) {
                                            activeActorLookup = null
                                            selectedMovie = m
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- SETTINGS SHEET ---
        if (showSettingsSheet) {
            ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 24.dp)) {
                    Text("Metadata Engine Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    ListItem(
                        headlineContent = { Text("Auto-Download Artwork") },
                        supportingContent = { Text("Posters, backdrops, and logos") },
                        trailingContent = { Switch(checked = true, onCheckedChange = {}) }
                    )
                    ListItem(
                        headlineContent = { Text("Clear Metadata Cache") },
                        supportingContent = { Text("Force resync from TMDB/TVMaze") },
                        trailingContent = { 
                            Button(onClick = { 
                                MetadataCacheManager.clearCache(context)
                                showSettingsSheet = false
                            }) { Text("Clear") }
                        }
                    )
                }
            }
        }
    }
}