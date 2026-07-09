package build.wallet.ui.components.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.ui.model.video.VideoStartingPosition
import build.wallet.ui.model.video.VideoStartingPosition.START

/**
 * A Compose video player shim wrapping Android VideoView and
 * iOS AVPlayer, with no-op for JVM.
 */
@Composable
expect fun VideoPlayer(
  modifier: Modifier = Modifier,
  resourcePath: String,
  isLooping: Boolean,
  backgroundColor: Color = Color.Black,
  autoStart: Boolean = true,
  startingPosition: VideoStartingPosition = START,
  scalingMode: VideoScalingMode = VideoScalingMode.FIT,
  topCornerRadius: Dp = 0.dp,
  allowSurfaceOnTopWorkaround: Boolean = true,
  videoPlayerCallback: (VideoPlayerHandler) -> Unit = {},
)

enum class VideoScalingMode {
  FIT,
  CROP,
}

interface VideoPlayerHandler {
  fun play()

  fun seekTo(position: Int)

  fun pause()
}
