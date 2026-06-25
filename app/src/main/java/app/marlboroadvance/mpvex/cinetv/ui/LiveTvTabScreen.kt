package app.marlboroadvance.mpvex.cinetv.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.marlboroadvance.mpvex.cinetv.data.JioTvRepo
import app.marlboroadvance.mpvex.cinetv.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvTabScreen(
    searchQuery: String, 
    onPlayRequested: (streamUrl: String, channelTitle: String) -> Unit 
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeSubTab by remember { mutableStateOf(LiveTab.CHANNELS) }
    var userAuthed by remember { mutableStateOf(JioTvRepo.isUserLoggedIn()) }
    var allChannels by remember { mutableStateOf(emptyList<LiveChannelItem>()) }
    var isLoading by remember { mutableStateOf(true) }

    var isSearchActive by remember { mutableStateOf(false) }
    var localSearchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("All") }
    var selectedLanguage by remember { mutableStateOf("All Languages") }
    var languageExpanded by remember { mutableStateOf(false) }

    var smartCache by remember { mutableStateOf(mapOf<String, ChannelCacheEntry>()) }
    var m3uEntries by remember { mutableStateOf(emptyList<JioTvRepo.M3uEntry>()) }

    // Bottom Sheet State
    var m3uToLink by remember { mutableStateOf<JioTvRepo.M3uEntry?>(null) }
    var linkSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        JioTvRepo.initTokens(context)
        userAuthed = JioTvRepo.isUserLoggedIn()
        smartCache = JioTvRepo.getChannelCacheMap(context)
        m3uEntries = JioTvRepo.loadM3uFallback(context)
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
            val activeSearch = if (isSearchActive) localSearchQuery else searchQuery
            val searchLower = activeSearch.trim().lowercase()
            
            val cacheEntry = smartCache[channel.defaultChannelId]
            val aliasName = cacheEntry?.mappedM3uName?.lowercase() ?: ""

            val matchesSearch = searchLower.isBlank() || 
                channel.title.lowercase().contains(searchLower) || 
                channel.category.lowercase().contains(searchLower) ||
                aliasName.contains(searchLower) ||
                channel.variants.any { it.language.lowercase().contains(searchLower) }

            matchesGenre && matchesLanguage && matchesSearch
        }
    }

    if (m3uToLink != null) {
        ModalBottomSheet(onDismissRequest = { m3uToLink = null }) {
            Column(Modifier.padding(16.dp).fillMaxSize()) {
                Text("Associate With Jio Channel", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = linkSearchQuery,
                    onValueChange = { linkSearchQuery = it },
                    label = { Text("Search Channels") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                
                val filteredJioForLink = allChannels.filter { 
                    it.title.lowercase().contains(linkSearchQuery.lowercase()) ||
                    it.category.lowercase().contains(linkSearchQuery.lowercase())
                }.take(20)

                LazyColumn {
                    items(filteredJioForLink) { jioCh ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                JioTvRepo.saveManualMapping(context, jioCh.defaultChannelId, m3uToLink!!.name, m3uToLink!!.url)
                                smartCache = JioTvRepo.getChannelCacheMap(context)
                                Toast.makeText(context, "Linked successfully!", Toast.LENGTH_SHORT).show()
                                m3uToLink = null
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(model = jioCh.logoUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(jioCh.title, fontWeight = FontWeight.Bold)
                                Text("${jioCh.category} • ${jioCh.defaultLanguage}", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Simulated BrowserTopBar mimicking CineHub layout
            Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f))) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("CineTV", fontWeight = FontWeight.Black, fontSize = 28.sp)
                        Text("Live TV", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Row {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                        IconButton(onClick = { activeSubTab = LiveTab.SETTINGS }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }
                }
                TabRow(selectedTabIndex = activeSubTab.ordinal, containerColor = Color.Transparent, divider = {}) {
                    LiveTab.values().forEach { tab ->
                        Tab(selected = activeSubTab == tab, onClick = { activeSubTab = tab }, text = { Text(tab.label, fontWeight = FontWeight.Bold) })
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (activeSubTab) {
                LiveTab.CHANNELS -> {
                    AnimatedVisibility(visible = isSearchActive) {
                        OutlinedTextField(
                            value = localSearchQuery,
                            onValueChange = { localSearchQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text("Search by name, alias, language...") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true
                        )
                    }

                    // FIXED LAZYROW (No border parameter)
                    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items = availableGenres) { genre ->
                            FilterChip(
                                selected = selectedGenre == genre,
                                onClick = { selectedGenre = genre },
                                label = { Text(genre, fontSize = 12.sp) },
                                shape = CircleShape
                            )
                        }
                    }

                    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items = availableLanguages) { lang ->
                            FilterChip(
                                selected = selectedLanguage == lang,
                                onClick = { selectedLanguage = lang },
                                label = { Text(lang, fontSize = 12.sp) },
                                shape = CircleShape
                            )
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
                                    onPlayRequested = { id ->
                                        scope.launch {
                                            try {
                                                val resolved = JioTvRepo.getResolvedLiveUrl(context, id, channel.title)
                                                JioTvRepo.lastResolvedHeaders = resolved.headers
                                                onPlayRequested(resolved.url, channel.title)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Playback Failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                LiveTab.SETTINGS -> {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Text("Jio Authentication", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(8.dp))
                                    if (userAuthed) {
                                        Text("Status: Logged In", color = Color.Green)
                                        Button(onClick = { JioTvRepo.logout(context); userAuthed = false }, modifier = Modifier.fillMaxWidth()) { Text("Logout") }
                                    } else {
                                        Text("Status: Logged Out", color = Color.Red)
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        item {
                            Text("IN.M3U Channel List", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                        }

                        items(m3uEntries.take(100)) { entry ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f))
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(entry.name, fontWeight = FontWeight.Bold)
                                    Text(entry.url, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray, fontSize = 10.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { 
                                            linkSearchQuery = JioTvRepo.generateSmartFilterKeyword(entry.name)
                                            m3uToLink = entry 
                                        }, modifier = Modifier.weight(1f)) { Text("LINK") }
                                        
                                        OutlinedButton(onClick = { 
                                            // Mock test button
                                            Toast.makeText(context, "Testing playback...", Toast.LENGTH_SHORT).show()
                                        }, modifier = Modifier.weight(1f)) { Text("TEST") }
                                        
                                        IconButton(onClick = { /* Delete Logic */ }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onPlayRequested(currentActiveId) },
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
                    
                    if (entry?.isManualMapping == true) {
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