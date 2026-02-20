package build.wallet.ui.app.nfc

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.android_nfc_tap
import build.wallet.statemachine.core.TimerDirection.Clockwise
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.progress.CircularProgressIndicator
import build.wallet.ui.system.isBlurSupported
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.jetbrains.compose.resources.painterResource

// Shared components for NFC progress screens (firmware update, transaction signing, etc.).
// These components provide consistent UI patterns for progress-based NFC operations.

/**
 * Generic NFC status indicator with circular progress and animated content.
 *
 * @param statusState Interface providing status information for animations
 * @param content Composable content to display inside the circle
 */
@Composable
fun <T> NfcProgressStatusIndicator(
  statusState: NfcProgressStatusState<T>,
  content: @Composable (T) -> Unit,
) {
  val density = LocalDensity.current.density

  val circleBackgroundColor: Color by animateColorAsState(
    targetValue =
      when {
        statusState.isIdle || statusState.isSuccess -> Color.White
        else -> Color.White.copy(alpha = 0.1f)
      },
    label = "circleBackgroundColorAnimation"
  )

  val circleStrokeWidth: Float by animateFloatAsState(
    targetValue =
      when {
        statusState.isIdle || statusState.isSuccess -> 1.5f * density
        else -> 4f * density
      },
    label = "circleStrokeWidthAnimation"
  )

  val circleSize: Float by animateFloatAsState(
    targetValue =
      when {
        statusState.isIdle || statusState.isSuccess -> 33f * density
        else -> 45f * density
      },
    label = "circleSizeAnimation"
  )

  val backgroundBlurColor: Color by animateColorAsState(
    targetValue =
      when {
        statusState.isInProgress -> Color(0xff1f60B8)
        statusState.isError -> Color(0xff341509)
        else -> Color.Transparent
      },
    label = "backgroundBlurColorAnimation"
  )

  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier
        .padding(4.dp)
        .size(140.dp)
        .wrapContentSize(unbounded = true)
  ) {
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

    NfcCircularProgress(
      strokeWidth = circleStrokeWidth,
      size = circleSize,
      backgroundColor = circleBackgroundColor,
      progressPercentage = statusState.progress,
      indicatorColor =
        when {
          statusState.isIdle || statusState.isSuccess -> Color.White
          statusState.isInProgress -> WalletTheme.colors.nfcBlue
          statusState.isError -> WalletTheme.colors.warningForeground
          else -> Color.White
        },
      indicatorModifier =
        when {
          statusState.isIdle || statusState.isSuccess -> Modifier
          statusState.isInProgress -> Modifier.nfcIndicatorInProgressBackground()
          statusState.isError -> Modifier.nfcIndicatorWarningBackground()
          else -> Modifier
        }
    ) {
      AnimatedContent(
        modifier = Modifier.fillMaxWidth(),
        targetState = statusState.status,
        transitionSpec = {
          // Prevent animation for progress updates within the same state
          if (statusState.shouldSkipTransition(initialState, targetState)) {
            androidx.compose.animation.EnterTransition.None togetherWith ExitTransition.None
          } else {
            fadeIn(animationSpec = tween(durationMillis = 500)) togetherWith
              fadeOut(animationSpec = tween(durationMillis = 500))
          }
        },
        contentAlignment = Alignment.Center,
        label = "NfcStatusIndicatorAnimation"
      ) { status ->
        content(status)
      }
    }
  }
}

/**
 * Interface for providing status information to [NfcProgressStatusIndicator].
 * Implement this for your specific status type (e.g., FwupStatus, SignTransactionStatus).
 */
interface NfcProgressStatusState<T> {
  /** Current status value */
  val status: T

  /** Progress from 0.0 to 1.0 */
  val progress: Float

  /** True if waiting/searching for device */
  val isIdle: Boolean

  /** True if actively processing/transferring */
  val isInProgress: Boolean

  /** True if operation succeeded */
  val isSuccess: Boolean

  /** True if connection lost or error occurred */
  val isError: Boolean

  /** Whether to skip animation between these two states */
  fun shouldSkipTransition(
    old: T,
    new: T,
  ): Boolean
}

/**
 * Animated status label for NFC screens.
 */
@Suppress("FunctionName")
@Composable
fun NfcStatusLabel(
  text: String,
  modifier: Modifier = Modifier,
  labelType: LabelType = LabelType.Title2,
  animationLabel: String = "NfcStatusLabelAnimation",
) {
  AnimatedContent(
    modifier = Modifier.fillMaxWidth(),
    targetState = text,
    transitionSpec = {
      fadeIn(animationSpec = tween(durationMillis = 500)) togetherWith
        fadeOut(animationSpec = tween(durationMillis = 500))
    },
    contentAlignment = Alignment.Center,
    label = animationLabel
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

/**
 * NFC icon for displaying in status indicators.
 */
@Composable
fun NfcIcon() {
  Image(
    alignment = Alignment.Center,
    modifier = Modifier.size(38.dp),
    painter = painterResource(Res.drawable.android_nfc_tap),
    contentDescription = ""
  )
}

/**
 * Progress percentage label with tabular numbers.
 */
@Composable
fun NfcProgressPercentageLabel(
  progressText: String,
  progressLabelType: LabelType = LabelType.Title1,
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

/**
 * Success animation using Lottie.
 */
@Composable
fun NfcSuccessAnimation() {
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

/**
 * Circular progress indicator with content inside.
 */
@Composable
fun NfcCircularProgress(
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

/**
 * Modifier for in-progress indicator background.
 */
fun Modifier.nfcIndicatorInProgressBackground() =
  clip(CircleShape).background(Color.White.copy(alpha = 0.1F))

/**
 * Modifier for warning/error indicator background.
 */
fun Modifier.nfcIndicatorWarningBackground() =
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
