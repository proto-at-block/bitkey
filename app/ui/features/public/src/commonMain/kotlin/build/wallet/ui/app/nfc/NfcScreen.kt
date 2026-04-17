package build.wallet.ui.app.nfc

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bitkey.account.HardwareType
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.android_nfc_tap
import bitkey.ui.framework_public.generated.resources.ios_nfc_background_standard
import bitkey.ui.framework_public.generated.resources.ios_nfc_background_w1
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel.Status.*
import build.wallet.ui.app.LocalDeviceInfo
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.progress.IndeterminateCircularProgressIndicator
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.toolbar.ToolbarAccessory
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Translucent
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.system.BackHandler
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.system.isBlurSupported
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.painterResource

private val IosNfcVideoTopSpacing = 16.dp

@Composable
fun NfcScreen(
  modifier: Modifier = Modifier,
  model: NfcBodyModel,
) {
  KeepScreenOn()
  val devicePlatform = LocalDeviceInfo.current.devicePlatform
  val designSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

  when {
    devicePlatform == DevicePlatform.IOS && designSystemV2Enabled -> {
      NfcScreenInternalIos(model = model, modifier = modifier)
    }
    devicePlatform == DevicePlatform.Android && designSystemV2Enabled -> {
      NfcScreenInternalV2(model = model, modifier = modifier)
    }
    else -> {
      NfcScreenInternal(model = model, modifier = modifier)
    }
  }
}

@Composable
internal fun NfcScreenInternal(
  model: NfcBodyModel,
  modifier: Modifier = Modifier,
) {
  model.status.whenInProgress { onCancel ->
    BackHandler {
      onCancel()
    }
  }

  val cancelButtonAlpha: Float by animateFloatAsState(
    targetValue =
      when (model.status) {
        is Searching, is Connected -> 1f
        is Success -> 0f
      },
    label = "cancelButtonAlphaAnimation"
  )
  NfcBlurBackground {
    Column(
      modifier =
        modifier
          .background(WalletTheme.colors.foreground.copy(alpha = 0.1F))
          .padding(horizontal = 20.dp)
          .navigationBarsPadding()
          .fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(Modifier.weight(1F))

      NfcStatusIcon(status = model.status)

      AnimatedContent(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        targetState = model.text,
        transitionSpec = {
          fadeIn(animationSpec = tween(durationMillis = 500)) togetherWith
            fadeOut(animationSpec = tween(durationMillis = 500))
        },
        contentAlignment = Center,
        label = "NfcStatusLabelAnimation"
      ) { text ->
        NfcStatusLabel(Modifier.align(CenterHorizontally), text)
      }

      Spacer(Modifier.weight(1F))
      Button(
        text = "Cancel",
        modifier = Modifier.alpha(cancelButtonAlpha),
        treatment = Translucent,
        size = Footer,
        onClick = StandardClick {
          when (val status = model.status) {
            is Searching -> status.onCancel()
            is Connected -> status.onCancel()
            is Success -> Unit
          }
        }
      )
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
internal fun NfcScreenInternalV2(
  model: NfcBodyModel,
  modifier: Modifier = Modifier,
) {
  NfcProgressScreenAndroidLayoutV2(
    modifier = modifier,
    onCancel =
      when (val status = model.status) {
        is Searching -> status.onCancel
        is Connected -> status.onCancel
        is Success -> null
      },
    topContent = {
      if (model.status !is Success && model.onHelpClick != null) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .statusBarsPadding()
        ) {
          Toolbar(
            modifier =
              Modifier
                .padding(horizontal = ANDROID_NFC_TOOLBAR_HORIZONTAL_PADDING)
                .fillMaxWidth(),
            trailingContent = {
              ToolbarAccessory(
                model =
                  ToolbarAccessoryModel.IconAccessory(
                    model =
                      IconButtonModel(
                        iconModel =
                          IconModel(
                            icon = Icon.SmallIconQuestionNoOutline,
                            iconSize = IconSize.Accessory,
                            iconBackgroundType =
                              IconBackgroundType.Circle(
                                circleSize = IconSize.Regular
                              )
                          ),
                        testTag = "nfc-help",
                        onClick = StandardClick(model.onHelpClick)
                      )
                  )
              )
            }
          )
        }
      }
    },
    statusContent = {
      NfcStatusIndicatorV2(status = model.status)

      AnimatedContent(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        targetState = model.text,
        transitionSpec = {
          fadeIn(animationSpec = tween(durationMillis = 500)) togetherWith
            fadeOut(animationSpec = tween(durationMillis = 500))
        },
        contentAlignment = Center,
        label = "NfcStatusLabelAnimation"
      ) { text ->
        NfcStatusLabel(Modifier.align(CenterHorizontally), text)
      }
    }
  )
}

@Composable
internal fun NfcScreenInternalIos(
  model: NfcBodyModel,
  modifier: Modifier = Modifier,
) {
  val designSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current
  val theme = LocalTheme.current
  val videoTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + IosNfcVideoTopSpacing

  model.status.whenInProgress { onCancel ->
    BackHandler {
      onCancel()
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    if (!model.showNativeSheetOnIos) {
      NfcProgressScreenIosLayout(
        modifier = Modifier.matchParentSize(),
        hardwareType = model.hardwareType,
        backgroundColor = WalletTheme.colors.background,
        statusTopPadding = 40.dp,
        showDefaultHardwareBackground = false
      ) {
        NfcStatusLabel(
          text = model.text,
          labelType = LabelType.Body2Regular,
          textColor =
            when (model.status) {
              is Success -> WalletTheme.colors.foreground
              is Searching, is Connected -> WalletTheme.colors.foreground60
            }
        )
      }
      return@Box
    }

    // Select video and placeholder based on hardware type
    val (heroVideo, backgroundDrawable) = when (model.hardwareType) {
      HardwareType.W1 -> IosNfcHeroVideo.StandardW1 to Res.drawable.ios_nfc_background_w1
      HardwareType.W3 -> IosNfcHeroVideo.Standard to Res.drawable.ios_nfc_background_standard
    }

    NfcIosBackgroundLayout(
      modifier = Modifier.matchParentSize(),
      backgroundPainter = if (designSystemV2Enabled) painterResource(backgroundDrawable) else null,
      backgroundVideoResourcePath =
        if (designSystemV2Enabled) {
          iosNfcHeroVideoResource(heroVideo, theme)
        } else {
          null
        },
      backgroundVideoIsLooping = !designSystemV2Enabled,
      backgroundVideoTopPadding = if (designSystemV2Enabled) videoTopPadding else 0.dp,
      backgroundTopPadding = if (designSystemV2Enabled) videoTopPadding else 200.dp
    ) {}
  }
}

private inline fun NfcBodyModel.Status.whenInProgress(onBack: (() -> Unit) -> Unit) {
  when (this) {
    is Searching -> onBack(onCancel)
    is Connected -> onBack(onCancel)
    is Success -> Unit
  }
}

@Composable
private fun NfcStatusIcon(status: NfcBodyModel.Status) {
  val density = LocalDensity.current.density
  val designSystemV2Enabled = LocalDesignSystemUpdatesEnabled.current

  Box(
    contentAlignment = Center,
    modifier =
      Modifier
        .padding(4.dp)
        .size(100.dp)
        .wrapContentSize(unbounded = true)
  ) {
    if (!designSystemV2Enabled && isBlurSupported()) {
      val blueBackgroundAlpha: Float by animateFloatAsState(
        targetValue =
          when (status) {
            is Connected -> 1f
            is Searching, Success -> 0f
          },
        label = "blueBackgroundAlphaAnimation"
      )

      Box(
        modifier =
          Modifier
            .size(50.dp * density)
            .blur(radius = 45.dp * density, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .background(
              color =
                Color(0xff1f60B8)
                  .copy(alpha = blueBackgroundAlpha),
              shape = RoundedCornerShape(size = 50.dp * density)
            )
      )
    }

    val circleStrokeWidth: Float by animateFloatAsState(
      targetValue =
        when (status) {
          is Connected -> if (status.showProgressSpinner) 1.5f * density else 5f * density
          is Searching, Success -> 4f * density
        },
      label = "circleStrokeWidthAnimation"
    )

    val circleRadius: Float by animateFloatAsState(
      targetValue =
        when (status) {
          is Connected -> 50f * density
          is Searching, Success -> 40f * density
        },
      label = "circleRadiusAnimation"
    )

    val imageSize: Dp by animateDpAsState(
      targetValue =
        when (status) {
          is Connected -> 48.dp
          is Searching, Success -> 38.dp
        },
      label = "imageSizeAnimation"
    )

    val circleAlpha: Float by animateFloatAsState(
      targetValue =
        when (status) {
          is Searching -> 0.2f
          Success -> 1f
          is Connected -> if (status.showProgressSpinner) .2f else 1f
        },
      label = "circleAlphaAnimation"
    )

    val successAnimationComposition by rememberLottieComposition {
      LottieCompositionSpec.JsonString(
        Res.readBytes("files/success.json").decodeToString()
      )
    }
    val successAnimationPainter = rememberLottiePainter(
      composition = successAnimationComposition,
      iterations = 1
    )

    if (status is Connected && status.showProgressSpinner) {
      val circleSize: Float by animateFloatAsState(
        targetValue = 37f * density,
        label = "circleSizeAnimation"
      )

      IndeterminateCircularProgress(
        indicatorColor = WalletTheme.colors.nfcBlue,
        backgroundColor = WalletTheme.colors.foreground.copy(circleAlpha),
        strokeWidth = circleStrokeWidth,
        size = circleSize
      )
    } else {
      val foregroundColor = WalletTheme.colors.foreground
      Canvas(
        modifier = Modifier.size(100.dp),
        onDraw = {
          drawCircle(
            color = foregroundColor,
            radius = circleRadius,
            alpha = circleAlpha,
            style = Stroke(circleStrokeWidth)
          )
        }
      )

      AnimatedVisibility(
        visible = status !is Success,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Image(
          alignment = Center,
          modifier = Modifier.size(imageSize),
          painter = painterResource(Res.drawable.android_nfc_tap),
          contentDescription = "",
          colorFilter = ColorFilter.tint(WalletTheme.colors.foreground)
        )
      }

      AnimatedVisibility(
        visible = status is Success,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Image(
          painter = successAnimationPainter,
          contentDescription = null,
          modifier = Modifier.size(68.dp),
          colorFilter = ColorFilter.tint(WalletTheme.colors.foreground)
        )
      }
    }
  }
}

@Composable
private fun NfcStatusIndicatorV2(status: NfcBodyModel.Status) {
  NfcStatusIcon(status = status)
}

@Composable
private fun IndeterminateCircularProgress(
  modifier: Modifier = Modifier,
  indicatorModifier: Modifier = Modifier,
  indicatorColor: Color,
  backgroundColor: Color,
  strokeWidth: Float,
  size: Float,
) {
  Box(
    modifier = modifier,
    contentAlignment = Center
  ) {
    IndeterminateCircularProgressIndicator(
      modifier = indicatorModifier,
      indicatorColor = indicatorColor,
      trackColor = backgroundColor,
      strokeWidth = strokeWidth.dp,
      size = size.dp
    )
  }
}

@Composable
private fun NfcStatusLabel(
  modifier: Modifier = Modifier,
  text: String,
) {
  Label(
    text = text,
    modifier = modifier,
    style =
      WalletTheme.labelStyle(
        type = LabelType.Title3,
        treatment = Primary,
        alignment = TextAlign.Center
      ).copy(color = WalletTheme.colors.foreground)
  )
}
