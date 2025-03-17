package build.wallet.ui.components.label

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.WalletTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Creates a shimmer effect on the Composable by applying
 * an infinite transition on the [alpha] property.
 */
fun Modifier.shimmer(): Modifier {
  return composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
      initialValue = 1f,
      targetValue = 0.6f,
      animationSpec = infiniteRepeatable(
        repeatMode = RepeatMode.Reverse,
        animation = tween(
          durationMillis = 400,
          delayMillis = 2000,
          easing = Ease
        )
      ),
      label = "shimmer"
    )
    alpha(alpha)
  }
}

/**
 * Apply a scrim over the bounds of the Composable content while
 * [isLoading] is true, applies an optional shimmer effect by default.
 *
 * @param maskColor A color to apply over the Composable, typically the surface color.
 */
fun Modifier.loadingScrim(
  isLoading: Boolean,
  isShimmering: Boolean = true,
  maskColor: Color? = null,
): Modifier {
  return composed {
    val resolvedMaskColor = maskColor ?: WalletTheme.colors.background
    val loadingColor = WalletTheme.colors.loadingBackground
    var targetColor by remember {
      mutableStateOf(
        loadingColor.copy(alpha = if (isLoading) 1f else 0f)
      )
    }
    val shimmerSpec = tween<Color>(durationMillis = 1000, easing = Ease)
    val transitionSpec = tween<Color>(durationMillis = 300, easing = Ease)
    val color by animateColorAsState(
      label = "loading-scrim-shimmer-color",
      targetValue = targetColor,
      animationSpec = shimmerSpec
    )
    val disabledColor by animateColorAsState(
      label = "loading-scrim-disabled-color",
      targetValue = if (isLoading) loadingColor else loadingColor.copy(alpha = 0f),
      animationSpec = transitionSpec
    )
    val maskColorAnimated by animateColorAsState(
      label = "loading-scrim-mask",
      targetValue = if (isLoading) resolvedMaskColor else resolvedMaskColor.copy(alpha = 0f),
      animationSpec = transitionSpec
    )
    LaunchedEffect(isLoading, isShimmering) {
      targetColor = loadingColor.copy(
        when {
          isLoading -> if (isShimmering) 0.6f else 1f
          else -> 0f
        }
      )
      while (isLoading && isShimmering) {
        targetColor = when (targetColor.alpha) {
          1f -> color.copy(alpha = 0.6f)
          else -> color.copy(alpha = 1f)
        }
        delay(1.seconds)
      }
    }
    drawWithContent {
      drawContent()
      drawIntoCanvas {
        val radius = 8.dp.toPx()
        val cornerRadius = CornerRadius(radius, radius)
        drawRoundRect(
          color = maskColorAnimated,
          cornerRadius = cornerRadius,
          size = size
        )
        drawRoundRect(
          color = if (isLoading) color else disabledColor,
          cornerRadius = CornerRadius(radius, radius),
          size = size
        )
      }
    }
  }
}
