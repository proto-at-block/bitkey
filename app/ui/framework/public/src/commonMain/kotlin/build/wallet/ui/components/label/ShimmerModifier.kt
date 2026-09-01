package build.wallet.ui.components.label

import androidx.compose.animation.core.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.dp
import build.wallet.ui.tokens.LocalColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Creates a shimmer effect on the Composable by applying
 * a gradient wipe animation.
 */
fun Modifier.shimmer(): Modifier {
  return this.then(ShimmerElement)
}

fun Modifier.loadingScrim(
  isLoading: Boolean,
  isShimmering: Boolean = true,
  maskColor: Color? = null,
  loadingColor: Color? = null,
): Modifier {
  return this.then(LoadingScrimElement(isLoading, isShimmering, maskColor, loadingColor))
}

private data object ShimmerElement : ModifierNodeElement<ShimmerNode>() {
  override fun create() = ShimmerNode()

  override fun update(node: ShimmerNode) = Unit
}

private class ShimmerNode :
  Modifier.Node(),
  DrawModifierNode,
  CompositionLocalConsumerModifierNode {
  private val shimmerOffset = Animatable(initialValue = 0f)

  override fun onAttach() {
    coroutineScope.launch {
      shimmerOffset.snapTo(0f)
      shimmerOffset.animateTo(
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
          animation = tween(
            durationMillis = 2000,
            delayMillis = 1000,
            easing = EaseOut
          ),
          repeatMode = RepeatMode.Restart
        )
      )
    }
  }

  override fun ContentDrawScope.draw() {
    drawContent()

    val backgroundColor = currentValueOf(LocalColors).background

    drawRect(
      brush = shimmerGradientBrush(backgroundColor, shimmerOffset.value),
      size = size
    )
  }
}

private data class LoadingScrimElement(
  val isLoading: Boolean,
  val isShimmering: Boolean,
  val maskColor: Color?,
  val loadingColor: Color?,
) : ModifierNodeElement<LoadingScrimNode>() {
  override fun create() = LoadingScrimNode(isLoading, isShimmering, maskColor, loadingColor)

  override fun update(node: LoadingScrimNode) {
    node.update(isLoading, isShimmering, maskColor, loadingColor)
  }
}

private class LoadingScrimNode(
  private var isLoading: Boolean,
  private var isShimmering: Boolean,
  private var maskColor: Color?,
  private var loadingColor: Color?,
) : Modifier.Node(),
  DrawModifierNode,
  SemanticsModifierNode,
  CompositionLocalConsumerModifierNode {
  /**
   * Visibility fraction of the scrim: 1f fully visible (loading), 0f hidden (loaded).
   *
   * The previous `animateColorAsState` targets only differed in alpha
   * (`color` vs `color.copy(alpha = 0f)`), so animating a single fraction with the
   * same spec and scaling the resolved colors' alpha at draw time is equivalent,
   * while letting theme colors be resolved fresh on every draw.
   */
  private val scrimAlpha = Animatable(initialValue = if (isLoading) 1f else 0f)
  private val shimmerOffset = Animatable(initialValue = 0f)
  private var shimmerJob: Job? = null

  override fun onAttach() {
    if (isLoading && isShimmering) {
      startShimmerAnimation()
    }
  }

  override fun onDetach() {
    stopShimmerAnimation()
  }

  fun update(
    isLoading: Boolean,
    isShimmering: Boolean,
    maskColor: Color?,
    loadingColor: Color?,
  ) {
    val wasLoading = this.isLoading
    val wasShimmering = this.isLoading && this.isShimmering
    this.isLoading = isLoading
    this.isShimmering = isShimmering
    this.maskColor = maskColor
    this.loadingColor = loadingColor

    if (isLoading != wasLoading) {
      coroutineScope.launch {
        scrimAlpha.animateTo(
          targetValue = if (isLoading) 1f else 0f,
          animationSpec = tween(durationMillis = 300, easing = Ease)
        )
      }
    }

    val isShimmeringNow = isLoading && isShimmering
    if (isShimmeringNow != wasShimmering) {
      if (isShimmeringNow) {
        startShimmerAnimation()
      } else {
        stopShimmerAnimation()
      }
    }
  }

  private fun startShimmerAnimation() {
    shimmerJob?.cancel()
    shimmerJob = coroutineScope.launch {
      shimmerOffset.snapTo(0f)
      shimmerOffset.animateTo(
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
          animation = tween(
            durationMillis = 1300,
            easing = LinearEasing
          ),
          repeatMode = RepeatMode.Restart
        )
      )
    }
  }

  private fun stopShimmerAnimation() {
    shimmerJob?.cancel()
    shimmerJob = null
  }

  override fun SemanticsPropertyReceiver.applySemantics() {
    if (isLoading) {
      hideFromAccessibility() // Hide from screen readers when loading
    }
  }

  override fun ContentDrawScope.draw() {
    drawContent()

    val colors = currentValueOf(LocalColors)
    val backgroundColor = colors.background
    val resolvedMaskColor = maskColor ?: colors.background
    val resolvedLoadingColor = loadingColor ?: colors.loadingBackground
    val scrimAlphaFraction = scrimAlpha.value

    val radius = 12.dp.toPx()
    val cornerRadius = CornerRadius(radius, radius)

    // Draw mask background
    drawRoundRect(
      color = resolvedMaskColor.copy(alpha = resolvedMaskColor.alpha * scrimAlphaFraction),
      cornerRadius = cornerRadius,
      size = size
    )

    // Draw loading background
    drawRoundRect(
      color = resolvedLoadingColor.copy(alpha = resolvedLoadingColor.alpha * scrimAlphaFraction),
      cornerRadius = cornerRadius,
      size = size
    )

    // Draw gradient wipe if loading and shimmering
    if (isLoading && isShimmering) {
      drawRoundRect(
        brush = shimmerGradientBrush(backgroundColor, shimmerOffset.value),
        cornerRadius = cornerRadius,
        size = size
      )
    }
  }
}

/**
 * Single source of truth for the shimmer gradient wipe drawn by [shimmer] and [loadingScrim].
 */
private fun DrawScope.shimmerGradientBrush(
  backgroundColor: Color,
  shimmerOffset: Float,
): Brush {
  val gradientWidth = size.width / 2.5f
  val startX = -gradientWidth + (size.width + gradientWidth * 2) * shimmerOffset
  return Brush.linearGradient(
    colors = listOf(
      backgroundColor.copy(alpha = 0f),
      backgroundColor.copy(alpha = 0.5f),
      backgroundColor.copy(alpha = 0f)
    ),
    start = Offset(startX, 0f),
    end = Offset(startX + gradientWidth, 0f)
  )
}
