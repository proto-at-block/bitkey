package build.wallet.ui.components.label

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
