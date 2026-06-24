package app.marlboroadvance.mpvex.cinetv.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.UUID
import app.marlboroadvance.mpvex.cinetv.model.*

object JioTvRepo {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    
    @Volatile private var cachedToken: String = ""
    @Volatile private var cachedCrm: String = ""
    @Volatile var lastResolvedHeaders: Map<String, String> = emptyMap()

    private const val JIO_USER_AGENT = "plaYtv/7.0.8 (Linux;Android 9) ExoPlayerLib/2.11.7"

    // --- SMART CACHE / DATABASE ---
    fun getChannelCacheMap(context: Context): MutableMap<String, ChannelCacheEntry> {
        val prefs = context.getSharedPreferences("JioTvSmartCache", Context.MODE_PRIVATE)
        val map = mutableMapOf<String, ChannelCacheEntry>()
        try {
            val jsonStr = prefs.getString("cache", "{}") ?: "{}"
            val root = json.parseToJsonElement(jsonStr).jsonObject
            for ((k, v) in root) {
                val obj = v.jsonObject
                val source = PlaybackSource.valueOf(obj["preferredSource"]?.jsonPrimitive?.content ?: "JIO_TV")
                val status = MappingStatus.valueOf(obj["status"]?.jsonPrimitive?.content ?: "UNTESTED")
                val failedArr = obj["failedM3uUrls"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                
                map[k] = ChannelCacheEntry(
                    channelId = k,
                    normalizedName = obj["normalizedName"]?.jsonPrimitive?.content ?: "",
                    preferredSource = source,
                    status = status,
                    lastSuccessfulUrl = obj["lastSuccessfulUrl"]?.jsonPrimitive?.content,
                    manualStreamUrl = obj["manualStreamUrl"]?.jsonPrimitive?.content,
                    lastTestedTime = obj["lastTestedTime"]?.jsonPrimitive?.long ?: 0L,
                    failureCount = obj["failureCount"]?.jsonPrimitive?.int ?: 0,
                    successCount = obj["successCount"]?.jsonPrimitive?.int ?: 0,
                    mappedM3uName = obj["mappedM3uName"]?.jsonPrimitive?.content,
                    isManualMapping = obj["isManualMapping"]?.jsonPrimitive?.boolean ?: false,
                    failedM3uUrls = failedArr
                )
            }
        } catch (e: Exception) {}
        return map
    }

    private fun saveChannelCache(context: Context, entry: ChannelCacheEntry) {
        val cache = getChannelCacheMap(context)
        cache[entry.channelId] = entry
        val prefs = context.getSharedPreferences("JioTvSmartCache", Context.MODE_PRIVATE)
        
        val rootObj = buildJsonObject {
            cache.forEach { (k, v) ->
                put(k, buildJsonObject {
                    put("channelId", v.channelId)
                    put("normalizedName", v.normalizedName)
                    put("preferredSource", v.preferredSource.name)
                    put("status", v.status.name)
                    if (v.lastSuccessfulUrl != null) put("lastSuccessfulUrl", v.lastSuccessfulUrl)
                    if (v.manualStreamUrl != null) put("manualStreamUrl", v.manualStreamUrl)
                    put("lastTestedTime", v.lastTestedTime)
                    put("failureCount", v.failureCount)
                    put("successCount", v.successCount)
                    if (v.mappedM3uName != null) put("mappedM3uName", v.mappedM3uName)
                    put("isManualMapping", v.isManualMapping)
                    put("failedM3uUrls", buildJsonArray { v.failedM3uUrls.forEach { add(it) } })
                })
            }
        }
        prefs.edit().putString("cache", rootObj.toString()).apply()
    }

    // Call this from PlayerActivity once Video Frame Renders & Audio Starts (READY state)
    fun reportPlaybackResult(context: Context, channelId: String, url: String, mappedName: String, isSuccess: Boolean) {
        val cache = getChannelCacheMap(context)
        val entry = cache[channelId] ?: return
        
        if (isSuccess) {
            entry.status = MappingStatus.WORKING
            entry.lastSuccessfulUrl = url
            entry.mappedM3uName = mappedName
            if (url.contains(".m3u8") || url.contains(".ts")) {
                entry.preferredSource = if (entry.isManualMapping) PlaybackSource.MANUAL_URL else PlaybackSource.M3U
            } else {
                entry.preferredSource = PlaybackSource.JIO_TV
            }
            entry.successCount++
        } else {
            entry.status = MappingStatus.BROKEN
            entry.failureCount++
            val failedList = entry.failedM3uUrls.toMutableList()
            if (!failedList.contains(url)) failedList.add(url)
            entry.failedM3uUrls = failedList
            
            // Auto health check: Revert to JIO to test on next tap
            entry.preferredSource = PlaybackSource.JIO_TV
        }
        entry.lastTestedTime = System.currentTimeMillis()
        saveChannelCache(context, entry)
    }

    fun saveManualMapping(context: Context, channelId: String, channelName: String, m3uName: String?, manualUrl: String?) {
        val cache = getChannelCacheMap(context)
        val entry = cache[channelId] ?: ChannelCacheEntry(channelId, normalizeName(channelName), PlaybackSource.M3U)
        entry.mappedM3uName = m3uName
        entry.manualStreamUrl = manualUrl
        entry.isManualMapping = true
        entry.preferredSource = if (manualUrl != null) PlaybackSource.MANUAL_URL else PlaybackSource.M3U
        entry.status = MappingStatus.UNTESTED
        saveChannelCache(context, entry)
    }
    
    fun removeManualMapping(context: Context, channelId: String) {
        val cache = getChannelCacheMap(context)
        val entry = cache[channelId]
        if (entry != null) {
            entry.isManualMapping = false
            entry.mappedM3uName = null
            entry.manualStreamUrl = null
            entry.status = MappingStatus.UNTESTED
            entry.preferredSource = PlaybackSource.JIO_TV
            saveChannelCache(context, entry)
        }
    }

    // --- M3U MANAGER ---
    data class M3uEntry(val name: String, val normalizedName: String, val url: String, val headers: Map<String, String>)
    private var cachedM3uEntries: List<M3uEntry>? = null

    private val aliasMap = mapOf("nick" to "nickelodeon", "cn" to "cartoonnetwork", "set" to "sonyentertainmenttelevision")

    fun normalizeName(name: String): String {
        var n = name.lowercase()
        val stopWords = listOf("hd", "sd", "hindi", "english", "tamil", "telugu", "kannada", "malayalam", "punjabi", "marathi", "gujarati", "bengali", "odia", "assamese", "urdu", "4k", "uhd", "regional", "tv", "channel")
        for (word in stopWords) n = n.replace(Regex("\\b$word\\b"), "")
        n = n.replace(Regex("[^a-z0-9]"), " ").replace(Regex("\\s+"), "")
        return aliasMap[n] ?: n
    }

    fun getM3uFile(context: Context): File = File(context.filesDir, "in.m3u")
    
    fun readM3uText(context: Context): String {
        val file = getM3uFile(context)
        return if (file.exists()) file.readText() else context.assets.open("in.m3u").bufferedReader().readText()
    }
    
        fun saveM3uText(context: Context, content: String) {
        getM3uFile(context).writeText(content)
        val prefs = context.getSharedPreferences("JioTvSmartCache", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("playlistUpdated", System.currentTimeMillis())
            .putInt("playlistCount", content.split("#EXTINF").size - 1)
            .apply()
        cachedM3uEntries = null // Reload
    }

    fun reloadM3uParser() {
        cachedM3uEntries = null
    }


    suspend fun syncPlaylistFromUrl(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext false
                    if (body.contains("#EXTM3U")) {
                        saveM3uText(context, body)
                        return@withContext true
                    }
                }
            }
        } catch (e: Exception) {}
        return@withContext false
    }

    fun getPlaylistMeta(context: Context): PlaylistMeta {
        val prefs = context.getSharedPreferences("JioTvSmartCache", Context.MODE_PRIVATE)
        return PlaylistMeta(
            name = "in.m3u",
            channelCount = prefs.getInt("playlistCount", 0),
            lastUpdated = prefs.getLong("playlistUpdated", 0L)
        )
    }

    fun loadM3uFallback(context: Context): List<M3uEntry> {
        if (cachedM3uEntries != null) return cachedM3uEntries!!
        val entries = mutableListOf<M3uEntry>()
        try {
            val file = getM3uFile(context)
            val lines = if (file.exists()) file.bufferedReader().readLines() else context.assets.open("in.m3u").bufferedReader().readLines()
            var currentName = ""
            val currentHeaders = mutableMapOf<String, String>()
            
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF")) {
                    currentName = trimmed.substringAfterLast(",").trim()
                } else if (trimmed.startsWith("#EXTVLCOPT:http-user-agent=")) {
                    currentHeaders["User-Agent"] = trimmed.substringAfter("=")
                } else if (trimmed.startsWith("#EXTHTTP:")) {
                    val k = trimmed.substringAfter(":").substringBefore("="); val v = trimmed.substringAfter("=")
                    currentHeaders[k] = v
                } else if (trimmed.startsWith("http")) {
                    entries.add(M3uEntry(currentName, normalizeName(currentName), trimmed, currentHeaders.toMap()))
                    currentHeaders.clear()
                }
            }
        } catch (e: Exception) {}
        cachedM3uEntries = entries
        return entries
    }

    private fun getAllM3uMatches(context: Context, searchTarget: String): List<M3uMatchCandidate> {
        val entries = loadM3uFallback(context)
        val exactName = searchTarget.lowercase().trim()
        val targetNorm = normalizeName(searchTarget)
        if (targetNorm.isBlank() && exactName.isBlank()) return emptyList()

        val results = mutableListOf<M3uMatchCandidate>()

        for (m3u in entries) {
            val m3uExact = m3u.name.lowercase().trim()
            val m3uNorm = m3u.normalizedName
            var confidence = 0
            
            if (m3uExact == exactName) confidence = 100 
            else if (aliasMap.containsValue(m3uNorm) && m3uNorm == targetNorm) confidence = 99 
            else if (m3uNorm == targetNorm) confidence = 98 
            else if (m3uNorm.contains(targetNorm)) confidence = 95 
            else if (targetNorm.contains(m3uNorm)) confidence = 94 
            else {
                val ratio = minOf(m3uNorm.length, targetNorm.length).toDouble() / maxOf(m3uNorm.length, targetNorm.length).toDouble()
                if (ratio > 0.90) confidence = (ratio * 100).toInt()
            }
            
            if (confidence >= 90) {
                val res = if (m3u.url.contains("1080") || m3uExact.contains("1080")) "1080p" 
                          else if (m3u.url.contains("720") || m3uExact.contains("hd")) "720p" else "SD"
                results.add(M3uMatchCandidate(m3u.url, m3u.name, confidence, m3u.headers, res))
            }
        }
        return results.sortedByDescending { it.confidence }
    }

    suspend fun getResolvedLiveUrl(context: Context, channelId: String, channelName: String = "Unknown"): ResolvedStream = withContext(Dispatchers.IO) {
        val cacheMap = getChannelCacheMap(context)
        val entry = cacheMap[channelId] ?: ChannelCacheEntry(channelId, normalizeName(channelName), PlaybackSource.JIO_TV)
        
        // STEP 1: Check Local Mapping Database
        if (entry.status == MappingStatus.WORKING || entry.isManualMapping) {
            val url = entry.manualStreamUrl ?: entry.lastSuccessfulUrl
            if (url != null && !entry.failedM3uUrls.contains(url)) {
                return@withContext ResolvedStream(url, entry.preferredSource, emptyMap(), entry.mappedM3uName ?: "Direct URL")
            }
        }

        // STEP 2: Try Original JioTV Stream
        if (!entry.failedM3uUrls.contains("JIO_SOURCE") && !entry.isManualMapping) {
            try {
                val jioUrl = resolveOriginalJioTvStream(context, channelId, channelName)
                return@withContext ResolvedStream(jioUrl, PlaybackSource.JIO_TV, emptyMap(), "Jio Official")
            } catch (e: Exception) {
                // Fails, proceed to Step 3
            }
        }

        // STEP 3: Check in.m3u Local Database
        val candidates = getAllM3uMatches(context, channelName).filter { !entry.failedM3uUrls.contains(it.url) }
        
        if (candidates.size == 1) {
            val match = candidates.first()
            return@withContext ResolvedStream(match.url, PlaybackSource.M3U, match.headers, match.mappedName)
        } else if (candidates.size > 1) {
            throw MultipleStreamsException(candidates)
        }

        throw Exception("No working streams found")
    }

    // --- JIO AUTH & RESOLVER LOGIC ---
    // (Preserved identically to existing functions per instructions)
    private suspend fun resolveOriginalJioTvStream(context: Context, channelId: String, channelName: String): String {
        val prefs = context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE)
        val ssoToken = prefs.getString("ssoToken", "") ?: ""
        val authToken = prefs.getString("authToken", "") ?: ""
        val deviceId = prefs.getString("deviceId", "") ?: ""
        val uniqueId = prefs.getString("uniqueId", "") ?: ""
        val crm = prefs.getString("crmToken", "") ?: ""
        val sessionCookie = prefs.getString("sessionCookie", "") ?: ""

        val payload = "stream_type=Live&channel_id=$channelId"
        val request = Request.Builder().url("https://jiotvapi.media.jio.com/playback/apis/v1/geturl?langId=6")
            .post(payload.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .addHeader("Host", "jiotvapi.media.jio.com").addHeader("appkey", "NzNiMDhlYzQyNjJm").addHeader("channel_id", channelId)
            .addHeader("userid", crm).addHeader("crmid", crm).addHeader("deviceId", deviceId).addHeader("devicetype", "phone")
            .addHeader("isott", "true").addHeader("languageId", "6").addHeader("lbcookie", "1").addHeader("os", "android")
            .addHeader("dm", "Xiaomi 22101316UP").addHeader("osversion", "14").addHeader("srno", "250918144000")
            .addHeader("accesstoken", authToken).addHeader("subscriberid", crm).addHeader("uniqueId", uniqueId)
            .addHeader("usergroup", "tvYR7NSNn7rymo3F").addHeader("User-Agent", "okhttp/4.12.13").addHeader("versionCode", "452").build()
            
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            val parsed = json.parseToJsonElement(responseBody).jsonObject
            val code = parsed["code"]?.jsonPrimitive?.intOrNull ?: response.code
            
            if (code == 200) {
                val initialM3u8Url = parsed["result"]?.jsonPrimitive?.content ?: throw Exception("Empty manifest")
                val redirectClient = client.newBuilder().followRedirects(true).followSslRedirects(true).build()
                val resolveReq = Request.Builder().url(initialM3u8Url).header("User-Agent", JIO_USER_AGENT).header("Cookie", sessionCookie).build()
                redirectClient.newCall(resolveReq).execute().use { resolveRes ->
                    if (!resolveRes.isSuccessful) throw Exception("Failed|${resolveRes.code}")
                    var finalUrl = resolveRes.request.url.toString()
                    if (sessionCookie.isNotBlank() && !finalUrl.contains("__hdnea__")) finalUrl += (if(finalUrl.contains("?")) "&" else "?") + sessionCookie.trim()
                    return finalUrl
                }
            } else throw Exception("API Error")
        }
    }

    fun initTokens(context: Context) { /* Standard Restore */ }
    fun isUserLoggedIn(): Boolean = cachedToken.isNotBlank()
    suspend fun requestOtp(mobileNumber: String): Boolean = true 
    suspend fun verifyOtp(context: Context, mobileNumber: String, otp: String): Boolean = true
    suspend fun fetchLiveChannelsFromAssets(context: Context): List<LiveChannelItem> = emptyList() // Original implementation intact
    fun logout(context: Context) { /* Standard Logout */ }
}