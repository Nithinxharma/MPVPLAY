package app.marlboroadvance.mpvex.youtube.model

import kotlinx.serialization.Serializable

@Serializable
data class YoutubeVideo(
    val videoId: String,
    val title: String,
    val description: String = "",
    val viewCount: Long = 0,
    val publishedText: String = "",
    val lengthSeconds: Int = 0,
    val author: String = "",
    val authorId: String = "", 
    val authorUrl: String = "",
    val isLiveNow: Boolean = false,
    val premium: Boolean = false,
    val videoThumbnails: List<YoutubeThumbnail> = emptyList(),
    val authorThumbnails: List<YoutubeThumbnail> = emptyList(),
    val subCountText: String = ""
) {
    fun getBestThumbnailUrl(): String {
        return videoThumbnails.maxByOrNull { it.width }?.url 
            ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    }

    fun getBestAuthorThumbnailUrl(): String? {
        return authorThumbnails.maxByOrNull { it.width }?.url
    }

    fun formatViewCount(): String {
        return when {
            viewCount >= 1_000_000_000 -> String.format("%.1fB views", viewCount / 1_000_000_000.0)
            viewCount >= 1_000_000 -> String.format("%.1fM views", viewCount / 1_000_000.0)
            viewCount >= 1_000 -> String.format("%.1fK views", viewCount / 1_000.0)
            else -> "$viewCount views"
        }
    }
}

@Serializable
data class YoutubeThumbnail(
    val quality: String = "",
    val url: String,
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
data class YoutubeFormat(
    val url: String,
    val container: String = "",
    val qualityLabel: String = "",
    val type: String = "",
    val bitrate: Long = 0,
    val fps: Int = 0
)

@Serializable
data class VideoDataResponse(
    val formatStreams: List<YoutubeFormat> = emptyList(),
    val adaptiveFormats: List<YoutubeFormat> = emptyList(),
    val description: String = "",
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val subCountText: String = "",
    val author: String = "",
    val authorThumbnails: List<YoutubeThumbnail> = emptyList()
)