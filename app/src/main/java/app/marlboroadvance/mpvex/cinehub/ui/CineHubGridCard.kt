package app.marlboroadvance.mpvex.cinehub.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun CineHubGridCard(
    title: String,
    genre: String,
    rating: Double,
    posterPath: String?,
    onClick: () -> Unit
) {
    // Interaction source to track touch injection press triggers
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth cinematic scaling animation response on touch click
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "CardScaleAnimation"
    )

    Card(
        modifier = Modifier
            .padding(6.dp)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Removes harsh default ripple to preserve clean scaling aesthetics
                onClick = onClick
            ),
        shape = RoundedCornerShape(14.dp), // More modern fluid corner curves
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isPressed) 0.4f else 0.1f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 2.dp else 6.dp
        )
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                // High definition poster artwork container
                AsyncImage(
                    model = posterPath ?: android.R.drawable.ic_menu_gallery,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .background(Color.DarkGray)
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                )

                // Cinematic dark overlay sheet at the bottom of thumbnail to enhance title text pop
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.65f)
                                )
                            )
                        )
                )

                // Glassmorphic Premium Stream Rating Stamp Indicator Badge
                if (rating > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "★",
                                color = Color(0xFFFFD700),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // Text descriptive layouts padded tightly matching Material 3 rules
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = genre.ifEmpty { "Media Content" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
