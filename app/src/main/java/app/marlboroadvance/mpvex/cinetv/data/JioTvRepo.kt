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

    private const val BASE_URL = "https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp"
    private const val USER_AGENT = "okhttp/3.14.9" 
    private const val APP_KEY = "ZWM2YjI1YzgtYmQ2YS0xMWU3LWJkYTMtZWIzNDI3ZTE2NDQ2"

    fun initTokens(context: Context) {
        val prefs = context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE)
        cachedToken = prefs.getString("ssoToken", "") ?: ""
        cachedCrm = prefs.getString("crmToken", "") ?: ""
    }

    fun isUserLoggedIn(): Boolean = cachedToken.isNotBlank()

    suspend fun requestOtp(mobileNumber: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fullPhone = if (mobileNumber.startsWith("+91")) mobileNumber else "+91$mobileNumber"
            val encodedPhone = Base64.encodeToString(fullPhone.toByteArray(), Base64.NO_WRAP)
            
            val jsonPayload = buildJsonObject { put("number", encodedPhone) }.toString()
            
            Log.d("JioTvRepo", "OTP Send Request URL: $BASE_URL/send")
            Log.d("JioTvRepo", "OTP Send Request Body: $jsonPayload")

            val request = Request.Builder()
                .url("$BASE_URL/send")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("appname", "RJIL_JioTV")
                .addHeader("os", "android")
                .addHeader("devicetype", "phone")
                .addHeader("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                val code = response.code
                Log.d("JioTvRepo", "OTP Send Response Code: $code")
                return@withContext code == 204 || response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("JioTvRepo", "OTP Send failed with exception", e)
            return@withContext false
        }
    }

    suspend fun verifyOtp(context: Context, mobileNumber: String, otp: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fullPhone = if (mobileNumber.startsWith("+91")) mobileNumber else "+91$mobileNumber"
            val encodedPhone = Base64.encodeToString(fullPhone.toByteArray(), Base64.NO_WRAP)
            
            // Replicating PHP device ID logic (16 chars)
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

            val url = "$BASE_URL/verify"
            Log.d("JioTvRepo", "Verify OTP Request URL: $url")
            Log.d("JioTvRepo", "Verify OTP Request Body: $jsonPayload")

            val request = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("appname", "RJIL_JioTV")
                .addHeader("os", "android")
                .addHeader("devicetype", "phone")
                .addHeader("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                val responseCode = response.code
                val body = response.body?.string() ?: ""
                
                Log.d("JioTvRepo", "Verify OTP Response Code: $responseCode")
                Log.d("JioTvRepo", "Verify OTP Response Body: $body")
                
                val parsed = json.parseToJsonElement(body).jsonObject
                val code = parsed["code"]?.jsonPrimitive?.intOrNull ?: 0
                
                if (code == 200) {
                    var dataObj = parsed["data"]?.jsonObject ?: throw Exception("Invalid API data format")
                    
                    val tempToken = dataObj["tempToken"]?.jsonPrimitive?.content ?: ""
                    val initialAuth = dataObj["authToken"]?.jsonPrimitive?.content ?: ""
                    val targetDeviceId = dataObj["deviceId"]?.jsonPrimitive?.content ?: genDeviceId
                    
                    // PHP expireallusers device limit clearance
                    if (tempToken.isNotBlank() && initialAuth.isBlank()) {
                        Log.d("JioTvRepo", "Temp token found, executing expireallusers (device limit clear)")
                        
                        val expirePayload = buildJsonObject {
                            put("appName", "RJIL_JioTV")
                            put("deviceId", targetDeviceId)
                        }.toString()
                        
                        val expireReq = Request.Builder()
                            .url("https://jiotvapi.media.jio.com/userservice/apis/v1/device/logoutall")
                            .post(expirePayload.toRequestBody("application/json".toMediaType()))
                            .addHeader("User-Agent", USER_AGENT)
                            .addHeader("x-platform", "android")
                            .addHeader("temptoken", tempToken)
                            .build()
                            
                        client.newCall(expireReq).execute().use { expireRes ->
                            val expBody = expireRes.body?.string() ?: ""
                            Log.d("JioTvRepo", "ExpireAllUsers Response: $expBody")
                            try {
                                val expParsed = json.parseToJsonElement(expBody).jsonObject
                                val expData = expParsed["data"]?.jsonObject
                                if (expData != null && expData["authToken"] != null) {
                                    dataObj = expData 
                                }
                            } catch (e: Exception) {
                                Log.e("JioTvRepo", "Failed to parse expireallusers response", e)
                            }
                        }
                    }
                    
                    // Exact PHP tokens
                    val finalSsoToken = dataObj["ssoToken"]?.jsonPrimitive?.content ?: ""
                    val finalAuthToken = dataObj["authToken"]?.jsonPrimitive?.content ?: ""
                    val finalRefreshToken = dataObj["refreshToken"]?.jsonPrimitive?.content ?: ""
                    val finalDeviceId = dataObj["deviceId"]?.jsonPrimitive?.content ?: targetDeviceId
                    
                    val userObj = dataObj["sessionAttributes"]?.jsonObject?.get("user")?.jsonObject
                    val finalCrm = userObj?.get("subscriberId")?.jsonPrimitive?.content ?: ""
                    val finalUniqueId = userObj?.get("unique")?.jsonPrimitive?.content ?: ""
                    val finalUserId = userObj?.get("uid")?.jsonPrimitive?.content ?: ""

                    if (finalSsoToken.isNotBlank() || finalAuthToken.isNotBlank()) {
                        cachedToken = finalSsoToken.ifBlank { finalAuthToken }
                        cachedCrm = finalCrm
                        
                        context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit()
                            .putString("ssoToken", finalSsoToken)
                            .putString("authToken", finalAuthToken)
                            .putString("refreshToken", finalRefreshToken)
                            .putString("crmToken", finalCrm)
                            .putString("deviceId", finalDeviceId)
                            .putString("uniqueId", finalUniqueId)
                            .putString("subscriberId", finalCrm)
                            .putString("userId", finalUserId)
                            .apply()
                            
                        Log.d("JioTvRepo", "All tokens saved successfully.")
                        return@withContext true
                    } else {
                        throw Exception("Token Generation Failed ❌")
                    }
                } else if (code == 1043) {
                    throw Exception("Incorrect OTP entered ❌")
                } else {
                    val errorMsg = parsed["message"]?.jsonPrimitive?.content ?: "Verification Failed ❌"
                    throw Exception(errorMsg)
                }
            }
        } catch (e: Exception) {
            Log.e("JioTvRepo", "Verify OTP failed", e)
            throw e 
        }
    }

    suspend fun fetchLiveChannelsFromAssets(context: Context): List<LiveChannelItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LiveChannelItem>()
        try {
            val url = "https://jiotv.data.cdn.jio.com/apis/v1.4/getMobileChannelList/get/?os=android&devicetype=phone"
            Log.d("JioTvRepo", "Fetching Live Channels API: $url")
            
            val request = Request.Builder().url(url).get().build()
                
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
                    Log.d("JioTvRepo", "Fetched ${list.size} channels from API successfully.")
                }
            }
        } catch (e: Exception) { Log.e("JioTvRepo", "Failed to fetch live channels", e) }
        return@withContext list
    }

    suspend fun getResolvedLiveUrl(context: Context, channelId: String): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE)
        val ssoToken = prefs.getString("ssoToken", "") ?: ""
        val authToken = prefs.getString("authToken", "") ?: ""
        val deviceId = prefs.getString("deviceId", "") ?: ""
        val uniqueId = prefs.getString("uniqueId", "") ?: ""
        val subId = prefs.getString("subscriberId", "") ?: ""
        val userId = prefs.getString("userId", "") ?: ""

        val payload = "stream_type=Seek&channel_id=$channelId"
        val endpoint = "https://jiotvapi.media.jio.com/userservice/apis/v1/geturl"
        
        Log.d("JioTvRepo", "Resolving Stream URL for channel: $channelId")
        Log.d("JioTvRepo", "Stream POST Body: $payload")
        
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("appkey", APP_KEY)
            .addHeader("devicetype", "phone")
            .addHeader("os", "android")
            .addHeader("deviceid", deviceId)
            .addHeader("versionCode", "290")
            .addHeader("osversion", "11")
            .addHeader("dm", "Android")
            .addHeader("x-platform", "android")
            .addHeader("uniqueid", uniqueId)
            .addHeader("usergroup", "tvYR7dGq1w==?")
            .addHeader("languageid", "6")
            .addHeader("userid", "ril$subId")
            .addHeader("crmid", subId)
            .addHeader("isott", "true")
            .addHeader("channel_id", channelId)
            .addHeader("accesstoken", authToken)
            .addHeader("ssotoken", ssoToken)
            .addHeader("subscriberid", subId)
            .addHeader("lbcookie", "1")
            .build()
            
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            Log.d("JioTvRepo", "Stream URL Response Code: ${response.code}")
            Log.d("JioTvRepo", "Stream URL Response Body: $responseBody")
            
            try {
                val parsed = json.parseToJsonElement(responseBody).jsonObject
                val code = parsed["code"]?.jsonPrimitive?.intOrNull
                
                if (code == 200) {
                    val m3u8Url = parsed["result"]?.jsonPrimitive?.content ?: ""
                    Log.d("JioTvRepo", "Resolved Final URL: $m3u8Url")
                    return@withContext m3u8Url
                } else {
                    throw Exception("API rejected stream generation: ${parsed["message"]?.jsonPrimitive?.content}")
                }
            } catch (e: Exception) {
                Log.e("JioTvRepo", "Failed to resolve final stream URL", e)
                throw e
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
