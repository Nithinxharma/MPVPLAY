package app.marlboroadvance.mpvex.cinetv.model

enum class LiveTab(val label: String) {
    CHANNELS("Live Channels"),
    JIO_LOGIN("Jio Authentication")
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

enum class PlaybackSource { JIO_TV, M3U }

data class ResolvedStream(
    val url: String,
    val source: PlaybackSource
)

data class ChannelCacheEntry(
    val channelId: String,
    val normalizedName: String,
    var preferredSource: PlaybackSource,
    var lastSuccessfulUrl: String? = null,
    var lastTestedTime: Long = 0,
    var failureCount: Int = 0,
    var successCount: Int = 0,
    var userFeedback: Boolean? = null,
    var mappedM3uName: String? = null,
    var confidenceScore: Int = 0,
    var isManualMapping: Boolean = false,
    var userVerified: Boolean = false
)