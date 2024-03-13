package build.wallet.ui.components.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.label.Label
import build.wallet.ui.compose.blurIf
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel.Size
import build.wallet.ui.model.button.ButtonModel.Treatment
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

/**
 * A screen that allows rendering [overlayContent] on top of blurred [behindContent] background.
 *
 * Touch events for elements inside [behindContent] are blocked by the [OverlaySurface].
 */
@Composable
fun Overlay(
  modifier: Modifier = Modifier,
  showOverlay: Boolean = false,
  behindContent: @Composable () -> Unit,
  overlayContent: @Composable () -> Unit,
) {
  Box(modifier = modifier) {
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .background(WalletTheme.colors.background)
          .blurIf(condition = showOverlay, blurRadius = 20.dp)
    ) {
      behindContent()
    }
    if (showOverlay) {
      OverlaySurface(modifier = Modifier.fillMaxSize()) {
        overlayContent()
      }
    }
  }
}

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
