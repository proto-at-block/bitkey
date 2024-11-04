package build.wallet.ui.components.icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Icon with single digit.
 */
@Composable
internal fun DigitIcon(
  modifier: Modifier = Modifier,
  digit: Int,
) {
  require(digit in (1..10)) { """¯\_(ツ)_/¯""" }
  Box(
    modifier =
      modifier
        .size(24.dp)
        .background(
          shape = CircleShape,
          color = WalletTheme.colors.secondaryIcon
        ),
    contentAlignment = Alignment.Center
  ) {
    Label(
      text = digit.toString(),
      style =
        WalletTheme.labelStyle(
          type = LabelType.Label3,
          treatment = LabelTreatment.Unspecified,
          textColor = WalletTheme.colors.secondaryIconForeground
        )
    )
  }
}

@Preview
@Composable
private fun IconWithNumberPreview() {
  PreviewWalletTheme {
    DigitIcon(digit = 3)
  }
}
