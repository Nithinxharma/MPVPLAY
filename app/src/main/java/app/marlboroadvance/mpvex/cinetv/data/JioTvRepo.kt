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

    private val quickClient = client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    
    @Volatile private var cachedToken: String = ""
    @Volatile private var cachedCrm: String = ""
    @Volatile var lastResolvedHeaders: Map<String, String> = emptyMap()

    private const val JIO_USER_AGENT = "plaYtv/7.0.8 (Linux;Android 9) ExoPlayerLib/2.11.7"

    // --- MAPPING DATABASE ---
    fun getChannelCacheMap(context: Context): MutableMap<String, ChannelCacheEntry> {
        val prefs = context.getSharedPreferences("JioTvSmartCache", Context.MODE_PRIVATE)
        val map = mutableMapOf<String, ChannelCacheEntry>()
        try {
            val jsonStr = prefs.getString("cache", "{}") ?: "{}"
            val root = json.parseToJsonElement(jsonStr).jsonObject
            for ((k, v) in root) {
                val obj = v.jsonObject
                map[k] = ChannelCacheEntry(
                    channelId = k,
                    mappedM3uName = obj["mappedM3uName"]?.jsonPrimitive?.content,
                    mappedUrl = obj["mappedUrl"]?.jsonPrimitive?.content,
                    isManualMapping = obj["isManualMapping"]?.jsonPrimitive?.boolean ?: false,
                    preferredSource = PlaybackSource.valueOf(obj["preferredSource"]?.jsonPrimitive?.content ?: "JIO_TV"),
                    status = MappingStatus.valueOf(obj["status"]?.jsonPrimitive?.content ?: "UNTESTED"),
                    lastSuccessTime = obj["lastSuccessTime"]?.jsonPrimitive?.long ?: 0L,
                    lastFailureTime = obj["lastFailureTime"]?.jsonPrimitive?.long ?: 0L,
                    successCount = obj["successCount"]?.jsonPrimitive?.int ?: 0,
                    failureCount = obj["failureCount"]?.jsonPrimitive?.int ?: 0
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
                    if (v.mappedM3uName != null) put("mappedM3uName", v.mappedM3uName)
                    if (v.mappedUrl != null) put("mappedUrl", v.mappedUrl)
                    put("isManualMapping", v.isManualMapping)
                    put("preferredSource", v.preferredSource.name)
                    put("status", v.status.name)
                    put("lastSuccessTime", v.lastSuccessTime)
                    put("lastFailureTime", v.lastFailureTime)
                    put("successCount", v.successCount)
                    put("failureCount", v.failureCount)
                })
            }
        }
        prefs.edit().putString("cache", rootObj.toString()).apply()
    }

    fun saveManualMapping(context: Context, jioChannelId: String, m3uName: String, m3uUrl: String) {
        val entry = ChannelCacheEntry(
            channelId = jioChannelId,
            mappedM3uName = m3uName,
            mappedUrl = m3uUrl,
            isManualMapping = true,
            preferredSource = PlaybackSource.MANUAL_URL,
            status = MappingStatus.UNTESTED
        )
        saveChannelCache(context, entry)
    }

    fun removeManualMapping(context: Context, jioChannelId: String) {
        val cache = getChannelCacheMap(context)
        cache.remove(jioChannelId)
        val prefs = context.getSharedPreferences("JioTvSmartCache", Context.MODE_PRIVATE)
        val rootObj = buildJsonObject {
            cache.forEach { (k, v) ->
                put(k, buildJsonObject {
                    put("channelId", v.channelId)
                    if (v.mappedM3uName != null) put("mappedM3uName", v.mappedM3uName)
                    if (v.mappedUrl != null) put("mappedUrl", v.mappedUrl)
                    put("isManualMapping", v.isManualMapping)
                    put("preferredSource", v.preferredSource.name)
                    put("status", v.status.name)
                })
            }
        }
        prefs.edit().putString("cache", rootObj.toString()).apply()
    }

    // --- M3U PARSER ---
    data class M3uEntry(val name: String, val url: String, val headers: Map<String, String>)
    private var cachedM3uEntries: List<M3uEntry>? = null

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
        cachedM3uEntries = null 
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
                    entries.add(M3uEntry(currentName, trimmed, currentHeaders.toMap()))
                    currentHeaders.clear()
                }
            }
        } catch (e: Exception) {}
        cachedM3uEntries = entries
        return entries
    }

    fun generateSmartFilterKeyword(m3uName: String): String {
        var n = m3uName.lowercase()
        val stops = listOf("hd", "sd", "hindi", "tamil", "english", "malayalam", "punjabi", "telugu", "kannada", "marathi", "gujarati", "bengali", "odia", "4k", "uhd", "hevc")
        for (w in stops) n = n.replace(Regex("\\b$w\\b"), "")
        n = n.replace(Regex("[^a-z0-9 ]"), "").trim()
        val parts = n.split(" ").filter { it.length > 2 }
        return if (parts.isNotEmpty()) parts.first() else n
    }

    suspend fun testStreamUrl(url: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder().url(url).head()
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            if (!headers.containsKey("User-Agent")) reqBuilder.header("User-Agent", JIO_USER_AGENT)

            quickClient.newCall(reqBuilder.build()).execute().use { res ->
                return@withContext when (res.code) {
                    in 200..299 -> "Working"
                    403 -> "403 Forbidden"
                    404 -> "404 Not Found"
                    else -> "Playlist Error (${res.code})"
                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("timeout", true) == true) return@withContext "Timeout"
            return@withContext "Connection Error"
        }
    }

    // --- PLAYBACK LOGIC ---
    suspend fun getResolvedLiveUrl(context: Context, channelId: String, channelName: String = "Unknown"): ResolvedStream = withContext(Dispatchers.IO) {
        val cacheMap = getChannelCacheMap(context)
        val entry = cacheMap[channelId]
        
        // Priority: EXACT MANUAL MAPPING FIRST
        if (entry != null && entry.isManualMapping && entry.mappedUrl != null) {
            val match = loadM3uFallback(context).find { it.url == entry.mappedUrl }
            return@withContext ResolvedStream(entry.mappedUrl, PlaybackSource.MANUAL_URL, match?.headers ?: emptyMap(), entry.mappedM3uName ?: "Direct M3U")
        }

        // Fallback: ORIGINAL JIO SOURCE
        val jioUrl = resolveOriginalJioTvStream(context, channelId, channelName)
        return@withContext ResolvedStream(jioUrl, PlaybackSource.JIO_TV, emptyMap(), "Jio Official")
    }

    // --- JIO AUTH ---
    private suspend fun resolveOriginalJioTvStream(context: Context, channelId: String, channelName: String): String {
        val prefs = context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE)
        val authToken = prefs.getString("authToken", "") ?: ""; val sessionCookie = prefs.getString("sessionCookie", "") ?: ""
        val payload = "stream_type=Live&channel_id=$channelId"
        
        val request = Request.Builder().url("https://jiotvapi.media.jio.com/playback/apis/v1/geturl?langId=6")
            .post(payload.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .addHeader("accesstoken", authToken).addHeader("User-Agent", "okhttp/4.12.13").build()
            
        client.newCall(request).execute().use { response ->
            val parsed = json.parseToJsonElement(response.body?.string() ?: "{}").jsonObject
            if (parsed["code"]?.jsonPrimitive?.intOrNull == 200) {
                val initialUrl = parsed["result"]?.jsonPrimitive?.content ?: throw Exception("Empty manifest")
                val redirectClient = client.newBuilder().followRedirects(true).followSslRedirects(true).build()
                val resolveReq = Request.Builder().url(initialUrl).header("User-Agent", JIO_USER_AGENT).header("Cookie", sessionCookie).build()
                redirectClient.newCall(resolveReq).execute().use { res ->
                    if (!res.isSuccessful) throw Exception("Failed|${res.code}")
                    var finalUrl = res.request.url.toString()
                    if (sessionCookie.isNotBlank() && !finalUrl.contains("__hdnea__")) finalUrl += (if(finalUrl.contains("?")) "&" else "?") + sessionCookie.trim()
                    return finalUrl
                }
            } else throw Exception("API Error")
        }
    }

    private fun restoreSessionFromAssets(context: Context): Boolean {
        try {
            val prefs = context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE)
            if (prefs.getString("authToken", "")!!.isNotBlank()) return true
            var ssoToken = ""; var authToken = ""; var refreshToken = ""; var deviceId = ""; var subscriberId = ""; var uniqueId = ""; var cookieStr = ""
            try {
                val credsBase64 = context.assets.open("creds.jtv").bufferedReader().readText()
                val jsonObj = json.parseToJsonElement(String(Base64.decode(credsBase64, Base64.DEFAULT))).jsonObject
                ssoToken = jsonObj["ssoToken"]?.jsonPrimitive?.content ?: ""
                authToken = jsonObj["authToken"]?.jsonPrimitive?.content ?: ""
                refreshToken = jsonObj["refreshToken"]?.jsonPrimitive?.content ?: ""
                deviceId = jsonObj["deviceId"]?.jsonPrimitive?.content ?: ""
                val userObj = jsonObj["sessionAttributes"]?.jsonObject?.get("user")?.jsonObject
                subscriberId = userObj?.get("subscriberId")?.jsonPrimitive?.content ?: ""
                uniqueId = userObj?.get("unique")?.jsonPrimitive?.content ?: ""
            } catch (e: Exception) {}
            try {
                cookieStr = String(context.assets.open("cookie.jtv").bufferedReader().readText().trim().chunked(2).map { it.toInt(16).toByte() }.toByteArray())
            } catch (e: Exception) {}

            if (authToken.isNotBlank()) {
                cachedToken = authToken; cachedCrm = subscriberId
                prefs.edit().putString("ssoToken", ssoToken).putString("authToken", authToken).putString("refreshToken", refreshToken)
                    .putString("crmToken", subscriberId).putString("deviceId", deviceId).putString("uniqueId", uniqueId)
                    .putString("sessionCookie", cookieStr).apply()
                return true
            }
        } catch (e: Exception) {}
        return false
    }

    fun initTokens(context: Context) {
        restoreSessionFromAssets(context)
        val prefs = context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE)
        cachedToken = prefs.getString("authToken", "") ?: ""
        cachedCrm = prefs.getString("crmToken", "") ?: ""
    }

    fun isUserLoggedIn(): Boolean = cachedToken.isNotBlank()
    suspend fun requestOtp(mobileNumber: String): Boolean = true 
    suspend fun verifyOtp(context: Context, mobileNumber: String, otp: String): Boolean = true
    suspend fun fetchLiveChannelsFromAssets(context: Context): List<LiveChannelItem> = emptyList() // Implemented in actual app
    fun logout(context: Context) {
        cachedToken = ""
        cachedCrm = ""
        context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit().clear().apply()
    }
}