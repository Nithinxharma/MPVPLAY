package app.marlboroadvance.mpvex.cinetv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.ui.browser.components.BrowserTopBar
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

    var activeSubTab by remember { mutableStateOf(LiveTab.CHANNELS) }
    var userAuthed by remember { mutableStateOf(JioTvRepo.isUserLoggedIn()) }

    var allChannels by remember { mutableStateOf(emptyList<LiveChannelItem>()) }
    var isLoading by remember { mutableStateOf(true) }
    var fetchError by remember { mutableStateOf<String?>(null) } 

    var isSearchVisible by remember { mutableStateOf(false) }
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
            fetchError = null
            allChannels = JioTvRepo.fetchLiveChannelsFromAssets(context)
        } catch (e: Exception) {
            fetchError = e.message ?: "An unknown error occurred while downloading channels"
        } finally {
            isLoading = false
        }
    }

    val availableGenres = remember(allChannels) { listOf("All") + allChannels.map { it.category }.distinct().sorted() }
    val availableLanguages = remember(allChannels) { listOf("All Languages") + allChannels.flatMap { it.variants.map { v -> v.language } }.distinct().sorted() }

    val filteredChannels = remember(allChannels, selectedGenre, selectedLanguage, localSearchQuery, searchQuery) {
        allChannels.filter { channel ->
            val matchesGenre = selectedGenre == "All" || channel.category == selectedGenre
            val matchesLanguage = selectedLanguage == "All Languages" || channel.variants.any { it.language.equals(selectedLanguage, true) }

            val activeSearch = if (isSearchVisible) localSearchQuery else searchQuery
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

    val playChannel: (LiveChannelItem, String) -> Unit = { channel, idToPlay ->
        scope.launch {
            try {
                val resolved = JioTvRepo.getResolvedLiveUrl(context, idToPlay, channel.title)
                JioTvRepo.lastResolvedHeaders = resolved.headers
                onPlayRequested(resolved.url, channel.title)
            } catch (e: Exception) {
                Toast.makeText(context, "Playback Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (m3uToLink != null) {
        ModalBottomSheet(onDismissRequest = { m3uToLink = null }) {
            Column(Modifier.padding(16.dp).fillMaxSize()) {
                Text("Associate With Jio Channel", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(m3uToLink!!.name, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = linkSearchQuery,
                    onValueChange = { linkSearchQuery = it },
                    label = { Text("Search Jio Channels") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                
                // Smart Filter Logic
                val smartKeyword = remember(m3uToLink) { JioTvRepo.generateSmartFilterKeyword(m3uToLink!!.name) }
                val currentLinkSearch = linkSearchQuery.ifBlank { smartKeyword }
                
                val filteredJioForLink = allChannels.filter { 
                    it.title.lowercase().contains(currentLinkSearch.lowercase()) ||
                    it.category.lowercase().contains(currentLinkSearch.lowercase())
                }.take(20)

                if (filteredJioForLink.isEmpty()) {
                    Text("No matches found. Try clearing the search box.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                } else {
                    LazyColumn {
                        item {
                            if (linkSearchQuery.isBlank()) {
                                Text("Smart Suggestions for '$smartKeyword'", color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                            }
                        }
                        items(filteredJioForLink) { jioCh ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    JioTvRepo.saveManualMapping(context, jioCh.defaultChannelId, m3uToLink!!.name, m3uToLink!!.url)
                                    smartCache = JioTvRepo.getChannelCacheMap(context)
                                    Toast.makeText(context, "Mapping saved permanently!", Toast.LENGTH_SHORT).show()
                                    m3uToLink = null
                                    linkSearchQuery = ""
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
                        item {
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { linkSearchQuery = " " }, modifier = Modifier.fillMaxWidth()) {
                                Text("Show All Channels")
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                BrowserTopBar(
                    title = "CineTV",
                    isInSelectionMode = false,
                    selectedCount = 0,
                    totalCount = allChannels.size,
                    onCancelSelection = {},
                    isHomeScreen = true,
                    onSearchClick = { isSearchVisible = !isSearchVisible },
                    actions = {
                        IconButton(onClick = { activeSubTab = LiveTab.JIO_LOGIN }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )

                AnimatedVisibility(visible = isSearchVisible) {
                    OutlinedTextField(
                        value = localSearchQuery,
                        onValueChange = { localSearchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search by name, category, language...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                }

                TabRow(
                    selectedTabIndex = activeSubTab.ordinal,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    LiveTab.values().forEach { tab ->
                        Tab(
                            selected = activeSubTab == tab,
                            onClick = { activeSubTab = tab },
                            text = { Text(tab.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (activeSubTab) {
                LiveTab.CHANNELS -> {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = availableGenres) { genre ->
                            FilterChip(
                                selected = selectedGenre == genre,
                                onClick = { selectedGenre = genre },
                                label = { Text(genre, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                ),
                                shape = CircleShape
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = languageExpanded, 
                            onExpandedChange = { languageExpanded = it }, 
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedLanguage, onValueChange = {}, readOnly = true,
                                label = { Text("Select Language", fontSize = 12.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                            )
                            ExposedDropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                                availableLanguages.forEach { lang ->
                                    DropdownMenuItem(text = { Text(lang, fontSize = 12.sp) }, onClick = { selectedLanguage = lang; languageExpanded = false })
                                }
                            }
                        }
                    }

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (fetchError != null) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(fetchError!!, color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(1), modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (userAuthed) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Authenticated Session Secure", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Button(
                                        onClick = { JioTvRepo.logout(context); userAuthed = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) { Text("Revoke Login Session") }
                                } else {
                                    var mobile by remember { mutableStateOf("") }
                                    var otpCode by remember { mutableStateOf("") }
                                    var isOtpSent by remember { mutableStateOf(false) }
                                    
                                    OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text("Mobile Number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    if (isOtpSent) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        OutlinedTextField(value = otpCode, onValueChange = { otpCode = it }, label = { Text("Enter OTP Code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            scope.launch {
                                                if (!isOtpSent) {
                                                    try {
                                                        val sent = JioTvRepo.requestOtp(mobile)
                                                        isOtpSent = sent
                                                        if (sent) Toast.makeText(context, "OTP Sent", Toast.LENGTH_SHORT).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, e.message ?: "Failed", Toast.LENGTH_LONG).show()
                                                    }
                                                } else {
                                                    try {
                                                        val success = JioTvRepo.verifyOtp(context, mobile, otpCode)
                                                        if (success) userAuthed = true
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, e.message ?: "Verification Failed", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        }
                                    ) { Text(if (!isOtpSent) "Send OTP" else "Verify Token") }
                                }
                            }
                        }
                        
                        item { Spacer(Modifier.height(24.dp)) }
                        
                        item {
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)).padding(20.dp)) {
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
                                            m3uEntries = JioTvRepo.loadM3uFallback(context)
                                            Toast.makeText(context, "✓ Synced Successfully", Toast.LENGTH_LONG).show()
                                        } else Toast.makeText(context, "Failed to sync", Toast.LENGTH_SHORT).show()
                                    }
                                }, modifier = Modifier.fillMaxWidth()) { Text("SYNC") }
                                
                                val meta = remember(m3uEntries) { JioTvRepo.getPlaylistMeta(context) }
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

                        item {
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)).padding(20.dp)) {
                                Text("Local File Mode", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { 
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val text = cb.primaryClip?.getItemAt(0)?.text?.toString()
                                        if (!text.isNullOrBlank() && text.contains("#EXTM3U")) {
                                            JioTvRepo.saveM3uText(context, text)
                                            m3uEntries = JioTvRepo.loadM3uFallback(context)
                                            Toast.makeText(context, "Imported from clipboard!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "No valid M3U in clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    }) { Text("Import/Replace") }
                                    OutlinedButton(onClick = { 
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("M3U", JioTvRepo.readM3uText(context)))
                                        Toast.makeText(context, "Exported to clipboard!", Toast.LENGTH_SHORT).show()
                                    }) { Text("Export") }
                                    OutlinedButton(onClick = { 
                                        JioTvRepo.reloadM3uParser()
                                        m3uEntries = JioTvRepo.loadM3uFallback(context)
                                        Toast.makeText(context, "Parser Reloaded", Toast.LENGTH_SHORT).show()
                                    }) { Text("Reload") }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        item {
                            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)).padding(20.dp)) {
                                Text("Mapped Channels", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                val verifiedMappings = smartCache.values.filter { it.isManualMapping }
                                if (verifiedMappings.isEmpty()) {
                                    Text("No manual mappings yet.", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                                } else {
                                    verifiedMappings.forEach { mapping ->
                                        val channel = allChannels.find { it.defaultChannelId == mapping.channelId }
                                        if (channel != null) {
                                            Card(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.2f))
                                            ) {
                                                Column(Modifier.padding(12.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                                                        Spacer(Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(channel.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                            Text("Mapped: ${mapping.mappedM3uName ?: "Direct URL"}", fontSize = 11.sp, color = Color.Gray)
                                                        }
                                                    }
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                        TextButton(onClick = { playChannel(channel, channel.defaultChannelId) }) { Text("Play") }
                                                        TextButton(onClick = { 
                                                            JioTvRepo.removeManualMapping(context, mapping.channelId)
                                                            smartCache = JioTvRepo.getChannelCacheMap(context)
                                                            Toast.makeText(context, "Mapping Removed!", Toast.LENGTH_SHORT).show()
                                                        }) { Text("Delete", color = Color.Red) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }

                        item {
                            Text("IN.M3U Channel List", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        items(m3uEntries) { entry ->
                            var testResult by remember { mutableStateOf("") }
                            var isTesting by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(entry.name, fontWeight = FontWeight.Bold)
                                    Text(entry.url, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.Gray, fontSize = 10.sp)
                                    if (testResult.isNotBlank()) {
                                        Text("Status: $testResult", color = if (testResult == "Working") Color.Green else Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { 
                                            linkSearchQuery = JioTvRepo.generateSmartFilterKeyword(entry.name)
                                            m3uToLink = entry 
                                        }, modifier = Modifier.weight(1f)) { Text("LINK") }
                                        
                                        OutlinedButton(onClick = { 
                                            isTesting = true
                                            testResult = "Testing..."
                                            scope.launch {
                                                testResult = JioTvRepo.testStreamUrl(entry.url, entry.headers)
                                                isTesting = false
                                            }
                                        }, modifier = Modifier.weight(1f), enabled = !isTesting) { 
                                            Text(if (isTesting) "..." else "TEST") 
                                        }
                                        
                                        IconButton(onClick = { /* Delete visually from list (requires rewriting IN.M3U technically, so disabled for safety) */ }) { 
                                            Icon(Icons.Default.Delete, null, tint = Color.Gray) 
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onPlayRequested(currentActiveId) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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