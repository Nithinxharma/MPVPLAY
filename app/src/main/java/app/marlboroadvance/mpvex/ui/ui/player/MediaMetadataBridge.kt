package app.marlboroadvance.mpvex.ui.player

import app.marlboroadvance.mpvex.ui.player.controls.components.MediaType

object MediaMetadataBridge {
    private var pendingMetadata: Map<String, Any>? = null

    fun setMetadata(
        type: MediaType,
        artwork: String?,
        title: String,
        subtitle: String?,
        description: String?,
        metadata: Map<String, String>
    ) {
        pendingMetadata = mapOf(
            "type" to type,
            "artwork" to (artwork ?: ""),
            "title" to title,
            "subtitle" to (subtitle ?: ""),
            "description" to (description ?: ""),
            "metadata" to metadata
        )
    }

    fun consumeMetadata(): Map<String, Any>? {
        val data = pendingMetadata
        pendingMetadata = null
        return data
    }
}
