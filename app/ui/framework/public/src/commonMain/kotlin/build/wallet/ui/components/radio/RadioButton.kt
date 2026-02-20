package build.wallet.ui.components.radio

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.WalletTheme

/**
 * Custom radio button that replaces Material3 RadioButton.
 * Draws a circular radio button with smooth animation.
 *
 * @param selected Whether this radio button is selected
 * @param onClick Called when the radio button is clicked
 * @param modifier Modifier to be applied to the radio button
 * @param enabled Whether the radio button is enabled
 * @param selectedColor Color when selected
 * @param unselectedColor Color when unselected
 */
@Composable
fun RadioButton(
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  selectedColor: Color = WalletTheme.colors.bitkeyPrimary,
  unselectedColor: Color = WalletTheme.colors.foreground30,
) {
  // Animate the inner dot scale
  val dotScale by animateFloatAsState(
    targetValue = if (selected) 1f else 0f,
    animationSpec = tween(durationMillis = 100),
    label = "radio-button-scale"
  )

  // Choose stroke color based on selection state
  val strokeColor = if (selected) selectedColor else unselectedColor

  Canvas(
    modifier = modifier
      .selectable(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        role = Role.RadioButton
      )
      .padding(12.dp)
      .requiredSize(20.dp)
  ) {
    val strokeWidth = 2.dp.toPx()
    val outerRadius = size.minDimension / 2
    val center = Offset(
      x = size.width / 2,
      y = size.height / 2
    )

    // Draw outer ring
    drawCircle(
      color = strokeColor,
      radius = outerRadius - strokeWidth / 2,
      center = center,
      style = Stroke(width = strokeWidth),
      alpha = if (enabled) 1f else 0.5f
    )

    // Draw inner dot when selected (with animation)
    if (dotScale > 0f) {
      val innerRadius = (outerRadius - strokeWidth * 2) * dotScale
      drawCircle(
        color = selectedColor,
        radius = innerRadius,
        center = center,
        alpha = if (enabled) 1f else 0.5f
      )
    }
  }
}
