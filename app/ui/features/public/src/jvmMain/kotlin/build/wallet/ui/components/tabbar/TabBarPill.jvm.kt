package build.wallet.ui.components.tabbar

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun TabBarPill(
  modifier: Modifier,
  selectedIndex: Int,
  tabCount: Int,
  tabs: @Composable RowScope.() -> Unit,
) {
  // no-op for JVM
}
