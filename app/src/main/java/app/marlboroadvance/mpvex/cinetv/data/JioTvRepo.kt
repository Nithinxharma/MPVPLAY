package app.marlboroadvance.mpvex.cinetv.data

import android.content.Context
import android.content.Intent
import android.util.Base64
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
                val source = if (obj["preferredSource"]?.jsonPrimitive?.content == "M3U") PlaybackSource.M3U else PlaybackSource.JIO_TV
                val failedArr = obj["failedM3uUrls"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                
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
                    userVerified = obj["userVerified"]?.jsonPrimitive?.boolean ?: false,
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
                    if (v.lastSuccessfulUrl != null) put("lastSuccessfulUrl", v.lastSuccessfulUrl)
                    put("lastTestedTime", v.lastTestedTime)
                    put("failureCount", v.failureCount)
                    put("successCount", v.successCount)
                    if (v.userFeedback != null) put("userFeedback", v.userFeedback)
                    if (v.mappedM3uName != null) put("mappedM3uName", v.mappedM3uName)
                    put("confidenceScore", v.confidenceScore)
                    put("isManualMapping", v.isManualMapping)
                    put("userVerified", v.userVerified)
                    put("failedM3uUrls", buildJsonArray { v.failedM3uUrls.forEach { add(it) } })
                })
            }
        }
        prefs.edit().putString("cache", rootObj.toString()).apply()
    }

    fun handleUserPlaybackFeedback(context: Context, channelId: String, worked: Boolean, playedUrl: String, channelName: String) {
        val cache = getChannelCacheMap(context)
        val entry = cache[channelId] ?: ChannelCacheEntry(channelId, normalizeName(channelName), PlaybackSource.JIO_TV)
        
        val isM3u = playedUrl.contains(".m3u") || playedUrl.contains("tsjiotv") || loadM3uFallback(context).any { it.url == playedUrl }
        
        if (worked) {
            entry.userFeedback = true
            entry.userVerified = true
            if (isM3u) {
                entry.preferredSource = PlaybackSource.M3U
                entry.lastSuccessfulUrl = playedUrl
                val match = loadM3uFallback(context).find { it.url == playedUrl }
                entry.mappedM3uName = match?.name
            } else {
                entry.preferredSource = PlaybackSource.JIO_TV
                entry.lastSuccessfulUrl = "JIO_SOURCE"
            }
        } else {
            entry.userFeedback = false
            entry.userVerified = false
            
            val newFailed = entry.failedM3uUrls.toMutableList()
            if (isM3u) {
                if (!newFailed.contains(playedUrl)) newFailed.add(playedUrl)
            } else {
                if (!newFailed.contains("JIO_SOURCE")) newFailed.add("JIO_SOURCE")
            }
            entry.failedM3uUrls = newFailed
            entry.preferredSource = PlaybackSource.M3U 
        }
        saveChannelCache(context, entry)
    }

    fun resetMapping(context: Context, channelId: String) {
        val cache = getChannelCacheMap(context)
        val entry = cache[channelId]
        if (entry != null) {
            entry.userVerified = false
            entry.userFeedback = null
            entry.failedM3uUrls = emptyList()
            entry.preferredSource = PlaybackSource.JIO_TV
            entry.lastSuccessfulUrl = null
            entry.mappedM3uName = null
            entry.isManualMapping = false
            saveChannelCache(context, entry)
        }
    }

    // --- M3U FALLBACK ENGINE & MANAGER ---
    data class M3uEntry(val name: String, val normalizedName: String, val url: String, val headers: Map<String, String>)
  
    private var cachedM3uEntries: List<M3uEntry>? = null

    private val aliasMap = mapOf(
        "nick" to "nickelodeon",
        "cn" to "cartoonnetwork",
        "set" to "sonyentertainmenttelevision"
    )

    fun normalizeName(name: String): String {
        var n = name.lowercase()
        val stopWords = listOf("hd", "sd", "hindi", "english", "tamil", "telugu", "kannada", "malayalam", "punjabi", "marathi", "gujarati", "bengali", "odia", "assamese", "urdu", "4k", "uhd")
        for (word in stopWords) n = n.replace(Regex("\\b$word\\b"), "")
        n = n.replace(Regex("[^a-z0-9]"), " ") 
        n = n.replace(Regex("\\s+"), "") 
        return aliasMap[n] ?: n
    }

    fun getM3uFile(context: Context): File = File(context.filesDir, "in.m3u")
    
    fun readM3uText(context: Context): String {
        val file = getM3uFile(context)
        return if (file.exists()) file.readText() else context.assets.open("in.m3u").bufferedReader().readText()
    }
    
    fun saveM3uText(context: Context, content: String) {
        getM3uFile(context).writeText(content)
    }
    
    fun reloadM3uParser() {
        cachedM3uEntries = null
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
                    val k = trimmed.substringAfter(":").substringBefore("=");
                    val v = trimmed.substringAfter("=")
                    currentHeaders[k] = v
                } else if (trimmed.startsWith("#KODIPROP:inputstream.adaptive.stream_headers=")) {
                    trimmed.substringAfter("=").split("&").forEach { val kv = it.split("=");
                    if(kv.size == 2) currentHeaders[kv[0]] = kv[1] }
                } else if (trimmed.startsWith("http")) {
                    entries.add(M3uEntry(currentName, normalizeName(currentName), trimmed, currentHeaders.toMap()))
                    currentHeaders.clear()
                }
            }
        } catch (e: Exception) {}
        cachedM3uEntries = entries
        return entries
    }

    data class MatchResult(val url: String, val mappedName: String, val confidence: Int, val headers: Map<String, String>)

    private fun getAllM3uMatches(context: Context, searchTarget: String, isManual: Boolean): List<MatchResult> {
        val entries = loadM3uFallback(context)
        val exactName = searchTarget.lowercase().trim()
        val targetNorm = normalizeName(searchTarget)
    
        if (targetNorm.isBlank() && exactName.isBlank()) return emptyList()

        val results = mutableListOf<MatchResult>()

        for (m3u in entries) {
            val m3uExact = m3u.name.lowercase().trim()
            val m3uNorm = m3u.normalizedName
            var confidence = 0
            
            if (m3uExact == exactName) {
                confidence = 100 
            } else if (aliasMap.containsValue(m3uNorm) && m3uNorm == targetNorm) {
                confidence = 99 
            } else if (m3uNorm == targetNorm) {
                confidence = 98 
            } else if (!isManual && m3uNorm.contains(targetNorm)) {
                confidence = 95 
            } else if (!isManual && targetNorm.contains(m3uNorm)) {
                confidence = 94 
            } else if (!isManual) {
                val minLen = minOf(m3uNorm.length, targetNorm.length).toDouble()
                val maxLen = maxOf(m3uNorm.length, targetNorm.length).toDouble()
                val ratio = minLen / maxLen
                if (ratio > 0.90) confidence = (ratio * 100).toInt()
            } else if (isManual && (m3uExact.contains(exactName) || exactName.contains(m3uExact))) {
                 confidence = 90
            }
            
            if (confidence >= 90) {
                results.add(MatchResult(m3u.url, m3u.name, confidence, m3u.headers))
            }
        }
        
        return results.sortedByDescending { it.confidence }
    }

    suspend fun getResolvedLiveUrl(context: Context, channelId: String, channelName: String = "Unknown"): ResolvedStream = withContext(Dispatchers.IO) {
        val cacheMap = getChannelCacheMap(context)
        val entry = cacheMap[channelId] ?: ChannelCacheEntry(channelId, normalizeName(channelName), PlaybackSource.JIO_TV)
        
        // Priority 1: User Verified
        if (entry.userVerified && entry.lastSuccessfulUrl != null) {
            val url = entry.lastSuccessfulUrl!!
            if (entry.preferredSource == PlaybackSource.M3U) {
                val match = loadM3uFallback(context).find { it.url == url }
                return@withContext ResolvedStream(url, PlaybackSource.M3U, match?.headers ?: emptyMap(), match?.name ?: "")
            } else {
                try {
                    val jioUrl = resolveOriginalJioTvStream(context, channelId, channelName)
                    return@withContext ResolvedStream(jioUrl, PlaybackSource.JIO_TV, emptyMap(), "Jio Official")
                } catch (e: Exception) {
                    // Fallthrough to hunt 
                }
            }
        }

        // Priority 2: Manual Mapping
        if (entry.isManualMapping && !entry.mappedM3uName.isNullOrBlank()) {
            val matches = getAllM3uMatches(context, entry.mappedM3uName!!, true)
            for (match in matches) {
                if (!entry.failedM3uUrls.contains(match.url)) {
                    return@withContext ResolvedStream(match.url, PlaybackSource.M3U, match.headers, match.mappedName)
                }
            }
        }

        // Priority 3: Try JioTV Official
        if (entry.preferredSource == PlaybackSource.JIO_TV && !entry.failedM3uUrls.contains("JIO_SOURCE")) {
            try {
                val jioUrl = resolveOriginalJioTvStream(context, channelId, channelName)
                return@withContext ResolvedStream(jioUrl, PlaybackSource.JIO_TV, emptyMap(), "Jio Official")
            } catch (e: Exception) {
                // Technically failed
            }
        }

        // Priority 4: Fallback M3U Automatic Matching
        val matches = getAllM3uMatches(context, channelName, false)
        for (match in matches) {
            if (!entry.failedM3uUrls.contains(match.url)) {
                return@withContext ResolvedStream(match.url, PlaybackSource.M3U, match.headers, match.mappedName)
            }
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

    // --- AUTH & LOGIN (Preserved untouched) ---
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

    fun logout(context: Context) {
        cachedToken = ""
        cachedCrm = ""
        context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun getCategoryName(id: Int): String = when (id) {
        1 -> "Entertainment"; 2 -> "Movies"; 3 -> "Kids"; 4 -> "Sports"; 5 -> "Lifestyle"
        6 -> "Infotainment"; 7 -> "News"; 8 -> "Music"; 9 -> "Devotional"; 10 -> "Lifestyle"
        11 -> "Infotainment"; 12 -> "Regional"; 13 -> "Business"; 14 -> "Educational"; 15 -> "Shopping"; 16 -> "JioDarshan"
        else -> "General"
    }

    private fun getLanguageName(id: Int): String = when (id) {
        1 -> "Hindi"; 2 -> "Marathi"; 3 -> "Punjabi"; 4 -> "Urdu"; 5 -> "Bengali"
        6 -> "English"; 7 -> "Malayalam"; 8 -> "Tamil"; 9 -> "Gujarati"; 10 -> "Odia"
        11 -> "Telugu"; 12 -> "Bhojpuri"; 13 -> "Kannada"; 14 -> "Assamese"; 15 -> "Nepali"; 16 -> "French"
        else -> "Other"
    }

    // --- NEW EXTENSIONS FOR M3U MANAGER ---
    
    // Dedicated client for quick stream availability checks without waiting too long
    private val quickClient = client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun saveManualMapping(context: Context, jioChannelId: String, m3uName: String, m3uUrl: String) {
        val cache = getChannelCacheMap(context)
        // Store URL mappedName manually within the legacy structure to avoid modifying CineTvModels.kt destructively
        val entry = cache[jioChannelId] ?: ChannelCacheEntry(jioChannelId, normalizeName(m3uName), PlaybackSource.M3U)
        entry.mappedM3uName = m3uName
        entry.lastSuccessfulUrl = m3uUrl
        entry.isManualMapping = true
        entry.preferredSource = PlaybackSource.M3U
        saveChannelCache(context, entry)
    }

    fun removeManualMapping(context: Context, channelId: String) {
        val cache = getChannelCacheMap(context)
        val entry = cache[channelId]
        if (entry != null) {
            entry.isManualMapping = false
            entry.mappedM3uName = null
            saveChannelCache(context, entry)
        }
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

    data class PlaylistMeta(val name: String, val channelCount: Int, val lastUpdated: Long)

    fun getPlaylistMeta(context: Context): PlaylistMeta {
        val prefs = context.getSharedPreferences("JioTvSmartCache", Context.MODE_PRIVATE)
        return PlaylistMeta(
            name = "in.m3u",
            channelCount = prefs.getInt("playlistCount", 0),
            lastUpdated = prefs.getLong("playlistUpdated", 0L)
        )
    }

    suspend fun syncPlaylistFromUrl(context: Context, url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext false
                    if (body.contains("#EXTM3U")) {
                        saveM3uText(context, body)
                        val prefs = context.getSharedPreferences("JioTvSmartCache", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putLong("playlistUpdated", System.currentTimeMillis())
                            .putInt("playlistCount", body.split("#EXTINF").size - 1)
                            .apply()
                        return@withContext true
                    }
                }
            }
        } catch (e: Exception) {}
        return@withContext false
    }
}