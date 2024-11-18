package build.wallet.ui.components.keypad

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun KeypadWithDecimalPreview() {
  PreviewWalletTheme {
    Keypad(showDecimal = true, onButtonPress = {})
  }
}

@Preview
@Composable
fun KeypadNoDecimalPreview() {
  PreviewWalletTheme {
    Keypad(showDecimal = false, onButtonPress = {})
  }
}
