package app.marlboroadvance.mpvex.cinehub.data

import app.marlboroadvance.mpvex.cinehub.model.MovieItem
import app.marlboroadvance.mpvex.cinehub.model.TvShowItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.Base64

object CineCloudRepoClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val domainsPool = listOf("https://net52.cc", "https://net11.cc", "https://hianime.lol")
    
    private val newTvDomains = listOf(
        "aHR0cHM6Ly9tb2JpbGVkZXRlY3RzLmNvbQ==",
        "aHR0cHM6Ly9tb2JpbGVkZXRlY3QuYXBw",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmFydA==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmNj",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmNsaWNr",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0Lmluaw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LmxpdmU=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnBybw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNob3A=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNpdGU=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnNwYWNl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnN0b3Jl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0LnZpcA==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0Lndpa2k=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0Lnh5eg==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5hcnQ=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5jYw==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pbmZv",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5pbms=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5saXZl",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5wcm8=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy5zdG9yZQ==",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy50b3A=",
        "aHR0cHM6Ly9tb2JpZGV0ZWN0cy54eXo="
    )

    @Volatile private var workingDomain: String = "https://net52.cc"
    @Volatile private var activeSessionCookie: String = ""
    @Volatile private var lastBypassTime: Long = 0L
    @Volatile private var resolvedApiUrl: String = ""

    private val standardHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 13; Pixel 5 Build/TQ3A.230901.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/144.0.7559.132 Safari/537.36 /OS.Gatu v3.0",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "Accept-Language" to "en-IN,en-US;q=0.9,en;q=0.8",
        "X-Requested-With" to "XMLHttpRequest"
    )

    private fun decodeBase64(value: String): String {
        return try {
            String(Base64.getDecoder().decode(value))
        } catch (e: Exception) { "" }
    }

    private suspend fun fetchLiveApiUrl(): String = withContext(Dispatchers.IO) {
        if (resolvedApiUrl.isNotBlank()) return@withContext resolvedApiUrl
        
        val verificationHeaders = mapOf(
            "Cache-Control" to "no-cache, no-store, must-revalidate",
            "Pragma" to "no-cache",
            "Expires" to "0",
            "X-Requested-With" to "NetmirrorNewTV v1.0",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0",
            "Accept" to "application/json, text/plain, */*"
        )

        for (encodedNode in newTvDomains) {
            val decodedBase = decodeBase64(encodedNode).trimEnd('/')
            if (decodedBase.isBlank()) continue
            try {
                val reqBuilder = Request.Builder().url("$decodedBase/checknewtv.php")
                for (headerEntry in verificationHeaders) {
                    reqBuilder.addHeader(headerEntry.key, headerEntry.value)
                }
                
                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val payload = response.body?.string() ?: ""
                        if (payload.contains("\"token_hash\":\"")) {
                            val extractedHash = payload.substringAfter("\"token_hash\":\"").substringBefore("\"")
                            if (extractedHash.isNotBlank()) {
                                resolvedApiUrl = decodeBase64(extractedHash).trimEnd('/')
                                return@withContext resolvedApiUrl
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return@withContext "https://mobiledetects.com"
    }

    private suspend fun findWorkingDomain() {
        for (domain in domainsPool) {
            try {
                val request = Request.Builder().url("$domain/mobile/home?app=1").get().build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful || response.code == 302) {
                        workingDomain = domain
                        return
                    }
                }
            } catch (e: Exception) { /* Continue scanning pool */ }
        }
    }

    private suspend fun ensureValidSession() = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        if (activeSessionCookie.isNotEmpty() && (currentTime - lastBypassTime < 54_000_000)) {
            return@withContext
        }

        findWorkingDomain()

        try {
            val bypassHeaders = mapOf(
                "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
                "Accept-Encoding" to "gzip, deflate, br, zstd",
                "Accept-Language" to "en-US,en;q=0.9",
                "Cache-Control" to "max-age=0",
                "Connection" to "keep-alive",
                "Content-Type" to "application/x-www-form-urlencoded",
                "Origin" to "https://net22.cc",
                "Referer" to "https://net22.cc/verify2",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
            )

            val formBody = FormBody.Builder()
                .add("g-recaptcha-response", UUID.randomUUID().toString())
                .build()

            val requestBuilder = Request.Builder().url("$workingDomain/verify.php").post(formBody)
            for (headerEntry in bypassHeaders) {
                requestBuilder.addHeader(headerEntry.key, headerEntry.value)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val cookiesList = response.headers("Set-Cookie")
                val targetCookie = cookiesList.firstOrNull { it.startsWith("t_hash_t=") }
                if (targetCookie != null) {
                    activeSessionCookie = targetCookie.substringAfter("t_hash_t=").substringBefore(";")
                    lastBypassTime = currentTime
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CineCloudRepo", "Bypass failure: " + e.message)
        }
    }

    private fun parseHtmlToItems(html: String, targetPlatform: String): List<Any> {
        val extractedItems = mutableListOf<Any>()
        val containerRegex = Regex("data-post=\"(\\d+)\"[^>]*>.*?<span[^>]*>([^<]+)</span>")
        var matches = containerRegex.findAll(html).toList()
        
        if (matches.isEmpty()) {
            val looseCardRegex = Regex("data-post=\"(\\d+)\".*?class=\"card-title\"[^>]*>([^<]+)")
            matches = looseCardRegex.findAll(html).toList()
        }

        matches.forEach { match ->
            val id = match.groupValues[1]
            val title = match.groupValues[2].trim()
            
            if (id.isNotBlank() && title.isNotBlank() && !title.equals("Later", ignoreCase = true)) {
                if (targetPlatform == "hs" || targetPlatform == "dp") {
                    extractedItems.add(
                        TvShowItem(
                            folderPath = "cnc_tv:$id:$targetPlatform",
                            title = title,
                            plot = "Premium cloud streaming tracking metrics active. Direct m3u8 player synchronization ready.",
                            userRating = 8.5,
                            genre = if (targetPlatform == "hs") "Hotstar Series" else "Disney+ Originals",
                            premiered = "2026",
                            studio = if (targetPlatform == "hs") "Hotstar Mirror" else "Disney+ Studio",
                            posterPath = "https://imgcdn.kim/hs/v/$id.jpg"
                        )
                    )
                } else {
                    extractedItems.add(
                        MovieItem(
                            videoFilePath = "cnc_stream:$id:$targetPlatform",
                            title = title,
                            originalTitle = if (targetPlatform == "nf") "Netflix" else "Prime Video",
                            userRating = 8.3,
                            plot = "Premium cloud progressive stream block active. Ready for native MPV engine hardware render loops.",
                            mpaa = "UA",
                            genre = if (targetPlatform == "nf") "Netflix Release" else "Prime Video Blockbuster",
                            director = "CNCVerse",
                            premiered = "2026",
                            posterPath = if (targetPlatform == "nf") "https://imgcdn.kim/poster/v/$id.jpg" else "https://imgcdn.kim/pv/v/$id.jpg"
                        )
                    )
                }
            }
        }
        return extractedItems
    }

    private suspend fun fetchPlatformRawHtml(ottCode: String): String {
        ensureValidSession()
        val requestBuilder = Request.Builder().url("$workingDomain/mobile/home?app=1")
        for (headerEntry in standardHeaders) {
            requestBuilder.addHeader(headerEntry.key, headerEntry.value)
        }
        requestBuilder.addHeader("Cookie", "t_hash_t=$activeSessionCookie; hd=on; ott=$ottCode")
        
        return try {
            client.newCall(requestBuilder.build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() ?: "" else ""
            }
        } catch (e: Exception) { "" }
    }

    // --- STREAM AGGREGATION SYSTEM ---
    suspend fun fetchOnlineMovies(): List<MovieItem> = withContext(Dispatchers.IO) {
        val aggregatedMovies = mutableListOf<MovieItem>()
        
        // Fetch both Netflix (nf) and Prime Video (pv) paths dynamically
        val netflixHtml = fetchPlatformRawHtml("nf")
        val primeHtml = fetchPlatformRawHtml("pv")

        @Suppress("UNCHECKED_CAST")
        aggregatedMovies.addAll(parseHtmlToItems(netflixHtml, "nf") as List<MovieItem>)
        @Suppress("UNCHECKED_CAST")
        aggregatedMovies.addAll(parseHtmlToItems(primeHtml, "pv") as List<MovieItem>)

        return@withContext aggregatedMovies.distinctBy { it.videoFilePath }.take(24)
    }

    suspend fun fetchOnlineTvShows(): List<TvShowItem> = withContext(Dispatchers.IO) {
        val aggregatedTv = mutableListOf<TvShowItem>()

        // Fetch both Hotstar (hs) and Disney+ (dp) paths dynamically
        val hotstarHtml = fetchPlatformRawHtml("hs")
        val disneyHtml = fetchPlatformRawHtml("dp")

        @Suppress("UNCHECKED_CAST")
        aggregatedTv.addAll(parseHtmlToItems(hotstarHtml, "hs") as List<TvShowItem>)
        @Suppress("UNCHECKED_CAST")
        aggregatedTv.addAll(parseHtmlToItems(disneyHtml, "dp") as List<TvShowItem>)

        return@withContext aggregatedTv.distinctBy { it.folderPath }.take(24)
    }

    suspend fun resolveDirectStreamUrl(postId: String, platformCode: String): String? = withContext(Dispatchers.IO) {
        val activeResolverNode = fetchLiveApiUrl()
        val playerUrl = "$activeResolverNode/newtv/player.php?id=$postId"
        
        val request = Request.Builder()
            .url(playerUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0 /OS.GatuNewTV v1.0")
            .addHeader("X-Requested-With", "NetmirrorNewTV v1.0")
            .addHeader("Ott", platformCode)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.contains("\"video_link\":\"")) {
                        val decryptedPath = body.substringAfter("\"video_link\":\"").substringBefore("\"")
                        return@withContext decryptedPath.replace("\\/", "/")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CineCloudRepo", "Decryption logs crash: " + e.message)
        }
        return@withContext null
    }
}
