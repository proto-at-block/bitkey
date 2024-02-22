package build.wallet.ui.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.ui.theme.SystemColorMode
import build.wallet.ui.theme.SystemColorMode.DARK
import build.wallet.ui.theme.SystemColorMode.LIGHT
import build.wallet.ui.theme.WalletTheme

@Composable
fun Divider(
  modifier: Modifier = Modifier,
  color: Color = WalletTheme.colors.foreground10,
  thickness: Dp = 1.dp,
) {
  Box(
    modifier
      .fillMaxWidth()
      .height(thickness)
      .background(color = color)
  )
}

@Preview
@Composable
private fun DividerLight() {
  DividerPreview(systemColorMode = LIGHT)
}

@Preview
@Composable
private fun DividerDark() {
  DividerPreview(systemColorMode = DARK)
}

@Composable
private fun DividerPreview(systemColorMode: SystemColorMode) {
  WalletTheme(systemColorMode = systemColorMode) {
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
