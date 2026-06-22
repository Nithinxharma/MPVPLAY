package app.marlboroadvance.mpvex.cinetv.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch
import app.marlboroadvance.mpvex.cinetv.data.JioTvRepo
import app.marlboroadvance.mpvex.cinetv.model.LiveChannelItem
import app.marlboroadvance.mpvex.cinetv.model.LiveTab
import app.marlboroadvance.mpvex.cinehub.data.NfoScanner

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
    var selectedCategory by remember { mutableStateOf("All") }
    var isLoading by remember { mutableStateOf(true) }

    // Trigger Scanner & Fetch Data
    LaunchedEffect(Unit) {
        JioTvRepo.initTokens(context)
        userAuthed = JioTvRepo.isUserLoggedIn()
        
        isLoading = true
        allChannels = JioTvRepo.fetchLiveChannelsFromAssets(context)
        isLoading = false

        // Automatic NFO Scanner trigger as per CineHub activity launch logic
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val rootStorageDir = android.os.Environment.getExternalStorageDirectory()
            try {
                NfoScanner.scanDirectoryForMovies(rootStorageDir)
                NfoScanner.scanDirectoryForTvShows(rootStorageDir)
                android.util.Log.d("LiveTvScanner", "CineHub background scan triggered successfully.")
            } catch (e: Exception) {
                android.util.Log.e("LiveTvScanner", "Scan loop interrupted.", e)
            }
        }
    }

    val computedCategories = remember(allChannels) {
        listOf("All") + allChannels.map { it.category }.distinct().sorted()
    }

    val filteredChannels = remember(allChannels, selectedCategory, searchQuery) {
        allChannels.filter { channel ->
            val matchesCat = selectedCategory == "All" || channel.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() || channel.title.lowercase().contains(searchQuery.trim().lowercase())
            matchesCat && matchesSearch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = activeSubTab.ordinal,
            containerColor = Color.Transparent,
            divider = {}
        ) {
            LiveTab.values().forEach { tab ->
                val active = activeSubTab == tab
                Tab(
                    selected = active,
                    onClick = { activeSubTab = tab },
                    text = { Text(tab.label, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }
        }

        when (activeSubTab) {
            LiveTab.CHANNELS -> {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(computedCategories) { cat ->
                        val isActive = selectedCategory == cat
                        Surface(
                            modifier = Modifier.clickable { selectedCategory = cat },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Text(
                                text = cat, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
                    }
                } else if (filteredChannels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No live channels match current selection.", color = Color.Gray)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(1),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredChannels) { channel ->
                            LiveChannelRowItem(
                                channel = channel,
                                onClick = {
                                    scope.launch {
                                        val streamLink = JioTvRepo.getResolvedLiveUrl(channel.channelId)
                                        onPlayRequested(streamLink, channel.title)
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
                                            if (JioTvRepo.requestOtp(mobile)) isOtpSent = true
                                        } else {
                                            if (JioTvRepo.verifyOtp(context, mobile, otpCode)) userAuthed = true
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
fun LiveChannelRowItem(channel: LiveChannelItem, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f))
            .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = channel.logoUrl, 
                contentDescription = channel.title,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).padding(4.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.title, fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text(channel.currentProgram, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }
            Icon(Icons.Default.PlayArrow, contentDescription = null)
        }
    }
}
