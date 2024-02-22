package build.wallet.ui.tooling

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import build.wallet.ui.theme.SystemColorMode
import build.wallet.ui.theme.SystemColorMode.LIGHT
import build.wallet.ui.theme.WalletTheme

/**
 * Same as [WalletTheme] but additionally wraps [content] into a [Box] with themed background.
 */
@Composable
fun PreviewWalletTheme(
  modifier: Modifier = Modifier,
  backgroundColor: Color = WalletTheme.colors.background,
  systemColorMode: SystemColorMode = LIGHT,
  content: @Composable () -> Unit,
) {
  WalletTheme(systemColorMode = systemColorMode) {
    Box(
      modifier = modifier.background(backgroundColor),
      contentAlignment = Alignment.Center
    ) {
      content()
    }
  }
}
