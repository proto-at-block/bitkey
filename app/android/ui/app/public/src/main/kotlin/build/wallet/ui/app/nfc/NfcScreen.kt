package build.wallet.ui.app.nfc

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.android.ui.core.R
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Connected
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Searching
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Success
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.button.ButtonModel.Treatment.Translucent
import build.wallet.ui.system.KeepScreenOn
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun NfcScreen(
  model: NfcBodyModel,
  modifier: Modifier = Modifier,
) {
  KeepScreenOn()
  NfcScreenInternal(model = model, modifier = modifier)
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun NfcScreenInternal(
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
        modifier = Modifier.fillMaxWidth(),
        targetState = model.text,
        transitionSpec = {
          fadeIn(animationSpec = tween(durationMillis = 500)) with
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
        onClick =
          Click.StandardClick {
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
    contentAlignment = Alignment.Center,
    modifier =
      Modifier
        .padding(4.dp)
        .size(100.dp)
        // Allow the content to be unbounded so the blue
        // background blur can extend beyond the bounds
        .wrapContentSize(unbounded = true)
  ) {
    val circleAlpha: Float by animateFloatAsState(
      targetValue =
        when (status) {
          is Searching -> 0.2f
          is Connected, Success -> 1f
        },
      label = "circleAlphaAnimation"
    )

    val circleStrokeWidth: Float by animateFloatAsState(
      targetValue =
        when (status) {
          is Connected -> 5f * density
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

    val blueBackgroundAlpha: Float by animateFloatAsState(
      targetValue =
        when (status) {
          is Connected -> 1f
          is Searching, Success -> 0f
        },
      label = "blueBackgroundAlphaAnimation"
    )

    val successAnimationComposition by rememberLottieComposition(
      LottieCompositionSpec.RawRes(R.raw.success)
    )

    // Blue blurred background, shown in Connected status
    // Only add the background blurs in versions that support [blur]
    if (Build.VERSION.SDK_INT > 30) {
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
        alignment = Alignment.Center,
        modifier = Modifier.size(imageSize),
        painter = painterResource(R.drawable.android_nfc_tap),
        contentDescription = ""
      )
    }

    // Success animation
    AnimatedVisibility(
      visible = status is Success,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      LottieAnimation(
        composition = successAnimationComposition,
        iterations = 1
      )
    }
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

@Preview
@Composable
internal fun NfcScreenSearchingPreview() {
  PreviewWalletTheme {
    NfcScreenInternal(
      model =
        NfcBodyModel(
          text = "Hold device here behind phone",
          status = Searching { },
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview
@Composable
internal fun NfcScreenConnectedPreview() {
  PreviewWalletTheme {
    NfcScreenInternal(
      model =
        NfcBodyModel(
          text = "Hold device here behind phone",
          status = Connected { },
          eventTrackerScreenInfo = null
        )
    )
  }
}

@Preview
@Composable
internal fun NfcScreenSuccessPreview() {
  PreviewWalletTheme {
    NfcScreenInternal(
      model =
        NfcBodyModel(
          text = "Success",
          status = Success,
          eventTrackerScreenInfo = null
        )
    )
  }
}
