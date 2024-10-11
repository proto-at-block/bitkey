package build.wallet.ui.components.video

import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.ui.model.video.VideoStartingPosition
import build.wallet.ui.model.video.VideoStartingPosition.END
import build.wallet.ui.model.video.VideoStartingPosition.START

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
  // Store the video and pause position to be able to resume after the
  // app is backgrounded / foregrounded
  var videoView: VideoView? by remember { mutableStateOf(null) }
  var videoPausedPosition: Int? by remember { mutableStateOf(null) }

  AndroidView(
    modifier = modifier,
    factory = { context ->
      VideoView(context).apply {
        if (backgroundColor != Color.Black) {
          setZOrderOnTop(true)
          background = ColorDrawable(backgroundColor.toArgb())
        }
      }
    },
    update = { video ->
      video.apply {
        setVideoURI(
          Uri.parse("android.resource://${context.packageName}/$resourcePath")
        )
        setOnPreparedListener { mediaPlayer ->
          mediaPlayer.isLooping = isLooping
          mediaPlayer.setVolume(0f, 0f)
          when (startingPosition) {
            START -> Unit
            END -> mediaPlayer.seekTo(duration)
          }
        }
        if (backgroundColor != Color.Black) {
          setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
              setZOrderOnTop(false)
              background = null
            }
            false
          }
        }
        setOnErrorListener { _, what, extra ->
          log(LogLevel.Warn) { "Error playing video: $what | $extra" }
          true
        }
        if (autoStart) {
          start()
        }
        videoView = this
        videoPlayerCallback(
          object : VideoPlayerHandler() {
            override fun play() {
              videoView?.start()
            }

            override fun pause() {
              videoView?.pause()
            }
          }
        )
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
