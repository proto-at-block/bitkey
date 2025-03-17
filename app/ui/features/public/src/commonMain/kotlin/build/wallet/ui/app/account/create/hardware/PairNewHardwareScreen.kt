package build.wallet.ui.app.account.create.hardware

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.*
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.video.VideoPlayer
import build.wallet.ui.components.video.VideoPlayerHandler
import build.wallet.ui.compose.getVideoResource
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.system.BackHandler
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.theme.Theme
import kotlinx.coroutines.delay

@Composable
fun PairNewHardwareScreen(
  modifier: Modifier = Modifier,
  model: PairNewHardwareBodyModel,
) {
  if (model.keepScreenOn) {
    KeepScreenOn()
  }

  var videoView: VideoPlayerHandler? by remember { mutableStateOf(null) }

  var videoAlpha: Float by remember { mutableStateOf(0.0f) }

  /**
   * 500ms after the screen has appeared, fade in the video. This prevents flickering of videos
   * during the transition from the prior [ChooseAccountAccessScreen] to this.
   */
  LaunchedEffect("Fade video in") {
    delay(500)
    videoAlpha = 1.0f
  }

  PairNewHardwareScreen(
    modifier = modifier,
    onBack = model.onBack,
    toolbarModel = model.toolbarModel(
      onRefreshClick = {
        // Replay the video
        videoView?.seekTo(0)
        videoView?.play()
      }
    ),
    headerModel = model.header,
    buttonModel = model.primaryButton,
    backgroundContent = {
      BoxWithConstraints {
        VideoPlayer(
          modifier =
            Modifier
              .wrapContentSize(Alignment.TopCenter, unbounded = true)
              .alpha(videoAlpha)
              .size(maxWidth + 200.dp),
          resourcePath =
            when (model.backgroundVideo.content) {
              BitkeyActivate -> Res.getVideoResource("activate")
              BitkeyFingerprint -> Res.getVideoResource("fingerprint")
              BitkeyPair -> Res.getVideoResource("pair")
            },
          isLooping = false,
          startingPosition = model.backgroundVideo.startingPosition,
          videoPlayerCallback = { view ->
            videoView = view
          }
        )
      }
    },
    isNavigatingBack = model.isNavigatingBack
  )
}

@Composable
fun PairNewHardwareScreen(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)?,
  toolbarModel: ToolbarModel?,
  headerModel: FormHeaderModel,
  buttonModel: ButtonModel,
  isNavigatingBack: Boolean,
  backgroundContent: @Composable () -> Unit,
) {
  onBack?.let {
    BackHandler(onBack = onBack)
  }
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    Box {
      // Background
      backgroundContent()

      // Content
      Column(
        modifier =
          Modifier
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Toolbar
        toolbarModel?.let {
          Toolbar(model = it)
        }

        // Header and button
        AnimatedContent(
          targetState = headerModel, // Animate on changes to the header
          transitionSpec = { slideAndFadeContentTransform(isNavigatingBack) },
          label = "PairNewHardwareHeaderAnimation"
        ) { newHeaderModel ->
          Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom
          ) {
            Header(
              model = newHeaderModel,
              theme = Theme.DARK
            )
            Spacer(Modifier.height(24.dp))
            Button(buttonModel)
            Spacer(Modifier.height(24.dp))
          }
        }
      }
    }
  }
}

private fun slideAndFadeContentTransform(isNavigatingBack: Boolean): ContentTransform {
  val slideTransitionXOffset = 300
  val slideAnimationSpec: FiniteAnimationSpec<IntOffset> = tween(
    durationMillis = 300,
    easing = FastOutSlowInEasing
  )
  val fadeAnimationSpec: FiniteAnimationSpec<Float> = tween(durationMillis = 500)

  return slideInHorizontally(
    initialOffsetX = {
      if (isNavigatingBack) -slideTransitionXOffset else slideTransitionXOffset
    },
    animationSpec = slideAnimationSpec
  ).plus(fadeIn(animationSpec = fadeAnimationSpec)) togetherWith
    slideOutHorizontally(
      targetOffsetX = {
        if (isNavigatingBack) slideTransitionXOffset else -slideTransitionXOffset
      },
      animationSpec = slideAnimationSpec
    ).plus(fadeOut(animationSpec = fadeAnimationSpec))
}
