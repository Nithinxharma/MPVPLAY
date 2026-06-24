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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import app.marlboroadvance.mpvex.cinetv.data.JioTvRepo
import app.marlboroadvance.mpvex.cinetv.model.*

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

    var localSearchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("All") }
    var selectedLanguage by remember { mutableStateOf("All Languages") }
    var languageExpanded by remember { mutableStateOf(false) }

    var smartCache by remember { mutableStateOf(mapOf<String, ChannelCacheEntry>()) }
    
    // Core Feedback Detector Mechanism
    var pendingFeedbackData by remember { mutableStateOf<Pair<LiveChannelItem, String>?>(null) }

    LaunchedEffect(Unit) {
        JioTvRepo.initTokens(context)
        userAuthed = JioTvRepo.isUserLoggedIn()
        smartCache = JioTvRepo.getChannelCacheMap(context)

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

            val activeSearch = if (localSearchQuery.isNotBlank()) localSearchQuery else searchQuery
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
                
                val cacheEntry = smartCache[idToPlay]
                if (cacheEntry?.userVerified != true) {
                    pendingFeedbackData = channel to resolved.url
                } else {
                    pendingFeedbackData = null
                }
                
                onPlayRequested(resolved.url, channel.title)
            } catch (e: Exception) {
                Toast.makeText(context, "No working streams found for ${channel.title}", Toast.LENGTH_SHORT).show()
                pendingFeedbackData = null
            }
        }
    }

    if (pendingFeedbackData != null) {
        val (channelToFeed, playedUrl) = pendingFeedbackData!!
        AlertDialog(
            onDismissRequest = { /* Must force interaction */ },
            title = { Text("Playback Feedback") },
            text = { Text("Did ${channelToFeed.title} play correctly?\n\n(Wait for stream to load. Video must render and audio must start.)") },
            confirmButton = {
                Button(onClick = {
                    JioTvRepo.handleUserPlaybackFeedback(context, channelToFeed.defaultChannelId, true, playedUrl, channelToFeed.title)
                    smartCache = JioTvRepo.getChannelCacheMap(context)
                    pendingFeedbackData = null
                }) { Text("Yes") }
            },
            dismissButton = {
                Button(onClick = {
                    JioTvRepo.handleUserPlaybackFeedback(context, channelToFeed.defaultChannelId, false, playedUrl, channelToFeed.title)
                    smartCache = JioTvRepo.getChannelCacheMap(context)
                    pendingFeedbackData = null
                    
                    Toast.makeText(context, "Searching alternative stream...", Toast.LENGTH_SHORT).show()
                    playChannel(channelToFeed, channelToFeed.defaultChannelId) 
                }) { Text("No") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TabRow(selectedTabIndex = activeSubTab.ordinal, containerColor = Color.Transparent, divider = {}) {
            LiveTab.values().forEach { tab ->
                Tab(
                    selected = activeSubTab == tab,
                    onClick = { activeSubTab = tab },
                    text = { Text(tab.label, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }
        }

        when (activeSubTab) {
            LiveTab.CHANNELS -> {
                OutlinedTextField(
                    value = localSearchQuery,
                    onValueChange = { localSearchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search channels, categories, aliases...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    shape = CircleShape,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent
                    )
                )

                                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                                isM3uFallback = entry?.preferredSource == PlaybackSource.M3U,
                                confidenceScore = entry?.confidenceScore ?: 0,
                                isManualMapping = entry?.isManualMapping ?: false,
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
                                .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)), RoundedCornerShape(24.dp))
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
                    
                    item { Spacer(Modifier.height(32.dp)) }
                    
                    item {
                        Text("Saved Stream Mappings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                    }
                    
                    val verifiedMappings = smartCache.values.filter { it.userVerified }
                    if (verifiedMappings.isEmpty()) {
                        item { Text("No verified mappings yet.", color = Color.Gray, modifier = Modifier.padding(bottom = 16.dp)) }
                    } else {
                        items(verifiedMappings.toList()) { mapping ->
                            val channel = allChannels.find { it.defaultChannelId == mapping.channelId }
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(model = channel?.logoUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(channel?.title ?: mapping.channelId, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Mapped: ${mapping.mappedM3uName ?: "Jio Official"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    IconButton(onClick = { if (channel != null) playChannel(channel, channel.defaultChannelId) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = {
                                        JioTvRepo.resetMapping(context, mapping.channelId)
                                        smartCache = JioTvRepo.getChannelCacheMap(context)
                                        Toast.makeText(context, "Mapping Reset!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    
                    item { Spacer(Modifier.height(32.dp)) }
                    
                    item {
                        Text("Manage in.m3u", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                    }
                    
                    item {
                        var m3uText by remember { mutableStateOf("") }
                        var showEditor by remember { mutableStateOf(false) }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            Button(onClick = { 
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = cb.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!text.isNullOrBlank() && text.contains("#EXTM3U")) {
                                    JioTvRepo.saveM3uText(context, text)
                                    JioTvRepo.reloadM3uParser()
                                    Toast.makeText(context, "Imported from clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No valid M3U in clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("Import/Replace") }
                            
                            Button(onClick = { 
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("M3U", JioTvRepo.readM3uText(context)))
                                Toast.makeText(context, "Exported to clipboard!", Toast.LENGTH_SHORT).show()
                            }) { Text("Export") }
                            
                            Button(onClick = { 
                                JioTvRepo.reloadM3uParser()
                                Toast.makeText(context, "Parser Reloaded", Toast.LENGTH_SHORT).show()
                            }) { Text("Reload") }
                            
                            Button(onClick = { 
                                m3uText = JioTvRepo.readM3uText(context)
                                showEditor = !showEditor
                            }) { Text(if (showEditor) "Close Editor" else "Open as text") }
                        }
                        
                        if (showEditor) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = m3uText,
                                onValueChange = { m3uText = it },
                                modifier = Modifier.fillMaxWidth().height(300.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp)
                            )
                            Button(onClick = {
                                JioTvRepo.saveM3uText(context, m3uText)
                                JioTvRepo.reloadM3uParser()
                                Toast.makeText(context, "Saved & Reloaded", Toast.LENGTH_SHORT).show()
                                showEditor = false
                            }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text("Save Changes")
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
    isM3uFallback: Boolean,
    confidenceScore: Int,
    isManualMapping: Boolean,
    onPlayRequested: (channelId: String) -> Unit
) {
    val isPaid = globalPaidChannels[currentActiveId] == true

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onPlayRequested(currentActiveId) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = channel.logoUrl, contentDescription = channel.title,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(4.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${channel.category} • ${channel.variants.find { it.channelId == currentActiveId }?.language ?: channel.defaultLanguage}", 
                         fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    if (isPaid) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text(" 🟡 Paid ", fontSize = 9.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                        }
                    }
                    if (isM3uFallback) {
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