package app.marlboroadvance.mpvex.cinetv.model

enum class LiveTab(val label: String) {
    CHANNELS("Live Channels"),
    JIO_LOGIN("Stream Manager")
}

data class ChannelVariant(
    val channelId: String,
    val language: String
)

data class LiveChannelItem(
    val defaultChannelId: String,
    val title: String,
    val category: String,
    val defaultLanguage: String,
    val logoUrl: String,
    val streamUrlHash: String,
    val variants: List<ChannelVariant> = emptyList()
) {
    fun getIdForLanguage(lang: String): String {
        return variants.find { it.language.equals(lang, ignoreCase = true) }?.channelId ?: defaultChannelId
    }
}

data class DiagnosticResult(
    val channelName: String,
    val channelId: String,
    val category: String,
    val language: String,
    val result: String,
    val failureReason: String = "",
    val httpStatus: String = "",
    val timeTakenMs: Long = 0L,
    val resolvedUrl: String = ""
)

enum class PlaybackSource { JIO_TV, M3U, MANUAL_URL }

enum class MappingStatus { WORKING, BROKEN, UNTESTED }

data class ResolvedStream(
    val url: String,
    val source: PlaybackSource,
    val headers: Map<String, String> = emptyMap(),
    val mappedName: String = ""
)

data class M3uMatchCandidate(
    val url: String,
    val mappedName: String,
    val confidence: Int,
    val headers: Map<String, String>,
    val resolution: String
)

class MultipleStreamsException(val candidates: List<M3uMatchCandidate>) : Exception("Multiple candidates found")

data class ChannelCacheEntry(
    val channelId: String,
    val normalizedName: String,
    var preferredSource: PlaybackSource,
    var status: MappingStatus = MappingStatus.UNTESTED,
    var lastSuccessfulUrl: String? = null,
    var manualStreamUrl: String? = null,
    var lastTestedTime: Long = 0,
    var failureCount: Int = 0,
    var successCount: Int = 0,
    var mappedM3uName: String? = null,
    var isManualMapping: Boolean = false,
    var failedM3uUrls: List<String> = emptyList() 
)

data class PlaylistMeta(
    val name: String,
    val channelCount: Int,
    val lastUpdated: Long
)