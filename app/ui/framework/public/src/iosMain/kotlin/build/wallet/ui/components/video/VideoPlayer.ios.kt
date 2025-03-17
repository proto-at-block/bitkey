package build.wallet.ui.components.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import build.wallet.ui.model.video.VideoStartingPosition
import kotlinx.cinterop.*
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.UIKit.*

@OptIn(ExperimentalForeignApi::class)
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
  val player = remember {
    if (isLooping) {
      AVQueuePlayer()
    } else {
      AVPlayer()
    }
  }
  val playerHandler = remember(resourcePath, isLooping, autoStart) {
    AVPlayerHandler(
      player = player,
      resourcePath = resourcePath,
      isLooping = isLooping,
      autoStart = autoStart
    ).also(videoPlayerCallback)
  }

  DisposableEffect(Unit) {
    // TODO(W-9784): Support pause on will resign
    playerHandler.addBackgroundObservers()
    onDispose {
      playerHandler.removeBackgroundObservers()
      playerHandler.dispose()
    }
  }
  val factory = remember(player) {
    {
      object : UIView(cValue { CGRectZero }) {
        private val playerLayer = AVPlayerLayer.playerLayerWithPlayer(player)

        init {
          this.backgroundColor = UIColor.colorWithRed(
            red = backgroundColor.red.toDouble(),
            green = backgroundColor.green.toDouble(),
            blue = backgroundColor.blue.toDouble(),
            alpha = backgroundColor.alpha.toDouble()
          )
          layer.addSublayer(playerLayer)
        }

        override fun layoutSubviews() {
          super.layoutSubviews()
          playerLayer.setFrame(bounds)
        }
      }
    }
  }

  UIKitView(
    factory = factory,
    modifier = modifier
      .fillMaxSize()
      .background(backgroundColor),
    properties = UIKitInteropProperties(
      isInteractive = false,
      isNativeAccessibilityEnabled = false
    )
  )
}

@OptIn(ExperimentalForeignApi::class)
private data class AVPlayerHandler(
  private val player: AVPlayer,
  private val resourcePath: String,
  private val isLooping: Boolean,
  private val autoStart: Boolean,
) : VideoPlayerHandler() {
  private var looper: AVPlayerLooper? = null

  init {
    val url = requireNotNull(NSURL.URLWithString(resourcePath)) {
      "Failed to parse URL from '$resourcePath'"
    }
    val item = AVPlayerItem.playerItemWithURL(url)
    player.replaceCurrentItemWithPlayerItem(item)

    if (isLooping) {
      looper = AVPlayerLooper.playerLooperWithPlayer(player as AVQueuePlayer, item)
    }
    if (autoStart) {
      player.play()
    }
  }

  override fun play() {
    player.play()
  }

  override fun seekTo(position: Int) {
    player.seekToTime(CMTimeMakeWithSeconds(position.toDouble(), 1))
  }

  override fun pause() {
    player.pause()
  }

  fun addBackgroundObservers() {
    NSNotificationCenter.defaultCenter.apply {
      addObserver(
        observer = player,
        selector = NSSelectorFromString(player::play.name),
        name = UIApplicationWillEnterForegroundNotification,
        `object` = null
      )
      addObserver(
        observer = player,
        selector = NSSelectorFromString(player::pause.name),
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null
      )
    }
  }

  fun removeBackgroundObservers() {
    NSNotificationCenter.defaultCenter.apply {
      removeObserver(
        observer = player,
        name = UIApplicationWillEnterForegroundNotification,
        `object` = null
      )
      removeObserver(
        observer = player,
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null
      )
    }
  }

  fun dispose() {
    looper = null
  }
}
