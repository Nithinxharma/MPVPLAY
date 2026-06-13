package app.marlboroadvance.mpvex.cinehub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.marlboroadvance.mpvex.cinehub.model.MovieItem
import app.marlboroadvance.mpvex.cinehub.model.TvShowItem
import app.marlboroadvance.mpvex.cinehub.model.EpisodeItem
import app.marlboroadvance.mpvex.cinehub.data.NfoScanner
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CineHubScreen(
    moviesList: List<MovieItem>,
    tvShowsList: List<TvShowItem>,
    onPlayRequested: (filePath: String, cleanTitle: String) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Movies", "TV Shows")
    
    var selectedMovie by remember { mutableStateOf<MovieItem?>(null) }
    var selectedTvShow by remember { mutableStateOf<TvShowItem?>(null) }

    // Fixes the layout going behind status bar
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(), 
        topBar = {
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        text = { Text(title, style = MaterialTheme.typography.titleSmall) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tabIndex) {
                0 -> {
                    if (moviesList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No local movies discovered.")
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(6.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            items(moviesList) { movie ->
                                CineHubGridCard(
                                    title = movie.title,
                                    genre = movie.genre,
                                    rating = movie.userRating,
                                    posterPath = movie.posterPath,
                                    onClick = { selectedMovie = movie }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (tvShowsList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No local TV series discovered.")
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(6.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            items(tvShowsList) { show ->
                                CineHubGridCard(
                                    title = show.title,
                                    genre = show.genre,
                                    rating = show.userRating,
                                    posterPath = show.posterPath,
                                    onClick = { selectedTvShow = show }
                                )
                            }
                        }
                    }
                }
            }

            // --- MOVIE DETAIL OVERLAY IN MATERIAL3 BOTTOM SHEET ---
            selectedMovie?.let { movie ->
                ModalBottomSheet(
                    onDismissRequest = { selectedMovie = null },
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = movie.posterPath ?: android.R.drawable.ic_menu_gallery,
                                    contentDescription = movie.title,
                                    modifier = Modifier
                                        .width(110.dp)
                                        .aspectRatio(2f / 3f)
                                        .background(Color.Gray, RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(movie.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    if (movie.originalTitle.isNotEmpty() && movie.originalTitle != movie.title) {
                                        Text(movie.originalTitle, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Rating: ★ ${movie.userRating} | Premiered: ${movie.premiered}", style = MaterialTheme.typography.bodySmall)
                                    Text("Genre: ${movie.genre}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    selectedMovie = null
                                    onPlayRequested(movie.videoFilePath, movie.title) // Passes clean title to player intent
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Play Movie")
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Plot Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                movie.plot.ifEmpty { "No description available." },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // --- TV SHOW SEASON & EPISODES DETAIL OVERLAY ---
            selectedTvShow?.let { show ->
                val episodes = remember(show) { NfoScanner.scanTvShowEpisodes(File(show.folderPath)) }
                val seasons = remember(episodes) { episodes.groupBy { it.season }.toSortedMap() }
                var selectedSeasonTab by remember { mutableIntStateOf(seasons.keys.firstOrNull() ?: 1) }

                ModalBottomSheet(
                    onDismissRequest = { selectedTvShow = null },
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(show.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Studio: ${show.studio} | Genre: ${show.genre}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (seasons.keys.size > 1) {
                            ScrollableTabRow(
                                selectedTabIndex = seasons.keys.indexOf(selectedSeasonTab).coerceAtLeast(0),
                                edgePadding = 0.dp
                            ) {
                                seasons.keys.forEach { seasonNum ->
                                    Tab(
                                        selected = selectedSeasonTab == seasonNum,
                                        onClick = { selectedSeasonTab = seasonNum },
                                        text = { Text("Season $seasonNum") }
                                    )
                                }
                            }
                        }

                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                            items(seasons[selectedSeasonTab] ?: emptyList()) { episode ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "E${episode.episode}: ${episode.title}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (episode.plot.isNotEmpty()) {
                                                Text(
                                                    episode.plot,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        IconButton(onClick = {
                                            selectedTvShow = null
                                            onPlayRequested(episode.videoFilePath, "${show.title} - S${episode.season}E${episode.episode}")
                                        }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Episode", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
