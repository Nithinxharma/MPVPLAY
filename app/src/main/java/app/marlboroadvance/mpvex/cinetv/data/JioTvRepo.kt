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

    private fun restoreSessionFromAssets(context: Context): Boolean {
        try {
            val prefs = context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE)
            if (prefs.getString("authToken", "")!!.isNotBlank()) return true

            var ssoToken = ""
            var authToken = ""
            var refreshToken = ""
            var deviceId = ""
            var subscriberId = ""
            var uniqueId = ""
            var cookieStr = ""
            
            try {
                // Read & Decode creds.jtv exactly as PHP decrypt_data fallback expects (base64 raw payload)
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
            } catch (e: Exception) { Log.d("JioTvRepo", "Failed decoding creds.jtv: ${e.message}") }
            
            try {
                // Read cookie.jtv (hex encoded __hdnea__)
                val cookieHex = context.assets.open("cookie.jtv").bufferedReader().readText().trim()
                val cookieBytes = cookieHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                cookieStr = String(cookieBytes)
            } catch (e: Exception) { Log.d("JioTvRepo", "Failed decoding cookie.jtv: ${e.message}") }

            if (authToken.isNotBlank()) {
                cachedToken = authToken
                cachedCrm = subscriberId
                
                prefs.edit()
                    .putString("ssoToken", ssoToken)
                    .putString("authToken", authToken)
                    .putString("refreshToken", refreshToken)
                    .putString("crmToken", subscriberId)
                    .putString("deviceId", deviceId)
                    .putString("uniqueId", uniqueId)
                    .putString("sessionCookie", cookieStr)
                    .apply()
                return true
            }
        } catch (e: Exception) {
            Log.e("JioTvRepo", "Error restoring authenticated session from assets", e)
        }
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

            val request = Request.Builder()
                .url("https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/send")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("appname", "RJIL_JioTV")
                .addHeader("os", "android")
                .addHeader("devicetype", "phone")
                .addHeader("User-Agent", "okhttp/3.14.9")
                .build()

            client.newCall(request).execute().use { response ->
                return@withContext response.code == 204 || response.isSuccessful
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

    suspend fun verifyOtp(context: Context, mobileNumber: String, otp: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fullPhone = if (mobileNumber.startsWith("+91")) mobileNumber else "+91$mobileNumber"
            val encodedPhone = Base64.encodeToString(fullPhone.toByteArray(), Base64.NO_WRAP)
            
            // Replicating exactly PHP's sha1 + rand string generation (16 length)
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

            val request = Request.Builder()
                .url("https://jiotvapi.media.jio.com/userservice/apis/v1/loginotp/verify")
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("appname", "RJIL_JioTV")
                .addHeader("os", "android")
                .addHeader("devicetype", "phone")
                .addHeader("content-type", "application/json")
                .addHeader("User-Agent", "okhttp/3.14.9")
                .build()

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
                            .putString("ssoToken", finalSsoToken)
                            .putString("authToken", finalAuthToken)
                            .putString("refreshToken", finalRefreshToken)
                            .putString("crmToken", finalCrm)
                            .putString("deviceId", targetDeviceId)
                            .putString("uniqueId", finalUniqueId)
                            .apply()
                            
                        return@withContext true
                    }
                }
                
                // Fallback to strict error parsing per PHP cpfunctions.php requirement
                var errorMsg = "Unknown Error Occured : Code ${response.code}"
                if (parsed.containsKey("message") && parsed["message"]?.jsonPrimitive?.content?.isNotBlank() == true) {
                    errorMsg = "Jio Error - " + parsed["message"]?.jsonPrimitive?.content
                } else if (parsed["errors"]?.jsonArray?.getOrNull(1)?.jsonObject?.get("message") != null) {
                    errorMsg = "Jio Error - " + parsed["errors"]?.jsonArray?.get(1)?.jsonObject?.get("message")?.jsonPrimitive?.content
                } else if (parsed["errors"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("message") != null) {
                    errorMsg = "Jio Error - " + parsed["errors"]?.jsonArray?.get(0)?.jsonObject?.get("message")?.jsonPrimitive?.content
                }
                throw Exception(errorMsg)
            }
        } catch (e: Exception) {
            Log.e("JioTvRepo", "Verify OTP threw Exception", e)
            throw e 
        }
    }

    suspend fun fetchLiveChannelsFromAssets(context: Context): List<LiveChannelItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LiveChannelItem>()
        try {
            // Unfurled URL dynamically parsed by PHP's file_get_contents(base64_decode(strrev($url)))
            val request = Request.Builder()
                .url("https://raw.githubusercontent.com/mitthu786/api/main/jiotv.json")
                .get()
                .build()
                
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val rootArray = json.parseToJsonElement(body).jsonArray
                    for (i in 0 until rootArray.size) {
                        val channelNode = rootArray[i].jsonObject
                        val id = channelNode["channel_id"]?.jsonPrimitive?.content ?: ""
                        val name = channelNode["channel_name"]?.jsonPrimitive?.content ?: ""
                        val category = channelNode["channelCategoryId"]?.jsonPrimitive?.content ?: "Entertainment"
                        val language = channelNode["channelLanguageId"]?.jsonPrimitive?.content ?: "Hindi"
                        val logoUrl = channelNode["logoUrl"]?.jsonPrimitive?.content ?: ""
                        
                        list.add(LiveChannelItem(id, name, category, language, logoUrl, "jiotv_live:$id"))
                    }
                }
            }
        } catch (e: Exception) { Log.e("JioTvRepo", "Failed to fetch channels from API", e) }
        return@withContext list
    }

    suspend fun getResolvedLiveUrl(context: Context, channelId: String): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE)
        val ssoToken = prefs.getString("ssoToken", "") ?: ""
        val authToken = prefs.getString("authToken", "") ?: ""
        val deviceId = prefs.getString("deviceId", "") ?: ""
        val uniqueId = prefs.getString("uniqueId", "") ?: ""
        val crm = prefs.getString("crmToken", "") ?: ""

        val payload = "stream_type=Seek&channel_id=$channelId"
        
        Log.d("JioTvRepo", "Fetching Live Stream Segment API for ID: $channelId")
        
        val request = Request.Builder()
            .url("https://jiotvapi.media.jio.com/playback/apis/v1/geturl?langId=6")
            .post(payload.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .addHeader("Host", "jiotvapi.media.jio.com")
            .addHeader("appkey", "NzNiMDhlYzQyNjJm")
            .addHeader("channel_id", channelId)
            .addHeader("userid", crm)
            .addHeader("crmid", crm)
            .addHeader("deviceId", deviceId)
            .addHeader("devicetype", "phone")
            .addHeader("isott", "true")
            .addHeader("languageId", "6")
            .addHeader("lbcookie", "1")
            .addHeader("os", "android")
            .addHeader("dm", "Xiaomi 22101316UP")
            .addHeader("osversion", "14")
            .addHeader("srno", "250918144000")
            .addHeader("accesstoken", authToken)
            .addHeader("subscriberid", crm)
            .addHeader("uniqueId", uniqueId)
            .addHeader("usergroup", "tvYR7NSNn7rymo3F")
            .addHeader("User-Agent", "okhttp/4.12.13")
            .addHeader("versionCode", "452")
            .build()
            
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            Log.d("JioTvRepo", "Stream Resolution Response Code: ${response.code}")
            
            try {
                val parsed = json.parseToJsonElement(responseBody).jsonObject
                val code = parsed["code"]?.jsonPrimitive?.intOrNull
                
                if (code == 200) {
                    val m3u8Url = parsed["result"]?.jsonPrimitive?.content ?: ""
                    Log.d("JioTvRepo", "Resolved Authentic M3U8: $m3u8Url")
                    return@withContext m3u8Url
                } else {
                    throw Exception(parsed["message"]?.jsonPrimitive?.content ?: "Stream Error $code")
                }
            } catch (e: Exception) {
                Log.e("JioTvRepo", "Failed parsing final URL map", e)
                throw e
            }
        }
    }

    fun logout(context: Context) {
        cachedToken = ""
        cachedCrm = ""
        context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
