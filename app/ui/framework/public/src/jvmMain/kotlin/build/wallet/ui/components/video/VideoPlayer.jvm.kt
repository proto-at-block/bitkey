package build.wallet.ui.components.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import build.wallet.ui.model.video.VideoStartingPosition

@Composable
actual fun VideoPlayer(
  modifier: Modifier,
  resourcePath: String,
  isLooping: Boolean,
  backgroundColor: Color,
  autoStart: Boolean,
  startingPosition: VideoStartingPosition,
  videoPlayerCallback: (VideoPlayerHandler) -> Unit,
) {
  // no-op
  Box(modifier = modifier.background(backgroundColor))
}
