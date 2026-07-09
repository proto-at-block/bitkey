package build.wallet.ui.components.label

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnit.Companion.Unspecified
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.LabelType.Title3

data class AnimatedAmount(
  val text: String,
  val value: Long,
  val animationKey: Long = 0L,
)

internal enum class AnimatedAmountDirection {
  Increase,
  Decrease,
}

internal fun animatedAmountDirectionFor(
  previousValue: Long,
  currentValue: Long,
): AnimatedAmountDirection {
  return if (currentValue < previousValue) {
    AnimatedAmountDirection.Decrease
  } else {
    AnimatedAmountDirection.Increase
  }
}

@Composable
expect fun AnimatedAmountAutoResizedLabel(
  amount: AnimatedAmount,
  modifier: Modifier = Modifier,
  type: LabelType = Title3,
  alignment: TextAlign = TextAlign.Start,
  treatment: LabelTreatment = LabelTreatment.Primary,
  color: Color = Color.Unspecified,
  allowFontScaling: Boolean = true,
  animate: Boolean = true,
  animationLabel: String = "AnimatedAmountAutoResizedLabel",
  minTextSize: TextUnit = Unspecified,
)
