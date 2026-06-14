package app.marlboroadvance.mpvex.youtube.data

import app.marlboroadvance.mpvex.youtube.model.YoutubeVideo
import app.marlboroadvance.mpvex.youtube.model.VideoDataResponse
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object InvidiousClient {
    private val client = OkHttpClient()
    private val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    
    // Public reliable global Invidious instance url baseline
    private const val INSTANCE_URL = "https://vid.puffyan.us"

    /**
     * Fetches trending videos from Invidious api to map on YouTube / Shorts channel feeds
     */
    suspend fun fetchTrendingVideos(type: String = "Movies"): List<YoutubeVideo> = withContext(Dispatchers.IO) {
        val url = "$INSTANCE_URL/api/v1/trending?type=$type"
        val request = Request.Builder().url(url).build()
        
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val responseBody = response.body?.string() ?: return@withContext emptyList()
                return@withContext jsonParser.decodeFromString<List<YoutubeVideo>>(responseBody)
            }
        } catch (e: Exception) {
            android.util.Log.e("InvidiousClient", "Structural transmission failure inside trend threads", e)
            return@withContext emptyList()
        }
    }

    /**
     * Extracts direct stream mkv/mp4 download links to pass straight to internal MPV layout engine
     */
    suspend fun fetchDirectStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        val url = "$INSTANCE_URL/api/v1/videos/$videoId"
        val request = Request.Builder().url(url).build()
        
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val responseBody = response.body?.string() ?: return@withContext null
                val parsed = jsonParser.decodeFromString<VideoDataResponse>(responseBody)
                
                // Return best resolution playable progressive stream url
                return@withContext parsed.formatStreams.firstOrNull { it.container == "mp4" }?.url
                    ?: parsed.formatStreams.firstOrNull()?.url
                    ?: parsed.adaptiveFormats.firstOrNull { it.container == "mp4" }?.url
            }
        } catch (e: Exception) {
            android.util.Log.e("InvidiousClient", "Format analytical link extraction failure on video token: $videoId", e)
            return@withContext null
        }
    }
}
