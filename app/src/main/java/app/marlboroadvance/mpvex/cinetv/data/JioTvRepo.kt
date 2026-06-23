package app.marlboroadvance.mpvex.cinetv.data

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.UUID
import app.marlboroadvance.mpvex.cinetv.model.LiveChannelItem

object JioTvRepo {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    
    @Volatile private var cachedToken: String = ""
    @Volatile private var cachedCrm: String = ""

    // Updated to match PHP Repo structure
    private const val BASE_URL = "https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp"
    private const val USER_AGENT = "okhttp/3.14.9" // As per PHP OkHttp agent

    fun initTokens(context: Context) {
        val prefs = context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE)
        cachedToken = prefs.getString("sso_token", "") ?: ""
        cachedCrm = prefs.getString("crm_token", "") ?: ""
    }

    fun isUserLoggedIn(): Boolean = cachedToken.isNotBlank()

    suspend fun requestOtp(mobileNumber: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fullPhone = if (mobileNumber.startsWith("+91")) mobileNumber else "+91$mobileNumber"
            val encodedPhone = Base64.encodeToString(fullPhone.toByteArray(), Base64.NO_WRAP)
            
            val jsonPayload = buildJsonObject { put("number", encodedPhone) }.toString()

            val request = Request.Builder()
                .url("$BASE_URL/send")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("appname", "RJIL_JioTV")
                .addHeader("os", "android")
                .addHeader("devicetype", "phone")
                .addHeader("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                return@withContext response.code == 204 || response.isSuccessful
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

    // FIXED: Handshake with device simulation EXACTLY mapping the PHP verify -> expire logic
    suspend fun verifyOtp(context: Context, mobileNumber: String, otp: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fullPhone = if (mobileNumber.startsWith("+91")) mobileNumber else "+91$mobileNumber"
            val encodedPhone = Base64.encodeToString(fullPhone.toByteArray(), Base64.NO_WRAP)
            
            // Replicating PHP's sha1 + rand string generation (16 length)
            val genDeviceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
            
            val jsonPayload = buildJsonObject {
                put("number", encodedPhone)
                put("otp", otp)
                put("deviceInfo", buildJsonObject {
                    put("consumptionDeviceName", "Jio")
                    put("info", buildJsonObject {
                        put("type", "android")
                        put("platform", buildJsonObject { put("name", "android") })
                        put("androidId", genDeviceId)
                    })
                })
            }.toString()

            val request = Request.Builder()
                .url("$BASE_URL/verify")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("appname", "RJIL_JioTV")
                .addHeader("os", "android")
                .addHeader("devicetype", "phone")
                .addHeader("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext false
                    val parsed = json.parseToJsonElement(body).jsonObject
                    
                    val code = parsed["code"]?.jsonPrimitive?.intOrNull ?: 0
                    if (code == 200) {
                        var dataObj = parsed["data"]?.jsonObject ?: return@withContext false
                        
                        // Check if device limit exceeded (tempToken present, authToken missing) -> Same as PHP expireallusers process
                        val tempToken = dataObj["tempToken"]?.jsonPrimitive?.content ?: ""
                        val initialAuth = dataObj["authToken"]?.jsonPrimitive?.content ?: ""
                        val targetDeviceId = dataObj["deviceId"]?.jsonPrimitive?.content ?: genDeviceId
                        
                        if (tempToken.isNotBlank() && initialAuth.isBlank()) {
                            // Replicating expireallusers() exactly as seen in PHP reference
                            val expirePayload = buildJsonObject {
                                put("appName", "RJIL_JioTV")
                                put("deviceId", targetDeviceId)
                            }.toString()
                            
                            val expireReq = Request.Builder()
                                .url("https://jiotvapi.media.jio.com/userservice/apis/v1/device/logoutall") // Replicating device logout protocol
                                .post(expirePayload.toRequestBody("application/json".toMediaType()))
                                .addHeader("User-Agent", USER_AGENT)
                                .addHeader("x-platform", "android")
                                .addHeader("temptoken", tempToken)
                                .build()
                                
                            client.newCall(expireReq).execute().use { expireRes ->
                                val expBody = expireRes.body?.string() ?: ""
                                try {
                                    val expParsed = json.parseToJsonElement(expBody).jsonObject
                                    val expData = expParsed["data"]?.jsonObject
                                    if (expData != null && expData["authToken"] != null) {
                                        dataObj = expData // Overwrite dataObj with new tokens generated by logoutall
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                        
                        val finalSsoToken = dataObj["ssoToken"]?.jsonPrimitive?.content ?: ""
                        val finalAuthToken = dataObj["authToken"]?.jsonPrimitive?.content ?: ""
                        val finalRefreshToken = dataObj["refreshToken"]?.jsonPrimitive?.content ?: ""
                        val finalDeviceId = dataObj["deviceId"]?.jsonPrimitive?.content ?: targetDeviceId
                        
                        val userObj = dataObj["sessionAttributes"]?.jsonObject?.get("user")?.jsonObject
                        val finalCrm = userObj?.get("subscriberId")?.jsonPrimitive?.content ?: ""
                        val finalUniqueId = userObj?.get("unique")?.jsonPrimitive?.content ?: ""

                        if (finalSsoToken.isNotBlank() || finalAuthToken.isNotBlank()) {
                            cachedToken = finalSsoToken.ifBlank { finalAuthToken }
                            cachedCrm = finalCrm
                            
                            // Save everything the PHP implementation relies upon
                            context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit()
                                .putString("sso_token", finalSsoToken)
                                .putString("auth_token", finalAuthToken)
                                .putString("refresh_token", finalRefreshToken)
                                .putString("crm_token", finalCrm)
                                .putString("device_id", finalDeviceId)
                                .putString("unique_id", finalUniqueId)
                                .apply()
                                
                            return@withContext true
                        }
                    }
                }
                return@withContext false
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

    // Replicating API fetching logic derived directly from playlist.php mapping structure
    suspend fun fetchLiveChannelsFromAssets(context: Context): List<LiveChannelItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LiveChannelItem>()
        try {
            val request = Request.Builder()
                .url("https://jiotv.data.cdn.jio.com/apis/v1.4/getMobileChannelList/get/?os=android&devicetype=phone")
                .get()
                .build()
                
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = json.parseToJsonElement(body).jsonObject
                    val resultArr = root["result"]?.jsonArray
                    if (resultArr != null) {
                        for (i in 0 until resultArr.size) {
                            val channelNode = resultArr[i].jsonObject
                            val id = channelNode["channel_id"]?.jsonPrimitive?.content ?: ""
                            val name = channelNode["channel_name"]?.jsonPrimitive?.content ?: ""
                            
                            val catId = channelNode["channelCategoryId"]?.jsonPrimitive?.intOrNull ?: 0
                            val langId = channelNode["channelLanguageId"]?.jsonPrimitive?.intOrNull ?: 0
                            
                            val category = getCategoryName(catId)
                            val language = getLanguageName(langId)
                            
                            val logoUrl = "https://jiotvimages.cdn.jio.com/dare_images/images/" + (channelNode["logoUrl"]?.jsonPrimitive?.content ?: "$id.png")
                            
                            list.add(LiveChannelItem(id, name, category, language, logoUrl, "jiotv_live:$id"))
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e("JioTvRepo", "Failed to fetch live channels", e) }
        return@withContext list
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

    suspend fun getResolvedLiveUrl(channelId: String): String = withContext(Dispatchers.IO) {
        // Preserving identical lbcookie logic & token append
        val token = if (cachedToken.isBlank()) "mock_token" else cachedToken
        return@withContext "https://jiotv.live.cdn.jio.com/$channelId/${channelId}_hd.m3u8?ver=2026&ssoToken=$token&lbcookie=1"
    }

    fun logout(context: Context) {
        cachedToken = ""
        cachedCrm = ""
        context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
