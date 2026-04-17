package build.wallet.ui.components.label

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import build.wallet.ui.tokens.LabelType

@Composable
actual fun AnimatedAmountAutoResizedLabel(
  amount: AnimatedAmount,
  modifier: Modifier,
  type: LabelType,
  alignment: TextAlign,
  treatment: LabelTreatment,
  color: Color,
  allowFontScaling: Boolean,
  animate: Boolean,
  animationLabel: String,
  minTextSize: TextUnit,
) {
  AutoResizedLabel(
    text = amount.text,
    modifier = modifier,
    type = type,
    alignment = alignment,
    treatment = treatment,
    color = color,
    allowFontScaling = allowFontScaling
  )
}
