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
    val defaultChannelId: String, // The ID to use if no specific variant is selected
    val title: String,
    val category: String,
    val defaultLanguage: String,
    val logoUrl: String,
    val streamUrlHash: String,
    val variants: List<ChannelVariant> = emptyList(), // Holds all language variants
    val currentProgram: String = "Live Broadcast Stream",
    val programTime: String = "Now Playing"
) {
    // Helper to get channelId by language name
    fun getIdForLanguage(lang: String): String {
        return variants.find { it.language == lang }?.channelId ?: defaultChannelId
    }
}
