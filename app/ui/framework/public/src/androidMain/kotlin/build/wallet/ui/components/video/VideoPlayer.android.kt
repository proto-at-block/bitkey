package build.wallet.ui.components.video

import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import build.wallet.logging.*
import build.wallet.ui.model.video.VideoStartingPosition
import build.wallet.ui.model.video.VideoStartingPosition.END
import build.wallet.ui.model.video.VideoStartingPosition.START
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
actual fun VideoPlayer(
  modifier: Modifier,
  resourcePath: String,
  isLooping: Boolean,
  backgroundColor: Color,
  autoStart: Boolean,
  startingPosition: VideoStartingPosition,
  scalingMode: VideoScalingMode,
  allowSurfaceOnTopWorkaround: Boolean,
  videoPlayerCallback: (VideoPlayerHandler) -> Unit,
) {
  // Store the video and pause position to be able to resume after the
  // app is backgrounded / foregrounded
  var videoView: VideoView? by remember { mutableStateOf(null) }
  var videoPausedPosition: Int? by remember { mutableStateOf(null) }

  val useSurfaceOnTopWorkaround = allowSurfaceOnTopWorkaround && backgroundColor != Color.Black

  AndroidView(
    modifier = modifier,
    factory = { context ->
      FrameLayout(context).apply {
        clipChildren = true
        clipToPadding = true
        if (backgroundColor != Color.Black) {
          setBackgroundColor(backgroundColor.toArgb())
        }
        addView(
          VideoView(context).apply {
            if (useSurfaceOnTopWorkaround) {
              setZOrderOnTop(true)
              background = ColorDrawable(backgroundColor.toArgb())
            }
          },
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
          )
        )
      }
    },
    update = { container ->
      val video = container.getChildAt(0) as VideoView
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
          mediaPlayer.setOnVideoSizeChangedListener { _, width, height ->
            updateVideoLayout(
              container = container,
              videoView = video,
              scalingMode = scalingMode,
              videoWidth = width,
              videoHeight = height
            )
          }
          updateVideoLayout(
            container = container,
            videoView = video,
            scalingMode = scalingMode,
            videoWidth = mediaPlayer.videoWidth,
            videoHeight = mediaPlayer.videoHeight
          )
        }
        if (useSurfaceOnTopWorkaround) {
          setOnInfoListener { _, what, _ ->
            if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
              setZOrderOnTop(false)
              background = null
            }
            false
          }
        }
        setOnErrorListener { _, what, extra ->
          logError {
            "Error playing video: errorCode=$what, extraCode=$extra, resourcePath='$resourcePath'"
          }
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

            override fun seekTo(position: Int) {
              videoView?.seekTo(position)
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

private fun updateVideoLayout(
  container: FrameLayout,
  videoView: VideoView,
  scalingMode: VideoScalingMode,
  videoWidth: Int,
  videoHeight: Int,
) {
  if (scalingMode == VideoScalingMode.FIT || videoWidth <= 0 || videoHeight <= 0) {
    videoView.layoutParams = FrameLayout.LayoutParams(
      FrameLayout.LayoutParams.MATCH_PARENT,
      FrameLayout.LayoutParams.MATCH_PARENT,
      Gravity.CENTER
    )
    return
  }

  val containerWidth = container.width
  val containerHeight = container.height

  if (containerWidth == 0 || containerHeight == 0) {
    container.post {
      updateVideoLayout(
        container = container,
        videoView = videoView,
        scalingMode = scalingMode,
        videoWidth = videoWidth,
        videoHeight = videoHeight
      )
    }
    return
  }

  val scale = max(
    containerWidth.toFloat() / videoWidth,
    containerHeight.toFloat() / videoHeight
  )
  videoView.layoutParams = FrameLayout.LayoutParams(
    (videoWidth * scale).roundToInt(),
    (videoHeight * scale).roundToInt(),
    Gravity.CENTER
  )
}
