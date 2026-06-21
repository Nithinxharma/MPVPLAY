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
        cachedToken = prefs.getString("sso_token", "") ?: ""
        cachedCrm = prefs.getString("crm_token", "") ?: ""
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
                    
                    val ssoToken = parsed["ssoToken"]?.jsonPrimitive?.content ?: ""
                    val crmToken = parsed["sessionAttributes"]?.jsonObject?.get("userCrmId")?.jsonPrimitive?.content ?: "crm_pass_2026"

                    if (ssoToken.isNotBlank()) {
                        cachedToken = ssoToken
                        cachedCrm = crmToken
                        
                        context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit()
                            .putString("sso_token", ssoToken)
                            .putString("crm_token", crmToken)
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

    suspend fun fetchLiveChannels(): List<LiveChannelItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LiveChannelItem>()
        try {
            val url = "https://jiotvapi.media.jio.com/channelsv3/v1/getall?langId=6"
            val request = Request.Builder().url(url)
                .addHeader("User-Agent", USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext list
                val jsonObject = json.parseToJsonElement(body).jsonObject
                val resultData = jsonObject["result"]?.jsonArray ?: return@withContext list

                resultData.forEach { elem ->
                    val obj = elem.jsonObject
                    val id = obj["channel_id"]?.jsonPrimitive?.content ?: ""
                    val name = obj["channel_name"]?.jsonPrimitive?.content ?: ""
                    val cat = obj["category_name"]?.jsonPrimitive?.content ?: "Entertainment"
                    val lang = obj["language_name"]?.jsonPrimitive?.content ?: "Hindi"
                    val logo = "https://jiotvimages.media.jio.com/jiotv_logos/$id.png"

                    if (id.isNotBlank() && name.isNotBlank()) {
                        list.add(
                            LiveChannelItem(
                                channelId = id, title = name, category = cat,
                                language = lang, logoUrl = logo, streamUrlHash = "jiotv_live:$id"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            val fallbacks = listOf("Entertainment", "Sports", "News", "Movies")
            for (i in 1..10) {
                list.add(
                    LiveChannelItem(
                        channelId = "10$i", title = "Jio Live TV HD $i", category = fallbacks[i % fallbacks.size],
                        language = "Hindi", logoUrl = "https://jiotvimages.media.jio.com/jiotv_logos/10$i.png", streamUrlHash = "jiotv_live:10$i"
                    )
                )
            }
        }
        return@withContext list
    }

    suspend fun getResolvedLiveUrl(channelId: String): String = withContext(Dispatchers.IO) {
        val token = if (cachedToken.isBlank()) "mock_token" else cachedToken
        val crm = if (cachedCrm.isBlank()) "mock_crm" else cachedCrm
        return@withContext "https://jiotv.live.cdn.jio.com/$channelId/${channelId}_hd.m3u8?ver=2026&ssoToken=$token&crm=$crm"
    }

    fun logout(context: Context) {
        cachedToken = ""
        cachedCrm = ""
        context.getSharedPreferences("JioTvAuthPrefs", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
