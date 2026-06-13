package app.marlboroadvance.mpvex.cinehub.data

import android.util.Log
import app.marlboroadvance.mpvex.cinehub.model.MovieItem
import org.w3c.dom.Document
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

object NfoScanner {

    fun scanDirectoryForMovies(directory: File): List<MovieItem> {
        val movies = mutableListOf<MovieItem>()
        if (!directory.exists() || !directory.isDirectory) return movies

        directory.listFiles()?.forEach { file ->
            if (file.isFile && isVideoFile(file)) {
                val specificNfo = File(directory, "${file.nameWithoutExtension}.nfo")
                val genericNfo = File(directory, "movie.nfo")
                val targetNfo = if (specificNfo.exists()) specificNfo else if (genericNfo.exists()) genericNfo else null

                if (targetNfo != null) {
                    parseMovieNfo(targetNfo, file)?.let { movies.add(it) }
                }
            } else if (file.isDirectory) {
                movies.addAll(scanDirectoryForMovies(file))
            }
        }
        return movies
    }

    private fun isVideoFile(file: File): Boolean {
        val extensions = setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "ts")
        return extensions.contains(file.extension.lowercase())
    }

    private fun parseMovieNfo(nfoFile: File, videoFile: File): MovieItem? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc: Document = builder.parse(nfoFile)
            doc.documentElement.normalize()

            if (doc.documentElement.nodeName != "movie") return null

            val title = doc.getElementsByTagName("title").item(0)?.textContent ?: videoFile.nameWithoutExtension
            val originalTitle = doc.getElementsByTagName("originaltitle").item(0)?.textContent ?: ""
            val userRating = doc.getElementsByTagName("userrating").item(0)?.textContent?.toDoubleOrNull() ?: 0.0
            val plot = doc.getElementsByTagName("plot").item(0)?.textContent ?: ""
            val mpaa = doc.getElementsByTagName("mpaa").item(0)?.textContent ?: ""
            val genre = doc.getElementsByTagName("genre").item(0)?.textContent ?: ""
            val director = doc.getElementsByTagName("director").item(0)?.textContent ?: ""
            val premiered = doc.getElementsByTagName("premiered").item(0)?.textContent ?: ""

            val baseName = nfoFile.nameWithoutExtension
            val parentDir = nfoFile.parentFile
            val posterFile = File(parentDir, "$baseName.jpg").takeIf { it.exists() }
                ?: File(parentDir, "$baseName.png").takeIf { it.exists() }
                ?: File(parentDir, "movie.tbn").takeIf { it.exists() }
                ?: File(parentDir, "poster.jpg").takeIf { it.exists() }

            MovieItem(
                videoFilePath = videoFile.absolutePath,
                title = title,
                originalTitle = originalTitle,
                userRating = userRating,
                plot = plot,
                mpaa = mpaa,
                genre = genre,
                director = director,
                premiered = premiered,
                posterPath = posterFile?.absolutePath
            )
        } catch (e: Exception) {
            Log.e("CineHubScanner", "Error processing XML file structural fields: ${nfoFile.name}", e)
            null
        }
    }
}
