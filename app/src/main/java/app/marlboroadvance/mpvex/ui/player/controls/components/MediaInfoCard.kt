package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

enum class MediaInfoPosition {
    LEFT, RIGHT, TOP, BOTTOM
}

enum class MediaType {
    LIVE_TV, MOVIE, TV_SHOW, AUDIO, UNKNOWN
}

/**
 * A floating, animated media information overlay card.
 * Intended to be placed within a Box occupying the player space.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaInfoCard(
    mediaType: MediaType,
    artworkUrl: String?,
    title: String,
    subtitle: String?,
    description: String?,
    metadata: Map<String, String>,
    visible: Boolean,
    position: MediaInfoPosition,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alignment = remember(position) {
        when (position) {
            MediaInfoPosition.LEFT -> Alignment.CenterStart
            MediaInfoPosition.RIGHT -> Alignment.CenterEnd
            MediaInfoPosition.TOP -> Alignment.TopCenter
            MediaInfoPosition.BOTTOM -> Alignment.BottomCenter
        }
    }

    val enterTransition = remember(position) {
        when (position) {
            MediaInfoPosition.LEFT -> slideInHorizontally { -it } + fadeIn()
            MediaInfoPosition.RIGHT -> slideInHorizontally { it } + fadeIn()
            MediaInfoPosition.TOP -> slideInVertically { -it } + fadeIn()
            MediaInfoPosition.BOTTOM -> slideInVertically { it } + fadeIn()
        }
    }

    val exitTransition = remember(position) {
        when (position) {
            MediaInfoPosition.LEFT -> slideOutHorizontally { -it } + fadeOut()
            MediaInfoPosition.RIGHT -> slideOutHorizontally { it } + fadeOut()
            MediaInfoPosition.TOP -> slideOutVertically { -it } + fadeOut()
            MediaInfoPosition.BOTTOM -> slideOutVertically { it } + fadeOut()
        }
    }

    // Wrap in a Box filling maximum bounds so alignment maps naturally inside the caller's overlay
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = alignment
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = enterTransition,
            exit = exitTransition
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(min = 280.dp, max = 420.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    // Blur-like appearance achieved by mixing a high-elevation surface color with alpha
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // --- Dynamic Artwork Layout ---
                    val isSquare = mediaType == MediaType.LIVE_TV || mediaType == MediaType.AUDIO
                    val imageModifier = Modifier
                        .width(if (isSquare) 80.dp else 100.dp)
                        .aspectRatio(if (isSquare) 1f else 2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    Box(modifier = imageModifier, contentAlignment = Alignment.Center) {
                        if (artworkUrl.isNullOrBlank()) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Placeholder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(32.dp)
                            )
                        } else {
                            AsyncImage(
                                model = artworkUrl,
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // --- Detail Layout ---
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!subtitle.isNullOrBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(28.dp)
                                    .offset(x = 8.dp, y = (-8).dp) // Align cleanly to top-right
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!description.isNullOrBlank()) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Dynamic Metadata Map Blocks
                        if (metadata.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                metadata.forEach { (key, value) ->
                                    if (value.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = if (key.isBlank()) value else "$key: $value",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Dedicated injection block for explicit Live TV state tracking
                        if (mediaType == MediaType.LIVE_TV && !metadata.containsKey("EPG")) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "EPG Coming Soon",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
