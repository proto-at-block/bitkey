package build.wallet.ui.app.nfc

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import bitkey.shared.ui_core_public.generated.resources.Res
import bitkey.shared.ui_core_public.generated.resources.android_nfc_tap
import bitkey.shared.ui_core_public.generated.resources.ios_nfc_tap
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.TimerDirection.Clockwise
import build.wallet.statemachine.fwup.FwupNfcBodyModel
import build.wallet.statemachine.fwup.FwupNfcBodyModel.Status.*
import build.wallet.ui.app.LocalDeviceInfo
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.progress.CircularProgressIndicator
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Translucent
import build.wallet.ui.system.BackHandler
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.system.isBlurSupported
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.painterResource

@Composable
fun FwupNfcScreen(
  modifier: Modifier = Modifier,
  model: FwupNfcBodyModel,
) {
  KeepScreenOn()
  when (LocalDeviceInfo.current.devicePlatform) {
    DevicePlatform.IOS -> {
      FwupNfcScreenInternalIos(model = model, modifier = modifier)
    }
    else -> {
      FwupNfcScreenInternal(model = model, modifier = modifier)
    }
  }
}

@Composable
internal fun FwupNfcScreenInternalIos(
  model: FwupNfcBodyModel,
  modifier: Modifier = Modifier,
) {
  val nfcBlue = WalletTheme.colors.nfcBlue.copy(alpha = 0.6f)
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(Color.Black, WalletTheme.colors.nfcBlue)
        )
      ),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Spacer(modifier = Modifier.height(48.dp))
    Box {
      Spacer(
        modifier = Modifier
          .matchParentSize()
          .drawBehind {
            drawRect(
              brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, nfcBlue, Color.Transparent),
                startY = size.height,
                endY = 0f
              )
            )
          }
          .blur(28.dp, BlurredEdgeTreatment.Rectangle)
      )
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Image(
          alignment = Alignment.Center,
          painter = painterResource(Res.drawable.ios_nfc_tap),
          contentDescription = ""
        )
        when (model.status) {
          is Searching -> {
            StatusLabel(
              text = "Ready to Update",
              labelType = LabelType.Title1
            )
            StatusLabel(
              text = "Hold device to phone",
              labelType = LabelType.Body2Regular
            )
          }
          is InProgress -> {
            StatusLabel(
              text = "Updating...",
              labelType = LabelType.Title1
            )
            StatusLabel(
              text = "Continue holding to phone",
              labelType = LabelType.Body2Regular
            )
          }
          is LostConnection,
          is Success,
          -> Unit
        }
      }
    }
  }
}

@Composable
fun FwupNfcScreenInternal(
  model: FwupNfcBodyModel,
  modifier: Modifier = Modifier,
) {
  BackHandler {
    model.onCancel?.invoke()
  }

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

      StatusIndicator(status = model.status)

      Box(modifier = Modifier.weight(1F)) {
        StatusLabel(
          text = model.status.text,
          modifier = Modifier.align(Alignment.TopCenter)
        )
      }

      Button(
        text = "Cancel",
        modifier =
          Modifier.alpha(
            when (model.onCancel) {
              null -> 0f
              else -> 1f
            }
          ),
        treatment = Translucent,
        size = Footer,
        onClick = StandardClick {
          model.onCancel?.invoke()
        }
      )
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun StatusIndicator(status: FwupNfcBodyModel.Status) {
  // The density of the pixels on the screen, to make sure
  // animations and drawings scale correctly on all screens
  val density = LocalDensity.current.density

  val circleBackgroundColor: Color by animateColorAsState(
    targetValue =
      when (status) {
        is Searching, is Success -> Color.White
        is InProgress, is LostConnection -> Color.White.copy(alpha = 0.1f)
      },
    label = "circleBackgroundColorAnimation"
  )

  val circleStrokeWidth: Float by animateFloatAsState(
    targetValue =
      when (status) {
        is Searching, is Success -> 1.5f * density
        is InProgress, is LostConnection -> 4f * density
      },
    label = "circleStrokeWidthAnimation"
  )

  val circleSize: Float by animateFloatAsState(
    targetValue =
      when (status) {
        is Searching, is Success -> 33f * density
        is InProgress, is LostConnection -> 45f * density
      },
    label = "circleStrokeWidthAnimation"
  )

  val backgroundBlurColor: Color by animateColorAsState(
    targetValue =
      when (status) {
        is InProgress -> Color(0xff1f60B8)
        is LostConnection -> Color(0xff341509)
        is Searching, is Success -> Color.Transparent
      },
    label = "backgroundBlurColorAnimation"
  )

  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier
        .padding(4.dp)
        .size(140.dp)
        // Allow the content to be unbounded so the blue
        // background blur can extend beyond the bounds
        .wrapContentSize(unbounded = true)
  ) {
    // Blue blurred background, shown in InProgress and LostConnection status
    // Only add the background blurs in versions that support [blur]
    if (isBlurSupported()) {
      Box(
        modifier =
          Modifier
            .size(50.dp * density)
            .blur(radius = 45.dp * density, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .background(
              color = backgroundBlurColor,
              shape = RoundedCornerShape(size = 50.dp * density)
            )
      )
    }

    CircularProgress(
      strokeWidth = circleStrokeWidth,
      size = circleSize,
      backgroundColor = circleBackgroundColor,
      progressPercentage =
        when (status) {
          is Searching, is Success -> 0f
          is InProgress -> status.progressPercentage
          is LostConnection -> status.progressPercentage
        },
      indicatorColor =
        when (status) {
          is Searching, is Success -> Color.White
          is InProgress -> WalletTheme.colors.nfcBlue
          is LostConnection -> WalletTheme.colors.warningForeground
        },
      indicatorModifier =
        when (status) {
          is Searching, is Success -> Modifier
          is InProgress -> Modifier.indicatorInProgressBackground()
          is LostConnection -> Modifier.indicatorWarningBackground()
        }
    ) {
      // Fade animation between status text changes
      AnimatedContent(
        modifier = Modifier.fillMaxWidth(),
        targetState = status,
        transitionSpec = {
          // Don't animate InProgress -> InProgress, the text will be
          // cross-faded separately
          if (initialState is InProgress && targetState is InProgress) {
            EnterTransition.None togetherWith ExitTransition.None
          } else {
            fadeIn(animationSpec = tween(durationMillis = 500)) togetherWith
              fadeOut(animationSpec = tween(durationMillis = 500))
          }
        },
        contentAlignment = Alignment.Center,
        label = "StatusIndicatorAnimation"
      ) { status ->
        when (status) {
          is Searching ->
            NfcIcon()

          is InProgress ->
            ProgressPercentageLabel(
              progressText = status.progressText,
              progressLabelType = LabelType.Title1
            )

          is LostConnection ->
            ProgressPercentageLabel(
              progressText = "!",
              progressLabelType = LabelType.Display2
            )

          is Success ->
            SuccessAnimation()
        }
      }
    }
  }
}

@Suppress("FunctionName")
@Composable
private fun StatusLabel(
  text: String,
  modifier: Modifier = Modifier,
  labelType: LabelType = LabelType.Title2,
) {
  // Fade animation between status text changes
  AnimatedContent(
    modifier = Modifier.fillMaxWidth(),
    targetState = text,
    transitionSpec = {
      fadeIn(animationSpec = tween(durationMillis = 500)) togetherWith
        fadeOut(animationSpec = tween(durationMillis = 500))
    },
    contentAlignment = Alignment.Center,
    label = "FwupNfcStatusLabelAnimation"
  ) { currentText ->
    Label(
      text = currentText,
      modifier = modifier,
      style =
        WalletTheme.labelStyle(
          type = labelType,
          treatment = Primary,
          alignment = TextAlign.Center
        ).copy(color = WalletTheme.colors.translucentForeground)
    )
  }
}

@Composable
private fun NfcIcon() {
  Image(
    alignment = Alignment.Center,
    modifier = Modifier.size(38.dp),
    painter = painterResource(Res.drawable.android_nfc_tap),
    contentDescription = ""
  )
}

@Composable
private fun ProgressPercentageLabel(
  progressText: String,
  progressLabelType: LabelType,
) {
  Label(
    modifier = Modifier.fillMaxWidth(),
    text =
      buildAnnotatedString {
        withStyle(style = SpanStyle(fontFeatureSettings = "tnum")) {
          append(progressText)
        }
      },
    style =
      WalletTheme.labelStyle(
        type = progressLabelType,
        treatment = Primary,
        alignment = TextAlign.Center
      ).copy(color = WalletTheme.colors.translucentForeground)
  )
}

@Composable
private fun SuccessAnimation() {
  val successAnimationComposition by rememberLottieComposition {
    LottieCompositionSpec.JsonString(
      Res.readBytes("files/success.json").decodeToString()
    )
  }
  val successAnimationPainter = rememberLottiePainter(
    composition = successAnimationComposition,
    iterations = 1
  )
  Image(
    modifier = Modifier.size(68.dp),
    painter = successAnimationPainter,
    contentDescription = null
  )
}

@Composable
private fun CircularProgress(
  modifier: Modifier = Modifier,
  indicatorModifier: Modifier = Modifier,
  progressPercentage: Float,
  indicatorColor: Color,
  backgroundColor: Color,
  strokeWidth: Float,
  size: Float,
  content: @Composable () -> Unit,
) {
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    CircularProgressIndicator(
      modifier = indicatorModifier,
      size = size.dp,
      progress = progressPercentage,
      direction = Clockwise,
      indicatorColor = indicatorColor,
      backgroundColor = backgroundColor,
      strokeWidth = strokeWidth.dp
    )

    content()
  }
}

private fun Modifier.indicatorInProgressBackground() =
  clip(CircleShape).background(Color.White.copy(alpha = 0.1F))

private fun Modifier.indicatorWarningBackground() =
  composed {
    clip(CircleShape)
      .background(
        Brush.radialGradient(
          colorStops =
            arrayOf(
              0f to WalletTheme.colors.warningForeground.copy(alpha = 0.2F),
              1f to Color.Black.copy(alpha = 0.2F)
            )
        )
      )
  }
