package xyz.mpv.rex.features.cinetube.data

import xyz.mpv.rex.features.cinetube.model.YoutubeVideo
import xyz.mpv.rex.features.cinetube.model.VideoDataResponse
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.FormBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object InvidiousClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    
    // Dynamic Settings States (Can be modified via FeaturesSetting layer)
    var currentInstance = "https://invidious.projectsegfau.lt"
    var userAuthToken: String? = null // Holds session token once logged in

    private val INSTANCES = listOf(
        "https://invidious.projectsegfau.lt",
        "https://yewtu.be",
        "https://invidious.privacydev.net",
        "https://iv.melmac.space"
    )

    private fun getActiveInstance(): String = currentInstance

    /**
     * Authenticates user against the active instance to grab session authentication loops
     */
    suspend fun loginUser(username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = getActiveInstance()
        val url = "$baseUrl/api/v1/auth/login"
        
        val formBody = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    // Extract authorization token from response header or cookie structure
                    val token = response.header("Authorization") ?: response.header("Set-Cookie")
                    if (token != null) {
                        userAuthToken = token
                        android.util.Log.d("InvidiousClient", "User logged in successfully to $baseUrl")
                        return@withContext true
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("InvidiousClient", "Login execution failed", e)
        }
        return@withContext false
    }

    /**
     * Fetches personalized subscription feed if authenticated, drops back to India trending if guest
     */
    suspend fun fetchPersonalizedFeed(): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        val token = userAuthToken
        if (token == null) {
            // Fallback cleanly to regional trending parameters if unauthenticated
            return@withContext fetchTrendingVideos()
        }

        val baseUrl = getActiveInstance()
        val url = "$baseUrl/api/v1/auth/feed"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", token)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: return@use
                    return@withContext jsonParser.decodeFromString<List<YoutubeVideo>>(responseBody)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("InvidiousClient", "Failed parsing personalized feed. Relying on default context.")
        }
        return@withContext fetchTrendingVideos()
    }

    suspend fun fetchTrendingVideos(type: String = "Movies"): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        val preferredUrl = getActiveInstance()
        val targetedNodes = listOf(preferredUrl) + (INSTANCES - preferredUrl)

        for (baseUrl in targetedNodes) {
            val url = "$baseUrl/api/v1/trending?type=$type&region=IN"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: return@use
                        val data = jsonParser.decodeFromString<List<YoutubeVideo>>(responseBody)
                        if (data.isNotEmpty()) return@withContext data
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("InvidiousClient", "Node $baseUrl failed target metrics.")
            }
        }
        return@withContext emptyList()
    }

    suspend fun fetchDirectStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val preferredUrl = getActiveInstance()
        val targetedNodes = listOf(preferredUrl) + (INSTANCES - preferredUrl)

        for (baseUrl in targetedNodes) {
            val url = "$baseUrl/api/v1/videos/$videoId"
            val request = Request.Builder()
                .url(url)
                .build()
            
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: return@use
                        val parsed = jsonParser.decodeFromString<VideoDataResponse>(responseBody)
                        
                        val streamUrl = parsed.formatStreams.firstOrNull { it.container == "mp4" }?.url
                            ?: parsed.formatStreams.firstOrNull()?.url
                        
                        if (streamUrl != null) return@withContext streamUrl
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("InvidiousClient", "Stream failure on node: $baseUrl")
            }
        }
        return@withContext null
    }

    suspend fun fetchSearchVideos(query: String): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        val encodedQuery = try { java.net.URLEncoder.encode(query, "UTF-8") } catch (e: Exception) { query }
        val preferredUrl = getActiveInstance()
        val targetedNodes = listOf(preferredUrl) + (INSTANCES - preferredUrl)

        for (baseUrl in targetedNodes) {
            val url = "$baseUrl/api/v1/search?q=$encodedQuery&type=video&region=IN"
            val request = Request.Builder()
                .url(url)
                .build()
            
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: return@use
                        val data = jsonParser.decodeFromString<List<YoutubeVideo>>(responseBody)
                        if (data.isNotEmpty()) return@withContext data
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("InvidiousClient", "Search fallback processed for node: $baseUrl")
            }
        }
        return@withContext emptyList()
    }

    /**
     * Updates settings configuration parameters dynamically via the UI dashboard layer
     */
    fun updateInstanceSetting(customUrl: String) {
        if (customUrl.startsWith("http")) {
            currentInstance = customUrl
        }
    }
}
