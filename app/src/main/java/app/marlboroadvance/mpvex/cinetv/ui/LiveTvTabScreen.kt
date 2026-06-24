package app.marlboroadvance.mpvex.cinetv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import app.marlboroadvance.mpvex.cinetv.data.JioTvRepo
import app.marlboroadvance.mpvex.cinetv.model.*
import java.text.SimpleDateFormat
import java.util.*

val globalPaidChannels = mutableStateMapOf<String, Boolean>()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvTabScreen(
    searchQuery: String, 
    onPlayRequested: (streamUrl: String, channelTitle: String) -> Unit 
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    var activeSubTab by remember { mutableStateOf(LiveTab.CHANNELS) }
    var userAuthed by remember { mutableStateOf(JioTvRepo.isUserLoggedIn()) }

    var allChannels by remember { mutableStateOf(emptyList<LiveChannelItem>()) }
    var isLoading by remember { mutableStateOf(true) }

    var localSearchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("All") }
    var selectedLanguage by remember { mutableStateOf("All Languages") }

    var smartCache by remember { mutableStateOf(mapOf<String, ChannelCacheEntry>()) }
    
    // Multiple Stream Collision Dialog
    var showStreamChooserFor by remember { mutableStateOf<Pair<LiveChannelItem, List<M3uMatchCandidate>>?>(null) }

    LaunchedEffect(Unit) {
        JioTvRepo.initTokens(context)
        userAuthed = JioTvRepo.isUserLoggedIn()
        smartCache = JioTvRepo.getChannelCacheMap(context)
        isLoading = true
        try {
            allChannels = JioTvRepo.fetchLiveChannelsFromAssets(context)
        } catch (e: Exception) { } finally { isLoading = false }
    }

    val availableGenres = remember(allChannels) { listOf("All") + allChannels.map { it.category }.distinct().sorted() }
    val availableLanguages = remember(allChannels) { listOf("All Languages") + allChannels.flatMap { it.variants.map { v -> v.language } }.distinct().sorted() }

    val filteredChannels = remember(allChannels, selectedGenre, selectedLanguage, localSearchQuery, searchQuery) {
        allChannels.filter { channel ->
            val matchesGenre = selectedGenre == "All" || channel.category == selectedGenre
            val matchesLanguage = selectedLanguage == "All Languages" || channel.variants.any { it.language.equals(selectedLanguage, true) }

            val activeSearch = if (localSearchQuery.isNotBlank()) localSearchQuery else searchQuery
            val searchLower = activeSearch.trim().lowercase()

            val cacheEntry = smartCache[channel.defaultChannelId]
            val aliasName = cacheEntry?.mappedM3uName?.lowercase() ?: ""
            val manualUrl = cacheEntry?.manualStreamUrl?.lowercase() ?: ""

            val matchesSearch = searchLower.isBlank() || 
                channel.title.lowercase().contains(searchLower) || 
                channel.category.lowercase().contains(searchLower) ||
                aliasName.contains(searchLower) ||
                manualUrl.contains(searchLower) ||
                (cacheEntry?.status == MappingStatus.BROKEN && searchLower == "broken") ||
                channel.variants.any { it.language.lowercase().contains(searchLower) }

            matchesGenre && matchesLanguage && matchesSearch
        }
    }

    val playChannel: (LiveChannelItem, String) -> Unit = { channel, idToPlay ->
        scope.launch {
            try {
                val resolved = JioTvRepo.getResolvedLiveUrl(context, idToPlay, channel.title)
                JioTvRepo.lastResolvedHeaders = resolved.headers
                onPlayRequested(resolved.url, channel.title)
            } catch (e: MultipleStreamsException) {
                showStreamChooserFor = channel to e.candidates
            } catch (e: Exception) {
                Toast.makeText(context, "No working streams found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showStreamChooserFor != null) {
        val (channel, candidates) = showStreamChooserFor!!
        ModalBottomSheet(onDismissRequest = { showStreamChooserFor = null }) {
            Column(Modifier.padding(16.dp)) {
                Text("Multiple Streams Found", fontWeight = FontWeight.Black, fontSize = 20.sp)
                Text("Select preferred source for ${channel.title}", color = Color.Gray, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                LazyColumn {
                    items(candidates) { candidate ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                scope.launch {
                                    JioTvRepo.lastResolvedHeaders = candidate.headers
                                    onPlayRequested(candidate.url, channel.title)
                                    // Pre-emptively assign this as chosen map override to prevent dialog again
                                    JioTvRepo.saveManualMapping(context, channel.defaultChannelId, channel.title, candidate.mappedName, null)
                                    smartCache = JioTvRepo.getChannelCacheMap(context)
                                    showStreamChooserFor = null
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(candidate.mappedName, fontWeight = FontWeight.Bold)
                                    Row {
                                        Text(candidate.resolution, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(" • M3U", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                                Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("CINE TV", fontWeight = FontWeight.Black, fontSize = 28.sp)
                        Text("Live TV", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    Box(modifier = Modifier.padding(end = 16.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).clickable { /* Search Action if needed */ }.padding(8.dp)) {
                        Icon(Icons.Default.Search, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    scrolledContainerColor = Color.Black.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = activeSubTab.ordinal, containerColor = Color.Transparent, divider = {}) {
                LiveTab.values().forEach { tab ->
                    Tab(selected = activeSubTab == tab, onClick = { activeSubTab = tab }, text = { Text(tab.label, fontWeight = FontWeight.Bold) })
                }
            }

            when (activeSubTab) {
                LiveTab.CHANNELS -> {
                    // Glassmorphism Search Bar
                    OutlinedTextField(
                        value = localSearchQuery,
                        onValueChange = { localSearchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        placeholder = { Text("Search by name, alias, manual URL...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = 0.35f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.35f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        )
                    )

                    LazyRow(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items = availableGenres) { genre ->
                            FilterChip(selected = selectedGenre == genre, onClick = { selectedGenre = genre }, label = { Text(genre) }, shape = CircleShape)
                        }
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        LazyVerticalGrid(columns = GridCells.Fixed(1), modifier = Modifier.weight(1f), contentPadding = PaddingValues(14.dp)) {
                            items(items = filteredChannels) { channel ->
                                val channelLangId = channel.getIdForLanguage(if (selectedLanguage != "All Languages") selectedLanguage else channel.defaultLanguage)
                                val entry = smartCache[channelLangId]

                                LiveChannelRowItem(
                                    channel = channel,
                                    currentActiveId = channelLangId,
                                    entry = entry,
                                    onPlayRequested = { id -> playChannel(channel, id) }
                                )
                            }
                        }
                    }
                }

                LiveTab.JIO_LOGIN -> {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        // Direct URL Mode Sync
                        item {
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.35f)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp)).padding(20.dp)) {
                                Text("Sync Playlist (Direct URL)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                var m3uUrl by remember { mutableStateOf("") }
                                OutlinedTextField(value = m3uUrl, onValueChange = { m3uUrl = it }, placeholder = { Text("https://abc.xyz/live.m3u") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = {
                                    scope.launch {
                                        Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show()
                                        val success = JioTvRepo.syncPlaylistFromUrl(context, m3uUrl)
                                        if (success) {
                                            JioTvRepo.reloadM3uParser()
                                            Toast.makeText(context, "✓ Synced Successfully", Toast.LENGTH_LONG).show()
                                        } else Toast.makeText(context, "Failed to sync", Toast.LENGTH_SHORT).show()
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("SYNC") }
                                
                                val meta = remember { JioTvRepo.getPlaylistMeta(context) }
                                if (meta.channelCount > 0) {
                                    Spacer(Modifier.height(16.dp))
                                    Text("Playlist: ${meta.name}", fontSize = 12.sp)
                                    Text("Channels: ${meta.channelCount}", fontSize = 12.sp)
                                    val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(meta.lastUpdated))
                                    Text("Last Updated: $dateStr", fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // Local File Mode
                        item {
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.35f)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp)).padding(20.dp)) {
                                Text("Local File Mode", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { /* Import Logic */ }) { Text("Import") }
                                    OutlinedButton(onClick = { /* Replace Logic */ }) { Text("Replace") }
                                    OutlinedButton(onClick = { /* Export Logic */ }) { Text("Export") }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // Manual Mapping UI
                        item {
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.35f)).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp)).padding(20.dp)) {
                                Text("Map Jio Channels", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                var jioQuery by remember { mutableStateOf("") }
                                var customUrl by remember { mutableStateOf("") }
                                var jioId by remember { mutableStateOf("") }
                                
                                OutlinedTextField(value = jioQuery, onValueChange = { jioQuery = it }, label = { Text("Search Jio Channel") }, modifier = Modifier.fillMaxWidth())
                                // Dummy selection logic for UX completeness
                                val match = allChannels.find { it.title.contains(jioQuery, true) }
                                if (match != null && jioQuery.isNotBlank()) {
                                    Text("Target: ${match.title}", color = Color.Green, fontSize = 12.sp)
                                    jioId = match.defaultChannelId
                                }
                                
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(value = customUrl, onValueChange = { customUrl = it }, label = { Text("Paste Stream URL or M3U Name") }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    enabled = jioId.isNotBlank() && customUrl.isNotBlank(),
                                    onClick = {
                                        val isUrl = customUrl.startsWith("http")
                                        JioTvRepo.saveManualMapping(context, jioId, match!!.title, if (!isUrl) customUrl else null, if (isUrl) customUrl else null)
                                        smartCache = JioTvRepo.getChannelCacheMap(context)
                                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                    }, modifier = Modifier.fillMaxWidth()
                                ) { Text("Save Mapping") }
                            }
                            Spacer(Modifier.height(24.dp))
                        }

                        // Saved Streams Status
                        item { Text("Mapping Status", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start)) }
                        
                        items(smartCache.values.toList()) { mapping ->
                            val channel = allChannels.find { it.defaultChannelId == mapping.channelId }
                            if (channel != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(channel.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                Text("Mapped: ${mapping.mappedM3uName ?: mapping.manualStreamUrl ?: "Original Jio"}", fontSize = 11.sp, color = Color.Gray)
                                                Text("Source: ${mapping.preferredSource.name} | Status: ${mapping.status.name}", fontSize = 10.sp, color = if(mapping.status == MappingStatus.BROKEN) Color.Red else MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = { playChannel(channel, channel.defaultChannelId) }) { Text("Test") }
                                            TextButton(onClick = { JioTvRepo.removeManualMapping(context, mapping.channelId); smartCache = JioTvRepo.getChannelCacheMap(context) }) { Text("Delete", color = Color.Red) }
                                            TextButton(onClick = { 
                                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cb.setPrimaryClip(ClipData.newPlainText("URL", mapping.manualStreamUrl ?: mapping.lastSuccessfulUrl ?: ""))
                                                Toast.makeText(context, "Copied URL", Toast.LENGTH_SHORT).show()
                                            }) { Text("Copy") }
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

@Composable
fun LiveChannelRowItem(
    channel: LiveChannelItem, 
    currentActiveId: String,
    entry: ChannelCacheEntry?,
    onPlayRequested: (channelId: String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 1.02f else 1f, animationSpec = tween(180))

    Card(
        modifier = Modifier.fillMaxWidth().scale(scale).clickable(interactionSource = interactionSource, indication = LocalIndication.current) { onPlayRequested(currentActiveId) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = channel.logoUrl, contentDescription = channel.title,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(4.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${channel.category} • ${channel.variants.find { it.channelId == currentActiveId }?.language ?: channel.defaultLanguage}", 
                         fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    
                    if (entry?.status == MappingStatus.BROKEN) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(color = Color.Red.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text(" ⚠ Broken ", fontSize = 9.sp, color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                        }
                    } else if (entry?.preferredSource == PlaybackSource.M3U || entry?.preferredSource == PlaybackSource.MANUAL_URL) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text(" ✓ Synced ", fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                        }
                    }
                }
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.padding(start = 8.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}