package build.wallet.ui.components.slider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.DragInteraction.Start
import androidx.compose.foundation.interaction.DragInteraction.Stop
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction.Cancel
import androidx.compose.foundation.interaction.PressInteraction.Press
import androidx.compose.foundation.interaction.PressInteraction.Release
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SliderState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection.Rtl
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.WalletTheme
import androidx.compose.material3.Slider as MaterialSlider

/**
 * A wrapper around the Material Slider themed for this application design. The parameters largely
 * mimic the existing functionality from the material component
 *
 * @param modifier the [Modifier] to be applied to this slider
 * @param value current value of the slider. If outside of [valueRange] provided, value will be
 * coerced to this range.
 * @param onValueChange callback in which value should be updated
 * @param enabled controls the enabled state of this slider. When `false`, this component will not
 * respond to user input, and it will appear visually disabled and disabled to accessibility
 * services.
 * @param valueRange range of values that this slider can take. The passed [value] will be coerced
 * to this range.
 * @param steps if greater than 0, specifies the amount of discrete allowable values, evenly
 * distributed across the whole value range. If 0, the slider will behave continuously and allow any
 * value from the range specified. Must not be negative.
 * @param onValueChangeFinished called when value change has ended. This should not be used to
 * update the slider value (use [onValueChange] instead), but rather to know when the user has
 * completed selecting a new value by ending a drag or a click.
 * @param interactionSource the [MutableInteractionSource] representing the stream of [Interaction]s
 * for this slider. You can create and pass in your own `remember`ed instance to observe
 * [Interaction]s and customize the appearance / behavior of this slider in different states.
 */
@Composable
fun Slider(
  modifier: Modifier = Modifier,
  value: Float,
  onValueChange: (Float) -> Unit,
  enabled: Boolean = true,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  onValueChangeFinished: (() -> Unit)? = null,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
  MaterialSlider(
    modifier = modifier,
    value = value,
    onValueChange = onValueChange,
    enabled = enabled,
    valueRange = valueRange,
    onValueChangeFinished = onValueChangeFinished,
    thumb =
      remember(interactionSource, enabled) {
        {
          SliderThumb(interactionSource = interactionSource, enabled = enabled)
        }
      },
    track =
      remember(enabled) {
        { sliderState ->
          Track(sliderState = sliderState)
        }
      }
  )
}

private val ThumbDefaultElevation = 1.dp
private val ThumbPressedElevation = 6.dp
private val TrackHeight = 8.dp

@Composable
private fun Track(
  sliderState: SliderState,
  modifier: Modifier = Modifier,
) {
  val inactiveTrackColor = WalletTheme.colors.foreground10
  val activeTrackColor = WalletTheme.colors.bitkeyPrimary
  Canvas(
    modifier
      .fillMaxWidth()
      .height(TrackHeight)
  ) {
    val isRtl = layoutDirection == Rtl
    val sliderLeft = Offset(0f, center.y)
    val sliderRight = Offset(size.width, center.y)
    val sliderStart = if (isRtl) sliderRight else sliderLeft
    val sliderEnd = if (isRtl) sliderLeft else sliderRight
    val trackStrokeWidth = TrackHeight.toPx()

    // draw the inactive portion of slider
    drawLine(
      inactiveTrackColor,
      sliderStart,
      sliderEnd,
      trackStrokeWidth,
      StrokeCap.Round
    )

    val sliderValueEnd =
      Offset(
        sliderStart.x + (sliderEnd.x - sliderStart.x) * (sliderState.value / sliderState.valueRange.endInclusive),
        center.y
      )
    val sliderValueStart =
      Offset(
        sliderStart.x + (sliderEnd.x - sliderStart.x) * sliderState.valueRange.start,
        center.y
      )

    // draw the active portion of the slider
    drawLine(
      activeTrackColor,
      sliderValueStart,
      sliderValueEnd,
      trackStrokeWidth,
      StrokeCap.Round
    )
  }
}

@Composable
private fun SliderThumb(
  modifier: Modifier = Modifier,
  thumbSize: Dp = 32.dp,
  interactionSource: MutableInteractionSource,
  enabled: Boolean,
) {
  val interactions = remember { mutableStateListOf<Interaction>() }
  LaunchedEffect(interactionSource) {
    interactionSource.interactions.collect { interaction ->
      when (interaction) {
        is Press -> interactions.add(interaction)
        is Release -> interactions.remove(interaction.press)
        is Cancel -> interactions.remove(interaction.press)
        is Start -> interactions.add(interaction)
        is Stop -> interactions.remove(interaction.start)
        is DragInteraction.Cancel -> interactions.remove(interaction.start)
      }
    }
  }

  val elevation =
    if (interactions.isNotEmpty()) {
      ThumbPressedElevation
    } else {
      ThumbDefaultElevation
    }
  val shape = CircleShape

  val color = WalletTheme.colors.bitkeyPrimary
  val strokeColor = WalletTheme.colors.primaryForeground

  Canvas(
    modifier
      .size(32.dp)
      .hoverable(interactionSource = interactionSource)
      .indication(
        interactionSource = interactionSource,
        indication = ripple(
          bounded = false,
          radius = thumbSize / 2
        )
      )
      .shadow(if (enabled) elevation else 0.dp, shape, clip = false)
  ) {
    // draw the smaller inner circle
    drawCircle(
      color = color,
      radius = thumbSize.value
    )
    // draw the outer stroke circle
    drawCircle(
      color = strokeColor,
      radius = thumbSize.value,
      style =
        Stroke(
          width = 6.dp.toPx()
        )
    )
  }
}
