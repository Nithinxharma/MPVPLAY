package app.marlboroadvance.mpvex.cinetv.data

import android.content.Context
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

    private const val USER_AGENT = "JioTV Android App Framework"

    fun initTokens(context: Context) {
        val prefs = context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE)
        cachedToken = prefs.getString("sso_token", "") ?: "[span_3](start_span)"[span_3](end_span)
        cachedCrm = prefs.getString("crm_token", "") ?: "[span_4](start_span)"[span_4](end_span)
    }

    fun isUserLoggedIn(): Boolean = cachedToken.isNotBlank()

    suspend fun requestOtp(mobileNumber: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.jio.com/v3/users/loginotp"
            val jsonPayload = buildJsonObject {
                put("number", mobileNumber)
                put("appname", "RJIL_JioTV")
            }.toString()

            val request = Request.Builder().url(url)
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

    suspend fun verifyOtp(context: Context, mobileNumber: String, otp: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.jio.com/v3/users/verifyotp"
            val jsonPayload = buildJsonObject {
                put("number", mobileNumber)
                put("otp", otp)
                put("appname", "RJIL_JioTV")
            }.toString()

            val request = Request.Builder().url(url)
                .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@withContext false
                    val parsed = json.parseToJsonElement(body).jsonObject
                    
                    val ssoToken = parsed["ssoToken"]?.jsonPrimitive?.content ?: "[span_5](start_span)"[span_5](end_span)
                    val crmToken = parsed["sessionAttributes"]?.jsonObject?.get("userCrmId")?.jsonPrimitive?.content ?: "crm_pass_2026[span_6](start_span)"[span_6](end_span)

                    if (ssoToken.isNotBlank()) {
                        cachedToken = ssoToken
                        cachedCrm = crmToken
                        
                        context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit()
                            .putString("sso_token", ssoToken)[span_7](start_span)[span_7](end_span)
                            .putString("crm_token", crmToken)[span_8](start_span)[span_8](end_span)
                            .apply()
                        return@withContext true
                    }
                }
            }
            return@withContext false
        } catch (e: Exception) {
            return@withContext false
        }
    }

    /**
     * Reads channel objects directly from the local assets directory tree[span_9](start_span)[span_9](end_span)[span_10](start_span)[span_10](end_span)
     */
    suspend fun fetchLiveChannelsFromAssets(context: Context): List<LiveChannelItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LiveChannelItem>()
        try {
            // Read local database mapping structural nodes safely[span_11](start_span)[span_11](end_span)
            val inputStream: InputStream = context.assets.open("channels.json")[span_12](start_span)[span_12](end_span)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val rootObject = json.parseToJsonElement(jsonString).jsonObject[span_13](start_span)[span_13](end_span)

            rootObject.keys.forEach { channelId ->
                val channelNode = rootObject[channelId]?.jsonObject ?: return@forEach
                val name = channelNode["name"]?.jsonPrimitive?.content ?: "[span_14](start_span)"[span_14](end_span)
                val genre = channelNode["genre"]?.jsonPrimitive?.content ?: "News[span_15](start_span)"[span_15](end_span)
                val language = channelNode["language"]?.jsonPrimitive?.content ?: "Hindi[span_16](start_span)"[span_16](end_span)
                val defaultLogo = channelNode["default_logo"]?.jsonPrimitive?.content ?: "$channelId.png[span_17](start_span)"[span_17](end_span)
                
                // Absolute image source resolution logic mapping
                val logoUrl = "https://jiotvimages.media.jio.com/jiotv_logos/$defaultLogo"

                if (name.isNotBlank()) {
                    list.add(
                        LiveChannelItem(
                            channelId = channelId,
                            title = name,
                            category = genre,
                            language = language,
                            logoUrl = logoUrl,
                            streamUrlHash = "jiotv_live:$channelId"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("JioTvRepo", "Asset read exception, running basic static map arrays as fallback execution context.")
            val fallbacks = listOf("Entertainment", "Sports", "News", "Movies")
            for (i in 1..10) {
                list.add(
                    LiveChannelItem(
                        channelId = "173", title = "Aaj Tak HD Fallback", category = fallbacks[i % fallbacks.size],
                        language = "Hindi", logoUrl = "https://jiotvimages.media.jio.com/jiotv_logos/Aaj_Tak.png", streamUrlHash = "jiotv_live:173"
                    )
                )
            }
        }
        return@withContext list
    }

    suspend fun getResolvedLiveUrl(channelId: String): String = withContext(Dispatchers.IO) {
        val token = if (cachedToken.isBlank()) "mock_token" else cachedToken[span_18](start_span)[span_18](end_span)
        val crm = if (cachedCrm.isBlank()) "mock_crm" else cachedCrm[span_19](start_span)[span_19](end_span)
        return@withContext "https://jiotv.live.cdn.jio.com/$channelId/${channelId}_hd.m3u8?ver=2026&ssoToken=$token&crm=$crm[span_20](start_span)"[span_20](end_span)
    }

    fun logout(context: Context) {
        cachedToken = ""
        cachedCrm = ""
        context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
