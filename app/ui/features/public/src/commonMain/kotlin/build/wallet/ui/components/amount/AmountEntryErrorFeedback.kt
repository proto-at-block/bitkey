package build.wallet.ui.components.amount

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import build.wallet.amount.KeypadButton
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.ui.compose.LocalHaptics

internal data class AmountEntryKeypadFeedback(
  val shouldShake: Boolean,
) {
  val hapticsEffect: HapticsEffect?
    get() = if (shouldShake) null else HapticsEffect.Selection
}

internal fun amountEntryKeypadFeedback(
  keypadButton: KeypadButton,
  isButtonPressRejected: Boolean,
  shouldTriggerContextualErrorFeedback: Boolean,
): AmountEntryKeypadFeedback {
  val shouldShake =
    when (keypadButton) {
      KeypadButton.Delete -> isButtonPressRejected
      else -> shouldTriggerContextualErrorFeedback || isButtonPressRejected
    }

  return AmountEntryKeypadFeedback(shouldShake = shouldShake)
}

@Composable
fun rememberAmountEntryShakeOffset(
  trigger: Any?,
  hapticsEffect: HapticsEffect? = null,
): Float {
  val haptics = LocalHaptics.current
  val density = LocalDensity.current
  val amountShakeOffsetPx = remember { Animatable(0f) }

  LaunchedEffect(trigger) {
    if (trigger != null) {
      amountShakeOffsetPx.snapTo(0f)
      if (hapticsEffect != null) {
        haptics?.vibrate(hapticsEffect)
      }
      amountShakeOffsetPx.runAmountErrorShake(density)
    } else {
      amountShakeOffsetPx.snapTo(0f)
    }
  }

  return amountShakeOffsetPx.value
}

private suspend fun Animatable<Float, *>.runAmountErrorShake(density: Density) {
  val shakeAmplitudes =
    with(density) {
      listOf(
        10.dp.toPx(),
        (-10).dp.toPx(),
        7.dp.toPx(),
        (-7).dp.toPx(),
        4.dp.toPx(),
        (-4).dp.toPx(),
        0f
      )
    }
  shakeAmplitudes.forEach { target ->
    animateTo(
      targetValue = target,
      animationSpec = tween(durationMillis = 42, easing = LinearOutSlowInEasing)
    )
  }
}
