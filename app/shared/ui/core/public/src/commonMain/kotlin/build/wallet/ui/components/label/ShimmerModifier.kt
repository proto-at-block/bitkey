package build.wallet.ui.components.label

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Ease
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.WalletTheme

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
  maskColor: Color = Color.White,
): Modifier {
  return composed {
    val loadingColor = WalletTheme.colors.loadingBackground
    val transition = rememberInfiniteTransition(label = "loading-scrim")
    val color by transition.animateColor(
      label = "loading-scrim-color",
      initialValue = loadingColor.copy(if (isLoading) 1f else 0f),
      targetValue = loadingColor.copy(
        when {
          isLoading -> if (isShimmering) 0.6f else 1f
          else -> 0f
        }
      ),
      animationSpec = infiniteRepeatable(
        repeatMode = RepeatMode.Reverse,
        animation = tween(
          durationMillis = 500,
          delayMillis = 1000,
          easing = Ease
        )
      )
    )
    val maskColorAnimated by animateColorAsState(
      label = "loading-scrim-mask",
      targetValue = if (isLoading) maskColor else maskColor.copy(alpha = 0f)
    )
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
          color = color,
          cornerRadius = cornerRadius,
          size = size
        )
      }
    }
  }
}
