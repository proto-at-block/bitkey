package build.wallet.ui.app.account.create.hardware

import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import build.wallet.android.ui.core.R
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.BitkeyActivate
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.BitkeyFingerprint
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.BitkeyPair
import build.wallet.statemachine.core.ScreenColorMode
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.video.Video
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.system.KeepScreenOn
import kotlinx.coroutines.delay

@Composable
fun PairNewHardwareScreen(model: PairNewHardwareBodyModel) {
  if (model.keepScreenOn) {
    KeepScreenOn()
  }

  var videoView: VideoView? by remember { mutableStateOf(null) }

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
    onBack = model.onBack,
    toolbarModel = model.toolbarModel(
      onRefreshClick = {
        // Replay the video
        videoView?.seekTo(0)
        videoView?.start()
      }
    ),
    headerModel = model.header,
    buttonModel = model.primaryButton,
    backgroundContent = {
      BoxWithConstraints {
        Video(
          modifier =
            Modifier
              .wrapContentSize(Alignment.TopCenter, unbounded = true)
              .alpha(videoAlpha)
              .size(maxWidth + 200.dp),
          resource =
            when (model.backgroundVideo.content) {
              BitkeyActivate -> R.raw.activate
              BitkeyFingerprint -> R.raw.fingerprint
              BitkeyPair -> R.raw.pair
            },
          isLooping = false,
          startingPosition = model.backgroundVideo.startingPosition,
          videoViewCallback = { view ->
            videoView = view
          }
        )
      }
    },
    isNavigatingBack = model.isNavigatingBack
  )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PairNewHardwareScreen(
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
    modifier =
      Modifier
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
              colorMode = ScreenColorMode.Dark
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
