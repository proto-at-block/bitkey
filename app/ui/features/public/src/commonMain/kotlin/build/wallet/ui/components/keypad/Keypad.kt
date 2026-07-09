package build.wallet.ui.components.keypad

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import build.wallet.amount.KeypadButton
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.compose.LocalHaptics
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.statemachine.core.Icon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Keypad(
  modifier: Modifier = Modifier,
  showDecimal: Boolean,
  onButtonPress: (KeypadButton) -> Unit,
  hapticsEffectForButtonPress: (KeypadButton) -> HapticsEffect? = {
    HapticsEffect.Selection
  },
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.SpaceAround
  ) {
    KeypadButtonsRow {
      KeypadButton(
        button = KeypadButton.Digit.One,
        onClick = onButtonPress,
        hapticsEffectForButtonPress = hapticsEffectForButtonPress
      )
      KeypadButton(
        button = KeypadButton.Digit.Two,
        onClick = onButtonPress,
        hapticsEffectForButtonPress = hapticsEffectForButtonPress
      )
      KeypadButton(
        button = KeypadButton.Digit.Three,
        onClick = onButtonPress,
        hapticsEffectForButtonPress = hapticsEffectForButtonPress
      )
    }
    KeypadButtonsRow {
      KeypadButton(
        button = KeypadButton.Digit.Four,
        onClick = onButtonPress,
        hapticsEffectForButtonPress = hapticsEffectForButtonPress
      )
      KeypadButton(
        button = KeypadButton.Digit.Five,
        onClick = onButtonPress,
        hapticsEffectForButtonPress = hapticsEffectForButtonPress
      )
      KeypadButton(
        button = KeypadButton.Digit.Six,
        onClick = onButtonPress,
        hapticsEffectForButtonPress = hapticsEffectForButtonPress
      )
    }
    KeypadButtonsRow {
      KeypadButton(
        button = KeypadButton.Digit.Seven,
        onClick = onButtonPress,
        hapticsEffectForButtonPress = hapticsEffectForButtonPress
      )
      KeypadButton(
        button = KeypadButton.Digit.Eight,
        onClick = onButtonPress,
        hapticsEffectForButtonPress = hapticsEffectForButtonPress
      )
      KeypadButton(
        button = KeypadButton.Digit.Nine,
        onClick = onButtonPress,
        hapticsEffectForButtonPress = hapticsEffectForButtonPress
      )
    }
    KeypadButtonsRow {
      KeypadButton(
        show = showDecimal,
        button = KeypadButton.Decimal,
        onClick = onButtonPress,
        hapticsEffectForButtonPress = hapticsEffectForButtonPress
      )
      KeypadButton(
        button = KeypadButton.Digit.Zero,
        onClick = onButtonPress,
        hapticsEffectForButtonPress = hapticsEffectForButtonPress
      )
      KeypadButton(
        button = KeypadButton.Delete,
        onClick = onButtonPress,
        hapticsEffectForButtonPress = hapticsEffectForButtonPress
      )
    }
  }
}

@Composable
private fun KeypadButtonsRow(buttons: @Composable (RowScope.() -> Unit)) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceAround
  ) {
    buttons()
  }
}

@Composable
private fun RowScope.KeypadButton(
  show: Boolean = true,
  button: KeypadButton,
  onClick: (KeypadButton) -> Unit,
  hapticsEffectForButtonPress: (KeypadButton) -> HapticsEffect?,
) {
  val interactionSource = remember { MutableInteractionSource() }
  var isVisuallyPressed by remember { mutableStateOf(false) }
  var releaseVisualPressJob by remember { mutableStateOf<Job?>(null) }
  val scope = rememberStableCoroutineScope()
  val haptics = LocalHaptics.current

  Box(
    modifier =
      Modifier
        .weight(1F)
        .height(72.dp)
        .pointerInput(show, button) {
          if (!show) return@pointerInput

          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            releaseVisualPressJob?.cancel()
            isVisuallyPressed = true
            waitForUpOrCancellation()
            releaseVisualPressJob =
              scope.launch {
                delay(KEYPAD_PRESS_MINIMUM_VISUAL_DURATION)
                isVisuallyPressed = false
              }
          }
        }
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          enabled = show,
          onClick = {
            hapticsEffectForButtonPress(button)?.let { effect ->
              haptics?.let { providedHaptics ->
                scope.launch { providedHaptics.vibrate(effect) }
              }
            }
            onClick(button)
          }
        ),
    contentAlignment = Alignment.Center
  ) {
    if (show) {
      KeypadButtonContent(
        button = button,
        isPressed = isVisuallyPressed
      )
    } else {
      // Only hide button content and keep the box to keep keypad layout.
    }
  }
}

@Composable
private fun KeypadButtonContent(
  button: KeypadButton,
  isPressed: Boolean,
) {
  val surfaceScale by animateFloatAsState(
    targetValue =
      if (isPressed) {
        1.06f
      } else {
        1f
      },
    animationSpec =
      if (isPressed) {
        snap()
      } else {
        tween(durationMillis = KEYPAD_PRESS_OUT_DURATION_MS)
      },
    label = "keypad-button-surface-scale"
  )
  val contentScale by animateFloatAsState(
    targetValue =
      if (isPressed) {
        1.08f
      } else {
        1f
      },
    animationSpec =
      if (isPressed) {
        snap()
      } else {
        tween(durationMillis = KEYPAD_PRESS_OUT_DURATION_MS)
      },
    label = "keypad-button-content-scale"
  )
  when (button) {
    KeypadButton.Decimal -> {
      KeypadButtonSurface(
        isPressed = isPressed,
        scale = surfaceScale
      ) {
        DecimalIcon(
          color = WalletTheme.colors.secondaryForeground,
          scale = contentScale
        )
      }
    }
    KeypadButton.Delete -> {
      KeypadButtonSurface(
        isPressed = isPressed,
        scale = surfaceScale
      ) {
        DeleteIcon(
          color = WalletTheme.colors.secondaryForeground,
          scale = contentScale
        )
      }
    }
    is KeypadButton.Digit -> {
      KeypadButtonSurface(
        isPressed = isPressed,
        scale = surfaceScale
      ) {
        Label(
          modifier =
            Modifier.graphicsLayer {
              scaleX = contentScale
              scaleY = contentScale
            },
          text = button.value.toString(),
          type = LabelType.Keypad,
          treatment = LabelTreatment.Unspecified,
          color = WalletTheme.colors.secondaryForeground
        )
      }
    }
  }
}

@Composable
private fun KeypadButtonSurface(
  isPressed: Boolean,
  scale: Float,
  content: @Composable () -> Unit,
) {
  Box(
    modifier =
      Modifier
        .zIndex(if (isPressed) 1f else 0f)
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .fillMaxSize()
        .padding(horizontal = 2.dp, vertical = 2.dp)
        .background(
          color = WalletTheme.colors.secondary,
          shape = CircleShape
        ),
    contentAlignment = Alignment.Center
  ) {
    content()
  }
}

@Composable
private fun DeleteIcon(
  color: Color,
  scale: Float,
) {
  IconImage(
    modifier =
      Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
      },
    model = IconModel(
      icon = Icon.Backspace,
      iconSize = IconSize.Small
    ),
    color = color
  )
}

private const val KEYPAD_PRESS_OUT_DURATION_MS = 120
private const val KEYPAD_PRESS_MINIMUM_VISUAL_DURATION = 32L

@Composable
private fun DecimalIcon(
  color: Color,
  scale: Float,
) {
  Box(
    modifier =
      Modifier
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
        }
        .size(6.dp)
        .background(
          color = color,
          shape = CircleShape
        )
  )
}
