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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CineHubScreen(
    moviesList: List<MovieItem>,
    tvShowsList: List<TvShowItem>,
    onPlayRequested: (filePath: String, cleanTitle: String, metadata: Map<String, String>) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Movies", "TV Shows")
    
    val activeLocalMovies = remember { mutableStateListOf<MovieItem>() }
    val activeLocalTvShows = remember { mutableStateListOf<TvShowItem>() }
    
    var selectedMovie by remember { mutableStateOf<MovieItem?>(null) }
    var selectedTvShow by remember { mutableStateOf<TvShowItem?>(null) }
    
    var activeActorLookup by remember { mutableStateOf<ActorItem?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    
    // Pickers & Manual States
    var showPosterPickerFor by remember { mutableStateOf<String?>(null) }
    var availableArtworks by remember { mutableStateOf<TMDBImagesResponse?>(null) }
    var showManualMatchSheet by remember { mutableStateOf(false) }
    var showCustomEntrySheet by remember { mutableStateOf(false) }
    var manualSearchQuery by remember { mutableStateOf("") }
    var manualMovieResults by remember { mutableStateOf<List<TMDBMovieNode>>(emptyList()) }
    var manualTvResults by remember { mutableStateOf<List<TMDBTvNode>>(emptyList()) }
    var isManualSearching by remember { mutableStateOf(false) }
    
    var onlineMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var onlineTvShows by remember { mutableStateOf<List<TvShowItem>>(emptyList()) }
    var isOnlineLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val gridColumnCount = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 6 else 3

    LaunchedEffect(moviesList, tvShowsList) {
        activeLocalMovies.clear()
        activeLocalMovies.addAll(moviesList)
        activeLocalTvShows.clear()
        activeLocalTvShows.addAll(tvShowsList)

        launch(Dispatchers.IO) {
            for (i in activeLocalMovies.indices) {
                val movie = activeLocalMovies[i]
                if (!movie.isMetadataCached) {
                    val onlineMeta = CineOnlineScraper.getOrFetchMovie(context, File(movie.videoFilePath).name, movie.tmdbId)
                    if (onlineMeta != null) {
                        activeLocalMovies[i] = onlineMeta.copy(
                            videoFilePath = movie.videoFilePath,
                            posterPath = onlineMeta.posterPath ?: movie.posterPath,
                            backdropPath = onlineMeta.backdropPath
                        )
                    } else {
                        activeLocalMovies[i] = movie.copy(isMetadataCached = false, plot = "Metadata completely unmapped.")
                    }
                }
            }
            
            for (i in activeLocalTvShows.indices) {
                val show = activeLocalTvShows[i]
                if (!show.isMetadataCached) {
                    val onlineMeta = CineOnlineScraper.getOrFetchTvShow(context, File(show.folderPath).name, show.tmdbId)
                    if (onlineMeta != null) {
                        activeLocalTvShows[i] = onlineMeta.copy(
                            folderPath = show.folderPath,
                            posterPath = onlineMeta.posterPath ?: show.posterPath,
                            backdropPath = onlineMeta.backdropPath
                        )
                    } else {
                        activeLocalTvShows[i] = show.copy(isMetadataCached = false, plot = "Metadata completely unmapped.")
                    }
                }
            }
        }
    }

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
                    totalCount = activeLocalMovies.size + activeLocalTvShows.size,
                    onCancelSelection = {},
                    isHomeScreen = true,
                    onSearchClick = {}
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
                    Row {
                        IconButton(onClick = { /* Internal Refresher Request Trigger */ }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(onClick = { showSettingsSheet = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Settings, contentDescription = "Engine Settings", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            item {
                if (tabIndex == 0) {
                    if (activeLocalMovies.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("No local files found inside target folders.", fontSize = 13.sp, color = Color.Gray)
                        }
                    } else {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(activeLocalMovies) { movie ->
                                Box(modifier = Modifier.width(135.dp)) {
                                    CineHubGridCard(title = movie.title, genre = movie.genre, rating = movie.userRating, posterPath = movie.posterPath, watchProgress = movie.watchProgress) {
                                        selectedMovie = movie
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (activeLocalTvShows.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("No local files found inside target folders.", fontSize = 13.sp, color = Color.Gray)
                        }
                    } else {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            items(activeLocalTvShows) { show ->
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
                                                    
                                                    val generatedMetadataMap = mapOf("Genre" to movieItem.genre, "Rating" to movieItem.userRating.toString(), "Plot" to movieItem.plot, "Poster" to (movieItem.posterPath ?: ""))

                                                    if (!directM3u8.isNullOrBlank()) onPlayRequested(directM3u8, movieItem.title, generatedMetadataMap)
                                                    else onPlayRequested("https://net52.cc/mobile/player.php?id=$rawId", movieItem.title, generatedMetadataMap)
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
                                                    
                                                    val generatedMetadataMap = mapOf("Genre" to tvShowItem.genre, "Rating" to tvShowItem.userRating.toString(), "Plot" to tvShowItem.plot, "Poster" to (tvShowItem.posterPath ?: ""))

                                                    if (!directM3u8.isNullOrBlank()) onPlayRequested(directM3u8, tvShowItem.title, generatedMetadataMap)
                                                    else onPlayRequested("https://net52.cc/mobile/player.php?id=$rawId", tvShowItem.title, generatedMetadataMap)
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

        // --- MOVIES DETAIL OVERLAY ---
        selectedMovie?.let { movie ->
            var isRefreshing by remember { mutableStateOf(false) }

            ModalBottomSheet(
                onDismissRequest = { selectedMovie = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
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
                                    if (!movie.isMetadataCached) {
                                        Button(
                                            onClick = { showManualMatchSheet = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Match Manually", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                isRefreshing = true
                                                scope.launch {
                                                    val refreshed = CineOnlineScraper.getOrFetchMovie(context, File(movie.videoFilePath).name, movie.tmdbId, forceRefresh = true)
                                                    if (refreshed != null) {
                                                        selectedMovie = movie.copy(
                                                            plot = refreshed.plot,
                                                            posterPath = refreshed.posterPath,
                                                            backdropPath = refreshed.backdropPath,
                                                            actors = refreshed.actors,
                                                            collection = refreshed.collection
                                                        )
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
                            }

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
                                    val compiledMetadata = mapOf(
                                        "Genre" to movie.genre,
                                        "Runtime" to "${movie.runtime}m",
                                        "TMDB" to movie.tmdbId,
                                        "Plot" to movie.plot,
                                        "Poster" to (movie.posterPath ?: "")
                                    )
                                    val playUrl = if (movie.sourceType == "drive") NfoScanner.GoogleDriveScanner.resolveDrivePlaybackUrl(movie.driveFileId ?: "") else movie.videoFilePath
                                    selectedMovie = null
                                    onPlayRequested(playUrl, movie.title, compiledMetadata)
                                },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (movie.sourceType == "drive") "Stream from Google Drive" else "Play Full Movie", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                            Text("Plot Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(movie.plot.ifEmpty { "No description available." }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }

        // --- FULLY RESTORED TV SHOWS AND EPISODES PANEL ---
        selectedTvShow?.let { show ->
            val episodes = remember(show) { NfoScanner.scanTvShowEpisodes(File(show.folderPath)).sortedBy { it.episode } }
            val seasons = remember(episodes) { episodes.groupBy { it.season }.toSortedMap() }
            var selectedSeasonTab by remember { mutableStateOf(seasons.keys.firstOrNull() ?: 1) }

            ModalBottomSheet(
                onDismissRequest = { selectedTvShow = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                        AsyncImage(
                            model = show.backdropPath ?: show.posterPath,
                            contentDescription = "Backdrop",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface))))
                    }

                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        Text(show.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text("Studio: ${show.studio} | Genre: ${show.genre} | Rating: ★ ${show.userRating}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        
                        if (!show.isMetadataCached) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showManualMatchSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Match Manually", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }

                        if (show.actors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Cast", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                items(show.actors) { actor ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp).clickable { activeActorLookup = actor }) {
                                        AsyncImage(model = actor.thumbUrl, contentDescription = actor.name, contentScale = ContentScale.Crop, modifier = Modifier.size(58.dp).clip(CircleShape).background(Color.LightGray))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(actor.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }

                        if (seasons.keys.size > 1) {
                            ScrollableTabRow(selectedTabIndex = seasons.keys.indexOf(selectedSeasonTab).coerceAtLeast(0), edgePadding = 0.dp, divider = {}) {
                                seasons.keys.forEach { seasonNum ->
                                    Tab(selected = selectedSeasonTab == seasonNum, onClick = { selectedSeasonTab = seasonNum }, text = { Text("Season $seasonNum", fontWeight = FontWeight.Bold) })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            items(seasons[selectedSeasonTab] ?: emptyList()) { episode ->
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)).border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(12.dp))) {
                                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Episode ${episode.episode}: ${episode.title}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                                            if (episode.plot.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(episode.plot, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        IconButton(
                                            onClick = {
                                                val tvMetadata = mapOf(
                                                    "Genre" to show.genre,
                                                    "Studio" to show.studio,
                                                    "Series" to show.title,
                                                    "Season" to episode.season.toString(),
                                                    "Episode" to episode.episode.toString(),
                                                    "Plot" to episode.plot,
                                                    "Poster" to (show.posterPath ?: "")
                                                )
                                                val playUrl = if (episode.sourceType == "drive") NfoScanner.GoogleDriveScanner.resolveDrivePlaybackUrl(episode.driveFileId ?: "") else episode.videoFilePath
                                                selectedTvShow = null
                                                onPlayRequested(playUrl, "${show.title} - S${episode.season}E${episode.episode}", tvMetadata)
                                            },
                                            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- ARTWORK PICKER ---
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
                                        
                                        selectedMovie?.let { MetadataCacheManager.saveToCache(context, "movie_${it.tmdbId}", it) }
                                        showPosterPickerFor = null
                                    }
                            )
                        }
                    }
                }
            }
        }

        // --- MANUAL MATCHING WORKFLOW ---
        if (showManualMatchSheet) {
            val isMovie = selectedMovie != null
            val activeName = if (isMovie) File(selectedMovie!!.videoFilePath).name else File(selectedTvShow!!.folderPath).name
            val safeName = CineOnlineScraper.cleanMediaFileName(activeName).first

            LaunchedEffect(safeName) { manualSearchQuery = safeName }

            ModalBottomSheet(onDismissRequest = { showManualMatchSheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(16.dp)) {
                    Text("Match Manually: $activeName", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = manualSearchQuery,
                        onValueChange = { manualSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search TMDB Core") },
                        trailingIcon = {
                            IconButton(onClick = {
                                isManualSearching = true
                                scope.launch {
                                    if (isMovie) manualMovieResults = CineOnlineScraper.executeManualMovieSearch(manualSearchQuery)
                                    else manualTvResults = CineOnlineScraper.executeManualTvSearch(manualSearchQuery)
                                    isManualSearching = false
                                }
                            }) { Icon(Icons.Default.Search, contentDescription = "Search") }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isManualSearching) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (isMovie) {
                                items(manualMovieResults) { result ->
                                    Row(modifier = Modifier.fillMaxWidth().clickable {
                                        ManualMappingManager.saveMapping(context, activeName, result.id.toString())
                                        scope.launch {
                                            val refreshed = CineOnlineScraper.getOrFetchMovie(context, activeName, result.id.toString(), forceRefresh = true)
                                            if (refreshed != null) {
                                                val ix = activeLocalMovies.indexOfFirst { it.videoFilePath == selectedMovie!!.videoFilePath }
                                                if (ix != -1) activeLocalMovies[ix] = refreshed
                                                selectedMovie = refreshed
                                            }
                                            showManualMatchSheet = false
                                        }
                                    }) {
                                        AsyncImage(model = result.poster_path?.let { "${CineOnlineScraper.THUMB_BASE_URL}$it" }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(60.dp).aspectRatio(2f/3f).clip(RoundedCornerShape(8.dp)).background(Color.Gray))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(result.title ?: "Unknown", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(result.release_date ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            Text(result.overview ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            } else {
                                items(manualTvResults) { result ->
                                    Row(modifier = Modifier.fillMaxWidth().clickable {
                                        ManualMappingManager.saveMapping(context, activeName, result.id.toString())
                                        scope.launch {
                                            val refreshed = CineOnlineScraper.getOrFetchTvShow(context, activeName, result.id.toString(), forceRefresh = true)
                                            if (refreshed != null) {
                                                val ix = activeLocalTvShows.indexOfFirst { it.folderPath == selectedTvShow!!.folderPath }
                                                if (ix != -1) activeLocalTvShows[ix] = refreshed
                                                selectedTvShow = refreshed
                                            }
                                            showManualMatchSheet = false
                                        }
                                    }) {
                                        AsyncImage(model = result.poster_path?.let { "${CineOnlineScraper.THUMB_BASE_URL}$it" }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(60.dp).aspectRatio(2f/3f).clip(RoundedCornerShape(8.dp)).background(Color.Gray))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(result.name ?: "Unknown", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(result.first_air_date ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            Text(result.overview ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            
                            item {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { 
                                        showManualMatchSheet = false
                                        showCustomEntrySheet = true 
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Create Custom Entry for Unlisted Media") }
                            }
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
                        
                        val (actorMovies, actorShows) = remember(actor.name) { NfoScanner.getSharedFilmography(actor.name, activeLocalMovies, activeLocalTvShows) }
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
                    Text("Library & Metadata Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    
                    Text("Library Sources", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    ListItem(headlineContent = { Text("Internal Storage / SD Card") }, trailingContent = { Checkbox(checked = true, onCheckedChange = {}) })
                    ListItem(headlineContent = { Text("Google Drive (CineRex Folder)") }, supportingContent = { Text("Scan cloud storage for streaming") }, trailingContent = { Switch(checked = true, onCheckedChange = {}) })
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text("Engine Config", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

                    ListItem(headlineContent = { Text("Auto-Download Artwork") }, supportingContent = { Text("Posters, backdrops, and logos") }, trailingContent = { Switch(checked = true, onCheckedChange = {}) })
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