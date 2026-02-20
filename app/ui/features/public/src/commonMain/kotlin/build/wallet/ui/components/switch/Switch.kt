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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import build.wallet.ui.compose.resId
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.theme.WalletTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Switch(
  model: SwitchModel,
  modifier: Modifier = Modifier,
) {
  with(model) {
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = modifier,
      enabled = enabled,
      testTag = testTag
    )
  }
}

/**
 * Standard dimensions:
 * - Total width: 52dp
 * - Total height: 48dp (includes touch target padding)
 * - Track width: 52dp
 * - Track height: 32dp (centered in 48dp space)
 * - Track corner radius: 16dp (half of track height)
 * - Thumb diameter (unchecked): 16dp
 * - Thumb diameter (checked): 24dp (animated)
 * - Thumb padding from track edges: 4dp
 *
 * @param checked Whether the switch is checked
 * @param onCheckedChange Called when the switch is toggled
 * @param modifier Modifier for the switch
 * @param enabled Whether the switch is enabled
 * @param testTag Test tag for the switch
 * @param checkedThumbColor Color of the thumb when checked (enabled state)
 * @param uncheckedThumbColor Color of the thumb when unchecked (enabled state)
 * @param checkedTrackColor Color of the track when checked (enabled state)
 * @param uncheckedTrackColor Color of the track when unchecked (enabled state)
 * @param disabledThumbColor Color of the thumb when disabled
 * @param disabledTrackColor Color of the track when disabled
 */
@Composable
fun Switch(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  testTag: String? = null,
  checkedThumbColor: Color = WalletTheme.colors.primaryForeground,
  uncheckedThumbColor: Color = WalletTheme.colors.primaryForeground,
  checkedTrackColor: Color = WalletTheme.colors.bitkeyPrimary,
  uncheckedTrackColor: Color = WalletTheme.colors.foreground10,
  disabledThumbColor: Color = WalletTheme.colors.foreground30,
  disabledTrackColor: Color = WalletTheme.colors.foreground10,
) {
  val coroutineScope = rememberCoroutineScope()

  // Track pressed state for thumb size animation
  var isPressed by remember { mutableStateOf(false) }

  // Animate thumb position
  val thumbOffset by animateFloatAsState(
    targetValue = if (checked) 1f else 0f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium
    ),
    label = "switch-thumb-offset"
  )

  // Animate thumb size - grows to 24dp when pressed OR checked (but not when disabled)
  val thumbDiameter by animateDpAsState(
    targetValue = if (enabled && (checked || isPressed)) 24.dp else 16.dp,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium
    ),
    label = "switch-thumb-size"
  )

  // Choose colors based on enabled and checked state
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
      .resId(testTag)
      .semantics {
        role = Role.Switch
        toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
        if (!enabled) {
          disabled()
        }
      }
      .pointerInput(enabled, checked) {
        if (enabled) {
          detectTapGestures(
            onPress = {
              // Launch background job to show press after delay
              val pressJob = coroutineScope.launch {
                delay(100) // Wait 100ms before showing press state
                isPressed = true // Thumb grows to 24dp
              }

              val released = tryAwaitRelease() // Wait for user to release

              // Clean up
              pressJob.cancel() // Cancel if still waiting
              isPressed = false // Reset press state

              if (released) {
                onCheckedChange(!checked) // Toggle the switch
              }
            }
          )
        }
      }
      .size(width = 52.dp, height = 48.dp) // 48dp height for minimum touch target
  ) {
    // Switch visual dimensions (track is 32dp tall, centered in 48dp canvas)
    val trackWidth = 52.dp.toPx()
    val trackHeight = 32.dp.toPx()
    val trackCornerRadius = trackHeight / 2
    val verticalOffset = (size.height - trackHeight) / 2 // Center vertically in 48dp space

    // Draw track (rounded rectangle) centered in canvas
    drawRoundRect(
      color = trackColor,
      topLeft = Offset(0f, verticalOffset),
      size = Size(trackWidth, trackHeight),
      cornerRadius = CornerRadius(trackCornerRadius, trackCornerRadius)
    )

    // Calculate thumb position and size
    val thumbRadius = (thumbDiameter / 2).toPx()
    val thumbPadding = 4.dp.toPx()
    val maxThumbRadius = 12.dp.toPx() // Max radius (24dp diameter when checked)
    val thumbTravelDistance = trackWidth - (maxThumbRadius * 2) - (thumbPadding * 2)
    val thumbX = thumbPadding + maxThumbRadius + (thumbTravelDistance * thumbOffset)
    val thumbY = size.height / 2

    // Draw thumb (circle) centered in 48dp space
    drawCircle(
      color = thumbColor,
      radius = thumbRadius,
      center = Offset(thumbX, thumbY)
    )
  }
}
