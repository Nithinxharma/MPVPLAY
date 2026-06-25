package app.marlboroadvance.mpvex.preferences

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Segment
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents a customizable button in the player controls.
 * Now includes an icon for the preference UI.
 */
enum class PlayerButton(
  val icon: ImageVector,
) {
  BACK_ARROW(Icons.AutoMirrored.Outlined.ArrowBack),
  VIDEO_TITLE(Icons.Outlined.Title),
  BOOKMARKS_CHAPTERS(Icons.Outlined.Bookmarks),
  PLAYBACK_SPEED(Icons.Outlined.Speed),
  DECODER(Icons.Outlined.Memory),
  SCREEN_ROTATION(Icons.Outlined.ScreenRotation),
  FRAME_NAVIGATION(Icons.Outlined.Camera),
  VIDEO_ZOOM(Icons.Outlined.ZoomIn),
  PICTURE_IN_PICTURE(Icons.Outlined.PictureInPictureAlt),
  ASPECT_RATIO(Icons.Outlined.AspectRatio),
  LOCK_CONTROLS(Icons.Outlined.LockOpen),
  AUDIO_TRACK(Icons.Outlined.Audiotrack),
  SUBTITLES(Icons.Outlined.Subtitles),
  MORE_OPTIONS(Icons.Outlined.MoreVert),
  CURRENT_CHAPTER(Icons.Outlined.Bookmarks),
  REPEAT_MODE(Icons.Outlined.Repeat),
  SHUFFLE(Icons.Outlined.Shuffle),
  MIRROR(Icons.Outlined.Flip),
  VERTICAL_FLIP(Icons.Outlined.Flip),
  AB_LOOP(Icons.AutoMirrored.Outlined.Segment),
  CUSTOM_SKIP(Icons.Outlined.FastForward),
  BACKGROUND_PLAYBACK(Icons.Outlined.Headset),
  AMBIENT_MODE(Icons.Outlined.BlurOn),

  CHANNEL_INFO(Icons.Outlined.LiveTv),

  NONE(Icons.Outlined.Bookmarks),
}

/**
 * A list of all buttons that the user can choose from in the customization menu.
 * Excludes NONE (placeholder) and constant buttons (BACK_ARROW, VIDEO_TITLE).
 */
val allPlayerButtons =
  PlayerButton.values().filter {
    it != PlayerButton.NONE &&
      it != PlayerButton.BACK_ARROW &&
      it != PlayerButton.VIDEO_TITLE
  }

/**
 * Gets the human-readable label for a player button.
 */
@Composable
fun getPlayerButtonLabel(button: PlayerButton): String =
  when (button) {
    PlayerButton.BACK_ARROW -> "Back Arrow"
    PlayerButton.VIDEO_TITLE -> "Video Title"
    PlayerButton.BOOKMARKS_CHAPTERS -> "Chapters / Bookmarks"
    PlayerButton.PLAYBACK_SPEED -> "Playback Speed"
    PlayerButton.DECODER -> "Decoder"
    PlayerButton.SCREEN_ROTATION -> "Screen Rotation"
    PlayerButton.FRAME_NAVIGATION -> "Frame Navigation"
    PlayerButton.VIDEO_ZOOM -> "Video Zoom"
    PlayerButton.PICTURE_IN_PICTURE -> "Picture-in-Picture"
    PlayerButton.ASPECT_RATIO -> "Aspect Ratio"
    PlayerButton.LOCK_CONTROLS -> "Lock Controls"
    PlayerButton.AUDIO_TRACK -> "Audio Track"
    PlayerButton.SUBTITLES -> "Subtitles"
    PlayerButton.MORE_OPTIONS -> "More Options"
    PlayerButton.CURRENT_CHAPTER -> "Current Chapter"
    PlayerButton.REPEAT_MODE -> "Repeat Mode"
    PlayerButton.SHUFFLE -> "Shuffle"
    PlayerButton.MIRROR -> "Horizontal Flip"
    PlayerButton.VERTICAL_FLIP -> "Vertical Flip"
    PlayerButton.AB_LOOP -> "A-B Loop"
    PlayerButton.CUSTOM_SKIP -> "Custom Skip"
    PlayerButton.BACKGROUND_PLAYBACK -> "Background Playback"
    PlayerButton.AMBIENT_MODE -> "Ambience Mode"
    PlayerButton.CHANNEL_INFO -> "Channel / Media Info"
    PlayerButton.NONE -> "None"
  }
