package build.wallet.ui.components.video

import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import build.wallet.ui.model.video.VideoStartingPosition
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.Foundation.NSURL
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIColor
import platform.UIKit.UIView

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
  // Retain AVPlayerLooper if necessary to loop indefinitely
  var looper by remember { mutableStateOf<AVPlayerLooper?>(null) }
  val playerHandler by produceState<AVPlayerHandler?>(null, resourcePath) {
    val url = NSURL.URLWithString(resourcePath) ?: return@produceState
    val item = AVPlayerItem.playerItemWithURL(url)
    player.replaceCurrentItemWithPlayerItem(item)

    if (isLooping) {
      looper = AVPlayerLooper.playerLooperWithPlayer(player as AVQueuePlayer, item)
    }
    if (autoStart) {
      player.play()
    }

    value = AVPlayerHandler(player).also(videoPlayerCallback)
  }

  val factory = remember {
    {
      val backgroundUIColor = UIColor.colorWithRed(
        red = backgroundColor.red.toDouble(),
        green = backgroundColor.green.toDouble(),
        blue = backgroundColor.blue.toDouble(),
        alpha = backgroundColor.alpha.toDouble()
      )
      UIView().apply {
        this.backgroundColor = backgroundUIColor
        val playerLayer = AVPlayerLayer.playerLayerWithPlayer(player)
          .also { it.backgroundColor = backgroundUIColor.CGColor }
        layer.addSublayer(playerLayer)
      }
    }
  }

  DisposableEffect(Unit) {
    // TODO(W-9784): Support pause on will resign
    playerHandler?.let { handler ->
      NSNotificationCenter.defaultCenter
        .addObserver(
          observer = player,
          selector = NSSelectorFromString(handler::play.name),
          name = UIApplicationWillEnterForegroundNotification,
          `object` = null
        )
      NSNotificationCenter.defaultCenter
        .addObserver(
          observer = player,
          selector = NSSelectorFromString(handler::pause.name),
          name = UIApplicationDidEnterBackgroundNotification,
          `object` = null
        )
    }
    onDispose {
      NSNotificationCenter.defaultCenter
        .removeObserver(
          observer = player,
          name = UIApplicationWillEnterForegroundNotification,
          `object` = null
        )

      NSNotificationCenter.defaultCenter
        .removeObserver(
          observer = player,
          name = UIApplicationDidEnterBackgroundNotification,
          `object` = null
        )
      looper = null
    }
  }
  UIKitView(
    modifier = modifier.background(backgroundColor),
    factory = factory,
    onResize = { view, size ->
      view.layer.sublayers.orEmpty()
        .filterIsInstance<AVPlayerLayer>()
        .forEach { it.setFrame(size) }
    },
    onRelease = { looper = null },
    interactive = false
  )
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private data class AVPlayerHandler(
  private val player: AVPlayer,
) : VideoPlayerHandler() {
  @ObjCAction
  override fun play() {
    player.play()
  }

  override fun seekTo(position: Int) {
    player.seekToTime(CMTimeMakeWithSeconds(position.toDouble(), 1))
  }

  @ObjCAction
  override fun pause() {
    player.pause()
  }
}
