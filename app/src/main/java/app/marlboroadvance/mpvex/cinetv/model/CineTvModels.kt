package app.marlboroadvance.mpvex.cinetv.model

enum class LiveTab(val label: String) {
    CHANNELS("Live Channels"),
    JIO_LOGIN("Jio Authentication")
}

data class LiveChannelItem(
    val channelId: String,
    val title: String,
    val category: String,
    val language: String,
    val logoUrl: String,
    val streamUrlHash: String,
    val currentProgram: String = "Live Broadcast Stream",
    val programTime: String = "Now Playing"
)
