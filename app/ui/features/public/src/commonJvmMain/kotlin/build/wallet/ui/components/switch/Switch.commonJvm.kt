package build.wallet.ui.components.switch

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import build.wallet.ui.compose.resId
import build.wallet.ui.compose.resolveTestTag
import build.wallet.ui.compose.switchTestTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal actual fun PlatformSwitch(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier,
  enabled: Boolean,
  interactionsEnabled: Boolean,
  testTag: String?,
  checkedThumbColor: Color,
  uncheckedThumbColor: Color,
  checkedTrackColor: Color,
  uncheckedTrackColor: Color,
  disabledThumbColor: Color,
  disabledTrackColor: Color,
  interopBackgroundColor: Color,
) {
  val coroutineScope = rememberCoroutineScope()

  // Track pressed state for thumb size animation.
  var isPressed by remember { mutableStateOf(false) }

  // Animate thumb position.
  val thumbOffset by animateFloatAsState(
    targetValue = if (checked) 1f else 0f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium
    ),
    label = "switch-thumb-offset"
  )

  // Animate thumb size - grows to 24dp when pressed OR checked (but not when disabled).
  val thumbDiameter by animateDpAsState(
    targetValue = if (enabled && (checked || isPressed)) 24.dp else 16.dp,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium
    ),
    label = "switch-thumb-size"
  )

  // Choose colors based on enabled and checked state.
  val trackColor = when {
    !enabled -> disabledTrackColor
    checked -> checkedTrackColor
    else -> uncheckedTrackColor
  }
  val thumbColor = when {
    !enabled -> disabledThumbColor
    checked -> checkedThumbColor
    else -> uncheckedThumbColor
  }

  Canvas(
    modifier = modifier
      .resId(resolveTestTag(testTag, switchTestTag()))
      .semantics {
        role = Role.Switch
        toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
        if (!enabled || !interactionsEnabled) {
          disabled()
        }
      }
      .pointerInput(interactionsEnabled, checked, onCheckedChange) {
        if (interactionsEnabled) {
          detectTapGestures(
            onPress = {
              // Launch background job to show press after delay.
              val pressJob = coroutineScope.launch {
                delay(100) // Wait 100ms before showing press state.
                isPressed = true // Thumb grows to 24dp.
              }

              val released = tryAwaitRelease() // Wait for user to release.

              // Clean up.
              pressJob.cancel() // Cancel if still waiting.
              isPressed = false // Reset press state.

              if (released) {
                onCheckedChange(!checked) // Toggle the switch.
              }
            }
          )
        }
      }
      .size(width = 52.dp, height = 48.dp) // 48dp height for minimum touch target.
  ) {
    // Switch visual dimensions (track is 32dp tall, centered in 48dp canvas).
    val trackWidth = 52.dp.toPx()
    val trackHeight = 32.dp.toPx()
    val trackCornerRadius = trackHeight / 2
    val verticalOffset = (size.height - trackHeight) / 2 // Center vertically in 48dp space.

    // Draw track (rounded rectangle) centered in canvas.
    drawRoundRect(
      color = trackColor,
      topLeft = Offset(0f, verticalOffset),
      size = Size(trackWidth, trackHeight),
      cornerRadius = CornerRadius(trackCornerRadius, trackCornerRadius)
    )

    // Calculate thumb position and size.
    val thumbRadius = (thumbDiameter / 2).toPx()
    val thumbPadding = 4.dp.toPx()
    val maxThumbRadius = 12.dp.toPx() // Max radius (24dp diameter when checked).
    val thumbTravelDistance = trackWidth - (maxThumbRadius * 2) - (thumbPadding * 2)
    val thumbX = thumbPadding + maxThumbRadius + (thumbTravelDistance * thumbOffset)
    val thumbY = size.height / 2

    // Draw thumb (circle) centered in 48dp space.
    drawCircle(
      color = thumbColor,
      radius = thumbRadius,
      center = Offset(thumbX, thumbY)
    )
  }
}
