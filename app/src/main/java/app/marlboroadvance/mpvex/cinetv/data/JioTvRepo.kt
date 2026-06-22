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
import java.io.InputStream
import java.util.concurrent.TimeUnit
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
    private const val USER_AGENT = "okhttp/3.14.9"

    fun initTokens(context: Context) {
        val prefs = context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE)
        cachedToken = prefs.getString("sso_token", "") ?: ""
        cachedCrm = prefs.getString("crm_token", "") ?: ""
    }

    fun isUserLoggedIn(): Boolean = cachedToken.isNotBlank()

    // FIXED: Base64 encoding + header simulation as per jitendraunatti/login.php
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

    // FIXED: Handshake with device simulation
    suspend fun verifyOtp(context: Context, mobileNumber: String, otp: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fullPhone = if (mobileNumber.startsWith("+91")) mobileNumber else "+91$mobileNumber"
            val encodedPhone = Base64.encodeToString(fullPhone.toByteArray(), Base64.NO_WRAP)
            
            val jsonPayload = buildJsonObject {
                put("number", encodedPhone)
                put("otp", otp)
                put("deviceInfo", buildJsonObject {
                    put("consumptionDeviceName", "RMX1945")
                    put("info", buildJsonObject {
                        put("type", "android")
                        put("androidId", "android_id_sync_99")
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
                    
                    val ssoToken = parsed["ssoToken"]?.jsonPrimitive?.content ?: ""
                    // Match ssoToken structure
                    if (ssoToken.isNotBlank()) {
                        cachedToken = ssoToken
                        context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit()
                            .putString("sso_token", ssoToken)
                            .apply()
                        return@withContext true
                    }
                }
                return@withContext false
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

    suspend fun fetchLiveChannelsFromAssets(context: Context): List<LiveChannelItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LiveChannelItem>()
        // Note: Ideally switch to fetchLiveChannelsFromApi(context) using the PHP Logic if assets get outdated
        // For now, keeping your assets parser
        try {
            val inputStream: InputStream = context.assets.open("channels.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val rootObject = json.parseToJsonElement(jsonString).jsonObject
            for ((channelId, channelElement) in rootObject) {
                val channelNode = channelElement.jsonObject
                val name = channelNode["name"]?.jsonPrimitive?.content ?: ""
                val category = channelNode["genre"]?.jsonPrimitive?.content ?: "Entertainment"
                val language = channelNode["language"]?.jsonPrimitive?.content ?: "Hindi"
                val logoUrl = "https://jiotvimages.cdn.jio.com/dare_images/images/" + (channelNode["logo"]?.jsonPrimitive?.content ?: "$channelId.png")
                
                list.add(LiveChannelItem(channelId, name, category, language, logoUrl, "jiotv_live:$channelId"))
            }
        } catch (e: Exception) { Log.e("JioTvRepo", "Failed", e) }
        return@withContext list
    }

    suspend fun getResolvedLiveUrl(channelId: String): String = withContext(Dispatchers.IO) {
        // Using LB Cookie logic
        val token = if (cachedToken.isBlank()) "mock_token" else cachedToken
        return@withContext "https://jiotv.live.cdn.jio.com/$channelId/${channelId}_hd.m3u8?ver=2026&ssoToken=$token&lbcookie=1"
    }

    fun logout(context: Context) {
        cachedToken = ""
        context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
