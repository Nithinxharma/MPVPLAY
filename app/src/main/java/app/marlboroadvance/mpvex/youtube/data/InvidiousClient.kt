package app.marlboroadvance.mpvex.youtube.data

import app.marlboroadvance.mpvex.youtube.model.YoutubeVideo
import app.marlboroadvance.mpvex.youtube.model.VideoDataResponse
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object InvidiousClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonParser = Json { 
        ignoreUnknownKeys = true
        coerceInputValues = true 
    }
    
    private val INSTANCES = listOf(
        "https://invidious.projectsegfau.lt",
        "https://yewtu.be",
        "https://invidious.privacydev.net",
        "https://iv.melmac.space"
    )

    suspend fun fetchTrendingVideos(type: String = "Movies"): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        for (baseUrl in INSTANCES) {
            val url = "$baseUrl/api/v1/trending?type=$type&region=IN"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .build()
            
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: return@use
                        val data = jsonParser.decodeFromString<List<YoutubeVideo>>(responseBody)
                        if (data.isNotEmpty()) {
                            return@withContext data
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("InvidiousClient", "Node $baseUrl failed. Shifting...")
            }
        }
        return@withContext emptyList()
    }

    suspend fun fetchDirectStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        for (baseUrl in INSTANCES) {
            val url = "$baseUrl/api/v1/videos/$videoId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .build()
            
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: return@use
                        val parsed = jsonParser.decodeFromString<VideoDataResponse>(responseBody)
                        
                        // RESTORED ORIGINAL BULLETPROOF LOGIC
                        // Grabs the best multiplexed MP4 (Video + Audio combined)
                        val streamUrl = parsed.formatStreams.firstOrNull { it.container == "mp4" }?.url
                            ?: parsed.formatStreams.firstOrNull()?.url
                            ?: parsed.adaptiveFormats.firstOrNull { it.container == "mp4" }?.url
                        
                        if (streamUrl != null) return@withContext streamUrl
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("InvidiousClient", "Stream collection rejected on node: $baseUrl")
            }
        }
        return@withContext null
    }

    suspend fun fetchSearchVideos(query: String): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        val encodedQuery = try {
            java.net.URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }

        for (baseUrl in INSTANCES) {
            val url = "$baseUrl/api/v1/search?q=$encodedQuery&type=video&region=IN"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
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
                android.util.Log.w("InvidiousClient", "Search compilation failed on node: $baseUrl")
            }
        }
        return@withContext emptyList()
    }
}