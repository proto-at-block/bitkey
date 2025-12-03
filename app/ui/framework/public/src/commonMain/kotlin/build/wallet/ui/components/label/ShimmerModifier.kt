package build.wallet.ui.components.label

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.WalletTheme

/**
 * Creates a shimmer effect on the Composable by applying
 * a gradient wipe animation.
 */
fun Modifier.shimmer(): Modifier {
  return composed {
    val backgroundColor = WalletTheme.colors.background
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(
          durationMillis = 2000,
          delayMillis = 1000,
          easing = EaseOut
        ),
        repeatMode = RepeatMode.Restart
      ),
      label = "shimmer-offset"
    )

    drawWithContent {
      drawContent()

      val gradientWidth = size.width / 2.5f
      val startX = -gradientWidth + (size.width + gradientWidth * 2) * shimmerOffset

      drawRect(
        brush = Brush.linearGradient(
          colors = listOf(
            backgroundColor.copy(alpha = 0f),
            backgroundColor.copy(alpha = 0.5f),
            backgroundColor.copy(alpha = 0f)
          ),
          start = Offset(startX, 0f),
          end = Offset(startX + gradientWidth, 0f)
        ),
        size = size
      )
    }
  }
}

fun Modifier.loadingScrim(
  isLoading: Boolean,
  isShimmering: Boolean = true,
  maskColor: Color? = null,
): Modifier {
  return composed {
    val backgroundColor = WalletTheme.colors.background
    val resolvedMaskColor = maskColor ?: WalletTheme.colors.background
    val loadingColor = WalletTheme.colors.loadingBackground

    val transitionSpec = tween<Color>(durationMillis = 300, easing = Ease)
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

    // Only create infinite transition when actually loading
    val shimmerOffset = if (isLoading && isShimmering) {
      val infiniteTransition = rememberInfiniteTransition(label = "shimmer-gradient")
      infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
          animation = tween(
            durationMillis = 1300,
            easing = LinearEasing
          ),
          repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-offset"
      ).value
    } else {
      0f
    }

    this
      .then(
        if (isLoading) {
          Modifier.semantics {
            invisibleToUser() // Hide from screen readers when loading
          }
        } else {
          Modifier
        }
      )
      .drawWithContent {
        drawContent()
        drawIntoCanvas {
          val radius = 32.dp.toPx()
          val cornerRadius = CornerRadius(radius, radius)

          // Draw mask background
          drawRoundRect(
            color = maskColorAnimated,
            cornerRadius = cornerRadius,
            size = size
          )

          // Draw loading background
          drawRoundRect(
            color = if (isLoading) loadingColor else disabledColor,
            cornerRadius = cornerRadius,
            size = size
          )

          // Draw gradient wipe if loading and shimmering
          if (isLoading && isShimmering) {
            val gradientWidth = size.width / 2.5f
            val startX = -gradientWidth + (size.width + gradientWidth * 2) * shimmerOffset

            drawRoundRect(
              brush = Brush.linearGradient(
                colors = listOf(
                  backgroundColor.copy(alpha = 0f),
                  backgroundColor.copy(alpha = 0.5f),
                  backgroundColor.copy(alpha = 0f)
                ),
                start = Offset(startX, 0f),
                end = Offset(startX + gradientWidth, 0f)
              ),
              cornerRadius = cornerRadius,
              size = size
            )
          }
        }
      }
  }
}
