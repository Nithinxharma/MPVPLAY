package app.marlboroadvance.mpvex.cinehub.model

import kotlinx.serialization.Serializable

@Serializable
data class ActorItem(
    val id: String = "",
    val name: String,
    val thumbUrl: String,
    val character: String = "",
    val biography: String = "",
    val birthday: String = "",
    val knownFor: String = ""
)

@Serializable
data class MediaCollection(
    val id: Int,
    val name: String,
    val posterPath: String?,
    val backdropPath: String?
)

@Serializable
data class MovieItem(
    val videoFilePath: String,
    var title: String,
    var originalTitle: String,
    var userRating: Double,
    var plot: String,
    var tagline: String = "",
    var mpaa: String,
    var genre: String,
    var director: String,
    var premiered: String,
    var runtime: Int = 0,
    var posterPath: String?,
    var backdropPath: String? = null,
    var logoPath: String? = null,
    var tmdbId: String = "",
    var imdbId: String = "",
    var collection: MediaCollection? = null,
    val watchProgress: Float = 0f,
    var actors: List<ActorItem> = emptyList(),
    var isMetadataCached: Boolean = false,
    var sourceType: String = "local", // "local" or "drive"
    var driveFileId: String? = null,
    var manualMappingId: String? = null
)

@Serializable
data class TvShowItem(
    val folderPath: String,
    var title: String,
    var plot: String,
    var userRating: Double,
    var genre: String,
    var premiered: String,
    var studio: String,
    var posterPath: String?,
    var backdropPath: String? = null,
    var logoPath: String? = null,
    var tmdbId: String = "",
    var tvdbId: String = "",
    val watchProgress: Float = 0f,
    var actors: List<ActorItem> = emptyList(),
    var isMetadataCached: Boolean = false,
    var sourceType: String = "local",
    var driveFolderId: String? = null,
    var manualMappingId: String? = null
)

@Serializable
data class EpisodeItem(
    val videoFilePath: String,
    var title: String,
    val season: Int,
    val episode: Int,
    var plot: String,
    var userRating: Double,
    var aired: String,
    var stillPath: String? = null,
    val watchProgress: Float = 0f,
    var sourceType: String = "local",
    var driveFileId: String? = null
)