package build.wallet.ui.components.tabbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun TabBarPill(
  modifier: Modifier,
  tabs: @Composable (() -> Unit),
) {
  // no-op for JVM
}
