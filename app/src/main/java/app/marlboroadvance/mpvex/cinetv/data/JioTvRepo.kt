package app.marlboroadvance.mpvex.cinetv.data

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
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
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    
    @Volatile private var cachedToken: String = ""
    @Volatile private var cachedCrm: String = ""
    
    // Holds the headers for the actively resolving stream so PlayerActivity can access them
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
                val source = if (obj["preferredSource"]?.jsonPrimitive?.content == "M3U") PlaybackSource.M3U else PlaybackSource.JIO_TV
                map[k] = ChannelCacheEntry(
                    channelId = k,
                    normalizedName = obj["normalizedName"]?.jsonPrimitive?.content ?: "",
                    preferredSource = source,
                    lastSuccessfulUrl = obj["lastSuccessfulUrl"]?.jsonPrimitive?.content,
                    lastTestedTime = obj["lastTestedTime"]?.jsonPrimitive?.long ?: 0L,
                    failureCount = obj["failureCount"]?.jsonPrimitive?.int ?: 0,
                    successCount = obj["successCount"]?.jsonPrimitive?.int ?: 0,
                    userFeedback = obj["userFeedback"]?.jsonPrimitive?.booleanOrNull,
                    mappedM3uName = obj["mappedM3uName"]?.jsonPrimitive?.content,
                    confidenceScore = obj["confidenceScore"]?.jsonPrimitive?.int ?: 0,
                    isManualMapping = obj["isManualMapping"]?.jsonPrimitive?.boolean ?: false,
                    userVerified = obj["userVerified"]?.jsonPrimitive?.boolean ?: false
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
                    if (v.lastSuccessfulUrl != null) put("lastSuccessfulUrl", v.lastSuccessfulUrl)
                    put("lastTestedTime", v.lastTestedTime)
                    put("failureCount", v.failureCount)
                    put("successCount", v.successCount)
                    if (v.userFeedback != null) put("userFeedback", v.userFeedback)
                    if (v.mappedM3uName != null) put("mappedM3uName", v.mappedM3uName)
                    put("confidenceScore", v.confidenceScore)
                    put("isManualMapping", v.isManualMapping)
                    put("userVerified", v.userVerified)
                })
            }
        }
        prefs.edit().putString("cache", rootObj.toString()).apply()
    }

    fun setUserFeedback(context: Context, channelId: String, worked: Boolean) {
        val cache = getChannelCacheMap(context)
        val entry = cache[channelId]
        if (entry != null) {
            entry.userFeedback = worked
            entry.userVerified = true
            if (!worked && entry.preferredSource == PlaybackSource.M3U && !entry.isManualMapping) {
                entry.preferredSource = PlaybackSource.JIO_TV 
            }
            saveChannelCache(context, entry)
        }
    }

    fun saveManualMapping(context: Context, channelId: String, channelName: String, m3uName: String) {
        val cache = getChannelCacheMap(context)
        val entry = cache[channelId] ?: ChannelCacheEntry(channelId, normalizeName(channelName), PlaybackSource.M3U)
        entry.mappedM3uName = m3uName
        entry.isManualMapping = true
        entry.preferredSource = PlaybackSource.M3U
        entry.confidenceScore = 100
        saveChannelCache(context, entry)
    }
    
    fun removeManualMapping(context: Context, channelId: String) {
        val cache = getChannelCacheMap(context)
        val entry = cache[channelId]
        if (entry != null) {
            entry.isManualMapping = false
            entry.mappedM3uName = null
            entry.preferredSource = PlaybackSource.JIO_TV
            saveChannelCache(context, entry)
        }
    }

    fun importMappings(context: Context, jsonStr: String) {
        val prefs = context.getSharedPreferences("JioTvSmartCache", Context.MODE_PRIVATE)
        prefs.edit().putString("cache", jsonStr).apply()
    }

    fun exportMappings(context: Context): String {
        val prefs = context.getSharedPreferences("JioTvSmartCache", Context.MODE_PRIVATE)
        return prefs.getString("cache", "{}") ?: "{}"
    }

    // --- M3U FALLBACK ENGINE ---
    data class M3uEntry(val name: String, val normalizedName: String, val url: String, val qualityScore: Int, val headers: Map<String, String>)
    private var cachedM3uEntries: List<M3uEntry>? = null

    private val aliasMap = mapOf(
        "nick" to "nickelodeon",
        "cn" to "cartoonnetwork",
        "set" to "sonyentertainmenttelevision"
    )

    private fun normalizeName(name: String): String {
        var n = name.lowercase()
        val stopWords = listOf(
            "hd", "sd", "4k", "uhd", "hindi", "english", "tamil", "telugu", "kannada", 
            "malayalam", "marathi", "gujarati", "punjabi", "bengali", "odia", "bhojpuri", 
            "urdu", "international", "regional", "plus", "tv", "channel"
        )
        for (word in stopWords) {
            n = n.replace(Regex("\\b$word\\b"), "")
        }
        n = n.replace(Regex("[^a-z0-9]"), "").trim()
        return aliasMap[n] ?: n
    }

    fun loadM3uFallback(context: Context): List<M3uEntry> {
        if (cachedM3uEntries != null) return cachedM3uEntries!!
        val entries = mutableListOf<M3uEntry>()
        try {
            val lines = context.assets.open("in.m3u").bufferedReader().readLines()
            var currentName = ""
            val currentHeaders = mutableMapOf<String, String>()
            
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF")) {
                    currentName = trimmed.substringAfterLast(",").trim()
                } else if (trimmed.startsWith("#EXTVLCOPT:http-user-agent=")) {
                    currentHeaders["User-Agent"] = trimmed.substringAfter("=")
                } else if (trimmed.startsWith("#EXTHTTP:")) {
                    val keyVal = trimmed.substringAfter(":")
                    val k = keyVal.substringBefore("=")
                    val v = keyVal.substringAfter("=")
                    currentHeaders[k] = v
                } else if (trimmed.startsWith("#KODIPROP:inputstream.adaptive.stream_headers=")) {
                    val props = trimmed.substringAfter("=").split("&")
                    props.forEach {
                        val k = it.substringBefore("=")
                        val v = it.substringAfter("=")
                        currentHeaders[k] = v
                    }
                } else if (trimmed.startsWith("http")) {
                    val score = when {
                        trimmed.contains("1080") || currentName.contains("1080") -> 3
                        trimmed.contains("720") || currentName.contains("HD", true) -> 2
                        else -> 1
                    }
                    entries.add(M3uEntry(currentName, normalizeName(currentName), trimmed, score, currentHeaders.toMap()))
                    currentHeaders.clear()
                }
            }
        } catch (e: Exception) {}
        cachedM3uEntries = entries
        return entries
    }

    private fun testM3uStream(url: String, headers: Map<String, String>): Boolean {
        try {
            val reqBuilder = Request.Builder().url(url)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            if (!headers.containsKey("User-Agent")) reqBuilder.header("User-Agent", JIO_USER_AGENT)
            
            quickClient.newCall(reqBuilder.build()).execute().use { res ->
                if (!res.isSuccessful) return false
                val body = res.body?.string() ?: return false
                if (!body.contains("#EXTM3U")) return false
                
                if (body.contains("#EXT-X-STREAM-INF")) {
                    val chunklistLine = body.lines().firstOrNull { it.endsWith(".m3u8") && !it.startsWith("#") }
                    if (chunklistLine != null) {
                        val chunklistUrl = if (chunklistLine.startsWith("http")) chunklistLine else "${url.substringBeforeLast("/")}/$chunklistLine"
                        val chunkReq = reqBuilder.url(chunklistUrl).build()
                        quickClient.newCall(chunkReq).execute().use { chunkRes ->
                            return chunkRes.isSuccessful && (chunkRes.body?.string()?.contains("#EXTINF") == true)
                        }
                    }
                } else if (body.contains("#EXTINF")) {
                    return true
                }
                return false
            }
        } catch(e: Exception) { return false }
    }

    private data class MatchResult(val url: String, val mappedName: String, val confidence: Int, val headers: Map<String, String>)

    private fun getWorkingM3uStream(context: Context, searchTarget: String, isManual: Boolean): MatchResult? {
        val entries = loadM3uFallback(context)
        val targetNorm = normalizeName(searchTarget)
        if (targetNorm.isBlank()) return null

        var bestMatch: M3uEntry? = null
        var bestConfidence = 0

        for (m3u in entries) {
            val m3uNorm = m3u.normalizedName
            var confidence = 0
            
            if (m3uNorm == targetNorm) {
                confidence = 100 
            } else if (!isManual && (m3uNorm.contains(targetNorm) || targetNorm.contains(m3uNorm))) {
                val minLen = minOf(m3uNorm.length, targetNorm.length).toDouble()
                val maxLen = maxOf(m3uNorm.length, targetNorm.length).toDouble()
                val ratio = minLen / maxLen
                if (ratio >= 0.9) confidence = 90
            }
            
            if (confidence >= 90 && confidence > bestConfidence) {
                if (testM3uStream(m3u.url, m3u.headers)) {
                    bestConfidence = confidence
                    bestMatch = m3u
                    if (confidence == 100) break
                }
            }
        }
        
        return if (bestMatch != null) MatchResult(bestMatch.url, bestMatch.name, bestConfidence, bestMatch.headers) else null
    }

    // --- AUTH & LOGIN ---
    private fun restoreSessionFromAssets(context: Context): Boolean {
        try {
            val prefs = context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE)
            if (prefs.getString("authToken", "")!!.isNotBlank()) return true

            var ssoToken = ""; var authToken = ""; var refreshToken = ""
            var deviceId = ""; var subscriberId = ""; var uniqueId = ""; var cookieStr = ""
            
            try {
                val credsBase64 = context.assets.open("creds.jtv").bufferedReader().readText()
                val decodedCreds = String(Base64.decode(credsBase64, Base64.DEFAULT))
                val jsonObj = json.parseToJsonElement(decodedCreds).jsonObject
                
                ssoToken = jsonObj["ssoToken"]?.jsonPrimitive?.content ?: ""
                authToken = jsonObj["authToken"]?.jsonPrimitive?.content ?: ""
                refreshToken = jsonObj["refreshToken"]?.jsonPrimitive?.content ?: ""
                deviceId = jsonObj["deviceId"]?.jsonPrimitive?.content ?: ""
                
                val userObj = jsonObj["sessionAttributes"]?.jsonObject?.get("user")?.jsonObject
                subscriberId = userObj?.get("subscriberId")?.jsonPrimitive?.content ?: ""
                uniqueId = userObj?.get("unique")?.jsonPrimitive?.content ?: ""
            } catch (e: Exception) {}
            
            try {
                val cookieHex = context.assets.open("cookie.jtv").bufferedReader().readText().trim()
                val cookieBytes = cookieHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                cookieStr = String(cookieBytes)
            } catch (e: Exception) {}

            if (authToken.isNotBlank()) {
                cachedToken = authToken
                cachedCrm = subscriberId
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

    suspend fun requestOtp(mobileNumber: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fullPhone = if (mobileNumber.startsWith("+91")) mobileNumber else "+91$mobileNumber"
            val encodedPhone = Base64.encodeToString(fullPhone.toByteArray(), Base64.NO_WRAP)
            val jsonPayload = buildJsonObject { put("number", encodedPhone) }.toString()
            val request = Request.Builder().url("https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/send")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("appname", "RJIL_JioTV").addHeader("os", "android").addHeader("devicetype", "phone")
                .addHeader("User-Agent", "okhttp/3.14.9").build()
            client.newCall(request).execute().use { response -> return@withContext response.code == 204 || response.isSuccessful }
        } catch (e: Exception) { return@withContext false }
    }

    suspend fun verifyOtp(context: Context, mobileNumber: String, otp: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fullPhone = if (mobileNumber.startsWith("+91")) mobileNumber else "+91$mobileNumber"
            val encodedPhone = Base64.encodeToString(fullPhone.toByteArray(), Base64.NO_WRAP)
            val genDeviceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
            val jsonPayload = buildJsonObject {
                put("number", encodedPhone)
                put("otp", otp)
                put("deviceInfo", buildJsonObject {
                    put("consumptionDeviceName", "RMX1945")
                    put("info", buildJsonObject {
                        put("type", "android")
                        put("platform", buildJsonObject { put("name", "RMX1945") })
                        put("androidId", genDeviceId)
                    })
                })
            }.toString()

            val request = Request.Builder().url("https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/verify")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("appname", "RJIL_JioTV").addHeader("os", "android").addHeader("devicetype", "phone")
                .addHeader("content-type", "application/json").addHeader("User-Agent", "okhttp/3.14.9").build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                val parsed = json.parseToJsonElement(body).jsonObject
                
                if (response.isSuccessful || response.code == 200) {
                    if (parsed["ssoToken"]?.jsonPrimitive?.content?.isNotBlank() == true || parsed["authToken"]?.jsonPrimitive?.content?.isNotBlank() == true) {
                        val finalSsoToken = parsed["ssoToken"]?.jsonPrimitive?.content ?: ""
                        val finalAuthToken = parsed["authToken"]?.jsonPrimitive?.content ?: ""
                        val finalRefreshToken = parsed["refreshToken"]?.jsonPrimitive?.content ?: ""
                        val targetDeviceId = parsed["deviceId"]?.jsonPrimitive?.content ?: genDeviceId
                        val userObj = parsed["sessionAttributes"]?.jsonObject?.get("user")?.jsonObject
                        val finalCrm = userObj?.get("subscriberId")?.jsonPrimitive?.content ?: ""
                        val finalUniqueId = userObj?.get("unique")?.jsonPrimitive?.content ?: ""

                        cachedToken = finalAuthToken
                        cachedCrm = finalCrm
                        
                        context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit()
                            .putString("ssoToken", finalSsoToken).putString("authToken", finalAuthToken).putString("refreshToken", finalRefreshToken)
                            .putString("crmToken", finalCrm).putString("deviceId", targetDeviceId).putString("uniqueId", finalUniqueId).apply()
                            
                        return@withContext true
                    }
                }
                var errorMsg = "Unknown Error Occured : Code ${response.code}"
                if (parsed.containsKey("message") && parsed["message"]?.jsonPrimitive?.content?.isNotBlank() == true) errorMsg = "Jio Error - " + parsed["message"]?.jsonPrimitive?.content
                throw Exception(errorMsg)
            }
        } catch (e: Exception) { throw e }
    }

    suspend fun fetchLiveChannelsFromAssets(context: Context): List<LiveChannelItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LiveChannelItem>()
        val prefs = context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE)
        val ssoToken = prefs.getString("ssoToken", "") ?: ""
        val authToken = prefs.getString("authToken", "") ?: ""
        val deviceId = prefs.getString("deviceId", "") ?: ""
        val uniqueId = prefs.getString("uniqueId", "") ?: ""
        val crm = prefs.getString("crmToken", "") ?: ""
        
        val url = "https://jiotv.data.cdn.jio.com/apis/v1.4/getMobileChannelList/get/?os=android&devicetype=phone"

        val request = Request.Builder().url(url).get().addHeader("Accept", "application/json").addHeader("User-Agent", "okhttp/3.14.9")
            .addHeader("os", "android").addHeader("devicetype", "phone").addHeader("ssotoken", ssoToken)
            .addHeader("accesstoken", authToken).addHeader("subscriberid", crm).addHeader("crmid", crm)
            .addHeader("uniqueid", uniqueId).addHeader("deviceid", deviceId).build()
            
        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) throw Exception("HTTP Error ${response.code}: $body")

            try {
                val root = json.parseToJsonElement(body).jsonObject
                val resultArr = root["result"]?.jsonArray ?: throw Exception("API returned empty array.")
                val rawChannels = mutableListOf<RawChannel>()

                for (i in 0 until resultArr.size) {
                    val channelNode = resultArr[i].jsonObject
                    val id = channelNode["channel_id"]?.jsonPrimitive?.content ?: continue
                    val name = channelNode["channel_name"]?.jsonPrimitive?.content ?: "Unknown"
                    val catId = channelNode["channelCategoryId"]?.jsonPrimitive?.intOrNull ?: 0
                    val langId = channelNode["channelLanguageId"]?.jsonPrimitive?.intOrNull ?: 0
                    val rawLogoUrl = channelNode["logoUrl"]?.jsonPrimitive?.content ?: "$id.png"
                    rawChannels.add(RawChannel(id, name, getCategoryName(catId), getLanguageName(langId), "https://jiotvimages.cdn.jio.com/dare_images/images/$rawLogoUrl"))
                }

                val grouped = rawChannels.groupBy { it.name }
                for ((name, channels) in grouped) {
                    val baseChannel = channels.first()
                    val variants = channels.map { ChannelVariant(it.id, it.language) }.distinctBy { it.language }
                    list.add(LiveChannelItem(baseChannel.id, name, baseChannel.category, baseChannel.language, baseChannel.logoUrl, "jiotv_live:${baseChannel.id}", variants))
                }
            } catch (e: Exception) { throw Exception("Failed to parse channel JSON: ${e.message}") }
        }
        return@withContext list
    }

    private data class RawChannel(val id: String, val name: String, val category: String, val language: String, val logoUrl: String)

    suspend fun getResolvedLiveUrl(context: Context, channelId: String, channelName: String = "Unknown"): ResolvedStream = withContext(Dispatchers.IO) {
        val cacheMap = getChannelCacheMap(context)
        val entry = cacheMap[channelId] ?: ChannelCacheEntry(channelId, normalizeName(channelName), PlaybackSource.JIO_TV)
        
        // Priority 1: Manual Mapping
        if (entry.isManualMapping && !entry.mappedM3uName.isNullOrBlank()) {
            val match = getWorkingM3uStream(context, entry.mappedM3uName!!, isManual = true)
            if (match != null) {
                entry.lastSuccessfulUrl = match.url
                saveChannelCache(context, entry)
                return@withContext ResolvedStream(match.url, PlaybackSource.M3U, match.headers)
            } else {
                throw Exception("Manual mapping stream unavailable|404")
            }
        }

        // Priority 2: Cached Working Stream
        if (entry.preferredSource == PlaybackSource.M3U && entry.lastSuccessfulUrl != null) {
            val cachedM3u = loadM3uFallback(context).find { it.url == entry.lastSuccessfulUrl }
            if (cachedM3u != null && testM3uStream(cachedM3u.url, cachedM3u.headers)) {
                return@withContext ResolvedStream(cachedM3u.url, PlaybackSource.M3U, cachedM3u.headers)
            }
        }

        // Priority 3: Try JioTV Official
        if (entry.preferredSource == PlaybackSource.JIO_TV) {
            try {
                val jioUrl = resolveOriginalJioTvStream(context, channelId, channelName)
                entry.successCount++
                entry.lastSuccessfulUrl = jioUrl
                entry.lastTestedTime = System.currentTimeMillis()
                saveChannelCache(context, entry)
                return@withContext ResolvedStream(jioUrl, PlaybackSource.JIO_TV, emptyMap())
            } catch (e: Exception) {
                entry.failureCount++
                entry.preferredSource = PlaybackSource.M3U 
                saveChannelCache(context, entry)
            }
        }

        // Priority 4: Fallback M3U Automatic Matching
        val match = getWorkingM3uStream(context, channelName, isManual = false)
        if (match != null) {
            entry.preferredSource = PlaybackSource.M3U
            entry.lastSuccessfulUrl = match.url
            entry.mappedM3uName = match.mappedName
            entry.confidenceScore = match.confidence
            entry.lastTestedTime = System.currentTimeMillis()
            saveChannelCache(context, entry)
            return@withContext ResolvedStream(match.url, PlaybackSource.M3U, match.headers)
        }

        throw Exception("No working streams found|404")
    }

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
                val initialM3u8Url = parsed["result"]?.jsonPrimitive?.content ?: throw Exception("Empty manifest URL returned|500")
                val redirectClient = client.newBuilder().followRedirects(true).followSslRedirects(true).build()

                val resolveReq = Request.Builder().url(initialM3u8Url).header("User-Agent", JIO_USER_AGENT).header("Cookie", sessionCookie).build()

                redirectClient.newCall(resolveReq).execute().use { resolveRes ->
                    val status = resolveRes.code
                    var finalUrl = resolveRes.request.url.toString()

                    if (sessionCookie.isNotBlank() && !finalUrl.contains("__hdnea__")) {
                        finalUrl += if (finalUrl.contains("?")) "&" else "?"
                        finalUrl += sessionCookie.trim()
                    }

                    if (!resolveRes.isSuccessful) {
                        val reason = when (status) {
                            403 -> "Subscription Required / 403 Forbidden"
                            401 -> "Token Expired / Unauthorized"
                            404 -> "Stream Offline / 404"
                            500 -> "Internal Server Error"
                            else -> "Manifest Request Failed"
                        }
                        throw Exception("$reason|$status")
                    }
                    return finalUrl
                }
            } else {
                val msg = parsed["message"]?.jsonPrimitive?.content ?: "API Error"
                if (msg.contains("No eligible plans", ignoreCase = true) || code == 3012) throw Exception("No eligible plans found (3012)|3012")
                if (msg.contains("Subscription Required", ignoreCase = true) || code == 403) throw Exception("Subscription Required (403)|403")
                throw Exception("$msg|$code")
            }
        }
    }

    suspend fun exportWorkingStreamsAsM3u(context: Context, channelsToTest: List<LiveChannelItem>) {
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "working_channels.m3u")
            file.bufferedWriter().use { out ->
                out.write("#EXTM3U\n")
                for (channel in channelsToTest) {
                    try {
                        val resolved = getResolvedLiveUrl(context, channel.defaultChannelId, channel.title)
                        out.write("#EXTINF:-1 tvg-id=\"${channel.defaultChannelId}\" tvg-logo=\"${channel.logoUrl}\" group-title=\"${channel.category}\",${channel.title}\n")
                        resolved.headers.forEach { (k, v) -> out.write("#EXTHTTP:$k=$v\n") }
                        out.write("${resolved.url}\n")
                    } catch (e: Exception) {}
                }
            }
            withContext(Dispatchers.Main) {
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/mpegurl"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Export Working M3U"))
                } catch (e: Exception) {}
            }
        }
    }

    fun logout(context: Context) {
        cachedToken = ""
        cachedCrm = ""
        context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun getCategoryName(id: Int): String = when (id) {
        1 -> "Entertainment"
        2 -> "Movies"
        3 -> "Kids"
        4 -> "Sports"
        5 -> "Lifestyle"
        6 -> "Infotainment"
        7 -> "News"
        8 -> "Music"
        9 -> "Devotional"
        10 -> "Lifestyle"
        11 -> "Infotainment"
        12 -> "Regional"
        13 -> "Business"
        14 -> "Educational"
        15 -> "Shopping"
        16 -> "JioDarshan"
        else -> "General"
    }

    private fun getLanguageName(id: Int): String = when (id) {
        1 -> "Hindi"
        2 -> "Marathi"
        3 -> "Punjabi"
        4 -> "Urdu"
        5 -> "Bengali"
        6 -> "English"
        7 -> "Malayalam"
        8 -> "Tamil"
        9 -> "Gujarati"
        10 -> "Odia"
        11 -> "Telugu"
        12 -> "Bhojpuri"
        13 -> "Kannada"
        14 -> "Assamese"
        15 -> "Nepali"
        16 -> "French"
        else -> "Other"
    }
}