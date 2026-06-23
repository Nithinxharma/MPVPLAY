package app.marlboroadvance.mpvex.cinetv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import app.marlboroadvance.mpvex.cinetv.data.JioTvRepo
import app.marlboroadvance.mpvex.cinetv.model.LiveChannelItem
import app.marlboroadvance.mpvex.cinetv.model.LiveTab
import app.marlboroadvance.mpvex.cinetv.model.EpgData
import app.marlboroadvance.mpvex.cinetv.model.DiagnosticResult

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

    // Filters
    var selectedGenre by remember { mutableStateOf("All") }
    var selectedLanguage by remember { mutableStateOf("All Languages") }
    var languageExpanded by remember { mutableStateOf(false) }

    // Diagnostics
    var showDiagnostics by remember { mutableStateOf(false) }
    var diagnosticRunning by remember { mutableStateOf(false) }
    var diagnosticsReport by remember { mutableStateOf(listOf<DiagnosticResult>()) }

    LaunchedEffect(Unit) {
        JioTvRepo.initTokens(context)
        userAuthed = JioTvRepo.isUserLoggedIn()
        
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
    val availableLanguages = remember(allChannels) { 
        listOf("All Languages") + allChannels.flatMap { it.variants.map { v -> v.language } }.distinct().sorted() 
    }

    val filteredChannels = remember(allChannels, selectedGenre, selectedLanguage, searchQuery) {
        allChannels.filter { channel ->
            val matchesGenre = selectedGenre == "All" || channel.category == selectedGenre
            val matchesLanguage = selectedLanguage == "All Languages" || channel.variants.any { it.language == selectedLanguage }
            val searchLower = searchQuery.trim().lowercase()
            val matchesSearch = searchLower.isBlank() || 
                channel.title.lowercase().contains(searchLower) || 
                channel.category.lowercase().contains(searchLower) ||
                channel.variants.any { it.language.lowercase().contains(searchLower) }
            
            matchesGenre && matchesLanguage && matchesSearch
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
                
                // M3 Filter Chips for Genres
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableGenres) { genre ->
                        FilterChip(
                            selected = selectedGenre == genre,
                            onClick = { selectedGenre = genre },
                            label = { Text(genre, fontSize = 12.sp) }
                        )
                    }
                }

                // M3 Language Dropdown
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, bottom = 8.dp)) {
                    ExposedDropdownMenuBox(expanded = languageExpanded, onExpandedChange = { languageExpanded = it }, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedLanguage, onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                        )
                        ExposedDropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                            availableLanguages.forEach { lang ->
                                DropdownMenuItem(text = { Text(lang, fontSize = 12.sp) }, onClick = { selectedLanguage = lang; languageExpanded = false })
                            }
                        }
                    }
                }

                // Diagnostics Toggle (Developer Only)
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Developer Mode", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                    Switch(checked = showDiagnostics, onCheckedChange = { showDiagnostics = it }, modifier = Modifier.scale(0.7f))
                }

                if (showDiagnostics) {
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
                                        // This will now execute the identical robust check that manual playback uses
                                        JioTvRepo.getResolvedLiveUrl(context, targetId)
                                        diagnosticsReport = diagnosticsReport + DiagnosticResult(
                                            channelName = channel.title, channelId = targetId,
                                            category = channel.category, language = channel.defaultLanguage,
                                            result = "Working", failureReason = "None", httpStatus = "200",
                                            timeTakenMs = System.currentTimeMillis() - startTime
                                        )
                                    } catch (e: Exception) {
                                        val parts = e.message?.split("|")
                                        val reason = parts?.getOrNull(0) ?: "Playback Failed"
                                        val status = parts?.getOrNull(1) ?: "500"
                                        
                                        if (reason.contains("Subscription", true)) {
                                            globalPaidChannels[targetId] = true
                                        }

                                        diagnosticsReport = diagnosticsReport + DiagnosticResult(
                                            channelName = channel.title, channelId = targetId,
                                            category = channel.category, language = channel.defaultLanguage,
                                            result = "Failed", failureReason = reason, httpStatus = status,
                                            timeTakenMs = System.currentTimeMillis() - startTime
                                        )
                                    }
                                    delay(200) // gentle rate limiting
                                }
                                diagnosticRunning = false
                            }
                        }
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (diagnosticRunning) "Running Diagnostic Tests..." else "Test All Channels")
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
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(" Full", fontSize = 10.sp)
                            }
                            OutlinedButton(onClick = { copyFunc(diagnosticsReport.filter { it.result == "Working" }, "Working") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(" Working", fontSize = 10.sp)
                            }
                            OutlinedButton(onClick = { copyFunc(diagnosticsReport.filter { it.result == "Failed" }, "Failed") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text(" Failed", fontSize = 10.sp)
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
                        items(filteredChannels) { channel ->
                            LiveChannelRowItem(
                                channel = channel,
                                preSelectedLanguage = if (selectedLanguage != "All Languages") selectedLanguage else channel.defaultLanguage,
                                onPlayRequested = { id ->
                                    scope.launch {
                                        try {
                                            val streamLink = JioTvRepo.getResolvedLiveUrl(context, id)
                                            onPlayRequested(streamLink, channel.title)
                                        } catch (e: Exception) {
                                            val parts = e.message?.split("|")
                                            val reason = parts?.getOrNull(0) ?: "Playback Failed"
                                            if (reason.contains("Subscription", true)) {
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
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                            .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (userAuthed) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Green, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Authenticated Session Secure", fontWeight = FontWeight.Black, fontSize = 16.sp)
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
                                                if (sent) {
                                                    Toast.makeText(context, "OTP Sent Successfully ✅", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Failed to send OTP", Toast.LENGTH_LONG).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, e.message ?: "Failed to send OTP", Toast.LENGTH_LONG).show()
                                            }
                                        } else {
                                            try {
                                                val success = JioTvRepo.verifyOtp(context, mobile, otpCode)
                                                if (success) {
                                                    userAuthed = true
                                                    Toast.makeText(context, "Logged in Successfully ✅", Toast.LENGTH_LONG).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, e.message ?: "Verification Failed ❌", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            ) { Text(if (!isOtpSent) "Send OTP Challenge" else "Verify Token") }
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
    onPlayRequested: (channelId: String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var currentActiveId by remember { mutableStateOf(channel.getIdForLanguage(preSelectedLanguage)) }
    
    var epgData by remember { mutableStateOf<EpgData?>(null) }
    val isPaid = globalPaidChannels[currentActiveId] == true

    LaunchedEffect(currentActiveId) {
        epgData = JioTvRepo.fetchEpgForChannel(currentActiveId)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = channel.logoUrl, contentDescription = channel.title,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(4.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(channel.title, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${channel.category} • ${channel.variants.find { it.channelId == currentActiveId }?.language ?: channel.defaultLanguage}", 
                             fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        if (isPaid) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = Color(0xFFD4AF37).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                Text(" 🟡 Paid ", fontSize = 9.sp, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, modifier = Modifier.padding(2.dp))
                            }
                        }
                    }
                }
                
                IconButton(onClick = { /* Handle Favorites locally if needed */ }) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = "Favorite", modifier = Modifier.size(20.dp), tint = Color.Gray)
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
                
                IconButton(
                    onClick = { onPlayRequested(currentActiveId) },
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
                }
            }

            if (epgData != null) {
                Spacer(modifier = Modifier.height(10.dp))
                val format = SimpleDateFormat("HH:mm", Locale.getDefault())
                val startStr = format.format(Date(epgData!!.startTimeMs))
                val endStr = format.format(Date(epgData!!.endTimeMs))
                val now = System.currentTimeMillis()
                
                val progressRaw = if (epgData!!.endTimeMs > epgData!!.startTimeMs) {
                    (now - epgData!!.startTimeMs).toFloat() / (epgData!!.endTimeMs - epgData!!.startTimeMs).toFloat()
                } else 0f
                val animatedProgress by animateFloatAsState(targetValue = progressRaw.coerceIn(0f, 1f))

                Text(epgData!!.programName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("$startStr - $endStr", fontSize = 10.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Next: ${epgData!!.nextProgramName}  •  ${format.format(Date(epgData!!.nextStartTimeMs))}", fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}