package build.wallet.ui.components.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import build.wallet.ui.model.video.VideoStartingPosition

/**
 * Desktop/JVM implementation of [VideoPlayer].
 *
 * LIMITATION: the Bitkey hero media is shipped as true video files
 * (`.mov` on iOS, `.webm` raw resources on Android) that live in the platform
 * app bundles, not in shared Compose resources. The Compose Desktop host has no
 * bundled-video backend on the classpath, and W-17310 explicitly forbids adding
 * a new video dependency (e.g. VLCJ / JavaFX MediaPlayer). The hero assets are
 * also not Lottie animations, so the existing `compottie` path cannot render
 * them either.
 *
 * As a result this actual renders a tasteful, branded static surface (the
 * caller-provided [backgroundColor], which the onboarding/FWUP screens already
 * set to their hero background) rather than an animated video. The screens that
 * use [VideoPlayer] for FWUP additionally overlay a real still image on top
 * (`FwupUpdateHeroPlatformImage`), so the desktop experience degrades to a
 * static hero rather than a blank box.
 *
 * The [VideoPlayerHandler] callback is still invoked with a no-op handler so
 * callers that drive playback imperatively (e.g. PairNewHardware / FormScreen)
 * receive a non-null handler, matching the iOS/Android actuals which always
 * invoke the callback.
 */
@Composable
actual fun VideoPlayer(
  modifier: Modifier,
  resourcePath: String,
  isLooping: Boolean,
  backgroundColor: Color,
  autoStart: Boolean,
  startingPosition: VideoStartingPosition,
  scalingMode: VideoScalingMode,
  topCornerRadius: Dp,
  allowSurfaceOnTopWorkaround: Boolean,
  videoPlayerCallback: (VideoPlayerHandler) -> Unit,
) {
  // Provide a no-op handler so imperative play/seek/pause calls from callers
  // are safe no-ops rather than NPEs from a never-delivered handler.
  DisposableEffect(videoPlayerCallback) {
    videoPlayerCallback(NoOpVideoPlayerHandler)
    onDispose { }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(backgroundColor)
  )
}

private object NoOpVideoPlayerHandler : VideoPlayerHandler {
  override fun play() = Unit

  override fun seekTo(position: Int) = Unit

  override fun pause() = Unit
}
