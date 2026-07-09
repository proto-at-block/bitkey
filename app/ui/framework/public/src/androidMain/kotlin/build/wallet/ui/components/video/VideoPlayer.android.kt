package build.wallet.ui.components.video

import android.graphics.Outline
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import build.wallet.logging.logError
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
  topCornerRadius: Dp,
  allowSurfaceOnTopWorkaround: Boolean,
  videoPlayerCallback: (VideoPlayerHandler) -> Unit,
) {
  SurfaceVideoPlayer(
    modifier = modifier,
    resourcePath = resourcePath,
    isLooping = isLooping,
    backgroundColor = backgroundColor,
    autoStart = autoStart,
    startingPosition = startingPosition,
    scalingMode = scalingMode,
    topCornerRadius = topCornerRadius,
    allowSurfaceOnTopWorkaround = allowSurfaceOnTopWorkaround,
    videoPlayerCallback = videoPlayerCallback
  )
}

@Composable
private fun SurfaceVideoPlayer(
  modifier: Modifier = Modifier,
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
  var videoView: VideoView? by remember { mutableStateOf(null) }
  var videoPausedPosition: Int? by remember { mutableStateOf(null) }
  val topCornerRadiusPx = with(LocalDensity.current) { topCornerRadius.toPx() }

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
        updateTopCornerOutline(topCornerRadiusPx)
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
      container.updateTopCornerOutline(topCornerRadiusPx)
      val video = container.getChildAt(0) as VideoView
      video.apply {
        setVideoURI(Uri.parse("android.resource://${context.packageName}/$resourcePath"))
        setOnPreparedListener { mediaPlayer ->
          mediaPlayer.isLooping = isLooping
          mediaPlayer.setVolume(0f, 0f)
          when (startingPosition) {
            START -> Unit
            END -> mediaPlayer.seekTo(duration)
          }
          mediaPlayer.setOnVideoSizeChangedListener { _, width, height ->
            updateSurfaceVideoLayout(
              container = container,
              videoView = video,
              scalingMode = scalingMode,
              videoWidth = width,
              videoHeight = height
            )
          }
          updateSurfaceVideoLayout(
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
          object : VideoPlayerHandler {
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

private fun View.updateTopCornerOutline(topCornerRadiusPx: Float) {
  outlineProvider =
    object : ViewOutlineProvider() {
      override fun getOutline(
        view: View,
        outline: Outline,
      ) {
        if (topCornerRadiusPx <= 0f || view.width == 0 || view.height == 0) {
          outline.setRect(0, 0, view.width, view.height)
          return
        }

        outline.setConvexPath(
          Path().apply {
            addRoundRect(
              0f,
              0f,
              view.width.toFloat(),
              view.height.toFloat(),
              floatArrayOf(
                topCornerRadiusPx,
                topCornerRadiusPx,
                topCornerRadiusPx,
                topCornerRadiusPx,
                0f,
                0f,
                0f,
                0f
              ),
              Path.Direction.CW
            )
          }
        )
      }
    }
  clipToOutline = topCornerRadiusPx > 0f
  invalidateOutline()
}

private fun updateSurfaceVideoLayout(
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
      updateSurfaceVideoLayout(
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
