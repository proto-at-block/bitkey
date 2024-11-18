package build.wallet.ui.components.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
private fun IconWithNumberPreview() {
  PreviewWalletTheme {
    DigitIcon(digit = 3)
  }
}
