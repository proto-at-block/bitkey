package build.wallet.ui.tooling

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import build.wallet.ui.theme.SystemColorMode
import build.wallet.ui.theme.SystemColorMode.LIGHT
import build.wallet.ui.theme.WalletTheme

val LocalIsPreviewTheme = compositionLocalOf { false }

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
  CompositionLocalProvider(
    LocalIsPreviewTheme provides true
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
}
