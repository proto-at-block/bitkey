package build.wallet.ui.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.Theme.DARK
import build.wallet.ui.theme.Theme.LIGHT
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
private fun DividerLight() {
  DividerPreview(theme = LIGHT)
}

@Preview
@Composable
private fun DividerDark() {
  DividerPreview(theme = DARK)
}

@Composable
private fun DividerPreview(theme: Theme) {
  PreviewWalletTheme(theme = theme) {
    Box(
      modifier =
        Modifier
          .size(500.dp)
          .background(color = WalletTheme.colors.background),
      contentAlignment = Alignment.Center
    ) {
      Divider()
    }
  }
}
