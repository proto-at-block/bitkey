package build.wallet.ui.components.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
  videoPlayerCallback: (VideoPlayerHandler) -> Unit = {},
)

abstract class VideoPlayerHandler {
  abstract fun play()

  abstract fun seekTo(position: Int)

  abstract fun pause()
}
