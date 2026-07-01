package app.marlboroadvance.mpvex.cinehub.data

import android.content.Context
import app.marlboroadvance.mpvex.cinehub.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@kotlinx.serialization.Serializable
data class TMDBMovieNode(
    val id: Int = 0,
    val title: String? = null, 
    val overview: String? = null, 
    val poster_path: String? = null, 
    val backdrop_path: String? = null,
    val vote_average: Double = 0.0, 
    val release_date: String? = null
)

@kotlinx.serialization.Serializable
data class TMDBTvNode(
    val id: Int = 0,
    val name: String? = null, 
    val overview: String? = null, 
    val poster_path: String? = null, 
    val backdrop_path: String? = null,
    val vote_average: Double = 0.0, 
    val first_air_date: String? = null
)

@kotlinx.serialization.Serializable
data class TMDBImage(val file_path: String, val iso_639_1: String? = null, val vote_average: Double = 0.0)

@kotlinx.serialization.Serializable
data class TMDBImagesResponse(val backdrops: List<TMDBImage> = emptyList(), val logos: List<TMDBImage> = emptyList(), val posters: List<TMDBImage> = emptyList())

@kotlinx.serialization.Serializable
data class TMDBCreditsResponse(val cast: List<TMDBCastNode> = emptyList(), val crew: List<TMDBCrewNode> = emptyList())

@kotlinx.serialization.Serializable
data class TMDBCastNode(val id: Int, val name: String, val character: String? = null, val profile_path: String? = null)

@kotlinx.serialization.Serializable
data class TMDBCrewNode(val id: Int, val name: String, val job: String? = null)

@kotlinx.serialization.Serializable
data class TMDBMovieDetails(
    val id: Int,
    val title: String,
    val overview: String?,
    val tagline: String?,
    val runtime: Int?,
    val release_date: String?,
    val vote_average: Double,
    val poster_path: String?,
    val backdrop_path: String?,
    val belongs_to_collection: TMDBCollectionNode?,
    val imdb_id: String?,
    val genres: List<TMDBGenre> = emptyList(),
    val credits: TMDBCreditsResponse? = null,
    val images: TMDBImagesResponse? = null
)

@kotlinx.serialization.Serializable
data class TMDBGenre(val id: Int, val name: String)

@kotlinx.serialization.Serializable
data class TMDBCollectionNode(val id: Int, val name: String, val poster_path: String?, val backdrop_path: String?)

@kotlinx.serialization.Serializable
data class TMDBPersonDetails(
    val id: Int,
    val name: String,
    val biography: String?,
    val birthday: String?,
    val profile_path: String?,
    val known_for_department: String?
)

@kotlinx.serialization.Serializable
data class TMDBMovieSearchWrapper(val results: List<TMDBMovieNode>)

@kotlinx.serialization.Serializable
data class TMDBTvSearchWrapper(val results: List<TMDBTvNode>)

@kotlinx.serialization.Serializable
data class TVMazeShowNode(
    val id: Int = 0,
    val name: String? = null,
    val summary: String? = null,
    val premiered: String? = null,
    val rating: TVMazeRating? = null,
    val image: TVMazeImage? = null,
    val externals: TVMazeExternals? = null
)

@kotlinx.serialization.Serializable
data class TVMazeExternals(val thetvdb: Int? = null, val imdb: String? = null)

@kotlinx.serialization.Serializable
data class TVMazeRating(val average: Double? = null)

@kotlinx.serialization.Serializable
data class TVMazeImage(val original: String? = null, val medium: String? = null)

@kotlinx.serialization.Serializable
data class TVMazeSearchWrapper(val show: TVMazeShowNode? = null)

data class OnlineMediaMetadata(
    val title: String, 
    val plot: String, 
    val rating: Double, 
    val posterPath: String?, 
    val premiered: String
)

object ManualMappingManager {
    private val jsonParser = Json { ignoreUnknownKeys = true }
    private const val MAPPING_FILE = "manual_mappings.json"

    private fun getMappingFile(context: Context): File {
        val dir = File(context.filesDir, "cinehub_config")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MAPPING_FILE)
    }

    fun saveMapping(context: Context, filenameOrId: String, tmdbId: String) {
        val file = getMappingFile(context)
        val currentMap = loadAllMappings(context).toMutableMap()
        currentMap[filenameOrId] = tmdbId
        file.writeText(jsonParser.encodeToString(currentMap))
    }

    fun getMapping(context: Context, filenameOrId: String): String? {
        return loadAllMappings(context)[filenameOrId]
    }

    private fun loadAllMappings(context: Context): Map<String, String> {
        val file = getMappingFile(context)
        if (!file.exists()) return emptyMap()
        return try {
            jsonParser.decodeFromString<Map<String, String>>(file.readText())
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

object MetadataCacheManager {
    val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    fun getCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "cinehub_metadata")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    inline fun <reified T> saveToCache(context: Context, id: String, data: T) {
        try {
            val file = File(getCacheDir(context), "${id.hashCode()}.json")
            file.writeText(jsonParser.encodeToString(data))
        } catch (e: Exception) {
            android.util.Log.e("MetadataCache", "Failed to write cache for $id", e)
        }
    }

    inline fun <reified T> loadFromCache(context: Context, id: String): T? {
        try {
            val file = File(getCacheDir(context), "${id.hashCode()}.json")
            if (file.exists()) {
                return jsonParser.decodeFromString<T>(file.readText())
            }
        } catch (e: Exception) {
            android.util.Log.e("MetadataCache", "Failed to read cache for $id", e)
        }
        return null
    }

    fun clearCache(context: Context) {
        getCacheDir(context).deleteRecursively()
    }
}

object CineOnlineScraper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
    private const val API_KEY = "38a73d59546aa8789c007d3dbd96cdbc"
    const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/original"
    const val THUMB_BASE_URL = "https://image.tmdb.org/t/p/w500"
    private const val TVMAZE_BASE_URL = "https://api.tvmaze.com"

    fun cleanMediaFileName(fileName: String): Pair<String, String?> {
        var cleanName = fileName.replace(Regex("(?i)\\.(mp4|mkv|avi|mov|webm|flv|ts)\$"), "")

        // Extended deep regex cleanup
        cleanName = cleanName.replace(Regex("(?i)\\b(1080p|2160p|480p|720p|4k|bluray|web-dl|webrip|hdrip|hevc|x264|x265|aac|dts|remux|extended|director's cut|dual|audio|hindi|english|korean|msubs|esubs|moviesmod|org|army|episode\\s*\\d+|season\\s*\\d+|s\\d+e\\d+)\\b.*"), "")

        cleanName = cleanName.replace(Regex("[\\.\\-_]"), " ")

        val yearRegex = Regex("\\b(19|20)\\d{2}\\b")
        val match = yearRegex.find(cleanName)
        val year = match?.value

        if (match != null) {
            cleanName = cleanName.substring(0, match.range.first)
        }
        
        cleanName = cleanName.replace(Regex("[\\[\\]\\(\\)]"), " ").replace(Regex("\\s+"), " ").trim()

        return Pair(cleanName, year)
    }

    suspend fun executeManualMovieSearch(query: String): List<TMDBMovieNode> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$TMDB_BASE_URL/search/movie?api_key=$API_KEY&query=$encodedQuery&language=en-US"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use emptyList()
                    val parsed = jsonParser.decodeFromString<TMDBMovieSearchWrapper>(body)
                    return@withContext parsed.results
                }
            }
        } catch (e: Exception) {}
        return@withContext emptyList()
    }

    suspend fun executeManualTvSearch(query: String): List<TMDBTvNode> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$TMDB_BASE_URL/search/tv?api_key=$API_KEY&query=$encodedQuery&language=en-US"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return@use emptyList()
                    val parsed = jsonParser.decodeFromString<TMDBTvSearchWrapper>(body)
                    return@withContext parsed.results
                }
            }
        } catch (e: Exception) {}
        return@withContext emptyList()
    }

    fun searchOnlineMovieMetadata(fileName: String): OnlineMediaMetadata? {
        return runBlocking {
            val movie = getOrFetchMovie(null, fileName, null, false)
            if (movie != null) {
                OnlineMediaMetadata(
                    title = movie.title,
                    plot = movie.plot,
                    rating = movie.userRating,
                    posterPath = movie.posterPath,
                    premiered = movie.premiered
                )
            } else null
        }
    }

    fun searchOnlineTvMetadata(folderName: String): OnlineMediaMetadata? {
        return runBlocking {
            val tv = getOrFetchTvShow(null, folderName, null, false)
            if (tv != null) {
                OnlineMediaMetadata(
                    title = tv.title,
                    plot = tv.plot,
                    rating = tv.userRating,
                    posterPath = tv.posterPath,
                    premiered = tv.premiered
                )
            } else null
        }
    }

    suspend fun getOrFetchMovie(context: Context?, fileName: String, fallbackTmdbId: String? = null, forceRefresh: Boolean = false): MovieItem? = withContext(Dispatchers.IO) {
        var tmdbId = fallbackTmdbId
        
        // 1. Check Permanent Manual Mapping
        if (context != null && tmdbId == null) {
            val mappedId = ManualMappingManager.getMapping(context, fileName)
            if (mappedId != null) tmdbId = mappedId
        }

        val cacheId = tmdbId ?: fileName
        
        // 2. Check JSON Cache
        if (!forceRefresh && context != null) {
            val cached = MetadataCacheManager.loadFromCache<MovieItem>(context, "movie_$cacheId")
            if (cached != null) return@withContext cached
        }

        try {
            val (cleanTitle, year) = cleanMediaFileName(fileName)

            if (tmdbId.isNullOrBlank()) {
                val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
                var searchUrl = "$TMDB_BASE_URL/search/movie?api_key=$API_KEY&query=$encodedTitle&language=en-US"
                if (year != null) searchUrl += "&primary_release_year=$year"

                val searchReq = Request.Builder().url(searchUrl).build()
                client.newCall(searchReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: return@use
                        val parsed = jsonParser.decodeFromString<TMDBMovieSearchWrapper>(body)
                        tmdbId = parsed.results.firstOrNull()?.id?.toString()
                    }
                }
            }

            if (!tmdbId.isNullOrBlank()) {
                val detailUrl = "$TMDB_BASE_URL/movie/$tmdbId?api_key=$API_KEY&append_to_response=credits,images&include_image_language=en,null"
                val detailReq = Request.Builder().url(detailUrl).build()
                
                client.newCall(detailReq).execute().use { res ->
                    if (res.isSuccessful) {
                        val body = res.body?.string() ?: return@use
                        val details = jsonParser.decodeFromString<TMDBMovieDetails>(body)
                        
                        val logoPath = details.images?.logos?.maxByOrNull { it.vote_average }?.file_path?.let { "$IMAGE_BASE_URL$it" }
                        val director = details.credits?.crew?.firstOrNull { it.job == "Director" }?.name ?: "Unknown"
                        
                        val actors = details.credits?.cast?.take(15)?.map { cast ->
                            ActorItem(
                                id = cast.id.toString(),
                                name = cast.name,
                                character = cast.character ?: "",
                                thumbUrl = cast.profile_path?.let { "$THUMB_BASE_URL$it" } ?: "https://ui-avatars.com/api/?name=${cast.name}&background=random"
                            )
                        } ?: emptyList()

                        val collection = details.belongs_to_collection?.let {
                            MediaCollection(
                                id = it.id,
                                name = it.name,
                                posterPath = it.poster_path?.let { p -> "$IMAGE_BASE_URL$p" },
                                backdropPath = it.backdrop_path?.let { b -> "$IMAGE_BASE_URL$b" }
                            )
                        }

                        val movieItem = MovieItem(
                            videoFilePath = "",
                            title = details.title,
                            originalTitle = details.title,
                            userRating = details.vote_average,
                            plot = details.overview ?: "No description available.",
                            tagline = details.tagline ?: "",
                            mpaa = "",
                            genre = details.genres.joinToString(", ") { it.name },
                            director = director,
                            premiered = details.release_date ?: "2026",
                            runtime = details.runtime ?: 0,
                            posterPath = details.poster_path?.let { "$IMAGE_BASE_URL$it" },
                            backdropPath = details.backdrop_path?.let { "$IMAGE_BASE_URL$it" },
                            logoPath = logoPath,
                            tmdbId = tmdbId.toString(),
                            imdbId = details.imdb_id ?: "",
                            collection = collection,
                            actors = actors,
                            isMetadataCached = true
                        )
                        
                        if (context != null) {
                            MetadataCacheManager.saveToCache(context, "movie_$cacheId", movieItem)
                        }
                        return@withContext movieItem
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CineOnlineScraper", "Online movie scan failed", e)
        }
        return@withContext null
    }

    suspend fun getOrFetchTvShow(context: Context?, folderName: String, fallbackTmdbId: String? = null, forceRefresh: Boolean = false): TvShowItem? = withContext(Dispatchers.IO) {
        var tmdbId = fallbackTmdbId
        
        if (context != null && tmdbId == null) {
            val mappedId = ManualMappingManager.getMapping(context, folderName)
            if (mappedId != null) tmdbId = mappedId
        }

        val cacheId = tmdbId ?: folderName
        
        if (!forceRefresh && context != null) {
            val cached = MetadataCacheManager.loadFromCache<TvShowItem>(context, "tv_$cacheId")
            if (cached != null) return@withContext cached
        }

        try {
            val (cleanTitle, year) = cleanMediaFileName(folderName)
            val encodedTitle = URLEncoder.encode(cleanTitle, "UTF-8")
            
            var searchUrl = "$TMDB_BASE_URL/search/tv?api_key=$API_KEY&query=$encodedTitle&language=en-US"
            if (year != null) searchUrl += "&first_air_date_year=$year"

            val searchReq = Request.Builder().url(searchUrl).build()
            client.newCall(searchReq).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: return@use
                    val parsed = jsonParser.decodeFromString<TMDBTvSearchWrapper>(body)
                    
                    val result = if (tmdbId != null) parsed.results.find { it.id.toString() == tmdbId } ?: parsed.results.firstOrNull() else parsed.results.firstOrNull()
                    
                    if (result != null) {
                        val tvShow = TvShowItem(
                            folderPath = "",
                            title = result.name ?: cleanTitle,
                            plot = result.overview ?: "No description.",
                            userRating = result.vote_average,
                            genre = "Series",
                            premiered = result.first_air_date ?: "2026",
                            studio = "Unknown",
                            posterPath = result.poster_path?.let { "$IMAGE_BASE_URL$it" },
                            backdropPath = result.backdrop_path?.let { "$IMAGE_BASE_URL$it" },
                            tmdbId = result.id.toString(),
                            isMetadataCached = true
                        )
                        if (context != null) MetadataCacheManager.saveToCache(context, "tv_$cacheId", tvShow)
                        return@withContext tvShow
                    }
                }
            }
            
            val tvMazeUrl = "$TVMAZE_BASE_URL/search/shows?q=$encodedTitle"
            val reqMaze = Request.Builder().url(tvMazeUrl).build()
            client.newCall(reqMaze).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val array = jsonParser.decodeFromString<List<TVMazeSearchWrapper>>(body)
                    val node = array.firstOrNull()?.show
                    if (node != null) {
                        val cleanPlot = node.summary?.replace(Regex("<[^>]*>"), "") ?: "No description."
                        val tvShow = TvShowItem(
                            folderPath = "",
                            title = node.name ?: cleanTitle,
                            plot = cleanPlot,
                            userRating = node.rating?.average ?: 0.0,
                            genre = "Series",
                            premiered = node.premiered ?: "2026",
                            studio = "Network",
                            posterPath = node.image?.original ?: node.image?.medium,
                            tvdbId = node.externals?.thetvdb?.toString() ?: "",
                            isMetadataCached = true
                        )
                        if (context != null) MetadataCacheManager.saveToCache(context, "tv_$cacheId", tvShow)
                        return@withContext tvShow
                    }
                }
            }
        } catch (e: Exception) {}
        return@withContext null
    }

    suspend fun fetchArtworkOptions(tmdbId: String, type: String = "movie"): TMDBImagesResponse? = withContext(Dispatchers.IO) {
        try {
            val url = "$TMDB_BASE_URL/$type/$tmdbId/images?api_key=$API_KEY&include_image_language=en,null"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: return@use null
                    return@withContext jsonParser.decodeFromString<TMDBImagesResponse>(body)
                }
            }
        } catch (e: Exception) {}
        return@withContext null
    }

    suspend fun fetchActorDetails(context: Context, personId: String): TMDBPersonDetails? = withContext(Dispatchers.IO) {
        val cached = MetadataCacheManager.loadFromCache<TMDBPersonDetails>(context, "actor_$personId")
        if (cached != null) return@withContext cached

        try {
            val url = "$TMDB_BASE_URL/person/$personId?api_key=$API_KEY"
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: return@use null
                    val details = jsonParser.decodeFromString<TMDBPersonDetails>(body)
                    MetadataCacheManager.saveToCache(context, "actor_$personId", details)
                    return@withContext details
                }
            }
        } catch (e: Exception) {}
        return@withContext null
    }
}