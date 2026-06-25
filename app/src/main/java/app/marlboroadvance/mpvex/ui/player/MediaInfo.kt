package app.marlboroadvance.mpvex.ui.player

import app.marlboroadvance.mpvex.ui.player.controls.components.MediaType

data class MediaInfo(
    val type: MediaType,
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val artworkUrl: String? = null,
    val metadata: Map<String, String> = emptyMap()
)