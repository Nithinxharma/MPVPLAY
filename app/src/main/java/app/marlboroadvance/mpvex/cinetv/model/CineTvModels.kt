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
        return variants.find { it.language == lang }?.channelId ?: defaultChannelId
    }
}

data class EpgData(
    val programName: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val nextProgramName: String,
    val nextStartTimeMs: Long
)

data class DiagnosticResult(
    val channelName: String,
    val channelId: String,
    val category: String,
    val language: String,
    val result: String,
    val failureReason: String = "",
    val httpStatus: String = "",
    val timeTakenMs: Long = 0L
)
