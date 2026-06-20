package xyz.mpv.rex.cinemine.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import xyz.mpv.rex.cinemine.data.CineMineRepo
import xyz.mpv.rex.cinemine.data.CineMineStreamResolver
import xyz.mpv.rex.cinemine.model.TvShowItem
import xyz.mpv.rex.cinemine.model.EpisodeItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvShowDetailSheet(
    show: TvShowItem,
    onDismiss: () -> Unit,
    onPlayRequested: (streamUrl: String, title: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var episodesList by remember { mutableStateOf(emptyList<EpisodeItem>()) }
    var isLoadingEpisodes by remember { mutableStateOf(true) }

    val groupedSeasons = remember(episodesList) {
        episodesList.groupBy { it.season }.toSortedMap()
    }
    
    var selectedSeasonTab by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(show.folderPath) {
        isLoadingEpisodes = true
        episodesList = CineMineRepo.fetchLocalEpisodes(show.folderPath)
        isLoadingEpisodes = false
        
        if (episodesList.isNotEmpty()) {
            selectedSeasonTab = episodesList.map { it.season }.minOrNull() ?: 1
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp, top = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                AsyncImage(
                    model = show.posterPath ?: android.R.drawable.ic_menu_gallery,
                    contentDescription = show.title,
                    modifier = Modifier
                        .width(90.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray.copy(alpha = 0.2f)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = show.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Studio: ${show.studio.ifBlank { "Unknown" }} | Genre: ${show.genre.ifBlank { "Series" }}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    if (show.userRating > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "★ ${show.getFormattedRating()}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD700),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = show.plot.ifBlank { "No show summary description available." },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            if (groupedSeasons.keys.size > 1 && selectedSeasonTab != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ScrollableTabRow(
                    selectedTabIndex = groupedSeasons.keys.indexOf(selectedSeasonTab).coerceAtLeast(0),
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp,
                    divider = {}
                ) {
                    groupedSeasons.keys.forEach { seasonNum ->
                        Tab(
                            selected = selectedSeasonTab == seasonNum,
                            onClick = { selectedSeasonTab = seasonNum },
                            text = { 
                                Text(
                                    text = "Season $seasonNum", 
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                ) 
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoadingEpisodes) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                }
            } else if (episodesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No local media video tracks detected inside this folder index.", fontSize = 12.sp, color = Color.Gray)
                }
            } else {
                val activeSeasonEpisodes = groupedSeasons[selectedSeasonTab] ?: emptyList()
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(activeSeasonEpisodes) { episode ->
                        EpisodeItemRow(
                            episodeCode = episode.getEpisodeCode(),
                            title = episode.title,
                            plot = episode.plot,
                            onPlayClick = {
                                scope.launch {
                                    val streamLink = CineMineStreamResolver.resolvePlaybackUrl(episode.videoFilePath)
                                    onPlayRequested(streamLink, "${show.title} - ${episode.getEpisodeCode()}")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
