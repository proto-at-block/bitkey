package build.wallet.ui.app.account.create.hardware

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.bitkey_create_dark
import bitkey.ui.framework_public.generated.resources.bitkey_create_light
import bitkey.ui.framework_public.generated.resources.bitkey_tilt_dark
import bitkey.ui.framework_public.generated.resources.bitkey_tilt_light
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel.BackgroundVideo.VideoContent.*
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.components.button.OrderedButtonPair
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.buildAnnotatedString
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.video.VideoPlayer
import build.wallet.ui.components.video.VideoPlayerHandler
import build.wallet.ui.components.video.VideoScalingMode
import build.wallet.ui.compose.getVideoResource
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.system.BackHandler
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.LocalIsPreviewTheme
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

private val HeroImageBottomSpacing = 40.dp

@Composable
fun PairNewHardwareScreen(
  modifier: Modifier = Modifier,
  model: PairNewHardwareBodyModel,
  debugHeroLayout: Boolean = false,
) {
  if (model.keepScreenOn) {
    KeepScreenOn()
  }

  var videoView: VideoPlayerHandler? by remember { mutableStateOf(null) }
  val showsHeroImage = model.heroImageContent != null

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
      showReplayAction = !showsHeroImage,
      useAdaptiveStyle = true,
      onRefreshClick = {
        // Replay the video
        videoView?.seekTo(0)
        videoView?.play()
      }
    ),
    headerModel = model.header,
    buttonModel = model.primaryButton,
    secondaryButtonModel = model.secondaryButton,
    backgroundContent = if (showsHeroImage) {
      null
    } else {
      {
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
      }
    },
    heroImageContent = model.heroImageContent,
    backgroundVideo = model.backgroundVideo,
    videoAlpha = videoAlpha,
    isNavigatingBack = model.isNavigatingBack,
    useHeroLayout = showsHeroImage,
    debugHeroLayout = debugHeroLayout
  )
}

@Composable
fun PairNewHardwareScreen(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)?,
  toolbarModel: ToolbarModel?,
  headerModel: FormHeaderModel,
  buttonModel: ButtonModel,
  secondaryButtonModel: ButtonModel? = null,
  isNavigatingBack: Boolean,
  backgroundContent: (@Composable () -> Unit)?,
  heroImageContent: PairNewHardwareBodyModel.HeroImageContent? = null,
  backgroundVideo: PairNewHardwareBodyModel.BackgroundVideo,
  videoAlpha: Float = 1f,
  useHeroLayout: Boolean = false,
  debugHeroLayout: Boolean = false,
) {
  onBack?.let {
    BackHandler(onBack = onBack)
  }

  Box(modifier = modifier) {
    if (useHeroLayout && heroImageContent != null) {
      PairNewHardwareHeroScreen(
        modifier = Modifier.fillMaxSize(),
        toolbarModel = toolbarModel,
        headerModel = headerModel,
        buttonModel = buttonModel,
        secondaryButtonModel = secondaryButtonModel,
        backgroundVideo = backgroundVideo,
        heroImageContent = heroImageContent,
        videoAlpha = videoAlpha,
        debugHeroLayout = debugHeroLayout
      )
      return@Box
    }

    PairNewHardwareVideoBackgroundScreen(
      modifier = Modifier.fillMaxSize(),
      toolbarModel = toolbarModel,
      headerModel = headerModel,
      buttonModel = buttonModel,
      secondaryButtonModel = secondaryButtonModel,
      backgroundContent = requireNotNull(backgroundContent),
      isNavigatingBack = isNavigatingBack
    )
  }
}

@Composable
private fun PairNewHardwareHeroScreen(
  modifier: Modifier = Modifier,
  toolbarModel: ToolbarModel?,
  headerModel: FormHeaderModel,
  buttonModel: ButtonModel,
  secondaryButtonModel: ButtonModel? = null,
  backgroundVideo: PairNewHardwareBodyModel.BackgroundVideo,
  heroImageContent: PairNewHardwareBodyModel.HeroImageContent,
  videoAlpha: Float = 1f,
  debugHeroLayout: Boolean = false,
) {
  val isPreviewTheme = LocalIsPreviewTheme.current
  val showsFingerprintVideo =
    heroImageContent == PairNewHardwareBodyModel.HeroImageContent.FingerprintSetup
  val showsFingerprintFallbackImage = showsFingerprintVideo && isPreviewTheme
  val backgroundColor =
    if (showsFingerprintVideo && !showsFingerprintFallbackImage) {
      Color.Black
    } else {
      WalletTheme.colors.background
    }
  val theme = LocalTheme.current
  val heroImagePainter =
    when (heroImageContent) {
      PairNewHardwareBodyModel.HeroImageContent.FingerprintSetup ->
        if (showsFingerprintFallbackImage) {
          painterResource(
            if (theme == Theme.DARK) Res.drawable.bitkey_tilt_dark else Res.drawable.bitkey_tilt_light
          )
        } else {
          null
        }
      PairNewHardwareBodyModel.HeroImageContent.BuildHardwareDescriptor ->
        painterResource(
          if (theme == Theme.DARK) Res.drawable.bitkey_create_dark else Res.drawable.bitkey_create_light
        )
    }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(backgroundColor)
  ) {
    if (showsFingerprintVideo && !showsFingerprintFallbackImage) {
      VideoPlayer(
        modifier = Modifier
          .matchParentSize()
          .alpha(videoAlpha),
        resourcePath = Res.getVideoResource("account_setup"),
        isLooping = false,
        startingPosition = backgroundVideo.startingPosition,
        backgroundColor = backgroundColor,
        scalingMode = VideoScalingMode.CROP
      )
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .systemBarsPadding()
        .padding(horizontal = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      toolbarModel?.let {
        Toolbar(
          model = it,
          showDesignSystemChrome = false
        )
      }

      Spacer(Modifier.height(24.dp))

      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        headerModel.headline?.let { headline ->
          Label(
            text = headline,
            type = LabelType.Body2MonoCaps,
            treatment = LabelTreatment.Unspecified,
            alignment = TextAlign.Center,
            color = WalletTheme.colors.foreground
          )
        }

        headerModel.sublineModel?.buildAnnotatedString()?.let { subline ->
          Spacer(Modifier.height(8.dp))
          Label(
            text = subline,
            type = LabelType.Body3Regular,
            treatment = LabelTreatment.Unspecified,
            alignment = TextAlign.Center,
            color = WalletTheme.colors.foreground60
          )
        }
      }
      Spacer(Modifier.weight(1f))

      heroImagePainter?.let { painter ->
        Image(
          painter = painter,
          contentDescription = null,
          modifier = Modifier
            .fillMaxWidth()
            .then(
              if (debugHeroLayout) {
                Modifier.background(Color.Red.copy(alpha = 0.22f))
              } else {
                Modifier
              }
            ),
          contentScale = ContentScale.FillWidth
        )

        Spacer(Modifier.height(HeroImageBottomSpacing))
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 24.dp)
          .then(
            if (debugHeroLayout) {
              Modifier.background(Color.Green.copy(alpha = 0.14f))
            } else {
              Modifier
            }
          ),
        verticalArrangement = Arrangement.Bottom
      ) {
        OrderedButtonPair(
          primary = buttonModel,
          secondary = secondaryButtonModel,
          spacing = 16.dp
        )
      }
    }
  }
}

@Composable
private fun PairNewHardwareVideoBackgroundScreen(
  modifier: Modifier = Modifier,
  toolbarModel: ToolbarModel?,
  headerModel: FormHeaderModel,
  buttonModel: ButtonModel,
  secondaryButtonModel: ButtonModel? = null,
  isNavigatingBack: Boolean,
  backgroundContent: @Composable () -> Unit,
) {
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
          Toolbar(
            model = it,
            showDesignSystemChrome = false
          )
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
            OrderedButtonPair(
              primary = buttonModel,
              secondary = secondaryButtonModel,
              spacing = 16.dp
            )
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
