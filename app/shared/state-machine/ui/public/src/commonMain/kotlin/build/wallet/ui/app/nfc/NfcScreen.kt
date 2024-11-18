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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bitkey.shared.ui_core_public.generated.resources.Res
import bitkey.shared.ui_core_public.generated.resources.android_nfc_tap
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel.Status.*
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.progress.IndeterminateCircularProgressIndicator
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
fun NfcScreen(
  modifier: Modifier = Modifier,
  model: NfcBodyModel,
) {
  KeepScreenOn()
  NfcScreenInternal(model = model, modifier = modifier)
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

      // Fade animation between status text changes
      AnimatedContent(
        modifier = Modifier.fillMaxWidth()
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

private inline fun NfcBodyModel.Status.whenInProgress(onBack: (() -> Unit) -> Unit) {
  when (this) {
    is Searching -> onBack(onCancel)
    is Connected -> onBack(onCancel)
    is Success -> Unit
  }
}

@Composable
private fun NfcStatusIcon(status: NfcBodyModel.Status) {
  // The density of the pixels on the screen, to make sure
  // animations and drawings scale correctly on all screens
  val density = LocalDensity.current.density

  Box(
    contentAlignment = Center,
    modifier =
      Modifier
        .padding(4.dp)
        .size(100.dp)
        // Allow the content to be unbounded so the blue
        // background blur can extend beyond the bounds
        .wrapContentSize(unbounded = true)
  ) {
    // Blue blurred background, shown in Connected status
    // Only add the background blurs in versions that support [blur]
    if (isBlurSupported()) {
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
        backgroundColor = Color.White.copy(circleAlpha),
        strokeWidth = circleStrokeWidth,
        size = circleSize
      )
    } else {
      // Circle stroke
      Canvas(
        modifier =
          Modifier
            .size(100.dp),
        onDraw = {
          drawCircle(
            color = Color.White,
            radius = circleRadius,
            alpha = circleAlpha,
            style = Stroke(circleStrokeWidth)
          )
        }
      )

      // NFC icon
      AnimatedVisibility(
        visible = status !is Success,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Image(
          alignment = Center,
          modifier = Modifier.size(imageSize),
          painter = painterResource(Res.drawable.android_nfc_tap),
          contentDescription = ""
        )
      }

      // Success animation
      AnimatedVisibility(
        visible = status is Success,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Image(
          painter = successAnimationPainter,
          contentDescription = null,
          modifier = Modifier.size(68.dp)
        )
      }
    }
  }
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
        type = LabelType.Title2,
        treatment = Primary,
        alignment = TextAlign.Center
      ).copy(color = WalletTheme.colors.translucentForeground)
  )
}
