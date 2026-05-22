package build.wallet.ui.tooling

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme

val LocalIsPreviewTheme = compositionLocalOf { false }

/**
 * Same as [WalletTheme] but additionally wraps [content] into a [Box] with themed background.
 */
@Composable
fun PreviewWalletTheme(
  modifier: Modifier = Modifier,
  backgroundColor: Color? = null,
  theme: Theme? = null,
  content: @Composable () -> Unit,
) {
  PreviewContextConfigurationEffect()
  val isNestedPreviewTheme = LocalIsPreviewTheme.current
  val resolvedTheme = theme ?: if (isNestedPreviewTheme) LocalTheme.current else Theme.LIGHT
  CompositionLocalProvider(
    LocalIsPreviewTheme provides true,
    LocalTheme provides resolvedTheme
  ) {
    WalletTheme {
      val resolvedBackgroundColor = backgroundColor ?: WalletTheme.colors.background
      Box(
        modifier = modifier.background(resolvedBackgroundColor),
        contentAlignment = Alignment.Center
      ) {
        content()
      }
    }
  }
}
