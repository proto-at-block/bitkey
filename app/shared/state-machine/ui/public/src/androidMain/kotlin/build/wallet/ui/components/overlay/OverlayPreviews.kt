package build.wallet.ui.components.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Size
import build.wallet.ui.model.button.ButtonModel.Treatment
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
private fun OverlayPreview() {
  PreviewWalletTheme {
    Overlay(
      behindContent = {
        Label("Behind!", type = LabelType.Display1)
      },
      overlayContent = {
        Button(
          text = "Hello!",
          treatment = Treatment.Primary,
          size = Size.Compact,
          onClick = StandardClick {}
        )
      }
    )
  }
}
