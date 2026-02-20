package build.wallet.ui.compose

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.platform.haptics.HapticsEffect
import kotlinx.coroutines.launch

/**
 * Conditionally animates into and from applying [blur] modifier.
 */
fun Modifier.blurIf(
  condition: Boolean,
  blurRadius: Dp = 20.dp,
): Modifier =
  composed {
    val blur: Float by animateFloatAsState(if (condition) blurRadius.value else 0f)
    blur(blur.dp)
  }

inline fun Modifier.thenIf(
  condition: Boolean,
  block: () -> Modifier,
): Modifier {
  return then(
    if (condition) {
      block()
    } else {
      Modifier
    }
  )
}

inline fun <T> Modifier.thenIfNotNull(
  value: T?,
  block: Modifier.(T) -> Modifier,
): Modifier {
  return if (value == null) {
    this
  } else {
    then(block(value))
  }
}

/**
 * A clickable modifier that scales down and changes opacity when pressed.
 *
 * @param enabled Whether the click is enabled.
 * @param scaleFactor The scale factor when pressed.
 * @param alphaFactor The alpha factor when pressed.
 * @param haptics Optional haptics service for feedback on press.
 * @param hapticsEffect The haptic effect to trigger on press.
 * @param onClick The callback when clicked.
 */
fun Modifier.scalingClickable(
  enabled: Boolean = true,
  scaleFactor: Float = 0.97f,
  alphaFactor: Float = 0.97f,
  hapticsEffect: HapticsEffect = HapticsEffect.Selection,
  onClick: () -> Unit,
) = composed {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val animationTransition = updateTransition(isPressed, label = "scaling-clickable-transition")
  val scaleAnimation by animationTransition.animateFloat(
    targetValueByState = { pressed -> if (pressed) scaleFactor else 1f },
    label = "scaling-clickable-scale-transition"
  )
  val alphaAnimation by animationTransition.animateFloat(
    targetValueByState = { pressed -> if (pressed) alphaFactor else 1f },
    label = "scaling-clickable-opacity-transition"
  )

  val scope = rememberCoroutineScope()
  val haptics = LocalHaptics.current

  this.graphicsLayer {
    scaleX = scaleAnimation
    scaleY = scaleAnimation
    alpha = alphaAnimation
  }.clickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    onClick = {
      haptics?.let {
        scope.launch { it.vibrate(hapticsEffect) }
      }
      onClick()
    }
  )
}

/**
 * Sets test tag as resource ID to this semantics node, if the tag is provided.
 * The test tag can be used to find nodes in UI testing frameworks.
 */
expect fun Modifier.resId(id: String?): Modifier
