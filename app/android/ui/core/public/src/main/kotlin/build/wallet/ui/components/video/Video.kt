package build.wallet.ui.components.video

import android.net.Uri
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.ui.model.video.VideoStartingPosition
import build.wallet.ui.model.video.VideoStartingPosition.END
import build.wallet.ui.model.video.VideoStartingPosition.START

/**
 * @param resource: The raw resource for the video, i.e. [R.raw.video]
 * @param isLooping: Whether the video should infinitely loop
 * @param startingPosition: The starting position of the video.
 * @param videoViewCallback: A callback that provides the [VideoView] for callers to use directly
 */
@Composable
fun Video(
  modifier: Modifier = Modifier,
  resource: Int,
  isLooping: Boolean,
  startingPosition: VideoStartingPosition = START,
  videoViewCallback: (VideoView) -> Unit = {},
) {
  // Store the video and pause position to be able to resume after the
  // app is backgrounded / foregrounded
  var videoView: VideoView? by remember { mutableStateOf(null) }
  var videoPausedPosition: Int? by remember { mutableStateOf(null) }

  AndroidView(
    modifier = modifier,
    factory = { context ->
      VideoView(context)
    },
    update = { video ->
      video.apply {
        setVideoURI(
          Uri.parse(
            "android.resource://" +
              context.packageName +
              "/" + resource
          )
        )
        setOnPreparedListener { mediaPlayer ->
          mediaPlayer.isLooping = isLooping
          mediaPlayer.setVolume(0f, 0f)
          when (startingPosition) {
            START -> Unit
            END -> mediaPlayer.seekTo(duration)
          }
        }
        setOnErrorListener { _, what, extra ->
          log(LogLevel.Warn) { "Error playing video: $what | $extra" }
          true
        }
        start()
        videoView = this
        videoViewCallback(this)
      }
    }
  )

  // Set up listener for app lifecycle events to pause / resume the video

  val lifecycleOwner = LocalLifecycleOwner.current

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_PAUSE) {
        videoPausedPosition = videoView?.currentPosition
        videoView?.pause()
      }

      if (event == Lifecycle.Event.ON_RESUME) {
        videoPausedPosition?.let {
          videoView?.seekTo(it)
          videoView?.start()
        }
      }
    }

    lifecycleOwner.lifecycle.addObserver(observer)

    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }
}
