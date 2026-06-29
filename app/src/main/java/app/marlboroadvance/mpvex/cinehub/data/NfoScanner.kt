package app.marlboroadvance.mpvex.cinehub.data

import android.util.Log
import app.marlboroadvance.mpvex.cinehub.model.MovieItem
import app.marlboroadvance.mpvex.cinehub.model.TvShowItem
import app.marlboroadvance.mpvex.cinehub.model.EpisodeItem
import app.marlboroadvance.mpvex.cinehub.model.ActorItem
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

object NfoScanner {

    private fun isVideoFile(file: File): Boolean {
        val extensions = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "ts")
        return extensions.contains(file.extension.lowercase())
    }

    private fun isStrictMoviePath(absolutePath: String): Boolean {
        val standardizedPath = absolutePath.replace("\\", "/")
        return standardizedPath.contains("/CineRex/movies/", ignoreCase = true) || 
               standardizedPath.contains("/Cinerex/movies/", ignoreCase = true)
    }

    private fun isStrictTvShowPath(absolutePath: String): Boolean {
        val standardizedPath = absolutePath.replace("\\", "/")
        return standardizedPath.contains("/CineRex/tvshows/", ignoreCase = true) || 
               standardizedPath.contains("/Cinerex/tvshows/", ignoreCase = true)
    }

    fun scanDirectoryForMovies(directory: File): List<MovieItem> {
        val movies = mutableListOf<MovieItem>()
        if (!directory.exists() || !directory.isDirectory) return movies

        directory.listFiles()?.forEach { file ->
            if (file.isFile && isVideoFile(file)) {
                if (isStrictMoviePath(file.absolutePath)) {
                    val specificNfo = File(directory, "${file.nameWithoutExtension}.nfo")
                    val genericNfo = File(directory, "movie.nfo")
                    val targetNfo = if (specificNfo.exists()) specificNfo else if (genericNfo.exists()) genericNfo else null
                    
                    var movieParsed = false
                    if (targetNfo != null) {
                        val parsed = parseMovieNfo(targetNfo, file)
                        if (parsed != null) {
                            movies.add(parsed)
                            movieParsed = true
                        }
                    } 
                    
                    if (!movieParsed) {
                        movies.add(
                            MovieItem(
                                videoFilePath = file.absolutePath,
                                title = file.nameWithoutExtension,
                                originalTitle = "",
                                userRating = 0.0,
                                plot = "Fetching secure online metadata...",
                                mpaa = "",
                                genre = "Local Movie",
                                director = "Unknown",
                                premiered = "2026",
                                posterPath = resolveArtworkLocalFallback(file.parentFile, "poster.jpg"),
                                isMetadataCached = false
                            )
                        )
                    }
                }
            } else if (file.isDirectory) {
                movies.addAll(scanDirectoryForMovies(file))
            }
        }
        return movies
    }

    fun scanDirectoryForTvShows(directory: File): List<TvShowItem> {
        val tvShows = mutableListOf<TvShowItem>()
        if (!directory.exists() || !directory.isDirectory) return tvShows

        if (isStrictTvShowPath(directory.absolutePath)) {
            val tvShowNfo = File(directory, "tvshow.nfo")
            val parentFolderName = directory.parentFile?.name ?: ""
            val isMainShowFolder = parentFolderName.equals("tvshows", ignoreCase = true)

            if (isMainShowFolder) {
                var showParsed = false
                if (tvShowNfo.exists()) {
                    val parsed = parseTvShowNfo(tvShowNfo, directory)
                    if (parsed != null) {
                        tvShows.add(parsed)
                        showParsed = true
                    }
                } 
                
                if (!showParsed) {
                    tvShows.add(
                        TvShowItem(
                            folderPath = directory.absolutePath,
                            title = directory.name,
                            plot = "Fetching secure online metadata...",
                            userRating = 0.0,
                            genre = "Local Series",
                            premiered = "2026",
                            studio = "Unknown",
                            posterPath = resolveArtworkLocalFallback(directory, "poster.jpg"),
                            isMetadataCached = false
                        )
                    )
                }
            }
        }

        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                tvShows.addAll(scanDirectoryForTvShows(file))
            }
        }
        return tvShows
    }

    fun scanTvShowEpisodes(showFolder: File): List<EpisodeItem> {
        val episodes = mutableListOf<EpisodeItem>()
        if (!showFolder.exists() || !showFolder.isDirectory) return episodes

        showFolder.listFiles()?.forEach { file ->
            if (file.isFile && isVideoFile(file)) {
                val nfoFile = File(showFolder, "${file.nameWithoutExtension}.nfo")
                var parsedEpisode: EpisodeItem? = null
                
                if (nfoFile.exists()) {
                    parsedEpisode = parseEpisodeNfo(nfoFile, file)
                }
                
                if (parsedEpisode != null) {
                    episodes.add(parsedEpisode)
                } else {
                    // Critical Fallback: Prevents invisible episodes if .nfo is purely ASCII text.
                    val cleanedTitle = file.nameWithoutExtension
                        .replace(Regex("(?i)\\b(1080p|720p|480p|x264|x265|hevc|10bit|dual|audio|hindi|english|korean|msubs|esubs|moviesmod|org|army)\\b.*"), "")
                        .replace(Regex("[\\.\\-_]"), " ").trim()

                    episodes.add(
                        EpisodeItem(
                            videoFilePath = file.absolutePath,
                            title = cleanedTitle,
                            season = extractSeasonNumber(showFolder.name),
                            episode = extractEpisodeNumber(file.name),
                            plot = "Local Media File.",
                            userRating = 0.0,
                            aired = "2026"
                        )
                    )
                }
            } else if (file.isDirectory) {
                episodes.addAll(scanTvShowEpisodes(file))
            }
        }
        return episodes
    }

    private fun extractSeasonNumber(folderName: String): Int {
        val seasonRegex = Regex("(?i)season\\s*(\\d+)|s(\\d+)")
        val match = seasonRegex.find(folderName)
        return match?.groupValues?.find { it.isNotBlank() && it.toIntOrNull() != null }?.toIntOrNull() ?: 1
    }

    private fun extractEpisodeNumber(fileName: String): Int {
        val epRegex = Regex("(?i)s\\d+e(\\d+)|e(\\d+)")
        val match = epRegex.find(fileName)
        return match?.groupValues?.find { it.isNotBlank() && it.toIntOrNull() != null }?.toIntOrNull() ?: 1
    }

    private fun parseMovieNfo(nfoFile: File, videoFile: File): MovieItem? {
        return try {
            val doc = getXmlDocument(nfoFile) ?: return null
            if (doc.documentElement.nodeName != "movie") return null

            val root = doc.documentElement
            val title = getTagText(root, "title").ifBlank { videoFile.nameWithoutExtension }
            
            MovieItem(
                videoFilePath = videoFile.absolutePath,
                title = title,
                originalTitle = getTagText(root, "originaltitle"),
                userRating = getTagText(root, "userrating").toDoubleOrNull() ?: 0.0,
                plot = getTagText(root, "plot").ifBlank { "No description available." },
                mpaa = getTagText(root, "mpaa"),
                genre = getTagText(root, "genre").ifBlank { "Local Movie" },
                director = getTagText(root, "director").ifBlank { "Unknown" },
                premiered = getTagText(root, "premiered").ifBlank { getTagText(root, "year").ifBlank { "2026" } },
                tmdbId = getTagText(root, "tmdbid"),
                imdbId = getTagText(root, "imdbid"),
                posterPath = resolvePosterWithFallback(nfoFile, root),
                actors = parseActorsFromNfo(doc),
                isMetadataCached = false
            )
        } catch (e: Exception) {
            Log.e("CineHubScanner", "Error processing movie XML: ${nfoFile.name}", e)
            null
        }
    }

    private fun parseTvShowNfo(nfoFile: File, folder: File): TvShowItem? {
        return try {
            val doc = getXmlDocument(nfoFile) ?: return null
            if (doc.documentElement.nodeName != "tvshow") return null

            val root = doc.documentElement
            val title = getTagText(root, "title").ifBlank { folder.name }
            
            TvShowItem(
                folderPath = folder.absolutePath,
                title = title,
                plot = getTagText(root, "plot").ifBlank { "No description available." },
                userRating = getTagText(root, "userrating").toDoubleOrNull() ?: 0.0,
                genre = getTagText(root, "genre").ifBlank { "Local Series" },
                premiered = getTagText(root, "premiered").ifBlank { getTagText(root, "year").ifBlank { "2026" } },
                studio = getTagText(root, "studio").ifBlank { "Unknown" },
                tmdbId = getTagText(root, "tmdbid"),
                tvdbId = getTagText(root, "tvdbid"),
                posterPath = resolvePosterWithFallback(nfoFile, root),
                actors = parseActorsFromNfo(doc),
                isMetadataCached = false
            )
        } catch (e: Exception) {
            Log.e("CineHubScanner", "Error processing tvshow XML: ${nfoFile.name}", e)
            null
        }
    }

    private fun parseEpisodeNfo(nfoFile: File, videoFile: File): EpisodeItem? {
        return try {
            val doc = getXmlDocument(nfoFile) ?: return null
            if (doc.documentElement.nodeName != "episodedetails") return null

            val root = doc.documentElement
            EpisodeItem(
                videoFilePath = videoFile.absolutePath,
                title = getTagText(root, "title").ifBlank { videoFile.nameWithoutExtension },
                season = getTagText(root, "season").toIntOrNull() ?: 1,
                episode = getTagText(root, "episode").toIntOrNull() ?: 1,
                plot = getTagText(root, "plot"),
                userRating = getTagText(root, "userrating").toDoubleOrNull() ?: 0.0,
                aired = getTagText(root, "aired")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun getXmlDocument(file: File): Document? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(file)
            doc.documentElement.normalize()
            doc
        } catch (e: Exception) { null }
    }

    fun getTagText(element: Element, tagName: String): String {
        val nodeList = element.getElementsByTagName(tagName)
        if (nodeList.length > 0) {
            return nodeList.item(0)?.textContent?.trim() ?: ""
        }
        return ""
    }

    private fun resolveArtworkLocalFallback(parentDir: File?, targetName: String): String? {
        if (parentDir == null) return null
        return File(parentDir, targetName).takeIf { it.exists() }?.absolutePath
    }

    private fun resolvePosterWithFallback(nfoFile: File, rootElement: Element): String? {
        val baseName = nfoFile.nameWithoutExtension
        val parentDir = nfoFile.parentFile

        val localCheck = resolveArtworkLocalFallback(parentDir, "$baseName.jpg")
            ?: resolveArtworkLocalFallback(parentDir, "poster.jpg")
            ?: resolveArtworkLocalFallback(parentDir, "folder.jpg")
            
        if (localCheck != null) return localCheck

        val thumbList = rootElement.getElementsByTagName("thumb")
        for (i in 0 until thumbList.length) {
            val thumbNode = thumbList.item(i)
            if (thumbNode != null && thumbNode.nodeType == Node.ELEMENT_NODE) {
                val thumbElement = thumbNode as Element
                val aspect = thumbElement.getAttribute("aspect")
                if (aspect == "poster" || aspect.isBlank()) {
                    val url = thumbElement.textContent?.trim() ?: ""
                    if (url.startsWith("http")) return url
                }
            }
        }
        return null
    }

    fun parseActorsFromNfo(doc: Document): List<ActorItem> {
        val actorsList = mutableListOf<ActorItem>()
        try {
            val actorNodes = doc.getElementsByTagName("actor")
            for (i in 0 until actorNodes.length) {
                val node = actorNodes.item(i)
                if (node != null && node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    val name = getTagText(element, "name")
                    val thumb = getTagText(element, "thumb")
                    val role = getTagText(element, "role")
                    if (name.isNotBlank()) {
                        actorsList.add(ActorItem(name = name, thumbUrl = thumb, character = role))
                    }
                }
            }
        } catch (_: Exception) {}
        return actorsList
    }

    fun getSharedFilmography(actorName: String, movies: List<MovieItem>, shows: List<TvShowItem>): Pair<List<MovieItem>, List<TvShowItem>> {
        val matchMovies = movies.filter { movie -> movie.actors.any { it.name.equals(actorName, ignoreCase = true) } }
        val matchShows = shows.filter { show -> show.actors.any { it.name.equals(actorName, ignoreCase = true) } }
        return Pair(matchMovies, matchShows)
    }
}