package app.marlboroadvance.mpvex.youtube.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.youtube.data.InvidiousClient
import app.marlboroadvance.mpvex.youtube.model.YoutubeVideo
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun YoutubeTabScreen(
    onPlayRequested: (String, String) -> Unit,
    onUpdateMediaInfo: (
        thumbnail: String,
        title: String,
        author: String,
        description: String,
        metadata: Map<String, String>
    ) -> Unit = { _, _, _, _, _ -> }
) {
    var videoList by remember { mutableStateOf<List<YoutubeVideo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isSearchBarVisible by remember { mutableStateOf(false) } 
    var refreshTrigger by remember { mutableIntStateOf(0) }
    
    // Bottom Sheet States
    var longPressedVideo by remember { mutableStateOf<YoutubeVideo?>(null) }
    var clickedChannelVideo by remember { mutableStateOf<YoutubeVideo?>(null) }
    
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(refreshTrigger, isSearching) {
        isLoading = true
        videoList = if (isSearching && searchQuery.isNotBlank()) {
            InvidiousClient.fetchSearchVideos(searchQuery)
        } else {
            InvidiousClient.fetchTrendingVideos("Movies")
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BrowserTopBar(
                    title = if (isSearching) "Search Results" else "CineTube Live",
                    isInSelectionMode = false,
                    selectedCount = 0,
                    totalCount = videoList.size,
                    onCancelSelection = {},
                    isHomeScreen = true, 
                    onSearchClick = {
                        isSearchBarVisible = !isSearchBarVisible
                    }
                )

                AnimatedVisibility(
                    visible = isSearchBarVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            placeholder = { Text("Search CineTube library...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search Icon", tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty() || isSearching) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        isSearching = false
                                        isSearchBarVisible = false
                                        keyboardController?.hide()
                                        refreshTrigger++
                                    }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    isSearching = true
                                    refreshTrigger++ 
                                }
                                keyboardController?.hide()
                            })
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Crossfade(targetState = isLoading, label = "LoadingTransition") { loading ->
                if (loading) {
                    SkeletonLoadingGrid()
                } else if (videoList.isEmpty()) {
                    ErrorStateUi(
                        isSearching = isSearching,
                        onRetry = { refreshTrigger++ }
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp), // Wide premium cards
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = videoList,
                            key = { it.videoId }
                        ) { video ->
                            VideoCardPremium(
                                video = video,
                                onClick = {
                                    scope.launch {
                                        // Update Media Info Metadata Before Playback
                                        onUpdateMediaInfo(
                                            video.getBestThumbnailUrl(),
                                            video.title,
                                            video.author,
                                            video.description,
                                            mapOf(
                                                "Views" to video.formatViewCount(),
                                                "Published" to video.publishedText,
                                                "Duration" to "${video.lengthSeconds}s",
                                                "Video ID" to video.videoId
                                            )
                                        )
                                        
                                        // Auto-fetch best progressive stream & Play
                                        val directStreamUrl = InvidiousClient.fetchDirectStreamUrl(video.videoId)
                                        if (directStreamUrl != null) {
                                            onPlayRequested(directStreamUrl, video.title)
                                        }
                                    }
                                },
                                onLongClick = { longPressedVideo = video },
                                onChannelClick = { clickedChannelVideo = video }
                            )
                        }
                    }
                }
            }

            // Context Menu Bottom Sheet
            if (longPressedVideo != null) {
                ContextMenuBottomSheet(
                    video = longPressedVideo!!,
                    onDismiss = { longPressedVideo = null }
                )
            }

            // Channel Info Bottom Sheet
            if (clickedChannelVideo != null) {
                ChannelInfoBottomSheet(
                    video = clickedChannelVideo!!,
                    onDismiss = { clickedChannelVideo = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCardPremium(
    video: YoutubeVideo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onChannelClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        // Thumbnail Box
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = video.getBestThumbnailUrl(),
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            
            // Badges Top Right
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (video.isLiveNow) {
                    BadgeChip(text = "LIVE", color = Color.Red)
                }
                BadgeChip(text = "HD", color = MaterialTheme.colorScheme.primary)
            }

            // Duration Bottom Right
            if (video.lengthSeconds > 0) {
                val minutes = video.lengthSeconds / 60
                val seconds = video.lengthSeconds % 60
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = String.format("%d:%02d", minutes, seconds),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

                // Info Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
          
            // Channel Avatar
            AsyncImage(
                model = video.getBestAuthorThumbnailUrl() ?: "https://ui-avatars.com/api/?name=${video.author}&background=random",
                contentDescription = video.author,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onChannelClick() },
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Text Metadata
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = video.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${video.formatViewCount()} • ${video.publishedText}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            IconButton(onClick = onLongClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BadgeChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.9f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextMenuBottomSheet(video: YoutubeVideo, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = video.getBestThumbnailUrl(),
                    contentDescription = null,
                    modifier = Modifier
                        .width(64.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

            val menuItems = listOf(
                Triple(Icons.Default.PlayArrow, "Play Next", {}),
                Triple(Icons.Default.WatchLater, "Save to Watch Later", {}),
                Triple(Icons.Default.Download, "Download Video", {}),
                Triple(Icons.Default.Share, "Share", {}),
                Triple(Icons.Default.ContentCopy, "Copy Link", {}),
                Triple(Icons.Default.Block, "Not Interested", {})
            )

            menuItems.forEach { (icon, text, action) ->
                BottomSheetMenuItem(icon = icon, text = text, onClick = {
                    action()
                    onDismiss()
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelInfoBottomSheet(video: YoutubeVideo, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = video.getBestAuthorThumbnailUrl() ?: "https://ui-avatars.com/api/?name=${video.author}&background=random",
                contentDescription = video.author,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = video.author,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = if(video.subCountText.isNotEmpty()) video.subCountText else "Tap to view channel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("View Channel", modifier = Modifier.padding(vertical = 4.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BottomSheetMenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = text, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(24.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SkeletonLoadingGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(6) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                    }
                }
            }
        }
    }
}

fun Modifier.shimmerEffect(): Modifier {
    return this.background(Color.Gray.copy(alpha = 0.2f)) // Placeholder for visual consistency, Compose animations handle core transitions
}

@Composable
fun ErrorStateUi(isSearching: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.WifiOff,
            contentDescription = "Error",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isSearching) "No cinematic results found." else "Network timeout. Node offline.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Please check your connection or switch nodes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(text = "Retry Connection", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}
