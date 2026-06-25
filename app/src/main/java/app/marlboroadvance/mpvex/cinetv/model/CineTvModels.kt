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

data class ChannelCacheEntry(
    val channelId: String,
    val mappedM3uName: String? = null,
    val mappedUrl: String? = null,
    val isManualMapping: Boolean = false,
    var preferredSource: PlaybackSource = PlaybackSource.JIO_TV,
    var status: MappingStatus = MappingStatus.UNTESTED,
    var lastSuccessTime: Long = 0,
    var lastFailureTime: Long = 0,
    var successCount: Int = 0,
    var failureCount: Int = 0
)

data class PlaylistMeta(
    val name: String,
    val channelCount: Int,
    val lastUpdated: Long
)