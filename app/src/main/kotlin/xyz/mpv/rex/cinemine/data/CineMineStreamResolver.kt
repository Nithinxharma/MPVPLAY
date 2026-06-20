package xyz.mpv.rex.cinemine.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CineMineStreamResolver {

    /**
     * Resolves incoming custom media layout URI markers dynamically 
     * using our synchronized zero-dependency repository methods.
     */
    suspend fun resolvePlaybackUrl(videoPath: String): String = withContext(Dispatchers.IO) {
        when {
            // ================= 1. CINETUBE LIVE STREAM STREAMING NODE =================
            // If it's a direct alphanumeric videoId string from Invidious instance nodes
            !videoPath.contains(":") && videoPath.length > 5 -> {
                CineMineRepo.fetchCineTubeDirectUrl(videoPath) ?: "https://www.youtube.com/watch?v=$videoPath"
            }
            
            // ================= 2. CINEMAX (CLOUD REPO) MOVIE STREAM NODE =================
            videoPath.startsWith("cnc_stream:") -> {
                val id = videoPath.substringAfter("cnc_stream:").substringBefore(":")
                val platform = videoPath.substringAfterLast(":")
                CineMineRepo.resolveCineMaxUrl(id, platform) ?: "https://net52.cc/mobile/player.php?id=$id"
            }

            // ================= 3. CINEMAX (CLOUD REPO) TV SERIES RESOLUTION NODE =================
            videoPath.startsWith("cnc_tv:") -> {
                val id = videoPath.substringAfter("cnc_tv:").substringBefore(":")
                val platform = videoPath.substringAfterLast(":")
                // Intercepts and hits identical server decryption pools for custom series streams
                CineMineRepo.resolveCineMaxUrl(id, platform) ?: "https://net52.cc/mobile/player.php?id=$id"
            }
            
            // ================= 4. CINEHUB LOCAL FILES DIRECT PASS FALLBACK =================
            // Absolute local device storage file tracks (/sdcard/CineRex/...) pass directly
            else -> {
                videoPath
            }
        }
    }
}
