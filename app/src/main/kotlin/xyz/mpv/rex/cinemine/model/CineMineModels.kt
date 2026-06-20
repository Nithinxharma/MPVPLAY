package xyz.mpv.rex.cinemine.model

enum class MineTab(val label: String) {
    UNIFIED("All-in-One"),
    CINEHUB_LOCAL("Local Hub"),
    CINETUBE("CineTube"),
    CINEHUB_ONLINE("Cloud Repo")
}

data class MovieItem(
    val videoFilePath: String,
    val title: String,
    val originalTitle: String = "",
    val userRating: Double = 0.0,
    val plot: String = "",
    val mpaa: String = "",
    val genre: String = "",
    val director: String = "",
    val premiered: String = "",
    val posterPath: String? = null,
    val watchProgress: Float = 0f,
    val actors: List<String> = emptyList()
) {
    fun getFormattedRating(): String = if (userRating > 0.0) String.format("%.1f", userRating) else "0.0"
}

data class TvShowItem(
    val folderPath: String,
    val title: String,
    val plot: String = "",
    val userRating: Double = 0.0,
    val genre: String = "",
    val premiered: String = "",
    val studio: String = "",
    val posterPath: String? = null,
    val watchProgress: Float = 0f,
    val actors: List<String> = emptyList()
) {
    fun getFormattedRating(): String = if (userRating > 0.0) String.format("%.1f", userRating) else "0.0"
}

data class EpisodeItem(
    val videoFilePath: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val plot: String = "",
    val userRating: Double = 0.0,
    val aired: String = "",
    val watchProgress: Float = 0f
) {
    fun getEpisodeCode(): String = String.format("S%02dE%02d", season, episode)
}

data class YoutubeVideo(
    val videoId: String,
    val title: String,
    val author: String,
    val lengthSeconds: Int,
    val videoThumbnails: List<YoutubeThumbnail> = emptyList()
) {
    fun getBestThumbnailUrl(): String = videoThumbnails.firstOrNull()?.url ?: ""
}

data class YoutubeThumbnail(
    val url: String
)
