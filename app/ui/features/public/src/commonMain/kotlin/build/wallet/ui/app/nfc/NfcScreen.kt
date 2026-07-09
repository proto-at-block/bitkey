package build.wallet.ui.app.nfc

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
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
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.progress.IndeterminateCircularProgressIndicator
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.toolbar.ToolbarAccessory
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.system.BackHandler
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.painterResource

@Composable
fun NfcScreen(
  modifier: Modifier = Modifier,
  model: NfcBodyModel,
) {
  KeepScreenOn()
  val devicePlatform = LocalDeviceInfo.current.devicePlatform

  when (devicePlatform) {
    DevicePlatform.IOS -> {
      NfcScreenInternalIos(model = model, modifier = modifier)
    }
    DevicePlatform.Android,
    DevicePlatform.Jvm,
    -> {
      NfcScreenInternalAndroid(model = model, modifier = modifier)
    }
  }
}

@Composable
internal fun NfcScreenInternalAndroid(
  model: NfcBodyModel,
  modifier: Modifier = Modifier,
) {
  NfcProgressScreenAndroidLayout(
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
                            icon = Icon.Question,
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
      NfcStatusIndicator(status = model.status)

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
        NfcScreenStatusLabel(
          modifier = Modifier.align(CenterHorizontally),
          text = text
        )
      }
    }
  )
}

@Composable
internal fun NfcScreenInternalIos(
  model: NfcBodyModel,
  modifier: Modifier = Modifier,
) {
  model.status.whenInProgress { onCancel ->
    BackHandler {
      onCancel()
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    if (!model.showNativeSheetOnIos) {
      FwupSystemThemedContent(followIosSystemTheme = true) {
        NfcProgressScreenIosLayout(
          modifier = Modifier.matchParentSize(),
          hardwareType = model.hardwareType,
          backgroundColor = WalletTheme.colors.background,
          statusTopPadding = 40.dp,
          showDefaultHardwareBackground = false
        ) {
          NfcIosStatusContent(
            status = model.status,
            text = model.text
          )
        }
      }
      return@Box
    }

    val backgroundDrawable = when (model.hardwareType) {
      HardwareType.W1 -> Res.drawable.ios_nfc_background_w1
      HardwareType.W3 -> Res.drawable.ios_nfc_background_standard
    }

    NfcIosBackgroundLayout(
      modifier = Modifier.matchParentSize(),
      backgroundPainter = painterResource(backgroundDrawable),
      backgroundTopPadding = 200.dp
    ) {}
  }
}

@Composable
private fun NfcIosStatusContent(
  status: NfcBodyModel.Status,
  text: String,
) {
  val headline = nfcIosStatusHeadline(status = status, text = text)
  val subtitle = nfcIosStatusSubtitle(status = status, text = text)

  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    NfcStatusLabel(
      text = headline,
      labelType = if (status is Success) LabelType.Body2Regular else LabelType.Body2MonoCaps,
      textColor = WalletTheme.colors.foreground
    )

    subtitle?.let {
      NfcStatusLabel(
        text = it,
        labelType = LabelType.Body2Regular,
        textColor = WalletTheme.colors.foreground60
      )
    }
  }
}

internal fun nfcIosStatusHeadline(
  status: NfcBodyModel.Status,
  text: String,
): String {
  return when (status) {
    is Searching -> "Ready"
    is Connected -> if (status.showProgressSpinner) "Keep holding..." else "Connected"
    is Success -> text
  }
}

internal fun nfcIosStatusSubtitle(
  status: NfcBodyModel.Status,
  text: String,
): String? {
  return when (status) {
    is Searching -> text
    is Connected ->
      when {
        status.showProgressSpinner -> text
        text != "Connected" -> text
        else -> null
      }

    is Success -> null
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

  Box(
    contentAlignment = Center,
    modifier =
      Modifier
        .padding(4.dp)
        .size(100.dp)
        .wrapContentSize(unbounded = true)
  ) {
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
private fun NfcStatusIndicator(status: NfcBodyModel.Status) {
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
private fun NfcScreenStatusLabel(
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
