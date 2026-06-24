package app.marlboroadvance.mpvex.cinetv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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

    var smartCache by remember { mutableStateOf(mapOf<String, ChannelCacheEntry>()) }
    var pendingFeedbackChannel by remember { mutableStateOf<LiveChannelItem?>(null) }

    var showDiagnostics by remember { mutableStateOf(false) }
    var diagnosticRunning by remember { mutableStateOf(false) }
    var diagnosticsReport by remember { mutableStateOf(listOf<DiagnosticResult>()) }
    var exportExpanded by remember { mutableStateOf(false) }

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

    if (pendingFeedbackChannel != null) {
        val channelToFeed = pendingFeedbackChannel!!
        LaunchedEffect(channelToFeed) {
            android.app.AlertDialog.Builder(context)
                .setTitle("Playback Feedback")
                .setMessage("Did ${channelToFeed.title} play correctly?")
                .setCancelable(false)
                .setPositiveButton("👍 Working") { _, _ -> 
                    JioTvRepo.setUserFeedback(context, channelToFeed.defaultChannelId, true)
                    smartCache = JioTvRepo.getChannelCacheMap(context)
                    pendingFeedbackChannel = null 
                }
                .setNegativeButton("👎 Not Working") { _, _ -> 
                    JioTvRepo.setUserFeedback(context, channelToFeed.defaultChannelId, false)
                    smartCache = JioTvRepo.getChannelCacheMap(context)
                    pendingFeedbackChannel = null 
                }
                .setNeutralButton("Dismiss") { _, _ -> 
                    pendingFeedbackChannel = null 
                }
                .show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                            shape = CircleShape,
                            border = FilterChipDefaults.filterChipBorder(borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        )
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = availableLanguages) { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = { selectedLanguage = lang },
                            label = { Text(lang, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                            ),
                            shape = CircleShape,
                            border = FilterChipDefaults.filterChipBorder(borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Developer Mode", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Switch(checked = showDiagnostics, onCheckedChange = { showDiagnostics = it }, modifier = Modifier.scale(0.7f))
                }

                if (showDiagnostics) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !diagnosticRunning && allChannels.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            onClick = {
                                diagnosticRunning = true
                                diagnosticsReport = emptyList()
                                scope.launch {
                                    for (channel in allChannels) {
                                        val targetId = channel.defaultChannelId
                                        val startTime = System.currentTimeMillis()
                                        try {
                                            val resolved = JioTvRepo.getResolvedLiveUrl(context, targetId, channel.title)
                                            diagnosticsReport = diagnosticsReport + DiagnosticResult(
                                                channelName = channel.title, channelId = targetId,
                                                category = channel.category, language = channel.defaultLanguage,
                                                result = "Working", failureReason = "None", httpStatus = "200",
                                                timeTakenMs = System.currentTimeMillis() - startTime, resolvedUrl = resolved.url
                                            )
                                        } catch (e: Exception) {
                                            val parts = e.message?.split("|")
                                            val reason = parts?.getOrNull(0) ?: "Playback Failed"
                                            val status = parts?.getOrNull(1) ?: "500"
                                            
                                            if (reason.contains("Subscription", true) || status == "403" || status == "3012") {
                                                globalPaidChannels[targetId] = true
                                            }

                                            diagnosticsReport = diagnosticsReport + DiagnosticResult(
                                                channelName = channel.title, channelId = targetId,
                                                category = channel.category, language = channel.defaultLanguage,
                                                result = "Failed", failureReason = reason, httpStatus = status,
                                                timeTakenMs = System.currentTimeMillis() - startTime
                                            )
                                        }
                                        delay(200)
                                    }
                                    diagnosticRunning = false
                                }
                            }
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (diagnosticRunning) "Testing..." else "Test All", fontSize = 11.sp)
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            var menuOpen by remember { mutableStateOf(false) }
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !diagnosticRunning && allChannels.isNotEmpty(),
                                onClick = { menuOpen = true }
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Export M3U", fontSize = 11.sp)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Export Working Streams") },
                                    onClick = { 
                                        menuOpen = false
                                        Toast.makeText(context, "Exporting streams...", Toast.LENGTH_LONG).show()
                                        scope.launch { JioTvRepo.exportWorkingStreamsAsM3u(context, allChannels) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Only Premium") },
                                    onClick = { 
                                        menuOpen = false
                                        scope.launch { JioTvRepo.exportWorkingStreamsAsM3u(context, allChannels.filter { globalPaidChannels[it.defaultChannelId] == true }) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Only Free") },
                                    onClick = { 
                                        menuOpen = false
                                        scope.launch { JioTvRepo.exportWorkingStreamsAsM3u(context, allChannels.filter { globalPaidChannels[it.defaultChannelId] != true }) }
                                    }
                                )
                            }
                        }
                    }

                    if (diagnosticsReport.isNotEmpty() && !diagnosticRunning) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val copyFunc = { items: List<DiagnosticResult>, title: String ->
                                val text = buildString {
                                    appendLine("Channel Name\tCategory\tLanguage\tResult\tFailure Reason\tHTTP Status")
                                    appendLine("-------------------------------------------------------------------------")
                                    items.forEach { appendLine("${it.channelName}\t${it.category}\t${it.language}\t${it.result}\t${it.failureReason}\t${it.httpStatus}") }
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(title, text))
                                Toast.makeText(context, "Copied ${items.size} channels", Toast.LENGTH_SHORT).show()
                            }
                            
                            OutlinedButton(onClick = { copyFunc(diagnosticsReport, "Full Report") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                Text(" Full", fontSize = 9.sp)
                            }
                            OutlinedButton(onClick = { copyFunc(diagnosticsReport.filter { it.result == "Working" }, "Working") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                Text(" Working", fontSize = 9.sp)
                            }
                            OutlinedButton(onClick = { copyFunc(diagnosticsReport.filter { it.result == "Failed" }, "Failed") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                Text(" Failed", fontSize = 9.sp)
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
                            val isM3uFallback = entry?.preferredSource == PlaybackSource.M3U

                            LiveChannelRowItem(
                                channel = channel,
                                preSelectedLanguage = if (selectedLanguage != "All Languages") selectedLanguage else channel.defaultLanguage,
                                isM3uFallback = isM3uFallback,
                                confidenceScore = entry?.confidenceScore ?: 0,
                                isManualMapping = entry?.isManualMapping ?: false,
                                onPlayRequested = { id ->
                                    scope.launch {
                                        try {
                                            val resolved = JioTvRepo.getResolvedLiveUrl(context, id, channel.title)
                                            smartCache = JioTvRepo.getChannelCacheMap(context) 
                                            pendingFeedbackChannel = channel
                                            
                                            // Make headers available globally for the PlayerActivity
                                            JioTvRepo.lastResolvedHeaders = resolved.headers
                                            
                                            onPlayRequested(resolved.url, channel.title)
                                        } catch (e: Exception) {
                                            val parts = e.message?.split("|")
                                            val reason = parts?.getOrNull(0) ?: "Playback Failed"
                                            if (reason.contains("Subscription", true) || parts?.getOrNull(1) == "403" || parts?.getOrNull(1) == "3012") {
                                                globalPaidChannels[id] = true
                                            }
                                            Toast.makeText(context, "$reason ❌", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
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
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                                .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)), RoundedCornerShape(24.dp))
                                .padding(24.dp)
                        ) {
                            Text("M3U Sync Manager", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Manually link a JioTV channel to an exact M3U alias.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))

                            var jioSearch by remember { mutableStateOf("") }
                            var m3uSearch by remember { mutableStateOf("") }
                            
                            var selectedJioId by remember { mutableStateOf("") }
                            var jioExpanded by remember { mutableStateOf(false) }

                            ExposedDropdownMenuBox(expanded = jioExpanded, onExpandedChange = { jioExpanded = it }) {
                                OutlinedTextField(
                                    value = jioSearch, onValueChange = { jioSearch = it; jioExpanded = true },
                                    label = { Text("Search JioTV Channel") }, modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true
                                )
                                val jioMatches = allChannels.filter { it.title.contains(jioSearch, true) }.take(5)
                                if (jioMatches.isNotEmpty() && jioExpanded) {
                                    ExposedDropdownMenu(expanded = jioExpanded, onDismissRequest = { jioExpanded = false }) {
                                        jioMatches.forEach { ch ->
                                            DropdownMenuItem(
                                                text = { Text(ch.title) },
                                                onClick = { jioSearch = ch.title; selectedJioId = ch.defaultChannelId; jioExpanded = false }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            var m3uExpanded by remember { mutableStateOf(false) }
                            val m3uList = remember { JioTvRepo.loadM3uFallback(context) }

                            ExposedDropdownMenuBox(expanded = m3uExpanded, onExpandedChange = { m3uExpanded = it }) {
                                OutlinedTextField(
                                    value = m3uSearch, onValueChange = { m3uSearch = it; m3uExpanded = true },
                                    label = { Text("Search M3U Channel") }, modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true
                                )
                                val m3uMatches = m3uList.filter { it.name.contains(m3uSearch, true) }.take(5)
                                if (m3uMatches.isNotEmpty() && m3uExpanded) {
                                    ExposedDropdownMenu(expanded = m3uExpanded, onDismissRequest = { m3uExpanded = false }) {
                                        m3uMatches.forEach { ch ->
                                            DropdownMenuItem(text = { Text(ch.name) }, onClick = { m3uSearch = ch.name; m3uExpanded = false })
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedJioId.isNotBlank() && m3uSearch.isNotBlank(),
                                onClick = {
                                    JioTvRepo.saveManualMapping(context, selectedJioId, jioSearch, m3uSearch)
                                    smartCache = JioTvRepo.getChannelCacheMap(context)
                                    Toast.makeText(context, "Linked $jioSearch to $m3uSearch", Toast.LENGTH_SHORT).show()
                                    jioSearch = ""; m3uSearch = ""; selectedJioId = ""
                                }
                            ) { Text("Save Manual Mapping") }

                            Spacer(Modifier.height(16.dp))
                            Row {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val data = JioTvRepo.exportMappings(context)
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Mappings", data))
                                        Toast.makeText(context, "Copied config to clipboard", Toast.LENGTH_SHORT).show()
                                    }
                                ) { Text("Export") }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                                        if (!text.isNullOrBlank() && text.startsWith("{")) {
                                            JioTvRepo.importMappings(context, text)
                                            smartCache = JioTvRepo.getChannelCacheMap(context)
                                            Toast.makeText(context, "Imported mappings", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "No valid JSON in clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) { Text("Import") }
                            }

                            Spacer(Modifier.height(16.dp))
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            Spacer(Modifier.height(8.dp))

                            Text("Existing Mappings:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            val manuals = smartCache.values.filter { it.isManualMapping }
                            if (manuals.isEmpty()) {
                                Text("No manual mappings found.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                manuals.forEach { mapping ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Jio: ${mapping.normalizedName}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text("M3U: ${mapping.mappedM3uName}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                        TextButton(onClick = { 
                                            JioTvRepo.removeManualMapping(context, mapping.channelId) 
                                            smartCache = JioTvRepo.getChannelCacheMap(context)
                                        }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
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
    preSelectedLanguage: String,
    isM3uFallback: Boolean,
    confidenceScore: Int,
    isManualMapping: Boolean,
    onPlayRequested: (channelId: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var currentActiveId by remember { mutableStateOf(channel.getIdForLanguage(preSelectedLanguage)) }
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
                            val label = if (isManualMapping) "✓ Synced" else "🔄 Synced ($confidenceScore%)"
                            Text(" $label ", fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                        }
                    }
                }
            }

            if (channel.variants.size > 1) {
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.Language, contentDescription = "Language", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        channel.variants.forEach { variant ->
                            DropdownMenuItem(
                                text = { Text(variant.language, fontWeight = if (currentActiveId == variant.channelId) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { 
                                    currentActiveId = variant.channelId
                                    expanded = false 
                                    onPlayRequested(currentActiveId) 
                                }
                            )
                        }
                    }
                }
            }
            
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.padding(start = 8.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}